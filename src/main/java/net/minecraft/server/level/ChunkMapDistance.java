package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ByteMaps;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPosition;
import net.minecraft.util.TriState;
import net.minecraft.util.thread.TaskScheduler;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.SpawnerCreature;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.Chunk;
import org.slf4j.Logger;

public abstract class ChunkMapDistance {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final int PLAYER_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
    final Long2ObjectMap<ObjectSet<EntityPlayer>> playersPerChunk = new Long2ObjectOpenHashMap();
    private final LoadingChunkTracker loadingChunkTracker;
    private final SimulationChunkTracker simulationChunkTracker;
    final TicketStorage ticketStorage;
    private final ChunkMapDistance.a naturalSpawnChunkCounter = new ChunkMapDistance.a(8);
    private final ChunkMapDistance.b playerTicketManager = new ChunkMapDistance.b(32);
    protected final Set<PlayerChunk> chunksToUpdateFutures = new ReferenceOpenHashSet();
    final ThrottlingChunkTaskDispatcher ticketDispatcher;
    final LongSet ticketsToRelease = new LongOpenHashSet();
    final Executor mainThreadExecutor;
    public int simulationDistance = 10;

    protected ChunkMapDistance(TicketStorage ticketstorage, Executor executor, Executor executor1) {
        this.ticketStorage = ticketstorage;
        this.loadingChunkTracker = new LoadingChunkTracker(this, ticketstorage);
        this.simulationChunkTracker = new SimulationChunkTracker(ticketstorage);
        TaskScheduler<Runnable> taskscheduler = TaskScheduler.wrapExecutor("player ticket throttler", executor1);

        this.ticketDispatcher = new ThrottlingChunkTaskDispatcher(taskscheduler, executor, 4);
        this.mainThreadExecutor = executor1;
    }

    protected abstract boolean isChunkToRemove(long i);

    @Nullable
    protected abstract PlayerChunk getChunk(long i);

    @Nullable
    protected abstract PlayerChunk updateChunkScheduling(long i, int j, @Nullable PlayerChunk playerchunk, int k);

    public boolean runAllUpdates(PlayerChunkMap playerchunkmap) {
        org.spigotmc.AsyncCatcher.catchOp("chunk updates"); // Spigot
        this.naturalSpawnChunkCounter.runAllUpdates();
        this.simulationChunkTracker.runAllUpdates();
        this.playerTicketManager.runAllUpdates();
        int i = Integer.MAX_VALUE - this.loadingChunkTracker.runDistanceUpdates(Integer.MAX_VALUE);
        boolean flag = i != 0;

        if (flag) {
            ;
        }

        if (!this.chunksToUpdateFutures.isEmpty()) {
            // CraftBukkit start - SPIGOT-7780: Call chunk unload events before updateHighestAllowedStatus
            for (PlayerChunk playerchunk : this.chunksToUpdateFutures) {
                playerchunk.callEventIfUnloading(playerchunkmap);
            }
            // CraftBukkit end
            for (PlayerChunk playerchunk : this.chunksToUpdateFutures) {
                playerchunk.updateHighestAllowedStatus(playerchunkmap);
            }

            for (PlayerChunk playerchunk1 : this.chunksToUpdateFutures) {
                playerchunk1.updateFutures(playerchunkmap, this.mainThreadExecutor);
            }

            this.chunksToUpdateFutures.clear();
            return true;
        } else {
            if (!this.ticketsToRelease.isEmpty()) {
                LongIterator longiterator = this.ticketsToRelease.iterator();

                while (longiterator.hasNext()) {
                    long j = longiterator.nextLong();

                    if (this.ticketStorage.getTickets(j).stream().anyMatch((ticket) -> {
                        return ticket.getType() == TicketType.PLAYER_LOADING;
                    })) {
                        PlayerChunk playerchunk2 = playerchunkmap.getUpdatingChunkIfPresent(j);

                        if (playerchunk2 == null) {
                            throw new IllegalStateException();
                        }

                        CompletableFuture<ChunkResult<Chunk>> completablefuture = playerchunk2.getEntityTickingChunkFuture();

                        completablefuture.thenAccept((chunkresult) -> {
                            this.mainThreadExecutor.execute(() -> {
                                this.ticketDispatcher.release(j, () -> {
                                }, false);
                            });
                        });
                    }
                }

                this.ticketsToRelease.clear();
            }

            return flag;
        }
    }

    public void addPlayer(SectionPosition sectionposition, EntityPlayer entityplayer) {
        ChunkCoordIntPair chunkcoordintpair = sectionposition.chunk();
        long i = chunkcoordintpair.toLong();

        ((ObjectSet) this.playersPerChunk.computeIfAbsent(i, (j) -> {
            return new ObjectOpenHashSet();
        })).add(entityplayer);
        this.naturalSpawnChunkCounter.update(i, 0, true);
        this.playerTicketManager.update(i, 0, true);
        this.ticketStorage.addTicket(new Ticket(TicketType.PLAYER_SIMULATION, this.getPlayerTicketLevel()), chunkcoordintpair);
    }

    public void removePlayer(SectionPosition sectionposition, EntityPlayer entityplayer) {
        ChunkCoordIntPair chunkcoordintpair = sectionposition.chunk();
        long i = chunkcoordintpair.toLong();
        ObjectSet<EntityPlayer> objectset = (ObjectSet) this.playersPerChunk.get(i);
        if (objectset == null) return; // CraftBukkit - SPIGOT-6208

        objectset.remove(entityplayer);
        if (objectset.isEmpty()) {
            this.playersPerChunk.remove(i);
            this.naturalSpawnChunkCounter.update(i, Integer.MAX_VALUE, false);
            this.playerTicketManager.update(i, Integer.MAX_VALUE, false);
            this.ticketStorage.removeTicket(new Ticket(TicketType.PLAYER_SIMULATION, this.getPlayerTicketLevel()), chunkcoordintpair);
        }

    }

    private int getPlayerTicketLevel() {
        return Math.max(0, ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) - this.simulationDistance);
    }

    public boolean inEntityTickingRange(long i) {
        return ChunkLevel.isEntityTicking(this.simulationChunkTracker.getLevel(i));
    }

    public boolean inBlockTickingRange(long i) {
        return ChunkLevel.isBlockTicking(this.simulationChunkTracker.getLevel(i));
    }

    public int getChunkLevel(long i, boolean flag) {
        return flag ? this.simulationChunkTracker.getLevel(i) : this.loadingChunkTracker.getLevel(i);
    }

    protected void updatePlayerTickets(int i) {
        this.playerTicketManager.updateViewDistance(i);
    }

    public void updateSimulationDistance(int i) {
        if (i != this.simulationDistance) {
            this.simulationDistance = i;
            this.ticketStorage.replaceTicketLevelOfType(this.getPlayerTicketLevel(), TicketType.PLAYER_SIMULATION);
        }

    }

    public int getNaturalSpawnChunkCount() {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.size();
    }

    public TriState hasPlayersNearby(long i) {
        this.naturalSpawnChunkCounter.runAllUpdates();
        int j = this.naturalSpawnChunkCounter.getLevel(i);

        return j <= SpawnerCreature.INSCRIBED_SQUARE_SPAWN_DISTANCE_CHUNK ? TriState.TRUE : (j > 8 ? TriState.FALSE : TriState.DEFAULT);
    }

    public void forEachEntityTickingChunk(LongConsumer longconsumer) {
        ObjectIterator objectiterator = Long2ByteMaps.fastIterable(this.simulationChunkTracker.chunks).iterator();

        while (objectiterator.hasNext()) {
            Entry entry = (Entry) objectiterator.next();
            byte b0 = entry.getByteValue();
            long i = entry.getLongKey();

            if (ChunkLevel.isEntityTicking(b0)) {
                longconsumer.accept(i);
            }
        }

    }

    public LongIterator getSpawnCandidateChunks() {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.keySet().iterator();
    }

    public String getDebugStatus() {
        return this.ticketDispatcher.getDebugStatus();
    }

    public boolean hasTickets() {
        return this.ticketStorage.hasTickets();
    }

    private class a extends ChunkMap {

        protected final Long2ByteMap chunks = new Long2ByteOpenHashMap();
        protected final int maxDistance;

        protected a(final int i) {
            super(i + 2, 16, 256);
            this.maxDistance = i;
            this.chunks.defaultReturnValue((byte) (i + 2));
        }

        @Override
        protected int getLevel(long i) {
            return this.chunks.get(i);
        }

        @Override
        protected void setLevel(long i, int j) {
            org.spigotmc.AsyncCatcher.catchOp("chunk level update"); // Spigot
            byte b0;

            if (j > this.maxDistance) {
                b0 = this.chunks.remove(i);
            } else {
                b0 = this.chunks.put(i, (byte) j);
            }

            this.onLevelChange(i, b0, j);
        }

        protected void onLevelChange(long i, int j, int k) {}

        @Override
        protected int getLevelFromSource(long i) {
            return this.havePlayer(i) ? 0 : Integer.MAX_VALUE;
        }

        private boolean havePlayer(long i) {
            ObjectSet<EntityPlayer> objectset = (ObjectSet) ChunkMapDistance.this.playersPerChunk.get(i);

            return objectset != null && !objectset.isEmpty();
        }

        public void runAllUpdates() {
            this.runUpdates(Integer.MAX_VALUE);
        }
    }

    private class b extends ChunkMapDistance.a {

        private int viewDistance = 0;
        private final Long2IntMap queueLevels = Long2IntMaps.synchronize(new Long2IntOpenHashMap());
        private final LongSet toUpdate = new LongOpenHashSet();

        protected b(final int i) {
            super(i);
            this.queueLevels.defaultReturnValue(i + 2);
        }

        @Override
        protected void onLevelChange(long i, int j, int k) {
            this.toUpdate.add(i);
        }

        public void updateViewDistance(int i) {
            ObjectIterator objectiterator = this.chunks.long2ByteEntrySet().iterator();

            while (objectiterator.hasNext()) {
                Entry entry = (Entry) objectiterator.next();
                byte b0 = entry.getByteValue();
                long j = entry.getLongKey();

                this.onLevelChange(j, b0, this.haveTicketFor(b0), b0 <= i);
            }

            this.viewDistance = i;
        }

        private void onLevelChange(long i, int j, boolean flag, boolean flag1) {
            if (flag != flag1) {
                Ticket ticket = new Ticket(TicketType.PLAYER_LOADING, ChunkMapDistance.PLAYER_TICKET_LEVEL);

                if (flag1) {
                    ChunkMapDistance.this.ticketDispatcher.submit(() -> {
                        ChunkMapDistance.this.mainThreadExecutor.execute(() -> {
                            if (this.haveTicketFor(this.getLevel(i))) {
                                ChunkMapDistance.this.ticketStorage.addTicket(i, ticket);
                                ChunkMapDistance.this.ticketsToRelease.add(i);
                            } else {
                                ChunkMapDistance.this.ticketDispatcher.release(i, () -> {
                                }, false);
                            }

                        });
                    }, i, () -> {
                        return j;
                    });
                } else {
                    ChunkMapDistance.this.ticketDispatcher.release(i, () -> {
                        ChunkMapDistance.this.mainThreadExecutor.execute(() -> {
                            ChunkMapDistance.this.ticketStorage.removeTicket(i, ticket);
                        });
                    }, true);
                }
            }

        }

        @Override
        public void runAllUpdates() {
            super.runAllUpdates();
            if (!this.toUpdate.isEmpty()) {
                LongIterator longiterator = this.toUpdate.iterator();

                while (longiterator.hasNext()) {
                    long i = longiterator.nextLong();
                    int j = this.queueLevels.get(i);
                    int k = this.getLevel(i);

                    if (j != k) {
                        ChunkMapDistance.this.ticketDispatcher.onLevelChange(new ChunkCoordIntPair(i), () -> {
                            return this.queueLevels.get(i);
                        }, k, (l) -> {
                            if (l >= this.queueLevels.defaultReturnValue()) {
                                this.queueLevels.remove(i);
                            } else {
                                this.queueLevels.put(i, l);
                            }

                        });
                        this.onLevelChange(i, k, this.haveTicketFor(j), this.haveTicketFor(k));
                    }
                }

                this.toUpdate.clear();
            }

        }

        private boolean haveTicketFor(int i) {
            return i <= this.viewDistance;
        }
    }
}

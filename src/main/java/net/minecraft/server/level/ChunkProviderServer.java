package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.FileUtils;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.SectionPosition;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.WorldLoadListener;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.thread.IAsyncTaskHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EnumCreatureType;
import net.minecraft.world.entity.ai.village.poi.VillagePlace;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.EnumSkyBlock;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.SpawnerCreature;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.World;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.IChunkAccess;
import net.minecraft.world.level.chunk.IChunkProvider;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.saveddata.PersistentBase;
import net.minecraft.world.level.storage.Convertable;
import net.minecraft.world.level.storage.WorldPersistentData;
import org.slf4j.Logger;

public class ChunkProviderServer extends IChunkProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final ChunkMapDistance distanceManager;
    private final WorldServer level;
    final Thread mainThread;
    final LightEngineThreaded lightEngine;
    private final ChunkProviderServer.a mainThreadProcessor;
    public final PlayerChunkMap chunkMap;
    private final WorldPersistentData dataStorage;
    public final TicketStorage ticketStorage;
    private long lastInhabitedUpdate;
    public boolean spawnEnemies = true;
    public boolean spawnFriendlies = true;
    private static final int CACHE_SIZE = 4;
    private final long[] lastChunkPos = new long[4];
    private final ChunkStatus[] lastChunkStatus = new ChunkStatus[4];
    private final IChunkAccess[] lastChunk = new IChunkAccess[4];
    private final List<Chunk> spawningChunks = new ObjectArrayList();
    private final Set<PlayerChunk> chunkHoldersToBroadcast = new ReferenceOpenHashSet();
    @Nullable
    @VisibleForDebug
    private SpawnerCreature.d lastSpawnState;

    public ChunkProviderServer(WorldServer worldserver, Convertable.ConversionSession convertable_conversionsession, DataFixer datafixer, StructureTemplateManager structuretemplatemanager, Executor executor, ChunkGenerator chunkgenerator, int i, int j, boolean flag, WorldLoadListener worldloadlistener, ChunkStatusUpdateListener chunkstatusupdatelistener, Supplier<WorldPersistentData> supplier) {
        this.level = worldserver;
        this.mainThreadProcessor = new ChunkProviderServer.a(worldserver);
        this.mainThread = Thread.currentThread();
        Path path = convertable_conversionsession.getDimensionPath(worldserver.dimension()).resolve("data");

        try {
            FileUtils.createDirectoriesSafe(path);
        } catch (IOException ioexception) {
            ChunkProviderServer.LOGGER.error("Failed to create dimension data storage directory", ioexception);
        }

        this.dataStorage = new WorldPersistentData(new PersistentBase.a(worldserver), path, datafixer, worldserver.registryAccess());
        this.ticketStorage = (TicketStorage) this.dataStorage.computeIfAbsent(TicketStorage.TYPE);
        this.chunkMap = new PlayerChunkMap(worldserver, convertable_conversionsession, datafixer, structuretemplatemanager, executor, this.mainThreadProcessor, this, chunkgenerator, worldloadlistener, chunkstatusupdatelistener, supplier, this.ticketStorage, i, flag);
        this.lightEngine = this.chunkMap.getLightEngine();
        this.distanceManager = this.chunkMap.getDistanceManager();
        this.distanceManager.updateSimulationDistance(j);
        this.clearCache();
    }

    // CraftBukkit start - properly implement isChunkLoaded
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        PlayerChunk chunk = this.chunkMap.getUpdatingChunkIfPresent(ChunkCoordIntPair.asLong(chunkX, chunkZ));
        if (chunk == null) {
            return false;
        }
        return chunk.getFullChunkNow() != null;
    }
    // CraftBukkit end

    @Override
    public LightEngineThreaded getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    private PlayerChunk getVisibleChunkIfPresent(long i) {
        return this.chunkMap.getVisibleChunkIfPresent(i);
    }

    public int getTickingGenerated() {
        return this.chunkMap.getTickingGenerated();
    }

    private void storeInCache(long i, @Nullable IChunkAccess ichunkaccess, ChunkStatus chunkstatus) {
        for (int j = 3; j > 0; --j) {
            this.lastChunkPos[j] = this.lastChunkPos[j - 1];
            this.lastChunkStatus[j] = this.lastChunkStatus[j - 1];
            this.lastChunk[j] = this.lastChunk[j - 1];
        }

        this.lastChunkPos[0] = i;
        this.lastChunkStatus[0] = chunkstatus;
        this.lastChunk[0] = ichunkaccess;
    }

    @Nullable
    @Override
    public IChunkAccess getChunk(int i, int j, ChunkStatus chunkstatus, boolean flag) {
        if (Thread.currentThread() != this.mainThread) {
            return (IChunkAccess) CompletableFuture.supplyAsync(() -> {
                return this.getChunk(i, j, chunkstatus, flag);
            }, this.mainThreadProcessor).join();
        } else {
            GameProfilerFiller gameprofilerfiller = Profiler.get();

            gameprofilerfiller.incrementCounter("getChunk");
            long k = ChunkCoordIntPair.asLong(i, j);

            for (int l = 0; l < 4; ++l) {
                if (k == this.lastChunkPos[l] && chunkstatus == this.lastChunkStatus[l]) {
                    IChunkAccess ichunkaccess = this.lastChunk[l];

                    if (ichunkaccess != null) { // CraftBukkit - the chunk can become accessible in the meantime TODO for non-null chunks it might also make sense to check that the chunk's state hasn't changed in the meantime
                        return ichunkaccess;
                    }
                }
            }

            gameprofilerfiller.incrementCounter("getChunkCacheMiss");
            CompletableFuture<ChunkResult<IChunkAccess>> completablefuture = this.getChunkFutureMainThread(i, j, chunkstatus, flag);
            ChunkProviderServer.a chunkproviderserver_a = this.mainThreadProcessor;

            Objects.requireNonNull(completablefuture);
            chunkproviderserver_a.managedBlock(completablefuture::isDone);
            ChunkResult<IChunkAccess> chunkresult = (ChunkResult) completablefuture.join();
            IChunkAccess ichunkaccess1 = chunkresult.orElse(null); // CraftBukkit - decompile error

            if (ichunkaccess1 == null && flag) {
                throw (IllegalStateException) SystemUtils.pauseInIde(new IllegalStateException("Chunk not there when requested: " + chunkresult.getError()));
            } else {
                this.storeInCache(k, ichunkaccess1, chunkstatus);
                return ichunkaccess1;
            }
        }
    }

    @Nullable
    @Override
    public Chunk getChunkNow(int i, int j) {
        if (Thread.currentThread() != this.mainThread) {
            return null;
        } else {
            Profiler.get().incrementCounter("getChunkNow");
            long k = ChunkCoordIntPair.asLong(i, j);

            for (int l = 0; l < 4; ++l) {
                if (k == this.lastChunkPos[l] && this.lastChunkStatus[l] == ChunkStatus.FULL) {
                    IChunkAccess ichunkaccess = this.lastChunk[l];

                    return ichunkaccess instanceof Chunk ? (Chunk) ichunkaccess : null;
                }
            }

            PlayerChunk playerchunk = this.getVisibleChunkIfPresent(k);

            if (playerchunk == null) {
                return null;
            } else {
                IChunkAccess ichunkaccess1 = playerchunk.getChunkIfPresent(ChunkStatus.FULL);

                if (ichunkaccess1 != null) {
                    this.storeInCache(k, ichunkaccess1, ChunkStatus.FULL);
                    if (ichunkaccess1 instanceof Chunk) {
                        return (Chunk) ichunkaccess1;
                    }
                }

                return null;
            }
        }
    }

    private void clearCache() {
        Arrays.fill(this.lastChunkPos, ChunkCoordIntPair.INVALID_CHUNK_POS);
        Arrays.fill(this.lastChunkStatus, (Object) null);
        Arrays.fill(this.lastChunk, (Object) null);
    }

    public CompletableFuture<ChunkResult<IChunkAccess>> getChunkFuture(int i, int j, ChunkStatus chunkstatus, boolean flag) {
        boolean flag1 = Thread.currentThread() == this.mainThread;
        CompletableFuture<ChunkResult<IChunkAccess>> completablefuture;

        if (flag1) {
            completablefuture = this.getChunkFutureMainThread(i, j, chunkstatus, flag);
            ChunkProviderServer.a chunkproviderserver_a = this.mainThreadProcessor;

            Objects.requireNonNull(completablefuture);
            chunkproviderserver_a.managedBlock(completablefuture::isDone);
        } else {
            completablefuture = CompletableFuture.supplyAsync(() -> {
                return this.getChunkFutureMainThread(i, j, chunkstatus, flag);
            }, this.mainThreadProcessor).thenCompose((completablefuture1) -> {
                return completablefuture1;
            });
        }

        return completablefuture;
    }

    private CompletableFuture<ChunkResult<IChunkAccess>> getChunkFutureMainThread(int i, int j, ChunkStatus chunkstatus, boolean flag) {
        ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(i, j);
        long k = chunkcoordintpair.toLong();
        int l = ChunkLevel.byStatus(chunkstatus);
        PlayerChunk playerchunk = this.getVisibleChunkIfPresent(k);

        // CraftBukkit start - don't add new ticket for currently unloading chunk
        boolean currentlyUnloading = false;
        if (playerchunk != null) {
            FullChunkStatus oldChunkState = ChunkLevel.fullStatus(playerchunk.oldTicketLevel);
            FullChunkStatus currentChunkState = ChunkLevel.fullStatus(playerchunk.getTicketLevel());
            currentlyUnloading = (oldChunkState.isOrAfter(FullChunkStatus.FULL) && !currentChunkState.isOrAfter(FullChunkStatus.FULL));
        }
        if (flag && !currentlyUnloading) {
            // CraftBukkit end
            this.addTicket(new Ticket(TicketType.UNKNOWN, l), chunkcoordintpair);
            if (this.chunkAbsent(playerchunk, l)) {
                GameProfilerFiller gameprofilerfiller = Profiler.get();

                gameprofilerfiller.push("chunkLoad");
                this.runDistanceManagerUpdates();
                playerchunk = this.getVisibleChunkIfPresent(k);
                gameprofilerfiller.pop();
                if (this.chunkAbsent(playerchunk, l)) {
                    throw (IllegalStateException) SystemUtils.pauseInIde(new IllegalStateException("No chunk holder after ticket has been added"));
                }
            }
        }

        return this.chunkAbsent(playerchunk, l) ? GenerationChunkHolder.UNLOADED_CHUNK_FUTURE : playerchunk.scheduleChunkGenerationTask(chunkstatus, this.chunkMap);
    }

    private boolean chunkAbsent(@Nullable PlayerChunk playerchunk, int i) {
        return playerchunk == null || playerchunk.oldTicketLevel > i; // CraftBukkit using oldTicketLevel for isLoaded checks
    }

    @Override
    public boolean hasChunk(int i, int j) {
        PlayerChunk playerchunk = this.getVisibleChunkIfPresent((new ChunkCoordIntPair(i, j)).toLong());
        int k = ChunkLevel.byStatus(ChunkStatus.FULL);

        return !this.chunkAbsent(playerchunk, k);
    }

    @Nullable
    @Override
    public LightChunk getChunkForLighting(int i, int j) {
        long k = ChunkCoordIntPair.asLong(i, j);
        PlayerChunk playerchunk = this.getVisibleChunkIfPresent(k);

        return playerchunk == null ? null : playerchunk.getChunkIfPresentUnchecked(ChunkStatus.INITIALIZE_LIGHT.getParent());
    }

    @Override
    public World getLevel() {
        return this.level;
    }

    public boolean pollTask() {
        return this.mainThreadProcessor.pollTask();
    }

    boolean runDistanceManagerUpdates() {
        boolean flag = this.distanceManager.runAllUpdates(this.chunkMap);
        boolean flag1 = this.chunkMap.promoteChunkMap();

        this.chunkMap.runGenerationTasks();
        if (!flag && !flag1) {
            return false;
        } else {
            this.clearCache();
            return true;
        }
    }

    public boolean isPositionTicking(long i) {
        if (!this.level.shouldTickBlocksAt(i)) {
            return false;
        } else {
            PlayerChunk playerchunk = this.getVisibleChunkIfPresent(i);

            return playerchunk == null ? false : ((ChunkResult) playerchunk.getTickingChunkFuture().getNow(PlayerChunk.UNLOADED_LEVEL_CHUNK)).isSuccess();
        }
    }

    public void save(boolean flag) {
        this.runDistanceManagerUpdates();
        this.chunkMap.saveAllChunks(flag);
    }

    @Override
    public void close() throws IOException {
        // CraftBukkit start
        close(true);
    }

    public void close(boolean save) throws IOException {
        if (save) {
            this.save(true);
        }
        // CraftBukkit end
        this.dataStorage.close();
        this.lightEngine.close();
        this.chunkMap.close();
    }

    // CraftBukkit start - modelled on below
    public void purgeUnload() {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("purge");
        this.ticketStorage.purgeStaleTickets();
        this.runDistanceManagerUpdates();
        gameprofilerfiller.popPush("unload");
        this.chunkMap.tick(() -> true);
        gameprofilerfiller.pop();
        this.clearCache();
    }
    // CraftBukkit end

    @Override
    public void tick(BooleanSupplier booleansupplier, boolean flag) {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("purge");
        if (this.level.tickRateManager().runsNormally() || !flag) {
            this.ticketStorage.purgeStaleTickets();
        }

        this.runDistanceManagerUpdates();
        gameprofilerfiller.popPush("chunks");
        if (flag) {
            this.tickChunks();
            this.chunkMap.tick();
        }

        gameprofilerfiller.popPush("unload");
        this.chunkMap.tick(booleansupplier);
        gameprofilerfiller.pop();
        this.clearCache();
    }

    private void tickChunks() {
        long i = this.level.getGameTime();
        long j = i - this.lastInhabitedUpdate;

        this.lastInhabitedUpdate = i;
        if (!this.level.isDebug()) {
            GameProfilerFiller gameprofilerfiller = Profiler.get();

            gameprofilerfiller.push("pollingChunks");
            if (this.level.tickRateManager().runsNormally()) {
                gameprofilerfiller.push("tickingChunks");
                this.tickChunks(gameprofilerfiller, j);
                gameprofilerfiller.pop();
            }

            this.broadcastChangedChunks(gameprofilerfiller);
            gameprofilerfiller.pop();
        }
    }

    private void broadcastChangedChunks(GameProfilerFiller gameprofilerfiller) {
        gameprofilerfiller.push("broadcast");

        for (PlayerChunk playerchunk : this.chunkHoldersToBroadcast) {
            Chunk chunk = playerchunk.getTickingChunk();

            if (chunk != null) {
                playerchunk.broadcastChanges(chunk);
            }
        }

        this.chunkHoldersToBroadcast.clear();
        gameprofilerfiller.pop();
    }

    private void tickChunks(GameProfilerFiller gameprofilerfiller, long i) {
        gameprofilerfiller.popPush("naturalSpawnCount");
        int j = this.distanceManager.getNaturalSpawnChunkCount();
        SpawnerCreature.d spawnercreature_d = SpawnerCreature.createState(j, this.level.getAllEntities(), this::getFullChunk, new LocalMobCapCalculator(this.chunkMap));

        this.lastSpawnState = spawnercreature_d;
        gameprofilerfiller.popPush("spawnAndTick");
        boolean flag = this.level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && !this.level.players().isEmpty(); // CraftBukkit
        int k = this.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        List<EnumCreatureType> list;

        if (flag && (this.spawnEnemies || this.spawnFriendlies)) {
            boolean flag1 = this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) != 0L && this.level.getLevelData().getGameTime() % this.level.ticksPerSpawnCategory.getLong(org.bukkit.entity.SpawnCategory.ANIMAL) == 0L; // CraftBukkit

            list = SpawnerCreature.getFilteredSpawningCategories(spawnercreature_d, this.spawnFriendlies, this.spawnEnemies, flag1, this.level); // CraftBukkit
        } else {
            list = List.of();
        }

        List<Chunk> list1 = this.spawningChunks;

        try {
            gameprofilerfiller.push("filteringSpawningChunks");
            this.chunkMap.collectSpawningChunks(list1);
            gameprofilerfiller.popPush("shuffleSpawningChunks");
            SystemUtils.shuffle(list1, this.level.random);
            gameprofilerfiller.popPush("tickSpawningChunks");

            for (Chunk chunk : list1) {
                this.tickSpawningChunk(chunk, i, list, spawnercreature_d);
            }
        } finally {
            list1.clear();
        }

        gameprofilerfiller.popPush("tickTickingChunks");
        this.chunkMap.forEachBlockTickingChunk((chunk1) -> {
            this.level.tickChunk(chunk1, k);
        });
        gameprofilerfiller.pop();
        gameprofilerfiller.popPush("customSpawners");
        if (flag) {
            this.level.tickCustomSpawners(this.spawnEnemies, this.spawnFriendlies);
        }

    }

    private void tickSpawningChunk(Chunk chunk, long i, List<EnumCreatureType> list, SpawnerCreature.d spawnercreature_d) {
        ChunkCoordIntPair chunkcoordintpair = chunk.getPos();

        chunk.incrementInhabitedTime(i);
        if (this.distanceManager.inEntityTickingRange(chunkcoordintpair.toLong())) {
            this.level.tickThunder(chunk);
        }

        if (!list.isEmpty()) {
            if (this.level.canSpawnEntitiesInChunk(chunkcoordintpair)) {
                SpawnerCreature.spawnForChunk(this.level, chunk, spawnercreature_d, list);
            }

        }
    }

    private void getFullChunk(long i, Consumer<Chunk> consumer) {
        PlayerChunk playerchunk = this.getVisibleChunkIfPresent(i);

        if (playerchunk != null) {
            ((ChunkResult) playerchunk.getFullChunkFuture().getNow(PlayerChunk.UNLOADED_LEVEL_CHUNK)).ifSuccess(consumer);
        }

    }

    @Override
    public String gatherStats() {
        return Integer.toString(this.getLoadedChunksCount());
    }

    @VisibleForTesting
    public int getPendingTasksCount() {
        return this.mainThreadProcessor.getPendingTasksCount();
    }

    public ChunkGenerator getGenerator() {
        return this.chunkMap.generator();
    }

    public ChunkGeneratorStructureState getGeneratorState() {
        return this.chunkMap.generatorState();
    }

    public RandomState randomState() {
        return this.chunkMap.randomState();
    }

    @Override
    public int getLoadedChunksCount() {
        return this.chunkMap.size();
    }

    public void blockChanged(BlockPosition blockposition) {
        int i = SectionPosition.blockToSectionCoord(blockposition.getX());
        int j = SectionPosition.blockToSectionCoord(blockposition.getZ());
        PlayerChunk playerchunk = this.getVisibleChunkIfPresent(ChunkCoordIntPair.asLong(i, j));

        if (playerchunk != null && playerchunk.blockChanged(blockposition)) {
            this.chunkHoldersToBroadcast.add(playerchunk);
        }

    }

    @Override
    public void onLightUpdate(EnumSkyBlock enumskyblock, SectionPosition sectionposition) {
        this.mainThreadProcessor.execute(() -> {
            PlayerChunk playerchunk = this.getVisibleChunkIfPresent(sectionposition.chunk().toLong());

            if (playerchunk != null && playerchunk.sectionLightChanged(enumskyblock, sectionposition.y())) {
                this.chunkHoldersToBroadcast.add(playerchunk);
            }

        });
    }

    public void addTicket(Ticket ticket, ChunkCoordIntPair chunkcoordintpair) {
        this.ticketStorage.addTicket(ticket, chunkcoordintpair);
    }

    public void addTicketWithRadius(TicketType tickettype, ChunkCoordIntPair chunkcoordintpair, int i) {
        this.ticketStorage.addTicketWithRadius(tickettype, chunkcoordintpair, i);
    }

    public void removeTicketWithRadius(TicketType tickettype, ChunkCoordIntPair chunkcoordintpair, int i) {
        this.ticketStorage.removeTicketWithRadius(tickettype, chunkcoordintpair, i);
    }

    @Override
    public boolean updateChunkForced(ChunkCoordIntPair chunkcoordintpair, boolean flag) {
        return this.ticketStorage.updateChunkForced(chunkcoordintpair, flag);
    }

    @Override
    public LongSet getForceLoadedChunks() {
        return this.ticketStorage.getForceLoadedChunks();
    }

    public void move(EntityPlayer entityplayer) {
        if (!entityplayer.isRemoved()) {
            this.chunkMap.move(entityplayer);
        }

    }

    public void removeEntity(Entity entity) {
        this.chunkMap.removeEntity(entity);
    }

    public void addEntity(Entity entity) {
        this.chunkMap.addEntity(entity);
    }

    public void broadcastAndSend(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcastAndSend(entity, packet);
    }

    public void broadcast(Entity entity, Packet<?> packet) {
        this.chunkMap.broadcast(entity, packet);
    }

    public void setViewDistance(int i) {
        this.chunkMap.setServerViewDistance(i);
    }

    public void setSimulationDistance(int i) {
        this.distanceManager.updateSimulationDistance(i);
    }

    @Override
    public void setSpawnSettings(boolean flag) {
        // CraftBukkit start
        this.setSpawnSettings(flag, this.spawnFriendlies);
    }

    public void setSpawnSettings(boolean flag, boolean spawnFriendlies) {
        this.spawnEnemies = flag;
        this.spawnFriendlies = spawnFriendlies;
        // CraftBukkit end
    }

    public String getChunkDebugData(ChunkCoordIntPair chunkcoordintpair) {
        return this.chunkMap.getChunkDebugData(chunkcoordintpair);
    }

    public WorldPersistentData getDataStorage() {
        return this.dataStorage;
    }

    public VillagePlace getPoiManager() {
        return this.chunkMap.getPoiManager();
    }

    public ChunkScanAccess chunkScanner() {
        return this.chunkMap.chunkScanner();
    }

    @Nullable
    @VisibleForDebug
    public SpawnerCreature.d getLastSpawnState() {
        return this.lastSpawnState;
    }

    public void deactivateTicketsOnClosing() {
        this.ticketStorage.deactivateTicketsOnClosing();
    }

    public void onChunkReadyToSend(PlayerChunk playerchunk) {
        if (playerchunk.hasChangesToBroadcast()) {
            this.chunkHoldersToBroadcast.add(playerchunk);
        }

    }

    private final class a extends IAsyncTaskHandler<Runnable> {

        a(final World world) {
            super("Chunk source main thread executor for " + String.valueOf(world.dimension().location()));
        }

        @Override
        public void managedBlock(BooleanSupplier booleansupplier) {
            super.managedBlock(() -> {
                return MinecraftServer.throwIfFatalException() && booleansupplier.getAsBoolean();
            });
        }

        @Override
        public Runnable wrapRunnable(Runnable runnable) {
            return runnable;
        }

        @Override
        protected boolean shouldRun(Runnable runnable) {
            return true;
        }

        @Override
        protected boolean scheduleExecutables() {
            return true;
        }

        @Override
        protected Thread getRunningThread() {
            return ChunkProviderServer.this.mainThread;
        }

        @Override
        protected void doRunTask(Runnable runnable) {
            Profiler.get().incrementCounter("runTask");
            super.doRunTask(runnable);
        }

        @Override
        // CraftBukkit start - process pending Chunk loadCallback() and unloadCallback() after each run task
        public boolean pollTask() {
        try {
            if (ChunkProviderServer.this.runDistanceManagerUpdates()) {
                return true;
            } else {
                ChunkProviderServer.this.lightEngine.tryScheduleUpdate();
                return super.pollTask();
            }
        } finally {
            chunkMap.callbackExecutor.run();
        }
        // CraftBukkit end
        }
    }
}

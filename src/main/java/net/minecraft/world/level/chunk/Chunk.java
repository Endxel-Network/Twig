package net.minecraft.world.level.chunk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPosition;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketDataSerializer;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockFluids;
import net.minecraft.world.level.block.BlockMinecartTrackAbstract;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ITileEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityTypes;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.level.levelgen.ChunkProviderDebug;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidType;
import net.minecraft.world.level.material.FluidTypes;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.slf4j.Logger;

public class Chunk extends IChunkAccess {

    static final Logger LOGGER = LogUtils.getLogger();
    private static final TickingBlockEntity NULL_TICKER = new TickingBlockEntity() {
        @Override
        public void tick() {}

        @Override
        public boolean isRemoved() {
            return true;
        }

        @Override
        public BlockPosition getPos() {
            return BlockPosition.ZERO;
        }

        @Override
        public String getType() {
            return "<null>";
        }
    };
    private final Map<BlockPosition, Chunk.d> tickersInLevel;
    public boolean loaded;
    public final WorldServer level; // CraftBukkit - type
    @Nullable
    private Supplier<FullChunkStatus> fullStatus;
    @Nullable
    private Chunk.c postLoad;
    private final Int2ObjectMap<GameEventListenerRegistry> gameEventListenerRegistrySections;
    private final LevelChunkTicks<Block> blockTicks;
    private final LevelChunkTicks<FluidType> fluidTicks;
    private Chunk.e unsavedListener;

    public Chunk(World world, ChunkCoordIntPair chunkcoordintpair) {
        this(world, chunkcoordintpair, ChunkConverter.EMPTY, new LevelChunkTicks(), new LevelChunkTicks(), 0L, (ChunkSection[]) null, (Chunk.c) null, (BlendingData) null);
    }

    public Chunk(World world, ChunkCoordIntPair chunkcoordintpair, ChunkConverter chunkconverter, LevelChunkTicks<Block> levelchunkticks, LevelChunkTicks<FluidType> levelchunkticks1, long i, @Nullable ChunkSection[] achunksection, @Nullable Chunk.c chunk_c, @Nullable BlendingData blendingdata) {
        super(chunkcoordintpair, chunkconverter, world, world.registryAccess().lookupOrThrow(Registries.BIOME), i, achunksection, blendingdata);
        this.tickersInLevel = Maps.newHashMap();
        this.unsavedListener = (chunkcoordintpair1) -> {
        };
        this.level = (WorldServer) world; // CraftBukkit - type
        this.gameEventListenerRegistrySections = new Int2ObjectOpenHashMap();

        for (HeightMap.Type heightmap_type : HeightMap.Type.values()) {
            if (ChunkStatus.FULL.heightmapsAfter().contains(heightmap_type)) {
                this.heightmaps.put(heightmap_type, new HeightMap(this, heightmap_type));
            }
        }

        this.postLoad = chunk_c;
        this.blockTicks = levelchunkticks;
        this.fluidTicks = levelchunkticks1;
    }

    // CraftBukkit start
    public boolean mustNotSave;
    public boolean needsDecoration;
    // CraftBukkit end

    public Chunk(WorldServer worldserver, ProtoChunk protochunk, @Nullable Chunk.c chunk_c) {
        this(worldserver, protochunk.getPos(), protochunk.getUpgradeData(), protochunk.unpackBlockTicks(), protochunk.unpackFluidTicks(), protochunk.getInhabitedTime(), protochunk.getSections(), chunk_c, protochunk.getBlendingData());
        if (!Collections.disjoint(protochunk.pendingBlockEntities.keySet(), protochunk.blockEntities.keySet())) {
            Chunk.LOGGER.error("Chunk at {} contains duplicated block entities", protochunk.getPos());
        }

        for (TileEntity tileentity : protochunk.getBlockEntities().values()) {
            this.setBlockEntity(tileentity);
        }

        this.pendingBlockEntities.putAll(protochunk.getBlockEntityNbts());

        for (int i = 0; i < protochunk.getPostProcessing().length; ++i) {
            this.postProcessing[i] = protochunk.getPostProcessing()[i];
        }

        this.setAllStarts(protochunk.getAllStarts());
        this.setAllReferences(protochunk.getAllReferences());

        for (Map.Entry<HeightMap.Type, HeightMap> map_entry : protochunk.getHeightmaps()) {
            if (ChunkStatus.FULL.heightmapsAfter().contains(map_entry.getKey())) {
                this.setHeightmap((HeightMap.Type) map_entry.getKey(), ((HeightMap) map_entry.getValue()).getRawData());
            }
        }

        this.skyLightSources = protochunk.skyLightSources;
        this.setLightCorrect(protochunk.isLightCorrect());
        this.markUnsaved();
        this.needsDecoration = true; // CraftBukkit
        // CraftBukkit start
        this.persistentDataContainer = protochunk.persistentDataContainer; // SPIGOT-6814: copy PDC to account for 1.17 to 1.18 chunk upgrading.
        // CraftBukkit end
    }

    public void setUnsavedListener(Chunk.e chunk_e) {
        this.unsavedListener = chunk_e;
        if (this.isUnsaved()) {
            chunk_e.setUnsaved(this.chunkPos);
        }

    }

    @Override
    public void markUnsaved() {
        boolean flag = this.isUnsaved();

        super.markUnsaved();
        if (!flag) {
            this.unsavedListener.setUnsaved(this.chunkPos);
        }

    }

    @Override
    public TickContainerAccess<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public TickContainerAccess<FluidType> getFluidTicks() {
        return this.fluidTicks;
    }

    @Override
    public IChunkAccess.a getTicksForSerialization(long i) {
        return new IChunkAccess.a(this.blockTicks.pack(i), this.fluidTicks.pack(i));
    }

    @Override
    public GameEventListenerRegistry getListenerRegistry(int i) {
        World world = this.level;

        if (world instanceof WorldServer worldserver) {
            return (GameEventListenerRegistry) this.gameEventListenerRegistrySections.computeIfAbsent(i, (j) -> {
                return new EuclideanGameEventListenerRegistry(worldserver, i, this::removeGameEventListenerRegistry);
            });
        } else {
            return super.getListenerRegistry(i);
        }
    }

    @Override
    public IBlockData getBlockState(BlockPosition blockposition) {
        int i = blockposition.getX();
        int j = blockposition.getY();
        int k = blockposition.getZ();

        if (this.level.isDebug()) {
            IBlockData iblockdata = null;

            if (j == 60) {
                iblockdata = Blocks.BARRIER.defaultBlockState();
            }

            if (j == 70) {
                iblockdata = ChunkProviderDebug.getBlockStateFor(i, k);
            }

            return iblockdata == null ? Blocks.AIR.defaultBlockState() : iblockdata;
        } else {
            try {
                int l = this.getSectionIndex(j);

                if (l >= 0 && l < this.sections.length) {
                    ChunkSection chunksection = this.sections[l];

                    if (!chunksection.hasOnlyAir()) {
                        return chunksection.getBlockState(i & 15, j & 15, k & 15);
                    }
                }

                return Blocks.AIR.defaultBlockState();
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting block state");
                CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Block being got");

                crashreportsystemdetails.setDetail("Location", () -> {
                    return CrashReportSystemDetails.formatLocation(this, i, j, k);
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    @Override
    public Fluid getFluidState(BlockPosition blockposition) {
        return this.getFluidState(blockposition.getX(), blockposition.getY(), blockposition.getZ());
    }

    public Fluid getFluidState(int i, int j, int k) {
        try {
            int l = this.getSectionIndex(j);

            if (l >= 0 && l < this.sections.length) {
                ChunkSection chunksection = this.sections[l];

                if (!chunksection.hasOnlyAir()) {
                    return chunksection.getFluidState(i & 15, j & 15, k & 15);
                }
            }

            return FluidTypes.EMPTY.defaultFluidState();
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting fluid state");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Block being got");

            crashreportsystemdetails.setDetail("Location", () -> {
                return CrashReportSystemDetails.formatLocation(this, i, j, k);
            });
            throw new ReportedException(crashreport);
        }
    }

    @Nullable
    @Override
    public IBlockData setBlockState(BlockPosition blockposition, IBlockData iblockdata, int i) {
        int j = blockposition.getY();
        ChunkSection chunksection = this.getSection(this.getSectionIndex(j));
        boolean flag = chunksection.hasOnlyAir();

        if (flag && iblockdata.isAir()) {
            return null;
        } else {
            int k = blockposition.getX() & 15;
            int l = j & 15;
            int i1 = blockposition.getZ() & 15;
            IBlockData iblockdata1 = chunksection.setBlockState(k, l, i1, iblockdata);

            if (iblockdata1 == iblockdata) {
                return null;
            } else {
                Block block = iblockdata.getBlock();

                ((HeightMap) this.heightmaps.get(HeightMap.Type.MOTION_BLOCKING)).update(k, j, i1, iblockdata);
                ((HeightMap) this.heightmaps.get(HeightMap.Type.MOTION_BLOCKING_NO_LEAVES)).update(k, j, i1, iblockdata);
                ((HeightMap) this.heightmaps.get(HeightMap.Type.OCEAN_FLOOR)).update(k, j, i1, iblockdata);
                ((HeightMap) this.heightmaps.get(HeightMap.Type.WORLD_SURFACE)).update(k, j, i1, iblockdata);
                boolean flag1 = chunksection.hasOnlyAir();

                if (flag != flag1) {
                    this.level.getChunkSource().getLightEngine().updateSectionStatus(blockposition, flag1);
                    this.level.getChunkSource().onSectionEmptinessChanged(this.chunkPos.x, SectionPosition.blockToSectionCoord(j), this.chunkPos.z, flag1);
                }

                if (LightEngine.hasDifferentLightProperties(iblockdata1, iblockdata)) {
                    GameProfilerFiller gameprofilerfiller = Profiler.get();

                    gameprofilerfiller.push("updateSkyLightSources");
                    this.skyLightSources.update(this, k, j, i1);
                    gameprofilerfiller.popPush("queueCheckLight");
                    this.level.getChunkSource().getLightEngine().checkBlock(blockposition);
                    gameprofilerfiller.pop();
                }

                boolean flag2 = !iblockdata1.is(block);
                boolean flag3 = (i & 64) != 0;
                boolean flag4 = (i & 256) == 0;

                if (flag2 && iblockdata1.hasBlockEntity()) {
                    if (!this.level.isClientSide && flag4) {
                        TileEntity tileentity = this.level.getBlockEntity(blockposition);

                        if (tileentity != null) {
                            tileentity.preRemoveSideEffects(blockposition, iblockdata1);
                        }
                    }

                    this.removeBlockEntity(blockposition);
                }

                if (flag2 || block instanceof BlockMinecartTrackAbstract) {
                    World world = this.level;

                    if (world instanceof WorldServer) {
                        WorldServer worldserver = (WorldServer) world;

                        if ((i & 1) != 0 || flag3) {
                            iblockdata1.affectNeighborsAfterRemoval(worldserver, blockposition, flag3);
                        }
                    }
                }

                if (!chunksection.getBlockState(k, l, i1).is(block)) {
                    return null;
                } else {
                    // CraftBukkit - Don't place while processing the BlockPlaceEvent, unless it's a BlockContainer. Prevents blocks such as TNT from activating when cancelled.
                    if (!this.level.isClientSide && (i & 512) == 0 && (!this.level.captureBlockStates || block instanceof net.minecraft.world.level.block.BlockTileEntity)) {
                        iblockdata.onPlace(this.level, blockposition, iblockdata1, flag3);
                    }

                    if (iblockdata.hasBlockEntity()) {
                        TileEntity tileentity1 = this.getBlockEntity(blockposition, Chunk.EnumTileEntityState.CHECK);

                        if (tileentity1 != null && !tileentity1.isValidBlockState(iblockdata)) {
                            Chunk.LOGGER.warn("Found mismatched block entity @ {}: type = {}, state = {}", new Object[]{blockposition, tileentity1.getType().builtInRegistryHolder().key().location(), iblockdata});
                            this.removeBlockEntity(blockposition);
                            tileentity1 = null;
                        }

                        if (tileentity1 == null) {
                            tileentity1 = ((ITileEntity) block).newBlockEntity(blockposition, iblockdata);
                            if (tileentity1 != null) {
                                this.addAndRegisterBlockEntity(tileentity1);
                            }
                        } else {
                            tileentity1.setBlockState(iblockdata);
                            this.updateBlockEntityTicker(tileentity1);
                        }
                    }

                    this.markUnsaved();
                    return iblockdata1;
                }
            }
        }
    }

    /** @deprecated */
    @Deprecated
    @Override
    public void addEntity(Entity entity) {}

    @Nullable
    private TileEntity createBlockEntity(BlockPosition blockposition) {
        IBlockData iblockdata = this.getBlockState(blockposition);

        return !iblockdata.hasBlockEntity() ? null : ((ITileEntity) iblockdata.getBlock()).newBlockEntity(blockposition, iblockdata);
    }

    @Nullable
    @Override
    public TileEntity getBlockEntity(BlockPosition blockposition) {
        return this.getBlockEntity(blockposition, Chunk.EnumTileEntityState.CHECK);
    }

    @Nullable
    public TileEntity getBlockEntity(BlockPosition blockposition, Chunk.EnumTileEntityState chunk_enumtileentitystate) {
        // CraftBukkit start
        TileEntity tileentity = level.capturedTileEntities.get(blockposition);
        if (tileentity == null) {
            tileentity = (TileEntity) this.blockEntities.get(blockposition);
        }
        // CraftBukkit end

        if (tileentity == null) {
            NBTTagCompound nbttagcompound = (NBTTagCompound) this.pendingBlockEntities.remove(blockposition);

            if (nbttagcompound != null) {
                TileEntity tileentity1 = this.promotePendingBlockEntity(blockposition, nbttagcompound);

                if (tileentity1 != null) {
                    return tileentity1;
                }
            }
        }

        if (tileentity == null) {
            if (chunk_enumtileentitystate == Chunk.EnumTileEntityState.IMMEDIATE) {
                tileentity = this.createBlockEntity(blockposition);
                if (tileentity != null) {
                    this.addAndRegisterBlockEntity(tileentity);
                }
            }
        } else if (tileentity.isRemoved()) {
            this.blockEntities.remove(blockposition);
            return null;
        }

        return tileentity;
    }

    public void addAndRegisterBlockEntity(TileEntity tileentity) {
        this.setBlockEntity(tileentity);
        if (this.isInLevel()) {
            World world = this.level;

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                this.addGameEventListener(tileentity, worldserver);
            }

            this.updateBlockEntityTicker(tileentity);
        }

    }

    private boolean isInLevel() {
        return this.loaded || this.level.isClientSide();
    }

    boolean isTicking(BlockPosition blockposition) {
        if (!this.level.getWorldBorder().isWithinBounds(blockposition)) {
            return false;
        } else {
            World world = this.level;

            if (!(world instanceof WorldServer)) {
                return true;
            } else {
                WorldServer worldserver = (WorldServer) world;

                return this.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING) && worldserver.areEntitiesLoaded(ChunkCoordIntPair.asLong(blockposition));
            }
        }
    }

    @Override
    public void setBlockEntity(TileEntity tileentity) {
        BlockPosition blockposition = tileentity.getBlockPos();
        IBlockData iblockdata = this.getBlockState(blockposition);

        if (!iblockdata.hasBlockEntity()) {
            Chunk.LOGGER.warn("Trying to set block entity {} at position {}, but state {} does not allow it", new Object[]{tileentity, blockposition, iblockdata});
            new Exception().printStackTrace(); // CraftBukkit
        } else {
            IBlockData iblockdata1 = tileentity.getBlockState();

            if (iblockdata != iblockdata1) {
                if (!tileentity.getType().isValid(iblockdata)) {
                    Chunk.LOGGER.warn("Trying to set block entity {} at position {}, but state {} does not allow it", new Object[]{tileentity, blockposition, iblockdata});
                    return;
                }

                if (iblockdata.getBlock() != iblockdata1.getBlock()) {
                    Chunk.LOGGER.warn("Block state mismatch on block entity {} in position {}, {} != {}, updating", new Object[]{tileentity, blockposition, iblockdata, iblockdata1});
                }

                tileentity.setBlockState(iblockdata);
            }

            tileentity.setLevel(this.level);
            tileentity.clearRemoved();
            TileEntity tileentity1 = (TileEntity) this.blockEntities.put(blockposition.immutable(), tileentity);

            if (tileentity1 != null && tileentity1 != tileentity) {
                tileentity1.setRemoved();
            }

        }
    }

    @Nullable
    @Override
    public NBTTagCompound getBlockEntityNbtForSaving(BlockPosition blockposition, HolderLookup.a holderlookup_a) {
        TileEntity tileentity = this.getBlockEntity(blockposition);

        if (tileentity != null && !tileentity.isRemoved()) {
            NBTTagCompound nbttagcompound = tileentity.saveWithFullMetadata(this.level.registryAccess());

            nbttagcompound.putBoolean("keepPacked", false);
            return nbttagcompound;
        } else {
            NBTTagCompound nbttagcompound1 = (NBTTagCompound) this.pendingBlockEntities.get(blockposition);

            if (nbttagcompound1 != null) {
                nbttagcompound1 = nbttagcompound1.copy();
                nbttagcompound1.putBoolean("keepPacked", true);
            }

            return nbttagcompound1;
        }
    }

    @Override
    public void removeBlockEntity(BlockPosition blockposition) {
        if (this.isInLevel()) {
            TileEntity tileentity = (TileEntity) this.blockEntities.remove(blockposition);

            // CraftBukkit start - SPIGOT-5561: Also remove from pending map
            if (!pendingBlockEntities.isEmpty()) {
                pendingBlockEntities.remove(blockposition);
            }
            // CraftBukkit end

            if (tileentity != null) {
                World world = this.level;

                if (world instanceof WorldServer) {
                    WorldServer worldserver = (WorldServer) world;

                    this.removeGameEventListener(tileentity, worldserver);
                }

                tileentity.setRemoved();
            }
        }

        this.removeBlockEntityTicker(blockposition);
    }

    private <T extends TileEntity> void removeGameEventListener(T t0, WorldServer worldserver) {
        Block block = t0.getBlockState().getBlock();

        if (block instanceof ITileEntity) {
            GameEventListener gameeventlistener = ((ITileEntity) block).getListener(worldserver, t0);

            if (gameeventlistener != null) {
                int i = SectionPosition.blockToSectionCoord(t0.getBlockPos().getY());
                GameEventListenerRegistry gameeventlistenerregistry = this.getListenerRegistry(i);

                gameeventlistenerregistry.unregister(gameeventlistener);
            }
        }

    }

    private void removeGameEventListenerRegistry(int i) {
        this.gameEventListenerRegistrySections.remove(i);
    }

    private void removeBlockEntityTicker(BlockPosition blockposition) {
        Chunk.d chunk_d = (Chunk.d) this.tickersInLevel.remove(blockposition);

        if (chunk_d != null) {
            chunk_d.rebind(Chunk.NULL_TICKER);
        }

    }

    public void runPostLoad() {
        if (this.postLoad != null) {
            this.postLoad.run(this);
            this.postLoad = null;
        }

    }

    // CraftBukkit start
    public void loadCallback() {
        org.bukkit.Server server = this.level.getCraftServer();
        if (server != null) {
            /*
             * If it's a new world, the first few chunks are generated inside
             * the World constructor. We can't reliably alter that, so we have
             * no way of creating a CraftWorld/CraftServer at that point.
             */
            org.bukkit.Chunk bukkitChunk = new org.bukkit.craftbukkit.CraftChunk(this);
            server.getPluginManager().callEvent(new org.bukkit.event.world.ChunkLoadEvent(bukkitChunk, this.needsDecoration));

            if (this.needsDecoration) {
                this.needsDecoration = false;
                java.util.Random random = new java.util.Random();
                random.setSeed(level.getSeed());
                long xRand = random.nextLong() / 2L * 2L + 1L;
                long zRand = random.nextLong() / 2L * 2L + 1L;
                random.setSeed((long) this.chunkPos.x * xRand + (long) this.chunkPos.z * zRand ^ level.getSeed());

                org.bukkit.World world = this.level.getWorld();
                if (world != null) {
                    this.level.populating = true;
                    try {
                        for (org.bukkit.generator.BlockPopulator populator : world.getPopulators()) {
                            populator.populate(world, random, bukkitChunk);
                        }
                    } finally {
                        this.level.populating = false;
                    }
                }
                server.getPluginManager().callEvent(new org.bukkit.event.world.ChunkPopulateEvent(bukkitChunk));
            }
        }
    }

    public void unloadCallback() {
        org.bukkit.Server server = this.level.getCraftServer();
        org.bukkit.Chunk bukkitChunk = new org.bukkit.craftbukkit.CraftChunk(this);
        org.bukkit.event.world.ChunkUnloadEvent unloadEvent = new org.bukkit.event.world.ChunkUnloadEvent(bukkitChunk, this.isUnsaved());
        server.getPluginManager().callEvent(unloadEvent);
        // note: saving can be prevented, but not forced if no saving is actually required
        this.mustNotSave = !unloadEvent.isSaveChunk();
    }

    @Override
    public boolean isUnsaved() {
        return super.isUnsaved() && !this.mustNotSave;
    }
    // CraftBukkit end

    public boolean isEmpty() {
        return false;
    }

    public void replaceWithPacketData(PacketDataSerializer packetdataserializer, Map<HeightMap.Type, long[]> map, Consumer<ClientboundLevelChunkPacketData.b> consumer) {
        this.clearAllBlockEntities();

        for (ChunkSection chunksection : this.sections) {
            chunksection.read(packetdataserializer);
        }

        map.forEach(this::setHeightmap);
        this.initializeLightSources();
        consumer.accept((ClientboundLevelChunkPacketData.b) (blockposition, tileentitytypes, nbttagcompound) -> {
            TileEntity tileentity = this.getBlockEntity(blockposition, Chunk.EnumTileEntityState.IMMEDIATE);

            if (tileentity != null && nbttagcompound != null && tileentity.getType() == tileentitytypes) {
                tileentity.loadWithComponents(nbttagcompound, this.level.registryAccess());
            }

        });
    }

    public void replaceBiomes(PacketDataSerializer packetdataserializer) {
        for (ChunkSection chunksection : this.sections) {
            chunksection.readBiomes(packetdataserializer);
        }

    }

    public void setLoaded(boolean flag) {
        this.loaded = flag;
    }

    public World getLevel() {
        return this.level;
    }

    public Map<BlockPosition, TileEntity> getBlockEntities() {
        return this.blockEntities;
    }

    public void postProcessGeneration(WorldServer worldserver) {
        ChunkCoordIntPair chunkcoordintpair = this.getPos();

        for (int i = 0; i < this.postProcessing.length; ++i) {
            if (this.postProcessing[i] != null) {
                ShortListIterator shortlistiterator = this.postProcessing[i].iterator();

                while (shortlistiterator.hasNext()) {
                    Short oshort = (Short) shortlistiterator.next();
                    BlockPosition blockposition = ProtoChunk.unpackOffsetCoordinates(oshort, this.getSectionYFromSectionIndex(i), chunkcoordintpair);
                    IBlockData iblockdata = this.getBlockState(blockposition);
                    Fluid fluid = iblockdata.getFluidState();

                    if (!fluid.isEmpty()) {
                        fluid.tick(worldserver, blockposition, iblockdata);
                    }

                    if (!(iblockdata.getBlock() instanceof BlockFluids)) {
                        IBlockData iblockdata1 = Block.updateFromNeighbourShapes(iblockdata, worldserver, blockposition);

                        if (iblockdata1 != iblockdata) {
                            worldserver.setBlock(blockposition, iblockdata1, 276);
                        }
                    }
                }

                this.postProcessing[i].clear();
            }
        }

        UnmodifiableIterator unmodifiableiterator = ImmutableList.copyOf(this.pendingBlockEntities.keySet()).iterator();

        while (unmodifiableiterator.hasNext()) {
            BlockPosition blockposition1 = (BlockPosition) unmodifiableiterator.next();

            this.getBlockEntity(blockposition1);
        }

        this.pendingBlockEntities.clear();
        this.upgradeData.upgrade(this);
    }

    @Nullable
    private TileEntity promotePendingBlockEntity(BlockPosition blockposition, NBTTagCompound nbttagcompound) {
        IBlockData iblockdata = this.getBlockState(blockposition);
        TileEntity tileentity;

        if ("DUMMY".equals(nbttagcompound.getStringOr("id", ""))) {
            if (iblockdata.hasBlockEntity()) {
                tileentity = ((ITileEntity) iblockdata.getBlock()).newBlockEntity(blockposition, iblockdata);
            } else {
                tileentity = null;
                Chunk.LOGGER.warn("Tried to load a DUMMY block entity @ {} but found not block entity block {} at location", blockposition, iblockdata);
            }
        } else {
            tileentity = TileEntity.loadStatic(blockposition, iblockdata, nbttagcompound, this.level.registryAccess());
        }

        if (tileentity != null) {
            tileentity.setLevel(this.level);
            this.addAndRegisterBlockEntity(tileentity);
        } else {
            Chunk.LOGGER.warn("Tried to load a block entity for block {} but failed at location {}", iblockdata, blockposition);
        }

        return tileentity;
    }

    public void unpackTicks(long i) {
        this.blockTicks.unpack(i);
        this.fluidTicks.unpack(i);
    }

    public void registerTickContainerInLevel(WorldServer worldserver) {
        worldserver.getBlockTicks().addContainer(this.chunkPos, this.blockTicks);
        worldserver.getFluidTicks().addContainer(this.chunkPos, this.fluidTicks);
    }

    public void unregisterTickContainerFromLevel(WorldServer worldserver) {
        worldserver.getBlockTicks().removeContainer(this.chunkPos);
        worldserver.getFluidTicks().removeContainer(this.chunkPos);
    }

    @Override
    public ChunkStatus getPersistedStatus() {
        return ChunkStatus.FULL;
    }

    public FullChunkStatus getFullStatus() {
        return this.fullStatus == null ? FullChunkStatus.FULL : (FullChunkStatus) this.fullStatus.get();
    }

    public void setFullStatus(Supplier<FullChunkStatus> supplier) {
        this.fullStatus = supplier;
    }

    public void clearAllBlockEntities() {
        this.blockEntities.values().forEach(TileEntity::setRemoved);
        this.blockEntities.clear();
        this.tickersInLevel.values().forEach((chunk_d) -> {
            chunk_d.rebind(Chunk.NULL_TICKER);
        });
        this.tickersInLevel.clear();
    }

    public void registerAllBlockEntitiesAfterLevelLoad() {
        this.blockEntities.values().forEach((tileentity) -> {
            World world = this.level;

            if (world instanceof WorldServer worldserver) {
                this.addGameEventListener(tileentity, worldserver);
            }

            this.updateBlockEntityTicker(tileentity);
        });
    }

    private <T extends TileEntity> void addGameEventListener(T t0, WorldServer worldserver) {
        Block block = t0.getBlockState().getBlock();

        if (block instanceof ITileEntity) {
            GameEventListener gameeventlistener = ((ITileEntity) block).getListener(worldserver, t0);

            if (gameeventlistener != null) {
                this.getListenerRegistry(SectionPosition.blockToSectionCoord(t0.getBlockPos().getY())).register(gameeventlistener);
            }
        }

    }

    private <T extends TileEntity> void updateBlockEntityTicker(T t0) {
        IBlockData iblockdata = t0.getBlockState();
        BlockEntityTicker<T> blockentityticker = iblockdata.<T>getTicker(this.level, (TileEntityTypes<T>) ((TileEntity) t0).getType()); // CraftBukkit - decompile error

        if (blockentityticker == null) {
            this.removeBlockEntityTicker(t0.getBlockPos());
        } else {
            this.tickersInLevel.compute(t0.getBlockPos(), (blockposition, chunk_d) -> {
                TickingBlockEntity tickingblockentity = this.createTicker(t0, blockentityticker);

                if (chunk_d != null) {
                    chunk_d.rebind(tickingblockentity);
                    return chunk_d;
                } else if (this.isInLevel()) {
                    Chunk.d chunk_d1 = new Chunk.d(tickingblockentity);

                    this.level.addBlockEntityTicker(chunk_d1);
                    return chunk_d1;
                } else {
                    return null;
                }
            });
        }

    }

    private <T extends TileEntity> TickingBlockEntity createTicker(T t0, BlockEntityTicker<T> blockentityticker) {
        return new Chunk.a(t0, blockentityticker);
    }

    public static enum EnumTileEntityState {

        IMMEDIATE, QUEUED, CHECK;

        private EnumTileEntityState() {}
    }

    private class a<T extends TileEntity> implements TickingBlockEntity {

        private final T blockEntity;
        private final BlockEntityTicker<T> ticker;
        private boolean loggedInvalidBlockState;

        a(final TileEntity tileentity, final BlockEntityTicker blockentityticker) {
            this.blockEntity = (T) tileentity; // CraftBukkit - decompile error
            this.ticker = blockentityticker;
        }

        @Override
        public void tick() {
            if (!this.blockEntity.isRemoved() && this.blockEntity.hasLevel()) {
                BlockPosition blockposition = this.blockEntity.getBlockPos();

                if (Chunk.this.isTicking(blockposition)) {
                    try {
                        GameProfilerFiller gameprofilerfiller = Profiler.get();

                        gameprofilerfiller.push(this::getType);
                        IBlockData iblockdata = Chunk.this.getBlockState(blockposition);

                        if (this.blockEntity.getType().isValid(iblockdata)) {
                            this.ticker.tick(Chunk.this.level, this.blockEntity.getBlockPos(), iblockdata, this.blockEntity);
                            this.loggedInvalidBlockState = false;
                        } else if (!this.loggedInvalidBlockState) {
                            this.loggedInvalidBlockState = true;
                            Chunk.LOGGER.warn("Block entity {} @ {} state {} invalid for ticking:", new Object[]{LogUtils.defer(this::getType), LogUtils.defer(this::getPos), iblockdata});
                        }

                        gameprofilerfiller.pop();
                    } catch (Throwable throwable) {
                        CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking block entity");
                        CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Block entity being ticked");

                        this.blockEntity.fillCrashReportCategory(crashreportsystemdetails);
                        throw new ReportedException(crashreport);
                    }
                }
            }

        }

        @Override
        public boolean isRemoved() {
            return this.blockEntity.isRemoved();
        }

        @Override
        public BlockPosition getPos() {
            return this.blockEntity.getBlockPos();
        }

        @Override
        public String getType() {
            return TileEntityTypes.getKey(this.blockEntity.getType()).toString();
        }

        public String toString() {
            String s = this.getType();

            return "Level ticker for " + s + "@" + String.valueOf(this.getPos());
        }
    }

    private static class d implements TickingBlockEntity {

        private TickingBlockEntity ticker;

        d(TickingBlockEntity tickingblockentity) {
            this.ticker = tickingblockentity;
        }

        void rebind(TickingBlockEntity tickingblockentity) {
            this.ticker = tickingblockentity;
        }

        @Override
        public void tick() {
            this.ticker.tick();
        }

        @Override
        public boolean isRemoved() {
            return this.ticker.isRemoved();
        }

        @Override
        public BlockPosition getPos() {
            return this.ticker.getPos();
        }

        @Override
        public String getType() {
            return this.ticker.getType();
        }

        public String toString() {
            return String.valueOf(this.ticker) + " <wrapped>";
        }
    }

    @FunctionalInterface
    public interface c {

        void run(Chunk chunk);
    }

    @FunctionalInterface
    public interface e {

        void setUnsaved(ChunkCoordIntPair chunkcoordintpair);
    }
}

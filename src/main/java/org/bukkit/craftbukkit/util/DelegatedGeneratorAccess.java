package org.bukkit.craftbukkit.util;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.IRegistry;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EnumSkyBlock;
import net.minecraft.world.level.GeneratorAccessSeed;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.RayTrace;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntityTypes;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.IChunkAccess;
import net.minecraft.world.level.chunk.IChunkProvider;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionManager;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidType;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.NextTickListEntry;
import net.minecraft.world.ticks.TickListPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;

public abstract class DelegatedGeneratorAccess implements GeneratorAccessSeed {

    private GeneratorAccessSeed handle;

    public void setHandle(GeneratorAccessSeed worldAccess) {
        this.handle = worldAccess;
    }

    public GeneratorAccessSeed getHandle() {
        return handle;
    }

    @Override
    public long getSeed() {
        return handle.getSeed();
    }

    @Override
    public boolean ensureCanWrite(BlockPosition blockposition) {
        return handle.ensureCanWrite(blockposition);
    }

    @Override
    public void setCurrentlyGenerating(Supplier<String> supplier) {
        handle.setCurrentlyGenerating(supplier);
    }

    @Override
    public WorldServer getLevel() {
        return handle.getLevel();
    }

    @Override
    public void addFreshEntityWithPassengers(Entity entity) {
        handle.addFreshEntityWithPassengers(entity);
    }

    @Override
    public void addFreshEntityWithPassengers(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        handle.addFreshEntityWithPassengers(entity, reason);
    }

    @Override
    public WorldServer getMinecraftWorld() {
        return handle.getMinecraftWorld();
    }

    @Override
    public long dayTime() {
        return handle.dayTime();
    }

    @Override
    public long nextSubTickCount() {
        return handle.nextSubTickCount();
    }

    @Override
    public <T> NextTickListEntry<T> createTick(BlockPosition blockposition, T t0, int i, TickListPriority ticklistpriority) {
        return handle.createTick(blockposition, t0, i, ticklistpriority);
    }

    @Override
    public <T> NextTickListEntry<T> createTick(BlockPosition blockposition, T t0, int i) {
        return handle.createTick(blockposition, t0, i);
    }

    @Override
    public WorldData getLevelData() {
        return handle.getLevelData();
    }

    @Override
    public DifficultyDamageScaler getCurrentDifficultyAt(BlockPosition blockposition) {
        return handle.getCurrentDifficultyAt(blockposition);
    }

    @Override
    public MinecraftServer getServer() {
        return handle.getServer();
    }

    @Override
    public EnumDifficulty getDifficulty() {
        return handle.getDifficulty();
    }

    @Override
    public IChunkProvider getChunkSource() {
        return handle.getChunkSource();
    }

    @Override
    public boolean hasChunk(int i, int j) {
        return handle.hasChunk(i, j);
    }

    @Override
    public RandomSource getRandom() {
        return handle.getRandom();
    }

    @Override
    public void updateNeighborsAt(BlockPosition blockposition, Block block) {
        handle.updateNeighborsAt(blockposition, block);
    }

    @Override
    public void neighborShapeChanged(EnumDirection enumdirection, BlockPosition blockposition, BlockPosition blockposition1, IBlockData iblockdata, int i, int j) {
        handle.neighborShapeChanged(enumdirection, blockposition, blockposition1, iblockdata, i, j);
    }

    @Override
    public void playSound(Entity entity, BlockPosition blockposition, SoundEffect soundeffect, SoundCategory soundcategory) {
        handle.playSound(entity, blockposition, soundeffect, soundcategory);
    }

    @Override
    public void playSound(Entity entity, BlockPosition blockposition, SoundEffect soundeffect, SoundCategory soundcategory, float f, float f1) {
        handle.playSound(entity, blockposition, soundeffect, soundcategory, f, f1);
    }

    @Override
    public void addParticle(ParticleParam particleparam, double d0, double d1, double d2, double d3, double d4, double d5) {
        handle.addParticle(particleparam, d0, d1, d2, d3, d4, d5);
    }

    @Override
    public void levelEvent(Entity entity, int i, BlockPosition blockposition, int j) {
        handle.levelEvent(entity, i, blockposition, j);
    }

    @Override
    public void levelEvent(int i, BlockPosition blockposition, int j) {
        handle.levelEvent(i, blockposition, j);
    }

    @Override
    public void gameEvent(Holder<GameEvent> holder, Vec3D vec3d, GameEvent.a gameevent_a) {
        handle.gameEvent(holder, vec3d, gameevent_a);
    }

    @Override
    public void gameEvent(Entity entity, Holder<GameEvent> holder, Vec3D vec3d) {
        handle.gameEvent(entity, holder, vec3d);
    }

    @Override
    public void gameEvent(Entity entity, Holder<GameEvent> holder, BlockPosition blockposition) {
        handle.gameEvent(entity, holder, blockposition);
    }

    @Override
    public void gameEvent(Holder<GameEvent> holder, BlockPosition blockposition, GameEvent.a gameevent_a) {
        handle.gameEvent(holder, blockposition, gameevent_a);
    }

    @Override
    public void gameEvent(ResourceKey<GameEvent> resourcekey, BlockPosition blockposition, GameEvent.a gameevent_a) {
        handle.gameEvent(resourcekey, blockposition, gameevent_a);
    }

    @Override
    public <T extends TileEntity> Optional<T> getBlockEntity(BlockPosition blockposition, TileEntityTypes<T> tileentitytypes) {
        return handle.getBlockEntity(blockposition, tileentitytypes);
    }

    @Override
    public List<VoxelShape> getEntityCollisions(Entity entity, AxisAlignedBB axisalignedbb) {
        return handle.getEntityCollisions(entity, axisalignedbb);
    }

    @Override
    public boolean isUnobstructed(Entity entity, VoxelShape voxelshape) {
        return handle.isUnobstructed(entity, voxelshape);
    }

    @Override
    public BlockPosition getHeightmapPos(HeightMap.Type heightmap_type, BlockPosition blockposition) {
        return handle.getHeightmapPos(heightmap_type, blockposition);
    }

    @Override
    public float getMoonBrightness() {
        return handle.getMoonBrightness();
    }

    @Override
    public float getTimeOfDay(float f) {
        return handle.getTimeOfDay(f);
    }

    @Override
    public int getMoonPhase() {
        return handle.getMoonPhase();
    }

    @Override
    public IChunkAccess getChunk(int i, int j, ChunkStatus chunkstatus, boolean flag) {
        return handle.getChunk(i, j, chunkstatus, flag);
    }

    @Override
    public int getHeight(HeightMap.Type heightmap_type, int i, int j) {
        return handle.getHeight(heightmap_type, i, j);
    }

    @Override
    public int getSkyDarken() {
        return handle.getSkyDarken();
    }

    @Override
    public BiomeManager getBiomeManager() {
        return handle.getBiomeManager();
    }

    @Override
    public Holder<BiomeBase> getBiome(BlockPosition blockposition) {
        return handle.getBiome(blockposition);
    }

    @Override
    public Stream<IBlockData> getBlockStatesIfLoaded(AxisAlignedBB axisalignedbb) {
        return handle.getBlockStatesIfLoaded(axisalignedbb);
    }

    @Override
    public int getBlockTint(BlockPosition blockposition, ColorResolver colorresolver) {
        return handle.getBlockTint(blockposition, colorresolver);
    }

    @Override
    public Holder<BiomeBase> getNoiseBiome(int i, int j, int k) {
        return handle.getNoiseBiome(i, j, k);
    }

    @Override
    public Holder<BiomeBase> getUncachedNoiseBiome(int i, int j, int k) {
        return handle.getUncachedNoiseBiome(i, j, k);
    }

    @Override
    public boolean isClientSide() {
        return handle.isClientSide();
    }

    @Override
    public int getSeaLevel() {
        return handle.getSeaLevel();
    }

    @Override
    public DimensionManager dimensionType() {
        return handle.dimensionType();
    }

    @Override
    public int getMinY() {
        return handle.getMinY();
    }

    @Override
    public int getHeight() {
        return handle.getHeight();
    }

    @Override
    public boolean isEmptyBlock(BlockPosition blockposition) {
        return handle.isEmptyBlock(blockposition);
    }

    @Override
    public boolean canSeeSkyFromBelowWater(BlockPosition blockposition) {
        return handle.canSeeSkyFromBelowWater(blockposition);
    }

    @Override
    public float getPathfindingCostFromLightLevels(BlockPosition blockposition) {
        return handle.getPathfindingCostFromLightLevels(blockposition);
    }

    @Override
    public float getLightLevelDependentMagicValue(BlockPosition blockposition) {
        return handle.getLightLevelDependentMagicValue(blockposition);
    }

    @Override
    public IChunkAccess getChunk(BlockPosition blockposition) {
        return handle.getChunk(blockposition);
    }

    @Override
    public IChunkAccess getChunk(int i, int j) {
        return handle.getChunk(i, j);
    }

    @Override
    public IChunkAccess getChunk(int i, int j, ChunkStatus chunkstatus) {
        return handle.getChunk(i, j, chunkstatus);
    }

    @Override
    public IBlockAccess getChunkForCollisions(int i, int j) {
        return handle.getChunkForCollisions(i, j);
    }

    @Override
    public boolean isWaterAt(BlockPosition blockposition) {
        return handle.isWaterAt(blockposition);
    }

    @Override
    public boolean containsAnyLiquid(AxisAlignedBB axisalignedbb) {
        return handle.containsAnyLiquid(axisalignedbb);
    }

    @Override
    public int getMaxLocalRawBrightness(BlockPosition blockposition) {
        return handle.getMaxLocalRawBrightness(blockposition);
    }

    @Override
    public int getMaxLocalRawBrightness(BlockPosition blockposition, int i) {
        return handle.getMaxLocalRawBrightness(blockposition, i);
    }

    @Override
    public boolean hasChunkAt(int i, int j) {
        return handle.hasChunkAt(i, j);
    }

    @Override
    public boolean hasChunkAt(BlockPosition blockposition) {
        return handle.hasChunkAt(blockposition);
    }

    @Override
    public boolean hasChunksAt(BlockPosition blockposition, BlockPosition blockposition1) {
        return handle.hasChunksAt(blockposition, blockposition1);
    }

    @Override
    public boolean hasChunksAt(int i, int j, int k, int l, int i1, int j1) {
        return handle.hasChunksAt(i, j, k, l, i1, j1);
    }

    @Override
    public boolean hasChunksAt(int i, int j, int k, int l) {
        return handle.hasChunksAt(i, j, k, l);
    }

    @Override
    public IRegistryCustom registryAccess() {
        return handle.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return handle.enabledFeatures();
    }

    @Override
    public <T> HolderLookup<T> holderLookup(ResourceKey<? extends IRegistry<? extends T>> resourcekey) {
        return handle.holderLookup(resourcekey);
    }

    @Override
    public float getShade(EnumDirection enumdirection, boolean flag) {
        return handle.getShade(enumdirection, flag);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return handle.getLightEngine();
    }

    @Override
    public int getBrightness(EnumSkyBlock enumskyblock, BlockPosition blockposition) {
        return handle.getBrightness(enumskyblock, blockposition);
    }

    @Override
    public int getRawBrightness(BlockPosition blockposition, int i) {
        return handle.getRawBrightness(blockposition, i);
    }

    @Override
    public boolean canSeeSky(BlockPosition blockposition) {
        return handle.canSeeSky(blockposition);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return handle.getWorldBorder();
    }

    @Override
    public boolean isUnobstructed(IBlockData iblockdata, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return handle.isUnobstructed(iblockdata, blockposition, voxelshapecollision);
    }

    @Override
    public boolean isUnobstructed(Entity entity) {
        return handle.isUnobstructed(entity);
    }

    @Override
    public boolean noCollision(AxisAlignedBB axisalignedbb) {
        return handle.noCollision(axisalignedbb);
    }

    @Override
    public boolean noCollision(Entity entity) {
        return handle.noCollision(entity);
    }

    @Override
    public boolean noCollision(Entity entity, AxisAlignedBB axisalignedbb) {
        return handle.noCollision(entity, axisalignedbb);
    }

    @Override
    public boolean noCollision(Entity entity, AxisAlignedBB axisalignedbb, boolean flag) {
        return handle.noCollision(entity, axisalignedbb, flag);
    }

    @Override
    public boolean noBlockCollision(Entity entity, AxisAlignedBB axisalignedbb) {
        return handle.noBlockCollision(entity, axisalignedbb);
    }

    @Override
    public Iterable<VoxelShape> getCollisions(Entity entity, AxisAlignedBB axisalignedbb) {
        return handle.getCollisions(entity, axisalignedbb);
    }

    @Override
    public Iterable<VoxelShape> getBlockCollisions(Entity entity, AxisAlignedBB axisalignedbb) {
        return handle.getBlockCollisions(entity, axisalignedbb);
    }

    @Override
    public Iterable<VoxelShape> getBlockAndLiquidCollisions(Entity entity, AxisAlignedBB axisalignedbb) {
        return handle.getBlockAndLiquidCollisions(entity, axisalignedbb);
    }

    @Override
    public MovingObjectPositionBlock clipIncludingBorder(RayTrace raytrace) {
        return handle.clipIncludingBorder(raytrace);
    }

    @Override
    public boolean collidesWithSuffocatingBlock(Entity entity, AxisAlignedBB axisalignedbb) {
        return handle.collidesWithSuffocatingBlock(entity, axisalignedbb);
    }

    @Override
    public Optional<BlockPosition> findSupportingBlock(Entity entity, AxisAlignedBB axisalignedbb) {
        return handle.findSupportingBlock(entity, axisalignedbb);
    }

    @Override
    public Optional<Vec3D> findFreePosition(Entity entity, VoxelShape voxelshape, Vec3D vec3d, double d0, double d1, double d2) {
        return handle.findFreePosition(entity, voxelshape, vec3d, d0, d1, d2);
    }

    @Override
    public int getDirectSignal(BlockPosition blockposition, EnumDirection enumdirection) {
        return handle.getDirectSignal(blockposition, enumdirection);
    }

    @Override
    public int getDirectSignalTo(BlockPosition blockposition) {
        return handle.getDirectSignalTo(blockposition);
    }

    @Override
    public int getControlInputSignal(BlockPosition blockposition, EnumDirection enumdirection, boolean flag) {
        return handle.getControlInputSignal(blockposition, enumdirection, flag);
    }

    @Override
    public boolean hasSignal(BlockPosition blockposition, EnumDirection enumdirection) {
        return handle.hasSignal(blockposition, enumdirection);
    }

    @Override
    public int getSignal(BlockPosition blockposition, EnumDirection enumdirection) {
        return handle.getSignal(blockposition, enumdirection);
    }

    @Override
    public boolean hasNeighborSignal(BlockPosition blockposition) {
        return handle.hasNeighborSignal(blockposition);
    }

    @Override
    public int getBestNeighborSignal(BlockPosition blockposition) {
        return handle.getBestNeighborSignal(blockposition);
    }

    @Override
    public TileEntity getBlockEntity(BlockPosition blockposition) {
        return handle.getBlockEntity(blockposition);
    }

    @Override
    public IBlockData getBlockState(BlockPosition blockposition) {
        return handle.getBlockState(blockposition);
    }

    @Override
    public Fluid getFluidState(BlockPosition blockposition) {
        return handle.getFluidState(blockposition);
    }

    @Override
    public int getLightEmission(BlockPosition blockposition) {
        return handle.getLightEmission(blockposition);
    }

    @Override
    public Stream<IBlockData> getBlockStates(AxisAlignedBB axisalignedbb) {
        return handle.getBlockStates(axisalignedbb);
    }

    @Override
    public MovingObjectPositionBlock isBlockInLine(ClipBlockStateContext clipblockstatecontext) {
        return handle.isBlockInLine(clipblockstatecontext);
    }

    @Override
    public MovingObjectPositionBlock clip(RayTrace raytrace1, BlockPosition blockposition) {
        return handle.clip(raytrace1, blockposition);
    }

    @Override
    public MovingObjectPositionBlock clip(RayTrace raytrace) {
        return handle.clip(raytrace);
    }

    @Override
    public MovingObjectPositionBlock clipWithInteractionOverride(Vec3D vec3d, Vec3D vec3d1, BlockPosition blockposition, VoxelShape voxelshape, IBlockData iblockdata) {
        return handle.clipWithInteractionOverride(vec3d, vec3d1, blockposition, voxelshape, iblockdata);
    }

    @Override
    public double getBlockFloorHeight(VoxelShape voxelshape, Supplier<VoxelShape> supplier) {
        return handle.getBlockFloorHeight(voxelshape, supplier);
    }

    @Override
    public double getBlockFloorHeight(BlockPosition blockposition) {
        return handle.getBlockFloorHeight(blockposition);
    }

    @Override
    public List<Entity> getEntities(Entity entity, AxisAlignedBB axisalignedbb, Predicate<? super Entity> predicate) {
        return handle.getEntities(entity, axisalignedbb, predicate);
    }

    @Override
    public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> entitytypetest, AxisAlignedBB axisalignedbb, Predicate<? super T> predicate) {
        return handle.getEntities(entitytypetest, axisalignedbb, predicate);
    }

    @Override
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> oclass, AxisAlignedBB axisalignedbb, Predicate<? super T> predicate) {
        return handle.getEntitiesOfClass(oclass, axisalignedbb, predicate);
    }

    @Override
    public List<? extends EntityHuman> players() {
        return handle.players();
    }

    @Override
    public List<Entity> getEntities(Entity entity, AxisAlignedBB axisalignedbb) {
        return handle.getEntities(entity, axisalignedbb);
    }

    @Override
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> oclass, AxisAlignedBB axisalignedbb) {
        return handle.getEntitiesOfClass(oclass, axisalignedbb);
    }

    @Override
    public EntityHuman getNearestPlayer(double d0, double d1, double d2, double d3, Predicate<Entity> predicate) {
        return handle.getNearestPlayer(d0, d1, d2, d3, predicate);
    }

    @Override
    public EntityHuman getNearestPlayer(Entity entity, double d0) {
        return handle.getNearestPlayer(entity, d0);
    }

    @Override
    public EntityHuman getNearestPlayer(double d0, double d1, double d2, double d3, boolean flag) {
        return handle.getNearestPlayer(d0, d1, d2, d3, flag);
    }

    @Override
    public boolean hasNearbyAlivePlayer(double d0, double d1, double d2, double d3) {
        return handle.hasNearbyAlivePlayer(d0, d1, d2, d3);
    }

    @Override
    public EntityHuman getPlayerByUUID(UUID uuid) {
        return handle.getPlayerByUUID(uuid);
    }

    @Override
    public boolean setBlock(BlockPosition blockposition, IBlockData iblockdata, int i, int j) {
        return handle.setBlock(blockposition, iblockdata, i, j);
    }

    @Override
    public boolean setBlock(BlockPosition blockposition, IBlockData iblockdata, int i) {
        return handle.setBlock(blockposition, iblockdata, i);
    }

    @Override
    public boolean removeBlock(BlockPosition blockposition, boolean flag) {
        return handle.removeBlock(blockposition, flag);
    }

    @Override
    public boolean destroyBlock(BlockPosition blockposition, boolean flag) {
        return handle.destroyBlock(blockposition, flag);
    }

    @Override
    public boolean destroyBlock(BlockPosition blockposition, boolean flag, Entity entity) {
        return handle.destroyBlock(blockposition, flag, entity);
    }

    @Override
    public boolean destroyBlock(BlockPosition blockposition, boolean flag, Entity entity, int i) {
        return handle.destroyBlock(blockposition, flag, entity, i);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        return handle.addFreshEntity(entity);
    }

    @Override
    public boolean addFreshEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return handle.addFreshEntity(entity, reason);
    }

    @Override
    public int getMaxY() {
        return handle.getMaxY();
    }

    @Override
    public int getSectionsCount() {
        return handle.getSectionsCount();
    }

    @Override
    public int getMinSectionY() {
        return handle.getMinSectionY();
    }

    @Override
    public int getMaxSectionY() {
        return handle.getMaxSectionY();
    }

    @Override
    public boolean isInsideBuildHeight(int i) {
        return handle.isInsideBuildHeight(i);
    }

    @Override
    public boolean isOutsideBuildHeight(BlockPosition blockposition) {
        return handle.isOutsideBuildHeight(blockposition);
    }

    @Override
    public boolean isOutsideBuildHeight(int i) {
        return handle.isOutsideBuildHeight(i);
    }

    @Override
    public int getSectionIndex(int i) {
        return handle.getSectionIndex(i);
    }

    @Override
    public int getSectionIndexFromSectionY(int i) {
        return handle.getSectionIndexFromSectionY(i);
    }

    @Override
    public int getSectionYFromSectionIndex(int i) {
        return handle.getSectionYFromSectionIndex(i);
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return handle.getBlockTicks();
    }

    @Override
    public void scheduleTick(BlockPosition blockposition, Block block, int i, TickListPriority ticklistpriority) {
        handle.scheduleTick(blockposition, block, i, ticklistpriority);
    }

    @Override
    public void scheduleTick(BlockPosition blockposition, Block block, int i) {
        handle.scheduleTick(blockposition, block, i);
    }

    @Override
    public LevelTickAccess<FluidType> getFluidTicks() {
        return handle.getFluidTicks();
    }

    @Override
    public void scheduleTick(BlockPosition blockposition, FluidType fluidtype, int i, TickListPriority ticklistpriority) {
        handle.scheduleTick(blockposition, fluidtype, i, ticklistpriority);
    }

    @Override
    public void scheduleTick(BlockPosition blockposition, FluidType fluidtype, int i) {
        handle.scheduleTick(blockposition, fluidtype, i);
    }

    @Override
    public boolean isStateAtPosition(BlockPosition blockposition, Predicate<IBlockData> predicate) {
        return handle.isStateAtPosition(blockposition, predicate);
    }

    @Override
    public boolean isFluidAtPosition(BlockPosition blockposition, Predicate<Fluid> predicate) {
        return handle.isFluidAtPosition(blockposition, predicate);
    }
}

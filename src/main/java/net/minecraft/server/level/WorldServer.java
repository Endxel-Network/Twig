package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.ReportType;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPosition;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.network.protocol.game.PacketPlayOutBlockAction;
import net.minecraft.network.protocol.game.PacketPlayOutBlockBreakAnimation;
import net.minecraft.network.protocol.game.PacketPlayOutEntitySound;
import net.minecraft.network.protocol.game.PacketPlayOutEntityStatus;
import net.minecraft.network.protocol.game.PacketPlayOutExplosion;
import net.minecraft.network.protocol.game.PacketPlayOutGameStateChange;
import net.minecraft.network.protocol.game.PacketPlayOutNamedSoundEffect;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnPosition;
import net.minecraft.network.protocol.game.PacketPlayOutWorldEvent;
import net.minecraft.network.protocol.game.PacketPlayOutWorldParticles;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ScoreboardServer;
import net.minecraft.server.level.progress.WorldLoadListener;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.SleepStatus;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.tags.TagKey;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.CSVWriter;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLightning;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumCreatureType;
import net.minecraft.world.entity.ReputationHandler;
import net.minecraft.world.entity.ai.navigation.NavigationAbstract;
import net.minecraft.world.entity.ai.village.ReputationEvent;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.village.poi.VillagePlace;
import net.minecraft.world.entity.ai.village.poi.VillagePlaceType;
import net.minecraft.world.entity.animal.horse.EntityHorseSkeleton;
import net.minecraft.world.entity.boss.EntityComplexPart;
import net.minecraft.world.entity.boss.enderdragon.EntityEnderDragon;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.raid.PersistentRaid;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.alchemy.PotionBrewer;
import net.minecraft.world.item.crafting.CraftingManager;
import net.minecraft.world.level.BlockActionData;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GeneratorAccessSeed;
import net.minecraft.world.level.MobSpawner;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.SpawnerCreature;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.World;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockSnow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkSection;
import net.minecraft.world.level.chunk.IChunkAccess;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.WorldDimension;
import net.minecraft.world.level.dimension.end.EnderDragonBattle;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureBoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidType;
import net.minecraft.world.level.pathfinder.PathTypeCache;
import net.minecraft.world.level.portal.PortalTravelAgent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.PersistentIdCounts;
import net.minecraft.world.level.saveddata.maps.WorldMap;
import net.minecraft.world.level.storage.Convertable;
import net.minecraft.world.level.storage.IWorldDataServer;
import net.minecraft.world.level.storage.WorldPersistentData;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.OperatorBoolean;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapes;
import net.minecraft.world.ticks.TickListServer;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.UUID;
import net.minecraft.world.level.biome.WorldChunkManager;
import net.minecraft.world.level.dimension.WorldDimension;
import net.minecraft.world.level.levelgen.ChunkGeneratorAbstract;
import net.minecraft.world.level.levelgen.ChunkProviderFlat;
import net.minecraft.world.level.storage.WorldDataServer;
import org.bukkit.Bukkit;
import org.bukkit.WeatherType;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.generator.CustomWorldChunkManager;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.WorldUUID;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.TimeSkipEvent;
// CraftBukkit end

public class WorldServer extends World implements ServerEntityGetter, GeneratorAccessSeed {

    public static final BlockPosition END_SPAWN_POINT = new BlockPosition(100, 50, 0);
    public static final IntProvider RAIN_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider RAIN_DURATION = UniformInt.of(12000, 24000);
    private static final IntProvider THUNDER_DELAY = UniformInt.of(12000, 180000);
    public static final IntProvider THUNDER_DURATION = UniformInt.of(3600, 15600);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EMPTY_TIME_NO_TICK = 300;
    private static final int MAX_SCHEDULED_TICKS_PER_TICK = 65536;
    final List<EntityPlayer> players = Lists.newArrayList();
    private final ChunkProviderServer chunkSource;
    private final MinecraftServer server;
    public final WorldDataServer serverLevelData; // CraftBukkit - type
    private int lastSpawnChunkRadius;
    final EntityTickList entityTickList = new EntityTickList();
    public final PersistentEntitySectionManager<Entity> entityManager;
    private final GameEventDispatcher gameEventDispatcher;
    public boolean noSave;
    private final SleepStatus sleepStatus;
    private int emptyTime;
    private final PortalTravelAgent portalForcer;
    private final TickListServer<Block> blockTicks = new TickListServer<Block>(this::isPositionTickingWithEntitiesLoaded);
    private final TickListServer<FluidType> fluidTicks = new TickListServer<FluidType>(this::isPositionTickingWithEntitiesLoaded);
    private final PathTypeCache pathTypesByPosCache = new PathTypeCache();
    final Set<EntityInsentient> navigatingMobs = new ObjectOpenHashSet();
    volatile boolean isUpdatingNavigations;
    protected final PersistentRaid raids;
    private final ObjectLinkedOpenHashSet<BlockActionData> blockEvents = new ObjectLinkedOpenHashSet();
    private final List<BlockActionData> blockEventsToReschedule = new ArrayList(64);
    private boolean handlingTick;
    private final List<MobSpawner> customSpawners;
    @Nullable
    private EnderDragonBattle dragonFight;
    final Int2ObjectMap<EntityComplexPart> dragonParts = new Int2ObjectOpenHashMap();
    private final StructureManager structureManager;
    private final StructureCheck structureCheck;
    private final boolean tickTime;
    private final RandomSequences randomSequences;

    // CraftBukkit start
    public final Convertable.ConversionSession convertable;
    public final UUID uuid;

    public Chunk getChunkIfLoaded(int x, int z) {
        return this.chunkSource.getChunk(x, z, false);
    }

    @Override
    public ResourceKey<WorldDimension> getTypeKey() {
        return convertable.dimensionType;
    }

    // Add env and gen to constructor, IWorldDataServer -> WorldDataServer
    public WorldServer(MinecraftServer minecraftserver, Executor executor, Convertable.ConversionSession convertable_conversionsession, WorldDataServer iworlddataserver, ResourceKey<World> resourcekey, WorldDimension worlddimension, WorldLoadListener worldloadlistener, boolean flag, long i, List<MobSpawner> list, boolean flag1, @Nullable RandomSequences randomsequences, org.bukkit.World.Environment env, org.bukkit.generator.ChunkGenerator gen, org.bukkit.generator.BiomeProvider biomeProvider) {
        super(iworlddataserver, resourcekey, minecraftserver.registryAccess(), worlddimension.type(), false, flag, i, minecraftserver.getMaxChainedNeighborUpdates(), gen, biomeProvider, env);
        this.pvpMode = minecraftserver.isPvpAllowed();
        convertable = convertable_conversionsession;
        uuid = WorldUUID.getUUID(convertable_conversionsession.levelDirectory.path().toFile());
        // CraftBukkit end
        this.tickTime = flag1;
        this.server = minecraftserver;
        this.customSpawners = list;
        this.serverLevelData = iworlddataserver;
        ChunkGenerator chunkgenerator = worlddimension.generator();
        // CraftBukkit start
        serverLevelData.setWorld(this);

        if (biomeProvider != null) {
            WorldChunkManager worldChunkManager = new CustomWorldChunkManager(getWorld(), biomeProvider, server.registryAccess().lookupOrThrow(Registries.BIOME));
            if (chunkgenerator instanceof ChunkGeneratorAbstract cga) {
                chunkgenerator = new ChunkGeneratorAbstract(worldChunkManager, cga.settings);
            } else if (chunkgenerator instanceof ChunkProviderFlat cpf) {
                chunkgenerator = new ChunkProviderFlat(cpf.settings(), worldChunkManager);
            }
        }

        if (gen != null) {
            chunkgenerator = new org.bukkit.craftbukkit.generator.CustomChunkGenerator(this, chunkgenerator, gen);
        }
        // CraftBukkit end
        boolean flag2 = minecraftserver.forceSynchronousWrites();
        DataFixer datafixer = minecraftserver.getFixerUpper();
        EntityPersistentStorage<Entity> entitypersistentstorage = new EntityStorage(new SimpleRegionStorage(new RegionStorageInfo(convertable_conversionsession.getLevelId(), resourcekey, "entities"), convertable_conversionsession.getDimensionPath(resourcekey).resolve("entities"), datafixer, flag2, DataFixTypes.ENTITY_CHUNK), this, minecraftserver);

        this.entityManager = new PersistentEntitySectionManager<Entity>(Entity.class, new WorldServer.a(), entitypersistentstorage);
        StructureTemplateManager structuretemplatemanager = minecraftserver.getStructureManager();
        int j = minecraftserver.getPlayerList().getViewDistance();
        int k = minecraftserver.getPlayerList().getSimulationDistance();
        PersistentEntitySectionManager persistententitysectionmanager = this.entityManager;

        Objects.requireNonNull(this.entityManager);
        this.chunkSource = new ChunkProviderServer(this, convertable_conversionsession, datafixer, structuretemplatemanager, executor, chunkgenerator, j, k, flag2, worldloadlistener, persistententitysectionmanager::updateChunkStatus, () -> {
            return minecraftserver.overworld().getDataStorage();
        });
        this.chunkSource.getGeneratorState().ensureStructuresGenerated();
        this.portalForcer = new PortalTravelAgent(this);
        this.updateSkyBrightness();
        this.prepareWeather();
        this.getWorldBorder().setAbsoluteMaxSize(minecraftserver.getAbsoluteMaxWorldSize());
        this.raids = (PersistentRaid) this.getDataStorage().computeIfAbsent(PersistentRaid.getType(this.dimensionTypeRegistration()));
        if (!minecraftserver.isSingleplayer()) {
            iworlddataserver.setGameType(minecraftserver.getDefaultGameType());
        }

        long l = minecraftserver.getWorldData().worldGenOptions().seed();

        this.structureCheck = new StructureCheck(this.chunkSource.chunkScanner(), this.registryAccess(), minecraftserver.getStructureManager(), resourcekey, chunkgenerator, this.chunkSource.randomState(), this, chunkgenerator.getBiomeSource(), l, datafixer);
        this.structureManager = new StructureManager(this, this.serverLevelData.worldGenOptions(), structureCheck); // CraftBukkit
        if ((this.dimension() == World.END && this.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)) || env == org.bukkit.World.Environment.THE_END) { // CraftBukkit - Allow to create EnderDragonBattle in default and custom END
            this.dragonFight = new EnderDragonBattle(this, this.serverLevelData.worldGenOptions().seed(), this.serverLevelData.endDragonFightData()); // CraftBukkit
        } else {
            this.dragonFight = null;
        }

        this.sleepStatus = new SleepStatus();
        this.gameEventDispatcher = new GameEventDispatcher(this);
        this.randomSequences = (RandomSequences) Objects.requireNonNullElseGet(randomsequences, () -> {
            return (RandomSequences) this.getDataStorage().computeIfAbsent(RandomSequences.TYPE);
        });
        this.getCraftServer().addWorld(this.getWorld()); // CraftBukkit
    }

    /** @deprecated */
    @Deprecated
    @VisibleForTesting
    public void setDragonFight(@Nullable EnderDragonBattle enderdragonbattle) {
        this.dragonFight = enderdragonbattle;
    }

    public void setWeatherParameters(int i, int j, boolean flag, boolean flag1) {
        this.serverLevelData.setClearWeatherTime(i);
        this.serverLevelData.setRainTime(j);
        this.serverLevelData.setThunderTime(j);
        this.serverLevelData.setRaining(flag);
        this.serverLevelData.setThundering(flag1);
    }

    @Override
    public Holder<BiomeBase> getUncachedNoiseBiome(int i, int j, int k) {
        return this.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(i, j, k, this.getChunkSource().randomState().sampler());
    }

    public StructureManager structureManager() {
        return this.structureManager;
    }

    public void tick(BooleanSupplier booleansupplier) {
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        this.handlingTick = true;
        TickRateManager tickratemanager = this.tickRateManager();
        boolean flag = tickratemanager.runsNormally();

        if (flag) {
            gameprofilerfiller.push("world border");
            this.getWorldBorder().tick();
            gameprofilerfiller.popPush("weather");
            this.advanceWeatherCycle();
            gameprofilerfiller.pop();
        }

        int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);

        if (this.sleepStatus.areEnoughSleeping(i) && this.sleepStatus.areEnoughDeepSleeping(i, this.players)) {
            TimeSkipEvent event = null; // CraftBukkit
            if (this.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                long j = this.levelData.getDayTime() + 24000L;

                // CraftBukkit start
                event = new TimeSkipEvent(this.getWorld(), TimeSkipEvent.SkipReason.NIGHT_SKIP, (j - j % 24000L) - this.getDayTime());
                getCraftServer().getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    this.setDayTime(this.getDayTime() + event.getSkipAmount());
                }
            }

            if (event == null || !event.isCancelled()) {
                this.wakeUpAllPlayers();
            }
            // CraftBukkit end
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && this.isRaining()) {
                this.resetWeatherCycle();
            }
        }

        this.updateSkyBrightness();
        if (flag) {
            this.tickTime();
        }

        gameprofilerfiller.push("tickPending");
        if (!this.isDebug() && flag) {
            long k = this.getGameTime();

            gameprofilerfiller.push("blockTicks");
            this.blockTicks.tick(k, 65536, this::tickBlock);
            gameprofilerfiller.popPush("fluidTicks");
            this.fluidTicks.tick(k, 65536, this::tickFluid);
            gameprofilerfiller.pop();
        }

        gameprofilerfiller.popPush("raid");
        if (flag) {
            this.raids.tick(this);
        }

        gameprofilerfiller.popPush("chunkSource");
        this.getChunkSource().tick(booleansupplier, true);
        gameprofilerfiller.popPush("blockEvents");
        if (flag) {
            this.runBlockEvents();
        }

        this.handlingTick = false;
        gameprofilerfiller.pop();
        boolean flag1 = true || !this.players.isEmpty() || !this.getForceLoadedChunks().isEmpty(); // CraftBukkit - this prevents entity cleanup, other issues on servers with no players

        if (flag1) {
            this.resetEmptyTime();
        }

        if (flag1 || this.emptyTime++ < 300) {
            gameprofilerfiller.push("entities");
            if (this.dragonFight != null && flag) {
                gameprofilerfiller.push("dragonFight");
                this.dragonFight.tick();
                gameprofilerfiller.pop();
            }

            this.entityTickList.forEach((entity) -> {
                if (!entity.isRemoved()) {
                    if (!tickratemanager.isEntityFrozen(entity)) {
                        gameprofilerfiller.push("checkDespawn");
                        entity.checkDespawn();
                        gameprofilerfiller.pop();
                        if (entity instanceof EntityPlayer || this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(entity.chunkPosition().toLong())) {
                            Entity entity1 = entity.getVehicle();

                            if (entity1 != null) {
                                if (!entity1.isRemoved() && entity1.hasPassenger(entity)) {
                                    return;
                                }

                                entity.stopRiding();
                            }

                            gameprofilerfiller.push("tick");
                            this.guardEntityTick(this::tickNonPassenger, entity);
                            gameprofilerfiller.pop();
                        }
                    }
                }
            });
            gameprofilerfiller.pop();
            this.tickBlockEntities();
        }

        gameprofilerfiller.push("entityManagement");
        this.entityManager.tick();
        gameprofilerfiller.pop();
    }

    @Override
    public boolean shouldTickBlocksAt(long i) {
        return this.chunkSource.chunkMap.getDistanceManager().inBlockTickingRange(i);
    }

    protected void tickTime() {
        if (this.tickTime) {
            long i = this.levelData.getGameTime() + 1L;

            this.serverLevelData.setGameTime(i);
            Profiler.get().push("scheduledFunctions");
            this.serverLevelData.getScheduledEvents().tick(this.server, i);
            Profiler.get().pop();
            if (this.serverLevelData.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
                this.setDayTime(this.levelData.getDayTime() + 1L);
            }

        }
    }

    public void setDayTime(long i) {
        this.serverLevelData.setDayTime(i);
    }

    public void tickCustomSpawners(boolean flag, boolean flag1) {
        for (MobSpawner mobspawner : this.customSpawners) {
            mobspawner.tick(this, flag, flag1);
        }

    }

    private void wakeUpAllPlayers() {
        this.sleepStatus.removeAllSleepers();
        (this.players.stream().filter(EntityLiving::isSleeping).collect(Collectors.toList())).forEach((entityplayer) -> { // CraftBukkit - decompile error
            entityplayer.stopSleepInBed(false, false);
        });
    }

    public void tickChunk(Chunk chunk, int i) {
        ChunkCoordIntPair chunkcoordintpair = chunk.getPos();
        int j = chunkcoordintpair.getMinBlockX();
        int k = chunkcoordintpair.getMinBlockZ();
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("iceandsnow");

        for (int l = 0; l < i; ++l) {
            if (this.random.nextInt(48) == 0) {
                this.tickPrecipitation(this.getBlockRandomPos(j, 0, k, 15));
            }
        }

        gameprofilerfiller.popPush("tickBlocks");
        if (i > 0) {
            ChunkSection[] achunksection = chunk.getSections();

            for (int i1 = 0; i1 < achunksection.length; ++i1) {
                ChunkSection chunksection = achunksection[i1];

                if (chunksection.isRandomlyTicking()) {
                    int j1 = chunk.getSectionYFromSectionIndex(i1);
                    int k1 = SectionPosition.sectionToBlockCoord(j1);

                    for (int l1 = 0; l1 < i; ++l1) {
                        BlockPosition blockposition = this.getBlockRandomPos(j, k1, k, 15);

                        gameprofilerfiller.push("randomTick");
                        IBlockData iblockdata = chunksection.getBlockState(blockposition.getX() - j, blockposition.getY() - k1, blockposition.getZ() - k);

                        if (iblockdata.isRandomlyTicking()) {
                            iblockdata.randomTick(this, blockposition, this.random);
                        }

                        Fluid fluid = iblockdata.getFluidState();

                        if (fluid.isRandomlyTicking()) {
                            fluid.randomTick(this, blockposition, this.random);
                        }

                        gameprofilerfiller.pop();
                    }
                }
            }
        }

        gameprofilerfiller.pop();
    }

    public void tickThunder(Chunk chunk) {
        ChunkCoordIntPair chunkcoordintpair = chunk.getPos();
        boolean flag = this.isRaining();
        int i = chunkcoordintpair.getMinBlockX();
        int j = chunkcoordintpair.getMinBlockZ();
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("thunder");
        if (flag && this.isThundering() && this.random.nextInt(100000) == 0) {
            BlockPosition blockposition = this.findLightningTargetAround(this.getBlockRandomPos(i, 0, j, 15));

            if (this.isRainingAt(blockposition)) {
                DifficultyDamageScaler difficultydamagescaler = this.getCurrentDifficultyAt(blockposition);
                boolean flag1 = this.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && this.random.nextDouble() < (double) difficultydamagescaler.getEffectiveDifficulty() * 0.01D && !this.getBlockState(blockposition.below()).is(Blocks.LIGHTNING_ROD);

                if (flag1) {
                    EntityHorseSkeleton entityhorseskeleton = EntityTypes.SKELETON_HORSE.create(this, EntitySpawnReason.EVENT);

                    if (entityhorseskeleton != null) {
                        entityhorseskeleton.setTrap(true);
                        entityhorseskeleton.setAge(0);
                        entityhorseskeleton.setPos((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
                        this.addFreshEntity(entityhorseskeleton, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.LIGHTNING); // CraftBukkit
                    }
                }

                EntityLightning entitylightning = EntityTypes.LIGHTNING_BOLT.create(this, EntitySpawnReason.EVENT);

                if (entitylightning != null) {
                    entitylightning.snapTo(Vec3D.atBottomCenterOf(blockposition));
                    entitylightning.setVisualOnly(flag1);
                    this.strikeLightning(entitylightning, org.bukkit.event.weather.LightningStrikeEvent.Cause.WEATHER); // CraftBukkit
                }
            }
        }

        gameprofilerfiller.pop();
    }

    @VisibleForTesting
    public void tickPrecipitation(BlockPosition blockposition) {
        BlockPosition blockposition1 = this.getHeightmapPos(HeightMap.Type.MOTION_BLOCKING, blockposition);
        BlockPosition blockposition2 = blockposition1.below();
        BiomeBase biomebase = (BiomeBase) this.getBiome(blockposition1).value();

        if (biomebase.shouldFreeze(this, blockposition2)) {
            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition2, Blocks.ICE.defaultBlockState(), null); // CraftBukkit
        }

        if (this.isRaining()) {
            int i = this.getGameRules().getInt(GameRules.RULE_SNOW_ACCUMULATION_HEIGHT);

            if (i > 0 && biomebase.shouldSnow(this, blockposition1)) {
                IBlockData iblockdata = this.getBlockState(blockposition1);

                if (iblockdata.is(Blocks.SNOW)) {
                    int j = (Integer) iblockdata.getValue(BlockSnow.LAYERS);

                    if (j < Math.min(i, 8)) {
                        IBlockData iblockdata1 = (IBlockData) iblockdata.setValue(BlockSnow.LAYERS, j + 1);

                        Block.pushEntitiesUp(iblockdata, iblockdata1, this, blockposition1);
                        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition1, iblockdata1, null); // CraftBukkit
                    }
                } else {
                    org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(this, blockposition1, Blocks.SNOW.defaultBlockState(), null); // CraftBukkit
                }
            }

            BiomeBase.Precipitation biomebase_precipitation = biomebase.getPrecipitationAt(blockposition2, this.getSeaLevel());

            if (biomebase_precipitation != BiomeBase.Precipitation.NONE) {
                IBlockData iblockdata2 = this.getBlockState(blockposition2);

                iblockdata2.getBlock().handlePrecipitation(iblockdata2, this, blockposition2, biomebase_precipitation);
            }
        }

    }

    private Optional<BlockPosition> findLightningRod(BlockPosition blockposition) {
        Optional<BlockPosition> optional = this.getPoiManager().findClosest((holder) -> {
            return holder.is(PoiTypes.LIGHTNING_ROD);
        }, (blockposition1) -> {
            return blockposition1.getY() == this.getHeight(HeightMap.Type.WORLD_SURFACE, blockposition1.getX(), blockposition1.getZ()) - 1;
        }, blockposition, 128, VillagePlace.Occupancy.ANY);

        return optional.map((blockposition1) -> {
            return blockposition1.above(1);
        });
    }

    protected BlockPosition findLightningTargetAround(BlockPosition blockposition) {
        BlockPosition blockposition1 = this.getHeightmapPos(HeightMap.Type.MOTION_BLOCKING, blockposition);
        Optional<BlockPosition> optional = this.findLightningRod(blockposition1);

        if (optional.isPresent()) {
            return (BlockPosition) optional.get();
        } else {
            AxisAlignedBB axisalignedbb = AxisAlignedBB.encapsulatingFullBlocks(blockposition1, blockposition1.atY(this.getMaxY() + 1)).inflate(3.0D);
            List<EntityLiving> list = this.<EntityLiving>getEntitiesOfClass(EntityLiving.class, axisalignedbb, (entityliving) -> {
                return entityliving != null && entityliving.isAlive() && this.canSeeSky(entityliving.blockPosition());
            });

            if (!list.isEmpty()) {
                return ((EntityLiving) list.get(this.random.nextInt(list.size()))).blockPosition();
            } else {
                if (blockposition1.getY() == this.getMinY() - 1) {
                    blockposition1 = blockposition1.above(2);
                }

                return blockposition1;
            }
        }
    }

    public boolean isHandlingTick() {
        return this.handlingTick;
    }

    public boolean canSleepThroughNights() {
        return this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) <= 100;
    }

    private void announceSleepStatus() {
        if (this.canSleepThroughNights()) {
            if (!this.getServer().isSingleplayer() || this.getServer().isPublished()) {
                int i = this.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
                IChatBaseComponent ichatbasecomponent;

                if (this.sleepStatus.areEnoughSleeping(i)) {
                    ichatbasecomponent = IChatBaseComponent.translatable("sleep.skipping_night");
                } else {
                    ichatbasecomponent = IChatBaseComponent.translatable("sleep.players_sleeping", this.sleepStatus.amountSleeping(), this.sleepStatus.sleepersNeeded(i));
                }

                for (EntityPlayer entityplayer : this.players) {
                    entityplayer.displayClientMessage(ichatbasecomponent, true);
                }

            }
        }
    }

    public void updateSleepingPlayerList() {
        if (!this.players.isEmpty() && this.sleepStatus.update(this.players)) {
            this.announceSleepStatus();
        }

    }

    @Override
    public ScoreboardServer getScoreboard() {
        return this.server.getScoreboard();
    }

    private void advanceWeatherCycle() {
        boolean flag = this.isRaining();

        if (this.dimensionType().hasSkyLight()) {
            if (this.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)) {
                int i = this.serverLevelData.getClearWeatherTime();
                int j = this.serverLevelData.getThunderTime();
                int k = this.serverLevelData.getRainTime();
                boolean flag1 = this.levelData.isThundering();
                boolean flag2 = this.levelData.isRaining();

                if (i > 0) {
                    --i;
                    j = flag1 ? 0 : 1;
                    k = flag2 ? 0 : 1;
                    flag1 = false;
                    flag2 = false;
                } else {
                    if (j > 0) {
                        --j;
                        if (j == 0) {
                            flag1 = !flag1;
                        }
                    } else if (flag1) {
                        j = WorldServer.THUNDER_DURATION.sample(this.random);
                    } else {
                        j = WorldServer.THUNDER_DELAY.sample(this.random);
                    }

                    if (k > 0) {
                        --k;
                        if (k == 0) {
                            flag2 = !flag2;
                        }
                    } else if (flag2) {
                        k = WorldServer.RAIN_DURATION.sample(this.random);
                    } else {
                        k = WorldServer.RAIN_DELAY.sample(this.random);
                    }
                }

                this.serverLevelData.setThunderTime(j);
                this.serverLevelData.setRainTime(k);
                this.serverLevelData.setClearWeatherTime(i);
                this.serverLevelData.setThundering(flag1);
                this.serverLevelData.setRaining(flag2);
            }

            this.oThunderLevel = this.thunderLevel;
            if (this.levelData.isThundering()) {
                this.thunderLevel += 0.01F;
            } else {
                this.thunderLevel -= 0.01F;
            }

            this.thunderLevel = MathHelper.clamp(this.thunderLevel, 0.0F, 1.0F);
            this.oRainLevel = this.rainLevel;
            if (this.levelData.isRaining()) {
                this.rainLevel += 0.01F;
            } else {
                this.rainLevel -= 0.01F;
            }

            this.rainLevel = MathHelper.clamp(this.rainLevel, 0.0F, 1.0F);
        }

        /* CraftBukkit start
        if (this.oRainLevel != this.rainLevel) {
            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, this.rainLevel), this.dimension());
        }

        if (this.oThunderLevel != this.thunderLevel) {
            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, this.thunderLevel), this.dimension());
        }

        if (flag != this.isRaining()) {
            if (flag) {
                this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.STOP_RAINING, 0.0F));
            } else {
                this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.START_RAINING, 0.0F));
            }

            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, this.rainLevel));
            this.server.getPlayerList().broadcastAll(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, this.thunderLevel));
        }
        // */
        for (int idx = 0; idx < this.players.size(); ++idx) {
            if (((EntityPlayer) this.players.get(idx)).level() == this) {
                ((EntityPlayer) this.players.get(idx)).tickWeather();
            }
        }

        if (flag != this.isRaining()) {
            // Only send weather packets to those affected
            for (int idx = 0; idx < this.players.size(); ++idx) {
                if (((EntityPlayer) this.players.get(idx)).level() == this) {
                    ((EntityPlayer) this.players.get(idx)).setPlayerWeather((!flag ? WeatherType.DOWNFALL : WeatherType.CLEAR), false);
                }
            }
        }
        for (int idx = 0; idx < this.players.size(); ++idx) {
            if (((EntityPlayer) this.players.get(idx)).level() == this) {
                ((EntityPlayer) this.players.get(idx)).updateWeather(this.oRainLevel, this.rainLevel, this.oThunderLevel, this.thunderLevel);
            }
        }
        // CraftBukkit end

    }

    @VisibleForTesting
    public void resetWeatherCycle() {
        // CraftBukkit start
        this.serverLevelData.setRaining(false);
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isRaining()) {
            this.serverLevelData.setRainTime(0);
        }
        // CraftBukkit end
        this.serverLevelData.setThundering(false);
        // CraftBukkit start
        // If we stop due to everyone sleeping we should reset the weather duration to some other random value.
        // Not that everyone ever manages to get the whole server to sleep at the same time....
        if (!this.serverLevelData.isThundering()) {
            this.serverLevelData.setThunderTime(0);
        }
        // CraftBukkit end
    }

    public void resetEmptyTime() {
        this.emptyTime = 0;
    }

    private void tickFluid(BlockPosition blockposition, FluidType fluidtype) {
        IBlockData iblockdata = this.getBlockState(blockposition);
        Fluid fluid = iblockdata.getFluidState();

        if (fluid.is(fluidtype)) {
            fluid.tick(this, blockposition, iblockdata);
        }

    }

    private void tickBlock(BlockPosition blockposition, Block block) {
        IBlockData iblockdata = this.getBlockState(blockposition);

        if (iblockdata.is(block)) {
            iblockdata.tick(this, blockposition, this.random);
        }

    }

    public void tickNonPassenger(Entity entity) {
        entity.setOldPosAndRot();
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        ++entity.tickCount;
        gameprofilerfiller.push(() -> {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        });
        gameprofilerfiller.incrementCounter("tickNonPassenger");
        entity.tick();
        entity.postTick(); // CraftBukkit
        gameprofilerfiller.pop();

        for (Entity entity1 : entity.getPassengers()) {
            this.tickPassenger(entity, entity1);
        }

    }

    private void tickPassenger(Entity entity, Entity entity1) {
        if (!entity1.isRemoved() && entity1.getVehicle() == entity) {
            if (entity1 instanceof EntityHuman || this.entityTickList.contains(entity1)) {
                entity1.setOldPosAndRot();
                ++entity1.tickCount;
                GameProfilerFiller gameprofilerfiller = Profiler.get();

                gameprofilerfiller.push(() -> {
                    return BuiltInRegistries.ENTITY_TYPE.getKey(entity1.getType()).toString();
                });
                gameprofilerfiller.incrementCounter("tickPassenger");
                entity1.rideTick();
                entity1.postTick(); // CraftBukkit
                gameprofilerfiller.pop();

                for (Entity entity2 : entity1.getPassengers()) {
                    this.tickPassenger(entity1, entity2);
                }

            }
        } else {
            entity1.stopRiding();
        }
    }

    @Override
    public boolean mayInteract(Entity entity, BlockPosition blockposition) {
        boolean flag;

        if (entity instanceof EntityHuman entityhuman) {
            if (this.server.isUnderSpawnProtection(this, blockposition, entityhuman) || !this.getWorldBorder().isWithinBounds(blockposition)) {
                flag = false;
                return flag;
            }
        }

        flag = true;
        return flag;
    }

    public void save(@Nullable IProgressUpdate iprogressupdate, boolean flag, boolean flag1) {
        ChunkProviderServer chunkproviderserver = this.getChunkSource();

        if (!flag1) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new org.bukkit.event.world.WorldSaveEvent(getWorld())); // CraftBukkit
            if (iprogressupdate != null) {
                iprogressupdate.progressStartNoAbort(IChatBaseComponent.translatable("menu.savingLevel"));
            }

            this.saveLevelData(flag);
            if (iprogressupdate != null) {
                iprogressupdate.progressStage(IChatBaseComponent.translatable("menu.savingChunks"));
            }

            chunkproviderserver.save(flag);
            if (flag) {
                this.entityManager.saveAll();
            } else {
                this.entityManager.autoSave();
            }

        }

        // CraftBukkit start - moved from MinecraftServer.saveChunks
        WorldServer worldserver1 = this;

        serverLevelData.setWorldBorder(worldserver1.getWorldBorder().createSettings());
        serverLevelData.setCustomBossEvents(this.server.getCustomBossEvents().save(this.registryAccess()));
        convertable.saveDataTag(this.server.registryAccess(), this.serverLevelData, this.server.getPlayerList().getSingleplayerData());
        // CraftBukkit end
    }

    private void saveLevelData(boolean flag) {
        if (this.dragonFight != null) {
            this.serverLevelData.setEndDragonFightData(this.dragonFight.saveData()); // CraftBukkit
        }

        WorldPersistentData worldpersistentdata = this.getChunkSource().getDataStorage();

        if (flag) {
            worldpersistentdata.saveAndJoin();
        } else {
            worldpersistentdata.scheduleSave();
        }

    }

    public <T extends Entity> List<? extends T> getEntities(EntityTypeTest<Entity, T> entitytypetest, Predicate<? super T> predicate) {
        List<T> list = Lists.newArrayList();

        this.getEntities(entitytypetest, predicate, list);
        return list;
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> entitytypetest, Predicate<? super T> predicate, List<? super T> list) {
        this.getEntities(entitytypetest, predicate, list, Integer.MAX_VALUE);
    }

    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> entitytypetest, Predicate<? super T> predicate, List<? super T> list, int i) {
        this.getEntities().get(entitytypetest, (entity) -> {
            if (predicate.test(entity)) {
                list.add(entity);
                if (list.size() >= i) {
                    return AbortableIterationConsumer.a.ABORT;
                }
            }

            return AbortableIterationConsumer.a.CONTINUE;
        });
    }

    public List<? extends EntityEnderDragon> getDragons() {
        return this.getEntities(EntityTypes.ENDER_DRAGON, EntityLiving::isAlive);
    }

    public List<EntityPlayer> getPlayers(Predicate<? super EntityPlayer> predicate) {
        return this.getPlayers(predicate, Integer.MAX_VALUE);
    }

    public List<EntityPlayer> getPlayers(Predicate<? super EntityPlayer> predicate, int i) {
        List<EntityPlayer> list = Lists.newArrayList();

        for (EntityPlayer entityplayer : this.players) {
            if (predicate.test(entityplayer)) {
                list.add(entityplayer);
                if (list.size() >= i) {
                    return list;
                }
            }
        }

        return list;
    }

    @Nullable
    public EntityPlayer getRandomPlayer() {
        List<EntityPlayer> list = this.getPlayers(EntityLiving::isAlive);

        return list.isEmpty() ? null : (EntityPlayer) list.get(this.random.nextInt(list.size()));
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        // CraftBukkit start
        return this.addFreshEntity(entity, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Override
    public boolean addFreshEntity(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public boolean addWithUUID(Entity entity) {
        // CraftBukkit start
        return this.addWithUUID(entity, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean addWithUUID(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        return this.addEntity(entity, reason);
        // CraftBukkit end
    }

    public void addDuringTeleport(Entity entity) {
        // CraftBukkit start
        // SPIGOT-6415: Don't call spawn event for entities which travel trough worlds,
        // since it is only an implementation detail, that a new entity is created when
        // they are traveling between worlds.
        this.addDuringTeleport(entity, null);
    }

    public void addDuringTeleport(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        // CraftBukkit end
        if (entity instanceof EntityPlayer entityplayer) {
            this.addPlayer(entityplayer);
        } else {
            this.addEntity(entity, reason); // CraftBukkit
        }

    }

    public void addNewPlayer(EntityPlayer entityplayer) {
        this.addPlayer(entityplayer);
    }

    public void addRespawnedPlayer(EntityPlayer entityplayer) {
        this.addPlayer(entityplayer);
    }

    private void addPlayer(EntityPlayer entityplayer) {
        Entity entity = this.getEntity(entityplayer.getUUID());

        if (entity != null) {
            WorldServer.LOGGER.warn("Force-added player with duplicate UUID {}", entityplayer.getUUID());
            entity.unRide();
            this.removePlayerImmediately((EntityPlayer) entity, Entity.RemovalReason.DISCARDED);
        }

        this.entityManager.addNewEntity(entityplayer);
    }

    // CraftBukkit start
    private boolean addEntity(Entity entity, CreatureSpawnEvent.SpawnReason spawnReason) {
        if (entity.isRemoved()) {
            // WorldServer.LOGGER.warn("Tried to add entity {} but it was marked as removed already", EntityTypes.getKey(entity.getType())); // CraftBukkit
            return false;
        } else {
            // SPIGOT-6415: Don't call spawn event when reason is null. For example when an entity teleports to a new world.
            if (spawnReason != null && !CraftEventFactory.doEntityAddEventCalling(this, entity, spawnReason)) {
                return false;
            }
            // CraftBukkit end

            return this.entityManager.addNewEntity(entity);
        }
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity) {
        // CraftBukkit start
        return this.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public boolean tryAddFreshEntityWithPassengers(Entity entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        // CraftBukkit end
        Stream<UUID> stream = entity.getSelfAndPassengers().map(Entity::getUUID); // CraftBukkit - decompile error
        PersistentEntitySectionManager persistententitysectionmanager = this.entityManager;

        Objects.requireNonNull(this.entityManager);
        if (stream.anyMatch(persistententitysectionmanager::isLoaded)) {
            return false;
        } else {
            this.addFreshEntityWithPassengers(entity, reason); // CraftBukkit
            return true;
        }
    }

    public void unload(Chunk chunk) {
        chunk.clearAllBlockEntities();
        chunk.unregisterTickContainerFromLevel(this);
    }

    public void removePlayerImmediately(EntityPlayer entityplayer, Entity.RemovalReason entity_removalreason) {
        entityplayer.remove(entity_removalreason, null); // CraftBukkit - add Bukkit remove cause
    }

    // CraftBukkit start
    public boolean strikeLightning(Entity entitylightning) {
        return this.strikeLightning(entitylightning, LightningStrikeEvent.Cause.UNKNOWN);
    }

    public boolean strikeLightning(Entity entitylightning, LightningStrikeEvent.Cause cause) {
        LightningStrikeEvent lightning = CraftEventFactory.callLightningStrikeEvent((org.bukkit.entity.LightningStrike) entitylightning.getBukkitEntity(), cause);

        if (lightning.isCancelled()) {
            return false;
        }

        return this.addFreshEntity(entitylightning);
    }
    // CraftBukkit end

    @Override
    public void destroyBlockProgress(int i, BlockPosition blockposition, int j) {
        // CraftBukkit start
        EntityHuman entityhuman = null;
        Entity entity = this.getEntity(i);
        if (entity instanceof EntityHuman) entityhuman = (EntityHuman) entity;
        // CraftBukkit end

        for (EntityPlayer entityplayer : this.server.getPlayerList().getPlayers()) {
            if (entityplayer != null && entityplayer.level() == this && entityplayer.getId() != i) {
                double d0 = (double) blockposition.getX() - entityplayer.getX();
                double d1 = (double) blockposition.getY() - entityplayer.getY();
                double d2 = (double) blockposition.getZ() - entityplayer.getZ();

                // CraftBukkit start
                if (entityhuman != null && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
                    continue;
                }
                // CraftBukkit end

                if (d0 * d0 + d1 * d1 + d2 * d2 < 1024.0D) {
                    entityplayer.connection.send(new PacketPlayOutBlockBreakAnimation(i, blockposition, j));
                }
            }
        }

    }

    @Override
    public void playSeededSound(@Nullable Entity entity, double d0, double d1, double d2, Holder<SoundEffect> holder, SoundCategory soundcategory, float f, float f1, long i) {
        PlayerList playerlist = this.server.getPlayerList();
        EntityHuman entityhuman;

        if (entity instanceof EntityHuman entityhuman1) {
            entityhuman = entityhuman1;
        } else {
            entityhuman = null;
        }

        playerlist.broadcast(entityhuman, d0, d1, d2, (double) ((SoundEffect) holder.value()).getRange(f), this.dimension(), new PacketPlayOutNamedSoundEffect(holder, soundcategory, d0, d1, d2, f, f1, i));
    }

    @Override
    public void playSeededSound(@Nullable Entity entity, Entity entity1, Holder<SoundEffect> holder, SoundCategory soundcategory, float f, float f1, long i) {
        PlayerList playerlist = this.server.getPlayerList();
        EntityHuman entityhuman;

        if (entity instanceof EntityHuman entityhuman1) {
            entityhuman = entityhuman1;
        } else {
            entityhuman = null;
        }

        playerlist.broadcast(entityhuman, entity1.getX(), entity1.getY(), entity1.getZ(), (double) ((SoundEffect) holder.value()).getRange(f), this.dimension(), new PacketPlayOutEntitySound(holder, soundcategory, entity1, f, f1, i));
    }

    @Override
    public void globalLevelEvent(int i, BlockPosition blockposition, int j) {
        if (this.getGameRules().getBoolean(GameRules.RULE_GLOBAL_SOUND_EVENTS)) {
            this.server.getPlayerList().getPlayers().forEach((entityplayer) -> {
                Vec3D vec3d;

                if (entityplayer.level() == this) {
                    Vec3D vec3d1 = Vec3D.atCenterOf(blockposition);

                    if (entityplayer.distanceToSqr(vec3d1) < (double) MathHelper.square(32)) {
                        vec3d = vec3d1;
                    } else {
                        Vec3D vec3d2 = vec3d1.subtract(entityplayer.position()).normalize();

                        vec3d = entityplayer.position().add(vec3d2.scale(32.0D));
                    }
                } else {
                    vec3d = entityplayer.position();
                }

                entityplayer.connection.send(new PacketPlayOutWorldEvent(i, BlockPosition.containing(vec3d), j, true));
            });
        } else {
            this.levelEvent((Entity) null, i, blockposition, j);
        }

    }

    @Override
    public void levelEvent(@Nullable Entity entity, int i, BlockPosition blockposition, int j) {
        PlayerList playerlist = this.server.getPlayerList();
        EntityHuman entityhuman;

        if (entity instanceof EntityHuman entityhuman1) {
            entityhuman = entityhuman1;
        } else {
            entityhuman = null;
        }

        playerlist.broadcast(entityhuman, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), 64.0D, this.dimension(), new PacketPlayOutWorldEvent(i, blockposition, j, false));
    }

    public int getLogicalHeight() {
        return this.dimensionType().logicalHeight();
    }

    @Override
    public void gameEvent(Holder<GameEvent> holder, Vec3D vec3d, GameEvent.a gameevent_a) {
        this.gameEventDispatcher.post(holder, vec3d, gameevent_a);
    }

    @Override
    public void sendBlockUpdated(BlockPosition blockposition, IBlockData iblockdata, IBlockData iblockdata1, int i) {
        if (this.isUpdatingNavigations) {
            String s = "recursive call to sendBlockUpdated";

            SystemUtils.logAndPauseIfInIde("recursive call to sendBlockUpdated", new IllegalStateException("recursive call to sendBlockUpdated"));
        }

        this.getChunkSource().blockChanged(blockposition);
        this.pathTypesByPosCache.invalidate(blockposition);
        VoxelShape voxelshape = iblockdata.getCollisionShape(this, blockposition);
        VoxelShape voxelshape1 = iblockdata1.getCollisionShape(this, blockposition);

        if (VoxelShapes.joinIsNotEmpty(voxelshape, voxelshape1, OperatorBoolean.NOT_SAME)) {
            List<NavigationAbstract> list = new ObjectArrayList();
            // CraftBukkit start - fix SPIGOT-6362
            java.util.Iterator<EntityInsentient> iterator = this.navigatingMobs.iterator();

            while (iterator.hasNext()) {
                EntityInsentient entityinsentient;
                try {
                    entityinsentient = iterator.next();
                } catch (java.util.ConcurrentModificationException ex) {
                    // This can happen because the pathfinder update below may trigger a chunk load, which in turn may cause more navigators to register
                    // In this case we just run the update again across all the iterators as the chunk will then be loaded
                    // As this is a relative edge case it is much faster than copying navigators (on either read or write)
                    sendBlockUpdated(blockposition, iblockdata, iblockdata1, i);
                    return;
                }
                // CraftBukkit end
                NavigationAbstract navigationabstract = entityinsentient.getNavigation();

                if (navigationabstract.shouldRecomputePath(blockposition)) {
                    list.add(navigationabstract);
                }
            }

            try {
                this.isUpdatingNavigations = true;

                for (NavigationAbstract navigationabstract1 : list) {
                    navigationabstract1.recomputePath();
                }
            } finally {
                this.isUpdatingNavigations = false;
            }

        }
    }

    @Override
    public void updateNeighborsAt(BlockPosition blockposition, Block block) {
        this.updateNeighborsAt(blockposition, block, ExperimentalRedstoneUtils.initialOrientation(this, (EnumDirection) null, (EnumDirection) null));
    }

    @Override
    public void updateNeighborsAt(BlockPosition blockposition, Block block, @Nullable Orientation orientation) {
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(blockposition, block, (EnumDirection) null, orientation);
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPosition blockposition, Block block, EnumDirection enumdirection, @Nullable Orientation orientation) {
        this.neighborUpdater.updateNeighborsAtExceptFromFacing(blockposition, block, enumdirection, orientation);
    }

    @Override
    public void neighborChanged(BlockPosition blockposition, Block block, @Nullable Orientation orientation) {
        this.neighborUpdater.neighborChanged(blockposition, block, orientation);
    }

    @Override
    public void neighborChanged(IBlockData iblockdata, BlockPosition blockposition, Block block, @Nullable Orientation orientation, boolean flag) {
        this.neighborUpdater.neighborChanged(iblockdata, blockposition, block, orientation, flag);
    }

    @Override
    public void broadcastEntityEvent(Entity entity, byte b0) {
        this.getChunkSource().broadcastAndSend(entity, new PacketPlayOutEntityStatus(entity, b0));
    }

    @Override
    public void broadcastDamageEvent(Entity entity, DamageSource damagesource) {
        this.getChunkSource().broadcastAndSend(entity, new ClientboundDamageEventPacket(entity, damagesource));
    }

    @Override
    public ChunkProviderServer getChunkSource() {
        return this.chunkSource;
    }

    @Override
    public void explode(@Nullable Entity entity, @Nullable DamageSource damagesource, @Nullable ExplosionDamageCalculator explosiondamagecalculator, double d0, double d1, double d2, float f, boolean flag, World.a world_a, ParticleParam particleparam, ParticleParam particleparam1, Holder<SoundEffect> holder) {
        // CraftBukkit start
        this.explode0(entity, damagesource, explosiondamagecalculator, d0, d1, d2, f, flag, world_a, particleparam, particleparam1, holder);
    }

    public ServerExplosion explode0(@Nullable Entity entity, @Nullable DamageSource damagesource, @Nullable ExplosionDamageCalculator explosiondamagecalculator, double d0, double d1, double d2, float f, boolean flag, World.a world_a, ParticleParam particleparam, ParticleParam particleparam1, Holder<SoundEffect> holder) {
        // CraftBukkit end
        Explosion.Effect explosion_effect;

        switch (world_a) {
            case NONE:
                explosion_effect = Explosion.Effect.KEEP;
                break;
            case BLOCK:
                explosion_effect = this.getDestroyType(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
                break;
            case MOB:
                explosion_effect = this.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) ? this.getDestroyType(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY) : Explosion.Effect.KEEP;
                break;
            case TNT:
                explosion_effect = this.getDestroyType(GameRules.RULE_TNT_EXPLOSION_DROP_DECAY);
                break;
            case TRIGGER:
                explosion_effect = Explosion.Effect.TRIGGER_BLOCK;
                break;
            // CraftBukkit start - handle custom explosion type
            case STANDARD:
                explosion_effect = Explosion.Effect.DESTROY;
                break;
            // CraftBukkit end
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        Explosion.Effect explosion_effect1 = explosion_effect;
        Vec3D vec3d = new Vec3D(d0, d1, d2);
        ServerExplosion serverexplosion = new ServerExplosion(this, entity, damagesource, explosiondamagecalculator, vec3d, f, flag, explosion_effect1);

        serverexplosion.explode();
        // CraftBukkit start
        if (serverexplosion.wasCanceled) {
            return serverexplosion;
        }
        // CraftBukkit end
        ParticleParam particleparam2 = serverexplosion.isSmall() ? particleparam : particleparam1;

        for (EntityPlayer entityplayer : this.players) {
            if (entityplayer.distanceToSqr(vec3d) < 4096.0D) {
                Optional<Vec3D> optional = Optional.ofNullable((Vec3D) serverexplosion.getHitPlayers().get(entityplayer));

                entityplayer.connection.send(new PacketPlayOutExplosion(vec3d, optional, particleparam2, holder));
            }
        }

        return serverexplosion; // CraftBukkit
    }

    private Explosion.Effect getDestroyType(GameRules.GameRuleKey<GameRules.GameRuleBoolean> gamerules_gamerulekey) {
        return this.getGameRules().getBoolean(gamerules_gamerulekey) ? Explosion.Effect.DESTROY_WITH_DECAY : Explosion.Effect.DESTROY;
    }

    @Override
    public void blockEvent(BlockPosition blockposition, Block block, int i, int j) {
        this.blockEvents.add(new BlockActionData(blockposition, block, i, j));
    }

    private void runBlockEvents() {
        this.blockEventsToReschedule.clear();

        while (!this.blockEvents.isEmpty()) {
            BlockActionData blockactiondata = (BlockActionData) this.blockEvents.removeFirst();

            if (this.shouldTickBlocksAt(blockactiondata.pos())) {
                if (this.doBlockEvent(blockactiondata)) {
                    this.server.getPlayerList().broadcast((EntityHuman) null, (double) blockactiondata.pos().getX(), (double) blockactiondata.pos().getY(), (double) blockactiondata.pos().getZ(), 64.0D, this.dimension(), new PacketPlayOutBlockAction(blockactiondata.pos(), blockactiondata.block(), blockactiondata.paramA(), blockactiondata.paramB()));
                }
            } else {
                this.blockEventsToReschedule.add(blockactiondata);
            }
        }

        this.blockEvents.addAll(this.blockEventsToReschedule);
    }

    private boolean doBlockEvent(BlockActionData blockactiondata) {
        IBlockData iblockdata = this.getBlockState(blockactiondata.pos());

        return iblockdata.is(blockactiondata.block()) ? iblockdata.triggerEvent(this, blockactiondata.pos(), blockactiondata.paramA(), blockactiondata.paramB()) : false;
    }

    @Override
    public TickListServer<Block> getBlockTicks() {
        return this.blockTicks;
    }

    @Override
    public TickListServer<FluidType> getFluidTicks() {
        return this.fluidTicks;
    }

    @Nonnull
    @Override
    public MinecraftServer getServer() {
        return this.server;
    }

    public PortalTravelAgent getPortalForcer() {
        return this.portalForcer;
    }

    public StructureTemplateManager getStructureManager() {
        return this.server.getStructureManager();
    }

    public <T extends ParticleParam> int sendParticles(T t0, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6) {
        return this.sendParticlesSource(null, t0, false, false, d0, d1, d2, i, d3, d4, d5, d6); // CraftBukkit - visibility api support
    }

    public <T extends ParticleParam> int sendParticles(T t0, boolean flag, boolean flag1, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6) {
        return this.sendParticlesSource(null, t0, flag, flag1, d0, d1, d2, i, d3, d4, d5, d6); // CraftBukkit - visibility api support
    }

    // CraftBukkit start - visibility api support
    public <T extends ParticleParam> int sendParticlesSource(EntityPlayer sender, T t0, boolean flag, boolean flag1, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6) {
        // CraftBukkit end
        PacketPlayOutWorldParticles packetplayoutworldparticles = new PacketPlayOutWorldParticles(t0, flag, flag1, d0, d1, d2, (float) d3, (float) d4, (float) d5, (float) d6, i);
        int j = 0;

        for (int k = 0; k < this.players.size(); ++k) {
            EntityPlayer entityplayer = (EntityPlayer) this.players.get(k);
            if (sender != null && !entityplayer.getBukkitEntity().canSee(sender.getBukkitEntity())) continue; // CraftBukkit

            if (this.sendParticles(entityplayer, flag, d0, d1, d2, packetplayoutworldparticles)) {
                ++j;
            }
        }

        return j;
    }

    public <T extends ParticleParam> boolean sendParticles(EntityPlayer entityplayer, T t0, boolean flag, boolean flag1, double d0, double d1, double d2, int i, double d3, double d4, double d5, double d6) {
        Packet<?> packet = new PacketPlayOutWorldParticles(t0, flag, flag1, d0, d1, d2, (float) d3, (float) d4, (float) d5, (float) d6, i);

        return this.sendParticles(entityplayer, flag, d0, d1, d2, packet);
    }

    private boolean sendParticles(EntityPlayer entityplayer, boolean flag, double d0, double d1, double d2, Packet<?> packet) {
        if (entityplayer.level() != this) {
            return false;
        } else {
            BlockPosition blockposition = entityplayer.blockPosition();

            if (blockposition.closerToCenterThan(new Vec3D(d0, d1, d2), flag ? 512.0D : 32.0D)) {
                entityplayer.connection.send(packet);
                return true;
            } else {
                return false;
            }
        }
    }

    @Nullable
    @Override
    public Entity getEntity(int i) {
        return (Entity) this.getEntities().get(i);
    }

    /** @deprecated */
    @Deprecated
    @Nullable
    public Entity getEntityOrPart(int i) {
        Entity entity = (Entity) this.getEntities().get(i);

        return entity != null ? entity : (Entity) this.dragonParts.get(i);
    }

    @Override
    public Collection<EntityComplexPart> dragonParts() {
        return this.dragonParts.values();
    }

    @Nullable
    public BlockPosition findNearestMapStructure(TagKey<Structure> tagkey, BlockPosition blockposition, int i, boolean flag) {
        if (!this.serverLevelData.worldGenOptions().generateStructures()) { // CraftBukkit
            return null;
        } else {
            Optional<HolderSet.Named<Structure>> optional = this.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(tagkey);

            if (optional.isEmpty()) {
                return null;
            } else {
                Pair<BlockPosition, Holder<Structure>> pair = this.getChunkSource().getGenerator().findNearestMapStructure(this, (HolderSet) optional.get(), blockposition, i, flag);

                return pair != null ? (BlockPosition) pair.getFirst() : null;
            }
        }
    }

    @Nullable
    public Pair<BlockPosition, Holder<BiomeBase>> findClosestBiome3d(Predicate<Holder<BiomeBase>> predicate, BlockPosition blockposition, int i, int j, int k) {
        return this.getChunkSource().getGenerator().getBiomeSource().findClosestBiome3d(blockposition, i, j, k, predicate, this.getChunkSource().randomState().sampler(), this);
    }

    @Override
    public CraftingManager recipeAccess() {
        return this.server.getRecipeManager();
    }

    @Override
    public TickRateManager tickRateManager() {
        return this.server.tickRateManager();
    }

    @Override
    public boolean noSave() {
        return this.noSave;
    }

    public WorldPersistentData getDataStorage() {
        return this.getChunkSource().getDataStorage();
    }

    @Nullable
    @Override
    public WorldMap getMapData(MapId mapid) {
        // CraftBukkit start
        WorldMap worldmap = (WorldMap) this.getServer().overworld().getDataStorage().get(WorldMap.type(mapid));
        if (worldmap != null) {
            worldmap.id = mapid;
        }
        return worldmap;
        // CraftBukkit end
    }

    public void setMapData(MapId mapid, WorldMap worldmap) {
        // CraftBukkit start
        worldmap.id = mapid;
        MapInitializeEvent event = new MapInitializeEvent(worldmap.mapView);
        Bukkit.getServer().getPluginManager().callEvent(event);
        // CraftBukkit end
        this.getServer().overworld().getDataStorage().set(WorldMap.type(mapid), worldmap);
    }

    public MapId getFreeMapId() {
        return ((PersistentIdCounts) this.getServer().overworld().getDataStorage().computeIfAbsent(PersistentIdCounts.TYPE)).getNextMapId();
    }

    public void setDefaultSpawnPos(BlockPosition blockposition, float f) {
        BlockPosition blockposition1 = this.levelData.getSpawnPos();
        float f1 = this.levelData.getSpawnAngle();

        if (!blockposition1.equals(blockposition) || f1 != f) {
            this.levelData.setSpawn(blockposition, f);
            // CraftBukkit start - Notify anyone who's listening.
            SpawnChangeEvent event = new SpawnChangeEvent(getWorld(), CraftLocation.toBukkit(blockposition, getWorld(), f1, 0.0F));
            getCraftServer().getPluginManager().callEvent(event);
            // CraftBukkit end
            this.getServer().getPlayerList().broadcastAll(new PacketPlayOutSpawnPosition(blockposition, f));
        }

        if (this.lastSpawnChunkRadius > 1) {
            this.getChunkSource().removeTicketWithRadius(TicketType.START, new ChunkCoordIntPair(blockposition1), this.lastSpawnChunkRadius);
        }

        int i = this.getGameRules().getInt(GameRules.RULE_SPAWN_CHUNK_RADIUS) + 1;

        if (i > 1) {
            this.getChunkSource().addTicketWithRadius(TicketType.START, new ChunkCoordIntPair(blockposition), i);
        }

        this.lastSpawnChunkRadius = i;
    }

    public LongSet getForceLoadedChunks() {
        return this.chunkSource.getForceLoadedChunks();
    }

    public boolean setChunkForced(int i, int j, boolean flag) {
        boolean flag1 = this.chunkSource.updateChunkForced(new ChunkCoordIntPair(i, j), flag);

        if (flag && flag1) {
            this.getChunk(i, j);
        }

        return flag1;
    }

    @Override
    public List<EntityPlayer> players() {
        return this.players;
    }

    @Override
    public void updatePOIOnBlockStateChange(BlockPosition blockposition, IBlockData iblockdata, IBlockData iblockdata1) {
        Optional<Holder<VillagePlaceType>> optional = PoiTypes.forState(iblockdata);
        Optional<Holder<VillagePlaceType>> optional1 = PoiTypes.forState(iblockdata1);

        if (!Objects.equals(optional, optional1)) {
            BlockPosition blockposition1 = blockposition.immutable();

            optional.ifPresent((holder) -> {
                this.getServer().execute(() -> {
                    this.getPoiManager().remove(blockposition1);
                    PacketDebug.sendPoiRemovedPacket(this, blockposition1);
                });
            });
            optional1.ifPresent((holder) -> {
                this.getServer().execute(() -> {
                    this.getPoiManager().add(blockposition1, holder);
                    PacketDebug.sendPoiAddedPacket(this, blockposition1);
                });
            });
        }
    }

    public VillagePlace getPoiManager() {
        return this.getChunkSource().getPoiManager();
    }

    public boolean isVillage(BlockPosition blockposition) {
        return this.isCloseToVillage(blockposition, 1);
    }

    public boolean isVillage(SectionPosition sectionposition) {
        return this.isVillage(sectionposition.center());
    }

    public boolean isCloseToVillage(BlockPosition blockposition, int i) {
        return i > 6 ? false : this.sectionsToVillage(SectionPosition.of(blockposition)) <= i;
    }

    public int sectionsToVillage(SectionPosition sectionposition) {
        return this.getPoiManager().sectionsToVillage(sectionposition);
    }

    public PersistentRaid getRaids() {
        return this.raids;
    }

    @Nullable
    public Raid getRaidAt(BlockPosition blockposition) {
        return this.raids.getNearbyRaid(blockposition, 9216);
    }

    public boolean isRaided(BlockPosition blockposition) {
        return this.getRaidAt(blockposition) != null;
    }

    public void onReputationEvent(ReputationEvent reputationevent, Entity entity, ReputationHandler reputationhandler) {
        reputationhandler.onReputationEventFrom(reputationevent, entity);
    }

    public void saveDebugReport(Path path) throws IOException {
        PlayerChunkMap playerchunkmap = this.getChunkSource().chunkMap;

        try (Writer writer = Files.newBufferedWriter(path.resolve("stats.txt"))) {
            writer.write(String.format(Locale.ROOT, "spawning_chunks: %d\n", playerchunkmap.getDistanceManager().getNaturalSpawnChunkCount()));
            SpawnerCreature.d spawnercreature_d = this.getChunkSource().getLastSpawnState();

            if (spawnercreature_d != null) {
                ObjectIterator objectiterator = spawnercreature_d.getMobCategoryCounts().object2IntEntrySet().iterator();

                while (objectiterator.hasNext()) {
                    Object2IntMap.Entry<EnumCreatureType> object2intmap_entry = (Entry) objectiterator.next();

                    writer.write(String.format(Locale.ROOT, "spawn_count.%s: %d\n", ((EnumCreatureType) object2intmap_entry.getKey()).getName(), object2intmap_entry.getIntValue()));
                }
            }

            writer.write(String.format(Locale.ROOT, "entities: %s\n", this.entityManager.gatherStats()));
            writer.write(String.format(Locale.ROOT, "block_entity_tickers: %d\n", this.blockEntityTickers.size()));
            writer.write(String.format(Locale.ROOT, "block_ticks: %d\n", this.getBlockTicks().count()));
            writer.write(String.format(Locale.ROOT, "fluid_ticks: %d\n", this.getFluidTicks().count()));
            writer.write("distance_manager: " + playerchunkmap.getDistanceManager().getDebugStatus() + "\n");
            writer.write(String.format(Locale.ROOT, "pending_tasks: %d\n", this.getChunkSource().getPendingTasksCount()));
        }

        CrashReport crashreport = new CrashReport("Level dump", new Exception("dummy"));

        this.fillReportDetails(crashreport);

        try (Writer writer1 = Files.newBufferedWriter(path.resolve("example_crash.txt"))) {
            writer1.write(crashreport.getFriendlyReport(ReportType.TEST));
        }

        Path path1 = path.resolve("chunks.csv");

        try (Writer writer2 = Files.newBufferedWriter(path1)) {
            playerchunkmap.dumpChunks(writer2);
        }

        Path path2 = path.resolve("entity_chunks.csv");

        try (Writer writer3 = Files.newBufferedWriter(path2)) {
            this.entityManager.dumpSections(writer3);
        }

        Path path3 = path.resolve("entities.csv");

        try (Writer writer4 = Files.newBufferedWriter(path3)) {
            dumpEntities(writer4, this.getEntities().getAll());
        }

        Path path4 = path.resolve("block_entities.csv");

        try (Writer writer5 = Files.newBufferedWriter(path4)) {
            this.dumpBlockEntityTickers(writer5);
        }

    }

    private static void dumpEntities(Writer writer, Iterable<Entity> iterable) throws IOException {
        CSVWriter csvwriter = CSVWriter.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("uuid").addColumn("type").addColumn("alive").addColumn("display_name").addColumn("custom_name").build(writer);

        for (Entity entity : iterable) {
            IChatBaseComponent ichatbasecomponent = entity.getCustomName();
            IChatBaseComponent ichatbasecomponent1 = entity.getDisplayName();

            csvwriter.writeRow(entity.getX(), entity.getY(), entity.getZ(), entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.isAlive(), ichatbasecomponent1.getString(), ichatbasecomponent != null ? ichatbasecomponent.getString() : null);
        }

    }

    private void dumpBlockEntityTickers(Writer writer) throws IOException {
        CSVWriter csvwriter = CSVWriter.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("type").build(writer);

        for (TickingBlockEntity tickingblockentity : this.blockEntityTickers) {
            BlockPosition blockposition = tickingblockentity.getPos();

            csvwriter.writeRow(blockposition.getX(), blockposition.getY(), blockposition.getZ(), tickingblockentity.getType());
        }

    }

    @VisibleForTesting
    public void clearBlockEvents(StructureBoundingBox structureboundingbox) {
        this.blockEvents.removeIf((blockactiondata) -> {
            return structureboundingbox.isInside(blockactiondata.pos());
        });
    }

    @Override
    public float getShade(EnumDirection enumdirection, boolean flag) {
        return 1.0F;
    }

    public Iterable<Entity> getAllEntities() {
        return this.getEntities().getAll();
    }

    public String toString() {
        return "ServerLevel[" + this.serverLevelData.getLevelName() + "]";
    }

    public boolean isFlat() {
        return this.serverLevelData.isFlatWorld(); // CraftBukkit
    }

    @Override
    public long getSeed() {
        return this.serverLevelData.worldGenOptions().seed(); // CraftBukkit
    }

    @Nullable
    public EnderDragonBattle getDragonFight() {
        return this.dragonFight;
    }

    @Override
    public WorldServer getLevel() {
        return this;
    }

    @VisibleForTesting
    public String getWatchdogStats() {
        return String.format(Locale.ROOT, "players: %s, entities: %s [%s], block_entities: %d [%s], block_ticks: %d, fluid_ticks: %d, chunk_source: %s", this.players.size(), this.entityManager.gatherStats(), getTypeCount(this.entityManager.getEntityGetter().getAll(), (entity) -> {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        }), this.blockEntityTickers.size(), getTypeCount(this.blockEntityTickers, TickingBlockEntity::getType), this.getBlockTicks().count(), this.getFluidTicks().count(), this.gatherChunkSourceStats());
    }

    private static <T> String getTypeCount(Iterable<T> iterable, Function<T, String> function) {
        try {
            Object2IntOpenHashMap<String> object2intopenhashmap = new Object2IntOpenHashMap();

            for (T t0 : iterable) {
                String s = (String) function.apply(t0);

                object2intopenhashmap.addTo(s, 1);
            }

            return (String) object2intopenhashmap.object2IntEntrySet().stream().sorted(Comparator.comparing(Entry<String>::getIntValue).reversed()).limit(5L).map((entry) -> { // CraftBukkit - decompile error
                String s1 = (String) entry.getKey();

                return s1 + ":" + entry.getIntValue();
            }).collect(Collectors.joining(","));
        } catch (Exception exception) {
            return "";
        }
    }

    @Override
    public LevelEntityGetter<Entity> getEntities() {
        return this.entityManager.getEntityGetter();
    }

    public void addLegacyChunkEntities(Stream<Entity> stream) {
        this.entityManager.addLegacyChunkEntities(stream);
    }

    public void addWorldGenChunkEntities(Stream<Entity> stream) {
        this.entityManager.addWorldGenChunkEntities(stream);
    }

    public void startTickingChunk(Chunk chunk) {
        chunk.unpackTicks(this.getLevelData().getGameTime());
    }

    public void onStructureStartsAvailable(IChunkAccess ichunkaccess) {
        this.server.execute(() -> {
            this.structureCheck.onStructureLoad(ichunkaccess.getPos(), ichunkaccess.getAllStarts());
        });
    }

    public PathTypeCache getPathTypeCache() {
        return this.pathTypesByPosCache;
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.entityManager.close();
    }

    @Override
    public String gatherChunkSourceStats() {
        String s = this.chunkSource.gatherStats();

        return "Chunks[S] W: " + s + " E: " + this.entityManager.gatherStats();
    }

    public boolean areEntitiesLoaded(long i) {
        return this.entityManager.areEntitiesLoaded(i);
    }

    public boolean isPositionTickingWithEntitiesLoaded(long i) {
        return this.areEntitiesLoaded(i) && this.chunkSource.isPositionTicking(i);
    }

    public boolean isPositionEntityTicking(BlockPosition blockposition) {
        return this.entityManager.canPositionTick(blockposition) && this.chunkSource.chunkMap.getDistanceManager().inEntityTickingRange(ChunkCoordIntPair.asLong(blockposition));
    }

    public boolean areEntitiesActuallyLoadedAndTicking(ChunkCoordIntPair chunkcoordintpair) {
        return this.entityManager.isTicking(chunkcoordintpair) && this.entityManager.areEntitiesLoaded(chunkcoordintpair.toLong());
    }

    public boolean anyPlayerCloseEnoughForSpawning(BlockPosition blockposition) {
        return this.anyPlayerCloseEnoughForSpawning(new ChunkCoordIntPair(blockposition));
    }

    public boolean anyPlayerCloseEnoughForSpawning(ChunkCoordIntPair chunkcoordintpair) {
        return this.chunkSource.chunkMap.anyPlayerCloseEnoughForSpawning(chunkcoordintpair);
    }

    public boolean canSpawnEntitiesInChunk(ChunkCoordIntPair chunkcoordintpair) {
        return this.entityManager.canPositionTick(chunkcoordintpair) && this.getWorldBorder().isWithinBounds(chunkcoordintpair);
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.server.getWorldData().enabledFeatures();
    }

    @Override
    public PotionBrewer potionBrewing() {
        return this.server.potionBrewing();
    }

    @Override
    public FuelValues fuelValues() {
        return this.server.fuelValues();
    }

    public RandomSource getRandomSequence(MinecraftKey minecraftkey) {
        return this.randomSequences.get(minecraftkey);
    }

    public RandomSequences getRandomSequences() {
        return this.randomSequences;
    }

    public GameRules getGameRules() {
        return this.serverLevelData.getGameRules();
    }

    @Override
    public CrashReportSystemDetails fillReportDetails(CrashReport crashreport) {
        CrashReportSystemDetails crashreportsystemdetails = super.fillReportDetails(crashreport);

        crashreportsystemdetails.setDetail("Loaded entity count", () -> {
            return String.valueOf(this.entityManager.count());
        });
        return crashreportsystemdetails;
    }

    @Override
    public int getSeaLevel() {
        return this.chunkSource.getGenerator().getSeaLevel();
    }

    private final class a implements LevelCallback<Entity> {

        a() {}

        public void onCreated(Entity entity) {}

        public void onDestroyed(Entity entity) {
            WorldServer.this.getScoreboard().entityRemoved(entity);
        }

        public void onTickingStart(Entity entity) {
            WorldServer.this.entityTickList.add(entity);
        }

        public void onTickingEnd(Entity entity) {
            WorldServer.this.entityTickList.remove(entity);
        }

        public void onTrackingStart(Entity entity) {
            WorldServer.this.getChunkSource().addEntity(entity);
            if (entity instanceof EntityPlayer entityplayer) {
                WorldServer.this.players.add(entityplayer);
                WorldServer.this.updateSleepingPlayerList();
            }

            if (entity instanceof EntityInsentient entityinsentient) {
                if (WorldServer.this.isUpdatingNavigations) {
                    String s = "onTrackingStart called during navigation iteration";

                    SystemUtils.logAndPauseIfInIde("onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration"));
                }

                WorldServer.this.navigatingMobs.add(entityinsentient);
            }

            if (entity instanceof EntityEnderDragon entityenderdragon) {
                for (EntityComplexPart entitycomplexpart : entityenderdragon.getSubEntities()) {
                    WorldServer.this.dragonParts.put(entitycomplexpart.getId(), entitycomplexpart);
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::add);
            entity.valid = true; // CraftBukkit
        }

        public void onTrackingEnd(Entity entity) {
            WorldServer.this.getChunkSource().removeEntity(entity);
            if (entity instanceof EntityPlayer entityplayer) {
                WorldServer.this.players.remove(entityplayer);
                WorldServer.this.updateSleepingPlayerList();
            }

            if (entity instanceof EntityInsentient entityinsentient) {
                if (WorldServer.this.isUpdatingNavigations) {
                    String s = "onTrackingStart called during navigation iteration";

                    SystemUtils.logAndPauseIfInIde("onTrackingStart called during navigation iteration", new IllegalStateException("onTrackingStart called during navigation iteration"));
                }

                WorldServer.this.navigatingMobs.remove(entityinsentient);
            }

            if (entity instanceof EntityEnderDragon entityenderdragon) {
                for (EntityComplexPart entitycomplexpart : entityenderdragon.getSubEntities()) {
                    WorldServer.this.dragonParts.remove(entitycomplexpart.getId());
                }
            }

            entity.updateDynamicGameEventListener(DynamicGameEventListener::remove);
            // CraftBukkit start
            entity.valid = false;
            if (!(entity instanceof EntityPlayer)) {
                for (EntityPlayer player : players) {
                    player.getBukkitEntity().onEntityRemove(entity);
                }
            }
            // CraftBukkit end
        }

        public void onSectionChange(Entity entity) {
            entity.updateDynamicGameEventListener(DynamicGameEventListener::move);
        }
    }
}

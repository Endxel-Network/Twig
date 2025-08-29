package net.minecraft.world.entity.raid;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPosition;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.PacketPlayOutNamedSoundEffect;
import net.minecraft.server.level.BossBattleServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.stats.StatisticList;
import net.minecraft.util.INamable;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossBattle;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPositionTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.item.EnumColor;
import net.minecraft.world.item.EnumItemRarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.EnumBannerPatternType;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.phys.Vec3D;

public class Raid {

    public static final SpawnPlacementType RAVAGER_SPAWN_PLACEMENT_TYPE = EntityPositionTypes.getPlacementType(EntityTypes.RAVAGER);
    public static final MapCodec<Raid> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Codec.BOOL.fieldOf("started").forGetter((raid) -> {
            return raid.started;
        }), Codec.BOOL.fieldOf("active").forGetter((raid) -> {
            return raid.active;
        }), Codec.LONG.fieldOf("ticks_active").forGetter((raid) -> {
            return raid.ticksActive;
        }), Codec.INT.fieldOf("raid_omen_level").forGetter((raid) -> {
            return raid.raidOmenLevel;
        }), Codec.INT.fieldOf("groups_spawned").forGetter((raid) -> {
            return raid.groupsSpawned;
        }), Codec.INT.fieldOf("cooldown_ticks").forGetter((raid) -> {
            return raid.raidCooldownTicks;
        }), Codec.INT.fieldOf("post_raid_ticks").forGetter((raid) -> {
            return raid.postRaidTicks;
        }), Codec.FLOAT.fieldOf("total_health").forGetter((raid) -> {
            return raid.totalHealth;
        }), Codec.INT.fieldOf("group_count").forGetter((raid) -> {
            return raid.numGroups;
        }), Raid.Status.CODEC.fieldOf("status").forGetter((raid) -> {
            return raid.status;
        }), BlockPosition.CODEC.fieldOf("center").forGetter((raid) -> {
            return raid.center;
        }), UUIDUtil.CODEC_SET.fieldOf("heroes_of_the_village").forGetter((raid) -> {
            return raid.heroesOfTheVillage;
        })).apply(instance, Raid::new);
    });
    private static final int ALLOW_SPAWNING_WITHIN_VILLAGE_SECONDS_THRESHOLD = 7;
    private static final int SECTION_RADIUS_FOR_FINDING_NEW_VILLAGE_CENTER = 2;
    private static final int VILLAGE_SEARCH_RADIUS = 32;
    private static final int RAID_TIMEOUT_TICKS = 48000;
    private static final int NUM_SPAWN_ATTEMPTS = 5;
    private static final IChatBaseComponent OMINOUS_BANNER_PATTERN_NAME = IChatBaseComponent.translatable("block.minecraft.ominous_banner");
    private static final String RAIDERS_REMAINING = "event.minecraft.raid.raiders_remaining";
    public static final int VILLAGE_RADIUS_BUFFER = 16;
    private static final int POST_RAID_TICK_LIMIT = 40;
    private static final int DEFAULT_PRE_RAID_TICKS = 300;
    public static final int MAX_NO_ACTION_TIME = 2400;
    public static final int MAX_CELEBRATION_TICKS = 600;
    private static final int OUTSIDE_RAID_BOUNDS_TIMEOUT = 30;
    public static final int TICKS_PER_DAY = 24000;
    public static final int DEFAULT_MAX_RAID_OMEN_LEVEL = 5;
    private static final int LOW_MOB_THRESHOLD = 2;
    private static final IChatBaseComponent RAID_NAME_COMPONENT = IChatBaseComponent.translatable("event.minecraft.raid");
    private static final IChatBaseComponent RAID_BAR_VICTORY_COMPONENT = IChatBaseComponent.translatable("event.minecraft.raid.victory.full");
    private static final IChatBaseComponent RAID_BAR_DEFEAT_COMPONENT = IChatBaseComponent.translatable("event.minecraft.raid.defeat.full");
    private static final int HERO_OF_THE_VILLAGE_DURATION = 48000;
    private static final int VALID_RAID_RADIUS = 96;
    public static final int VALID_RAID_RADIUS_SQR = 9216;
    public static final int RAID_REMOVAL_THRESHOLD_SQR = 12544;
    private final Map<Integer, EntityRaider> groupToLeaderMap = Maps.newHashMap();
    private final Map<Integer, Set<EntityRaider>> groupRaiderMap = Maps.newHashMap();
    public final Set<UUID> heroesOfTheVillage = Sets.newHashSet();
    public long ticksActive;
    private BlockPosition center;
    private boolean started;
    public float totalHealth;
    public int raidOmenLevel;
    private boolean active;
    private int groupsSpawned;
    private final BossBattleServer raidEvent;
    private int postRaidTicks;
    private int raidCooldownTicks;
    private final RandomSource random;
    public final int numGroups;
    private Raid.Status status;
    private int celebrationTicks;
    private Optional<BlockPosition> waveSpawnPos;

    public Raid(BlockPosition blockposition, EnumDifficulty enumdifficulty) {
        this.raidEvent = new BossBattleServer(Raid.RAID_NAME_COMPONENT, BossBattle.BarColor.RED, BossBattle.BarStyle.NOTCHED_10);
        this.random = RandomSource.create();
        this.waveSpawnPos = Optional.empty();
        this.active = true;
        this.raidCooldownTicks = 300;
        this.raidEvent.setProgress(0.0F);
        this.center = blockposition;
        this.numGroups = this.getNumGroups(enumdifficulty);
        this.status = Raid.Status.ONGOING;
    }

    private Raid(boolean flag, boolean flag1, long i, int j, int k, int l, int i1, float f, int j1, Raid.Status raid_status, BlockPosition blockposition, Set<UUID> set) {
        this.raidEvent = new BossBattleServer(Raid.RAID_NAME_COMPONENT, BossBattle.BarColor.RED, BossBattle.BarStyle.NOTCHED_10);
        this.random = RandomSource.create();
        this.waveSpawnPos = Optional.empty();
        this.started = flag;
        this.active = flag1;
        this.ticksActive = i;
        this.raidOmenLevel = j;
        this.groupsSpawned = k;
        this.raidCooldownTicks = l;
        this.postRaidTicks = i1;
        this.totalHealth = f;
        this.center = blockposition;
        this.numGroups = j1;
        this.status = raid_status;
        this.heroesOfTheVillage.addAll(set);
    }

    public boolean isOver() {
        return this.isVictory() || this.isLoss();
    }

    public boolean isBetweenWaves() {
        return this.hasFirstWaveSpawned() && this.getTotalRaidersAlive() == 0 && this.raidCooldownTicks > 0;
    }

    public boolean hasFirstWaveSpawned() {
        return this.groupsSpawned > 0;
    }

    public boolean isStopped() {
        return this.status == Raid.Status.STOPPED;
    }

    public boolean isVictory() {
        return this.status == Raid.Status.VICTORY;
    }

    public boolean isLoss() {
        return this.status == Raid.Status.LOSS;
    }

    // CraftBukkit start
    public boolean isInProgress() {
        return this.status == Status.ONGOING;
    }
    // CraftBukkit end

    public float getTotalHealth() {
        return this.totalHealth;
    }

    public Set<EntityRaider> getAllRaiders() {
        Set<EntityRaider> set = Sets.newHashSet();

        for (Set<EntityRaider> set1 : this.groupRaiderMap.values()) {
            set.addAll(set1);
        }

        return set;
    }

    public boolean isStarted() {
        return this.started;
    }

    public int getGroupsSpawned() {
        return this.groupsSpawned;
    }

    private Predicate<EntityPlayer> validPlayer() {
        return (entityplayer) -> {
            BlockPosition blockposition = entityplayer.blockPosition();

            return entityplayer.isAlive() && entityplayer.serverLevel().getRaidAt(blockposition) == this;
        };
    }

    private void updatePlayers(WorldServer worldserver) {
        Set<EntityPlayer> set = Sets.newHashSet(this.raidEvent.getPlayers());
        List<EntityPlayer> list = worldserver.getPlayers(this.validPlayer());

        for (EntityPlayer entityplayer : list) {
            if (!set.contains(entityplayer)) {
                this.raidEvent.addPlayer(entityplayer);
            }
        }

        for (EntityPlayer entityplayer1 : set) {
            if (!list.contains(entityplayer1)) {
                this.raidEvent.removePlayer(entityplayer1);
            }
        }

    }

    public int getMaxRaidOmenLevel() {
        return 5;
    }

    public int getRaidOmenLevel() {
        return this.raidOmenLevel;
    }

    public void setRaidOmenLevel(int i) {
        this.raidOmenLevel = i;
    }

    public boolean absorbRaidOmen(EntityPlayer entityplayer) {
        MobEffect mobeffect = entityplayer.getEffect(MobEffects.RAID_OMEN);

        if (mobeffect == null) {
            return false;
        } else {
            this.raidOmenLevel += mobeffect.getAmplifier() + 1;
            this.raidOmenLevel = MathHelper.clamp(this.raidOmenLevel, 0, this.getMaxRaidOmenLevel());
            if (!this.hasFirstWaveSpawned()) {
                entityplayer.awardStat(StatisticList.RAID_TRIGGER);
                CriterionTriggers.RAID_OMEN.trigger(entityplayer);
            }

            return true;
        }
    }

    public void stop() {
        this.active = false;
        this.raidEvent.removeAllPlayers();
        this.status = Raid.Status.STOPPED;
    }

    public void tick(WorldServer worldserver) {
        if (!this.isStopped()) {
            if (this.status == Raid.Status.ONGOING) {
                boolean flag = this.active;

                this.active = worldserver.hasChunkAt(this.center);
                if (worldserver.getDifficulty() == EnumDifficulty.PEACEFUL) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, worldserver, org.bukkit.event.raid.RaidStopEvent.Reason.PEACE); // CraftBukkit
                    this.stop();
                    return;
                }

                if (flag != this.active) {
                    this.raidEvent.setVisible(this.active);
                }

                if (!this.active) {
                    return;
                }

                if (!worldserver.isVillage(this.center)) {
                    this.moveRaidCenterToNearbyVillageSection(worldserver);
                }

                if (!worldserver.isVillage(this.center)) {
                    if (this.groupsSpawned > 0) {
                        this.status = Raid.Status.LOSS;
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidFinishEvent(this, worldserver, new java.util.ArrayList<>()); // CraftBukkit
                    } else {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, worldserver, org.bukkit.event.raid.RaidStopEvent.Reason.NOT_IN_VILLAGE); // CraftBukkit
                        this.stop();
                    }
                }

                ++this.ticksActive;
                if (this.ticksActive >= 48000L) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, worldserver, org.bukkit.event.raid.RaidStopEvent.Reason.TIMEOUT); // CraftBukkit
                    this.stop();
                    return;
                }

                int i = this.getTotalRaidersAlive();

                if (i == 0 && this.hasMoreWaves()) {
                    if (this.raidCooldownTicks <= 0) {
                        if (this.raidCooldownTicks == 0 && this.groupsSpawned > 0) {
                            this.raidCooldownTicks = 300;
                            this.raidEvent.setName(Raid.RAID_NAME_COMPONENT);
                            return;
                        }
                    } else {
                        boolean flag1 = this.waveSpawnPos.isPresent();
                        boolean flag2 = !flag1 && this.raidCooldownTicks % 5 == 0;

                        if (flag1 && !worldserver.isPositionEntityTicking((BlockPosition) this.waveSpawnPos.get())) {
                            flag2 = true;
                        }

                        if (flag2) {
                            this.waveSpawnPos = this.getValidSpawnPos(worldserver);
                        }

                        if (this.raidCooldownTicks == 300 || this.raidCooldownTicks % 20 == 0) {
                            this.updatePlayers(worldserver);
                        }

                        --this.raidCooldownTicks;
                        this.raidEvent.setProgress(MathHelper.clamp((float) (300 - this.raidCooldownTicks) / 300.0F, 0.0F, 1.0F));
                    }
                }

                if (this.ticksActive % 20L == 0L) {
                    this.updatePlayers(worldserver);
                    this.updateRaiders(worldserver);
                    if (i > 0) {
                        if (i <= 2) {
                            this.raidEvent.setName(Raid.RAID_NAME_COMPONENT.copy().append(" - ").append((IChatBaseComponent) IChatBaseComponent.translatable("event.minecraft.raid.raiders_remaining", i)));
                        } else {
                            this.raidEvent.setName(Raid.RAID_NAME_COMPONENT);
                        }
                    } else {
                        this.raidEvent.setName(Raid.RAID_NAME_COMPONENT);
                    }
                }

                boolean flag3 = false;
                int j = 0;

                while (this.shouldSpawnGroup()) {
                    BlockPosition blockposition = (BlockPosition) this.waveSpawnPos.orElseGet(() -> {
                        return this.findRandomSpawnPos(worldserver, 20);
                    });

                    if (blockposition != null) {
                        this.started = true;
                        this.spawnGroup(worldserver, blockposition);
                        if (!flag3) {
                            this.playSound(worldserver, blockposition);
                            flag3 = true;
                        }
                    } else {
                        ++j;
                    }

                    if (j > 5) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, worldserver, org.bukkit.event.raid.RaidStopEvent.Reason.UNSPAWNABLE);  // CraftBukkit
                        this.stop();
                        break;
                    }
                }

                if (this.isStarted() && !this.hasMoreWaves() && i == 0) {
                    if (this.postRaidTicks < 40) {
                        ++this.postRaidTicks;
                    } else {
                        this.status = Raid.Status.VICTORY;

                        List<org.bukkit.entity.Player> winners = new java.util.ArrayList<>(); // CraftBukkit
                        for (UUID uuid : this.heroesOfTheVillage) {
                            Entity entity = worldserver.getEntity(uuid);

                            if (entity instanceof EntityLiving) {
                                EntityLiving entityliving = (EntityLiving) entity;

                                if (!entity.isSpectator()) {
                                    entityliving.addEffect(new MobEffect(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.raidOmenLevel - 1, false, false, true));
                                    if (entityliving instanceof EntityPlayer) {
                                        EntityPlayer entityplayer = (EntityPlayer) entityliving;

                                        entityplayer.awardStat(StatisticList.RAID_WIN);
                                        CriterionTriggers.RAID_WIN.trigger(entityplayer);
                                        winners.add(entityplayer.getBukkitEntity()); // CraftBukkit
                                    }
                                }
                            }
                        }
                        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidFinishEvent(this, worldserver, winners); // CraftBukkit
                    }
                }

                this.setDirty(worldserver);
            } else if (this.isOver()) {
                ++this.celebrationTicks;
                if (this.celebrationTicks >= 600) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callRaidStopEvent(this, worldserver, org.bukkit.event.raid.RaidStopEvent.Reason.FINISHED); // CraftBukkit
                    this.stop();
                    return;
                }

                if (this.celebrationTicks % 20 == 0) {
                    this.updatePlayers(worldserver);
                    this.raidEvent.setVisible(true);
                    if (this.isVictory()) {
                        this.raidEvent.setProgress(0.0F);
                        this.raidEvent.setName(Raid.RAID_BAR_VICTORY_COMPONENT);
                    } else {
                        this.raidEvent.setName(Raid.RAID_BAR_DEFEAT_COMPONENT);
                    }
                }
            }

        }
    }

    private void moveRaidCenterToNearbyVillageSection(WorldServer worldserver) {
        Stream<SectionPosition> stream = SectionPosition.cube(SectionPosition.of(this.center), 2);

        Objects.requireNonNull(worldserver);
        stream.filter(worldserver::isVillage).map(SectionPosition::center).min(Comparator.comparingDouble((blockposition) -> {
            return blockposition.distSqr(this.center);
        })).ifPresent(this::setCenter);
    }

    private Optional<BlockPosition> getValidSpawnPos(WorldServer worldserver) {
        BlockPosition blockposition = this.findRandomSpawnPos(worldserver, 8);

        return blockposition != null ? Optional.of(blockposition) : Optional.empty();
    }

    private boolean hasMoreWaves() {
        return this.hasBonusWave() ? !this.hasSpawnedBonusWave() : !this.isFinalWave();
    }

    private boolean isFinalWave() {
        return this.getGroupsSpawned() == this.numGroups;
    }

    private boolean hasBonusWave() {
        return this.raidOmenLevel > 1;
    }

    private boolean hasSpawnedBonusWave() {
        return this.getGroupsSpawned() > this.numGroups;
    }

    private boolean shouldSpawnBonusGroup() {
        return this.isFinalWave() && this.getTotalRaidersAlive() == 0 && this.hasBonusWave();
    }

    private void updateRaiders(WorldServer worldserver) {
        Iterator<Set<EntityRaider>> iterator = this.groupRaiderMap.values().iterator();
        Set<EntityRaider> set = Sets.newHashSet();

        while (iterator.hasNext()) {
            Set<EntityRaider> set1 = (Set) iterator.next();

            for (EntityRaider entityraider : set1) {
                BlockPosition blockposition = entityraider.blockPosition();

                if (!entityraider.isRemoved() && entityraider.level().dimension() == worldserver.dimension() && this.center.distSqr(blockposition) < 12544.0D) {
                    if (entityraider.tickCount > 600) {
                        if (worldserver.getEntity(entityraider.getUUID()) == null) {
                            set.add(entityraider);
                        }

                        if (!worldserver.isVillage(blockposition) && entityraider.getNoActionTime() > 2400) {
                            entityraider.setTicksOutsideRaid(entityraider.getTicksOutsideRaid() + 1);
                        }

                        if (entityraider.getTicksOutsideRaid() >= 30) {
                            set.add(entityraider);
                        }
                    }
                } else {
                    set.add(entityraider);
                }
            }
        }

        for (EntityRaider entityraider1 : set) {
            this.removeFromRaid(worldserver, entityraider1, true);
            if (entityraider1.isPatrolLeader()) {
                this.removeLeader(entityraider1.getWave());
            }
        }

    }

    private void playSound(WorldServer worldserver, BlockPosition blockposition) {
        float f = 13.0F;
        int i = 64;
        Collection<EntityPlayer> collection = this.raidEvent.getPlayers();
        long j = this.random.nextLong();

        for (EntityPlayer entityplayer : worldserver.players()) {
            Vec3D vec3d = entityplayer.position();
            Vec3D vec3d1 = Vec3D.atCenterOf(blockposition);
            double d0 = Math.sqrt((vec3d1.x - vec3d.x) * (vec3d1.x - vec3d.x) + (vec3d1.z - vec3d.z) * (vec3d1.z - vec3d.z));
            double d1 = vec3d.x + 13.0D / d0 * (vec3d1.x - vec3d.x);
            double d2 = vec3d.z + 13.0D / d0 * (vec3d1.z - vec3d.z);

            if (d0 <= 64.0D || collection.contains(entityplayer)) {
                entityplayer.connection.send(new PacketPlayOutNamedSoundEffect(SoundEffects.RAID_HORN, SoundCategory.NEUTRAL, d1, entityplayer.getY(), d2, 64.0F, 1.0F, j));
            }
        }

    }

    private void spawnGroup(WorldServer worldserver, BlockPosition blockposition) {
        boolean flag = false;
        int i = this.groupsSpawned + 1;

        this.totalHealth = 0.0F;
        DifficultyDamageScaler difficultydamagescaler = worldserver.getCurrentDifficultyAt(blockposition);
        boolean flag1 = this.shouldSpawnBonusGroup();

        // CraftBukkit start
        EntityRaider leader = null;
        List<EntityRaider> raiders = new java.util.ArrayList<>();
        // CraftBukkit end
        for (Raid.Wave raid_wave : Raid.Wave.VALUES) {
            int j = this.getDefaultNumSpawns(raid_wave, i, flag1) + this.getPotentialBonusSpawns(raid_wave, this.random, i, difficultydamagescaler, flag1);
            int k = 0;

            for (int l = 0; l < j; ++l) {
                EntityRaider entityraider = raid_wave.entityType.create(worldserver, EntitySpawnReason.EVENT);

                if (entityraider == null) {
                    break;
                }

                if (!flag && entityraider.canBeLeader()) {
                    entityraider.setPatrolLeader(true);
                    this.setLeader(i, entityraider);
                    flag = true;
                    leader = entityraider; // CraftBukkit
                }

                this.joinRaid(worldserver, i, entityraider, blockposition, false);
                raiders.add(entityraider); // CraftBukkit
                if (raid_wave.entityType == EntityTypes.RAVAGER) {
                    EntityRaider entityraider1 = null;

                    if (i == this.getNumGroups(EnumDifficulty.NORMAL)) {
                        entityraider1 = EntityTypes.PILLAGER.create(worldserver, EntitySpawnReason.EVENT);
                    } else if (i >= this.getNumGroups(EnumDifficulty.HARD)) {
                        if (k == 0) {
                            entityraider1 = EntityTypes.EVOKER.create(worldserver, EntitySpawnReason.EVENT);
                        } else {
                            entityraider1 = EntityTypes.VINDICATOR.create(worldserver, EntitySpawnReason.EVENT);
                        }
                    }

                    ++k;
                    if (entityraider1 != null) {
                        this.joinRaid(worldserver, i, entityraider1, blockposition, false);
                        entityraider1.snapTo(blockposition, 0.0F, 0.0F);
                        entityraider1.startRiding(entityraider);
                        raiders.add(entityraider); // CraftBukkit
                    }
                }
            }
        }

        this.waveSpawnPos = Optional.empty();
        ++this.groupsSpawned;
        this.updateBossbar();
        this.setDirty(worldserver);
        org.bukkit.craftbukkit.event.CraftEventFactory.callRaidSpawnWaveEvent(this, worldserver, leader, raiders); // CraftBukkit
    }

    public void joinRaid(WorldServer worldserver, int i, EntityRaider entityraider, @Nullable BlockPosition blockposition, boolean flag) {
        boolean flag1 = this.addWaveMob(worldserver, i, entityraider);

        if (flag1) {
            entityraider.setCurrentRaid(this);
            entityraider.setWave(i);
            entityraider.setCanJoinRaid(true);
            entityraider.setTicksOutsideRaid(0);
            if (!flag && blockposition != null) {
                entityraider.setPos((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 1.0D, (double) blockposition.getZ() + 0.5D);
                entityraider.finalizeSpawn(worldserver, worldserver.getCurrentDifficultyAt(blockposition), EntitySpawnReason.EVENT, (GroupDataEntity) null);
                entityraider.applyRaidBuffs(worldserver, i, false);
                entityraider.setOnGround(true);
                worldserver.addFreshEntityWithPassengers(entityraider, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.RAID); // CraftBukkit
            }
        }

    }

    public void updateBossbar() {
        this.raidEvent.setProgress(MathHelper.clamp(this.getHealthOfLivingRaiders() / this.totalHealth, 0.0F, 1.0F));
    }

    public float getHealthOfLivingRaiders() {
        float f = 0.0F;

        for (Set<EntityRaider> set : this.groupRaiderMap.values()) {
            for (EntityRaider entityraider : set) {
                f += entityraider.getHealth();
            }
        }

        return f;
    }

    private boolean shouldSpawnGroup() {
        return this.raidCooldownTicks == 0 && (this.groupsSpawned < this.numGroups || this.shouldSpawnBonusGroup()) && this.getTotalRaidersAlive() == 0;
    }

    public int getTotalRaidersAlive() {
        return this.groupRaiderMap.values().stream().mapToInt(Set::size).sum();
    }

    public void removeFromRaid(WorldServer worldserver, EntityRaider entityraider, boolean flag) {
        Set<EntityRaider> set = (Set) this.groupRaiderMap.get(entityraider.getWave());

        if (set != null) {
            boolean flag1 = set.remove(entityraider);

            if (flag1) {
                if (flag) {
                    this.totalHealth -= entityraider.getHealth();
                }

                entityraider.setCurrentRaid((Raid) null);
                this.updateBossbar();
                this.setDirty(worldserver);
            }
        }

    }

    private void setDirty(WorldServer worldserver) {
        worldserver.getRaids().setDirty();
    }

    public static ItemStack getOminousBannerInstance(HolderGetter<EnumBannerPatternType> holdergetter) {
        ItemStack itemstack = new ItemStack(Items.WHITE_BANNER);
        BannerPatternLayers bannerpatternlayers = (new BannerPatternLayers.a()).addIfRegistered(holdergetter, BannerPatterns.RHOMBUS_MIDDLE, EnumColor.CYAN).addIfRegistered(holdergetter, BannerPatterns.STRIPE_BOTTOM, EnumColor.LIGHT_GRAY).addIfRegistered(holdergetter, BannerPatterns.STRIPE_CENTER, EnumColor.GRAY).addIfRegistered(holdergetter, BannerPatterns.BORDER, EnumColor.LIGHT_GRAY).addIfRegistered(holdergetter, BannerPatterns.STRIPE_MIDDLE, EnumColor.BLACK).addIfRegistered(holdergetter, BannerPatterns.HALF_HORIZONTAL, EnumColor.LIGHT_GRAY).addIfRegistered(holdergetter, BannerPatterns.CIRCLE_MIDDLE, EnumColor.LIGHT_GRAY).addIfRegistered(holdergetter, BannerPatterns.BORDER, EnumColor.BLACK).build();

        itemstack.set(DataComponents.BANNER_PATTERNS, bannerpatternlayers);
        itemstack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.BANNER_PATTERNS, true));
        itemstack.set(DataComponents.ITEM_NAME, Raid.OMINOUS_BANNER_PATTERN_NAME);
        itemstack.set(DataComponents.RARITY, EnumItemRarity.UNCOMMON);
        return itemstack;
    }

    @Nullable
    public EntityRaider getLeader(int i) {
        return (EntityRaider) this.groupToLeaderMap.get(i);
    }

    @Nullable
    private BlockPosition findRandomSpawnPos(WorldServer worldserver, int i) {
        int j = this.raidCooldownTicks / 20;
        float f = 0.22F * (float) j - 0.24F;
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();
        float f1 = worldserver.random.nextFloat() * ((float) Math.PI * 2F);

        for (int k = 0; k < i; ++k) {
            float f2 = f1 + (float) Math.PI * (float) k / 8.0F;
            int l = this.center.getX() + MathHelper.floor(MathHelper.cos(f2) * 32.0F * f) + worldserver.random.nextInt(3) * MathHelper.floor(f);
            int i1 = this.center.getZ() + MathHelper.floor(MathHelper.sin(f2) * 32.0F * f) + worldserver.random.nextInt(3) * MathHelper.floor(f);
            int j1 = worldserver.getHeight(HeightMap.Type.WORLD_SURFACE, l, i1);

            if (MathHelper.abs(j1 - this.center.getY()) <= 96) {
                blockposition_mutableblockposition.set(l, j1, i1);
                if (!worldserver.isVillage((BlockPosition) blockposition_mutableblockposition) || j <= 7) {
                    int k1 = 10;

                    if (worldserver.hasChunksAt(blockposition_mutableblockposition.getX() - 10, blockposition_mutableblockposition.getZ() - 10, blockposition_mutableblockposition.getX() + 10, blockposition_mutableblockposition.getZ() + 10) && worldserver.isPositionEntityTicking(blockposition_mutableblockposition) && (Raid.RAVAGER_SPAWN_PLACEMENT_TYPE.isSpawnPositionOk(worldserver, blockposition_mutableblockposition, EntityTypes.RAVAGER) || worldserver.getBlockState(blockposition_mutableblockposition.below()).is(Blocks.SNOW) && worldserver.getBlockState(blockposition_mutableblockposition).isAir())) {
                        return blockposition_mutableblockposition;
                    }
                }
            }
        }

        return null;
    }

    private boolean addWaveMob(WorldServer worldserver, int i, EntityRaider entityraider) {
        return this.addWaveMob(worldserver, i, entityraider, true);
    }

    public boolean addWaveMob(WorldServer worldserver, int i, EntityRaider entityraider, boolean flag) {
        this.groupRaiderMap.computeIfAbsent(i, (integer) -> {
            return Sets.newHashSet();
        });
        Set<EntityRaider> set = (Set) this.groupRaiderMap.get(i);
        EntityRaider entityraider1 = null;

        for (EntityRaider entityraider2 : set) {
            if (entityraider2.getUUID().equals(entityraider.getUUID())) {
                entityraider1 = entityraider2;
                break;
            }
        }

        if (entityraider1 != null) {
            set.remove(entityraider1);
            set.add(entityraider);
        }

        set.add(entityraider);
        if (flag) {
            this.totalHealth += entityraider.getHealth();
        }

        this.updateBossbar();
        this.setDirty(worldserver);
        return true;
    }

    public void setLeader(int i, EntityRaider entityraider) {
        this.groupToLeaderMap.put(i, entityraider);
        entityraider.setItemSlot(EnumItemSlot.HEAD, getOminousBannerInstance(entityraider.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
        entityraider.setDropChance(EnumItemSlot.HEAD, 2.0F);
    }

    public void removeLeader(int i) {
        this.groupToLeaderMap.remove(i);
    }

    public BlockPosition getCenter() {
        return this.center;
    }

    private void setCenter(BlockPosition blockposition) {
        this.center = blockposition;
    }

    private int getDefaultNumSpawns(Raid.Wave raid_wave, int i, boolean flag) {
        return flag ? raid_wave.spawnsPerWaveBeforeBonus[this.numGroups] : raid_wave.spawnsPerWaveBeforeBonus[i];
    }

    private int getPotentialBonusSpawns(Raid.Wave raid_wave, RandomSource randomsource, int i, DifficultyDamageScaler difficultydamagescaler, boolean flag) {
        EnumDifficulty enumdifficulty = difficultydamagescaler.getDifficulty();
        boolean flag1 = enumdifficulty == EnumDifficulty.EASY;
        boolean flag2 = enumdifficulty == EnumDifficulty.NORMAL;
        int j;

        switch (raid_wave.ordinal()) {
            case 0:
            case 2:
                if (flag1) {
                    j = randomsource.nextInt(2);
                } else if (flag2) {
                    j = 1;
                } else {
                    j = 2;
                }
                break;
            case 1:
            default:
                return 0;
            case 3:
                if (flag1 || i <= 2 || i == 4) {
                    return 0;
                }

                j = 1;
                break;
            case 4:
                j = !flag1 && flag ? 1 : 0;
        }

        return j > 0 ? randomsource.nextInt(j + 1) : 0;
    }

    public boolean isActive() {
        return this.active;
    }

    public int getNumGroups(EnumDifficulty enumdifficulty) {
        byte b0;

        switch (enumdifficulty) {
            case PEACEFUL:
                b0 = 0;
                break;
            case EASY:
                b0 = 3;
                break;
            case NORMAL:
                b0 = 5;
                break;
            case HARD:
                b0 = 7;
                break;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }

        return b0;
    }

    public float getEnchantOdds() {
        int i = this.getRaidOmenLevel();

        return i == 2 ? 0.1F : (i == 3 ? 0.25F : (i == 4 ? 0.5F : (i == 5 ? 0.75F : 0.0F)));
    }

    public void addHeroOfTheVillage(Entity entity) {
        this.heroesOfTheVillage.add(entity.getUUID());
    }

    // CraftBukkit start - a method to get all raiders
    public java.util.Collection<EntityRaider> getRaiders() {
        return this.groupRaiderMap.values().stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toSet());
    }
    // CraftBukkit end

    private static enum Status implements INamable {

        ONGOING("ongoing"), VICTORY("victory"), LOSS("loss"), STOPPED("stopped");

        public static final Codec<Raid.Status> CODEC = INamable.<Raid.Status>fromEnum(Raid.Status::values);
        private final String name;

        private Status(final String s) {
            this.name = s;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    private static enum Wave {

        VINDICATOR(EntityTypes.VINDICATOR, new int[]{0, 0, 2, 0, 1, 4, 2, 5}), EVOKER(EntityTypes.EVOKER, new int[]{0, 0, 0, 0, 0, 1, 1, 2}), PILLAGER(EntityTypes.PILLAGER, new int[]{0, 4, 3, 3, 4, 4, 4, 2}), WITCH(EntityTypes.WITCH, new int[]{0, 0, 0, 0, 3, 0, 0, 1}), RAVAGER(EntityTypes.RAVAGER, new int[]{0, 0, 0, 1, 0, 1, 0, 2});

        static final Raid.Wave[] VALUES = values();
        final EntityTypes<? extends EntityRaider> entityType;
        final int[] spawnsPerWaveBeforeBonus;

        private Wave(final EntityTypes entitytypes, final int[] aint) {
            this.entityType = entitytypes;
            this.spawnsPerWaveBeforeBonus = aint;
        }
    }
}

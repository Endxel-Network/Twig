package net.minecraft.world.entity.raid;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRaid;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.village.poi.VillagePlace;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.monster.EntityIllagerAbstract;
import net.minecraft.world.entity.monster.EntityMonsterPatrolling;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.pathfinder.PathEntity;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public abstract class EntityRaider extends EntityMonsterPatrolling {

    protected static final DataWatcherObject<Boolean> IS_CELEBRATING = DataWatcher.<Boolean>defineId(EntityRaider.class, DataWatcherRegistry.BOOLEAN);
    static final Predicate<EntityItem> ALLOWED_ITEMS = (entityitem) -> {
        return !entityitem.hasPickUpDelay() && entityitem.isAlive() && ItemStack.matches(entityitem.getItem(), Raid.getOminousBannerInstance(entityitem.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
    };
    private static final int DEFAULT_WAVE = 0;
    private static final boolean DEFAULT_CAN_JOIN_RAID = false;
    @Nullable
    protected Raid raid;
    private int wave = 0;
    private boolean canJoinRaid = false;
    private int ticksOutsideRaid;

    protected EntityRaider(EntityTypes<? extends EntityRaider> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new EntityRaider.b(this));
        this.goalSelector.addGoal(3, new PathfinderGoalRaid(this));
        this.goalSelector.addGoal(4, new EntityRaider.d(this, (double) 1.05F, 1));
        this.goalSelector.addGoal(5, new EntityRaider.c(this));
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityRaider.IS_CELEBRATING, false);
    }

    public abstract void applyRaidBuffs(WorldServer worldserver, int i, boolean flag);

    public boolean canJoinRaid() {
        return this.canJoinRaid;
    }

    public void setCanJoinRaid(boolean flag) {
        this.canJoinRaid = flag;
    }

    @Override
    public void aiStep() {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (this.isAlive()) {
                Raid raid = this.getCurrentRaid();

                if (this.canJoinRaid()) {
                    if (raid == null) {
                        if (this.level().getGameTime() % 20L == 0L) {
                            Raid raid1 = worldserver.getRaidAt(this.blockPosition());

                            if (raid1 != null && PersistentRaid.canJoinRaid(this)) {
                                raid1.joinRaid(worldserver, raid1.getGroupsSpawned(), this, (BlockPosition) null, true);
                            }
                        }
                    } else {
                        EntityLiving entityliving = this.getTarget();

                        if (entityliving != null && (entityliving.getType() == EntityTypes.PLAYER || entityliving.getType() == EntityTypes.IRON_GOLEM)) {
                            this.noActionTime = 0;
                        }
                    }
                }
            }
        }

        super.aiStep();
    }

    @Override
    protected void updateNoActionTime() {
        this.noActionTime += 2;
    }

    @Override
    public void die(DamageSource damagesource) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            Entity entity = damagesource.getEntity();
            Raid raid = this.getCurrentRaid();

            if (raid != null) {
                if (this.isPatrolLeader()) {
                    raid.removeLeader(this.getWave());
                }

                if (entity != null && entity.getType() == EntityTypes.PLAYER) {
                    raid.addHeroOfTheVillage(entity);
                }

                raid.removeFromRaid(worldserver, this, false);
            }
        }

        super.die(damagesource);
    }

    @Override
    public boolean canJoinPatrol() {
        return !this.hasActiveRaid();
    }

    public void setCurrentRaid(@Nullable Raid raid) {
        this.raid = raid;
    }

    @Nullable
    public Raid getCurrentRaid() {
        return this.raid;
    }

    public boolean isCaptain() {
        ItemStack itemstack = this.getItemBySlot(EnumItemSlot.HEAD);
        boolean flag = !itemstack.isEmpty() && ItemStack.matches(itemstack, Raid.getOminousBannerInstance(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
        boolean flag1 = this.isPatrolLeader();

        return flag && flag1;
    }

    public boolean hasRaid() {
        World world = this.level();

        if (!(world instanceof WorldServer worldserver)) {
            return false;
        } else {
            return this.getCurrentRaid() != null || worldserver.getRaidAt(this.blockPosition()) != null;
        }
    }

    public boolean hasActiveRaid() {
        return this.getCurrentRaid() != null && this.getCurrentRaid().isActive();
    }

    public void setWave(int i) {
        this.wave = i;
    }

    public int getWave() {
        return this.wave;
    }

    public boolean isCelebrating() {
        return (Boolean) this.entityData.get(EntityRaider.IS_CELEBRATING);
    }

    public void setCelebrating(boolean flag) {
        this.entityData.set(EntityRaider.IS_CELEBRATING, flag);
    }

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("Wave", this.wave);
        nbttagcompound.putBoolean("CanJoinRaid", this.canJoinRaid);
        if (this.raid != null) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                worldserver.getRaids().getId(this.raid).ifPresent((i) -> {
                    nbttagcompound.putInt("RaidId", i);
                });
            }
        }

    }

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.wave = nbttagcompound.getIntOr("Wave", 0);
        this.canJoinRaid = nbttagcompound.getBooleanOr("CanJoinRaid", false);
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            nbttagcompound.getInt("RaidId").ifPresent((integer) -> {
                this.raid = worldserver.getRaids().get(integer);
                if (this.raid != null) {
                    this.raid.addWaveMob(worldserver, this.wave, this, false);
                    if (this.isPatrolLeader()) {
                        this.raid.setLeader(this.wave, this);
                    }
                }

            });
        }

    }

    @Override
    protected void pickUpItem(WorldServer worldserver, EntityItem entityitem) {
        ItemStack itemstack = entityitem.getItem();
        boolean flag = this.hasActiveRaid() && this.getCurrentRaid().getLeader(this.getWave()) != null;

        if (this.hasActiveRaid() && !flag && ItemStack.matches(itemstack, Raid.getOminousBannerInstance(this.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)))) {
            EnumItemSlot enumitemslot = EnumItemSlot.HEAD;
            ItemStack itemstack1 = this.getItemBySlot(enumitemslot);
            double d0 = (double) this.getDropChances().byEquipment(enumitemslot);

            if (!itemstack1.isEmpty() && (double) Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d0) {
                this.spawnAtLocation(worldserver, itemstack1);
            }

            this.onItemPickup(entityitem);
            this.setItemSlot(enumitemslot, itemstack);
            this.take(entityitem, itemstack.getCount());
            entityitem.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            this.getCurrentRaid().setLeader(this.getWave(), this);
            this.setPatrolLeader(true);
        } else {
            super.pickUpItem(worldserver, entityitem);
        }

    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return this.getCurrentRaid() == null ? super.removeWhenFarAway(d0) : false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.getCurrentRaid() != null;
    }

    public int getTicksOutsideRaid() {
        return this.ticksOutsideRaid;
    }

    public void setTicksOutsideRaid(int i) {
        this.ticksOutsideRaid = i;
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.hasActiveRaid()) {
            this.getCurrentRaid().updateBossbar();
        }

        return super.hurtServer(worldserver, damagesource, f);
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        this.setCanJoinRaid(this.getType() != EntityTypes.WITCH || entityspawnreason != EntitySpawnReason.NATURAL);
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    public abstract SoundEffect getCelebrateSound();

    public class b<T extends EntityRaider> extends PathfinderGoal {

        private final T mob;
        private Int2LongOpenHashMap unreachableBannerCache = new Int2LongOpenHashMap();
        @Nullable
        private PathEntity pathToBanner;
        @Nullable
        private EntityItem pursuedBannerItemEntity;

        public b(final T entityraider) { // CraftBukkit - decompile error
            this.mob = entityraider;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.cannotPickUpBanner()) {
                return false;
            } else {
                Int2LongOpenHashMap int2longopenhashmap = new Int2LongOpenHashMap();
                double d0 = EntityRaider.this.getAttributeValue(GenericAttributes.FOLLOW_RANGE);

                for (EntityItem entityitem : this.mob.level().getEntitiesOfClass(EntityItem.class, this.mob.getBoundingBox().inflate(d0, 8.0D, d0), EntityRaider.ALLOWED_ITEMS)) {
                    long i = this.unreachableBannerCache.getOrDefault(entityitem.getId(), Long.MIN_VALUE);

                    if (EntityRaider.this.level().getGameTime() < i) {
                        int2longopenhashmap.put(entityitem.getId(), i);
                    } else {
                        PathEntity pathentity = this.mob.getNavigation().createPath(entityitem, 1);

                        if (pathentity != null && pathentity.canReach()) {
                            this.pathToBanner = pathentity;
                            this.pursuedBannerItemEntity = entityitem;
                            return true;
                        }

                        int2longopenhashmap.put(entityitem.getId(), EntityRaider.this.level().getGameTime() + 600L);
                    }
                }

                this.unreachableBannerCache = int2longopenhashmap;
                return false;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return this.pursuedBannerItemEntity != null && this.pathToBanner != null ? (this.pursuedBannerItemEntity.isRemoved() ? false : (this.pathToBanner.isDone() ? false : !this.cannotPickUpBanner())) : false;
        }

        private boolean cannotPickUpBanner() {
            if (!this.mob.hasActiveRaid()) {
                return true;
            } else if (this.mob.getCurrentRaid().isOver()) {
                return true;
            } else if (!this.mob.canBeLeader()) {
                return true;
            } else if (ItemStack.matches(this.mob.getItemBySlot(EnumItemSlot.HEAD), Raid.getOminousBannerInstance(this.mob.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)))) {
                return true;
            } else {
                EntityRaider entityraider = EntityRaider.this.raid.getLeader(this.mob.getWave());

                return entityraider != null && entityraider.isAlive();
            }
        }

        @Override
        public void start() {
            this.mob.getNavigation().moveTo(this.pathToBanner, (double) 1.15F);
        }

        @Override
        public void stop() {
            this.pathToBanner = null;
            this.pursuedBannerItemEntity = null;
        }

        @Override
        public void tick() {
            if (this.pursuedBannerItemEntity != null && this.pursuedBannerItemEntity.closerThan(this.mob, 1.414D)) {
                this.mob.pickUpItem(getServerLevel(EntityRaider.this.level()), this.pursuedBannerItemEntity);
            }

        }
    }

    public class c extends PathfinderGoal {

        private final EntityRaider mob;

        c(final EntityRaider entityraider) {
            this.mob = entityraider;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canUse() {
            Raid raid = this.mob.getCurrentRaid();

            return this.mob.isAlive() && this.mob.getTarget() == null && raid != null && raid.isLoss();
        }

        @Override
        public void start() {
            this.mob.setCelebrating(true);
            super.start();
        }

        @Override
        public void stop() {
            this.mob.setCelebrating(false);
            super.stop();
        }

        @Override
        public void tick() {
            if (!this.mob.isSilent() && this.mob.random.nextInt(this.adjustedTickDelay(100)) == 0) {
                EntityRaider.this.makeSound(EntityRaider.this.getCelebrateSound());
            }

            if (!this.mob.isPassenger() && this.mob.random.nextInt(this.adjustedTickDelay(50)) == 0) {
                this.mob.getJumpControl().jump();
            }

            super.tick();
        }
    }

    protected static class a extends PathfinderGoal {

        private final EntityRaider mob;
        private final float hostileRadiusSqr;
        public final PathfinderTargetCondition shoutTargeting = PathfinderTargetCondition.forNonCombat().range(8.0D).ignoreLineOfSight().ignoreInvisibilityTesting();

        public a(EntityIllagerAbstract entityillagerabstract, float f) {
            this.mob = entityillagerabstract;
            this.hostileRadiusSqr = f * f;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE, PathfinderGoal.Type.LOOK));
        }

        @Override
        public boolean canUse() {
            EntityLiving entityliving = this.mob.getLastHurtByMob();

            return this.mob.getCurrentRaid() == null && this.mob.isPatrolling() && this.mob.getTarget() != null && !this.mob.isAggressive() && (entityliving == null || entityliving.getType() != EntityTypes.PLAYER);
        }

        @Override
        public void start() {
            super.start();
            this.mob.getNavigation().stop();

            for (EntityRaider entityraider : getServerLevel((Entity) this.mob).getNearbyEntities(EntityRaider.class, this.shoutTargeting, this.mob, this.mob.getBoundingBox().inflate(8.0D, 8.0D, 8.0D))) {
                entityraider.setTarget(this.mob.getTarget(), org.bukkit.event.entity.EntityTargetEvent.TargetReason.FOLLOW_LEADER, true); // CraftBukkit
            }

        }

        @Override
        public void stop() {
            super.stop();
            EntityLiving entityliving = this.mob.getTarget();

            if (entityliving != null) {
                for (EntityRaider entityraider : getServerLevel((Entity) this.mob).getNearbyEntities(EntityRaider.class, this.shoutTargeting, this.mob, this.mob.getBoundingBox().inflate(8.0D, 8.0D, 8.0D))) {
                    entityraider.setTarget(entityliving, org.bukkit.event.entity.EntityTargetEvent.TargetReason.FOLLOW_LEADER, true); // CraftBukkit
                    entityraider.setAggressive(true);
                }

                this.mob.setAggressive(true);
            }

        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            EntityLiving entityliving = this.mob.getTarget();

            if (entityliving != null) {
                if (this.mob.distanceToSqr((Entity) entityliving) > (double) this.hostileRadiusSqr) {
                    this.mob.getLookControl().setLookAt(entityliving, 30.0F, 30.0F);
                    if (this.mob.random.nextInt(50) == 0) {
                        this.mob.playAmbientSound();
                    }
                } else {
                    this.mob.setAggressive(true);
                }

                super.tick();
            }
        }
    }

    private static class d extends PathfinderGoal {

        private final EntityRaider raider;
        private final double speedModifier;
        private BlockPosition poiPos;
        private final List<BlockPosition> visited = Lists.newArrayList();
        private final int distanceToPoi;
        private boolean stuck;

        public d(EntityRaider entityraider, double d0, int i) {
            this.raider = entityraider;
            this.speedModifier = d0;
            this.distanceToPoi = i;
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canUse() {
            this.updateVisited();
            return this.isValidRaid() && this.hasSuitablePoi() && this.raider.getTarget() == null;
        }

        private boolean isValidRaid() {
            return this.raider.hasActiveRaid() && !this.raider.getCurrentRaid().isOver();
        }

        private boolean hasSuitablePoi() {
            WorldServer worldserver = (WorldServer) this.raider.level();
            BlockPosition blockposition = this.raider.blockPosition();
            Optional<BlockPosition> optional = worldserver.getPoiManager().getRandom((holder) -> {
                return holder.is(PoiTypes.HOME);
            }, this::hasNotVisited, VillagePlace.Occupancy.ANY, blockposition, 48, this.raider.random);

            if (optional.isEmpty()) {
                return false;
            } else {
                this.poiPos = ((BlockPosition) optional.get()).immutable();
                return true;
            }
        }

        @Override
        public boolean canContinueToUse() {
            return this.raider.getNavigation().isDone() ? false : this.raider.getTarget() == null && !this.poiPos.closerToCenterThan(this.raider.position(), (double) (this.raider.getBbWidth() + (float) this.distanceToPoi)) && !this.stuck;
        }

        @Override
        public void stop() {
            if (this.poiPos.closerToCenterThan(this.raider.position(), (double) this.distanceToPoi)) {
                this.visited.add(this.poiPos);
            }

        }

        @Override
        public void start() {
            super.start();
            this.raider.setNoActionTime(0);
            this.raider.getNavigation().moveTo((double) this.poiPos.getX(), (double) this.poiPos.getY(), (double) this.poiPos.getZ(), this.speedModifier);
            this.stuck = false;
        }

        @Override
        public void tick() {
            if (this.raider.getNavigation().isDone()) {
                Vec3D vec3d = Vec3D.atBottomCenterOf(this.poiPos);
                Vec3D vec3d1 = DefaultRandomPos.getPosTowards(this.raider, 16, 7, vec3d, (double) ((float) Math.PI / 10F));

                if (vec3d1 == null) {
                    vec3d1 = DefaultRandomPos.getPosTowards(this.raider, 8, 7, vec3d, (double) ((float) Math.PI / 2F));
                }

                if (vec3d1 == null) {
                    this.stuck = true;
                    return;
                }

                this.raider.getNavigation().moveTo(vec3d1.x, vec3d1.y, vec3d1.z, this.speedModifier);
            }

        }

        private boolean hasNotVisited(BlockPosition blockposition) {
            for (BlockPosition blockposition1 : this.visited) {
                if (Objects.equals(blockposition, blockposition1)) {
                    return false;
                }
            }

            return true;
        }

        private void updateVisited() {
            if (this.visited.size() > 2) {
                this.visited.remove(0);
            }

        }
    }
}

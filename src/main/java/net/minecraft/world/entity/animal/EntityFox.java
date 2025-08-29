package net.minecraft.world.entity.animal;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleParamItem;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.stats.StatisticList;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsFluid;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.INamable;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityExperienceOrb;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTameableAnimal;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerLook;
import net.minecraft.world.entity.ai.control.ControllerMove;
import net.minecraft.world.entity.ai.goal.ClimbOnTopOfPowderSnowGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalAvoidTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFleeSun;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowParent;
import net.minecraft.world.entity.ai.goal.PathfinderGoalGotoTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLeapAtTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalNearestVillage;
import net.minecraft.world.entity.ai.goal.PathfinderGoalPanic;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalWaterJumpAbstract;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.animal.wolf.EntityWolf;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.monster.EntityMonster;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockSweetBerryBush;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityFox extends EntityAnimal {

    private static final DataWatcherObject<Integer> DATA_TYPE_ID = DataWatcher.<Integer>defineId(EntityFox.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Byte> DATA_FLAGS_ID = DataWatcher.<Byte>defineId(EntityFox.class, DataWatcherRegistry.BYTE);
    private static final int FLAG_SITTING = 1;
    public static final int FLAG_CROUCHING = 4;
    public static final int FLAG_INTERESTED = 8;
    public static final int FLAG_POUNCING = 16;
    private static final int FLAG_SLEEPING = 32;
    private static final int FLAG_FACEPLANTED = 64;
    private static final int FLAG_DEFENDING = 128;
    public static final DataWatcherObject<Optional<EntityReference<EntityLiving>>> DATA_TRUSTED_ID_0 = DataWatcher.<Optional<EntityReference<EntityLiving>>>defineId(EntityFox.class, DataWatcherRegistry.OPTIONAL_LIVING_ENTITY_REFERENCE);
    public static final DataWatcherObject<Optional<EntityReference<EntityLiving>>> DATA_TRUSTED_ID_1 = DataWatcher.<Optional<EntityReference<EntityLiving>>>defineId(EntityFox.class, DataWatcherRegistry.OPTIONAL_LIVING_ENTITY_REFERENCE);
    static final Predicate<EntityItem> ALLOWED_ITEMS = (entityitem) -> {
        return !entityitem.hasPickUpDelay() && entityitem.isAlive();
    };
    private static final Predicate<Entity> TRUSTED_TARGET_SELECTOR = (entity) -> {
        if (!(entity instanceof EntityLiving entityliving)) {
            return false;
        } else {
            return entityliving.getLastHurtMob() != null && entityliving.getLastHurtMobTimestamp() < entityliving.tickCount + 600;
        }
    };
    static final Predicate<Entity> STALKABLE_PREY = (entity) -> {
        return entity instanceof EntityChicken || entity instanceof EntityRabbit;
    };
    private static final Predicate<Entity> AVOID_PLAYERS = (entity) -> {
        return !entity.isDiscrete() && IEntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity);
    };
    private static final int MIN_TICKS_BEFORE_EAT = 600;
    private static final EntitySize BABY_DIMENSIONS = EntityTypes.FOX.getDimensions().scale(0.5F).withEyeHeight(0.2975F);
    private static final Codec<List<EntityReference<EntityLiving>>> TRUSTED_LIST_CODEC = EntityReference.<EntityLiving>codec().listOf(); // CraftBukkit - decompile error
    private static final boolean DEFAULT_SLEEPING = false;
    private static final boolean DEFAULT_SITTING = false;
    private static final boolean DEFAULT_CROUCHING = false;
    private PathfinderGoal landTargetGoal;
    private PathfinderGoal turtleEggTargetGoal;
    private PathfinderGoal fishTargetGoal;
    private float interestedAngle;
    private float interestedAngleO;
    float crouchAmount;
    float crouchAmountO;
    private int ticksSinceEaten;

    public EntityFox(EntityTypes<? extends EntityFox> entitytypes, World world) {
        super(entitytypes, world);
        this.lookControl = new EntityFox.k();
        this.moveControl = new EntityFox.m();
        this.setPathfindingMalus(PathType.DANGER_OTHER, 0.0F);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, 0.0F);
        this.setCanPickUpLoot(true);
        this.getNavigation().setRequiredPathLength(32.0F);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityFox.DATA_TRUSTED_ID_0, Optional.empty());
        datawatcher_a.define(EntityFox.DATA_TRUSTED_ID_1, Optional.empty());
        datawatcher_a.define(EntityFox.DATA_TYPE_ID, EntityFox.Type.DEFAULT.getId());
        datawatcher_a.define(EntityFox.DATA_FLAGS_ID, (byte) 0);
    }

    @Override
    protected void registerGoals() {
        this.landTargetGoal = new PathfinderGoalNearestAttackableTarget(this, EntityAnimal.class, 10, false, false, (entityliving, worldserver) -> {
            return entityliving instanceof EntityChicken || entityliving instanceof EntityRabbit;
        });
        this.turtleEggTargetGoal = new PathfinderGoalNearestAttackableTarget(this, EntityTurtle.class, 10, false, false, EntityTurtle.BABY_ON_LAND_SELECTOR);
        this.fishTargetGoal = new PathfinderGoalNearestAttackableTarget(this, EntityFish.class, 20, false, false, (entityliving, worldserver) -> {
            return entityliving instanceof EntityFishSchool;
        });
        this.goalSelector.addGoal(0, new EntityFox.g());
        this.goalSelector.addGoal(0, new ClimbOnTopOfPowderSnowGoal(this, this.level()));
        this.goalSelector.addGoal(1, new EntityFox.b());
        this.goalSelector.addGoal(2, new EntityFox.n(2.2D));
        this.goalSelector.addGoal(3, new EntityFox.e(1.0D));
        this.goalSelector.addGoal(4, new PathfinderGoalAvoidTarget<>(this, EntityHuman.class, 16.0F, 1.6D, 1.4D, (entityliving) -> { // CraftBukkit - decompile error
            return EntityFox.AVOID_PLAYERS.test(entityliving) && !this.trusts(entityliving) && !this.isDefending();
        }));
        this.goalSelector.addGoal(4, new PathfinderGoalAvoidTarget(this, EntityWolf.class, 8.0F, 1.6D, 1.4D, (entityliving) -> {
            return !((EntityWolf) entityliving).isTame() && !this.isDefending();
        }));
        this.goalSelector.addGoal(4, new PathfinderGoalAvoidTarget(this, EntityPolarBear.class, 8.0F, 1.6D, 1.4D, (entityliving) -> {
            return !this.isDefending();
        }));
        this.goalSelector.addGoal(5, new EntityFox.u());
        this.goalSelector.addGoal(6, new EntityFox.o());
        this.goalSelector.addGoal(6, new EntityFox.s(1.25D));
        this.goalSelector.addGoal(7, new EntityFox.l((double) 1.2F, true));
        this.goalSelector.addGoal(7, new EntityFox.t());
        this.goalSelector.addGoal(8, new EntityFox.h(this, 1.25D));
        this.goalSelector.addGoal(9, new EntityFox.q(32, 200));
        this.goalSelector.addGoal(10, new EntityFox.f((double) 1.2F, 12, 1));
        this.goalSelector.addGoal(10, new PathfinderGoalLeapAtTarget(this, 0.4F));
        this.goalSelector.addGoal(11, new PathfinderGoalRandomStrollLand(this, 1.0D));
        this.goalSelector.addGoal(11, new EntityFox.p());
        this.goalSelector.addGoal(12, new EntityFox.j(this, EntityHuman.class, 24.0F));
        this.goalSelector.addGoal(13, new EntityFox.r());
        this.targetSelector.addGoal(3, new EntityFox.a(EntityLiving.class, false, false, (entityliving, worldserver) -> {
            return EntityFox.TRUSTED_TARGET_SELECTOR.test(entityliving) && !this.trusts(entityliving);
        }));
    }

    @Override
    public void aiStep() {
        if (!this.level().isClientSide && this.isAlive() && this.isEffectiveAi()) {
            ++this.ticksSinceEaten;
            ItemStack itemstack = this.getItemBySlot(EnumItemSlot.MAINHAND);

            if (this.canEat(itemstack)) {
                if (this.ticksSinceEaten > 600) {
                    ItemStack itemstack1 = itemstack.finishUsingItem(this.level(), this);

                    if (!itemstack1.isEmpty()) {
                        this.setItemSlot(EnumItemSlot.MAINHAND, itemstack1);
                    }

                    this.ticksSinceEaten = 0;
                } else if (this.ticksSinceEaten > 560 && this.random.nextFloat() < 0.1F) {
                    this.playEatingSound();
                    this.level().broadcastEntityEvent(this, (byte) 45);
                }
            }

            EntityLiving entityliving = this.getTarget();

            if (entityliving == null || !entityliving.isAlive()) {
                this.setIsCrouching(false);
                this.setIsInterested(false);
            }
        }

        if (this.isSleeping() || this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        }

        super.aiStep();
        if (this.isDefending() && this.random.nextFloat() < 0.05F) {
            this.playSound(SoundEffects.FOX_AGGRO, 1.0F, 1.0F);
        }

    }

    @Override
    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    private boolean canEat(ItemStack itemstack) {
        return itemstack.has(DataComponents.FOOD) && this.getTarget() == null && this.onGround() && !this.isSleeping();
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource randomsource, DifficultyDamageScaler difficultydamagescaler) {
        if (randomsource.nextFloat() < 0.2F) {
            float f = randomsource.nextFloat();
            ItemStack itemstack;

            if (f < 0.05F) {
                itemstack = new ItemStack(Items.EMERALD);
            } else if (f < 0.2F) {
                itemstack = new ItemStack(Items.EGG);
            } else if (f < 0.4F) {
                itemstack = randomsource.nextBoolean() ? new ItemStack(Items.RABBIT_FOOT) : new ItemStack(Items.RABBIT_HIDE);
            } else if (f < 0.6F) {
                itemstack = new ItemStack(Items.WHEAT);
            } else if (f < 0.8F) {
                itemstack = new ItemStack(Items.LEATHER);
            } else {
                itemstack = new ItemStack(Items.FEATHER);
            }

            this.setItemSlot(EnumItemSlot.MAINHAND, itemstack);
        }

    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 45) {
            ItemStack itemstack = this.getItemBySlot(EnumItemSlot.MAINHAND);

            if (!itemstack.isEmpty()) {
                for (int i = 0; i < 8; ++i) {
                    Vec3D vec3d = (new Vec3D(((double) this.random.nextFloat() - 0.5D) * 0.1D, Math.random() * 0.1D + 0.1D, 0.0D)).xRot(-this.getXRot() * ((float) Math.PI / 180F)).yRot(-this.getYRot() * ((float) Math.PI / 180F));

                    this.level().addParticle(new ParticleParamItem(Particles.ITEM, itemstack), this.getX() + this.getLookAngle().x / 2.0D, this.getY(), this.getZ() + this.getLookAngle().z / 2.0D, vec3d.x, vec3d.y + 0.05D, vec3d.z);
                }
            }
        } else {
            super.handleEntityEvent(b0);
        }

    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MOVEMENT_SPEED, (double) 0.3F).add(GenericAttributes.MAX_HEALTH, 10.0D).add(GenericAttributes.ATTACK_DAMAGE, 2.0D).add(GenericAttributes.SAFE_FALL_DISTANCE, 5.0D).add(GenericAttributes.FOLLOW_RANGE, 32.0D);
    }

    @Nullable
    @Override
    public EntityFox getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        EntityFox entityfox = EntityTypes.FOX.create(worldserver, EntitySpawnReason.BREEDING);

        if (entityfox != null) {
            entityfox.setVariant(this.random.nextBoolean() ? this.getVariant() : ((EntityFox) entityageable).getVariant());
        }

        return entityfox;
    }

    public static boolean checkFoxSpawnRules(EntityTypes<EntityFox> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return generatoraccess.getBlockState(blockposition.below()).is(TagsBlock.FOXES_SPAWNABLE_ON) && isBrightEnoughToSpawn(generatoraccess, blockposition);
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        Holder<BiomeBase> holder = worldaccess.getBiome(this.blockPosition());
        EntityFox.Type entityfox_type = EntityFox.Type.byBiome(holder);
        boolean flag = false;

        if (groupdataentity instanceof EntityFox.i entityfox_i) {
            entityfox_type = entityfox_i.variant;
            if (entityfox_i.getGroupSize() >= 2) {
                flag = true;
            }
        } else {
            groupdataentity = new EntityFox.i(entityfox_type);
        }

        this.setVariant(entityfox_type);
        if (flag) {
            this.setAge(-24000);
        }

        if (worldaccess instanceof WorldServer) {
            this.setTargetGoals();
        }

        this.populateDefaultEquipmentSlots(worldaccess.getRandom(), difficultydamagescaler);
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    private void setTargetGoals() {
        if (this.getVariant() == EntityFox.Type.RED) {
            this.targetSelector.addGoal(4, this.landTargetGoal);
            this.targetSelector.addGoal(4, this.turtleEggTargetGoal);
            this.targetSelector.addGoal(6, this.fishTargetGoal);
        } else {
            this.targetSelector.addGoal(4, this.fishTargetGoal);
            this.targetSelector.addGoal(6, this.landTargetGoal);
            this.targetSelector.addGoal(6, this.turtleEggTargetGoal);
        }

    }

    @Override
    protected void playEatingSound() {
        this.playSound(SoundEffects.FOX_EAT, 1.0F, 1.0F);
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return this.isBaby() ? EntityFox.BABY_DIMENSIONS : super.getDefaultDimensions(entitypose);
    }

    public EntityFox.Type getVariant() {
        return EntityFox.Type.byId((Integer) this.entityData.get(EntityFox.DATA_TYPE_ID));
    }

    public void setVariant(EntityFox.Type entityfox_type) {
        this.entityData.set(EntityFox.DATA_TYPE_ID, entityfox_type.getId());
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.FOX_VARIANT ? castComponentValue(datacomponenttype, this.getVariant()) : super.get(datacomponenttype));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.FOX_VARIANT);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.FOX_VARIANT) {
            this.setVariant((EntityFox.Type) castComponentValue(DataComponents.FOX_VARIANT, t0));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }

    Stream<EntityReference<EntityLiving>> getTrustedEntities() {
        return Stream.concat(((Optional) this.entityData.get(EntityFox.DATA_TRUSTED_ID_0)).stream(), ((Optional) this.entityData.get(EntityFox.DATA_TRUSTED_ID_1)).stream());
    }

    void addTrustedEntity(EntityLiving entityliving) {
        this.addTrustedEntity(new EntityReference(entityliving));
    }

    private void addTrustedEntity(EntityReference<EntityLiving> entityreference) {
        if (((Optional) this.entityData.get(EntityFox.DATA_TRUSTED_ID_0)).isPresent()) {
            this.entityData.set(EntityFox.DATA_TRUSTED_ID_1, Optional.of(entityreference));
        } else {
            this.entityData.set(EntityFox.DATA_TRUSTED_ID_0, Optional.of(entityreference));
        }

    }

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.store("Trusted", EntityFox.TRUSTED_LIST_CODEC, this.getTrustedEntities().toList());
        nbttagcompound.putBoolean("Sleeping", this.isSleeping());
        nbttagcompound.store("Type", EntityFox.Type.CODEC, this.getVariant());
        nbttagcompound.putBoolean("Sitting", this.isSitting());
        nbttagcompound.putBoolean("Crouching", this.isCrouching());
    }

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.clearTrusted();
        (nbttagcompound.read("Trusted", EntityFox.TRUSTED_LIST_CODEC).orElse(List.of())).forEach(this::addTrustedEntity); // CraftBukkit - decompile error
        this.setSleeping(nbttagcompound.getBooleanOr("Sleeping", false));
        this.setVariant((EntityFox.Type) nbttagcompound.read("Type", EntityFox.Type.CODEC).orElse(EntityFox.Type.DEFAULT));
        this.setSitting(nbttagcompound.getBooleanOr("Sitting", false));
        this.setIsCrouching(nbttagcompound.getBooleanOr("Crouching", false));
        if (this.level() instanceof WorldServer) {
            this.setTargetGoals();
        }

    }

    private void clearTrusted() {
        this.entityData.set(EntityFox.DATA_TRUSTED_ID_0, Optional.empty());
        this.entityData.set(EntityFox.DATA_TRUSTED_ID_1, Optional.empty());
    }

    public boolean isSitting() {
        return this.getFlag(1);
    }

    public void setSitting(boolean flag) {
        this.setFlag(1, flag);
    }

    public boolean isFaceplanted() {
        return this.getFlag(64);
    }

    void setFaceplanted(boolean flag) {
        this.setFlag(64, flag);
    }

    boolean isDefending() {
        return this.getFlag(128);
    }

    void setDefending(boolean flag) {
        this.setFlag(128, flag);
    }

    @Override
    public boolean isSleeping() {
        return this.getFlag(32);
    }

    public void setSleeping(boolean flag) {
        this.setFlag(32, flag);
    }

    private void setFlag(int i, boolean flag) {
        if (flag) {
            this.entityData.set(EntityFox.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(EntityFox.DATA_FLAGS_ID) | i));
        } else {
            this.entityData.set(EntityFox.DATA_FLAGS_ID, (byte) ((Byte) this.entityData.get(EntityFox.DATA_FLAGS_ID) & ~i));
        }

    }

    private boolean getFlag(int i) {
        return ((Byte) this.entityData.get(EntityFox.DATA_FLAGS_ID) & i) != 0;
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EnumItemSlot enumitemslot) {
        return enumitemslot == EnumItemSlot.MAINHAND && this.canPickUpLoot();
    }

    @Override
    public boolean canHoldItem(ItemStack itemstack) {
        ItemStack itemstack1 = this.getItemBySlot(EnumItemSlot.MAINHAND);

        return itemstack1.isEmpty() || this.ticksSinceEaten > 0 && itemstack.has(DataComponents.FOOD) && !itemstack1.has(DataComponents.FOOD);
    }

    private void spitOutItem(ItemStack itemstack) {
        if (!itemstack.isEmpty() && !this.level().isClientSide) {
            EntityItem entityitem = new EntityItem(this.level(), this.getX() + this.getLookAngle().x, this.getY() + 1.0D, this.getZ() + this.getLookAngle().z, itemstack);

            entityitem.setPickUpDelay(40);
            entityitem.setThrower(this);
            this.playSound(SoundEffects.FOX_SPIT, 1.0F, 1.0F);
            this.level().addFreshEntity(entityitem);
        }
    }

    private void dropItemStack(ItemStack itemstack) {
        EntityItem entityitem = new EntityItem(this.level(), this.getX(), this.getY(), this.getZ(), itemstack);

        this.level().addFreshEntity(entityitem);
    }

    @Override
    protected void pickUpItem(WorldServer worldserver, EntityItem entityitem) {
        ItemStack itemstack = entityitem.getItem();

        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entityitem, itemstack.getCount() - 1, !this.canHoldItem(itemstack)).isCancelled()) { // CraftBukkit - call EntityPickupItemEvent
            itemstack = entityitem.getItem(); // CraftBukkit - update ItemStack from event
            int i = itemstack.getCount();

            if (i > 1) {
                this.dropItemStack(itemstack.split(i - 1));
            }

            this.spitOutItem(this.getItemBySlot(EnumItemSlot.MAINHAND));
            this.onItemPickup(entityitem);
            this.setItemSlot(EnumItemSlot.MAINHAND, itemstack.split(1));
            this.setGuaranteedDrop(EnumItemSlot.MAINHAND);
            this.take(entityitem, itemstack.getCount());
            entityitem.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            this.ticksSinceEaten = 0;
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isEffectiveAi()) {
            boolean flag = this.isInWater();

            if (flag || this.getTarget() != null || this.level().isThundering()) {
                this.wakeUp();
            }

            if (flag || this.isSleeping()) {
                this.setSitting(false);
            }

            if (this.isFaceplanted() && this.level().random.nextFloat() < 0.2F) {
                BlockPosition blockposition = this.blockPosition();
                IBlockData iblockdata = this.level().getBlockState(blockposition);

                this.level().levelEvent(2001, blockposition, Block.getId(iblockdata));
            }
        }

        this.interestedAngleO = this.interestedAngle;
        if (this.isInterested()) {
            this.interestedAngle += (1.0F - this.interestedAngle) * 0.4F;
        } else {
            this.interestedAngle += (0.0F - this.interestedAngle) * 0.4F;
        }

        this.crouchAmountO = this.crouchAmount;
        if (this.isCrouching()) {
            this.crouchAmount += 0.2F;
            if (this.crouchAmount > 3.0F) {
                this.crouchAmount = 3.0F;
            }
        } else {
            this.crouchAmount = 0.0F;
        }

    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.FOX_FOOD);
    }

    @Override
    protected void onOffspringSpawnedFromEgg(EntityHuman entityhuman, EntityInsentient entityinsentient) {
        ((EntityFox) entityinsentient).addTrustedEntity(entityhuman);
    }

    public boolean isPouncing() {
        return this.getFlag(16);
    }

    public void setIsPouncing(boolean flag) {
        this.setFlag(16, flag);
    }

    public boolean isJumping() {
        return this.jumping;
    }

    public boolean isFullyCrouched() {
        return this.crouchAmount == 3.0F;
    }

    public void setIsCrouching(boolean flag) {
        this.setFlag(4, flag);
    }

    @Override
    public boolean isCrouching() {
        return this.getFlag(4);
    }

    public void setIsInterested(boolean flag) {
        this.setFlag(8, flag);
    }

    public boolean isInterested() {
        return this.getFlag(8);
    }

    public float getHeadRollAngle(float f) {
        return MathHelper.lerp(f, this.interestedAngleO, this.interestedAngle) * 0.11F * (float) Math.PI;
    }

    public float getCrouchAmount(float f) {
        return MathHelper.lerp(f, this.crouchAmountO, this.crouchAmount);
    }

    @Override
    public void setTarget(@Nullable EntityLiving entityliving) {
        if (this.isDefending() && entityliving == null) {
            this.setDefending(false);
        }

        super.setTarget(entityliving);
    }

    void wakeUp() {
        this.setSleeping(false);
    }

    void clearStates() {
        this.setIsInterested(false);
        this.setIsCrouching(false);
        this.setSitting(false);
        this.setSleeping(false);
        this.setDefending(false);
        this.setFaceplanted(false);
    }

    boolean canMove() {
        return !this.isSleeping() && !this.isSitting() && !this.isFaceplanted();
    }

    @Override
    public void playAmbientSound() {
        SoundEffect soundeffect = this.getAmbientSound();

        if (soundeffect == SoundEffects.FOX_SCREECH) {
            this.playSound(soundeffect, 2.0F, this.getVoicePitch());
        } else {
            super.playAmbientSound();
        }

    }

    @Nullable
    @Override
    protected SoundEffect getAmbientSound() {
        if (this.isSleeping()) {
            return SoundEffects.FOX_SLEEP;
        } else {
            if (!this.level().isBrightOutside() && this.random.nextFloat() < 0.1F) {
                List<EntityHuman> list = this.level().<EntityHuman>getEntitiesOfClass(EntityHuman.class, this.getBoundingBox().inflate(16.0D, 16.0D, 16.0D), IEntitySelector.NO_SPECTATORS);

                if (list.isEmpty()) {
                    return SoundEffects.FOX_SCREECH;
                }
            }

            return SoundEffects.FOX_AMBIENT;
        }
    }

    @Nullable
    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.FOX_HURT;
    }

    @Nullable
    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.FOX_DEATH;
    }

    boolean trusts(EntityLiving entityliving) {
        return this.getTrustedEntities().anyMatch((entityreference) -> {
            return entityreference.matches(entityliving);
        });
    }

    @Override
    protected void dropAllDeathLoot(WorldServer worldserver, DamageSource damagesource) {
        ItemStack itemstack = this.getItemBySlot(EnumItemSlot.MAINHAND);

        if (!itemstack.isEmpty()) {
            this.spawnAtLocation(worldserver, itemstack);
            this.setItemSlot(EnumItemSlot.MAINHAND, ItemStack.EMPTY);
        }

        super.dropAllDeathLoot(worldserver, damagesource);
    }

    public static boolean isPathClear(EntityFox entityfox, EntityLiving entityliving) {
        double d0 = entityliving.getZ() - entityfox.getZ();
        double d1 = entityliving.getX() - entityfox.getX();
        double d2 = d0 / d1;
        int i = 6;

        for (int j = 0; j < 6; ++j) {
            double d3 = d2 == 0.0D ? 0.0D : d0 * (double) ((float) j / 6.0F);
            double d4 = d2 == 0.0D ? d1 * (double) ((float) j / 6.0F) : d3 / d2;

            for (int k = 1; k < 4; ++k) {
                if (!entityfox.level().getBlockState(BlockPosition.containing(entityfox.getX() + d4, entityfox.getY() + (double) k, entityfox.getZ() + d3)).canBeReplaced()) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public Vec3D getLeashOffset() {
        return new Vec3D(0.0D, (double) (0.55F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    public static enum Type implements INamable {

        RED(0, "red"), SNOW(1, "snow");

        public static final EntityFox.Type DEFAULT = EntityFox.Type.RED;
        public static final INamable.a<EntityFox.Type> CODEC = INamable.<EntityFox.Type>fromEnum(EntityFox.Type::values);
        private static final IntFunction<EntityFox.Type> BY_ID = ByIdMap.<EntityFox.Type>continuous(EntityFox.Type::getId, values(), ByIdMap.a.ZERO);
        public static final StreamCodec<ByteBuf, EntityFox.Type> STREAM_CODEC = ByteBufCodecs.idMapper(EntityFox.Type.BY_ID, EntityFox.Type::getId);
        private final int id;
        private final String name;

        private Type(final int i, final String s) {
            this.id = i;
            this.name = s;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public int getId() {
            return this.id;
        }

        public static EntityFox.Type byId(int i) {
            return (EntityFox.Type) EntityFox.Type.BY_ID.apply(i);
        }

        public static EntityFox.Type byBiome(Holder<BiomeBase> holder) {
            return holder.is(BiomeTags.SPAWNS_SNOW_FOXES) ? EntityFox.Type.SNOW : EntityFox.Type.RED;
        }
    }

    private class p extends PathfinderGoal {

        public p() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!EntityFox.this.getItemBySlot(EnumItemSlot.MAINHAND).isEmpty()) {
                return false;
            } else if (EntityFox.this.getTarget() == null && EntityFox.this.getLastHurtByMob() == null) {
                if (!EntityFox.this.canMove()) {
                    return false;
                } else if (EntityFox.this.getRandom().nextInt(reducedTickDelay(10)) != 0) {
                    return false;
                } else {
                    List<EntityItem> list = EntityFox.this.level().<EntityItem>getEntitiesOfClass(EntityItem.class, EntityFox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), EntityFox.ALLOWED_ITEMS);

                    return !list.isEmpty() && EntityFox.this.getItemBySlot(EnumItemSlot.MAINHAND).isEmpty();
                }
            } else {
                return false;
            }
        }

        @Override
        public void tick() {
            List<EntityItem> list = EntityFox.this.level().<EntityItem>getEntitiesOfClass(EntityItem.class, EntityFox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), EntityFox.ALLOWED_ITEMS);
            ItemStack itemstack = EntityFox.this.getItemBySlot(EnumItemSlot.MAINHAND);

            if (itemstack.isEmpty() && !list.isEmpty()) {
                EntityFox.this.getNavigation().moveTo((Entity) list.get(0), (double) 1.2F);
            }

        }

        @Override
        public void start() {
            List<EntityItem> list = EntityFox.this.level().<EntityItem>getEntitiesOfClass(EntityItem.class, EntityFox.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), EntityFox.ALLOWED_ITEMS);

            if (!list.isEmpty()) {
                EntityFox.this.getNavigation().moveTo((Entity) list.get(0), (double) 1.2F);
            }

        }
    }

    private class m extends ControllerMove {

        public m() {
            super(EntityFox.this);
        }

        @Override
        public void tick() {
            if (EntityFox.this.canMove()) {
                super.tick();
            }

        }
    }

    private class u extends PathfinderGoal {

        public u() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE, PathfinderGoal.Type.LOOK));
        }

        @Override
        public boolean canUse() {
            if (EntityFox.this.isSleeping()) {
                return false;
            } else {
                EntityLiving entityliving = EntityFox.this.getTarget();

                return entityliving != null && entityliving.isAlive() && EntityFox.STALKABLE_PREY.test(entityliving) && EntityFox.this.distanceToSqr((Entity) entityliving) > 36.0D && !EntityFox.this.isCrouching() && !EntityFox.this.isInterested() && !EntityFox.this.jumping;
            }
        }

        @Override
        public void start() {
            EntityFox.this.setSitting(false);
            EntityFox.this.setFaceplanted(false);
        }

        @Override
        public void stop() {
            EntityLiving entityliving = EntityFox.this.getTarget();

            if (entityliving != null && EntityFox.isPathClear(EntityFox.this, entityliving)) {
                EntityFox.this.setIsInterested(true);
                EntityFox.this.setIsCrouching(true);
                EntityFox.this.getNavigation().stop();
                EntityFox.this.getLookControl().setLookAt(entityliving, (float) EntityFox.this.getMaxHeadYRot(), (float) EntityFox.this.getMaxHeadXRot());
            } else {
                EntityFox.this.setIsInterested(false);
                EntityFox.this.setIsCrouching(false);
            }

        }

        @Override
        public void tick() {
            EntityLiving entityliving = EntityFox.this.getTarget();

            if (entityliving != null) {
                EntityFox.this.getLookControl().setLookAt(entityliving, (float) EntityFox.this.getMaxHeadYRot(), (float) EntityFox.this.getMaxHeadXRot());
                if (EntityFox.this.distanceToSqr((Entity) entityliving) <= 36.0D) {
                    EntityFox.this.setIsInterested(true);
                    EntityFox.this.setIsCrouching(true);
                    EntityFox.this.getNavigation().stop();
                } else {
                    EntityFox.this.getNavigation().moveTo((Entity) entityliving, 1.5D);
                }

            }
        }
    }

    private class l extends PathfinderGoalMeleeAttack {

        public l(final double d0, final boolean flag) {
            super(EntityFox.this, d0, flag);
        }

        @Override
        protected void checkAndPerformAttack(EntityLiving entityliving) {
            if (this.canPerformAttack(entityliving)) {
                this.resetAttackCooldown();
                this.mob.doHurtTarget(getServerLevel((Entity) this.mob), entityliving);
                EntityFox.this.playSound(SoundEffects.FOX_BITE, 1.0F, 1.0F);
            }

        }

        @Override
        public void start() {
            EntityFox.this.setIsInterested(false);
            super.start();
        }

        @Override
        public boolean canUse() {
            return !EntityFox.this.isSitting() && !EntityFox.this.isSleeping() && !EntityFox.this.isCrouching() && !EntityFox.this.isFaceplanted() && super.canUse();
        }
    }

    private class e extends PathfinderGoalBreed {

        public e(final double d0) {
            super(EntityFox.this, d0);
        }

        @Override
        public void start() {
            ((EntityFox) this.animal).clearStates();
            ((EntityFox) this.partner).clearStates();
            super.start();
        }

        @Override
        protected void breed() {
            WorldServer worldserver = this.level;
            EntityFox entityfox = (EntityFox) this.animal.getBreedOffspring(worldserver, this.partner);

            if (entityfox != null) {
                EntityPlayer entityplayer = this.animal.getLoveCause();
                EntityPlayer entityplayer1 = this.partner.getLoveCause();
                EntityPlayer entityplayer2 = entityplayer;

                if (entityplayer != null) {
                    entityfox.addTrustedEntity(entityplayer);
                } else {
                    entityplayer2 = entityplayer1;
                }

                if (entityplayer1 != null && entityplayer != entityplayer1) {
                    entityfox.addTrustedEntity(entityplayer1);
                }
                // CraftBukkit start - call EntityBreedEvent
                entityfox.setAge(-24000);
                entityfox.snapTo(this.animal.getX(), this.animal.getY(), this.animal.getZ(), 0.0F, 0.0F);
                int experience = this.animal.getRandom().nextInt(7) + 1;
                org.bukkit.event.entity.EntityBreedEvent entityBreedEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityBreedEvent(entityfox, animal, partner, entityplayer, this.animal.breedItem, experience);
                if (entityBreedEvent.isCancelled()) {
                    return;
                }
                experience = entityBreedEvent.getExperience();
                // CraftBukkit end

                if (entityplayer2 != null) {
                    entityplayer2.awardStat(StatisticList.ANIMALS_BRED);
                    CriterionTriggers.BRED_ANIMALS.trigger(entityplayer2, this.animal, this.partner, entityfox);
                }

                this.animal.setAge(6000);
                this.partner.setAge(6000);
                this.animal.resetLove();
                this.partner.resetLove();
                worldserver.addFreshEntityWithPassengers(entityfox, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BREEDING); // CraftBukkit - added SpawnReason
                this.level.broadcastEntityEvent(this.animal, (byte) 18);
                if (worldserver.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
                    // CraftBukkit start - use event experience
                    if (experience > 0) {
                        this.level.addFreshEntity(new EntityExperienceOrb(this.level, this.animal.getX(), this.animal.getY(), this.animal.getZ(), experience));
                    }
                    // CraftBukkit end
                }

            }
        }
    }

    private class a extends PathfinderGoalNearestAttackableTarget<EntityLiving> {

        @Nullable
        private EntityLiving trustedLastHurtBy;
        @Nullable
        private EntityLiving trustedLastHurt;
        private int timestamp;

        public a(final Class oclass, final boolean flag, final boolean flag1, @Nullable final PathfinderTargetCondition.a pathfindertargetcondition_a) {
            super(EntityFox.this, oclass, 10, flag, flag1, pathfindertargetcondition_a);
        }

        @Override
        public boolean canUse() {
            if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
                return false;
            } else {
                WorldServer worldserver = getServerLevel(EntityFox.this.level());

                for (EntityReference<EntityLiving> entityreference : EntityFox.this.getTrustedEntities().toList()) {
                    EntityLiving entityliving = entityreference.getEntity(worldserver, EntityLiving.class);

                    if (entityliving != null) {
                        this.trustedLastHurt = entityliving;
                        this.trustedLastHurtBy = entityliving.getLastHurtByMob();
                        int i = entityliving.getLastHurtByMobTimestamp();

                        return i != this.timestamp && this.canAttack(this.trustedLastHurtBy, this.targetConditions);
                    }
                }

                return false;
            }
        }

        @Override
        public void start() {
            this.setTarget(this.trustedLastHurtBy);
            this.target = this.trustedLastHurtBy;
            if (this.trustedLastHurt != null) {
                this.timestamp = this.trustedLastHurt.getLastHurtByMobTimestamp();
            }

            EntityFox.this.playSound(SoundEffects.FOX_AGGRO, 1.0F, 1.0F);
            EntityFox.this.setDefending(true);
            EntityFox.this.wakeUp();
            super.start();
        }
    }

    private class s extends PathfinderGoalFleeSun {

        private int interval = reducedTickDelay(100);

        public s(final double d0) {
            super(EntityFox.this, d0);
        }

        @Override
        public boolean canUse() {
            if (!EntityFox.this.isSleeping() && this.mob.getTarget() == null) {
                if (EntityFox.this.level().isThundering() && EntityFox.this.level().canSeeSky(this.mob.blockPosition())) {
                    return this.setWantedPos();
                } else if (this.interval > 0) {
                    --this.interval;
                    return false;
                } else {
                    this.interval = 100;
                    BlockPosition blockposition = this.mob.blockPosition();

                    return EntityFox.this.level().isBrightOutside() && EntityFox.this.level().canSeeSky(blockposition) && !((WorldServer) EntityFox.this.level()).isVillage(blockposition) && this.setWantedPos();
                }
            } else {
                return false;
            }
        }

        @Override
        public void start() {
            EntityFox.this.clearStates();
            super.start();
        }
    }

    public class c implements PathfinderTargetCondition.a {

        public c() {}

        @Override
        public boolean test(EntityLiving entityliving, WorldServer worldserver) {
            if (entityliving instanceof EntityFox) {
                return false;
            } else if (!(entityliving instanceof EntityChicken) && !(entityliving instanceof EntityRabbit) && !(entityliving instanceof EntityMonster)) {
                if (entityliving instanceof EntityTameableAnimal) {
                    return !((EntityTameableAnimal) entityliving).isTame();
                } else {
                    if (entityliving instanceof EntityHuman) {
                        EntityHuman entityhuman = (EntityHuman) entityliving;

                        if (entityhuman.isSpectator() || entityhuman.isCreative()) {
                            return false;
                        }
                    }

                    if (EntityFox.this.trusts(entityliving)) {
                        return false;
                    } else {
                        return !entityliving.isSleeping() && !entityliving.isDiscrete();
                    }
                }
            } else {
                return true;
            }
        }
    }

    private abstract class d extends PathfinderGoal {

        private final PathfinderTargetCondition alertableTargeting = PathfinderTargetCondition.forCombat().range(12.0D).ignoreLineOfSight().selector(EntityFox.this.new c());

        d() {}

        protected boolean hasShelter() {
            BlockPosition blockposition = BlockPosition.containing(EntityFox.this.getX(), EntityFox.this.getBoundingBox().maxY, EntityFox.this.getZ());

            return !EntityFox.this.level().canSeeSky(blockposition) && EntityFox.this.getWalkTargetValue(blockposition) >= 0.0F;
        }

        protected boolean alertable() {
            return !getServerLevel(EntityFox.this.level()).getNearbyEntities(EntityLiving.class, this.alertableTargeting, EntityFox.this, EntityFox.this.getBoundingBox().inflate(12.0D, 6.0D, 12.0D)).isEmpty();
        }
    }

    private class t extends EntityFox.d {

        private static final int WAIT_TIME_BEFORE_SLEEP = reducedTickDelay(140);
        private int countdown;

        public t() {
            this.countdown = EntityFox.this.random.nextInt(EntityFox.t.WAIT_TIME_BEFORE_SLEEP);
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE, PathfinderGoal.Type.LOOK, PathfinderGoal.Type.JUMP));
        }

        @Override
        public boolean canUse() {
            return EntityFox.this.xxa == 0.0F && EntityFox.this.yya == 0.0F && EntityFox.this.zza == 0.0F ? this.canSleep() || EntityFox.this.isSleeping() : false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canSleep();
        }

        private boolean canSleep() {
            if (this.countdown > 0) {
                --this.countdown;
                return false;
            } else {
                return EntityFox.this.level().isBrightOutside() && this.hasShelter() && !this.alertable() && !EntityFox.this.isInPowderSnow;
            }
        }

        @Override
        public void stop() {
            this.countdown = EntityFox.this.random.nextInt(EntityFox.t.WAIT_TIME_BEFORE_SLEEP);
            EntityFox.this.clearStates();
        }

        @Override
        public void start() {
            EntityFox.this.setSitting(false);
            EntityFox.this.setIsCrouching(false);
            EntityFox.this.setIsInterested(false);
            EntityFox.this.setJumping(false);
            EntityFox.this.setSleeping(true);
            EntityFox.this.getNavigation().stop();
            EntityFox.this.getMoveControl().setWantedPosition(EntityFox.this.getX(), EntityFox.this.getY(), EntityFox.this.getZ(), 0.0D);
        }
    }

    private class r extends EntityFox.d {

        private double relX;
        private double relZ;
        private int lookTime;
        private int looksRemaining;

        public r() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.MOVE, PathfinderGoal.Type.LOOK));
        }

        @Override
        public boolean canUse() {
            return EntityFox.this.getLastHurtByMob() == null && EntityFox.this.getRandom().nextFloat() < 0.02F && !EntityFox.this.isSleeping() && EntityFox.this.getTarget() == null && EntityFox.this.getNavigation().isDone() && !this.alertable() && !EntityFox.this.isPouncing() && !EntityFox.this.isCrouching();
        }

        @Override
        public boolean canContinueToUse() {
            return this.looksRemaining > 0;
        }

        @Override
        public void start() {
            this.resetLook();
            this.looksRemaining = 2 + EntityFox.this.getRandom().nextInt(3);
            EntityFox.this.setSitting(true);
            EntityFox.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            EntityFox.this.setSitting(false);
        }

        @Override
        public void tick() {
            --this.lookTime;
            if (this.lookTime <= 0) {
                --this.looksRemaining;
                this.resetLook();
            }

            EntityFox.this.getLookControl().setLookAt(EntityFox.this.getX() + this.relX, EntityFox.this.getEyeY(), EntityFox.this.getZ() + this.relZ, (float) EntityFox.this.getMaxHeadYRot(), (float) EntityFox.this.getMaxHeadXRot());
        }

        private void resetLook() {
            double d0 = (Math.PI * 2D) * EntityFox.this.getRandom().nextDouble();

            this.relX = Math.cos(d0);
            this.relZ = Math.sin(d0);
            this.lookTime = this.adjustedTickDelay(80 + EntityFox.this.getRandom().nextInt(20));
        }
    }

    public class f extends PathfinderGoalGotoTarget {

        private static final int WAIT_TICKS = 40;
        protected int ticksWaited;

        public f(final double d0, final int i, final int j) {
            super(EntityFox.this, d0, i, j);
        }

        @Override
        public double acceptedDistance() {
            return 2.0D;
        }

        @Override
        public boolean shouldRecalculatePath() {
            return this.tryTicks % 100 == 0;
        }

        @Override
        protected boolean isValidTarget(IWorldReader iworldreader, BlockPosition blockposition) {
            IBlockData iblockdata = iworldreader.getBlockState(blockposition);

            return iblockdata.is(Blocks.SWEET_BERRY_BUSH) && (Integer) iblockdata.getValue(BlockSweetBerryBush.AGE) >= 2 || CaveVines.hasGlowBerries(iblockdata);
        }

        @Override
        public void tick() {
            if (this.isReachedTarget()) {
                if (this.ticksWaited >= 40) {
                    this.onReachedTarget();
                } else {
                    ++this.ticksWaited;
                }
            } else if (!this.isReachedTarget() && EntityFox.this.random.nextFloat() < 0.05F) {
                EntityFox.this.playSound(SoundEffects.FOX_SNIFF, 1.0F, 1.0F);
            }

            super.tick();
        }

        protected void onReachedTarget() {
            if (getServerLevel(EntityFox.this.level()).getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                IBlockData iblockdata = EntityFox.this.level().getBlockState(this.blockPos);

                if (iblockdata.is(Blocks.SWEET_BERRY_BUSH)) {
                    this.pickSweetBerries(iblockdata);
                } else if (CaveVines.hasGlowBerries(iblockdata)) {
                    this.pickGlowBerry(iblockdata);
                }

            }
        }

        private void pickGlowBerry(IBlockData iblockdata) {
            CaveVines.use(EntityFox.this, iblockdata, EntityFox.this.level(), this.blockPos);
        }

        private void pickSweetBerries(IBlockData iblockdata) {
            int i = (Integer) iblockdata.getValue(BlockSweetBerryBush.AGE);

            iblockdata.setValue(BlockSweetBerryBush.AGE, 1);
            // CraftBukkit start - call EntityChangeBlockEvent
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(EntityFox.this, this.blockPos, iblockdata.setValue(BlockSweetBerryBush.AGE, 1))) {
                return;
            }
            // CraftBukkit end
            int j = 1 + EntityFox.this.level().random.nextInt(2) + (i == 3 ? 1 : 0);
            ItemStack itemstack = EntityFox.this.getItemBySlot(EnumItemSlot.MAINHAND);

            if (itemstack.isEmpty()) {
                EntityFox.this.setItemSlot(EnumItemSlot.MAINHAND, new ItemStack(Items.SWEET_BERRIES));
                --j;
            }

            if (j > 0) {
                Block.popResource(EntityFox.this.level(), this.blockPos, new ItemStack(Items.SWEET_BERRIES, j));
            }

            EntityFox.this.playSound(SoundEffects.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0F, 1.0F);
            EntityFox.this.level().setBlock(this.blockPos, (IBlockData) iblockdata.setValue(BlockSweetBerryBush.AGE, 1), 2);
            EntityFox.this.level().gameEvent(GameEvent.BLOCK_CHANGE, this.blockPos, GameEvent.a.of((Entity) EntityFox.this));
        }

        @Override
        public boolean canUse() {
            return !EntityFox.this.isSleeping() && super.canUse();
        }

        @Override
        public void start() {
            this.ticksWaited = 0;
            EntityFox.this.setSitting(false);
            super.start();
        }
    }

    public static class i extends EntityAgeable.a {

        public final EntityFox.Type variant;

        public i(EntityFox.Type entityfox_type) {
            super(false);
            this.variant = entityfox_type;
        }
    }

    private class b extends PathfinderGoal {

        int countdown;

        public b() {
            this.setFlags(EnumSet.of(PathfinderGoal.Type.LOOK, PathfinderGoal.Type.JUMP, PathfinderGoal.Type.MOVE));
        }

        @Override
        public boolean canUse() {
            return EntityFox.this.isFaceplanted();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse() && this.countdown > 0;
        }

        @Override
        public void start() {
            this.countdown = this.adjustedTickDelay(40);
        }

        @Override
        public void stop() {
            EntityFox.this.setFaceplanted(false);
        }

        @Override
        public void tick() {
            --this.countdown;
        }
    }

    private class n extends PathfinderGoalPanic {

        public n(final double d0) {
            super(EntityFox.this, d0);
        }

        @Override
        public boolean shouldPanic() {
            return !EntityFox.this.isDefending() && super.shouldPanic();
        }
    }

    private class q extends PathfinderGoalNearestVillage {

        public q(final int i, final int j) {
            super(EntityFox.this, j);
        }

        @Override
        public void start() {
            EntityFox.this.clearStates();
            super.start();
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.canFoxMove();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.canFoxMove();
        }

        private boolean canFoxMove() {
            return !EntityFox.this.isSleeping() && !EntityFox.this.isSitting() && !EntityFox.this.isDefending() && EntityFox.this.getTarget() == null;
        }
    }

    private class g extends PathfinderGoalFloat {

        public g() {
            super(EntityFox.this);
        }

        @Override
        public void start() {
            super.start();
            EntityFox.this.clearStates();
        }

        @Override
        public boolean canUse() {
            return EntityFox.this.isInWater() && EntityFox.this.getFluidHeight(TagsFluid.WATER) > 0.25D || EntityFox.this.isInLava();
        }
    }

    public class o extends PathfinderGoalWaterJumpAbstract {

        public o() {}

        @Override
        public boolean canUse() {
            if (!EntityFox.this.isFullyCrouched()) {
                return false;
            } else {
                EntityLiving entityliving = EntityFox.this.getTarget();

                if (entityliving != null && entityliving.isAlive()) {
                    if (entityliving.getMotionDirection() != entityliving.getDirection()) {
                        return false;
                    } else {
                        boolean flag = EntityFox.isPathClear(EntityFox.this, entityliving);

                        if (!flag) {
                            EntityFox.this.getNavigation().createPath(entityliving, 0);
                            EntityFox.this.setIsCrouching(false);
                            EntityFox.this.setIsInterested(false);
                        }

                        return flag;
                    }
                } else {
                    return false;
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            EntityLiving entityliving = EntityFox.this.getTarget();

            if (entityliving != null && entityliving.isAlive()) {
                double d0 = EntityFox.this.getDeltaMovement().y;

                return (d0 * d0 >= (double) 0.05F || Math.abs(EntityFox.this.getXRot()) >= 15.0F || !EntityFox.this.onGround()) && !EntityFox.this.isFaceplanted();
            } else {
                return false;
            }
        }

        @Override
        public boolean isInterruptable() {
            return false;
        }

        @Override
        public void start() {
            EntityFox.this.setJumping(true);
            EntityFox.this.setIsPouncing(true);
            EntityFox.this.setIsInterested(false);
            EntityLiving entityliving = EntityFox.this.getTarget();

            if (entityliving != null) {
                EntityFox.this.getLookControl().setLookAt(entityliving, 60.0F, 30.0F);
                Vec3D vec3d = (new Vec3D(entityliving.getX() - EntityFox.this.getX(), entityliving.getY() - EntityFox.this.getY(), entityliving.getZ() - EntityFox.this.getZ())).normalize();

                EntityFox.this.setDeltaMovement(EntityFox.this.getDeltaMovement().add(vec3d.x * 0.8D, 0.9D, vec3d.z * 0.8D));
            }

            EntityFox.this.getNavigation().stop();
        }

        @Override
        public void stop() {
            EntityFox.this.setIsCrouching(false);
            EntityFox.this.crouchAmount = 0.0F;
            EntityFox.this.crouchAmountO = 0.0F;
            EntityFox.this.setIsInterested(false);
            EntityFox.this.setIsPouncing(false);
        }

        @Override
        public void tick() {
            EntityLiving entityliving = EntityFox.this.getTarget();

            if (entityliving != null) {
                EntityFox.this.getLookControl().setLookAt(entityliving, 60.0F, 30.0F);
            }

            if (!EntityFox.this.isFaceplanted()) {
                Vec3D vec3d = EntityFox.this.getDeltaMovement();

                if (vec3d.y * vec3d.y < (double) 0.03F && EntityFox.this.getXRot() != 0.0F) {
                    EntityFox.this.setXRot(MathHelper.rotLerp(0.2F, EntityFox.this.getXRot(), 0.0F));
                } else {
                    double d0 = vec3d.horizontalDistance();
                    double d1 = Math.signum(-vec3d.y) * Math.acos(d0 / vec3d.length()) * (double) (180F / (float) Math.PI);

                    EntityFox.this.setXRot((float) d1);
                }
            }

            if (entityliving != null && EntityFox.this.distanceTo(entityliving) <= 2.0F) {
                EntityFox.this.doHurtTarget(getServerLevel(EntityFox.this.level()), entityliving);
            } else if (EntityFox.this.getXRot() > 0.0F && EntityFox.this.onGround() && (float) EntityFox.this.getDeltaMovement().y != 0.0F && EntityFox.this.level().getBlockState(EntityFox.this.blockPosition()).is(Blocks.SNOW)) {
                EntityFox.this.setXRot(60.0F);
                EntityFox.this.setTarget((EntityLiving) null);
                EntityFox.this.setFaceplanted(true);
            }

        }
    }

    public class k extends ControllerLook {

        public k() {
            super(EntityFox.this);
        }

        @Override
        public void tick() {
            if (!EntityFox.this.isSleeping()) {
                super.tick();
            }

        }

        @Override
        protected boolean resetXRotOnTick() {
            return !EntityFox.this.isPouncing() && !EntityFox.this.isCrouching() && !EntityFox.this.isInterested() && !EntityFox.this.isFaceplanted();
        }
    }

    private static class h extends PathfinderGoalFollowParent {

        private final EntityFox fox;

        public h(EntityFox entityfox, double d0) {
            super(entityfox, d0);
            this.fox = entityfox;
        }

        @Override
        public boolean canUse() {
            return !this.fox.isDefending() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.fox.isDefending() && super.canContinueToUse();
        }

        @Override
        public void start() {
            this.fox.clearStates();
            super.start();
        }
    }

    private class j extends PathfinderGoalLookAtPlayer {

        public j(final EntityInsentient entityinsentient, final Class oclass, final float f) {
            super(entityinsentient, oclass, f);
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !EntityFox.this.isFaceplanted() && !EntityFox.this.isInterested();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && !EntityFox.this.isFaceplanted() && !EntityFox.this.isInterested();
        }
    }
}

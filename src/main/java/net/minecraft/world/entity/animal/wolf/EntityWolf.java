package net.minecraft.world.entity.animal.wolf;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.IRegistry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleParamItem;
import net.minecraft.core.particles.Particles;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeRange;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.EntityAgeable;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTameableAnimal;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.IEntityAngerable;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.goal.PathfinderGoalAvoidTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBeg;
import net.minecraft.world.entity.ai.goal.PathfinderGoalBreed;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFollowOwner;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLeapAtTarget;
import net.minecraft.world.entity.ai.goal.PathfinderGoalLookAtPlayer;
import net.minecraft.world.entity.ai.goal.PathfinderGoalMeleeAttack;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomLookaround;
import net.minecraft.world.entity.ai.goal.PathfinderGoalRandomStrollLand;
import net.minecraft.world.entity.ai.goal.PathfinderGoalSit;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalNearestAttackableTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalOwnerHurtByTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalOwnerHurtTarget;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalRandomTargetNonTamed;
import net.minecraft.world.entity.ai.goal.target.PathfinderGoalUniversalAngerReset;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.animal.EntityAnimal;
import net.minecraft.world.entity.animal.EntityTurtle;
import net.minecraft.world.entity.animal.horse.EntityHorseAbstract;
import net.minecraft.world.entity.animal.horse.EntityLlama;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import net.minecraft.world.entity.monster.EntityCreeper;
import net.minecraft.world.entity.monster.EntityGhast;
import net.minecraft.world.entity.monster.EntitySkeletonAbstract;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.variant.SpawnContext;
import net.minecraft.world.entity.variant.VariantUtils;
import net.minecraft.world.food.FoodInfo;
import net.minecraft.world.item.EnumColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDye;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.craftbukkit.event.CraftEventFactory;
// CraftBukkit end

public class EntityWolf extends EntityTameableAnimal implements IEntityAngerable {

    private static final DataWatcherObject<Boolean> DATA_INTERESTED_ID = DataWatcher.<Boolean>defineId(EntityWolf.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<Integer> DATA_COLLAR_COLOR = DataWatcher.<Integer>defineId(EntityWolf.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Integer> DATA_REMAINING_ANGER_TIME = DataWatcher.<Integer>defineId(EntityWolf.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Holder<WolfVariant>> DATA_VARIANT_ID = DataWatcher.<Holder<WolfVariant>>defineId(EntityWolf.class, DataWatcherRegistry.WOLF_VARIANT);
    private static final DataWatcherObject<Holder<WolfSoundVariant>> DATA_SOUND_VARIANT_ID = DataWatcher.<Holder<WolfSoundVariant>>defineId(EntityWolf.class, DataWatcherRegistry.WOLF_SOUND_VARIANT);
    public static final PathfinderTargetCondition.a PREY_SELECTOR = (entityliving, worldserver) -> {
        EntityTypes<?> entitytypes = entityliving.getType();

        return entitytypes == EntityTypes.SHEEP || entitytypes == EntityTypes.RABBIT || entitytypes == EntityTypes.FOX;
    };
    private static final float START_HEALTH = 8.0F;
    private static final float TAME_HEALTH = 40.0F;
    private static final float ARMOR_REPAIR_UNIT = 0.125F;
    public static final float DEFAULT_TAIL_ANGLE = ((float) Math.PI / 5F);
    private static final EnumColor DEFAULT_COLLAR_COLOR = EnumColor.RED;
    private float interestedAngle;
    private float interestedAngleO;
    public boolean isWet;
    private boolean isShaking;
    private float shakeAnim;
    private float shakeAnimO;
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeRange.rangeOfSeconds(20, 39);
    @Nullable
    private UUID persistentAngerTarget;

    public EntityWolf(EntityTypes<? extends EntityWolf> entitytypes, World world) {
        super(entitytypes, world);
        this.setTame(false, false);
        this.setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, -1.0F);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new PathfinderGoalFloat(this));
        this.goalSelector.addGoal(1, new EntityTameableAnimal.a(1.5D, DamageTypeTags.PANIC_ENVIRONMENTAL_CAUSES));
        this.goalSelector.addGoal(2, new PathfinderGoalSit(this));
        this.goalSelector.addGoal(3, new EntityWolf.a(this, EntityLlama.class, 24.0F, 1.5D, 1.5D));
        this.goalSelector.addGoal(4, new PathfinderGoalLeapAtTarget(this, 0.4F));
        this.goalSelector.addGoal(5, new PathfinderGoalMeleeAttack(this, 1.0D, true));
        this.goalSelector.addGoal(6, new PathfinderGoalFollowOwner(this, 1.0D, 10.0F, 2.0F));
        this.goalSelector.addGoal(7, new PathfinderGoalBreed(this, 1.0D));
        this.goalSelector.addGoal(8, new PathfinderGoalRandomStrollLand(this, 1.0D));
        this.goalSelector.addGoal(9, new PathfinderGoalBeg(this, 8.0F));
        this.goalSelector.addGoal(10, new PathfinderGoalLookAtPlayer(this, EntityHuman.class, 8.0F));
        this.goalSelector.addGoal(10, new PathfinderGoalRandomLookaround(this));
        this.targetSelector.addGoal(1, new PathfinderGoalOwnerHurtByTarget(this));
        this.targetSelector.addGoal(2, new PathfinderGoalOwnerHurtTarget(this));
        this.targetSelector.addGoal(3, (new PathfinderGoalHurtByTarget(this, new Class[0])).setAlertOthers());
        this.targetSelector.addGoal(4, new PathfinderGoalNearestAttackableTarget(this, EntityHuman.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(5, new PathfinderGoalRandomTargetNonTamed(this, EntityAnimal.class, false, EntityWolf.PREY_SELECTOR));
        this.targetSelector.addGoal(6, new PathfinderGoalRandomTargetNonTamed(this, EntityTurtle.class, false, EntityTurtle.BABY_ON_LAND_SELECTOR));
        this.targetSelector.addGoal(7, new PathfinderGoalNearestAttackableTarget(this, EntitySkeletonAbstract.class, false));
        this.targetSelector.addGoal(8, new PathfinderGoalUniversalAngerReset(this, true));
    }

    public MinecraftKey getTexture() {
        WolfVariant wolfvariant = (WolfVariant) this.getVariant().value();

        return this.isTame() ? wolfvariant.assetInfo().tame().texturePath() : (this.isAngry() ? wolfvariant.assetInfo().angry().texturePath() : wolfvariant.assetInfo().wild().texturePath());
    }

    public Holder<WolfVariant> getVariant() {
        return (Holder) this.entityData.get(EntityWolf.DATA_VARIANT_ID);
    }

    public void setVariant(Holder<WolfVariant> holder) {
        this.entityData.set(EntityWolf.DATA_VARIANT_ID, holder);
    }

    private Holder<WolfSoundVariant> getSoundVariant() {
        return (Holder) this.entityData.get(EntityWolf.DATA_SOUND_VARIANT_ID);
    }

    private void setSoundVariant(Holder<WolfSoundVariant> holder) {
        this.entityData.set(EntityWolf.DATA_SOUND_VARIANT_ID, holder);
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.WOLF_VARIANT ? castComponentValue(datacomponenttype, this.getVariant()) : (datacomponenttype == DataComponents.WOLF_SOUND_VARIANT ? castComponentValue(datacomponenttype, this.getSoundVariant()) : (datacomponenttype == DataComponents.WOLF_COLLAR ? castComponentValue(datacomponenttype, this.getCollarColor()) : super.get(datacomponenttype))));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.WOLF_VARIANT);
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.WOLF_SOUND_VARIANT);
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.WOLF_COLLAR);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.WOLF_VARIANT) {
            this.setVariant((Holder) castComponentValue(DataComponents.WOLF_VARIANT, t0));
            return true;
        } else if (datacomponenttype == DataComponents.WOLF_SOUND_VARIANT) {
            this.setSoundVariant((Holder) castComponentValue(DataComponents.WOLF_SOUND_VARIANT, t0));
            return true;
        } else if (datacomponenttype == DataComponents.WOLF_COLLAR) {
            this.setCollarColor((EnumColor) castComponentValue(DataComponents.WOLF_COLLAR, t0));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityAnimal.createAnimalAttributes().add(GenericAttributes.MOVEMENT_SPEED, (double) 0.3F).add(GenericAttributes.MAX_HEALTH, 8.0D).add(GenericAttributes.ATTACK_DAMAGE, 4.0D);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        IRegistry<WolfSoundVariant> iregistry = this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT);

        datawatcher_a.define(EntityWolf.DATA_VARIANT_ID, VariantUtils.getDefaultOrAny(this.registryAccess(), WolfVariants.DEFAULT));
        DataWatcherObject datawatcherobject = EntityWolf.DATA_SOUND_VARIANT_ID;
        Optional optional = iregistry.get(WolfSoundVariants.CLASSIC);

        Objects.requireNonNull(iregistry);
        datawatcher_a.define(datawatcherobject, (Holder) optional.or(iregistry::getAny).orElseThrow());
        datawatcher_a.define(EntityWolf.DATA_INTERESTED_ID, false);
        datawatcher_a.define(EntityWolf.DATA_COLLAR_COLOR, EntityWolf.DEFAULT_COLLAR_COLOR.getId());
        datawatcher_a.define(EntityWolf.DATA_REMAINING_ANGER_TIME, 0);
    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        this.playSound(SoundEffects.WOLF_STEP, 0.15F, 1.0F);
    }

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.store("CollarColor", EnumColor.LEGACY_ID_CODEC, this.getCollarColor());
        VariantUtils.writeVariant(nbttagcompound, this.getVariant());
        this.addPersistentAngerSaveData(nbttagcompound);
        this.getSoundVariant().unwrapKey().ifPresent((resourcekey) -> {
            nbttagcompound.store("sound_variant", ResourceKey.codec(Registries.WOLF_SOUND_VARIANT), resourcekey);
        });
    }

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        VariantUtils.readVariant(nbttagcompound, this.registryAccess(), Registries.WOLF_VARIANT).ifPresent(this::setVariant);
        this.setCollarColor((EnumColor) nbttagcompound.read("CollarColor", EnumColor.LEGACY_ID_CODEC).orElse(EntityWolf.DEFAULT_COLLAR_COLOR));
        this.readPersistentAngerSaveData(this.level(), nbttagcompound);
        nbttagcompound.read("sound_variant", ResourceKey.codec(Registries.WOLF_SOUND_VARIANT)).flatMap((resourcekey) -> {
            return this.registryAccess().lookupOrThrow(Registries.WOLF_SOUND_VARIANT).get(resourcekey);
        }).ifPresent(this::setSoundVariant);
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        if (groupdataentity instanceof EntityWolf.b entitywolf_b) {
            this.setVariant(entitywolf_b.type);
        } else {
            Optional<? extends Holder<WolfVariant>> optional = WolfVariants.selectVariantToSpawn(this.random, this.registryAccess(), SpawnContext.create(worldaccess, this.blockPosition()));

            if (optional.isPresent()) {
                this.setVariant((Holder) optional.get());
                groupdataentity = new EntityWolf.b((Holder) optional.get());
            }
        }

        this.setSoundVariant(WolfSoundVariants.pickRandomSoundVariant(this.registryAccess(), this.random));
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    protected SoundEffect getAmbientSound() {
        return this.isAngry() ? (SoundEffect) ((WolfSoundVariant) this.getSoundVariant().value()).growlSound().value() : (this.random.nextInt(3) == 0 ? (this.isTame() && this.getHealth() < 20.0F ? (SoundEffect) ((WolfSoundVariant) this.getSoundVariant().value()).whineSound().value() : (SoundEffect) ((WolfSoundVariant) this.getSoundVariant().value()).pantSound().value()) : (SoundEffect) ((WolfSoundVariant) this.getSoundVariant().value()).ambientSound().value());
    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return this.canArmorAbsorb(damagesource) ? SoundEffects.WOLF_ARMOR_DAMAGE : (SoundEffect) ((WolfSoundVariant) this.getSoundVariant().value()).hurtSound().value();
    }

    @Override
    protected SoundEffect getDeathSound() {
        return (SoundEffect) ((WolfSoundVariant) this.getSoundVariant().value()).deathSound().value();
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.isWet && !this.isShaking && !this.isPathFinding() && this.onGround()) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
            this.level().broadcastEntityEvent(this, (byte) 8);
        }

        if (!this.level().isClientSide) {
            this.updatePersistentAnger((WorldServer) this.level(), true);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (this.isAlive()) {
            this.interestedAngleO = this.interestedAngle;
            if (this.isInterested()) {
                this.interestedAngle += (1.0F - this.interestedAngle) * 0.4F;
            } else {
                this.interestedAngle += (0.0F - this.interestedAngle) * 0.4F;
            }

            if (this.isInWaterOrRain()) {
                this.isWet = true;
                if (this.isShaking && !this.level().isClientSide) {
                    this.level().broadcastEntityEvent(this, (byte) 56);
                    this.cancelShake();
                }
            } else if ((this.isWet || this.isShaking) && this.isShaking) {
                if (this.shakeAnim == 0.0F) {
                    this.playSound(SoundEffects.WOLF_SHAKE, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                    this.gameEvent(GameEvent.ENTITY_ACTION);
                }

                this.shakeAnimO = this.shakeAnim;
                this.shakeAnim += 0.05F;
                if (this.shakeAnimO >= 2.0F) {
                    this.isWet = false;
                    this.isShaking = false;
                    this.shakeAnimO = 0.0F;
                    this.shakeAnim = 0.0F;
                }

                if (this.shakeAnim > 0.4F) {
                    float f = (float) this.getY();
                    int i = (int) (MathHelper.sin((this.shakeAnim - 0.4F) * (float) Math.PI) * 7.0F);
                    Vec3D vec3d = this.getDeltaMovement();

                    for (int j = 0; j < i; ++j) {
                        float f1 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;
                        float f2 = (this.random.nextFloat() * 2.0F - 1.0F) * this.getBbWidth() * 0.5F;

                        this.level().addParticle(Particles.SPLASH, this.getX() + (double) f1, (double) (f + 0.8F), this.getZ() + (double) f2, vec3d.x, vec3d.y, vec3d.z);
                    }
                }
            }

        }
    }

    private void cancelShake() {
        this.isShaking = false;
        this.shakeAnim = 0.0F;
        this.shakeAnimO = 0.0F;
    }

    @Override
    public void die(DamageSource damagesource) {
        this.isWet = false;
        this.isShaking = false;
        this.shakeAnimO = 0.0F;
        this.shakeAnim = 0.0F;
        super.die(damagesource);
    }

    public float getWetShade(float f) {
        return !this.isWet ? 1.0F : Math.min(0.75F + MathHelper.lerp(f, this.shakeAnimO, this.shakeAnim) / 2.0F * 0.25F, 1.0F);
    }

    public float getShakeAnim(float f) {
        return MathHelper.lerp(f, this.shakeAnimO, this.shakeAnim);
    }

    public float getHeadRollAngle(float f) {
        return MathHelper.lerp(f, this.interestedAngleO, this.interestedAngle) * 0.15F * (float) Math.PI;
    }

    @Override
    public int getMaxHeadXRot() {
        return this.isInSittingPose() ? 20 : super.getMaxHeadXRot();
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(worldserver, damagesource)) {
            return false;
        } else {
            // CraftBukkit start
            boolean result = super.hurtServer(worldserver, damagesource, f);
            if (!result) {
                return result;
            }
            // CraftBukkit end
            this.setOrderedToSit(false);
            return result; // CraftBukkit
        }
    }

    @Override
    public boolean actuallyHurt(WorldServer worldserver, DamageSource damagesource, float f, EntityDamageEvent event) { // CraftBukkit - void -> boolean
        if (!this.canArmorAbsorb(damagesource)) {
            return super.actuallyHurt(worldserver, damagesource, f, event); // CraftBukkit
        } else {
            // CraftBukkit start - SPIGOT-7815: if the damage was cancelled, no need to run the wolf armor behaviour
            if (event.isCancelled()) {
                return false;
            }
            // CraftBukkit end
            ItemStack itemstack = this.getBodyArmorItem();
            int i = itemstack.getDamageValue();
            int j = itemstack.getMaxDamage();

            itemstack.hurtAndBreak(MathHelper.ceil(f), this, EnumItemSlot.BODY);
            if (Crackiness.WOLF_ARMOR.byDamage(i, j) != Crackiness.WOLF_ARMOR.byDamage(this.getBodyArmorItem())) {
                this.playSound(SoundEffects.WOLF_ARMOR_CRACK);
                worldserver.sendParticles(new ParticleParamItem(Particles.ITEM, Items.ARMADILLO_SCUTE.getDefaultInstance()), this.getX(), this.getY() + 1.0D, this.getZ(), 20, 0.2D, 0.1D, 0.2D, 0.1D);
            }

        }
        return false; // CraftBukkit
    }

    private boolean canArmorAbsorb(DamageSource damagesource) {
        return this.getBodyArmorItem().is(Items.WOLF_ARMOR) && !damagesource.is(DamageTypeTags.BYPASSES_WOLF_ARMOR);
    }

    @Override
    protected void applyTamingSideEffects() {
        if (this.isTame()) {
            this.getAttribute(GenericAttributes.MAX_HEALTH).setBaseValue(40.0D);
            this.setHealth(this.getMaxHealth()); // CraftBukkit - 40.0 -> getMaxHealth()
        } else {
            this.getAttribute(GenericAttributes.MAX_HEALTH).setBaseValue(8.0D);
        }

    }

    @Override
    protected void hurtArmor(DamageSource damagesource, float f) {
        this.doHurtEquipment(damagesource, f, new EnumItemSlot[]{EnumItemSlot.BODY});
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);
        Item item = itemstack.getItem();

        if (this.isTame()) {
            if (this.isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                this.usePlayerItem(entityhuman, enumhand, itemstack);
                FoodInfo foodinfo = (FoodInfo) itemstack.get(DataComponents.FOOD);
                float f = foodinfo != null ? (float) foodinfo.nutrition() : 1.0F;

                this.heal(2.0F * f, EntityRegainHealthEvent.RegainReason.EATING); // CraftBukkit
                return EnumInteractionResult.SUCCESS;
            } else {
                if (item instanceof ItemDye) {
                    ItemDye itemdye = (ItemDye) item;

                    if (this.isOwnedBy(entityhuman)) {
                        EnumColor enumcolor = itemdye.getDyeColor();

                        if (enumcolor != this.getCollarColor()) {
                            this.setCollarColor(enumcolor);
                            itemstack.consume(1, entityhuman);
                            return EnumInteractionResult.SUCCESS;
                        }

                        return super.mobInteract(entityhuman, enumhand);
                    }
                }

                if (this.isEquippableInSlot(itemstack, EnumItemSlot.BODY) && !this.isWearingBodyArmor() && this.isOwnedBy(entityhuman) && !this.isBaby()) {
                    this.setBodyArmorItem(itemstack.copyWithCount(1));
                    itemstack.consume(1, entityhuman);
                    return EnumInteractionResult.SUCCESS;
                } else if (!itemstack.is(Items.SHEARS) || !this.isOwnedBy(entityhuman) || !this.isWearingBodyArmor() || EnchantmentManager.has(this.getBodyArmorItem(), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) && !entityhuman.isCreative()) {
                    if (this.isInSittingPose() && this.isWearingBodyArmor() && this.isOwnedBy(entityhuman) && this.getBodyArmorItem().isDamaged() && this.getBodyArmorItem().isValidRepairItem(itemstack)) {
                        itemstack.shrink(1);
                        this.playSound(SoundEffects.WOLF_ARMOR_REPAIR);
                        ItemStack itemstack1 = this.getBodyArmorItem();
                        int i = (int) ((float) itemstack1.getMaxDamage() * 0.125F);

                        itemstack1.setDamageValue(Math.max(0, itemstack1.getDamageValue() - i));
                        return EnumInteractionResult.SUCCESS;
                    } else {
                        EnumInteractionResult enuminteractionresult = super.mobInteract(entityhuman, enumhand);

                        if (!enuminteractionresult.consumesAction() && this.isOwnedBy(entityhuman)) {
                            this.setOrderedToSit(!this.isOrderedToSit());
                            this.jumping = false;
                            this.navigation.stop();
                            this.setTarget((EntityLiving) null, EntityTargetEvent.TargetReason.FORGOT_TARGET, true); // CraftBukkit - reason
                            return EnumInteractionResult.SUCCESS.withoutItem();
                        } else {
                            return enuminteractionresult;
                        }
                    }
                } else {
                    itemstack.hurtAndBreak(1, entityhuman, getSlotForHand(enumhand));
                    this.playSound(SoundEffects.ARMOR_UNEQUIP_WOLF);
                    ItemStack itemstack2 = this.getBodyArmorItem();

                    this.setBodyArmorItem(ItemStack.EMPTY);
                    World world = this.level();

                    if (world instanceof WorldServer) {
                        WorldServer worldserver = (WorldServer) world;

                        this.forceDrops = true; // CraftBukkit
                        this.spawnAtLocation(worldserver, itemstack2);
                        this.forceDrops = false; // CraftBukkit
                    }

                    return EnumInteractionResult.SUCCESS;
                }
            }
        } else if (!this.level().isClientSide && itemstack.is(Items.BONE) && !this.isAngry()) {
            itemstack.consume(1, entityhuman);
            this.tryToTame(entityhuman);
            return EnumInteractionResult.SUCCESS_SERVER;
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    private void tryToTame(EntityHuman entityhuman) {
        // CraftBukkit - added event call and isCancelled check.
        if (this.random.nextInt(3) == 0 && !CraftEventFactory.callEntityTameEvent(this, entityhuman).isCancelled()) {
            this.tame(entityhuman);
            this.navigation.stop();
            this.setTarget((EntityLiving) null);
            this.setOrderedToSit(true);
            this.level().broadcastEntityEvent(this, (byte) 7);
        } else {
            this.level().broadcastEntityEvent(this, (byte) 6);
        }

    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 8) {
            this.isShaking = true;
            this.shakeAnim = 0.0F;
            this.shakeAnimO = 0.0F;
        } else if (b0 == 56) {
            this.cancelShake();
        } else {
            super.handleEntityEvent(b0);
        }

    }

    public float getTailAngle() {
        if (this.isAngry()) {
            return 1.5393804F;
        } else if (this.isTame()) {
            float f = this.getMaxHealth();
            float f1 = (f - this.getHealth()) / f;

            return (0.55F - f1 * 0.4F) * (float) Math.PI;
        } else {
            return ((float) Math.PI / 5F);
        }
    }

    @Override
    public boolean isFood(ItemStack itemstack) {
        return itemstack.is(TagsItem.WOLF_FOOD);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 8;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return (Integer) this.entityData.get(EntityWolf.DATA_REMAINING_ANGER_TIME);
    }

    @Override
    public void setRemainingPersistentAngerTime(int i) {
        this.entityData.set(EntityWolf.DATA_REMAINING_ANGER_TIME, i);
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(EntityWolf.PERSISTENT_ANGER_TIME.sample(this.random));
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID uuid) {
        this.persistentAngerTarget = uuid;
    }

    public EnumColor getCollarColor() {
        return EnumColor.byId((Integer) this.entityData.get(EntityWolf.DATA_COLLAR_COLOR));
    }

    public void setCollarColor(EnumColor enumcolor) {
        this.entityData.set(EntityWolf.DATA_COLLAR_COLOR, enumcolor.getId());
    }

    @Nullable
    @Override
    public EntityWolf getBreedOffspring(WorldServer worldserver, EntityAgeable entityageable) {
        EntityWolf entitywolf = EntityTypes.WOLF.create(worldserver, EntitySpawnReason.BREEDING);

        if (entitywolf != null && entityageable instanceof EntityWolf entitywolf1) {
            if (this.random.nextBoolean()) {
                entitywolf.setVariant(this.getVariant());
            } else {
                entitywolf.setVariant(entitywolf1.getVariant());
            }

            if (this.isTame()) {
                entitywolf.setOwnerReference(this.getOwnerReference());
                entitywolf.setTame(true, true);
                EnumColor enumcolor = this.getCollarColor();
                EnumColor enumcolor1 = entitywolf1.getCollarColor();

                entitywolf.setCollarColor(EnumColor.getMixedColor(worldserver, enumcolor, enumcolor1));
            }

            entitywolf.setSoundVariant(WolfSoundVariants.pickRandomSoundVariant(this.registryAccess(), this.random));
        }

        return entitywolf;
    }

    public void setIsInterested(boolean flag) {
        this.entityData.set(EntityWolf.DATA_INTERESTED_ID, flag);
    }

    @Override
    public boolean canMate(EntityAnimal entityanimal) {
        if (entityanimal == this) {
            return false;
        } else if (!this.isTame()) {
            return false;
        } else if (!(entityanimal instanceof EntityWolf)) {
            return false;
        } else {
            EntityWolf entitywolf = (EntityWolf) entityanimal;

            return !entitywolf.isTame() ? false : (entitywolf.isInSittingPose() ? false : this.isInLove() && entitywolf.isInLove());
        }
    }

    public boolean isInterested() {
        return (Boolean) this.entityData.get(EntityWolf.DATA_INTERESTED_ID);
    }

    @Override
    public boolean wantsToAttack(EntityLiving entityliving, EntityLiving entityliving1) {
        if (!(entityliving instanceof EntityCreeper) && !(entityliving instanceof EntityGhast) && !(entityliving instanceof EntityArmorStand)) {
            if (entityliving instanceof EntityWolf) {
                EntityWolf entitywolf = (EntityWolf) entityliving;

                return !entitywolf.isTame() || entitywolf.getOwner() != entityliving1;
            } else {
                if (entityliving instanceof EntityHuman) {
                    EntityHuman entityhuman = (EntityHuman) entityliving;

                    if (entityliving1 instanceof EntityHuman) {
                        EntityHuman entityhuman1 = (EntityHuman) entityliving1;

                        if (!entityhuman1.canHarmPlayer(entityhuman)) {
                            return false;
                        }
                    }
                }

                if (entityliving instanceof EntityHorseAbstract) {
                    EntityHorseAbstract entityhorseabstract = (EntityHorseAbstract) entityliving;

                    if (entityhorseabstract.isTamed()) {
                        return false;
                    }
                }

                boolean flag;

                if (entityliving instanceof EntityTameableAnimal) {
                    EntityTameableAnimal entitytameableanimal = (EntityTameableAnimal) entityliving;

                    if (entitytameableanimal.isTame()) {
                        flag = false;
                        return flag;
                    }
                }

                flag = true;
                return flag;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean canBeLeashed() {
        return !this.isAngry();
    }

    @Override
    public Vec3D getLeashOffset() {
        return new Vec3D(0.0D, (double) (0.6F * this.getEyeHeight()), (double) (this.getBbWidth() * 0.4F));
    }

    public static boolean checkWolfSpawnRules(EntityTypes<EntityWolf> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        return generatoraccess.getBlockState(blockposition.below()).is(TagsBlock.WOLVES_SPAWNABLE_ON) && isBrightEnoughToSpawn(generatoraccess, blockposition);
    }

    public static class b extends EntityAgeable.a {

        public final Holder<WolfVariant> type;

        public b(Holder<WolfVariant> holder) {
            super(false);
            this.type = holder;
        }
    }

    private class a<T extends EntityLiving> extends PathfinderGoalAvoidTarget<T> {

        private final EntityWolf wolf;

        public a(final EntityWolf entitywolf, final Class oclass, final float f, final double d0, final double d1) {
            super(entitywolf, oclass, f, d0, d1);
            this.wolf = entitywolf;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && this.toAvoid instanceof EntityLlama ? !this.wolf.isTame() && this.avoidLlama((EntityLlama) this.toAvoid) : false;
        }

        private boolean avoidLlama(EntityLlama entityllama) {
            return entityllama.getStrength() >= EntityWolf.this.random.nextInt(5);
        }

        @Override
        public void start() {
            EntityWolf.this.setTarget((EntityLiving) null);
            super.start();
        }

        @Override
        public void tick() {
            EntityWolf.this.setTarget((EntityLiving) null);
            super.tick();
        }
    }
}

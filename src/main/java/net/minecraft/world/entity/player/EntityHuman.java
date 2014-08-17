package net.minecraft.world.entity.player;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.math.IntMath;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.GameProfileSerializer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.chat.ChatClickable;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.chat.IChatMutableComponent;
import net.minecraft.network.protocol.game.PacketPlayOutEntityVelocity;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.stats.Statistic;
import net.minecraft.stats.StatisticList;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsEntity;
import net.minecraft.tags.TagsFluid;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Unit;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.IInventory;
import net.minecraft.world.ITileInventory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTameableAnimal;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.EnumMainHand;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.animal.EntityParrot;
import net.minecraft.world.entity.animal.horse.EntityHorseAbstract;
import net.minecraft.world.entity.boss.EntityComplexPart;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.projectile.EntityFishingHook;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.food.FoodMetaData;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerPlayer;
import net.minecraft.world.inventory.InventoryEnderChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldown;
import net.minecraft.world.item.ItemProjectileWeapon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.crafting.IRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.trading.MerchantRecipeList;
import net.minecraft.world.level.CommandBlockListenerAbstract;
import net.minecraft.world.level.EnumGamemode;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.entity.TestBlockEntity;
import net.minecraft.world.level.block.entity.TestInstanceBlockEntity;
import net.minecraft.world.level.block.entity.TileEntityCommand;
import net.minecraft.world.level.block.entity.TileEntityJigsaw;
import net.minecraft.world.level.block.entity.TileEntitySign;
import net.minecraft.world.level.block.entity.TileEntityStructure;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.pattern.ShapeDetectorBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreboardTeam;
import net.minecraft.world.scores.ScoreboardTeamBase;

// CraftBukkit start
import net.minecraft.nbt.NBTBase;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.util.CraftVector;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
// CraftBukkit end

public abstract class EntityHuman extends EntityLiving {

    public static final EnumMainHand DEFAULT_MAIN_HAND = EnumMainHand.RIGHT;
    public static final int DEFAULT_MODEL_CUSTOMIZATION = 0;
    public static final int MAX_HEALTH = 20;
    public static final int SLEEP_DURATION = 100;
    public static final int WAKE_UP_DURATION = 10;
    public static final int ENDER_SLOT_OFFSET = 200;
    public static final int HELD_ITEM_SLOT = 499;
    public static final int CRAFTING_SLOT_OFFSET = 500;
    public static final float DEFAULT_BLOCK_INTERACTION_RANGE = 4.5F;
    public static final float DEFAULT_ENTITY_INTERACTION_RANGE = 3.0F;
    public static final float CROUCH_BB_HEIGHT = 1.5F;
    public static final float SWIMMING_BB_WIDTH = 0.6F;
    public static final float SWIMMING_BB_HEIGHT = 0.6F;
    public static final float DEFAULT_EYE_HEIGHT = 1.62F;
    private static final int CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME_TICKS = 40;
    public static final Vec3D DEFAULT_VEHICLE_ATTACHMENT = new Vec3D(0.0D, 0.6D, 0.0D);
    public static final EntitySize STANDING_DIMENSIONS = EntitySize.scalable(0.6F, 1.8F).withEyeHeight(1.62F).withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, EntityHuman.DEFAULT_VEHICLE_ATTACHMENT));
    // CraftBukkit - decompile error
    private static final Map<EntityPose, EntitySize> POSES = ImmutableMap.<EntityPose, EntitySize>builder().put(EntityPose.STANDING, EntityHuman.STANDING_DIMENSIONS).put(EntityPose.SLEEPING, EntityHuman.SLEEPING_DIMENSIONS).put(EntityPose.FALL_FLYING, EntitySize.scalable(0.6F, 0.6F).withEyeHeight(0.4F)).put(EntityPose.SWIMMING, EntitySize.scalable(0.6F, 0.6F).withEyeHeight(0.4F)).put(EntityPose.SPIN_ATTACK, EntitySize.scalable(0.6F, 0.6F).withEyeHeight(0.4F)).put(EntityPose.CROUCHING, EntitySize.scalable(0.6F, 1.5F).withEyeHeight(1.27F).withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, EntityHuman.DEFAULT_VEHICLE_ATTACHMENT))).put(EntityPose.DYING, EntitySize.fixed(0.2F, 0.2F).withEyeHeight(1.62F)).build();
    private static final DataWatcherObject<Float> DATA_PLAYER_ABSORPTION_ID = DataWatcher.<Float>defineId(EntityHuman.class, DataWatcherRegistry.FLOAT);
    private static final DataWatcherObject<Integer> DATA_SCORE_ID = DataWatcher.<Integer>defineId(EntityHuman.class, DataWatcherRegistry.INT);
    protected static final DataWatcherObject<Byte> DATA_PLAYER_MODE_CUSTOMISATION = DataWatcher.<Byte>defineId(EntityHuman.class, DataWatcherRegistry.BYTE);
    protected static final DataWatcherObject<Byte> DATA_PLAYER_MAIN_HAND = DataWatcher.<Byte>defineId(EntityHuman.class, DataWatcherRegistry.BYTE);
    protected static final DataWatcherObject<NBTTagCompound> DATA_SHOULDER_LEFT = DataWatcher.<NBTTagCompound>defineId(EntityHuman.class, DataWatcherRegistry.COMPOUND_TAG);
    protected static final DataWatcherObject<NBTTagCompound> DATA_SHOULDER_RIGHT = DataWatcher.<NBTTagCompound>defineId(EntityHuman.class, DataWatcherRegistry.COMPOUND_TAG);
    public static final int CLIENT_LOADED_TIMEOUT_TIME = 60;
    private static final short DEFAULT_SLEEP_TIMER = 0;
    private static final float DEFAULT_EXPERIENCE_PROGRESS = 0.0F;
    private static final int DEFAULT_EXPERIENCE_LEVEL = 0;
    private static final int DEFAULT_TOTAL_EXPERIENCE = 0;
    private static final int NO_ENCHANTMENT_SEED = 0;
    private static final int DEFAULT_SELECTED_SLOT = 0;
    private static final int DEFAULT_SCORE = 0;
    private static final boolean DEFAULT_IGNORE_FALL_DAMAGE_FROM_CURRENT_IMPULSE = false;
    private static final int DEFAULT_CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME = 0;
    private long timeEntitySatOnShoulder;
    final PlayerInventory inventory;
    protected InventoryEnderChest enderChestInventory = new InventoryEnderChest(this); // CraftBukkit - add "this" to constructor
    public final ContainerPlayer inventoryMenu;
    public Container containerMenu;
    protected FoodMetaData foodData = new FoodMetaData();
    protected int jumpTriggerTime;
    private boolean clientLoaded = false;
    protected int clientLoadedTimeoutTimer = 60;
    public float oBob;
    public float bob;
    public int takeXpDelay;
    public double xCloakO;
    public double yCloakO;
    public double zCloakO;
    public double xCloak;
    public double yCloak;
    public double zCloak;
    public int sleepCounter = 0;
    protected boolean wasUnderwater;
    private final PlayerAbilities abilities = new PlayerAbilities();
    public int experienceLevel = 0;
    public int totalExperience = 0;
    public float experienceProgress = 0.0F;
    public int enchantmentSeed = 0;
    protected final float defaultFlySpeed = 0.02F;
    private int lastLevelUpTime;
    private final GameProfile gameProfile;
    private boolean reducedDebugInfo;
    private ItemStack lastItemInMainHand;
    private final ItemCooldown cooldowns;
    private Optional<GlobalPos> lastDeathLocation;
    @Nullable
    public EntityFishingHook fishing;
    protected float hurtDir;
    @Nullable
    public Vec3D currentImpulseImpactPos;
    @Nullable
    public Entity currentExplosionCause;
    private boolean ignoreFallDamageFromCurrentImpulse;
    private int currentImpulseContextResetGraceTime;

    // CraftBukkit start
    public boolean fauxSleeping;
    public int oldLevel = -1;

    @Override
    public CraftHumanEntity getBukkitEntity() {
        return (CraftHumanEntity) super.getBukkitEntity();
    }
    // CraftBukkit end

    public EntityHuman(World world, BlockPosition blockposition, float f, GameProfile gameprofile) {
        super(EntityTypes.PLAYER, world);
        this.lastItemInMainHand = ItemStack.EMPTY;
        this.cooldowns = this.createItemCooldowns();
        this.lastDeathLocation = Optional.empty();
        this.ignoreFallDamageFromCurrentImpulse = false;
        this.currentImpulseContextResetGraceTime = 0;
        this.setUUID(gameprofile.getId());
        this.gameProfile = gameprofile;
        this.inventory = new PlayerInventory(this, this.equipment);
        this.inventoryMenu = new ContainerPlayer(this.inventory, !world.isClientSide, this);
        this.containerMenu = this.inventoryMenu;
        this.snapTo((double) blockposition.getX() + 0.5D, (double) (blockposition.getY() + 1), (double) blockposition.getZ() + 0.5D, f, 0.0F);
    }

    @Override
    protected EntityEquipment createEquipment() {
        return new PlayerEquipment(this);
    }

    public boolean blockActionRestricted(World world, BlockPosition blockposition, EnumGamemode enumgamemode) {
        if (!enumgamemode.isBlockPlacingRestricted()) {
            return false;
        } else if (enumgamemode == EnumGamemode.SPECTATOR) {
            return true;
        } else if (this.mayBuild()) {
            return false;
        } else {
            ItemStack itemstack = this.getMainHandItem();

            return itemstack.isEmpty() || !itemstack.canBreakBlockInAdventureMode(new ShapeDetectorBlock(world, blockposition, false));
        }
    }

    public static AttributeProvider.Builder createAttributes() {
        return EntityLiving.createLivingAttributes().add(GenericAttributes.ATTACK_DAMAGE, 1.0D).add(GenericAttributes.MOVEMENT_SPEED, (double) 0.1F).add(GenericAttributes.ATTACK_SPEED).add(GenericAttributes.LUCK).add(GenericAttributes.BLOCK_INTERACTION_RANGE, 4.5D).add(GenericAttributes.ENTITY_INTERACTION_RANGE, 3.0D).add(GenericAttributes.BLOCK_BREAK_SPEED).add(GenericAttributes.SUBMERGED_MINING_SPEED).add(GenericAttributes.SNEAKING_SPEED).add(GenericAttributes.MINING_EFFICIENCY).add(GenericAttributes.SWEEPING_DAMAGE_RATIO);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityHuman.DATA_PLAYER_ABSORPTION_ID, 0.0F);
        datawatcher_a.define(EntityHuman.DATA_SCORE_ID, 0);
        datawatcher_a.define(EntityHuman.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0);
        datawatcher_a.define(EntityHuman.DATA_PLAYER_MAIN_HAND, (byte) EntityHuman.DEFAULT_MAIN_HAND.getId());
        datawatcher_a.define(EntityHuman.DATA_SHOULDER_LEFT, new NBTTagCompound());
        datawatcher_a.define(EntityHuman.DATA_SHOULDER_RIGHT, new NBTTagCompound());
    }

    @Override
    public void tick() {
        this.noPhysics = this.isSpectator();
        if (this.isSpectator() || this.isPassenger()) {
            this.setOnGround(false);
        }

        if (this.takeXpDelay > 0) {
            --this.takeXpDelay;
        }

        if (this.isSleeping()) {
            ++this.sleepCounter;
            if (this.sleepCounter > 100) {
                this.sleepCounter = 100;
            }

            if (!this.level().isClientSide && this.level().isBrightOutside()) {
                this.stopSleepInBed(false, true);
            }
        } else if (this.sleepCounter > 0) {
            ++this.sleepCounter;
            if (this.sleepCounter >= 110) {
                this.sleepCounter = 0;
            }
        }

        this.updateIsUnderwater();
        super.tick();
        if (!this.level().isClientSide && this.containerMenu != null && !this.containerMenu.stillValid(this)) {
            this.closeContainer();
            this.containerMenu = this.inventoryMenu;
        }

        this.moveCloak();
        if (this instanceof EntityPlayer entityplayer) {
            this.foodData.tick(entityplayer);
            this.awardStat(StatisticList.PLAY_TIME);
            this.awardStat(StatisticList.TOTAL_WORLD_TIME);
            if (this.isAlive()) {
                this.awardStat(StatisticList.TIME_SINCE_DEATH);
            }

            if (this.isDiscrete()) {
                this.awardStat(StatisticList.CROUCH_TIME);
            }

            if (!this.isSleeping()) {
                this.awardStat(StatisticList.TIME_SINCE_REST);
            }
        }

        int i = 29999999;
        double d0 = MathHelper.clamp(this.getX(), -2.9999999E7D, 2.9999999E7D);
        double d1 = MathHelper.clamp(this.getZ(), -2.9999999E7D, 2.9999999E7D);

        if (d0 != this.getX() || d1 != this.getZ()) {
            this.setPos(d0, this.getY(), d1);
        }

        ++this.attackStrengthTicker;
        ItemStack itemstack = this.getMainHandItem();

        if (!ItemStack.matches(this.lastItemInMainHand, itemstack)) {
            if (!ItemStack.isSameItem(this.lastItemInMainHand, itemstack)) {
                this.resetAttackStrengthTicker();
            }

            this.lastItemInMainHand = itemstack.copy();
        }

        if (!this.isEyeInFluid(TagsFluid.WATER) && this.isEquipped(Items.TURTLE_HELMET)) {
            this.turtleHelmetTick();
        }

        this.cooldowns.tick();
        this.updatePlayerPose();
        if (this.currentImpulseContextResetGraceTime > 0) {
            --this.currentImpulseContextResetGraceTime;
        }

    }

    @Override
    protected float getMaxHeadRotationRelativeToBody() {
        return this.isBlocking() ? 15.0F : super.getMaxHeadRotationRelativeToBody();
    }

    public boolean isSecondaryUseActive() {
        return this.isShiftKeyDown();
    }

    protected boolean wantsToStopRiding() {
        return this.isShiftKeyDown();
    }

    protected boolean isStayingOnGroundSurface() {
        return this.isShiftKeyDown();
    }

    protected boolean updateIsUnderwater() {
        this.wasUnderwater = this.isEyeInFluid(TagsFluid.WATER);
        return this.wasUnderwater;
    }

    @Override
    public void onAboveBubbleColumn(boolean flag, BlockPosition blockposition) {
        if (!this.getAbilities().flying) {
            super.onAboveBubbleColumn(flag, blockposition);
        }

    }

    @Override
    public void onInsideBubbleColumn(boolean flag) {
        if (!this.getAbilities().flying) {
            super.onInsideBubbleColumn(flag);
        }

    }

    private void turtleHelmetTick() {
        this.addEffect(new MobEffect(MobEffects.WATER_BREATHING, 200, 0, false, false, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.TURTLE_HELMET); // CraftBukkit
    }

    private boolean isEquipped(Item item) {
        for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
            ItemStack itemstack = this.getItemBySlot(enumitemslot);
            Equippable equippable = (Equippable) itemstack.get(DataComponents.EQUIPPABLE);

            if (itemstack.is(item) && equippable != null && equippable.slot() == enumitemslot) {
                return true;
            }
        }

        return false;
    }

    protected ItemCooldown createItemCooldowns() {
        return new ItemCooldown();
    }

    private void moveCloak() {
        this.xCloakO = this.xCloak;
        this.yCloakO = this.yCloak;
        this.zCloakO = this.zCloak;
        double d0 = this.getX() - this.xCloak;
        double d1 = this.getY() - this.yCloak;
        double d2 = this.getZ() - this.zCloak;
        double d3 = 10.0D;

        if (d0 > 10.0D) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 > 10.0D) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 > 10.0D) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        if (d0 < -10.0D) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 < -10.0D) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 < -10.0D) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        this.xCloak += d0 * 0.25D;
        this.zCloak += d2 * 0.25D;
        this.yCloak += d1 * 0.25D;
    }

    protected void updatePlayerPose() {
        if (this.canPlayerFitWithinBlocksAndEntitiesWhen(EntityPose.SWIMMING)) {
            EntityPose entitypose = this.getDesiredPose();
            EntityPose entitypose1;

            if (!this.isSpectator() && !this.isPassenger() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(entitypose)) {
                if (this.canPlayerFitWithinBlocksAndEntitiesWhen(EntityPose.CROUCHING)) {
                    entitypose1 = EntityPose.CROUCHING;
                } else {
                    entitypose1 = EntityPose.SWIMMING;
                }
            } else {
                entitypose1 = entitypose;
            }

            this.setPose(entitypose1);
        }
    }

    private EntityPose getDesiredPose() {
        return this.isSleeping() ? EntityPose.SLEEPING : (this.isSwimming() ? EntityPose.SWIMMING : (this.isFallFlying() ? EntityPose.FALL_FLYING : (this.isAutoSpinAttack() ? EntityPose.SPIN_ATTACK : (this.isShiftKeyDown() && !this.abilities.flying ? EntityPose.CROUCHING : EntityPose.STANDING))));
    }

    protected boolean canPlayerFitWithinBlocksAndEntitiesWhen(EntityPose entitypose) {
        return this.level().noCollision(this, this.getDimensions(entitypose).makeBoundingBox(this.position()).deflate(1.0E-7D));
    }

    @Override
    protected SoundEffect getSwimSound() {
        return SoundEffects.PLAYER_SWIM;
    }

    @Override
    protected SoundEffect getSwimSplashSound() {
        return SoundEffects.PLAYER_SPLASH;
    }

    @Override
    protected SoundEffect getSwimHighSpeedSplashSound() {
        return SoundEffects.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    public int getDimensionChangingDelay() {
        return 10;
    }

    @Override
    public void playSound(SoundEffect soundeffect, float f, float f1) {
        this.level().playSound(this, this.getX(), this.getY(), this.getZ(), soundeffect, this.getSoundSource(), f, f1);
    }

    public void playNotifySound(SoundEffect soundeffect, SoundCategory soundcategory, float f, float f1) {}

    @Override
    public SoundCategory getSoundSource() {
        return SoundCategory.PLAYERS;
    }

    @Override
    public int getFireImmuneTicks() {
        return 20;
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 9) {
            this.completeUsingItem();
        } else if (b0 == 23) {
            this.reducedDebugInfo = false;
        } else if (b0 == 22) {
            this.reducedDebugInfo = true;
        } else {
            super.handleEntityEvent(b0);
        }

    }

    public void closeContainer() {
        this.containerMenu = this.inventoryMenu;
    }

    protected void doCloseContainer() {}

    @Override
    public void rideTick() {
        if (!this.level().isClientSide && this.wantsToStopRiding() && this.isPassenger()) {
            this.stopRiding();
            // CraftBukkit start - SPIGOT-7316: no longer passenger, dismount and return
            if (!this.isPassenger()) {
                this.setShiftKeyDown(false);
                return;
            }
        }
        {
            // CraftBukkit end
            super.rideTick();
            this.oBob = this.bob;
            this.bob = 0.0F;
        }
    }

    @Override
    public void aiStep() {
        if (this.jumpTriggerTime > 0) {
            --this.jumpTriggerTime;
        }

        this.tickRegeneration();
        this.inventory.tick();
        this.oBob = this.bob;
        if (this.abilities.flying && !this.isPassenger()) {
            this.resetFallDistance();
        }

        super.aiStep();
        this.updateSwingTime();
        this.yHeadRot = this.getYRot();
        this.setSpeed((float) this.getAttributeValue(GenericAttributes.MOVEMENT_SPEED));
        float f;

        if (this.onGround() && !this.isDeadOrDying() && !this.isSwimming()) {
            f = Math.min(0.1F, (float) this.getDeltaMovement().horizontalDistance());
        } else {
            f = 0.0F;
        }

        this.bob += (f - this.bob) * 0.4F;
        if (this.getHealth() > 0.0F && !this.isSpectator()) {
            AxisAlignedBB axisalignedbb;

            if (this.isPassenger() && !this.getVehicle().isRemoved()) {
                axisalignedbb = this.getBoundingBox().minmax(this.getVehicle().getBoundingBox()).inflate(1.0D, 0.0D, 1.0D);
            } else {
                axisalignedbb = this.getBoundingBox().inflate(1.0D, 0.5D, 1.0D);
            }

            List<Entity> list = this.level().getEntities(this, axisalignedbb);
            List<Entity> list1 = Lists.newArrayList();

            for (Entity entity : list) {
                if (entity.getType() == EntityTypes.EXPERIENCE_ORB) {
                    list1.add(entity);
                } else if (!entity.isRemoved()) {
                    this.touch(entity);
                }
            }

            if (!list1.isEmpty()) {
                this.touch((Entity) SystemUtils.getRandom(list1, this.random));
            }
        }

        this.playShoulderEntityAmbientSound(this.getShoulderEntityLeft());
        this.playShoulderEntityAmbientSound(this.getShoulderEntityRight());
        if (!this.level().isClientSide && (this.fallDistance > 0.5D || this.isInWater()) || this.abilities.flying || this.isSleeping() || this.isInPowderSnow) {
            this.removeEntitiesOnShoulder();
        }

    }

    protected void tickRegeneration() {}

    private void playShoulderEntityAmbientSound(NBTTagCompound nbttagcompound) {
        if (!nbttagcompound.isEmpty() && !nbttagcompound.getBooleanOr("Silent", false)) {
            if (this.level().random.nextInt(200) == 0) {
                EntityTypes<?> entitytypes = (EntityTypes) nbttagcompound.read("id", EntityTypes.CODEC).orElse(null); // CraftBukkit - decompile error

                if (entitytypes == EntityTypes.PARROT && !EntityParrot.imitateNearbyMobs(this.level(), this)) {
                    this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), EntityParrot.getAmbient(this.level(), this.level().random), this.getSoundSource(), 1.0F, EntityParrot.getPitch(this.level().random));
                }
            }

        }
    }

    private void touch(Entity entity) {
        entity.playerTouch(this);
    }

    public int getScore() {
        return (Integer) this.entityData.get(EntityHuman.DATA_SCORE_ID);
    }

    public void setScore(int i) {
        this.entityData.set(EntityHuman.DATA_SCORE_ID, i);
    }

    public void increaseScore(int i) {
        int j = this.getScore();

        this.entityData.set(EntityHuman.DATA_SCORE_ID, j + i);
    }

    public void startAutoSpinAttack(int i, float f, ItemStack itemstack) {
        this.autoSpinAttackTicks = i;
        this.autoSpinAttackDmg = f;
        this.autoSpinAttackItemStack = itemstack;
        if (!this.level().isClientSide) {
            this.removeEntitiesOnShoulder();
            this.setLivingEntityFlag(4, true);
        }

    }

    @Nonnull
    @Override
    public ItemStack getWeaponItem() {
        return this.isAutoSpinAttack() && this.autoSpinAttackItemStack != null ? this.autoSpinAttackItemStack : super.getWeaponItem();
    }

    @Override
    public void die(DamageSource damagesource) {
        super.die(damagesource);
        this.reapplyPosition();
        if (!this.isSpectator()) {
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                this.dropAllDeathLoot(worldserver, damagesource);
            }
        }

        if (damagesource != null) {
            this.setDeltaMovement((double) (-MathHelper.cos((this.getHurtDir() + this.getYRot()) * ((float) Math.PI / 180F)) * 0.1F), (double) 0.1F, (double) (-MathHelper.sin((this.getHurtDir() + this.getYRot()) * ((float) Math.PI / 180F)) * 0.1F));
        } else {
            this.setDeltaMovement(0.0D, 0.1D, 0.0D);
        }

        this.awardStat(StatisticList.DEATHS);
        this.resetStat(StatisticList.CUSTOM.get(StatisticList.TIME_SINCE_DEATH));
        this.resetStat(StatisticList.CUSTOM.get(StatisticList.TIME_SINCE_REST));
        this.clearFire();
        this.setSharedFlagOnFire(false);
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
    }

    @Override
    protected void dropEquipment(WorldServer worldserver) {
        super.dropEquipment(worldserver);
        if (!worldserver.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            this.destroyVanishingCursedItems();
            this.inventory.dropAll();
        }

    }

    protected void destroyVanishingCursedItems() {
        for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
            ItemStack itemstack = this.inventory.getItem(i);

            if (!itemstack.isEmpty() && EnchantmentManager.has(itemstack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                this.inventory.removeItemNoUpdate(i);
            }
        }

    }

    @Override
    protected SoundEffect getHurtSound(DamageSource damagesource) {
        return damagesource.type().effects().sound();
    }

    @Override
    protected SoundEffect getDeathSound() {
        return SoundEffects.PLAYER_DEATH;
    }

    public void handleCreativeModeItemDrop(ItemStack itemstack) {}

    @Nullable
    public EntityItem drop(ItemStack itemstack, boolean flag) {
        return this.drop(itemstack, false, flag);
    }

    public float getDestroySpeed(IBlockData iblockdata) {
        float f = this.inventory.getSelectedItem().getDestroySpeed(iblockdata);

        if (f > 1.0F) {
            f += (float) this.getAttributeValue(GenericAttributes.MINING_EFFICIENCY);
        }

        if (MobEffectUtil.hasDigSpeed(this)) {
            f *= 1.0F + (float) (MobEffectUtil.getDigSpeedAmplification(this) + 1) * 0.2F;
        }

        if (this.hasEffect(MobEffects.MINING_FATIGUE)) {
            float f1;

            switch (this.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
                case 0:
                    f1 = 0.3F;
                    break;
                case 1:
                    f1 = 0.09F;
                    break;
                case 2:
                    f1 = 0.0027F;
                    break;
                default:
                    f1 = 8.1E-4F;
            }

            float f2 = f1;

            f *= f2;
        }

        f *= (float) this.getAttributeValue(GenericAttributes.BLOCK_BREAK_SPEED);
        if (this.isEyeInFluid(TagsFluid.WATER)) {
            f *= (float) this.getAttribute(GenericAttributes.SUBMERGED_MINING_SPEED).getValue();
        }

        if (!this.onGround()) {
            f /= 5.0F;
        }

        return f;
    }

    public boolean hasCorrectToolForDrops(IBlockData iblockdata) {
        return !iblockdata.requiresCorrectToolForDrops() || this.inventory.getSelectedItem().isCorrectToolForDrops(iblockdata);
    }

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.setUUID(this.gameProfile.getId());
        NBTTagList nbttaglist = nbttagcompound.getListOrEmpty("Inventory");

        this.inventory.load(nbttaglist);
        this.inventory.setSelectedSlot(nbttagcompound.getIntOr("SelectedItemSlot", 0));
        this.sleepCounter = nbttagcompound.getShortOr("SleepTimer", (short) 0);
        this.experienceProgress = nbttagcompound.getFloatOr("XpP", 0.0F);
        this.experienceLevel = nbttagcompound.getIntOr("XpLevel", 0);
        this.totalExperience = nbttagcompound.getIntOr("XpTotal", 0);
        this.enchantmentSeed = nbttagcompound.getIntOr("XpSeed", 0);
        if (this.enchantmentSeed == 0) {
            this.enchantmentSeed = this.random.nextInt();
        }

        this.setScore(nbttagcompound.getIntOr("Score", 0));
        this.foodData.readAdditionalSaveData(nbttagcompound);
        this.abilities.loadSaveData(nbttagcompound);
        this.getAttribute(GenericAttributes.MOVEMENT_SPEED).setBaseValue((double) this.abilities.getWalkingSpeed());
        nbttagcompound.getList("EnderItems").ifPresent((nbttaglist1) -> {
            this.enderChestInventory.fromTag(nbttaglist1, this.registryAccess());
        });
        this.setShoulderEntityLeft(nbttagcompound.getCompoundOrEmpty("ShoulderEntityLeft"));
        this.setShoulderEntityRight(nbttagcompound.getCompoundOrEmpty("ShoulderEntityRight"));
        this.setLastDeathLocation(nbttagcompound.read("LastDeathLocation", GlobalPos.CODEC));
        this.currentImpulseImpactPos = (Vec3D) nbttagcompound.read("current_explosion_impact_pos", Vec3D.CODEC).orElse(null); // CraftBukkit - decompile error
        this.ignoreFallDamageFromCurrentImpulse = nbttagcompound.getBooleanOr("ignore_fall_damage_from_current_explosion", false);
        this.currentImpulseContextResetGraceTime = nbttagcompound.getIntOr("current_impulse_context_reset_grace_time", 0);
    }

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        GameProfileSerializer.addCurrentDataVersion(nbttagcompound);
        nbttagcompound.put("Inventory", this.inventory.save(new NBTTagList()));
        nbttagcompound.putInt("SelectedItemSlot", this.inventory.getSelectedSlot());
        nbttagcompound.putShort("SleepTimer", (short) this.sleepCounter);
        nbttagcompound.putFloat("XpP", this.experienceProgress);
        nbttagcompound.putInt("XpLevel", this.experienceLevel);
        nbttagcompound.putInt("XpTotal", this.totalExperience);
        nbttagcompound.putInt("XpSeed", this.enchantmentSeed);
        nbttagcompound.putInt("Score", this.getScore());
        this.foodData.addAdditionalSaveData(nbttagcompound);
        this.abilities.addSaveData(nbttagcompound);
        nbttagcompound.put("EnderItems", this.enderChestInventory.createTag(this.registryAccess()));
        if (!this.getShoulderEntityLeft().isEmpty()) {
            nbttagcompound.put("ShoulderEntityLeft", this.getShoulderEntityLeft());
        }

        if (!this.getShoulderEntityRight().isEmpty()) {
            nbttagcompound.put("ShoulderEntityRight", this.getShoulderEntityRight());
        }

        this.lastDeathLocation.ifPresent((globalpos) -> {
            nbttagcompound.store("LastDeathLocation", GlobalPos.CODEC, globalpos);
        });
        nbttagcompound.storeNullable("current_explosion_impact_pos", Vec3D.CODEC, this.currentImpulseImpactPos);
        nbttagcompound.putBoolean("ignore_fall_damage_from_current_explosion", this.ignoreFallDamageFromCurrentImpulse);
        nbttagcompound.putInt("current_impulse_context_reset_grace_time", this.currentImpulseContextResetGraceTime);
    }

    @Override
    public boolean isInvulnerableTo(WorldServer worldserver, DamageSource damagesource) {
        return super.isInvulnerableTo(worldserver, damagesource) ? true : (damagesource.is(DamageTypeTags.IS_DROWNING) ? !worldserver.getGameRules().getBoolean(GameRules.RULE_DROWNING_DAMAGE) : (damagesource.is(DamageTypeTags.IS_FALL) ? !worldserver.getGameRules().getBoolean(GameRules.RULE_FALL_DAMAGE) : (damagesource.is(DamageTypeTags.IS_FIRE) ? !worldserver.getGameRules().getBoolean(GameRules.RULE_FIRE_DAMAGE) : (damagesource.is(DamageTypeTags.IS_FREEZING) ? !worldserver.getGameRules().getBoolean(GameRules.RULE_FREEZE_DAMAGE) : false))));
    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (this.isInvulnerableTo(worldserver, damagesource)) {
            return false;
        } else if (this.abilities.invulnerable && !damagesource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        } else {
            this.noActionTime = 0;
            if (this.isDeadOrDying()) {
                return false;
            } else {
                // this.removeEntitiesOnShoulder(); // CraftBukkit - moved down
                if (damagesource.scalesWithDifficulty()) {
                    if (worldserver.getDifficulty() == EnumDifficulty.PEACEFUL) {
                        return false; // CraftBukkit - f = 0.0f -> return false
                    }

                    if (worldserver.getDifficulty() == EnumDifficulty.EASY) {
                        f = Math.min(f / 2.0F + 1.0F, f);
                    }

                    if (worldserver.getDifficulty() == EnumDifficulty.HARD) {
                        f = f * 3.0F / 2.0F;
                    }
                }

                // CraftBukkit start - Don't filter out 0 damage
                boolean damaged = super.hurtServer(worldserver, damagesource, f);
                if (damaged) {
                    this.removeEntitiesOnShoulder();
                }
                return damaged;
                // CraftBukkit end
            }
        }
    }

    @Override
    protected void blockUsingItem(WorldServer worldserver, EntityLiving entityliving) {
        super.blockUsingItem(worldserver, entityliving);
        ItemStack itemstack = this.getItemBlockingWith();
        BlocksAttacks blocksattacks = itemstack != null ? (BlocksAttacks) itemstack.get(DataComponents.BLOCKS_ATTACKS) : null;
        float f = entityliving.getSecondsToDisableBlocking();

        if (f > 0.0F && blocksattacks != null) {
            blocksattacks.disable(worldserver, this, f, itemstack);
        }

    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.getAbilities().invulnerable && super.canBeSeenAsEnemy();
    }

    public boolean canHarmPlayer(EntityHuman entityhuman) {
        // CraftBukkit start - Change to check OTHER player's scoreboard team according to API
        // To summarize this method's logic, it's "Can parameter hurt this"
        org.bukkit.scoreboard.Team team;
        if (entityhuman instanceof EntityPlayer) {
            EntityPlayer thatPlayer = (EntityPlayer) entityhuman;
            team = thatPlayer.getBukkitEntity().getScoreboard().getPlayerTeam(thatPlayer.getBukkitEntity());
            if (team == null || team.allowFriendlyFire()) {
                return true;
            }
        } else {
            // This should never be called, but is implemented anyway
            org.bukkit.OfflinePlayer thisPlayer = entityhuman.level().getCraftServer().getOfflinePlayer(entityhuman.getScoreboardName());
            team = entityhuman.level().getCraftServer().getScoreboardManager().getMainScoreboard().getPlayerTeam(thisPlayer);
            if (team == null || team.allowFriendlyFire()) {
                return true;
            }
        }

        if (this instanceof EntityPlayer) {
            return !team.hasPlayer(((EntityPlayer) this).getBukkitEntity());
        }
        return !team.hasPlayer(this.level().getCraftServer().getOfflinePlayer(this.getScoreboardName()));
        // CraftBukkit end
    }

    @Override
    protected void hurtArmor(DamageSource damagesource, float f) {
        this.doHurtEquipment(damagesource, f, new EnumItemSlot[]{EnumItemSlot.FEET, EnumItemSlot.LEGS, EnumItemSlot.CHEST, EnumItemSlot.HEAD});
    }

    @Override
    protected void hurtHelmet(DamageSource damagesource, float f) {
        this.doHurtEquipment(damagesource, f, new EnumItemSlot[]{EnumItemSlot.HEAD});
    }

    @Override
    // CraftBukkit start
    protected boolean actuallyHurt(WorldServer worldserver, DamageSource damagesource, float f, EntityDamageEvent event) { // void -> boolean
        if (true) {
            return super.actuallyHurt(worldserver, damagesource, f, event);
        }
        // CraftBukkit end
        if (!this.isInvulnerableTo(worldserver, damagesource)) {
            f = this.getDamageAfterArmorAbsorb(damagesource, f);
            f = this.getDamageAfterMagicAbsorb(damagesource, f);
            float f1 = f;

            f = Math.max(f - this.getAbsorptionAmount(), 0.0F);
            this.setAbsorptionAmount(this.getAbsorptionAmount() - (f1 - f));
            float f2 = f1 - f;

            if (f2 > 0.0F && f2 < 3.4028235E37F) {
                this.awardStat(StatisticList.DAMAGE_ABSORBED, Math.round(f2 * 10.0F));
            }

            if (f != 0.0F) {
                this.causeFoodExhaustion(damagesource.getFoodExhaustion(), EntityExhaustionEvent.ExhaustionReason.DAMAGED); // CraftBukkit - EntityExhaustionEvent
                this.getCombatTracker().recordDamage(damagesource, f);
                this.setHealth(this.getHealth() - f);
                if (f < 3.4028235E37F) {
                    this.awardStat(StatisticList.DAMAGE_TAKEN, Math.round(f * 10.0F));
                }

                this.gameEvent(GameEvent.ENTITY_DAMAGE);
            }
        }
        return false; // CraftBukkit
    }

    public boolean isTextFilteringEnabled() {
        return false;
    }

    public void openTextEdit(TileEntitySign tileentitysign, boolean flag) {}

    public void openMinecartCommandBlock(CommandBlockListenerAbstract commandblocklistenerabstract) {}

    public void openCommandBlock(TileEntityCommand tileentitycommand) {}

    public void openStructureBlock(TileEntityStructure tileentitystructure) {}

    public void openTestBlock(TestBlockEntity testblockentity) {}

    public void openTestInstanceBlock(TestInstanceBlockEntity testinstanceblockentity) {}

    public void openJigsawBlock(TileEntityJigsaw tileentityjigsaw) {}

    public void openHorseInventory(EntityHorseAbstract entityhorseabstract, IInventory iinventory) {}

    public OptionalInt openMenu(@Nullable ITileInventory itileinventory) {
        return OptionalInt.empty();
    }

    public void sendMerchantOffers(int i, MerchantRecipeList merchantrecipelist, int j, int k, boolean flag, boolean flag1) {}

    public void openItemGui(ItemStack itemstack, EnumHand enumhand) {}

    public EnumInteractionResult interactOn(Entity entity, EnumHand enumhand) {
        if (this.isSpectator()) {
            if (entity instanceof ITileInventory) {
                this.openMenu((ITileInventory) entity);
            }

            return EnumInteractionResult.PASS;
        } else {
            ItemStack itemstack = this.getItemInHand(enumhand);
            ItemStack itemstack1 = itemstack.copy();
            EnumInteractionResult enuminteractionresult = entity.interact(this, enumhand);

            if (enuminteractionresult.consumesAction()) {
                if (this.hasInfiniteMaterials() && itemstack == this.getItemInHand(enumhand) && itemstack.getCount() < itemstack1.getCount()) {
                    itemstack.setCount(itemstack1.getCount());
                }

                return enuminteractionresult;
            } else {
                if (!itemstack.isEmpty() && entity instanceof EntityLiving) {
                    if (this.hasInfiniteMaterials()) {
                        itemstack = itemstack1;
                    }

                    EnumInteractionResult enuminteractionresult1 = itemstack.interactLivingEntity(this, (EntityLiving) entity, enumhand);

                    if (enuminteractionresult1.consumesAction()) {
                        this.level().gameEvent(GameEvent.ENTITY_INTERACT, entity.position(), GameEvent.a.of((Entity) this));
                        if (itemstack.isEmpty() && !this.hasInfiniteMaterials()) {
                            this.setItemInHand(enumhand, ItemStack.EMPTY);
                        }

                        return enuminteractionresult1;
                    }
                }

                return EnumInteractionResult.PASS;
            }
        }
    }

    @Override
    public void removeVehicle() {
        super.removeVehicle();
        this.boardingCooldown = 0;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.isSleeping();
    }

    @Override
    public boolean isAffectedByFluids() {
        return !this.abilities.flying;
    }

    @Override
    protected Vec3D maybeBackOffFromEdge(Vec3D vec3d, EnumMoveType enummovetype) {
        float f = this.maxUpStep();

        if (!this.abilities.flying && vec3d.y <= 0.0D && (enummovetype == EnumMoveType.SELF || enummovetype == EnumMoveType.PLAYER) && this.isStayingOnGroundSurface() && this.isAboveGround(f)) {
            double d0 = vec3d.x;
            double d1 = vec3d.z;
            double d2 = 0.05D;
            double d3 = Math.signum(d0) * 0.05D;

            double d4;

            for (d4 = Math.signum(d1) * 0.05D; d0 != 0.0D && this.canFallAtLeast(d0, 0.0D, (double) f); d0 -= d3) {
                if (Math.abs(d0) <= 0.05D) {
                    d0 = 0.0D;
                    break;
                }
            }

            while (d1 != 0.0D && this.canFallAtLeast(0.0D, d1, (double) f)) {
                if (Math.abs(d1) <= 0.05D) {
                    d1 = 0.0D;
                    break;
                }

                d1 -= d4;
            }

            while (d0 != 0.0D && d1 != 0.0D && this.canFallAtLeast(d0, d1, (double) f)) {
                if (Math.abs(d0) <= 0.05D) {
                    d0 = 0.0D;
                } else {
                    d0 -= d3;
                }

                if (Math.abs(d1) <= 0.05D) {
                    d1 = 0.0D;
                } else {
                    d1 -= d4;
                }
            }

            return new Vec3D(d0, vec3d.y, d1);
        } else {
            return vec3d;
        }
    }

    private boolean isAboveGround(float f) {
        return this.onGround() || this.fallDistance < (double) f && !this.canFallAtLeast(0.0D, 0.0D, (double) f - this.fallDistance);
    }

    private boolean canFallAtLeast(double d0, double d1, double d2) {
        AxisAlignedBB axisalignedbb = this.getBoundingBox();

        return this.level().noCollision(this, new AxisAlignedBB(axisalignedbb.minX + 1.0E-7D + d0, axisalignedbb.minY - d2 - 1.0E-7D, axisalignedbb.minZ + 1.0E-7D + d1, axisalignedbb.maxX - 1.0E-7D + d0, axisalignedbb.minY, axisalignedbb.maxZ - 1.0E-7D + d1));
    }

    public void attack(Entity entity) {
        if (entity.isAttackable()) {
            if (!entity.skipAttackInteraction(this)) {
                float f = this.isAutoSpinAttack() ? this.autoSpinAttackDmg : (float) this.getAttributeValue(GenericAttributes.ATTACK_DAMAGE);
                ItemStack itemstack = this.getWeaponItem();
                DamageSource damagesource = (DamageSource) Optional.ofNullable(itemstack.getItem().getDamageSource(this)).orElse(this.damageSources().playerAttack(this));
                float f1 = this.getEnchantedDamage(entity, f, damagesource) - f;
                float f2 = this.getAttackStrengthScale(0.5F);

                f *= 0.2F + f2 * f2 * 0.8F;
                f1 *= f2;
                // this.resetAttackStrengthTicker(); // CraftBukkit - Moved to EntityLiving to reset the cooldown after the damage is dealt
                if (entity.getType().is(TagsEntity.REDIRECTABLE_PROJECTILE) && entity instanceof IProjectile) {
                    IProjectile iprojectile = (IProjectile) entity;

                    // CraftBukkit start
                    if (CraftEventFactory.handleNonLivingEntityDamageEvent(entity, damagesource, f1, false)) {
                        return;
                    }
                    // CraftBukkit end
                    if (iprojectile.deflect(ProjectileDeflection.AIM_DEFLECT, this, this, true)) {
                        this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.PLAYER_ATTACK_NODAMAGE, this.getSoundSource());
                        return;
                    }
                }

                if (f > 0.0F || f1 > 0.0F) {
                    boolean flag = f2 > 0.9F;
                    boolean flag1;

                    if (this.isSprinting() && flag) {
                        this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.PLAYER_ATTACK_KNOCKBACK, this.getSoundSource(), 1.0F, 1.0F);
                        flag1 = true;
                    } else {
                        flag1 = false;
                    }

                    f += itemstack.getItem().getAttackDamageBonus(entity, f, damagesource);
                    boolean flag2 = flag && this.fallDistance > 0.0D && !this.onGround() && !this.onClimbable() && !this.isInWater() && !this.hasEffect(MobEffects.BLINDNESS) && !this.isPassenger() && entity instanceof EntityLiving && !this.isSprinting();

                    if (flag2) {
                        f *= 1.5F;
                    }

                    float f3 = f + f1;
                    boolean flag3 = false;

                    if (flag && !flag2 && !flag1 && this.onGround()) {
                        double d0 = this.getKnownMovement().horizontalDistanceSqr();
                        double d1 = (double) this.getSpeed() * 2.5D;

                        if (d0 < MathHelper.square(d1) && this.getItemInHand(EnumHand.MAIN_HAND).is(TagsItem.SWORDS)) {
                            flag3 = true;
                        }
                    }

                    float f4 = 0.0F;

                    if (entity instanceof EntityLiving) {
                        EntityLiving entityliving = (EntityLiving) entity;

                        f4 = entityliving.getHealth();
                    }

                    Vec3D vec3d = entity.getDeltaMovement();
                    boolean flag4 = entity.hurtOrSimulate(damagesource, f3);

                    if (flag4) {
                        float f5 = this.getKnockback(entity, damagesource) + (flag1 ? 1.0F : 0.0F);

                        if (f5 > 0.0F) {
                            if (entity instanceof EntityLiving) {
                                EntityLiving entityliving1 = (EntityLiving) entity;

                                entityliving1.knockback((double) (f5 * 0.5F), (double) MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F)), (double) (-MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F))));
                            } else {
                                entity.push((double) (-MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F)) * f5 * 0.5F), 0.1D, (double) (MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F)) * f5 * 0.5F));
                            }

                            this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
                            this.setSprinting(false);
                        }

                        if (flag3) {
                            float f6 = 1.0F + (float) this.getAttributeValue(GenericAttributes.SWEEPING_DAMAGE_RATIO) * f;

                            for (EntityLiving entityliving2 : this.level().getEntitiesOfClass(EntityLiving.class, entity.getBoundingBox().inflate(1.0D, 0.25D, 1.0D))) {
                                if (entityliving2 != this && entityliving2 != entity && !this.isAlliedTo((Entity) entityliving2)) {
                                    if (entityliving2 instanceof EntityArmorStand) {
                                        EntityArmorStand entityarmorstand = (EntityArmorStand) entityliving2;

                                        if (entityarmorstand.isMarker()) {
                                            continue;
                                        }
                                    }

                                    if (this.distanceToSqr((Entity) entityliving2) < 9.0D) {
                                        float f7 = this.getEnchantedDamage(entityliving2, f6, damagesource) * f2;
                                        World world = this.level();

                                        if (world instanceof WorldServer) {
                                            WorldServer worldserver = (WorldServer) world;

                                            if (entityliving2.hurtServer(worldserver, damagesource, f7)) {
                                                entityliving2.knockback((double) 0.4F, (double) MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F)), (double) (-MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F))));
                                                EnchantmentManager.doPostAttackEffects(worldserver, entityliving2, damagesource);
                                            }
                                        }
                                    }
                                }
                            }

                            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.PLAYER_ATTACK_SWEEP, this.getSoundSource(), 1.0F, 1.0F);
                            this.sweepAttack();
                        }

                        if (entity instanceof EntityPlayer && entity.hurtMarked) {
                            // CraftBukkit start - Add Velocity Event
                            boolean cancelled = false;
                            Player player = (Player) entity.getBukkitEntity();
                            org.bukkit.util.Vector velocity = CraftVector.toBukkit(vec3d);

                            PlayerVelocityEvent event = new PlayerVelocityEvent(player, velocity.clone());
                            this.level().getCraftServer().getPluginManager().callEvent(event);

                            if (event.isCancelled()) {
                                cancelled = true;
                            } else if (!velocity.equals(event.getVelocity())) {
                                player.setVelocity(event.getVelocity());
                            }

                            if (!cancelled) {
                            ((EntityPlayer) entity).connection.send(new PacketPlayOutEntityVelocity(entity));
                            entity.hurtMarked = false;
                            entity.setDeltaMovement(vec3d);
                            }
                            // CraftBukkit end
                        }

                        if (flag2) {
                            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.PLAYER_ATTACK_CRIT, this.getSoundSource(), 1.0F, 1.0F);
                            this.crit(entity);
                        }

                        if (!flag2 && !flag3) {
                            if (flag) {
                                this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.PLAYER_ATTACK_STRONG, this.getSoundSource(), 1.0F, 1.0F);
                            } else {
                                this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.PLAYER_ATTACK_WEAK, this.getSoundSource(), 1.0F, 1.0F);
                            }
                        }

                        if (f1 > 0.0F) {
                            this.magicCrit(entity);
                        }

                        this.setLastHurtMob(entity);
                        Entity entity1 = entity;

                        if (entity instanceof EntityComplexPart) {
                            entity1 = ((EntityComplexPart) entity).parentMob;
                        }

                        boolean flag5 = false;
                        World world1 = this.level();

                        if (world1 instanceof WorldServer) {
                            WorldServer worldserver1 = (WorldServer) world1;

                            if (entity1 instanceof EntityLiving) {
                                EntityLiving entityliving3 = (EntityLiving) entity1;

                                flag5 = itemstack.hurtEnemy(entityliving3, this);
                            }

                            EnchantmentManager.doPostAttackEffects(worldserver1, entity, damagesource);
                        }

                        if (!this.level().isClientSide && !itemstack.isEmpty() && entity1 instanceof EntityLiving) {
                            if (flag5) {
                                itemstack.postHurtEnemy((EntityLiving) entity1, this);
                            }

                            if (itemstack.isEmpty()) {
                                if (itemstack == this.getMainHandItem()) {
                                    this.setItemInHand(EnumHand.MAIN_HAND, ItemStack.EMPTY);
                                } else {
                                    this.setItemInHand(EnumHand.OFF_HAND, ItemStack.EMPTY);
                                }
                            }
                        }

                        if (entity instanceof EntityLiving) {
                            float f8 = f4 - ((EntityLiving) entity).getHealth();

                            this.awardStat(StatisticList.DAMAGE_DEALT, Math.round(f8 * 10.0F));
                            if (this.level() instanceof WorldServer && f8 > 2.0F) {
                                int i = (int) ((double) f8 * 0.5D);

                                ((WorldServer) this.level()).sendParticles(Particles.DAMAGE_INDICATOR, entity.getX(), entity.getY(0.5D), entity.getZ(), i, 0.1D, 0.0D, 0.1D, 0.2D);
                            }
                        }

                        this.causeFoodExhaustion(this.level().spigotConfig.combatExhaustion, EntityExhaustionEvent.ExhaustionReason.ATTACK); // CraftBukkit - EntityExhaustionEvent // Spigot - Change to use configurable value
                    } else {
                        this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.PLAYER_ATTACK_NODAMAGE, this.getSoundSource(), 1.0F, 1.0F);
                        // CraftBukkit start - resync on cancelled event
                        if (this instanceof EntityPlayer) {
                            ((EntityPlayer) this).getBukkitEntity().updateInventory();
                        }
                        // CraftBukkit end
                    }
                }

            }
        }
    }

    protected float getEnchantedDamage(Entity entity, float f, DamageSource damagesource) {
        return f;
    }

    @Override
    protected void doAutoAttackOnTouch(EntityLiving entityliving) {
        this.attack(entityliving);
    }

    public void crit(Entity entity) {}

    public void magicCrit(Entity entity) {}

    public void sweepAttack() {
        double d0 = (double) (-MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F)));
        double d1 = (double) MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F));

        if (this.level() instanceof WorldServer) {
            ((WorldServer) this.level()).sendParticles(Particles.SWEEP_ATTACK, this.getX() + d0, this.getY(0.5D), this.getZ() + d1, 0, d0, 0.0D, d1, 0.0D);
        }

    }

    public void respawn() {}

    @Override
    public void remove(Entity.RemovalReason entity_removalreason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(entity_removalreason, null);
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        super.remove(entity_removalreason, cause);
        // CraftBukkit end
        this.inventoryMenu.removed(this);
        if (this.containerMenu != null && this.hasContainerOpen()) {
            this.doCloseContainer();
        }

    }

    @Override
    public boolean isClientAuthoritative() {
        return true;
    }

    @Override
    protected boolean isLocalClientAuthoritative() {
        return this.isLocalPlayer();
    }

    public boolean isLocalPlayer() {
        return false;
    }

    @Override
    public boolean canSimulateMovement() {
        return !this.level().isClientSide || this.isLocalPlayer();
    }

    @Override
    public boolean isEffectiveAi() {
        return !this.level().isClientSide || this.isLocalPlayer();
    }

    public GameProfile getGameProfile() {
        return this.gameProfile;
    }

    public PlayerInventory getInventory() {
        return this.inventory;
    }

    public PlayerAbilities getAbilities() {
        return this.abilities;
    }

    @Override
    public boolean hasInfiniteMaterials() {
        return this.abilities.instabuild;
    }

    public boolean preventsBlockDrops() {
        return this.abilities.instabuild;
    }

    public void updateTutorialInventoryAction(ItemStack itemstack, ItemStack itemstack1, ClickAction clickaction) {}

    public boolean hasContainerOpen() {
        return this.containerMenu != this.inventoryMenu;
    }

    public boolean canDropItems() {
        return true;
    }

    public Either<EntityHuman.EnumBedResult, Unit> startSleepInBed(BlockPosition blockposition) {
        // CraftBukkit start
        return this.startSleepInBed(blockposition, false);
    }

    public Either<EntityHuman.EnumBedResult, Unit> startSleepInBed(BlockPosition blockposition, boolean force) {
        // CraftBukkit end
        this.startSleeping(blockposition);
        this.sleepCounter = 0;
        return Either.right(Unit.INSTANCE);
    }

    public void stopSleepInBed(boolean flag, boolean flag1) {
        super.stopSleeping();
        if (this.level() instanceof WorldServer && flag1) {
            ((WorldServer) this.level()).updateSleepingPlayerList();
        }

        this.sleepCounter = flag ? 0 : 100;
    }

    @Override
    public void stopSleeping() {
        this.stopSleepInBed(true, true);
    }

    public boolean isSleepingLongEnough() {
        return this.isSleeping() && this.sleepCounter >= 100;
    }

    public int getSleepTimer() {
        return this.sleepCounter;
    }

    public void displayClientMessage(IChatBaseComponent ichatbasecomponent, boolean flag) {}

    public void awardStat(MinecraftKey minecraftkey) {
        this.awardStat(StatisticList.CUSTOM.get(minecraftkey));
    }

    public void awardStat(MinecraftKey minecraftkey, int i) {
        this.awardStat(StatisticList.CUSTOM.get(minecraftkey), i);
    }

    public void awardStat(Statistic<?> statistic) {
        this.awardStat(statistic, 1);
    }

    public void awardStat(Statistic<?> statistic, int i) {}

    public void resetStat(Statistic<?> statistic) {}

    public int awardRecipes(Collection<RecipeHolder<?>> collection) {
        return 0;
    }

    public void triggerRecipeCrafted(RecipeHolder<?> recipeholder, List<ItemStack> list) {}

    public void awardRecipesByKey(List<ResourceKey<IRecipe<?>>> list) {}

    public int resetRecipes(Collection<RecipeHolder<?>> collection) {
        return 0;
    }

    @Override
    public void travel(Vec3D vec3d) {
        if (this.isPassenger()) {
            super.travel(vec3d);
        } else {
            if (this.isSwimming()) {
                double d0 = this.getLookAngle().y;
                double d1 = d0 < -0.2D ? 0.085D : 0.06D;

                if (d0 <= 0.0D || this.jumping || !this.level().getFluidState(BlockPosition.containing(this.getX(), this.getY() + 1.0D - 0.1D, this.getZ())).isEmpty()) {
                    Vec3D vec3d1 = this.getDeltaMovement();

                    this.setDeltaMovement(vec3d1.add(0.0D, (d0 - vec3d1.y) * d1, 0.0D));
                }
            }

            if (this.getAbilities().flying) {
                double d2 = this.getDeltaMovement().y;

                super.travel(vec3d);
                this.setDeltaMovement(this.getDeltaMovement().with(EnumDirection.EnumAxis.Y, d2 * 0.6D));
            } else {
                super.travel(vec3d);
            }

        }
    }

    @Override
    protected boolean canGlide() {
        return !this.abilities.flying && super.canGlide();
    }

    @Override
    public void updateSwimming() {
        if (this.abilities.flying) {
            this.setSwimming(false);
        } else {
            super.updateSwimming();
        }

    }

    protected boolean freeAt(BlockPosition blockposition) {
        return !this.level().getBlockState(blockposition).isSuffocating(this.level(), blockposition);
    }

    @Override
    public float getSpeed() {
        return (float) this.getAttributeValue(GenericAttributes.MOVEMENT_SPEED);
    }

    @Override
    public boolean causeFallDamage(double d0, float f, DamageSource damagesource) {
        if (this.abilities.mayfly) {
            return false;
        } else {
            if (d0 >= 2.0D) {
                this.awardStat(StatisticList.FALL_ONE_CM, (int) Math.round(d0 * 100.0D));
            }

            boolean flag = this.currentImpulseImpactPos != null && this.ignoreFallDamageFromCurrentImpulse;
            double d1;

            if (flag) {
                d1 = Math.min(d0, this.currentImpulseImpactPos.y - this.getY());
                boolean flag1 = d1 <= 0.0D;

                if (flag1) {
                    this.resetCurrentImpulseContext();
                } else {
                    this.tryResetCurrentImpulseContext();
                }
            } else {
                d1 = d0;
            }

            if (d1 > 0.0D && super.causeFallDamage(d1, f, damagesource)) {
                this.resetCurrentImpulseContext();
                return true;
            } else {
                this.propagateFallToPassengers(d0, f, damagesource);
                return false;
            }
        }
    }

    public boolean tryToStartFallFlying() {
        if (!this.isFallFlying() && this.canGlide() && !this.isInWater()) {
            this.startFallFlying();
            return true;
        } else {
            return false;
        }
    }

    public void startFallFlying() {
        // CraftBukkit start
        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callToggleGlideEvent(this, true).isCancelled()) {
            this.setSharedFlag(7, true);
        } else {
            // SPIGOT-5542: must toggle like below
            this.setSharedFlag(7, true);
            this.setSharedFlag(7, false);
        }
        // CraftBukkit end
    }

    @Override
    protected void doWaterSplashEffect() {
        if (!this.isSpectator()) {
            super.doWaterSplashEffect();
        }

    }

    @Override
    protected void playStepSound(BlockPosition blockposition, IBlockData iblockdata) {
        if (this.isInWater()) {
            this.waterSwimSound();
            this.playMuffledStepSound(iblockdata);
        } else {
            BlockPosition blockposition1 = this.getPrimaryStepSoundBlockPos(blockposition);

            if (!blockposition.equals(blockposition1)) {
                IBlockData iblockdata1 = this.level().getBlockState(blockposition1);

                if (iblockdata1.is(TagsBlock.COMBINATION_STEP_SOUND_BLOCKS)) {
                    this.playCombinationStepSounds(iblockdata1, iblockdata);
                } else {
                    super.playStepSound(blockposition1, iblockdata1);
                }
            } else {
                super.playStepSound(blockposition, iblockdata);
            }
        }

    }

    @Override
    public EntityLiving.a getFallSounds() {
        return new EntityLiving.a(SoundEffects.PLAYER_SMALL_FALL, SoundEffects.PLAYER_BIG_FALL);
    }

    @Override
    public boolean killedEntity(WorldServer worldserver, EntityLiving entityliving) {
        this.awardStat(StatisticList.ENTITY_KILLED.get(entityliving.getType()));
        return true;
    }

    @Override
    public void makeStuckInBlock(IBlockData iblockdata, Vec3D vec3d) {
        if (!this.abilities.flying) {
            super.makeStuckInBlock(iblockdata, vec3d);
        }

        this.tryResetCurrentImpulseContext();
    }

    public void giveExperiencePoints(int i) {
        this.increaseScore(i);
        this.experienceProgress += (float) i / (float) this.getXpNeededForNextLevel();
        this.totalExperience = MathHelper.clamp(this.totalExperience + i, 0, Integer.MAX_VALUE);

        while (this.experienceProgress < 0.0F) {
            float f = this.experienceProgress * (float) this.getXpNeededForNextLevel();

            if (this.experienceLevel > 0) {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 1.0F + f / (float) this.getXpNeededForNextLevel();
            } else {
                this.giveExperienceLevels(-1);
                this.experienceProgress = 0.0F;
            }
        }

        while (this.experienceProgress >= 1.0F) {
            this.experienceProgress = (this.experienceProgress - 1.0F) * (float) this.getXpNeededForNextLevel();
            this.giveExperienceLevels(1);
            this.experienceProgress /= (float) this.getXpNeededForNextLevel();
        }

    }

    public int getEnchantmentSeed() {
        return this.enchantmentSeed;
    }

    public void onEnchantmentPerformed(ItemStack itemstack, int i) {
        this.experienceLevel -= i;
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        this.enchantmentSeed = this.random.nextInt();
    }

    public void giveExperienceLevels(int i) {
        this.experienceLevel = IntMath.saturatedAdd(this.experienceLevel, i);
        if (this.experienceLevel < 0) {
            this.experienceLevel = 0;
            this.experienceProgress = 0.0F;
            this.totalExperience = 0;
        }

        if (i > 0 && this.experienceLevel % 5 == 0 && (float) this.lastLevelUpTime < (float) this.tickCount - 100.0F) {
            float f = this.experienceLevel > 30 ? 1.0F : (float) this.experienceLevel / 30.0F;

            this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.PLAYER_LEVELUP, this.getSoundSource(), f * 0.75F, 1.0F);
            this.lastLevelUpTime = this.tickCount;
        }

    }

    public int getXpNeededForNextLevel() {
        return this.experienceLevel >= 30 ? 112 + (this.experienceLevel - 30) * 9 : (this.experienceLevel >= 15 ? 37 + (this.experienceLevel - 15) * 5 : 7 + this.experienceLevel * 2);
    }

    // CraftBukkit start
    public void causeFoodExhaustion(float f) {
        this.causeFoodExhaustion(f, EntityExhaustionEvent.ExhaustionReason.UNKNOWN);
    }

    public void causeFoodExhaustion(float f, EntityExhaustionEvent.ExhaustionReason reason) {
        // CraftBukkit end
        if (!this.abilities.invulnerable) {
            if (!this.level().isClientSide) {
                // CraftBukkit start
                EntityExhaustionEvent event = CraftEventFactory.callPlayerExhaustionEvent(this, reason, f);
                if (!event.isCancelled()) {
                    this.foodData.addExhaustion(event.getExhaustion());
                }
                // CraftBukkit end
            }

        }
    }

    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.empty();
    }

    public FoodMetaData getFoodData() {
        return this.foodData;
    }

    public boolean canEat(boolean flag) {
        return this.abilities.invulnerable || flag || this.foodData.needsFood();
    }

    public boolean isHurt() {
        return this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth();
    }

    public boolean mayBuild() {
        return this.abilities.mayBuild;
    }

    public boolean mayUseItemAt(BlockPosition blockposition, EnumDirection enumdirection, ItemStack itemstack) {
        if (this.abilities.mayBuild) {
            return true;
        } else {
            BlockPosition blockposition1 = blockposition.relative(enumdirection.getOpposite());
            ShapeDetectorBlock shapedetectorblock = new ShapeDetectorBlock(this.level(), blockposition1, false);

            return itemstack.canPlaceOnBlockInAdventureMode(shapedetectorblock);
        }
    }

    @Override
    protected int getBaseExperienceReward(WorldServer worldserver) {
        return !worldserver.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) && !this.isSpectator() ? Math.min(this.experienceLevel * 7, 100) : 0;
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return this.abilities.flying || this.onGround() && this.isDiscrete() ? Entity.MovementEmission.NONE : Entity.MovementEmission.ALL;
    }

    public void onUpdateAbilities() {}

    @Override
    public IChatBaseComponent getName() {
        return IChatBaseComponent.literal(this.gameProfile.getName());
    }

    public InventoryEnderChest getEnderChestInventory() {
        return this.enderChestInventory;
    }

    @Override
    protected boolean doesEmitEquipEvent(EnumItemSlot enumitemslot) {
        return enumitemslot.getType() == EnumItemSlot.Function.HUMANOID_ARMOR;
    }

    public boolean addItem(ItemStack itemstack) {
        return this.inventory.add(itemstack);
    }

    public boolean setEntityOnShoulder(NBTTagCompound nbttagcompound) {
        if (!this.isPassenger() && this.onGround() && !this.isInWater() && !this.isInPowderSnow) {
            if (this.getShoulderEntityLeft().isEmpty()) {
                this.setShoulderEntityLeft(nbttagcompound);
                this.timeEntitySatOnShoulder = this.level().getGameTime();
                return true;
            } else if (this.getShoulderEntityRight().isEmpty()) {
                this.setShoulderEntityRight(nbttagcompound);
                this.timeEntitySatOnShoulder = this.level().getGameTime();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    protected void removeEntitiesOnShoulder() {
        if (this.timeEntitySatOnShoulder + 20L < this.level().getGameTime()) {
            // CraftBukkit start
            if (this.respawnEntityOnShoulder(this.getShoulderEntityLeft())) {
                this.setShoulderEntityLeft(new NBTTagCompound());
            }
            if (this.respawnEntityOnShoulder(this.getShoulderEntityRight())) {
                this.setShoulderEntityRight(new NBTTagCompound());
            }
            // CraftBukkit end
        }

    }

    private boolean respawnEntityOnShoulder(NBTTagCompound nbttagcompound) { // CraftBukkit void->boolean
        if (!this.level().isClientSide && !nbttagcompound.isEmpty()) {
            return EntityTypes.create(nbttagcompound, this.level(), EntitySpawnReason.LOAD).map((entity) -> { // CraftBukkit
                if (entity instanceof EntityTameableAnimal entitytameableanimal) {
                    entitytameableanimal.setOwner(this);
                }

                entity.setPos(this.getX(), this.getY() + (double) 0.7F, this.getZ());
                return ((WorldServer) this.level()).addWithUUID(entity, CreatureSpawnEvent.SpawnReason.SHOULDER_ENTITY); // CraftBukkit
            }).orElse(true); // CraftBukkit
        }

        return true; // CraftBukkit
    }

    @Nullable
    public abstract EnumGamemode gameMode();

    @Override
    public boolean isSpectator() {
        return this.gameMode() == EnumGamemode.SPECTATOR;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return !this.isSpectator() && super.canBeHitByProjectile();
    }

    @Override
    public boolean isSwimming() {
        return !this.abilities.flying && !this.isSpectator() && super.isSwimming();
    }

    public boolean isCreative() {
        return this.gameMode() == EnumGamemode.CREATIVE;
    }

    @Override
    public boolean isPushedByFluid() {
        return !this.abilities.flying;
    }

    public Scoreboard getScoreboard() {
        return this.level().getScoreboard();
    }

    @Override
    public IChatBaseComponent getDisplayName() {
        IChatMutableComponent ichatmutablecomponent = ScoreboardTeam.formatNameForTeam(this.getTeam(), this.getName());

        return this.decorateDisplayNameComponent(ichatmutablecomponent);
    }

    private IChatMutableComponent decorateDisplayNameComponent(IChatMutableComponent ichatmutablecomponent) {
        String s = this.getGameProfile().getName();

        return ichatmutablecomponent.withStyle((chatmodifier) -> {
            return chatmodifier.withClickEvent(new ChatClickable.SuggestCommand("/tell " + s + " ")).withHoverEvent(this.createHoverEvent()).withInsertion(s);
        });
    }

    @Override
    public String getScoreboardName() {
        return this.getGameProfile().getName();
    }

    @Override
    protected void internalSetAbsorptionAmount(float f) {
        this.getEntityData().set(EntityHuman.DATA_PLAYER_ABSORPTION_ID, f);
    }

    @Override
    public float getAbsorptionAmount() {
        return (Float) this.getEntityData().get(EntityHuman.DATA_PLAYER_ABSORPTION_ID);
    }

    public boolean isModelPartShown(PlayerModelPart playermodelpart) {
        return ((Byte) this.getEntityData().get(EntityHuman.DATA_PLAYER_MODE_CUSTOMISATION) & playermodelpart.getMask()) == playermodelpart.getMask();
    }

    @Override
    public SlotAccess getSlot(int i) {
        if (i == 499) {
            return new SlotAccess() {
                @Override
                public ItemStack get() {
                    return EntityHuman.this.containerMenu.getCarried();
                }

                @Override
                public boolean set(ItemStack itemstack) {
                    EntityHuman.this.containerMenu.setCarried(itemstack);
                    return true;
                }
            };
        } else {
            final int j = i - 500;

            if (j >= 0 && j < 4) {
                return new SlotAccess() {
                    @Override
                    public ItemStack get() {
                        return EntityHuman.this.inventoryMenu.getCraftSlots().getItem(j);
                    }

                    @Override
                    public boolean set(ItemStack itemstack) {
                        EntityHuman.this.inventoryMenu.getCraftSlots().setItem(j, itemstack);
                        EntityHuman.this.inventoryMenu.slotsChanged(EntityHuman.this.inventory);
                        return true;
                    }
                };
            } else if (i >= 0 && i < this.inventory.getNonEquipmentItems().size()) {
                return SlotAccess.forContainer(this.inventory, i);
            } else {
                int k = i - 200;

                return k >= 0 && k < this.enderChestInventory.getContainerSize() ? SlotAccess.forContainer(this.enderChestInventory, k) : super.getSlot(i);
            }
        }
    }

    public boolean isReducedDebugInfo() {
        return this.reducedDebugInfo;
    }

    public void setReducedDebugInfo(boolean flag) {
        this.reducedDebugInfo = flag;
    }

    @Override
    public void setRemainingFireTicks(int i) {
        super.setRemainingFireTicks(this.abilities.invulnerable ? Math.min(i, 1) : i);
    }

    @Override
    public EnumMainHand getMainArm() {
        return (Byte) this.entityData.get(EntityHuman.DATA_PLAYER_MAIN_HAND) == 0 ? EnumMainHand.LEFT : EnumMainHand.RIGHT;
    }

    public void setMainArm(EnumMainHand enummainhand) {
        this.entityData.set(EntityHuman.DATA_PLAYER_MAIN_HAND, (byte) (enummainhand == EnumMainHand.LEFT ? 0 : 1));
    }

    public NBTTagCompound getShoulderEntityLeft() {
        return (NBTTagCompound) this.entityData.get(EntityHuman.DATA_SHOULDER_LEFT);
    }

    public void setShoulderEntityLeft(NBTTagCompound nbttagcompound) {
        this.entityData.set(EntityHuman.DATA_SHOULDER_LEFT, nbttagcompound);
    }

    public NBTTagCompound getShoulderEntityRight() {
        return (NBTTagCompound) this.entityData.get(EntityHuman.DATA_SHOULDER_RIGHT);
    }

    public void setShoulderEntityRight(NBTTagCompound nbttagcompound) {
        this.entityData.set(EntityHuman.DATA_SHOULDER_RIGHT, nbttagcompound);
    }

    public float getCurrentItemAttackStrengthDelay() {
        return (float) (1.0D / this.getAttributeValue(GenericAttributes.ATTACK_SPEED) * 20.0D);
    }

    public float getAttackStrengthScale(float f) {
        return MathHelper.clamp(((float) this.attackStrengthTicker + f) / this.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F);
    }

    public void resetAttackStrengthTicker() {
        this.attackStrengthTicker = 0;
    }

    public ItemCooldown getCooldowns() {
        return this.cooldowns;
    }

    @Override
    protected float getBlockSpeedFactor() {
        return !this.abilities.flying && !this.isFallFlying() ? super.getBlockSpeedFactor() : 1.0F;
    }

    @Override
    public float getLuck() {
        return (float) this.getAttributeValue(GenericAttributes.LUCK);
    }

    public boolean canUseGameMasterBlocks() {
        return this.abilities.instabuild && this.getPermissionLevel() >= 2;
    }

    public int getPermissionLevel() {
        return 0;
    }

    public boolean hasPermissions(int i) {
        return this.getPermissionLevel() >= i;
    }

    @Override
    public EntitySize getDefaultDimensions(EntityPose entitypose) {
        return (EntitySize) EntityHuman.POSES.getOrDefault(entitypose, EntityHuman.STANDING_DIMENSIONS);
    }

    @Override
    public ImmutableList<EntityPose> getDismountPoses() {
        return ImmutableList.of(EntityPose.STANDING, EntityPose.CROUCHING, EntityPose.SWIMMING);
    }

    @Override
    public ItemStack getProjectile(ItemStack itemstack) {
        if (!(itemstack.getItem() instanceof ItemProjectileWeapon)) {
            return ItemStack.EMPTY;
        } else {
            Predicate<ItemStack> predicate = ((ItemProjectileWeapon) itemstack.getItem()).getSupportedHeldProjectiles();
            ItemStack itemstack1 = ItemProjectileWeapon.getHeldProjectile(this, predicate);

            if (!itemstack1.isEmpty()) {
                return itemstack1;
            } else {
                predicate = ((ItemProjectileWeapon) itemstack.getItem()).getAllSupportedProjectiles();

                for (int i = 0; i < this.inventory.getContainerSize(); ++i) {
                    ItemStack itemstack2 = this.inventory.getItem(i);

                    if (predicate.test(itemstack2)) {
                        return itemstack2;
                    }
                }

                return this.hasInfiniteMaterials() ? new ItemStack(Items.ARROW) : ItemStack.EMPTY;
            }
        }
    }

    @Override
    public Vec3D getRopeHoldPosition(float f) {
        double d0 = 0.22D * (this.getMainArm() == EnumMainHand.RIGHT ? -1.0D : 1.0D);
        float f1 = MathHelper.lerp(f * 0.5F, this.getXRot(), this.xRotO) * ((float) Math.PI / 180F);
        float f2 = MathHelper.lerp(f, this.yBodyRotO, this.yBodyRot) * ((float) Math.PI / 180F);

        if (!this.isFallFlying() && !this.isAutoSpinAttack()) {
            if (this.isVisuallySwimming()) {
                return this.getPosition(f).add((new Vec3D(d0, 0.2D, -0.15D)).xRot(-f1).yRot(-f2));
            } else {
                double d1 = this.getBoundingBox().getYsize() - 1.0D;
                double d2 = this.isCrouching() ? -0.2D : 0.07D;

                return this.getPosition(f).add((new Vec3D(d0, d1, d2)).yRot(-f2));
            }
        } else {
            Vec3D vec3d = this.getViewVector(f);
            Vec3D vec3d1 = this.getDeltaMovement();
            double d3 = vec3d1.horizontalDistanceSqr();
            double d4 = vec3d.horizontalDistanceSqr();
            float f3;

            if (d3 > 0.0D && d4 > 0.0D) {
                double d5 = (vec3d1.x * vec3d.x + vec3d1.z * vec3d.z) / Math.sqrt(d3 * d4);
                double d6 = vec3d1.x * vec3d.z - vec3d1.z * vec3d.x;

                f3 = (float) (Math.signum(d6) * Math.acos(d5));
            } else {
                f3 = 0.0F;
            }

            return this.getPosition(f).add((new Vec3D(d0, -0.11D, 0.85D)).zRot(-f3).xRot(-f1).yRot(-f2));
        }
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    public boolean isScoping() {
        return this.isUsingItem() && this.getUseItem().is(Items.SPYGLASS);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    public Optional<GlobalPos> getLastDeathLocation() {
        return this.lastDeathLocation;
    }

    public void setLastDeathLocation(Optional<GlobalPos> optional) {
        this.lastDeathLocation = optional;
    }

    @Override
    public float getHurtDir() {
        return this.hurtDir;
    }

    @Override
    public void animateHurt(float f) {
        super.animateHurt(f);
        this.hurtDir = f;
    }

    @Override
    public boolean canSprint() {
        return true;
    }

    @Override
    protected float getFlyingSpeed() {
        return this.abilities.flying && !this.isPassenger() ? (this.isSprinting() ? this.abilities.getFlyingSpeed() * 2.0F : this.abilities.getFlyingSpeed()) : (this.isSprinting() ? 0.025999999F : 0.02F);
    }

    public boolean hasClientLoaded() {
        return this.clientLoaded || this.clientLoadedTimeoutTimer <= 0;
    }

    public void tickClientLoadTimeout() {
        if (!this.clientLoaded) {
            --this.clientLoadedTimeoutTimer;
        }

    }

    public void setClientLoaded(boolean flag) {
        this.clientLoaded = flag;
        if (!this.clientLoaded) {
            this.clientLoadedTimeoutTimer = 60;
        }

    }

    public double blockInteractionRange() {
        return this.getAttributeValue(GenericAttributes.BLOCK_INTERACTION_RANGE);
    }

    public double entityInteractionRange() {
        return this.getAttributeValue(GenericAttributes.ENTITY_INTERACTION_RANGE);
    }

    public boolean canInteractWithEntity(Entity entity, double d0) {
        return entity.isRemoved() ? false : this.canInteractWithEntity(entity.getBoundingBox(), d0);
    }

    public boolean canInteractWithEntity(AxisAlignedBB axisalignedbb, double d0) {
        double d1 = this.entityInteractionRange() + d0;

        return axisalignedbb.distanceToSqr(this.getEyePosition()) < d1 * d1;
    }

    public boolean canInteractWithBlock(BlockPosition blockposition, double d0) {
        double d1 = this.blockInteractionRange() + d0;

        return (new AxisAlignedBB(blockposition)).distanceToSqr(this.getEyePosition()) < d1 * d1;
    }

    public void setIgnoreFallDamageFromCurrentImpulse(boolean flag) {
        this.ignoreFallDamageFromCurrentImpulse = flag;
        if (flag) {
            this.currentImpulseContextResetGraceTime = 40;
        } else {
            this.currentImpulseContextResetGraceTime = 0;
        }

    }

    public boolean isIgnoringFallDamageFromCurrentImpulse() {
        return this.ignoreFallDamageFromCurrentImpulse;
    }

    public void tryResetCurrentImpulseContext() {
        if (this.currentImpulseContextResetGraceTime == 0) {
            this.resetCurrentImpulseContext();
        }

    }

    public void resetCurrentImpulseContext() {
        this.currentImpulseContextResetGraceTime = 0;
        this.currentExplosionCause = null;
        this.currentImpulseImpactPos = null;
        this.ignoreFallDamageFromCurrentImpulse = false;
    }

    public boolean shouldRotateWithMinecart() {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return this.abilities.flying ? false : super.onClimbable();
    }

    public String debugInfo() {
        return MoreObjects.toStringHelper(this).add("name", this.getName().getString()).add("id", this.getId()).add("pos", this.position()).add("mode", this.gameMode()).add("permission", this.getPermissionLevel()).toString();
    }

    public static enum EnumBedResult {

        NOT_POSSIBLE_HERE, NOT_POSSIBLE_NOW(IChatBaseComponent.translatable("block.minecraft.bed.no_sleep")), TOO_FAR_AWAY(IChatBaseComponent.translatable("block.minecraft.bed.too_far_away")), OBSTRUCTED(IChatBaseComponent.translatable("block.minecraft.bed.obstructed")), OTHER_PROBLEM, NOT_SAFE(IChatBaseComponent.translatable("block.minecraft.bed.not_safe"));

        @Nullable
        private final IChatBaseComponent message;

        private EnumBedResult() {
            this.message = null;
        }

        private EnumBedResult(final IChatBaseComponent ichatbasecomponent) {
            this.message = ichatbasecomponent;
        }

        @Nullable
        public IChatBaseComponent getMessage() {
            return this.message;
        }
    }
}

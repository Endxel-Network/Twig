package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BaseBlockPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.tags.TagKey;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.GameProfilerFiller;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.IInventory;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeBase;
import net.minecraft.world.entity.ai.attributes.AttributeModifiable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeProvider;
import net.minecraft.world.entity.ai.attributes.GenericAttributes;
import net.minecraft.world.entity.ai.control.ControllerJump;
import net.minecraft.world.entity.ai.control.ControllerLook;
import net.minecraft.world.entity.ai.control.ControllerMove;
import net.minecraft.world.entity.ai.control.EntityAIBodyControl;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.goal.PathfinderGoalSelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.Navigation;
import net.minecraft.world.entity.ai.navigation.NavigationAbstract;
import net.minecraft.world.entity.ai.sensing.EntitySenses;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.monster.IMonster;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.vehicle.AbstractBoat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemMonsterEgg;
import net.minecraft.world.item.ItemProjectileWeapon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameterSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameters;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.ticks.ContainerSingleItem;

// CraftBukkit start
import java.util.Arrays;
import org.bukkit.Location;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.EntityUnleashEvent.UnleashReason;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

public abstract class EntityInsentient extends EntityLiving implements EquipmentUser, Leashable, Targeting {

    private static final DataWatcherObject<Byte> DATA_MOB_FLAGS_ID = DataWatcher.<Byte>defineId(EntityInsentient.class, DataWatcherRegistry.BYTE);
    private static final int MOB_FLAG_NO_AI = 1;
    private static final int MOB_FLAG_LEFTHANDED = 2;
    private static final int MOB_FLAG_AGGRESSIVE = 4;
    protected static final int PICKUP_REACH = 1;
    private static final BaseBlockPosition ITEM_PICKUP_REACH = new BaseBlockPosition(1, 0, 1);
    private static final List<EnumItemSlot> EQUIPMENT_POPULATION_ORDER = List.of(EnumItemSlot.HEAD, EnumItemSlot.CHEST, EnumItemSlot.LEGS, EnumItemSlot.FEET);
    public static final float MAX_WEARING_ARMOR_CHANCE = 0.15F;
    public static final float MAX_PICKUP_LOOT_CHANCE = 0.55F;
    public static final float MAX_ENCHANTED_ARMOR_CHANCE = 0.5F;
    public static final float MAX_ENCHANTED_WEAPON_CHANCE = 0.25F;
    public static final int UPDATE_GOAL_SELECTOR_EVERY_N_TICKS = 2;
    private static final double DEFAULT_ATTACK_REACH = Math.sqrt((double) 2.04F) - (double) 0.6F;
    private static final boolean DEFAULT_CAN_PICK_UP_LOOT = false;
    private static final boolean DEFAULT_PERSISTENCE_REQUIRED = false;
    private static final boolean DEFAULT_LEFT_HANDED = false;
    private static final boolean DEFAULT_NO_AI = false;
    protected static final MinecraftKey RANDOM_SPAWN_BONUS_ID = MinecraftKey.withDefaultNamespace("random_spawn_bonus");
    public int ambientSoundTime;
    protected int xpReward;
    protected ControllerLook lookControl;
    protected ControllerMove moveControl;
    protected ControllerJump jumpControl;
    private final EntityAIBodyControl bodyRotationControl;
    protected NavigationAbstract navigation;
    public PathfinderGoalSelector goalSelector;
    public PathfinderGoalSelector targetSelector;
    @Nullable
    private EntityLiving target;
    private final EntitySenses sensing;
    private DropChances dropChances;
    private boolean canPickUpLoot;
    private boolean persistenceRequired;
    private final Map<PathType, Float> pathfindingMalus;
    public Optional<ResourceKey<LootTable>> lootTable;
    public long lootTableSeed;
    @Nullable
    private Leashable.a leashData;
    private BlockPosition restrictCenter;
    private float restrictRadius;

    public boolean aware = true; // CraftBukkit

    protected EntityInsentient(EntityTypes<? extends EntityInsentient> entitytypes, World world) {
        super(entitytypes, world);
        this.dropChances = DropChances.DEFAULT;
        this.canPickUpLoot = false;
        this.persistenceRequired = false;
        this.pathfindingMalus = Maps.newEnumMap(PathType.class);
        this.lootTable = Optional.empty();
        this.restrictCenter = BlockPosition.ZERO;
        this.restrictRadius = -1.0F;
        this.goalSelector = new PathfinderGoalSelector();
        this.targetSelector = new PathfinderGoalSelector();
        this.lookControl = new ControllerLook(this);
        this.moveControl = new ControllerMove(this);
        this.jumpControl = new ControllerJump(this);
        this.bodyRotationControl = this.createBodyControl();
        this.navigation = this.createNavigation(world);
        this.sensing = new EntitySenses(this);
        if (world instanceof WorldServer) {
            this.registerGoals();
        }

    }

    // CraftBukkit start
    public void setPersistenceRequired(boolean persistenceRequired) {
        this.persistenceRequired = persistenceRequired;
    }
    // CraftBukkit end

    protected void registerGoals() {}

    public static AttributeProvider.Builder createMobAttributes() {
        return EntityLiving.createLivingAttributes().add(GenericAttributes.FOLLOW_RANGE, 16.0D);
    }

    protected NavigationAbstract createNavigation(World world) {
        return new Navigation(this, world);
    }

    protected boolean shouldPassengersInheritMalus() {
        return false;
    }

    public float getPathfindingMalus(PathType pathtype) {
        EntityInsentient entityinsentient;
        label17:
        {
            Entity entity = this.getControlledVehicle();

            if (entity instanceof EntityInsentient entityinsentient1) {
                if (entityinsentient1.shouldPassengersInheritMalus()) {
                    entityinsentient = entityinsentient1;
                    break label17;
                }
            }

            entityinsentient = this;
        }

        Float ofloat = (Float) entityinsentient.pathfindingMalus.get(pathtype);

        return ofloat == null ? pathtype.getMalus() : ofloat;
    }

    public void setPathfindingMalus(PathType pathtype, float f) {
        this.pathfindingMalus.put(pathtype, f);
    }

    public void onPathfindingStart() {}

    public void onPathfindingDone() {}

    protected EntityAIBodyControl createBodyControl() {
        return new EntityAIBodyControl(this);
    }

    public ControllerLook getLookControl() {
        return this.lookControl;
    }

    public ControllerMove getMoveControl() {
        Entity entity = this.getControlledVehicle();

        if (entity instanceof EntityInsentient entityinsentient) {
            return entityinsentient.getMoveControl();
        } else {
            return this.moveControl;
        }
    }

    public ControllerJump getJumpControl() {
        return this.jumpControl;
    }

    public NavigationAbstract getNavigation() {
        Entity entity = this.getControlledVehicle();

        if (entity instanceof EntityInsentient entityinsentient) {
            return entityinsentient.getNavigation();
        } else {
            return this.navigation;
        }
    }

    @Nullable
    @Override
    public EntityLiving getControllingPassenger() {
        Entity entity = this.getFirstPassenger();
        EntityInsentient entityinsentient;

        if (!this.isNoAi() && entity instanceof EntityInsentient entityinsentient1) {
            if (entity.canControlVehicle()) {
                entityinsentient = entityinsentient1;
                return entityinsentient;
            }
        }

        entityinsentient = null;
        return entityinsentient;
    }

    public EntitySenses getSensing() {
        return this.sensing;
    }

    @Nullable
    @Override
    public EntityLiving getTarget() {
        return this.target;
    }

    @Nullable
    protected final EntityLiving getTargetFromBrain() {
        return (EntityLiving) this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null); // CraftBukkit - decompile error
    }

    public void setTarget(@Nullable EntityLiving entityliving) {
        // CraftBukkit start - fire event
        setTarget(entityliving, EntityTargetEvent.TargetReason.UNKNOWN, true);
    }

    public boolean setTarget(EntityLiving entityliving, EntityTargetEvent.TargetReason reason, boolean fireEvent) {
        if (getTarget() == entityliving) return false;
        if (fireEvent) {
            if (reason == EntityTargetEvent.TargetReason.UNKNOWN && getTarget() != null && entityliving == null) {
                reason = getTarget().isAlive() ? EntityTargetEvent.TargetReason.FORGOT_TARGET : EntityTargetEvent.TargetReason.TARGET_DIED;
            }
            if (reason == EntityTargetEvent.TargetReason.UNKNOWN) {
                this.level().getCraftServer().getLogger().log(java.util.logging.Level.WARNING, "Unknown target reason, please report on the issue tracker", new Exception());
            }
            CraftLivingEntity ctarget = null;
            if (entityliving != null) {
                ctarget = (CraftLivingEntity) entityliving.getBukkitEntity();
            }
            EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(this.getBukkitEntity(), ctarget, reason);
            this.level().getCraftServer().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return false;
            }

            if (event.getTarget() != null) {
                entityliving = ((CraftLivingEntity) event.getTarget()).getHandle();
            } else {
                entityliving = null;
            }
        }
        this.target = entityliving;
        return true;
        // CraftBukkit end
    }

    @Override
    public boolean canAttackType(EntityTypes<?> entitytypes) {
        return entitytypes != EntityTypes.GHAST;
    }

    public boolean canFireProjectileWeapon(ItemProjectileWeapon itemprojectileweapon) {
        return false;
    }

    public void ate() {
        this.gameEvent(GameEvent.EAT);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityInsentient.DATA_MOB_FLAGS_ID, (byte) 0);
    }

    public int getAmbientSoundInterval() {
        return 80;
    }

    public void playAmbientSound() {
        this.makeSound(this.getAmbientSound());
    }

    @Override
    public void baseTick() {
        super.baseTick();
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("mobBaseTick");
        if (this.isAlive() && this.random.nextInt(1000) < this.ambientSoundTime++) {
            this.resetAmbientSoundTime();
            this.playAmbientSound();
        }

        gameprofilerfiller.pop();
    }

    @Override
    protected void playHurtSound(DamageSource damagesource) {
        this.resetAmbientSoundTime();
        super.playHurtSound(damagesource);
    }

    private void resetAmbientSoundTime() {
        this.ambientSoundTime = -this.getAmbientSoundInterval();
    }

    @Override
    protected int getBaseExperienceReward(WorldServer worldserver) {
        if (this.xpReward > 0) {
            int i = this.xpReward;

            for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
                if (enumitemslot.canIncreaseExperience()) {
                    ItemStack itemstack = this.getItemBySlot(enumitemslot);

                    if (!itemstack.isEmpty() && this.dropChances.byEquipment(enumitemslot) <= 1.0F) {
                        i += 1 + this.random.nextInt(3);
                    }
                }
            }

            return i;
        } else {
            return this.xpReward;
        }
    }

    public void spawnAnim() {
        if (this.level().isClientSide) {
            this.makePoofParticles();
        } else {
            this.level().broadcastEntityEvent(this, (byte) 20);
        }

    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 20) {
            this.spawnAnim();
        } else {
            super.handleEntityEvent(b0);
        }

    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount % 5 == 0) {
            this.updateControlFlags();
        }

    }

    protected void updateControlFlags() {
        boolean flag = !(this.getControllingPassenger() instanceof EntityInsentient);
        boolean flag1 = !(this.getVehicle() instanceof AbstractBoat);

        this.goalSelector.setControlFlag(PathfinderGoal.Type.MOVE, flag);
        this.goalSelector.setControlFlag(PathfinderGoal.Type.JUMP, flag && flag1);
        this.goalSelector.setControlFlag(PathfinderGoal.Type.LOOK, flag);
    }

    @Override
    protected void tickHeadTurn(float f) {
        this.bodyRotationControl.clientTick();
    }

    @Nullable
    protected SoundEffect getAmbientSound() {
        return null;
    }

    // CraftBukkit start - Add delegate method
    public SoundEffect getAmbientSound0() {
        return getAmbientSound();
    }
    // CraftBukkit end

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putBoolean("CanPickUpLoot", this.canPickUpLoot());
        nbttagcompound.putBoolean("PersistenceRequired", this.persistenceRequired);
        RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

        if (!this.dropChances.equals(DropChances.DEFAULT)) {
            nbttagcompound.store("drop_chances", DropChances.CODEC, registryops, this.dropChances);
        }

        this.writeLeashData(nbttagcompound, this.leashData);
        nbttagcompound.putBoolean("LeftHanded", this.isLeftHanded());
        this.lootTable.ifPresent((resourcekey) -> {
            nbttagcompound.store("DeathLootTable", LootTable.KEY_CODEC, resourcekey);
        });
        if (this.lootTableSeed != 0L) {
            nbttagcompound.putLong("DeathLootTableSeed", this.lootTableSeed);
        }

        if (this.isNoAi()) {
            nbttagcompound.putBoolean("NoAI", this.isNoAi());
        }

        nbttagcompound.putBoolean("Bukkit.Aware", this.aware); // CraftBukkit
    }

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        // CraftBukkit start - If looting or persistence is false only use it if it was set after we started using it
        if (nbttagcompound.contains("CanPickUpLoot")) {
            boolean data = nbttagcompound.getBooleanOr("CanPickUpLoot", false);
            if (isLevelAtLeast(nbttagcompound, 1) || data) {
                this.setCanPickUpLoot(data);
            }
        }

        boolean data = nbttagcompound.getBooleanOr("PersistenceRequired", false);
        if (isLevelAtLeast(nbttagcompound, 1) || data) {
            this.persistenceRequired = data;
        }
        // CraftBukkit end
        RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

        this.dropChances = (DropChances) nbttagcompound.read("drop_chances", DropChances.CODEC, registryops).orElse(DropChances.DEFAULT);
        this.readLeashData(nbttagcompound);
        this.setLeftHanded(nbttagcompound.getBooleanOr("LeftHanded", false));
        this.lootTable = nbttagcompound.read("DeathLootTable", LootTable.KEY_CODEC);
        this.lootTableSeed = nbttagcompound.getLongOr("DeathLootTableSeed", 0L);
        this.setNoAi(nbttagcompound.getBooleanOr("NoAI", false));
        // CraftBukkit start
        this.aware = nbttagcompound.getBooleanOr("Bukkit.Aware", this.aware);
        // CraftBukkit end
    }

    @Override
    protected void dropFromLootTable(WorldServer worldserver, DamageSource damagesource, boolean flag) {
        super.dropFromLootTable(worldserver, damagesource, flag);
        this.lootTable = Optional.empty();
    }

    @Override
    public final Optional<ResourceKey<LootTable>> getLootTable() {
        return this.lootTable.isPresent() ? this.lootTable : super.getLootTable();
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    public void setZza(float f) {
        this.zza = f;
    }

    public void setYya(float f) {
        this.yya = f;
    }

    public void setXxa(float f) {
        this.xxa = f;
    }

    @Override
    public void setSpeed(float f) {
        super.setSpeed(f);
        this.setZza(f);
    }

    public void stopInPlace() {
        this.getNavigation().stop();
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setSpeed(0.0F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("looting");
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (this.canPickUpLoot() && this.isAlive() && !this.dead && worldserver.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                BaseBlockPosition baseblockposition = this.getPickupReach();

                for (EntityItem entityitem : this.level().getEntitiesOfClass(EntityItem.class, this.getBoundingBox().inflate((double) baseblockposition.getX(), (double) baseblockposition.getY(), (double) baseblockposition.getZ()))) {
                    if (!entityitem.isRemoved() && !entityitem.getItem().isEmpty() && !entityitem.hasPickUpDelay() && this.wantsToPickUp(worldserver, entityitem.getItem())) {
                        this.pickUpItem(worldserver, entityitem);
                    }
                }
            }
        }

        gameprofilerfiller.pop();
    }

    protected BaseBlockPosition getPickupReach() {
        return EntityInsentient.ITEM_PICKUP_REACH;
    }

    protected void pickUpItem(WorldServer worldserver, EntityItem entityitem) {
        ItemStack itemstack = entityitem.getItem();
        ItemStack itemstack1 = this.equipItemIfPossible(worldserver, itemstack.copy(), entityitem); // CraftBukkit - add item

        if (!itemstack1.isEmpty()) {
            this.onItemPickup(entityitem);
            this.take(entityitem, itemstack1.getCount());
            itemstack.shrink(itemstack1.getCount());
            if (itemstack.isEmpty()) {
                entityitem.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            }
        }

    }

    public ItemStack equipItemIfPossible(WorldServer worldserver, ItemStack itemstack) {
        // CraftBukkit start - add item
        return this.equipItemIfPossible(worldserver, itemstack, null);
    }

    public ItemStack equipItemIfPossible(WorldServer worldserver, ItemStack itemstack, EntityItem entityitem) {
        // CraftBukkit end
        EnumItemSlot enumitemslot = this.getEquipmentSlotForItem(itemstack);

        if (!this.isEquippableInSlot(itemstack, enumitemslot)) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack1 = this.getItemBySlot(enumitemslot);
            boolean flag = this.canReplaceCurrentItem(itemstack, itemstack1, enumitemslot);

            if (enumitemslot.isArmor() && !flag) {
                enumitemslot = EnumItemSlot.MAINHAND;
                itemstack1 = this.getItemBySlot(enumitemslot);
                flag = itemstack1.isEmpty();
            }

            // CraftBukkit start
            boolean canPickup = flag && this.canHoldItem(itemstack);
            if (entityitem != null) {
                canPickup = !org.bukkit.craftbukkit.event.CraftEventFactory.callEntityPickupItemEvent(this, entityitem, 0, !canPickup).isCancelled();
            }
            if (canPickup) {
                // CraftBukkit end
                double d0 = (double) this.dropChances.byEquipment(enumitemslot);

                if (!itemstack1.isEmpty() && (double) Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d0) {
                    this.forceDrops = true; // CraftBukkit
                    this.spawnAtLocation(worldserver, itemstack1);
                    this.forceDrops = false; // CraftBukkit
                }

                ItemStack itemstack2 = enumitemslot.limit(itemstack);

                this.setItemSlotAndDropWhenKilled(enumitemslot, itemstack2);
                return itemstack2;
            } else {
                return ItemStack.EMPTY;
            }
        }
    }

    protected void setItemSlotAndDropWhenKilled(EnumItemSlot enumitemslot, ItemStack itemstack) {
        this.setItemSlot(enumitemslot, itemstack);
        this.setGuaranteedDrop(enumitemslot);
        this.persistenceRequired = true;
    }

    public void setGuaranteedDrop(EnumItemSlot enumitemslot) {
        this.dropChances = this.dropChances.withGuaranteedDrop(enumitemslot);
    }

    protected boolean canReplaceCurrentItem(ItemStack itemstack, ItemStack itemstack1, EnumItemSlot enumitemslot) {
        return itemstack1.isEmpty() ? true : (enumitemslot.isArmor() ? this.compareArmor(itemstack, itemstack1, enumitemslot) : (enumitemslot == EnumItemSlot.MAINHAND ? this.compareWeapons(itemstack, itemstack1, enumitemslot) : false));
    }

    private boolean compareArmor(ItemStack itemstack, ItemStack itemstack1, EnumItemSlot enumitemslot) {
        if (EnchantmentManager.has(itemstack1, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return false;
        } else {
            double d0 = this.getApproximateAttributeWith(itemstack, GenericAttributes.ARMOR, enumitemslot);
            double d1 = this.getApproximateAttributeWith(itemstack1, GenericAttributes.ARMOR, enumitemslot);
            double d2 = this.getApproximateAttributeWith(itemstack, GenericAttributes.ARMOR_TOUGHNESS, enumitemslot);
            double d3 = this.getApproximateAttributeWith(itemstack1, GenericAttributes.ARMOR_TOUGHNESS, enumitemslot);

            return d0 != d1 ? d0 > d1 : (d2 != d3 ? d2 > d3 : this.canReplaceEqualItem(itemstack, itemstack1));
        }
    }

    private boolean compareWeapons(ItemStack itemstack, ItemStack itemstack1, EnumItemSlot enumitemslot) {
        TagKey<Item> tagkey = this.getPreferredWeaponType();

        if (tagkey != null) {
            if (itemstack1.is(tagkey) && !itemstack.is(tagkey)) {
                return false;
            }

            if (!itemstack1.is(tagkey) && itemstack.is(tagkey)) {
                return true;
            }
        }

        double d0 = this.getApproximateAttributeWith(itemstack, GenericAttributes.ATTACK_DAMAGE, enumitemslot);
        double d1 = this.getApproximateAttributeWith(itemstack1, GenericAttributes.ATTACK_DAMAGE, enumitemslot);

        return d0 != d1 ? d0 > d1 : this.canReplaceEqualItem(itemstack, itemstack1);
    }

    private double getApproximateAttributeWith(ItemStack itemstack, Holder<AttributeBase> holder, EnumItemSlot enumitemslot) {
        double d0 = this.getAttributes().hasAttribute(holder) ? this.getAttributeBaseValue(holder) : 0.0D;
        ItemAttributeModifiers itemattributemodifiers = (ItemAttributeModifiers) itemstack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        return itemattributemodifiers.compute(d0, enumitemslot);
    }

    public boolean canReplaceEqualItem(ItemStack itemstack, ItemStack itemstack1) {
        Set<Object2IntMap.Entry<Holder<Enchantment>>> set = ((ItemEnchantments) itemstack1.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)).entrySet();
        Set<Object2IntMap.Entry<Holder<Enchantment>>> set1 = ((ItemEnchantments) itemstack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)).entrySet();

        if (set1.size() != set.size()) {
            return set1.size() > set.size();
        } else {
            int i = itemstack.getDamageValue();
            int j = itemstack1.getDamageValue();

            return i != j ? i < j : itemstack.has(DataComponents.CUSTOM_NAME) && !itemstack1.has(DataComponents.CUSTOM_NAME);
        }
    }

    public boolean canHoldItem(ItemStack itemstack) {
        return true;
    }

    public boolean wantsToPickUp(WorldServer worldserver, ItemStack itemstack) {
        return this.canHoldItem(itemstack);
    }

    @Nullable
    public TagKey<Item> getPreferredWeaponType() {
        return null;
    }

    public boolean removeWhenFarAway(double d0) {
        return true;
    }

    public boolean requiresCustomPersistence() {
        return this.isPassenger();
    }

    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == EnumDifficulty.PEACEFUL && this.shouldDespawnInPeaceful()) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else if (!this.isPersistenceRequired() && !this.requiresCustomPersistence()) {
            Entity entity = this.level().getNearestPlayer(this, -1.0D);

            if (entity != null) {
                double d0 = entity.distanceToSqr((Entity) this);
                int i = this.getType().getCategory().getDespawnDistance();
                int j = i * i;

                if (d0 > (double) j && this.removeWhenFarAway(d0)) {
                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                }

                int k = this.getType().getCategory().getNoDespawnDistance();
                int l = k * k;

                if (this.noActionTime > 600 && this.random.nextInt(800) == 0 && d0 > (double) l && this.removeWhenFarAway(d0)) {
                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                } else if (d0 < (double) l) {
                    this.noActionTime = 0;
                }
            }

        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    protected final void serverAiStep() {
        ++this.noActionTime;
        if (!this.aware) return; // CraftBukkit
        GameProfilerFiller gameprofilerfiller = Profiler.get();

        gameprofilerfiller.push("sensing");
        this.sensing.tick();
        gameprofilerfiller.pop();
        int i = this.tickCount + this.getId();

        if (i % 2 != 0 && this.tickCount > 1) {
            gameprofilerfiller.push("targetSelector");
            this.targetSelector.tickRunningGoals(false);
            gameprofilerfiller.pop();
            gameprofilerfiller.push("goalSelector");
            this.goalSelector.tickRunningGoals(false);
            gameprofilerfiller.pop();
        } else {
            gameprofilerfiller.push("targetSelector");
            this.targetSelector.tick();
            gameprofilerfiller.pop();
            gameprofilerfiller.push("goalSelector");
            this.goalSelector.tick();
            gameprofilerfiller.pop();
        }

        gameprofilerfiller.push("navigation");
        this.navigation.tick();
        gameprofilerfiller.pop();
        gameprofilerfiller.push("mob tick");
        this.customServerAiStep((WorldServer) this.level());
        gameprofilerfiller.pop();
        gameprofilerfiller.push("controls");
        gameprofilerfiller.push("move");
        this.moveControl.tick();
        gameprofilerfiller.popPush("look");
        this.lookControl.tick();
        gameprofilerfiller.popPush("jump");
        this.jumpControl.tick();
        gameprofilerfiller.pop();
        gameprofilerfiller.pop();
        this.sendDebugPackets();
    }

    protected void sendDebugPackets() {
        PacketDebug.sendGoalSelector(this.level(), this, this.goalSelector);
    }

    protected void customServerAiStep(WorldServer worldserver) {}

    public int getMaxHeadXRot() {
        return 40;
    }

    public int getMaxHeadYRot() {
        return 75;
    }

    protected void clampHeadRotationToBody() {
        float f = (float) this.getMaxHeadYRot();
        float f1 = this.getYHeadRot();
        float f2 = MathHelper.wrapDegrees(this.yBodyRot - f1);
        float f3 = MathHelper.clamp(MathHelper.wrapDegrees(this.yBodyRot - f1), -f, f);
        float f4 = f1 + f2 - f3;

        this.setYHeadRot(f4);
    }

    public int getHeadRotSpeed() {
        return 10;
    }

    public void lookAt(Entity entity, float f, float f1) {
        double d0 = entity.getX() - this.getX();
        double d1 = entity.getZ() - this.getZ();
        double d2;

        if (entity instanceof EntityLiving entityliving) {
            d2 = entityliving.getEyeY() - this.getEyeY();
        } else {
            d2 = (entity.getBoundingBox().minY + entity.getBoundingBox().maxY) / 2.0D - this.getEyeY();
        }

        double d3 = Math.sqrt(d0 * d0 + d1 * d1);
        float f2 = (float) (MathHelper.atan2(d1, d0) * (double) (180F / (float) Math.PI)) - 90.0F;
        float f3 = (float) (-(MathHelper.atan2(d2, d3) * (double) (180F / (float) Math.PI)));

        this.setXRot(this.rotlerp(this.getXRot(), f3, f1));
        this.setYRot(this.rotlerp(this.getYRot(), f2, f));
    }

    private float rotlerp(float f, float f1, float f2) {
        float f3 = MathHelper.wrapDegrees(f1 - f);

        if (f3 > f2) {
            f3 = f2;
        }

        if (f3 < -f2) {
            f3 = -f2;
        }

        return f + f3;
    }

    public static boolean checkMobSpawnRules(EntityTypes<? extends EntityInsentient> entitytypes, GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason, BlockPosition blockposition, RandomSource randomsource) {
        BlockPosition blockposition1 = blockposition.below();

        return EntitySpawnReason.isSpawner(entityspawnreason) || generatoraccess.getBlockState(blockposition1).isValidSpawn(generatoraccess, blockposition1, entitytypes);
    }

    public boolean checkSpawnRules(GeneratorAccess generatoraccess, EntitySpawnReason entityspawnreason) {
        return true;
    }

    public boolean checkSpawnObstruction(IWorldReader iworldreader) {
        return !iworldreader.containsAnyLiquid(this.getBoundingBox()) && iworldreader.isUnobstructed(this);
    }

    public int getMaxSpawnClusterSize() {
        return 4;
    }

    public boolean isMaxGroupSizeReached(int i) {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        if (this.getTarget() == null) {
            return this.getComfortableFallDistance(0.0F);
        } else {
            int i = (int) (this.getHealth() - this.getMaxHealth() * 0.33F);

            i -= (3 - this.level().getDifficulty().getId()) * 4;
            if (i < 0) {
                i = 0;
            }

            return this.getComfortableFallDistance((float) i);
        }
    }

    public ItemStack getBodyArmorItem() {
        return this.getItemBySlot(EnumItemSlot.BODY);
    }

    public boolean isSaddled() {
        return this.hasItemInSlot(EnumItemSlot.SADDLE);
    }

    public boolean isWearingBodyArmor() {
        return this.hasItemInSlot(EnumItemSlot.BODY);
    }

    public void setBodyArmorItem(ItemStack itemstack) {
        this.setItemSlotAndDropWhenKilled(EnumItemSlot.BODY, itemstack);
    }

    public IInventory createEquipmentSlotContainer(final EnumItemSlot enumitemslot) {
        return new ContainerSingleItem() {
            @Override
            public ItemStack getTheItem() {
                return EntityInsentient.this.getItemBySlot(enumitemslot);
            }

            @Override
            public void setTheItem(ItemStack itemstack) {
                EntityInsentient.this.setItemSlot(enumitemslot, itemstack);
                if (!itemstack.isEmpty()) {
                    EntityInsentient.this.setGuaranteedDrop(enumitemslot);
                    EntityInsentient.this.setPersistenceRequired();
                }

            }

            @Override
            public void setChanged() {}

            @Override
            public boolean stillValid(EntityHuman entityhuman) {
                return entityhuman.getVehicle() == EntityInsentient.this || entityhuman.canInteractWithEntity((Entity) EntityInsentient.this, 4.0D);
            }

            // CraftBukkit start - add fields and methods
            public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();
            private int maxStack = MAX_STACK;

            @Override
            public List<ItemStack> getContents() {
                return Arrays.asList(this.getTheItem());
            }

            @Override
            public void onOpen(CraftHumanEntity who) {
                transaction.add(who);
            }

            @Override
            public void onClose(CraftHumanEntity who) {
                transaction.remove(who);
            }

            @Override
            public List<HumanEntity> getViewers() {
                return transaction;
            }

            @Override
            public int getMaxStackSize() {
                return maxStack;
            }

            @Override
            public void setMaxStackSize(int size) {
                maxStack = size;
            }

            @Override
            public InventoryHolder getOwner() {
                return (InventoryHolder) EntityInsentient.this.getBukkitEntity();
            }

            @Override
            public Location getLocation() {
                return EntityInsentient.this.getBukkitEntity().getLocation();
            }
            // CraftBukkit end
        };
    }

    @Override
    protected void dropCustomDeathLoot(WorldServer worldserver, DamageSource damagesource, boolean flag) {
        super.dropCustomDeathLoot(worldserver, damagesource, flag);

        for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
            ItemStack itemstack = this.getItemBySlot(enumitemslot);
            float f = this.dropChances.byEquipment(enumitemslot);

            if (f != 0.0F) {
                boolean flag1 = this.dropChances.isPreserved(enumitemslot);
                Entity entity = damagesource.getEntity();

                if (entity instanceof EntityLiving) {
                    EntityLiving entityliving = (EntityLiving) entity;
                    World world = this.level();

                    if (world instanceof WorldServer) {
                        WorldServer worldserver1 = (WorldServer) world;

                        f = EnchantmentManager.processEquipmentDropChance(worldserver1, entityliving, damagesource, f);
                    }
                }

                if (!itemstack.isEmpty() && !EnchantmentManager.has(itemstack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP) && (flag || flag1) && this.random.nextFloat() < f) {
                    if (!flag1 && itemstack.isDamageableItem()) {
                        itemstack.setDamageValue(itemstack.getMaxDamage() - this.random.nextInt(1 + this.random.nextInt(Math.max(itemstack.getMaxDamage() - 3, 1))));
                    }

                    this.spawnAtLocation(worldserver, itemstack);
                    this.setItemSlot(enumitemslot, ItemStack.EMPTY);
                }
            }
        }

    }

    public DropChances getDropChances() {
        return this.dropChances;
    }

    public void dropPreservedEquipment(WorldServer worldserver) {
        this.dropPreservedEquipment(worldserver, (itemstack) -> {
            return true;
        });
    }

    public Set<EnumItemSlot> dropPreservedEquipment(WorldServer worldserver, Predicate<ItemStack> predicate) {
        Set<EnumItemSlot> set = new HashSet();

        for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
            ItemStack itemstack = this.getItemBySlot(enumitemslot);

            if (!itemstack.isEmpty()) {
                if (!predicate.test(itemstack)) {
                    set.add(enumitemslot);
                } else if (this.dropChances.isPreserved(enumitemslot)) {
                    this.setItemSlot(enumitemslot, ItemStack.EMPTY);
                    this.spawnAtLocation(worldserver, itemstack);
                }
            }
        }

        return set;
    }

    private LootParams createEquipmentParams(WorldServer worldserver) {
        return (new LootParams.a(worldserver)).withParameter(LootContextParameters.ORIGIN, this.position()).withParameter(LootContextParameters.THIS_ENTITY, this).create(LootContextParameterSets.EQUIPMENT);
    }

    public void equip(EquipmentTable equipmenttable) {
        this.equip(equipmenttable.lootTable(), equipmenttable.slotDropChances());
    }

    public void equip(ResourceKey<LootTable> resourcekey, Map<EnumItemSlot, Float> map) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            this.equip(resourcekey, this.createEquipmentParams(worldserver), map);
        }

    }

    protected void populateDefaultEquipmentSlots(RandomSource randomsource, DifficultyDamageScaler difficultydamagescaler) {
        if (randomsource.nextFloat() < 0.15F * difficultydamagescaler.getSpecialMultiplier()) {
            int i = randomsource.nextInt(2);
            float f = this.level().getDifficulty() == EnumDifficulty.HARD ? 0.1F : 0.25F;

            if (randomsource.nextFloat() < 0.095F) {
                ++i;
            }

            if (randomsource.nextFloat() < 0.095F) {
                ++i;
            }

            if (randomsource.nextFloat() < 0.095F) {
                ++i;
            }

            boolean flag = true;

            for (EnumItemSlot enumitemslot : EntityInsentient.EQUIPMENT_POPULATION_ORDER) {
                ItemStack itemstack = this.getItemBySlot(enumitemslot);

                if (!flag && randomsource.nextFloat() < f) {
                    break;
                }

                flag = false;
                if (itemstack.isEmpty()) {
                    Item item = getEquipmentForSlot(enumitemslot, i);

                    if (item != null) {
                        this.setItemSlot(enumitemslot, new ItemStack(item));
                    }
                }
            }
        }

    }

    @Nullable
    public static Item getEquipmentForSlot(EnumItemSlot enumitemslot, int i) {
        switch (enumitemslot) {
            case HEAD:
                if (i == 0) {
                    return Items.LEATHER_HELMET;
                } else if (i == 1) {
                    return Items.GOLDEN_HELMET;
                } else if (i == 2) {
                    return Items.CHAINMAIL_HELMET;
                } else if (i == 3) {
                    return Items.IRON_HELMET;
                } else if (i == 4) {
                    return Items.DIAMOND_HELMET;
                }
            case CHEST:
                if (i == 0) {
                    return Items.LEATHER_CHESTPLATE;
                } else if (i == 1) {
                    return Items.GOLDEN_CHESTPLATE;
                } else if (i == 2) {
                    return Items.CHAINMAIL_CHESTPLATE;
                } else if (i == 3) {
                    return Items.IRON_CHESTPLATE;
                } else if (i == 4) {
                    return Items.DIAMOND_CHESTPLATE;
                }
            case LEGS:
                if (i == 0) {
                    return Items.LEATHER_LEGGINGS;
                } else if (i == 1) {
                    return Items.GOLDEN_LEGGINGS;
                } else if (i == 2) {
                    return Items.CHAINMAIL_LEGGINGS;
                } else if (i == 3) {
                    return Items.IRON_LEGGINGS;
                } else if (i == 4) {
                    return Items.DIAMOND_LEGGINGS;
                }
            case FEET:
                if (i == 0) {
                    return Items.LEATHER_BOOTS;
                } else if (i == 1) {
                    return Items.GOLDEN_BOOTS;
                } else if (i == 2) {
                    return Items.CHAINMAIL_BOOTS;
                } else if (i == 3) {
                    return Items.IRON_BOOTS;
                } else if (i == 4) {
                    return Items.DIAMOND_BOOTS;
                }
            default:
                return null;
        }
    }

    protected void populateDefaultEquipmentEnchantments(WorldAccess worldaccess, RandomSource randomsource, DifficultyDamageScaler difficultydamagescaler) {
        this.enchantSpawnedWeapon(worldaccess, randomsource, difficultydamagescaler);

        for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
            if (enumitemslot.getType() == EnumItemSlot.Function.HUMANOID_ARMOR) {
                this.enchantSpawnedArmor(worldaccess, randomsource, enumitemslot, difficultydamagescaler);
            }
        }

    }

    protected void enchantSpawnedWeapon(WorldAccess worldaccess, RandomSource randomsource, DifficultyDamageScaler difficultydamagescaler) {
        this.enchantSpawnedEquipment(worldaccess, EnumItemSlot.MAINHAND, randomsource, 0.25F, difficultydamagescaler);
    }

    protected void enchantSpawnedArmor(WorldAccess worldaccess, RandomSource randomsource, EnumItemSlot enumitemslot, DifficultyDamageScaler difficultydamagescaler) {
        this.enchantSpawnedEquipment(worldaccess, enumitemslot, randomsource, 0.5F, difficultydamagescaler);
    }

    private void enchantSpawnedEquipment(WorldAccess worldaccess, EnumItemSlot enumitemslot, RandomSource randomsource, float f, DifficultyDamageScaler difficultydamagescaler) {
        ItemStack itemstack = this.getItemBySlot(enumitemslot);

        if (!itemstack.isEmpty() && randomsource.nextFloat() < f * difficultydamagescaler.getSpecialMultiplier()) {
            EnchantmentManager.enchantItemFromProvider(itemstack, worldaccess.registryAccess(), VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficultydamagescaler, randomsource);
            this.setItemSlot(enumitemslot, itemstack);
        }

    }

    @Nullable
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        RandomSource randomsource = worldaccess.getRandom();
        AttributeModifiable attributemodifiable = (AttributeModifiable) Objects.requireNonNull(this.getAttribute(GenericAttributes.FOLLOW_RANGE));

        if (!attributemodifiable.hasModifier(EntityInsentient.RANDOM_SPAWN_BONUS_ID)) {
            attributemodifiable.addPermanentModifier(new AttributeModifier(EntityInsentient.RANDOM_SPAWN_BONUS_ID, randomsource.triangle(0.0D, 0.11485000000000001D), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }

        this.setLeftHanded(randomsource.nextFloat() < 0.05F);
        return groupdataentity;
    }

    public void setPersistenceRequired() {
        this.persistenceRequired = true;
    }

    @Override
    public void setDropChance(EnumItemSlot enumitemslot, float f) {
        this.dropChances = this.dropChances.withEquipmentChance(enumitemslot, f);
    }

    @Override
    public boolean canPickUpLoot() {
        return this.canPickUpLoot;
    }

    public void setCanPickUpLoot(boolean flag) {
        this.canPickUpLoot = flag;
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EnumItemSlot enumitemslot) {
        return this.canPickUpLoot();
    }

    public boolean isPersistenceRequired() {
        return this.persistenceRequired;
    }

    @Override
    public final EnumInteractionResult interact(EntityHuman entityhuman, EnumHand enumhand) {
        if (!this.isAlive()) {
            return EnumInteractionResult.PASS;
        } else {
            EnumInteractionResult enuminteractionresult = this.checkAndHandleImportantInteractions(entityhuman, enumhand);

            if (enuminteractionresult.consumesAction()) {
                this.gameEvent(GameEvent.ENTITY_INTERACT, entityhuman);
                return enuminteractionresult;
            } else {
                EnumInteractionResult enuminteractionresult1 = super.interact(entityhuman, enumhand);

                if (enuminteractionresult1 != EnumInteractionResult.PASS) {
                    return enuminteractionresult1;
                } else {
                    enuminteractionresult = this.mobInteract(entityhuman, enumhand);
                    if (enuminteractionresult.consumesAction()) {
                        this.gameEvent(GameEvent.ENTITY_INTERACT, entityhuman);
                        return enuminteractionresult;
                    } else {
                        return EnumInteractionResult.PASS;
                    }
                }
            }
        }
    }

    private EnumInteractionResult checkAndHandleImportantInteractions(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.is(Items.NAME_TAG)) {
            EnumInteractionResult enuminteractionresult = itemstack.interactLivingEntity(entityhuman, this, enumhand);

            if (enuminteractionresult.consumesAction()) {
                return enuminteractionresult;
            }
        }

        if (itemstack.getItem() instanceof ItemMonsterEgg) {
            if (this.level() instanceof WorldServer) {
                ItemMonsterEgg itemmonsteregg = (ItemMonsterEgg) itemstack.getItem();
                Optional<EntityInsentient> optional = itemmonsteregg.spawnOffspringFromSpawnEgg(entityhuman, this, (EntityTypes<? extends EntityInsentient>) this.getType(), (WorldServer) this.level(), this.position(), itemstack); // CraftBukkit - decompile error

                optional.ifPresent((entityinsentient) -> {
                    this.onOffspringSpawnedFromEgg(entityhuman, entityinsentient);
                });
                if (optional.isEmpty()) {
                    return EnumInteractionResult.PASS;
                }
            }

            return EnumInteractionResult.SUCCESS_SERVER;
        } else {
            return EnumInteractionResult.PASS;
        }
    }

    protected void onOffspringSpawnedFromEgg(EntityHuman entityhuman, EntityInsentient entityinsentient) {}

    protected EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        return EnumInteractionResult.PASS;
    }

    public boolean isWithinRestriction() {
        return this.isWithinRestriction(this.blockPosition());
    }

    public boolean isWithinRestriction(BlockPosition blockposition) {
        return this.restrictRadius == -1.0F ? true : this.restrictCenter.distSqr(blockposition) < (double) (this.restrictRadius * this.restrictRadius);
    }

    public void restrictTo(BlockPosition blockposition, int i) {
        this.restrictCenter = blockposition;
        this.restrictRadius = (float) i;
    }

    public BlockPosition getRestrictCenter() {
        return this.restrictCenter;
    }

    public float getRestrictRadius() {
        return this.restrictRadius;
    }

    public void clearRestriction() {
        this.restrictRadius = -1.0F;
    }

    public boolean hasRestriction() {
        return this.restrictRadius != -1.0F;
    }

    @Nullable
    public <T extends EntityInsentient> T convertTo(EntityTypes<T> entitytypes, ConversionParams conversionparams, EntitySpawnReason entityspawnreason, ConversionParams.a<T> conversionparams_a) {
        // CraftBukkit start
        return this.convertTo(entitytypes, conversionparams, entityspawnreason, conversionparams_a, EntityTransformEvent.TransformReason.UNKNOWN, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Nullable
    public <T extends EntityInsentient> T convertTo(EntityTypes<T> entitytypes, ConversionParams conversionparams, EntitySpawnReason entityspawnreason, ConversionParams.a<T> conversionparams_a, EntityTransformEvent.TransformReason transformReason, CreatureSpawnEvent.SpawnReason spawnReason) {
        // CraftBukkit end
        if (this.isRemoved()) {
            return null;
        } else {
            T t0 = entitytypes.create(this.level(), entityspawnreason);

            if (t0 == null) {
                return null;
            } else {
                conversionparams.type().convert(this, t0, conversionparams);
                conversionparams_a.finalizeConversion(t0);
                World world = this.level();

                // CraftBukkit start
                if (transformReason == null) {
                    // Special handling for slime split and pig lightning
                    return t0;
                }

                if (CraftEventFactory.callEntityTransformEvent(this, t0, transformReason).isCancelled()) {
                    return null;
                }

                conversionparams.type().postConvert(this, t0, conversionparams);
                // CraftBukkit end
                if (world instanceof WorldServer) {
                    WorldServer worldserver = (WorldServer) world;

                    worldserver.addFreshEntity(t0, spawnReason); // CraftBukkit
                }

                if (conversionparams.type().shouldDiscardAfterConversion()) {
                    this.discard(EntityRemoveEvent.Cause.TRANSFORMATION); // CraftBukkit - add Bukkit remove cause
                }

                return t0;
            }
        }
    }

    @Nullable
    public <T extends EntityInsentient> T convertTo(EntityTypes<T> entitytypes, ConversionParams conversionparams, ConversionParams.a<T> conversionparams_a) {
        // CraftBukkit start
        return (T) this.convertTo(entitytypes, conversionparams, conversionparams_a, EntityTransformEvent.TransformReason.UNKNOWN, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Nullable
    public <T extends EntityInsentient> T convertTo(EntityTypes<T> entitytypes, ConversionParams conversionparams, ConversionParams.a<T> conversionparams_a, EntityTransformEvent.TransformReason transformReason, CreatureSpawnEvent.SpawnReason spawnReason) {
        return (T) this.convertTo(entitytypes, conversionparams, EntitySpawnReason.CONVERSION, conversionparams_a, transformReason, spawnReason);
        // CraftBukkit end
    }

    @Nullable
    @Override
    public Leashable.a getLeashData() {
        return this.leashData;
    }

    @Override
    public void setLeashData(@Nullable Leashable.a leashable_a) {
        this.leashData = leashable_a;
    }

    @Override
    public void onLeashRemoved() {
        if (this.getLeashData() == null) {
            this.clearRestriction();
        }

    }

    @Override
    public void leashTooFarBehaviour() {
        Leashable.super.leashTooFarBehaviour();
        this.goalSelector.disableControlFlag(PathfinderGoal.Type.MOVE);
    }

    @Override
    public boolean canBeLeashed() {
        return !(this instanceof IMonster);
    }

    @Override
    public boolean startRiding(Entity entity, boolean flag) {
        boolean flag1 = super.startRiding(entity, flag);

        if (flag1 && this.isLeashed()) {
            this.level().getCraftServer().getPluginManager().callEvent(new EntityUnleashEvent(this.getBukkitEntity(), UnleashReason.UNKNOWN)); // CraftBukkit
            this.dropLeash();
        }

        return flag1;
    }

    @Override
    public boolean canSimulateMovement() {
        return super.canSimulateMovement() && !this.isNoAi();
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && !this.isNoAi();
    }

    public void setNoAi(boolean flag) {
        byte b0 = (Byte) this.entityData.get(EntityInsentient.DATA_MOB_FLAGS_ID);

        this.entityData.set(EntityInsentient.DATA_MOB_FLAGS_ID, flag ? (byte) (b0 | 1) : (byte) (b0 & -2));
    }

    public void setLeftHanded(boolean flag) {
        byte b0 = (Byte) this.entityData.get(EntityInsentient.DATA_MOB_FLAGS_ID);

        this.entityData.set(EntityInsentient.DATA_MOB_FLAGS_ID, flag ? (byte) (b0 | 2) : (byte) (b0 & -3));
    }

    public void setAggressive(boolean flag) {
        byte b0 = (Byte) this.entityData.get(EntityInsentient.DATA_MOB_FLAGS_ID);

        this.entityData.set(EntityInsentient.DATA_MOB_FLAGS_ID, flag ? (byte) (b0 | 4) : (byte) (b0 & -5));
    }

    public boolean isNoAi() {
        return ((Byte) this.entityData.get(EntityInsentient.DATA_MOB_FLAGS_ID) & 1) != 0;
    }

    public boolean isLeftHanded() {
        return ((Byte) this.entityData.get(EntityInsentient.DATA_MOB_FLAGS_ID) & 2) != 0;
    }

    public boolean isAggressive() {
        return ((Byte) this.entityData.get(EntityInsentient.DATA_MOB_FLAGS_ID) & 4) != 0;
    }

    public void setBaby(boolean flag) {}

    @Override
    public EnumMainHand getMainArm() {
        return this.isLeftHanded() ? EnumMainHand.LEFT : EnumMainHand.RIGHT;
    }

    public boolean isWithinMeleeAttackRange(EntityLiving entityliving) {
        return this.getAttackBoundingBox().intersects(entityliving.getHitbox());
    }

    protected AxisAlignedBB getAttackBoundingBox() {
        Entity entity = this.getVehicle();
        AxisAlignedBB axisalignedbb;

        if (entity != null) {
            AxisAlignedBB axisalignedbb1 = entity.getBoundingBox();
            AxisAlignedBB axisalignedbb2 = this.getBoundingBox();

            axisalignedbb = new AxisAlignedBB(Math.min(axisalignedbb2.minX, axisalignedbb1.minX), axisalignedbb2.minY, Math.min(axisalignedbb2.minZ, axisalignedbb1.minZ), Math.max(axisalignedbb2.maxX, axisalignedbb1.maxX), axisalignedbb2.maxY, Math.max(axisalignedbb2.maxZ, axisalignedbb1.maxZ));
        } else {
            axisalignedbb = this.getBoundingBox();
        }

        return axisalignedbb.inflate(EntityInsentient.DEFAULT_ATTACK_REACH, 0.0D, EntityInsentient.DEFAULT_ATTACK_REACH);
    }

    @Override
    public boolean doHurtTarget(WorldServer worldserver, Entity entity) {
        float f = (float) this.getAttributeValue(GenericAttributes.ATTACK_DAMAGE);
        ItemStack itemstack = this.getWeaponItem();
        DamageSource damagesource = (DamageSource) Optional.ofNullable(itemstack.getItem().getDamageSource(this)).orElse(this.damageSources().mobAttack(this));

        f = EnchantmentManager.modifyDamage(worldserver, itemstack, entity, damagesource, f);
        f += itemstack.getItem().getAttackDamageBonus(entity, f, damagesource);
        boolean flag = entity.hurtServer(worldserver, damagesource, f);

        if (flag) {
            float f1 = this.getKnockback(entity, damagesource);

            if (f1 > 0.0F && entity instanceof EntityLiving) {
                EntityLiving entityliving = (EntityLiving) entity;

                entityliving.knockback((double) (f1 * 0.5F), (double) MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F)), (double) (-MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F))), this, org.bukkit.event.entity.EntityKnockbackEvent.KnockbackCause.ENTITY_ATTACK); // CraftBukkit
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
            }

            if (entity instanceof EntityLiving) {
                EntityLiving entityliving1 = (EntityLiving) entity;

                itemstack.hurtEnemy(entityliving1, this);
            }

            EnchantmentManager.doPostAttackEffects(worldserver, entity, damagesource);
            this.setLastHurtMob(entity);
            this.playAttackSound();
        }

        return flag;
    }

    protected void playAttackSound() {}

    protected boolean isSunBurnTick() {
        if (this.level().isBrightOutside() && !this.level().isClientSide) {
            float f = this.getLightLevelDependentMagicValue();
            BlockPosition blockposition = BlockPosition.containing(this.getX(), this.getEyeY(), this.getZ());
            boolean flag = this.isInWaterOrRain() || this.isInPowderSnow || this.wasInPowderSnow;

            if (f > 0.5F && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F && !flag && this.level().canSeeSky(blockposition)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void jumpInLiquid(TagKey<FluidType> tagkey) {
        if (this.getNavigation().canFloat()) {
            super.jumpInLiquid(tagkey);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, 0.3D, 0.0D));
        }

    }

    @VisibleForTesting
    public void removeFreeWill() {
        this.removeAllGoals((pathfindergoal) -> {
            return true;
        });
        this.getBrain().removeAllBehaviors();
    }

    public void removeAllGoals(Predicate<PathfinderGoal> predicate) {
        this.goalSelector.removeAllGoals(predicate);
    }

    @Override
    protected void removeAfterChangingDimensions() {
        super.removeAfterChangingDimensions();

        for (EnumItemSlot enumitemslot : EnumItemSlot.VALUES) {
            ItemStack itemstack = this.getItemBySlot(enumitemslot);

            if (!itemstack.isEmpty()) {
                itemstack.setCount(0);
            }
        }

    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        ItemMonsterEgg itemmonsteregg = ItemMonsterEgg.byId(this.getType());

        return itemmonsteregg == null ? null : new ItemStack(itemmonsteregg);
    }

    @Override
    protected void onAttributeUpdated(Holder<AttributeBase> holder) {
        super.onAttributeUpdated(holder);
        if (holder.is(GenericAttributes.FOLLOW_RANGE) || holder.is(GenericAttributes.TEMPT_RANGE)) {
            this.getNavigation().updatePathfinderMaxVisitedNodes();
        }

    }
}

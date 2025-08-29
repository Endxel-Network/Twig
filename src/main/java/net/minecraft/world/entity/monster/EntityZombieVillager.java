package net.minecraft.world.entity.monster;

import com.google.common.annotations.VisibleForTesting;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.world.DifficultyDamageScaler;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.gossip.Reputation;
import net.minecraft.world.entity.ai.village.ReputationEvent;
import net.minecraft.world.entity.npc.EntityVillager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.trading.MerchantRecipeList;
import net.minecraft.world.level.World;
import net.minecraft.world.level.WorldAccess;
import net.minecraft.world.level.block.BlockBed;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;

// CraftBukkit start
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.EntityVillager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
// CraftBukkit end

public class EntityZombieVillager extends EntityZombie implements VillagerDataHolder {

    public static final DataWatcherObject<Boolean> DATA_CONVERTING_ID = DataWatcher.<Boolean>defineId(EntityZombieVillager.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<VillagerData> DATA_VILLAGER_DATA = DataWatcher.<VillagerData>defineId(EntityZombieVillager.class, DataWatcherRegistry.VILLAGER_DATA);
    private static final int VILLAGER_CONVERSION_WAIT_MIN = 3600;
    private static final int VILLAGER_CONVERSION_WAIT_MAX = 6000;
    private static final int MAX_SPECIAL_BLOCKS_COUNT = 14;
    private static final int SPECIAL_BLOCK_RADIUS = 4;
    private static final int NOT_CONVERTING = -1;
    private static final int DEFAULT_XP = 0;
    public int villagerConversionTime;
    @Nullable
    public UUID conversionStarter;
    @Nullable
    private Reputation gossips;
    @Nullable
    private MerchantRecipeList tradeOffers;
    private int villagerXp = 0;
    private int lastTick = MinecraftServer.currentTick; // CraftBukkit - add field

    public EntityZombieVillager(EntityTypes<? extends EntityZombieVillager> entitytypes, World world) {
        super(entitytypes, world);
        BuiltInRegistries.VILLAGER_PROFESSION.getRandom(this.random).ifPresent((holder_c) -> {
            this.setVillagerData(this.getVillagerData().withProfession(holder_c));
        });
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityZombieVillager.DATA_CONVERTING_ID, false);
        datawatcher_a.define(EntityZombieVillager.DATA_VILLAGER_DATA, EntityVillager.createDefaultVillagerData());
    }

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.store("VillagerData", VillagerData.CODEC, this.getVillagerData());
        nbttagcompound.storeNullable("Offers", MerchantRecipeList.CODEC, this.registryAccess().createSerializationContext(DynamicOpsNBT.INSTANCE), this.tradeOffers);
        nbttagcompound.storeNullable("Gossips", Reputation.CODEC, this.gossips);
        nbttagcompound.putInt("ConversionTime", this.isConverting() ? this.villagerConversionTime : -1);
        nbttagcompound.storeNullable("ConversionPlayer", UUIDUtil.CODEC, this.conversionStarter);
        nbttagcompound.putInt("Xp", this.villagerXp);
    }

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.entityData.set(EntityZombieVillager.DATA_VILLAGER_DATA, (VillagerData) nbttagcompound.read("VillagerData", VillagerData.CODEC).orElseGet(EntityVillager::createDefaultVillagerData));
        this.tradeOffers = (MerchantRecipeList) nbttagcompound.read("Offers", MerchantRecipeList.CODEC, this.registryAccess().createSerializationContext(DynamicOpsNBT.INSTANCE)).orElse(null); // CraftBukkit - decompile error
        this.gossips = (Reputation) nbttagcompound.read("Gossips", Reputation.CODEC).orElse(null); // CraftBukkit - decompile error
        int i = nbttagcompound.getIntOr("ConversionTime", -1);

        if (i != -1) {
            UUID uuid = (UUID) nbttagcompound.read("ConversionPlayer", UUIDUtil.CODEC).orElse(null); // CraftBukkit - decompile error

            this.startConverting(uuid, i);
        } else {
            this.getEntityData().set(EntityZombieVillager.DATA_CONVERTING_ID, false);
            this.villagerConversionTime = -1;
        }

        this.villagerXp = nbttagcompound.getIntOr("Xp", 0);
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && this.isConverting()) {
            int i = this.getConversionProgress();
            // CraftBukkit start - Use wall time instead of ticks for villager conversion
            int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
            i *= elapsedTicks;
            // CraftBukkit end

            this.villagerConversionTime -= i;
            if (this.villagerConversionTime <= 0) {
                this.finishConversion((WorldServer) this.level());
            }
        }

        super.tick();
        this.lastTick = MinecraftServer.currentTick; // CraftBukkit
    }

    @Override
    public EnumInteractionResult mobInteract(EntityHuman entityhuman, EnumHand enumhand) {
        ItemStack itemstack = entityhuman.getItemInHand(enumhand);

        if (itemstack.is(Items.GOLDEN_APPLE)) {
            if (this.hasEffect(MobEffects.WEAKNESS)) {
                itemstack.consume(1, entityhuman);
                if (!this.level().isClientSide) {
                    this.startConverting(entityhuman.getUUID(), this.random.nextInt(2401) + 3600);
                }

                return EnumInteractionResult.SUCCESS_SERVER;
            } else {
                return EnumInteractionResult.CONSUME;
            }
        } else {
            return super.mobInteract(entityhuman, enumhand);
        }
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d0) {
        return !this.isConverting() && this.villagerXp == 0;
    }

    public boolean isConverting() {
        return (Boolean) this.getEntityData().get(EntityZombieVillager.DATA_CONVERTING_ID);
    }

    public void startConverting(@Nullable UUID uuid, int i) {
        this.conversionStarter = uuid;
        this.villagerConversionTime = i;
        this.getEntityData().set(EntityZombieVillager.DATA_CONVERTING_ID, true);
        // CraftBukkit start
        this.removeEffect(MobEffects.WEAKNESS, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION);
        this.addEffect(new MobEffect(MobEffects.STRENGTH, i, Math.min(this.level().getDifficulty().getId() - 1, 0)), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION);
        // CraftBukkit end
        this.level().broadcastEntityEvent(this, (byte) 16);
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 16) {
            if (!this.isSilent()) {
                this.level().playLocalSound(this.getX(), this.getEyeY(), this.getZ(), SoundEffects.ZOMBIE_VILLAGER_CURE, this.getSoundSource(), 1.0F + this.random.nextFloat(), this.random.nextFloat() * 0.7F + 0.3F, false);
            }

        } else {
            super.handleEntityEvent(b0);
        }
    }

    private void finishConversion(WorldServer worldserver) {
        EntityVillager converted = this.convertTo(EntityTypes.VILLAGER, ConversionParams.single(this, false, false), (entityvillager) -> { // CraftBukkit
            this.forceDrops = false; // CraftBukkit
            for (EnumItemSlot enumitemslot : this.dropPreservedEquipment(worldserver, (itemstack) -> {
                return !EnchantmentManager.has(itemstack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
            })) {
                SlotAccess slotaccess = entityvillager.getSlot(enumitemslot.getIndex() + 300);

                slotaccess.set(this.getItemBySlot(enumitemslot));
            }

            entityvillager.setVillagerData(this.getVillagerData());
            if (this.gossips != null) {
                entityvillager.setGossips(this.gossips);
            }

            if (this.tradeOffers != null) {
                entityvillager.setOffers(this.tradeOffers.copy());
            }

            entityvillager.setVillagerXp(this.villagerXp);
            entityvillager.finalizeSpawn(worldserver, worldserver.getCurrentDifficultyAt(entityvillager.blockPosition()), EntitySpawnReason.CONVERSION, (GroupDataEntity) null);
            entityvillager.refreshBrain(worldserver);
            if (this.conversionStarter != null) {
                EntityHuman entityhuman = worldserver.getPlayerByUUID(this.conversionStarter);

                if (entityhuman instanceof EntityPlayer) {
                    CriterionTriggers.CURED_ZOMBIE_VILLAGER.trigger((EntityPlayer) entityhuman, this, entityvillager);
                    worldserver.onReputationEvent(ReputationEvent.ZOMBIE_VILLAGER_CURED, entityhuman, entityvillager);
                }
            }

            entityvillager.addEffect(new MobEffect(MobEffects.NAUSEA, 200, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION); // CraftBukkit
            if (!this.isSilent()) {
                worldserver.levelEvent((Entity) null, 1027, this.blockPosition(), 0);
            }
        // CraftBukkit start
        }, EntityTransformEvent.TransformReason.CURED, CreatureSpawnEvent.SpawnReason.CURED);
        if (converted == null) {
            ((ZombieVillager) getBukkitEntity()).setConversionTime(-1); // SPIGOT-5208: End conversion to stop event spam
        }
        // CraftBukkit end
    }

    @VisibleForTesting
    public void setVillagerConversionTime(int i) {
        this.villagerConversionTime = i;
    }

    private int getConversionProgress() {
        int i = 1;

        if (this.random.nextFloat() < 0.01F) {
            int j = 0;
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();

            for (int k = (int) this.getX() - 4; k < (int) this.getX() + 4 && j < 14; ++k) {
                for (int l = (int) this.getY() - 4; l < (int) this.getY() + 4 && j < 14; ++l) {
                    for (int i1 = (int) this.getZ() - 4; i1 < (int) this.getZ() + 4 && j < 14; ++i1) {
                        IBlockData iblockdata = this.level().getBlockState(blockposition_mutableblockposition.set(k, l, i1));

                        if (iblockdata.is(Blocks.IRON_BARS) || iblockdata.getBlock() instanceof BlockBed) {
                            if (this.random.nextFloat() < 0.3F) {
                                ++i;
                            }

                            ++j;
                        }
                    }
                }
            }
        }

        return i;
    }

    @Override
    public float getVoicePitch() {
        return this.isBaby() ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 2.0F : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    public SoundEffect getAmbientSound() {
        return SoundEffects.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    public SoundEffect getHurtSound(DamageSource damagesource) {
        return SoundEffects.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    public SoundEffect getDeathSound() {
        return SoundEffects.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    public SoundEffect getStepSound() {
        return SoundEffects.ZOMBIE_VILLAGER_STEP;
    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }

    public void setTradeOffers(MerchantRecipeList merchantrecipelist) {
        this.tradeOffers = merchantrecipelist;
    }

    public void setGossips(Reputation reputation) {
        this.gossips = reputation;
    }

    @Nullable
    @Override
    public GroupDataEntity finalizeSpawn(WorldAccess worldaccess, DifficultyDamageScaler difficultydamagescaler, EntitySpawnReason entityspawnreason, @Nullable GroupDataEntity groupdataentity) {
        this.setVillagerData(this.getVillagerData().withType(worldaccess.registryAccess(), VillagerType.byBiome(worldaccess.getBiome(this.blockPosition()))));
        return super.finalizeSpawn(worldaccess, difficultydamagescaler, entityspawnreason, groupdataentity);
    }

    @Override
    public void setVillagerData(VillagerData villagerdata) {
        VillagerData villagerdata1 = this.getVillagerData();

        if (!villagerdata1.profession().equals(villagerdata.profession())) {
            this.tradeOffers = null;
        }

        this.entityData.set(EntityZombieVillager.DATA_VILLAGER_DATA, villagerdata);
    }

    @Override
    public VillagerData getVillagerData() {
        return (VillagerData) this.entityData.get(EntityZombieVillager.DATA_VILLAGER_DATA);
    }

    public int getVillagerXp() {
        return this.villagerXp;
    }

    public void setVillagerXp(int i) {
        this.villagerXp = i;
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.VILLAGER_VARIANT ? castComponentValue(datacomponenttype, this.getVillagerData().type()) : super.get(datacomponenttype));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.VILLAGER_VARIANT);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.VILLAGER_VARIANT) {
            Holder<VillagerType> holder = (Holder) castComponentValue(DataComponents.VILLAGER_VARIANT, t0);

            this.setVillagerData(this.getVillagerData().withType(holder));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }
}

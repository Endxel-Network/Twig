package net.minecraft.world.entity.projectile;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketListenerPlayOut;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.EntityTrackerEntry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.stats.StatisticList;
import net.minecraft.tags.TagsFluid;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityExperienceOrb;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameterSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParameters;
import net.minecraft.world.phys.MovingObjectPosition;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.MovingObjectPositionEntity;
import net.minecraft.world.phys.Vec3D;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.entity.Player;
import org.bukkit.entity.FishHook;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerFishEvent;
// CraftBukkit end

public class EntityFishingHook extends IProjectile {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final RandomSource syncronizedRandom;
    private boolean biting;
    private int outOfWaterTime;
    private static final int MAX_OUT_OF_WATER_TIME = 10;
    public static final DataWatcherObject<Integer> DATA_HOOKED_ENTITY = DataWatcher.<Integer>defineId(EntityFishingHook.class, DataWatcherRegistry.INT);
    private static final DataWatcherObject<Boolean> DATA_BITING = DataWatcher.<Boolean>defineId(EntityFishingHook.class, DataWatcherRegistry.BOOLEAN);
    private int life;
    private int nibble;
    public int timeUntilLured;
    public int timeUntilHooked;
    private float fishAngle;
    private boolean openWater;
    @Nullable
    public Entity hookedIn;
    public EntityFishingHook.HookState currentState;
    private final int luck;
    private final int lureSpeed;

    // CraftBukkit start - Extra variables to enable modification of fishing wait time, values are minecraft defaults
    public int minWaitTime = 100;
    public int maxWaitTime = 600;
    public int minLureTime = 20;
    public int maxLureTime = 80;
    public float minLureAngle = 0.0F;
    public float maxLureAngle = 360.0F;
    public boolean applyLure = true;
    public boolean rainInfluenced = true;
    public boolean skyInfluenced = true;
    // CraftBukkit end

    private EntityFishingHook(EntityTypes<? extends EntityFishingHook> entitytypes, World world, int i, int j) {
        super(entitytypes, world);
        this.syncronizedRandom = RandomSource.create();
        this.openWater = true;
        this.currentState = EntityFishingHook.HookState.FLYING;
        this.luck = Math.max(0, i);
        this.lureSpeed = Math.max(0, j);
    }

    public EntityFishingHook(EntityTypes<? extends EntityFishingHook> entitytypes, World world) {
        this(entitytypes, world, 0, 0);
    }

    public EntityFishingHook(EntityHuman entityhuman, World world, int i, int j) {
        this(EntityTypes.FISHING_BOBBER, world, i, j);
        this.setOwner(entityhuman);
        float f = entityhuman.getXRot();
        float f1 = entityhuman.getYRot();
        float f2 = MathHelper.cos(-f1 * ((float) Math.PI / 180F) - (float) Math.PI);
        float f3 = MathHelper.sin(-f1 * ((float) Math.PI / 180F) - (float) Math.PI);
        float f4 = -MathHelper.cos(-f * ((float) Math.PI / 180F));
        float f5 = MathHelper.sin(-f * ((float) Math.PI / 180F));
        double d0 = entityhuman.getX() - (double) f3 * 0.3D;
        double d1 = entityhuman.getEyeY();
        double d2 = entityhuman.getZ() - (double) f2 * 0.3D;

        this.snapTo(d0, d1, d2, f1, f);
        Vec3D vec3d = new Vec3D((double) (-f3), (double) MathHelper.clamp(-(f5 / f4), -5.0F, 5.0F), (double) (-f2));
        double d3 = vec3d.length();

        vec3d = vec3d.multiply(0.6D / d3 + this.random.triangle(0.5D, 0.0103365D), 0.6D / d3 + this.random.triangle(0.5D, 0.0103365D), 0.6D / d3 + this.random.triangle(0.5D, 0.0103365D));
        this.setDeltaMovement(vec3d);
        this.setYRot((float) (MathHelper.atan2(vec3d.x, vec3d.z) * (double) (180F / (float) Math.PI)));
        this.setXRot((float) (MathHelper.atan2(vec3d.y, vec3d.horizontalDistance()) * (double) (180F / (float) Math.PI)));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        datawatcher_a.define(EntityFishingHook.DATA_HOOKED_ENTITY, 0);
        datawatcher_a.define(EntityFishingHook.DATA_BITING, false);
    }

    @Override
    protected boolean shouldBounceOnWorldBorder() {
        return true;
    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (EntityFishingHook.DATA_HOOKED_ENTITY.equals(datawatcherobject)) {
            int i = (Integer) this.getEntityData().get(EntityFishingHook.DATA_HOOKED_ENTITY);

            this.hookedIn = i > 0 ? this.level().getEntity(i - 1) : null;
        }

        if (EntityFishingHook.DATA_BITING.equals(datawatcherobject)) {
            this.biting = (Boolean) this.getEntityData().get(EntityFishingHook.DATA_BITING);
            if (this.biting) {
                this.setDeltaMovement(this.getDeltaMovement().x, (double) (-0.4F * MathHelper.nextFloat(this.syncronizedRandom, 0.6F, 1.0F)), this.getDeltaMovement().z);
            }
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d0) {
        double d1 = 64.0D;

        return d0 < 4096.0D;
    }

    @Override
    public void tick() {
        this.syncronizedRandom.setSeed(this.getUUID().getLeastSignificantBits() ^ this.level().getGameTime());
        super.tick();
        EntityHuman entityhuman = this.getPlayerOwner();

        if (entityhuman == null) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else if (this.level().isClientSide || !this.shouldStopFishing(entityhuman)) {
            if (this.onGround()) {
                ++this.life;
                if (this.life >= 1200) {
                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                    return;
                }
            } else {
                this.life = 0;
            }

            float f = 0.0F;
            BlockPosition blockposition = this.blockPosition();
            Fluid fluid = this.level().getFluidState(blockposition);

            if (fluid.is(TagsFluid.WATER)) {
                f = fluid.getHeight(this.level(), blockposition);
            }

            boolean flag = f > 0.0F;

            if (this.currentState == EntityFishingHook.HookState.FLYING) {
                if (this.hookedIn != null) {
                    this.setDeltaMovement(Vec3D.ZERO);
                    this.currentState = EntityFishingHook.HookState.HOOKED_IN_ENTITY;
                    return;
                }

                if (flag) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.3D, 0.2D, 0.3D));
                    this.currentState = EntityFishingHook.HookState.BOBBING;
                    return;
                }

                this.checkCollision();
            } else {
                if (this.currentState == EntityFishingHook.HookState.HOOKED_IN_ENTITY) {
                    if (this.hookedIn != null) {
                        if (!this.hookedIn.isRemoved() && this.hookedIn.level().dimension() == this.level().dimension()) {
                            this.setPos(this.hookedIn.getX(), this.hookedIn.getY(0.8D), this.hookedIn.getZ());
                        } else {
                            this.setHookedEntity((Entity) null);
                            this.currentState = EntityFishingHook.HookState.FLYING;
                        }
                    }

                    return;
                }

                if (this.currentState == EntityFishingHook.HookState.BOBBING) {
                    Vec3D vec3d = this.getDeltaMovement();
                    double d0 = this.getY() + vec3d.y - (double) blockposition.getY() - (double) f;

                    if (Math.abs(d0) < 0.01D) {
                        d0 += Math.signum(d0) * 0.1D;
                    }

                    this.setDeltaMovement(vec3d.x * 0.9D, vec3d.y - d0 * (double) this.random.nextFloat() * 0.2D, vec3d.z * 0.9D);
                    if (this.nibble <= 0 && this.timeUntilHooked <= 0) {
                        this.openWater = true;
                    } else {
                        this.openWater = this.openWater && this.outOfWaterTime < 10 && this.calculateOpenWater(blockposition);
                    }

                    if (flag) {
                        this.outOfWaterTime = Math.max(0, this.outOfWaterTime - 1);
                        if (this.biting) {
                            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.1D * (double) this.syncronizedRandom.nextFloat() * (double) this.syncronizedRandom.nextFloat(), 0.0D));
                        }

                        if (!this.level().isClientSide) {
                            this.catchingFish(blockposition);
                        }
                    } else {
                        this.outOfWaterTime = Math.min(10, this.outOfWaterTime + 1);
                    }
                }
            }

            if (!fluid.is(TagsFluid.WATER)) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.03D, 0.0D));
            }

            this.move(EnumMoveType.SELF, this.getDeltaMovement());
            this.applyEffectsFromBlocks();
            this.updateRotation();
            if (this.currentState == EntityFishingHook.HookState.FLYING && (this.onGround() || this.horizontalCollision)) {
                this.setDeltaMovement(Vec3D.ZERO);
            }

            double d1 = 0.92D;

            this.setDeltaMovement(this.getDeltaMovement().scale(0.92D));
            this.reapplyPosition();
        }
    }

    private boolean shouldStopFishing(EntityHuman entityhuman) {
        ItemStack itemstack = entityhuman.getMainHandItem();
        ItemStack itemstack1 = entityhuman.getOffhandItem();
        boolean flag = itemstack.is(Items.FISHING_ROD);
        boolean flag1 = itemstack1.is(Items.FISHING_ROD);

        if (!entityhuman.isRemoved() && entityhuman.isAlive() && (flag || flag1) && this.distanceToSqr((Entity) entityhuman) <= 1024.0D) {
            return false;
        } else {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            return true;
        }
    }

    private void checkCollision() {
        MovingObjectPosition movingobjectposition = ProjectileHelper.getHitResultOnMoveVector(this, this::canHitEntity);

        this.preHitTargetOrDeflectSelf(movingobjectposition); // CraftBukkit - projectile hit event
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) || entity.isAlive() && entity instanceof EntityItem;
    }

    @Override
    protected void onHitEntity(MovingObjectPositionEntity movingobjectpositionentity) {
        super.onHitEntity(movingobjectpositionentity);
        if (!this.level().isClientSide) {
            this.setHookedEntity(movingobjectpositionentity.getEntity());
        }

    }

    @Override
    protected void onHitBlock(MovingObjectPositionBlock movingobjectpositionblock) {
        super.onHitBlock(movingobjectpositionblock);
        this.setDeltaMovement(this.getDeltaMovement().normalize().scale(movingobjectpositionblock.distanceTo(this)));
    }

    public void setHookedEntity(@Nullable Entity entity) {
        this.hookedIn = entity;
        this.getEntityData().set(EntityFishingHook.DATA_HOOKED_ENTITY, entity == null ? 0 : entity.getId() + 1);
    }

    private void catchingFish(BlockPosition blockposition) {
        WorldServer worldserver = (WorldServer) this.level();
        int i = 1;
        BlockPosition blockposition1 = blockposition.above();

        if (this.rainInfluenced && this.random.nextFloat() < 0.25F && this.level().isRainingAt(blockposition1)) { // CraftBukkit
            ++i;
        }

        if (this.skyInfluenced && this.random.nextFloat() < 0.5F && !this.level().canSeeSky(blockposition1)) { // CraftBukkit
            --i;
        }

        if (this.nibble > 0) {
            --this.nibble;
            if (this.nibble <= 0) {
                this.timeUntilLured = 0;
                this.timeUntilHooked = 0;
                this.getEntityData().set(EntityFishingHook.DATA_BITING, false);
                // CraftBukkit start
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) this.getPlayerOwner().getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.FAILED_ATTEMPT);
                this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);
                // CraftBukkit end
            }
        } else if (this.timeUntilHooked > 0) {
            this.timeUntilHooked -= i;
            if (this.timeUntilHooked > 0) {
                this.fishAngle += (float) this.random.triangle(0.0D, 9.188D);
                float f = this.fishAngle * ((float) Math.PI / 180F);
                float f1 = MathHelper.sin(f);
                float f2 = MathHelper.cos(f);
                double d0 = this.getX() + (double) (f1 * (float) this.timeUntilHooked * 0.1F);
                double d1 = (double) ((float) MathHelper.floor(this.getY()) + 1.0F);
                double d2 = this.getZ() + (double) (f2 * (float) this.timeUntilHooked * 0.1F);
                IBlockData iblockdata = worldserver.getBlockState(BlockPosition.containing(d0, d1 - 1.0D, d2));

                if (iblockdata.is(Blocks.WATER)) {
                    if (this.random.nextFloat() < 0.15F) {
                        worldserver.sendParticles(Particles.BUBBLE, d0, d1 - (double) 0.1F, d2, 1, (double) f1, 0.1D, (double) f2, 0.0D);
                    }

                    float f3 = f1 * 0.04F;
                    float f4 = f2 * 0.04F;

                    worldserver.sendParticles(Particles.FISHING, d0, d1, d2, 0, (double) f4, 0.01D, (double) (-f3), 1.0D);
                    worldserver.sendParticles(Particles.FISHING, d0, d1, d2, 0, (double) (-f4), 0.01D, (double) f3, 1.0D);
                }
            } else {
                // CraftBukkit start
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) this.getPlayerOwner().getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.BITE);
                this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);
                if (playerFishEvent.isCancelled()) {
                    return;
                }
                // CraftBukkit end
                this.playSound(SoundEffects.FISHING_BOBBER_SPLASH, 0.25F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
                double d3 = this.getY() + 0.5D;

                worldserver.sendParticles(Particles.BUBBLE, this.getX(), d3, this.getZ(), (int) (1.0F + this.getBbWidth() * 20.0F), (double) this.getBbWidth(), 0.0D, (double) this.getBbWidth(), (double) 0.2F);
                worldserver.sendParticles(Particles.FISHING, this.getX(), d3, this.getZ(), (int) (1.0F + this.getBbWidth() * 20.0F), (double) this.getBbWidth(), 0.0D, (double) this.getBbWidth(), (double) 0.2F);
                this.nibble = MathHelper.nextInt(this.random, 20, 40);
                this.getEntityData().set(EntityFishingHook.DATA_BITING, true);
            }
        } else if (this.timeUntilLured > 0) {
            this.timeUntilLured -= i;
            float f5 = 0.15F;

            if (this.timeUntilLured < 20) {
                f5 += (float) (20 - this.timeUntilLured) * 0.05F;
            } else if (this.timeUntilLured < 40) {
                f5 += (float) (40 - this.timeUntilLured) * 0.02F;
            } else if (this.timeUntilLured < 60) {
                f5 += (float) (60 - this.timeUntilLured) * 0.01F;
            }

            if (this.random.nextFloat() < f5) {
                float f6 = MathHelper.nextFloat(this.random, 0.0F, 360.0F) * ((float) Math.PI / 180F);
                float f7 = MathHelper.nextFloat(this.random, 25.0F, 60.0F);
                double d4 = this.getX() + (double) (MathHelper.sin(f6) * f7) * 0.1D;
                double d5 = (double) ((float) MathHelper.floor(this.getY()) + 1.0F);
                double d6 = this.getZ() + (double) (MathHelper.cos(f6) * f7) * 0.1D;
                IBlockData iblockdata1 = worldserver.getBlockState(BlockPosition.containing(d4, d5 - 1.0D, d6));

                if (iblockdata1.is(Blocks.WATER)) {
                    worldserver.sendParticles(Particles.SPLASH, d4, d5, d6, 2 + this.random.nextInt(2), (double) 0.1F, 0.0D, (double) 0.1F, 0.0D);
                }
            }

            if (this.timeUntilLured <= 0) {
                // CraftBukkit start - logic to modify fishing wait time, lure time, and lure angle
                this.fishAngle = MathHelper.nextFloat(this.random, this.minLureAngle, this.maxLureAngle);
                this.timeUntilHooked = MathHelper.nextInt(this.random, this.minLureTime, this.maxLureTime);
                // CraftBukkit end
            }
        } else {
            // CraftBukkit start - logic to modify fishing wait time
            this.timeUntilLured = MathHelper.nextInt(this.random, this.minWaitTime, this.maxWaitTime);
            this.timeUntilLured -= (this.applyLure) ? this.lureSpeed : 0;
            // CraftBukkit end
        }

    }

    private boolean calculateOpenWater(BlockPosition blockposition) {
        EntityFishingHook.WaterPosition entityfishinghook_waterposition = EntityFishingHook.WaterPosition.INVALID;

        for (int i = -1; i <= 2; ++i) {
            EntityFishingHook.WaterPosition entityfishinghook_waterposition1 = this.getOpenWaterTypeForArea(blockposition.offset(-2, i, -2), blockposition.offset(2, i, 2));

            switch (entityfishinghook_waterposition1.ordinal()) {
                case 0:
                    if (entityfishinghook_waterposition == EntityFishingHook.WaterPosition.INVALID) {
                        return false;
                    }
                    break;
                case 1:
                    if (entityfishinghook_waterposition == EntityFishingHook.WaterPosition.ABOVE_WATER) {
                        return false;
                    }
                    break;
                case 2:
                    return false;
            }

            entityfishinghook_waterposition = entityfishinghook_waterposition1;
        }

        return true;
    }

    private EntityFishingHook.WaterPosition getOpenWaterTypeForArea(BlockPosition blockposition, BlockPosition blockposition1) {
        return (EntityFishingHook.WaterPosition) BlockPosition.betweenClosedStream(blockposition, blockposition1).map(this::getOpenWaterTypeForBlock).reduce((entityfishinghook_waterposition, entityfishinghook_waterposition1) -> {
            return entityfishinghook_waterposition == entityfishinghook_waterposition1 ? entityfishinghook_waterposition : EntityFishingHook.WaterPosition.INVALID;
        }).orElse(EntityFishingHook.WaterPosition.INVALID);
    }

    private EntityFishingHook.WaterPosition getOpenWaterTypeForBlock(BlockPosition blockposition) {
        IBlockData iblockdata = this.level().getBlockState(blockposition);

        if (!iblockdata.isAir() && !iblockdata.is(Blocks.LILY_PAD)) {
            Fluid fluid = iblockdata.getFluidState();

            return fluid.is(TagsFluid.WATER) && fluid.isSource() && iblockdata.getCollisionShape(this.level(), blockposition).isEmpty() ? EntityFishingHook.WaterPosition.INSIDE_WATER : EntityFishingHook.WaterPosition.INVALID;
        } else {
            return EntityFishingHook.WaterPosition.ABOVE_WATER;
        }
    }

    public boolean isOpenWaterFishing() {
        return this.openWater;
    }

    @Override
    public void addAdditionalSaveData(NBTTagCompound nbttagcompound) {}

    @Override
    public void readAdditionalSaveData(NBTTagCompound nbttagcompound) {}

    public int retrieve(ItemStack itemstack) {
        EntityHuman entityhuman = this.getPlayerOwner();

        if (!this.level().isClientSide && entityhuman != null && !this.shouldStopFishing(entityhuman)) {
            int i = 0;

            if (this.hookedIn != null) {
                // CraftBukkit start
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), this.hookedIn.getBukkitEntity(), (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.CAUGHT_ENTITY);
                this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);

                if (playerFishEvent.isCancelled()) {
                    return 0;
                }
                // CraftBukkit end
                this.pullEntity(this.hookedIn);
                CriterionTriggers.FISHING_ROD_HOOKED.trigger((EntityPlayer) entityhuman, itemstack, this, Collections.emptyList());
                this.level().broadcastEntityEvent(this, (byte) 31);
                i = this.hookedIn instanceof EntityItem ? 3 : 5;
            } else if (this.nibble > 0) {
                LootParams lootparams = (new LootParams.a((WorldServer) this.level())).withParameter(LootContextParameters.ORIGIN, this.position()).withParameter(LootContextParameters.TOOL, itemstack).withParameter(LootContextParameters.THIS_ENTITY, this).withLuck((float) this.luck + entityhuman.getLuck()).create(LootContextParameterSets.FISHING);
                LootTable loottable = this.level().getServer().reloadableRegistries().getLootTable(LootTables.FISHING);
                List<ItemStack> list = loottable.getRandomItems(lootparams);

                CriterionTriggers.FISHING_ROD_HOOKED.trigger((EntityPlayer) entityhuman, itemstack, this, list);

                for (ItemStack itemstack1 : list) {
                    EntityItem entityitem = new EntityItem(this.level(), this.getX(), this.getY(), this.getZ(), itemstack1);
                    // CraftBukkit start
                    PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), entityitem.getBukkitEntity(), (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.CAUGHT_FISH);
                    playerFishEvent.setExpToDrop(this.random.nextInt(6) + 1);
                    this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);

                    if (playerFishEvent.isCancelled()) {
                        return 0;
                    }
                    // CraftBukkit end
                    double d0 = entityhuman.getX() - this.getX();
                    double d1 = entityhuman.getY() - this.getY();
                    double d2 = entityhuman.getZ() - this.getZ();
                    double d3 = 0.1D;

                    entityitem.setDeltaMovement(d0 * 0.1D, d1 * 0.1D + Math.sqrt(Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2)) * 0.08D, d2 * 0.1D);
                    this.level().addFreshEntity(entityitem);
                    // CraftBukkit start - this.random.nextInt(6) + 1 -> playerFishEvent.getExpToDrop()
                    if (playerFishEvent.getExpToDrop() > 0) {
                        entityhuman.level().addFreshEntity(new EntityExperienceOrb(entityhuman.level(), entityhuman.getX(), entityhuman.getY() + 0.5D, entityhuman.getZ() + 0.5D, playerFishEvent.getExpToDrop()));
                    }
                    // CraftBukkit end
                    if (itemstack1.is(TagsItem.FISHES)) {
                        entityhuman.awardStat(StatisticList.FISH_CAUGHT, 1);
                    }
                }

                i = 1;
            }

            if (this.onGround()) {
                // CraftBukkit start
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.IN_GROUND);
                this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);

                if (playerFishEvent.isCancelled()) {
                    return 0;
                }
                // CraftBukkit end
                i = 2;
            }
            // CraftBukkit start
            if (i == 0) {
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((Player) entityhuman.getBukkitEntity(), null, (FishHook) this.getBukkitEntity(), PlayerFishEvent.State.REEL_IN);
                this.level().getCraftServer().getPluginManager().callEvent(playerFishEvent);
                if (playerFishEvent.isCancelled()) {
                    return 0;
                }
            }
            // CraftBukkit end

            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            return i;
        } else {
            return 0;
        }
    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 31 && this.level().isClientSide) {
            Entity entity = this.hookedIn;

            if (entity instanceof EntityHuman) {
                EntityHuman entityhuman = (EntityHuman) entity;

                if (entityhuman.isLocalPlayer()) {
                    this.pullEntity(this.hookedIn);
                }
            }
        }

        super.handleEntityEvent(b0);
    }

    public void pullEntity(Entity entity) {
        Entity entity1 = this.getOwner();

        if (entity1 != null) {
            Vec3D vec3d = (new Vec3D(entity1.getX() - this.getX(), entity1.getY() - this.getY(), entity1.getZ() - this.getZ())).scale(0.1D);

            entity.setDeltaMovement(entity.getDeltaMovement().add(vec3d));
        }
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason) {
        // CraftBukkit start - add Bukkit remove cause
        this.remove(entity_removalreason, null);
    }

    @Override
    public void remove(Entity.RemovalReason entity_removalreason, EntityRemoveEvent.Cause cause) {
        // CraftBukkit end
        this.updateOwnerInfo((EntityFishingHook) null);
        super.remove(entity_removalreason, cause); // CraftBukkit - add Bukkit remove cause
    }

    @Override
    public void onClientRemoval() {
        this.updateOwnerInfo((EntityFishingHook) null);
    }

    @Override
    public void setOwner(@Nullable Entity entity) {
        super.setOwner(entity);
        this.updateOwnerInfo(this);
    }

    private void updateOwnerInfo(@Nullable EntityFishingHook entityfishinghook) {
        EntityHuman entityhuman = this.getPlayerOwner();

        if (entityhuman != null) {
            entityhuman.fishing = entityfishinghook;
        }

    }

    @Nullable
    public EntityHuman getPlayerOwner() {
        Entity entity = this.getOwner();
        EntityHuman entityhuman;

        if (entity instanceof EntityHuman entityhuman1) {
            entityhuman = entityhuman1;
        } else {
            entityhuman = null;
        }

        return entityhuman;
    }

    @Nullable
    public Entity getHookedIn() {
        return this.hookedIn;
    }

    @Override
    public boolean canUsePortal(boolean flag) {
        return false;
    }

    @Override
    public Packet<PacketListenerPlayOut> getAddEntityPacket(EntityTrackerEntry entitytrackerentry) {
        Entity entity = this.getOwner();

        return new PacketPlayOutSpawnEntity(this, entitytrackerentry, entity == null ? this.getId() : entity.getId());
    }

    @Override
    public void recreateFromPacket(PacketPlayOutSpawnEntity packetplayoutspawnentity) {
        super.recreateFromPacket(packetplayoutspawnentity);
        if (this.getPlayerOwner() == null) {
            int i = packetplayoutspawnentity.getData();

            EntityFishingHook.LOGGER.error("Failed to recreate fishing hook on client. {} (id: {}) is not a valid owner.", this.level().getEntity(i), i);
            this.discard(null); // CraftBukkit - add Bukkit remove cause
        }

    }

    public static enum HookState {

        FLYING, HOOKED_IN_ENTITY, BOBBING;

        private HookState() {}
    }

    private static enum WaterPosition {

        ABOVE_WATER, INSIDE_WATER, INVALID;

        private WaterPosition() {}
    }
}

package net.minecraft.world.entity.vehicle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.SystemUtils;
import net.minecraft.core.BaseBlockPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.MathHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.npc.EntityVillager;
import net.minecraft.world.entity.npc.EntityVillagerTrader;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockMinecartTrackAbstract;
import net.minecraft.world.level.block.BlockPoweredRail;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockPropertyTrackPosition;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.Location;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.util.Vector;
// CraftBukkit end

public abstract class EntityMinecartAbstract extends VehicleEntity {

    private static final Vec3D LOWERED_PASSENGER_ATTACHMENT = new Vec3D(0.0D, 0.0D, 0.0D);
    private static final DataWatcherObject<Optional<IBlockData>> DATA_ID_CUSTOM_DISPLAY_BLOCK = DataWatcher.<Optional<IBlockData>>defineId(EntityMinecartAbstract.class, DataWatcherRegistry.OPTIONAL_BLOCK_STATE);
    private static final DataWatcherObject<Integer> DATA_ID_DISPLAY_OFFSET = DataWatcher.<Integer>defineId(EntityMinecartAbstract.class, DataWatcherRegistry.INT);
    private static final ImmutableMap<EntityPose, ImmutableList<Integer>> POSE_DISMOUNT_HEIGHTS = ImmutableMap.of(EntityPose.STANDING, ImmutableList.of(0, 1, -1), EntityPose.CROUCHING, ImmutableList.of(0, 1, -1), EntityPose.SWIMMING, ImmutableList.of(0, 1));
    protected static final float WATER_SLOWDOWN_FACTOR = 0.95F;
    private static final boolean DEFAULT_FLIPPED_ROTATION = false;
    private boolean onRails;
    private boolean flipped;
    private final MinecartBehavior behavior;
    private static final Map<BlockPropertyTrackPosition, Pair<BaseBlockPosition, BaseBlockPosition>> EXITS = Maps.newEnumMap((Map) SystemUtils.make(() -> {
        BaseBlockPosition baseblockposition = EnumDirection.WEST.getUnitVec3i();
        BaseBlockPosition baseblockposition1 = EnumDirection.EAST.getUnitVec3i();
        BaseBlockPosition baseblockposition2 = EnumDirection.NORTH.getUnitVec3i();
        BaseBlockPosition baseblockposition3 = EnumDirection.SOUTH.getUnitVec3i();
        BaseBlockPosition baseblockposition4 = baseblockposition.below();
        BaseBlockPosition baseblockposition5 = baseblockposition1.below();
        BaseBlockPosition baseblockposition6 = baseblockposition2.below();
        BaseBlockPosition baseblockposition7 = baseblockposition3.below();

        return ImmutableMap.of(BlockPropertyTrackPosition.NORTH_SOUTH, Pair.of(baseblockposition2, baseblockposition3), BlockPropertyTrackPosition.EAST_WEST, Pair.of(baseblockposition, baseblockposition1), BlockPropertyTrackPosition.ASCENDING_EAST, Pair.of(baseblockposition4, baseblockposition1), BlockPropertyTrackPosition.ASCENDING_WEST, Pair.of(baseblockposition, baseblockposition5), BlockPropertyTrackPosition.ASCENDING_NORTH, Pair.of(baseblockposition2, baseblockposition7), BlockPropertyTrackPosition.ASCENDING_SOUTH, Pair.of(baseblockposition6, baseblockposition3), BlockPropertyTrackPosition.SOUTH_EAST, Pair.of(baseblockposition3, baseblockposition1), BlockPropertyTrackPosition.SOUTH_WEST, Pair.of(baseblockposition3, baseblockposition), BlockPropertyTrackPosition.NORTH_WEST, Pair.of(baseblockposition2, baseblockposition), BlockPropertyTrackPosition.NORTH_EAST, Pair.of(baseblockposition2, baseblockposition1));
    }));

    // CraftBukkit start
    public boolean slowWhenEmpty = true;
    private double derailedX = 0.5;
    private double derailedY = 0.5;
    private double derailedZ = 0.5;
    private double flyingX = 0.95;
    private double flyingY = 0.95;
    private double flyingZ = 0.95;
    public Double maxSpeed;
    // CraftBukkit end

    protected EntityMinecartAbstract(EntityTypes<?> entitytypes, World world) {
        super(entitytypes, world);
        this.flipped = false;
        this.blocksBuilding = true;
        if (useExperimentalMovement(world)) {
            this.behavior = new NewMinecartBehavior(this);
        } else {
            this.behavior = new OldMinecartBehavior(this);
        }

    }

    protected EntityMinecartAbstract(EntityTypes<?> entitytypes, World world, double d0, double d1, double d2) {
        this(entitytypes, world);
        this.setInitialPos(d0, d1, d2);
    }

    public void setInitialPos(double d0, double d1, double d2) {
        this.setPos(d0, d1, d2);
        this.xo = d0;
        this.yo = d1;
        this.zo = d2;
    }

    @Nullable
    public static <T extends EntityMinecartAbstract> T createMinecart(World world, double d0, double d1, double d2, EntityTypes<T> entitytypes, EntitySpawnReason entityspawnreason, ItemStack itemstack, @Nullable EntityHuman entityhuman) {
        T t0 = entitytypes.create(world, entityspawnreason);

        if (t0 != null) {
            t0.setInitialPos(d0, d1, d2);
            EntityTypes.createDefaultStackConfig(world, itemstack, entityhuman).accept(t0);
            MinecartBehavior minecartbehavior = t0.getBehavior();

            if (minecartbehavior instanceof NewMinecartBehavior) {
                NewMinecartBehavior newminecartbehavior = (NewMinecartBehavior) minecartbehavior;
                BlockPosition blockposition = t0.getCurrentBlockPosOrRailBelow();
                IBlockData iblockdata = world.getBlockState(blockposition);

                newminecartbehavior.adjustToRails(blockposition, iblockdata, true);
            }
        }

        return t0;
    }

    public MinecartBehavior getBehavior() {
        return this.behavior;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.EVENTS;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        super.defineSynchedData(datawatcher_a);
        datawatcher_a.define(EntityMinecartAbstract.DATA_ID_CUSTOM_DISPLAY_BLOCK, Optional.empty());
        datawatcher_a.define(EntityMinecartAbstract.DATA_ID_DISPLAY_OFFSET, this.getDefaultDisplayOffset());
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return AbstractBoat.canVehicleCollide(this, entity);
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public Vec3D getRelativePortalPosition(EnumDirection.EnumAxis enumdirection_enumaxis, BlockUtil.Rectangle blockutil_rectangle) {
        return EntityLiving.resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(enumdirection_enumaxis, blockutil_rectangle));
    }

    @Override
    protected Vec3D getPassengerAttachmentPoint(Entity entity, EntitySize entitysize, float f) {
        boolean flag = entity instanceof EntityVillager || entity instanceof EntityVillagerTrader;

        return flag ? EntityMinecartAbstract.LOWERED_PASSENGER_ATTACHMENT : super.getPassengerAttachmentPoint(entity, entitysize, f);
    }

    @Override
    public Vec3D getDismountLocationForPassenger(EntityLiving entityliving) {
        EnumDirection enumdirection = this.getMotionDirection();

        if (enumdirection.getAxis() == EnumDirection.EnumAxis.Y) {
            return super.getDismountLocationForPassenger(entityliving);
        } else {
            int[][] aint = DismountUtil.offsetsForDirection(enumdirection);
            BlockPosition blockposition = this.blockPosition();
            BlockPosition.MutableBlockPosition blockposition_mutableblockposition = new BlockPosition.MutableBlockPosition();
            ImmutableList<EntityPose> immutablelist = entityliving.getDismountPoses();
            UnmodifiableIterator unmodifiableiterator = immutablelist.iterator();

            while (unmodifiableiterator.hasNext()) {
                EntityPose entitypose = (EntityPose) unmodifiableiterator.next();
                EntitySize entitysize = entityliving.getDimensions(entitypose);
                float f = Math.min(entitysize.width(), 1.0F) / 2.0F;
                UnmodifiableIterator unmodifiableiterator1 = ((ImmutableList) EntityMinecartAbstract.POSE_DISMOUNT_HEIGHTS.get(entitypose)).iterator();

                while (unmodifiableiterator1.hasNext()) {
                    int i = (Integer) unmodifiableiterator1.next();

                    for (int[] aint1 : aint) {
                        blockposition_mutableblockposition.set(blockposition.getX() + aint1[0], blockposition.getY() + i, blockposition.getZ() + aint1[1]);
                        double d0 = this.level().getBlockFloorHeight(DismountUtil.nonClimbableShape(this.level(), blockposition_mutableblockposition), () -> {
                            return DismountUtil.nonClimbableShape(this.level(), blockposition_mutableblockposition.below());
                        });

                        if (DismountUtil.isBlockFloorValid(d0)) {
                            AxisAlignedBB axisalignedbb = new AxisAlignedBB((double) (-f), 0.0D, (double) (-f), (double) f, (double) entitysize.height(), (double) f);
                            Vec3D vec3d = Vec3D.upFromBottomCenterOf(blockposition_mutableblockposition, d0);

                            if (DismountUtil.canDismountTo(this.level(), entityliving, axisalignedbb.move(vec3d))) {
                                entityliving.setPose(entitypose);
                                return vec3d;
                            }
                        }
                    }
                }
            }

            double d1 = this.getBoundingBox().maxY;

            blockposition_mutableblockposition.set((double) blockposition.getX(), d1, (double) blockposition.getZ());
            UnmodifiableIterator unmodifiableiterator2 = immutablelist.iterator();

            while (unmodifiableiterator2.hasNext()) {
                EntityPose entitypose1 = (EntityPose) unmodifiableiterator2.next();
                double d2 = (double) entityliving.getDimensions(entitypose1).height();
                int j = MathHelper.ceil(d1 - (double) blockposition_mutableblockposition.getY() + d2);
                double d3 = DismountUtil.findCeilingFrom(blockposition_mutableblockposition, j, (blockposition1) -> {
                    return this.level().getBlockState(blockposition1).getCollisionShape(this.level(), blockposition1);
                });

                if (d1 + d2 <= d3) {
                    entityliving.setPose(entitypose1);
                    break;
                }
            }

            return super.getDismountLocationForPassenger(entityliving);
        }
    }

    @Override
    protected float getBlockSpeedFactor() {
        IBlockData iblockdata = this.level().getBlockState(this.blockPosition());

        return iblockdata.is(TagsBlock.RAILS) ? 1.0F : super.getBlockSpeedFactor();
    }

    @Override
    public void animateHurt(float f) {
        this.setHurtDir(-this.getHurtDir());
        this.setHurtTime(10);
        this.setDamage(this.getDamage() + this.getDamage() * 10.0F);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    public static Pair<BaseBlockPosition, BaseBlockPosition> exits(BlockPropertyTrackPosition blockpropertytrackposition) {
        return (Pair) EntityMinecartAbstract.EXITS.get(blockpropertytrackposition);
    }

    @Override
    public EnumDirection getMotionDirection() {
        return this.behavior.getMotionDirection();
    }

    @Override
    protected double getDefaultGravity() {
        return this.isInWater() ? 0.005D : 0.04D;
    }

    @Override
    public void tick() {
        // CraftBukkit start
        double prevX = this.getX();
        double prevY = this.getY();
        double prevZ = this.getZ();
        float prevYaw = this.getYRot();
        float prevPitch = this.getXRot();
        // CraftBukkit end

        if (this.getHurtTime() > 0) {
            this.setHurtTime(this.getHurtTime() - 1);
        }

        if (this.getDamage() > 0.0F) {
            this.setDamage(this.getDamage() - 1.0F);
        }

        this.checkBelowWorld();
        // this.handlePortal(); // CraftBukkit - handled in postTick
        this.behavior.tick();
        // CraftBukkit start
        org.bukkit.World bworld = this.level().getWorld();
        Location from = new Location(bworld, prevX, prevY, prevZ, prevYaw, prevPitch);
        Location to = CraftLocation.toBukkit(this.position(), bworld, this.getYRot(), this.getXRot());
        Vehicle vehicle = (Vehicle) this.getBukkitEntity();

        this.level().getCraftServer().getPluginManager().callEvent(new org.bukkit.event.vehicle.VehicleUpdateEvent(vehicle));

        if (!from.equals(to)) {
            this.level().getCraftServer().getPluginManager().callEvent(new org.bukkit.event.vehicle.VehicleMoveEvent(vehicle, from, to));
        }
        // CraftBukkit end
        this.updateInWaterStateAndDoFluidPushing();
        if (this.isInLava()) {
            this.lavaIgnite();
            this.lavaHurt();
            this.fallDistance *= 0.5D;
        }

        this.firstTick = false;
    }

    public boolean isFirstTick() {
        return this.firstTick;
    }

    public BlockPosition getCurrentBlockPosOrRailBelow() {
        int i = MathHelper.floor(this.getX());
        int j = MathHelper.floor(this.getY());
        int k = MathHelper.floor(this.getZ());

        if (useExperimentalMovement(this.level())) {
            double d0 = this.getY() - 0.1D - (double) 1.0E-5F;

            if (this.level().getBlockState(BlockPosition.containing((double) i, d0, (double) k)).is(TagsBlock.RAILS)) {
                j = MathHelper.floor(d0);
            }
        } else if (this.level().getBlockState(new BlockPosition(i, j - 1, k)).is(TagsBlock.RAILS)) {
            --j;
        }

        return new BlockPosition(i, j, k);
    }

    protected double getMaxSpeed(WorldServer worldserver) {
        return this.behavior.getMaxSpeed(worldserver);
    }

    public void activateMinecart(int i, int j, int k, boolean flag) {}

    @Override
    public void lerpPositionAndRotationStep(int i, double d0, double d1, double d2, double d3, double d4) {
        super.lerpPositionAndRotationStep(i, d0, d1, d2, d3, d4);
    }

    @Override
    public void applyGravity() {
        super.applyGravity();
    }

    @Override
    public void reapplyPosition() {
        super.reapplyPosition();
    }

    @Override
    public boolean updateInWaterStateAndDoFluidPushing() {
        return super.updateInWaterStateAndDoFluidPushing();
    }

    @Override
    public Vec3D getKnownMovement() {
        return this.behavior.getKnownMovement(super.getKnownMovement());
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.behavior.getInterpolation();
    }

    @Override
    public void lerpMotion(double d0, double d1, double d2) {
        this.behavior.lerpMotion(d0, d1, d2);
    }

    protected void moveAlongTrack(WorldServer worldserver) {
        this.behavior.moveAlongTrack(worldserver);
    }

    protected void comeOffTrack(WorldServer worldserver) {
        double d0 = this.getMaxSpeed(worldserver);
        Vec3D vec3d = this.getDeltaMovement();

        this.setDeltaMovement(MathHelper.clamp(vec3d.x, -d0, d0), vec3d.y, MathHelper.clamp(vec3d.z, -d0, d0));
        if (this.onGround()) {
            // CraftBukkit start - replace magic numbers with our variables
            this.setDeltaMovement(new Vec3D(this.getDeltaMovement().x * this.derailedX, this.getDeltaMovement().y * this.derailedY, this.getDeltaMovement().z * this.derailedZ));
            // CraftBukkit end
        }

        this.move(EnumMoveType.SELF, this.getDeltaMovement());
        if (!this.onGround()) {
            // CraftBukkit start - replace magic numbers with our variables
            this.setDeltaMovement(new Vec3D(this.getDeltaMovement().x * this.flyingX, this.getDeltaMovement().y * this.flyingY, this.getDeltaMovement().z * this.flyingZ));
            // CraftBukkit end
        }

    }

    protected double makeStepAlongTrack(BlockPosition blockposition, BlockPropertyTrackPosition blockpropertytrackposition, double d0) {
        return this.behavior.stepAlongTrack(blockposition, blockpropertytrackposition, d0);
    }

    @Override
    public void move(EnumMoveType enummovetype, Vec3D vec3d) {
        if (useExperimentalMovement(this.level())) {
            Vec3D vec3d1 = this.position().add(vec3d);

            super.move(enummovetype, vec3d);
            boolean flag = this.behavior.pushAndPickupEntities();

            if (flag) {
                super.move(enummovetype, vec3d1.subtract(this.position()));
            }

            if (enummovetype.equals(EnumMoveType.PISTON)) {
                this.onRails = false;
            }
        } else {
            super.move(enummovetype, vec3d);
            this.applyEffectsFromBlocks();
        }

    }

    @Override
    public void applyEffectsFromBlocks() {
        if (!useExperimentalMovement(this.level())) {
            this.applyEffectsFromBlocks(this.position(), this.position());
        } else {
            super.applyEffectsFromBlocks();
        }

    }

    @Override
    public boolean isOnRails() {
        return this.onRails;
    }

    public void setOnRails(boolean flag) {
        this.onRails = flag;
    }

    public boolean isFlipped() {
        return this.flipped;
    }

    public void setFlipped(boolean flag) {
        this.flipped = flag;
    }

    public Vec3D getRedstoneDirection(BlockPosition blockposition) {
        IBlockData iblockdata = this.level().getBlockState(blockposition);

        if (iblockdata.is(Blocks.POWERED_RAIL) && (Boolean) iblockdata.getValue(BlockPoweredRail.POWERED)) {
            BlockPropertyTrackPosition blockpropertytrackposition = (BlockPropertyTrackPosition) iblockdata.getValue(((BlockMinecartTrackAbstract) iblockdata.getBlock()).getShapeProperty());

            if (blockpropertytrackposition == BlockPropertyTrackPosition.EAST_WEST) {
                if (this.isRedstoneConductor(blockposition.west())) {
                    return new Vec3D(1.0D, 0.0D, 0.0D);
                }

                if (this.isRedstoneConductor(blockposition.east())) {
                    return new Vec3D(-1.0D, 0.0D, 0.0D);
                }
            } else if (blockpropertytrackposition == BlockPropertyTrackPosition.NORTH_SOUTH) {
                if (this.isRedstoneConductor(blockposition.north())) {
                    return new Vec3D(0.0D, 0.0D, 1.0D);
                }

                if (this.isRedstoneConductor(blockposition.south())) {
                    return new Vec3D(0.0D, 0.0D, -1.0D);
                }
            }

            return Vec3D.ZERO;
        } else {
            return Vec3D.ZERO;
        }
    }

    public boolean isRedstoneConductor(BlockPosition blockposition) {
        return this.level().getBlockState(blockposition).isRedstoneConductor(this.level(), blockposition);
    }

    protected Vec3D applyNaturalSlowdown(Vec3D vec3d) {
        double d0 = this.behavior.getSlowdownFactor();
        Vec3D vec3d1 = vec3d.multiply(d0, 0.0D, d0);

        if (this.isInWater()) {
            vec3d1 = vec3d1.scale((double) 0.95F);
        }

        return vec3d1;
    }

    @Override
    protected void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

        this.setCustomDisplayBlockState(nbttagcompound.read("DisplayState", IBlockData.CODEC, registryops));
        this.setDisplayOffset(nbttagcompound.getIntOr("DisplayOffset", this.getDefaultDisplayOffset()));
        this.flipped = nbttagcompound.getBooleanOr("FlippedRotation", false);
        this.firstTick = nbttagcompound.getBooleanOr("HasTicked", false);
    }

    @Override
    protected void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        this.getCustomDisplayBlockState().ifPresent((iblockdata) -> {
            RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

            nbttagcompound.store("DisplayState", IBlockData.CODEC, registryops, iblockdata);
        });
        int i = this.getDisplayOffset();

        if (i != this.getDefaultDisplayOffset()) {
            nbttagcompound.putInt("DisplayOffset", i);
        }

        nbttagcompound.putBoolean("FlippedRotation", this.flipped);
        nbttagcompound.putBoolean("HasTicked", this.firstTick);
    }

    @Override
    public void push(Entity entity) {
        if (!this.level().isClientSide) {
            if (!entity.noPhysics && !this.noPhysics) {
                if (!this.hasPassenger(entity)) {
                    // CraftBukkit start
                    VehicleEntityCollisionEvent collisionEvent = new VehicleEntityCollisionEvent((Vehicle) this.getBukkitEntity(), entity.getBukkitEntity());
                    this.level().getCraftServer().getPluginManager().callEvent(collisionEvent);

                    if (collisionEvent.isCancelled()) {
                        return;
                    }
                    // CraftBukkit end
                    double d0 = entity.getX() - this.getX();
                    double d1 = entity.getZ() - this.getZ();
                    double d2 = d0 * d0 + d1 * d1;

                    if (d2 >= (double) 1.0E-4F) {
                        d2 = Math.sqrt(d2);
                        d0 /= d2;
                        d1 /= d2;
                        double d3 = 1.0D / d2;

                        if (d3 > 1.0D) {
                            d3 = 1.0D;
                        }

                        d0 *= d3;
                        d1 *= d3;
                        d0 *= (double) 0.1F;
                        d1 *= (double) 0.1F;
                        d0 *= 0.5D;
                        d1 *= 0.5D;
                        if (entity instanceof EntityMinecartAbstract) {
                            EntityMinecartAbstract entityminecartabstract = (EntityMinecartAbstract) entity;

                            this.pushOtherMinecart(entityminecartabstract, d0, d1);
                        } else {
                            this.push(-d0, 0.0D, -d1);
                            entity.push(d0 / 4.0D, 0.0D, d1 / 4.0D);
                        }
                    }

                }
            }
        }
    }

    private void pushOtherMinecart(EntityMinecartAbstract entityminecartabstract, double d0, double d1) {
        double d2;
        double d3;

        if (useExperimentalMovement(this.level())) {
            d2 = this.getDeltaMovement().x;
            d3 = this.getDeltaMovement().z;
        } else {
            d2 = entityminecartabstract.getX() - this.getX();
            d3 = entityminecartabstract.getZ() - this.getZ();
        }

        Vec3D vec3d = (new Vec3D(d2, 0.0D, d3)).normalize();
        Vec3D vec3d1 = (new Vec3D((double) MathHelper.cos(this.getYRot() * ((float) Math.PI / 180F)), 0.0D, (double) MathHelper.sin(this.getYRot() * ((float) Math.PI / 180F)))).normalize();
        double d4 = Math.abs(vec3d.dot(vec3d1));

        if (d4 >= (double) 0.8F || useExperimentalMovement(this.level())) {
            Vec3D vec3d2 = this.getDeltaMovement();
            Vec3D vec3d3 = entityminecartabstract.getDeltaMovement();

            if (entityminecartabstract.isFurnace() && !this.isFurnace()) {
                this.setDeltaMovement(vec3d2.multiply(0.2D, 1.0D, 0.2D));
                this.push(vec3d3.x - d0, 0.0D, vec3d3.z - d1);
                entityminecartabstract.setDeltaMovement(vec3d3.multiply(0.95D, 1.0D, 0.95D));
            } else if (!entityminecartabstract.isFurnace() && this.isFurnace()) {
                entityminecartabstract.setDeltaMovement(vec3d3.multiply(0.2D, 1.0D, 0.2D));
                entityminecartabstract.push(vec3d2.x + d0, 0.0D, vec3d2.z + d1);
                this.setDeltaMovement(vec3d2.multiply(0.95D, 1.0D, 0.95D));
            } else {
                double d5 = (vec3d3.x + vec3d2.x) / 2.0D;
                double d6 = (vec3d3.z + vec3d2.z) / 2.0D;

                this.setDeltaMovement(vec3d2.multiply(0.2D, 1.0D, 0.2D));
                this.push(d5 - d0, 0.0D, d6 - d1);
                entityminecartabstract.setDeltaMovement(vec3d3.multiply(0.2D, 1.0D, 0.2D));
                entityminecartabstract.push(d5 + d0, 0.0D, d6 + d1);
            }

        }
    }

    public IBlockData getDisplayBlockState() {
        return (IBlockData) this.getCustomDisplayBlockState().orElseGet(this::getDefaultDisplayBlockState);
    }

    private Optional<IBlockData> getCustomDisplayBlockState() {
        return (Optional) this.getEntityData().get(EntityMinecartAbstract.DATA_ID_CUSTOM_DISPLAY_BLOCK);
    }

    public IBlockData getDefaultDisplayBlockState() {
        return Blocks.AIR.defaultBlockState();
    }

    public int getDisplayOffset() {
        return (Integer) this.getEntityData().get(EntityMinecartAbstract.DATA_ID_DISPLAY_OFFSET);
    }

    public int getDefaultDisplayOffset() {
        return 6;
    }

    public void setCustomDisplayBlockState(Optional<IBlockData> optional) {
        this.getEntityData().set(EntityMinecartAbstract.DATA_ID_CUSTOM_DISPLAY_BLOCK, optional);
    }

    public void setDisplayOffset(int i) {
        this.getEntityData().set(EntityMinecartAbstract.DATA_ID_DISPLAY_OFFSET, i);
    }

    public static boolean useExperimentalMovement(World world) {
        return world.enabledFeatures().contains(FeatureFlags.MINECART_IMPROVEMENTS);
    }

    @Override
    public abstract ItemStack getPickResult();

    public boolean isRideable() {
        return false;
    }

    public boolean isFurnace() {
        return false;
    }

    // CraftBukkit start - Methods for getting and setting flying and derailed velocity modifiers
    public Vector getFlyingVelocityMod() {
        return new Vector(flyingX, flyingY, flyingZ);
    }

    public void setFlyingVelocityMod(Vector flying) {
        flyingX = flying.getX();
        flyingY = flying.getY();
        flyingZ = flying.getZ();
    }

    public Vector getDerailedVelocityMod() {
        return new Vector(derailedX, derailedY, derailedZ);
    }

    public void setDerailedVelocityMod(Vector derailed) {
        derailedX = derailed.getX();
        derailedY = derailed.getY();
        derailedZ = derailed.getZ();
    }
    // CraftBukkit end
}

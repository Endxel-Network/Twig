package net.minecraft.world.level.border;

import com.google.common.collect.Lists;
import com.mojang.serialization.DynamicLike;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;
import net.minecraft.world.phys.shapes.OperatorBoolean;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapes;

public class WorldBorder {

    public static final double MAX_SIZE = (double) 5.999997E7F;
    public static final double MAX_CENTER_COORDINATE = 2.9999984E7D;
    private final List<IWorldBorderListener> listeners = Lists.newArrayList();
    private double damagePerBlock = 0.2D;
    private double damageSafeZone = 5.0D;
    private int warningTime = 15;
    private int warningBlocks = 5;
    private double centerX;
    private double centerZ;
    int absoluteMaxSize = 29999984;
    private WorldBorder.a extent = new WorldBorder.e((double) 5.999997E7F);
    public static final WorldBorder.d DEFAULT_SETTINGS = new WorldBorder.d(0.0D, 0.0D, 0.2D, 5.0D, 5, 15, (double) 5.999997E7F, 0L, 0.0D);
    public net.minecraft.server.level.WorldServer world; // CraftBukkit

    public WorldBorder() {}

    public boolean isWithinBounds(BlockPosition blockposition) {
        return this.isWithinBounds((double) blockposition.getX(), (double) blockposition.getZ());
    }

    public boolean isWithinBounds(Vec3D vec3d) {
        return this.isWithinBounds(vec3d.x, vec3d.z);
    }

    public boolean isWithinBounds(ChunkCoordIntPair chunkcoordintpair) {
        return this.isWithinBounds((double) chunkcoordintpair.getMinBlockX(), (double) chunkcoordintpair.getMinBlockZ()) && this.isWithinBounds((double) chunkcoordintpair.getMaxBlockX(), (double) chunkcoordintpair.getMaxBlockZ());
    }

    public boolean isWithinBounds(AxisAlignedBB axisalignedbb) {
        return this.isWithinBounds(axisalignedbb.minX, axisalignedbb.minZ, axisalignedbb.maxX - (double) 1.0E-5F, axisalignedbb.maxZ - (double) 1.0E-5F);
    }

    private boolean isWithinBounds(double d0, double d1, double d2, double d3) {
        return this.isWithinBounds(d0, d1) && this.isWithinBounds(d2, d3);
    }

    public boolean isWithinBounds(double d0, double d1) {
        return this.isWithinBounds(d0, d1, 0.0D);
    }

    public boolean isWithinBounds(double d0, double d1, double d2) {
        return d0 >= this.getMinX() - d2 && d0 < this.getMaxX() + d2 && d1 >= this.getMinZ() - d2 && d1 < this.getMaxZ() + d2;
    }

    public BlockPosition clampToBounds(BlockPosition blockposition) {
        return this.clampToBounds((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
    }

    public BlockPosition clampToBounds(Vec3D vec3d) {
        return this.clampToBounds(vec3d.x(), vec3d.y(), vec3d.z());
    }

    public BlockPosition clampToBounds(double d0, double d1, double d2) {
        return BlockPosition.containing(this.clampVec3ToBound(d0, d1, d2));
    }

    public Vec3D clampVec3ToBound(Vec3D vec3d) {
        return this.clampVec3ToBound(vec3d.x, vec3d.y, vec3d.z);
    }

    public Vec3D clampVec3ToBound(double d0, double d1, double d2) {
        return new Vec3D(MathHelper.clamp(d0, this.getMinX(), this.getMaxX() - (double) 1.0E-5F), d1, MathHelper.clamp(d2, this.getMinZ(), this.getMaxZ() - (double) 1.0E-5F));
    }

    public double getDistanceToBorder(Entity entity) {
        return this.getDistanceToBorder(entity.getX(), entity.getZ());
    }

    public VoxelShape getCollisionShape() {
        return this.extent.getCollisionShape();
    }

    public double getDistanceToBorder(double d0, double d1) {
        double d2 = d1 - this.getMinZ();
        double d3 = this.getMaxZ() - d1;
        double d4 = d0 - this.getMinX();
        double d5 = this.getMaxX() - d0;
        double d6 = Math.min(d4, d5);

        d6 = Math.min(d6, d2);
        return Math.min(d6, d3);
    }

    public List<WorldBorder.b> closestBorder(double d0, double d1) {
        WorldBorder.b[] aworldborder_b = new WorldBorder.b[]{new WorldBorder.b(EnumDirection.NORTH, d1 - this.getMinZ()), new WorldBorder.b(EnumDirection.SOUTH, this.getMaxZ() - d1), new WorldBorder.b(EnumDirection.WEST, d0 - this.getMinX()), new WorldBorder.b(EnumDirection.EAST, this.getMaxX() - d0)};

        return Arrays.stream(aworldborder_b).sorted(Comparator.comparingDouble((worldborder_b) -> {
            return worldborder_b.distance;
        })).toList();
    }

    public boolean isInsideCloseToBorder(Entity entity, AxisAlignedBB axisalignedbb) {
        double d0 = Math.max(MathHelper.absMax(axisalignedbb.getXsize(), axisalignedbb.getZsize()), 1.0D);

        return this.getDistanceToBorder(entity) < d0 * 2.0D && this.isWithinBounds(entity.getX(), entity.getZ(), d0);
    }

    public BorderStatus getStatus() {
        return this.extent.getStatus();
    }

    public double getMinX() {
        return this.extent.getMinX();
    }

    public double getMinZ() {
        return this.extent.getMinZ();
    }

    public double getMaxX() {
        return this.extent.getMaxX();
    }

    public double getMaxZ() {
        return this.extent.getMaxZ();
    }

    public double getCenterX() {
        return this.centerX;
    }

    public double getCenterZ() {
        return this.centerZ;
    }

    public void setCenter(double d0, double d1) {
        this.centerX = d0;
        this.centerZ = d1;
        this.extent.onCenterChange();

        for (IWorldBorderListener iworldborderlistener : this.getListeners()) {
            iworldborderlistener.onBorderCenterSet(this, d0, d1);
        }

    }

    public double getSize() {
        return this.extent.getSize();
    }

    public long getLerpRemainingTime() {
        return this.extent.getLerpRemainingTime();
    }

    public double getLerpTarget() {
        return this.extent.getLerpTarget();
    }

    public void setSize(double d0) {
        this.extent = new WorldBorder.e(d0);

        for (IWorldBorderListener iworldborderlistener : this.getListeners()) {
            iworldborderlistener.onBorderSizeSet(this, d0);
        }

    }

    public void lerpSizeBetween(double d0, double d1, long i) {
        this.extent = (WorldBorder.a) (d0 == d1 ? new WorldBorder.e(d1) : new WorldBorder.c(d0, d1, i));

        for (IWorldBorderListener iworldborderlistener : this.getListeners()) {
            iworldborderlistener.onBorderSizeLerping(this, d0, d1, i);
        }

    }

    protected List<IWorldBorderListener> getListeners() {
        return Lists.newArrayList(this.listeners);
    }

    public void addListener(IWorldBorderListener iworldborderlistener) {
        if (listeners.contains(iworldborderlistener)) return; // CraftBukkit
        this.listeners.add(iworldborderlistener);
    }

    public void removeListener(IWorldBorderListener iworldborderlistener) {
        this.listeners.remove(iworldborderlistener);
    }

    public void setAbsoluteMaxSize(int i) {
        this.absoluteMaxSize = i;
        this.extent.onAbsoluteMaxSizeChange();
    }

    public int getAbsoluteMaxSize() {
        return this.absoluteMaxSize;
    }

    public double getDamageSafeZone() {
        return this.damageSafeZone;
    }

    public void setDamageSafeZone(double d0) {
        this.damageSafeZone = d0;

        for (IWorldBorderListener iworldborderlistener : this.getListeners()) {
            iworldborderlistener.onBorderSetDamageSafeZOne(this, d0);
        }

    }

    public double getDamagePerBlock() {
        return this.damagePerBlock;
    }

    public void setDamagePerBlock(double d0) {
        this.damagePerBlock = d0;

        for (IWorldBorderListener iworldborderlistener : this.getListeners()) {
            iworldborderlistener.onBorderSetDamagePerBlock(this, d0);
        }

    }

    public double getLerpSpeed() {
        return this.extent.getLerpSpeed();
    }

    public int getWarningTime() {
        return this.warningTime;
    }

    public void setWarningTime(int i) {
        this.warningTime = i;

        for (IWorldBorderListener iworldborderlistener : this.getListeners()) {
            iworldborderlistener.onBorderSetWarningTime(this, i);
        }

    }

    public int getWarningBlocks() {
        return this.warningBlocks;
    }

    public void setWarningBlocks(int i) {
        this.warningBlocks = i;

        for (IWorldBorderListener iworldborderlistener : this.getListeners()) {
            iworldborderlistener.onBorderSetWarningBlocks(this, i);
        }

    }

    public void tick() {
        this.extent = this.extent.update();
    }

    public WorldBorder.d createSettings() {
        return new WorldBorder.d(this);
    }

    public void applySettings(WorldBorder.d worldborder_d) {
        this.setCenter(worldborder_d.getCenterX(), worldborder_d.getCenterZ());
        this.setDamagePerBlock(worldborder_d.getDamagePerBlock());
        this.setDamageSafeZone(worldborder_d.getSafeZone());
        this.setWarningBlocks(worldborder_d.getWarningBlocks());
        this.setWarningTime(worldborder_d.getWarningTime());
        if (worldborder_d.getSizeLerpTime() > 0L) {
            this.lerpSizeBetween(worldborder_d.getSize(), worldborder_d.getSizeLerpTarget(), worldborder_d.getSizeLerpTime());
        } else {
            this.setSize(worldborder_d.getSize());
        }

    }

    private class c implements WorldBorder.a {

        private final double from;
        private final double to;
        private final long lerpEnd;
        private final long lerpBegin;
        private final double lerpDuration;

        c(final double d0, final double d1, final long i) {
            this.from = d0;
            this.to = d1;
            this.lerpDuration = (double) i;
            this.lerpBegin = SystemUtils.getMillis();
            this.lerpEnd = this.lerpBegin + i;
        }

        @Override
        public double getMinX() {
            return MathHelper.clamp(WorldBorder.this.getCenterX() - this.getSize() / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getMinZ() {
            return MathHelper.clamp(WorldBorder.this.getCenterZ() - this.getSize() / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getMaxX() {
            return MathHelper.clamp(WorldBorder.this.getCenterX() + this.getSize() / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getMaxZ() {
            return MathHelper.clamp(WorldBorder.this.getCenterZ() + this.getSize() / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
        }

        @Override
        public double getSize() {
            double d0 = (double) (SystemUtils.getMillis() - this.lerpBegin) / this.lerpDuration;

            return d0 < 1.0D ? MathHelper.lerp(d0, this.from, this.to) : this.to;
        }

        @Override
        public double getLerpSpeed() {
            return Math.abs(this.from - this.to) / (double) (this.lerpEnd - this.lerpBegin);
        }

        @Override
        public long getLerpRemainingTime() {
            return this.lerpEnd - SystemUtils.getMillis();
        }

        @Override
        public double getLerpTarget() {
            return this.to;
        }

        @Override
        public BorderStatus getStatus() {
            return this.to < this.from ? BorderStatus.SHRINKING : BorderStatus.GROWING;
        }

        @Override
        public void onCenterChange() {}

        @Override
        public void onAbsoluteMaxSizeChange() {}

        @Override
        public WorldBorder.a update() {
            return (WorldBorder.a) (this.getLerpRemainingTime() <= 0L ? WorldBorder.this.new e(this.to) : this);
        }

        @Override
        public VoxelShape getCollisionShape() {
            return VoxelShapes.join(VoxelShapes.INFINITY, VoxelShapes.box(Math.floor(this.getMinX()), Double.NEGATIVE_INFINITY, Math.floor(this.getMinZ()), Math.ceil(this.getMaxX()), Double.POSITIVE_INFINITY, Math.ceil(this.getMaxZ())), OperatorBoolean.ONLY_FIRST);
        }
    }

    private class e implements WorldBorder.a {

        private final double size;
        private double minX;
        private double minZ;
        private double maxX;
        private double maxZ;
        private VoxelShape shape;

        public e(final double d0) {
            this.size = d0;
            this.updateBox();
        }

        @Override
        public double getMinX() {
            return this.minX;
        }

        @Override
        public double getMaxX() {
            return this.maxX;
        }

        @Override
        public double getMinZ() {
            return this.minZ;
        }

        @Override
        public double getMaxZ() {
            return this.maxZ;
        }

        @Override
        public double getSize() {
            return this.size;
        }

        @Override
        public BorderStatus getStatus() {
            return BorderStatus.STATIONARY;
        }

        @Override
        public double getLerpSpeed() {
            return 0.0D;
        }

        @Override
        public long getLerpRemainingTime() {
            return 0L;
        }

        @Override
        public double getLerpTarget() {
            return this.size;
        }

        private void updateBox() {
            this.minX = MathHelper.clamp(WorldBorder.this.getCenterX() - this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.minZ = MathHelper.clamp(WorldBorder.this.getCenterZ() - this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.maxX = MathHelper.clamp(WorldBorder.this.getCenterX() + this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.maxZ = MathHelper.clamp(WorldBorder.this.getCenterZ() + this.size / 2.0D, (double) (-WorldBorder.this.absoluteMaxSize), (double) WorldBorder.this.absoluteMaxSize);
            this.shape = VoxelShapes.join(VoxelShapes.INFINITY, VoxelShapes.box(Math.floor(this.getMinX()), Double.NEGATIVE_INFINITY, Math.floor(this.getMinZ()), Math.ceil(this.getMaxX()), Double.POSITIVE_INFINITY, Math.ceil(this.getMaxZ())), OperatorBoolean.ONLY_FIRST);
        }

        @Override
        public void onAbsoluteMaxSizeChange() {
            this.updateBox();
        }

        @Override
        public void onCenterChange() {
            this.updateBox();
        }

        @Override
        public WorldBorder.a update() {
            return this;
        }

        @Override
        public VoxelShape getCollisionShape() {
            return this.shape;
        }
    }

    public static record b(EnumDirection direction, double distance) {

    }

    public static class d {

        private final double centerX;
        private final double centerZ;
        private final double damagePerBlock;
        private final double safeZone;
        private final int warningBlocks;
        private final int warningTime;
        private final double size;
        private final long sizeLerpTime;
        private final double sizeLerpTarget;

        d(double d0, double d1, double d2, double d3, int i, int j, double d4, long k, double d5) {
            this.centerX = d0;
            this.centerZ = d1;
            this.damagePerBlock = d2;
            this.safeZone = d3;
            this.warningBlocks = i;
            this.warningTime = j;
            this.size = d4;
            this.sizeLerpTime = k;
            this.sizeLerpTarget = d5;
        }

        d(WorldBorder worldborder) {
            this.centerX = worldborder.getCenterX();
            this.centerZ = worldborder.getCenterZ();
            this.damagePerBlock = worldborder.getDamagePerBlock();
            this.safeZone = worldborder.getDamageSafeZone();
            this.warningBlocks = worldborder.getWarningBlocks();
            this.warningTime = worldborder.getWarningTime();
            this.size = worldborder.getSize();
            this.sizeLerpTime = worldborder.getLerpRemainingTime();
            this.sizeLerpTarget = worldborder.getLerpTarget();
        }

        public double getCenterX() {
            return this.centerX;
        }

        public double getCenterZ() {
            return this.centerZ;
        }

        public double getDamagePerBlock() {
            return this.damagePerBlock;
        }

        public double getSafeZone() {
            return this.safeZone;
        }

        public int getWarningBlocks() {
            return this.warningBlocks;
        }

        public int getWarningTime() {
            return this.warningTime;
        }

        public double getSize() {
            return this.size;
        }

        public long getSizeLerpTime() {
            return this.sizeLerpTime;
        }

        public double getSizeLerpTarget() {
            return this.sizeLerpTarget;
        }

        public static WorldBorder.d read(DynamicLike<?> dynamiclike, WorldBorder.d worldborder_d) {
            double d0 = MathHelper.clamp(dynamiclike.get("BorderCenterX").asDouble(worldborder_d.centerX), -2.9999984E7D, 2.9999984E7D);
            double d1 = MathHelper.clamp(dynamiclike.get("BorderCenterZ").asDouble(worldborder_d.centerZ), -2.9999984E7D, 2.9999984E7D);
            double d2 = dynamiclike.get("BorderSize").asDouble(worldborder_d.size);
            long i = dynamiclike.get("BorderSizeLerpTime").asLong(worldborder_d.sizeLerpTime);
            double d3 = dynamiclike.get("BorderSizeLerpTarget").asDouble(worldborder_d.sizeLerpTarget);
            double d4 = dynamiclike.get("BorderSafeZone").asDouble(worldborder_d.safeZone);
            double d5 = dynamiclike.get("BorderDamagePerBlock").asDouble(worldborder_d.damagePerBlock);
            int j = dynamiclike.get("BorderWarningBlocks").asInt(worldborder_d.warningBlocks);
            int k = dynamiclike.get("BorderWarningTime").asInt(worldborder_d.warningTime);

            return new WorldBorder.d(d0, d1, d5, d4, j, k, d2, i, d3);
        }

        public void write(NBTTagCompound nbttagcompound) {
            nbttagcompound.putDouble("BorderCenterX", this.centerX);
            nbttagcompound.putDouble("BorderCenterZ", this.centerZ);
            nbttagcompound.putDouble("BorderSize", this.size);
            nbttagcompound.putLong("BorderSizeLerpTime", this.sizeLerpTime);
            nbttagcompound.putDouble("BorderSafeZone", this.safeZone);
            nbttagcompound.putDouble("BorderDamagePerBlock", this.damagePerBlock);
            nbttagcompound.putDouble("BorderSizeLerpTarget", this.sizeLerpTarget);
            nbttagcompound.putDouble("BorderWarningBlocks", (double) this.warningBlocks);
            nbttagcompound.putDouble("BorderWarningTime", (double) this.warningTime);
        }
    }

    private interface a {

        double getMinX();

        double getMaxX();

        double getMinZ();

        double getMaxZ();

        double getSize();

        double getLerpSpeed();

        long getLerpRemainingTime();

        double getLerpTarget();

        BorderStatus getStatus();

        void onAbsoluteMaxSizeChange();

        void onCenterChange();

        WorldBorder.a update();

        VoxelShape getCollisionShape();
    }
}

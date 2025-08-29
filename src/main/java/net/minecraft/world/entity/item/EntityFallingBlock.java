package net.minecraft.world.entity.item;

import com.mojang.logging.LogUtils;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketListenerPlayOut;
import net.minecraft.network.protocol.game.PacketPlayOutBlockChange;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.EntityTrackerEntry;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsFluid;
import net.minecraft.util.MathHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.item.context.BlockActionContextDirectional;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.IMaterial;
import net.minecraft.world.level.RayTrace;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BlockAnvil;
import net.minecraft.world.level.block.BlockConcretePowder;
import net.minecraft.world.level.block.BlockFalling;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.material.FluidTypes;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.MovingObjectPosition;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.Vec3D;
import org.slf4j.Logger;

// CraftBukkit start;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityFallingBlock extends Entity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final IBlockData DEFAULT_BLOCK_STATE = Blocks.SAND.defaultBlockState();
    private static final int DEFAULT_TIME = 0;
    private static final float DEFAULT_FALL_DAMAGE_PER_DISTANCE = 0.0F;
    private static final int DEFAULT_MAX_FALL_DAMAGE = 40;
    private static final boolean DEFAULT_DROP_ITEM = true;
    private static final boolean DEFAULT_CANCEL_DROP = false;
    private IBlockData blockState;
    public int time;
    public boolean dropItem;
    public boolean cancelDrop;
    public boolean hurtEntities;
    public int fallDamageMax;
    public float fallDamagePerDistance;
    @Nullable
    public NBTTagCompound blockData;
    public boolean forceTickAfterTeleportToDuplicate;
    protected static final DataWatcherObject<BlockPosition> DATA_START_POS = DataWatcher.<BlockPosition>defineId(EntityFallingBlock.class, DataWatcherRegistry.BLOCK_POS);

    public EntityFallingBlock(EntityTypes<? extends EntityFallingBlock> entitytypes, World world) {
        super(entitytypes, world);
        this.blockState = EntityFallingBlock.DEFAULT_BLOCK_STATE;
        this.time = 0;
        this.dropItem = true;
        this.cancelDrop = false;
        this.fallDamageMax = 40;
        this.fallDamagePerDistance = 0.0F;
    }

    private EntityFallingBlock(World world, double d0, double d1, double d2, IBlockData iblockdata) {
        this(EntityTypes.FALLING_BLOCK, world);
        this.blockState = iblockdata;
        this.blocksBuilding = true;
        this.setPos(d0, d1, d2);
        this.setDeltaMovement(Vec3D.ZERO);
        this.xo = d0;
        this.yo = d1;
        this.zo = d2;
        this.setStartPos(this.blockPosition());
    }

    public static EntityFallingBlock fall(World world, BlockPosition blockposition, IBlockData iblockdata) {
        // CraftBukkit start
        return fall(world, blockposition, iblockdata, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    public static EntityFallingBlock fall(World world, BlockPosition blockposition, IBlockData iblockdata, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason spawnReason) {
        // CraftBukkit end
        EntityFallingBlock entityfallingblock = new EntityFallingBlock(world, (double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, iblockdata.hasProperty(BlockProperties.WATERLOGGED) ? (IBlockData) iblockdata.setValue(BlockProperties.WATERLOGGED, false) : iblockdata);
        if (!CraftEventFactory.callEntityChangeBlockEvent(entityfallingblock, blockposition, iblockdata.getFluidState().createLegacyBlock())) return entityfallingblock; // CraftBukkit

        world.setBlock(blockposition, iblockdata.getFluidState().createLegacyBlock(), 3);
        world.addFreshEntity(entityfallingblock, spawnReason); // CraftBukkit
        return entityfallingblock;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public final boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        if (!this.isInvulnerableToBase(damagesource)) {
            this.markHurt();
        }

        return false;
    }

    public void setStartPos(BlockPosition blockposition) {
        this.entityData.set(EntityFallingBlock.DATA_START_POS, blockposition);
    }

    public BlockPosition getStartPos() {
        return (BlockPosition) this.entityData.get(EntityFallingBlock.DATA_START_POS);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        datawatcher_a.define(EntityFallingBlock.DATA_START_POS, BlockPosition.ZERO);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04D;
    }

    @Override
    public void tick() {
        if (this.blockState.isAir()) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            Block block = this.blockState.getBlock();

            ++this.time;
            this.applyGravity();
            this.move(EnumMoveType.SELF, this.getDeltaMovement());
            this.applyEffectsFromBlocks();
            this.handlePortal();
            World world = this.level();

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                if (this.isAlive() || this.forceTickAfterTeleportToDuplicate) {
                    BlockPosition blockposition = this.blockPosition();
                    boolean flag = this.blockState.getBlock() instanceof BlockConcretePowder;
                    boolean flag1 = flag && this.level().getFluidState(blockposition).is(TagsFluid.WATER);
                    double d0 = this.getDeltaMovement().lengthSqr();

                    if (flag && d0 > 1.0D) {
                        MovingObjectPositionBlock movingobjectpositionblock = this.level().clip(new RayTrace(new Vec3D(this.xo, this.yo, this.zo), this.position(), RayTrace.BlockCollisionOption.COLLIDER, RayTrace.FluidCollisionOption.SOURCE_ONLY, this));

                        if (movingobjectpositionblock.getType() != MovingObjectPosition.EnumMovingObjectType.MISS && this.level().getFluidState(movingobjectpositionblock.getBlockPos()).is(TagsFluid.WATER)) {
                            blockposition = movingobjectpositionblock.getBlockPos();
                            flag1 = true;
                        }
                    }

                    if (!this.onGround() && !flag1) {
                        if (this.time > 100 && (blockposition.getY() <= this.level().getMinY() || blockposition.getY() > this.level().getMaxY()) || this.time > 600) {
                            if (this.dropItem && worldserver.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                                this.spawnAtLocation(worldserver, (IMaterial) block);
                            }

                            this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                        }
                    } else {
                        IBlockData iblockdata = this.level().getBlockState(blockposition);

                        this.setDeltaMovement(this.getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
                        if (!iblockdata.is(Blocks.MOVING_PISTON)) {
                            if (!this.cancelDrop) {
                                boolean flag2 = iblockdata.canBeReplaced((BlockActionContext) (new BlockActionContextDirectional(this.level(), blockposition, EnumDirection.DOWN, ItemStack.EMPTY, EnumDirection.UP)));
                                boolean flag3 = BlockFalling.isFree(this.level().getBlockState(blockposition.below())) && (!flag || !flag1);
                                boolean flag4 = this.blockState.canSurvive(this.level(), blockposition) && !flag3;

                                if (flag2 && flag4) {
                                    if (this.blockState.hasProperty(BlockProperties.WATERLOGGED) && this.level().getFluidState(blockposition).getType() == FluidTypes.WATER) {
                                        this.blockState = (IBlockData) this.blockState.setValue(BlockProperties.WATERLOGGED, true);
                                    }

                                    // CraftBukkit start
                                    if (!CraftEventFactory.callEntityChangeBlockEvent(this, blockposition, this.blockState)) {
                                        this.discard(EntityRemoveEvent.Cause.DESPAWN); // SPIGOT-6586 called before the event in previous versions
                                        return;
                                    }
                                    // CraftBukkit end
                                    if (this.level().setBlock(blockposition, this.blockState, 3)) {
                                        ((WorldServer) this.level()).getChunkSource().chunkMap.broadcast(this, new PacketPlayOutBlockChange(blockposition, this.level().getBlockState(blockposition)));
                                        this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                                        if (block instanceof Fallable) {
                                            ((Fallable) block).onLand(this.level(), blockposition, this.blockState, iblockdata, this);
                                        }

                                        if (this.blockData != null && this.blockState.hasBlockEntity()) {
                                            TileEntity tileentity = this.level().getBlockEntity(blockposition);

                                            if (tileentity != null) {
                                                NBTTagCompound nbttagcompound = tileentity.saveWithoutMetadata(this.level().registryAccess());

                                                this.blockData.forEach((s, nbtbase) -> {
                                                    nbttagcompound.put(s, nbtbase.copy());
                                                });

                                                try {
                                                    tileentity.loadWithComponents(nbttagcompound, this.level().registryAccess());
                                                } catch (Exception exception) {
                                                    EntityFallingBlock.LOGGER.error("Failed to load block entity from falling block", exception);
                                                }

                                                tileentity.setChanged();
                                            }
                                        }
                                    } else if (this.dropItem && worldserver.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                                        this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                                        this.callOnBrokenAfterFall(block, blockposition);
                                        this.spawnAtLocation(worldserver, (IMaterial) block);
                                    }
                                } else {
                                    this.discard(EntityRemoveEvent.Cause.DROP); // CraftBukkit - add Bukkit remove cause
                                    if (this.dropItem && worldserver.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
                                        this.callOnBrokenAfterFall(block, blockposition);
                                        this.spawnAtLocation(worldserver, (IMaterial) block);
                                    }
                                }
                            } else {
                                this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                                this.callOnBrokenAfterFall(block, blockposition);
                            }
                        }
                    }
                }
            }

            this.setDeltaMovement(this.getDeltaMovement().scale(0.98D));
        }
    }

    public void callOnBrokenAfterFall(Block block, BlockPosition blockposition) {
        if (block instanceof Fallable) {
            ((Fallable) block).onBrokenAfterFall(this.level(), blockposition, this);
        }

    }

    @Override
    public boolean causeFallDamage(double d0, float f, DamageSource damagesource) {
        if (!this.hurtEntities) {
            return false;
        } else {
            int i = MathHelper.ceil(d0 - 1.0D);

            if (i < 0) {
                return false;
            } else {
                Predicate<Entity> predicate = IEntitySelector.NO_CREATIVE_OR_SPECTATOR.and(IEntitySelector.LIVING_ENTITY_STILL_ALIVE);
                Block block = this.blockState.getBlock();
                DamageSource damagesource1;

                if (block instanceof Fallable) {
                    Fallable fallable = (Fallable) block;

                    damagesource1 = fallable.getFallDamageSource(this);
                } else {
                    damagesource1 = this.damageSources().fallingBlock(this);
                }

                DamageSource damagesource2 = damagesource1;
                float f1 = (float) Math.min(MathHelper.floor((float) i * this.fallDamagePerDistance), this.fallDamageMax);

                this.level().getEntities(this, this.getBoundingBox(), predicate).forEach((entity) -> {
                    entity.hurt(damagesource2, f1);
                });
                boolean flag = this.blockState.is(TagsBlock.ANVIL);

                if (flag && f1 > 0.0F && this.random.nextFloat() < 0.05F + (float) i * 0.05F) {
                    IBlockData iblockdata = BlockAnvil.damage(this.blockState);

                    if (iblockdata == null) {
                        this.cancelDrop = true;
                    } else {
                        this.blockState = iblockdata;
                    }
                }

                return false;
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

        nbttagcompound.store("BlockState", IBlockData.CODEC, registryops, this.blockState);
        nbttagcompound.putInt("Time", this.time);
        nbttagcompound.putBoolean("DropItem", this.dropItem);
        nbttagcompound.putBoolean("HurtEntities", this.hurtEntities);
        nbttagcompound.putFloat("FallHurtAmount", this.fallDamagePerDistance);
        nbttagcompound.putInt("FallHurtMax", this.fallDamageMax);
        if (this.blockData != null) {
            nbttagcompound.put("TileEntityData", this.blockData);
        }

        nbttagcompound.putBoolean("CancelDrop", this.cancelDrop);
    }

    @Override
    protected void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

        this.blockState = (IBlockData) nbttagcompound.read("BlockState", IBlockData.CODEC, registryops).orElse(EntityFallingBlock.DEFAULT_BLOCK_STATE);
        this.time = nbttagcompound.getIntOr("Time", 0);
        boolean flag = this.blockState.is(TagsBlock.ANVIL);

        this.hurtEntities = nbttagcompound.getBooleanOr("HurtEntities", flag);
        this.fallDamagePerDistance = nbttagcompound.getFloatOr("FallHurtAmount", 0.0F);
        this.fallDamageMax = nbttagcompound.getIntOr("FallHurtMax", 40);
        this.dropItem = nbttagcompound.getBooleanOr("DropItem", true);
        this.blockData = (NBTTagCompound) nbttagcompound.getCompound("TileEntityData").map(NBTTagCompound::copy).orElse(null); // CraftBukkit - decompile error
        this.cancelDrop = nbttagcompound.getBooleanOr("CancelDrop", false);
    }

    public void setHurtsEntities(float f, int i) {
        this.hurtEntities = true;
        this.fallDamagePerDistance = f;
        this.fallDamageMax = i;
    }

    public void disableDrop() {
        this.cancelDrop = true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public void fillCrashReportCategory(CrashReportSystemDetails crashreportsystemdetails) {
        super.fillCrashReportCategory(crashreportsystemdetails);
        crashreportsystemdetails.setDetail("Immitating BlockState", this.blockState.toString());
    }

    public IBlockData getBlockState() {
        return this.blockState;
    }

    @Override
    protected IChatBaseComponent getTypeName() {
        return IChatBaseComponent.translatable("entity.minecraft.falling_block_type", this.blockState.getBlock().getName());
    }

    @Override
    public Packet<PacketListenerPlayOut> getAddEntityPacket(EntityTrackerEntry entitytrackerentry) {
        return new PacketPlayOutSpawnEntity(this, entitytrackerentry, Block.getId(this.getBlockState()));
    }

    @Override
    public void recreateFromPacket(PacketPlayOutSpawnEntity packetplayoutspawnentity) {
        super.recreateFromPacket(packetplayoutspawnentity);
        this.blockState = Block.stateById(packetplayoutspawnentity.getData());
        this.blocksBuilding = true;
        double d0 = packetplayoutspawnentity.getX();
        double d1 = packetplayoutspawnentity.getY();
        double d2 = packetplayoutspawnentity.getZ();

        this.setPos(d0, d1, d2);
        this.setStartPos(this.blockPosition());
    }

    @Nullable
    @Override
    public Entity teleport(TeleportTransition teleporttransition) {
        ResourceKey<World> resourcekey = teleporttransition.newLevel().dimension();
        ResourceKey<World> resourcekey1 = this.level().dimension();
        boolean flag = (resourcekey1 == World.END || resourcekey == World.END) && resourcekey1 != resourcekey;
        Entity entity = super.teleport(teleporttransition);

        this.forceTickAfterTeleportToDuplicate = entity != null && flag;
        return entity;
    }
}

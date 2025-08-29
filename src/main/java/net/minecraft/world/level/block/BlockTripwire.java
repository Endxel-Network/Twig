package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockStateBoolean;
import net.minecraft.world.level.block.state.properties.IBlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;

import org.bukkit.event.entity.EntityInteractEvent; // CraftBukkit

public class BlockTripwire extends Block {

    public static final MapCodec<BlockTripwire> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("hook").forGetter((blocktripwire) -> {
            return blocktripwire.hook;
        }), propertiesCodec()).apply(instance, BlockTripwire::new);
    });
    public static final BlockStateBoolean POWERED = BlockProperties.POWERED;
    public static final BlockStateBoolean ATTACHED = BlockProperties.ATTACHED;
    public static final BlockStateBoolean DISARMED = BlockProperties.DISARMED;
    public static final BlockStateBoolean NORTH = BlockSprawling.NORTH;
    public static final BlockStateBoolean EAST = BlockSprawling.EAST;
    public static final BlockStateBoolean SOUTH = BlockSprawling.SOUTH;
    public static final BlockStateBoolean WEST = BlockSprawling.WEST;
    private static final Map<EnumDirection, BlockStateBoolean> PROPERTY_BY_DIRECTION = BlockTall.PROPERTY_BY_DIRECTION;
    private static final VoxelShape SHAPE_ATTACHED = Block.column(16.0D, 1.0D, 2.5D);
    private static final VoxelShape SHAPE_NOT_ATTACHED = Block.column(16.0D, 0.0D, 8.0D);
    private static final int RECHECK_PERIOD = 10;
    private final Block hook;

    @Override
    public MapCodec<BlockTripwire> codec() {
        return BlockTripwire.CODEC;
    }

    public BlockTripwire(Block block, BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) (this.stateDefinition.any()).setValue(BlockTripwire.POWERED, false)).setValue(BlockTripwire.ATTACHED, false)).setValue(BlockTripwire.DISARMED, false)).setValue(BlockTripwire.NORTH, false)).setValue(BlockTripwire.EAST, false)).setValue(BlockTripwire.SOUTH, false)).setValue(BlockTripwire.WEST, false));
        this.hook = block;
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return (Boolean) iblockdata.getValue(BlockTripwire.ATTACHED) ? BlockTripwire.SHAPE_ATTACHED : BlockTripwire.SHAPE_NOT_ATTACHED;
    }

    @Override
    public IBlockData getStateForPlacement(BlockActionContext blockactioncontext) {
        IBlockAccess iblockaccess = blockactioncontext.getLevel();
        BlockPosition blockposition = blockactioncontext.getClickedPos();

        return (IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) this.defaultBlockState().setValue(BlockTripwire.NORTH, this.shouldConnectTo(iblockaccess.getBlockState(blockposition.north()), EnumDirection.NORTH))).setValue(BlockTripwire.EAST, this.shouldConnectTo(iblockaccess.getBlockState(blockposition.east()), EnumDirection.EAST))).setValue(BlockTripwire.SOUTH, this.shouldConnectTo(iblockaccess.getBlockState(blockposition.south()), EnumDirection.SOUTH))).setValue(BlockTripwire.WEST, this.shouldConnectTo(iblockaccess.getBlockState(blockposition.west()), EnumDirection.WEST));
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        return enumdirection.getAxis().isHorizontal() ? (IBlockData) iblockdata.setValue((IBlockState) BlockTripwire.PROPERTY_BY_DIRECTION.get(enumdirection), this.shouldConnectTo(iblockdata1, enumdirection)) : super.updateShape(iblockdata, iworldreader, scheduledtickaccess, blockposition, enumdirection, blockposition1, iblockdata1, randomsource);
    }

    @Override
    protected void onPlace(IBlockData iblockdata, World world, BlockPosition blockposition, IBlockData iblockdata1, boolean flag) {
        if (!iblockdata1.is(iblockdata.getBlock())) {
            this.updateSource(world, blockposition, iblockdata);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, boolean flag) {
        if (!flag) {
            this.updateSource(worldserver, blockposition, (IBlockData) iblockdata.setValue(BlockTripwire.POWERED, true));
        }

    }

    @Override
    public IBlockData playerWillDestroy(World world, BlockPosition blockposition, IBlockData iblockdata, EntityHuman entityhuman) {
        if (!world.isClientSide && !entityhuman.getMainHandItem().isEmpty() && entityhuman.getMainHandItem().is(Items.SHEARS)) {
            world.setBlock(blockposition, (IBlockData) iblockdata.setValue(BlockTripwire.DISARMED, true), 260);
            world.gameEvent(entityhuman, (Holder) GameEvent.SHEAR, blockposition);
        }

        return super.playerWillDestroy(world, blockposition, iblockdata, entityhuman);
    }

    private void updateSource(World world, BlockPosition blockposition, IBlockData iblockdata) {
        for (EnumDirection enumdirection : new EnumDirection[]{EnumDirection.SOUTH, EnumDirection.WEST}) {
            for (int i = 1; i < 42; ++i) {
                BlockPosition blockposition1 = blockposition.relative(enumdirection, i);
                IBlockData iblockdata1 = world.getBlockState(blockposition1);

                if (iblockdata1.is(this.hook)) {
                    if (iblockdata1.getValue(BlockTripwireHook.FACING) == enumdirection.getOpposite()) {
                        BlockTripwireHook.calculateState(world, blockposition1, iblockdata1, false, true, i, iblockdata);
                    }
                    break;
                }

                if (!iblockdata1.is(this)) {
                    break;
                }
            }
        }

    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, Entity entity) {
        return iblockdata.getShape(iblockaccess, blockposition);
    }

    @Override
    protected void entityInside(IBlockData iblockdata, World world, BlockPosition blockposition, Entity entity, InsideBlockEffectApplier insideblockeffectapplier) {
        if (!world.isClientSide) {
            if (!(Boolean) iblockdata.getValue(BlockTripwire.POWERED)) {
                this.checkPressed(world, blockposition, List.of(entity));
            }
        }
    }

    @Override
    protected void tick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        if ((Boolean) worldserver.getBlockState(blockposition).getValue(BlockTripwire.POWERED)) {
            this.checkPressed(worldserver, blockposition);
        }
    }

    private void checkPressed(World world, BlockPosition blockposition) {
        IBlockData iblockdata = world.getBlockState(blockposition);
        List<? extends Entity> list = world.getEntities((Entity) null, iblockdata.getShape(world, blockposition).bounds().move(blockposition));

        this.checkPressed(world, blockposition, list);
    }

    private void checkPressed(World world, BlockPosition blockposition, List<? extends Entity> list) {
        IBlockData iblockdata = world.getBlockState(blockposition);
        boolean flag = (Boolean) iblockdata.getValue(BlockTripwire.POWERED);
        boolean flag1 = false;

        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (!entity.isIgnoringBlockTriggers()) {
                    flag1 = true;
                    break;
                }
            }
        }

        // CraftBukkit start - Call interact even when triggering connected tripwire
        if (flag != flag1 && flag1 && (Boolean)iblockdata.getValue(ATTACHED)) {
            org.bukkit.World bworld = world.getWorld();
            org.bukkit.plugin.PluginManager manager = world.getCraftServer().getPluginManager();
            org.bukkit.block.Block block = bworld.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
            boolean allowed = false;

            // If all of the events are cancelled block the tripwire trigger, else allow
            for (Object object : list) {
                if (object != null) {
                    org.bukkit.event.Cancellable cancellable;

                    if (object instanceof EntityHuman) {
                        cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent((EntityHuman) object, org.bukkit.event.block.Action.PHYSICAL, blockposition, null, null, null);
                    } else if (object instanceof Entity) {
                        cancellable = new EntityInteractEvent(((Entity) object).getBukkitEntity(), block);
                        manager.callEvent((EntityInteractEvent) cancellable);
                    } else {
                        continue;
                    }

                    if (!cancellable.isCancelled()) {
                        allowed = true;
                        break;
                    }
                }
            }

            if (!allowed) {
                return;
            }
        }
        // CraftBukkit end

        if (flag1 != flag) {
            iblockdata = (IBlockData) iblockdata.setValue(BlockTripwire.POWERED, flag1);
            world.setBlock(blockposition, iblockdata, 3);
            this.updateSource(world, blockposition, iblockdata);
        }

        if (flag1) {
            world.scheduleTick(new BlockPosition(blockposition), (Block) this, 10);
        }

    }

    public boolean shouldConnectTo(IBlockData iblockdata, EnumDirection enumdirection) {
        return iblockdata.is(this.hook) ? iblockdata.getValue(BlockTripwireHook.FACING) == enumdirection.getOpposite() : iblockdata.is(this);
    }

    @Override
    protected IBlockData rotate(IBlockData iblockdata, EnumBlockRotation enumblockrotation) {
        switch (enumblockrotation) {
            case CLOCKWISE_180:
                return (IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) iblockdata.setValue(BlockTripwire.NORTH, (Boolean) iblockdata.getValue(BlockTripwire.SOUTH))).setValue(BlockTripwire.EAST, (Boolean) iblockdata.getValue(BlockTripwire.WEST))).setValue(BlockTripwire.SOUTH, (Boolean) iblockdata.getValue(BlockTripwire.NORTH))).setValue(BlockTripwire.WEST, (Boolean) iblockdata.getValue(BlockTripwire.EAST));
            case COUNTERCLOCKWISE_90:
                return (IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) iblockdata.setValue(BlockTripwire.NORTH, (Boolean) iblockdata.getValue(BlockTripwire.EAST))).setValue(BlockTripwire.EAST, (Boolean) iblockdata.getValue(BlockTripwire.SOUTH))).setValue(BlockTripwire.SOUTH, (Boolean) iblockdata.getValue(BlockTripwire.WEST))).setValue(BlockTripwire.WEST, (Boolean) iblockdata.getValue(BlockTripwire.NORTH));
            case CLOCKWISE_90:
                return (IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) iblockdata.setValue(BlockTripwire.NORTH, (Boolean) iblockdata.getValue(BlockTripwire.WEST))).setValue(BlockTripwire.EAST, (Boolean) iblockdata.getValue(BlockTripwire.NORTH))).setValue(BlockTripwire.SOUTH, (Boolean) iblockdata.getValue(BlockTripwire.EAST))).setValue(BlockTripwire.WEST, (Boolean) iblockdata.getValue(BlockTripwire.SOUTH));
            default:
                return iblockdata;
        }
    }

    @Override
    protected IBlockData mirror(IBlockData iblockdata, EnumBlockMirror enumblockmirror) {
        switch (enumblockmirror) {
            case LEFT_RIGHT:
                return (IBlockData) ((IBlockData) iblockdata.setValue(BlockTripwire.NORTH, (Boolean) iblockdata.getValue(BlockTripwire.SOUTH))).setValue(BlockTripwire.SOUTH, (Boolean) iblockdata.getValue(BlockTripwire.NORTH));
            case FRONT_BACK:
                return (IBlockData) ((IBlockData) iblockdata.setValue(BlockTripwire.EAST, (Boolean) iblockdata.getValue(BlockTripwire.WEST))).setValue(BlockTripwire.WEST, (Boolean) iblockdata.getValue(BlockTripwire.EAST));
            default:
                return super.mirror(iblockdata, enumblockmirror);
        }
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockTripwire.POWERED, BlockTripwire.ATTACHED, BlockTripwire.DISARMED, BlockTripwire.NORTH, BlockTripwire.EAST, BlockTripwire.WEST, BlockTripwire.SOUTH);
    }
}

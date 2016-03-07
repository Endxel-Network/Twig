package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockStateBoolean;
import net.minecraft.world.level.block.state.properties.IBlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;
import net.minecraft.world.phys.shapes.VoxelShapes;

import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class BlockVine extends Block {

    public static final MapCodec<BlockVine> CODEC = simpleCodec(BlockVine::new);
    public static final BlockStateBoolean UP = BlockSprawling.UP;
    public static final BlockStateBoolean NORTH = BlockSprawling.NORTH;
    public static final BlockStateBoolean EAST = BlockSprawling.EAST;
    public static final BlockStateBoolean SOUTH = BlockSprawling.SOUTH;
    public static final BlockStateBoolean WEST = BlockSprawling.WEST;
    public static final Map<EnumDirection, BlockStateBoolean> PROPERTY_BY_DIRECTION = (Map) BlockSprawling.PROPERTY_BY_DIRECTION.entrySet().stream().filter((entry) -> {
        return entry.getKey() != EnumDirection.DOWN;
    }).collect(SystemUtils.toMap());
    private final Function<IBlockData, VoxelShape> shapes;

    @Override
    public MapCodec<BlockVine> codec() {
        return BlockVine.CODEC;
    }

    public BlockVine(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) (this.stateDefinition.any()).setValue(BlockVine.UP, false)).setValue(BlockVine.NORTH, false)).setValue(BlockVine.EAST, false)).setValue(BlockVine.SOUTH, false)).setValue(BlockVine.WEST, false));
        this.shapes = this.makeShapes();
    }

    private Function<IBlockData, VoxelShape> makeShapes() {
        Map<EnumDirection, VoxelShape> map = VoxelShapes.rotateAll(Block.boxZ(16.0D, 0.0D, 1.0D));

        return this.getShapeForEachState((iblockdata) -> {
            VoxelShape voxelshape = VoxelShapes.empty();

            for (Map.Entry<EnumDirection, BlockStateBoolean> map_entry : BlockVine.PROPERTY_BY_DIRECTION.entrySet()) {
                if ((Boolean) iblockdata.getValue((IBlockState) map_entry.getValue())) {
                    voxelshape = VoxelShapes.or(voxelshape, (VoxelShape) map.get(map_entry.getKey()));
                }
            }

            return voxelshape.isEmpty() ? VoxelShapes.block() : voxelshape;
        });
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return (VoxelShape) this.shapes.apply(iblockdata);
    }

    @Override
    protected boolean propagatesSkylightDown(IBlockData iblockdata) {
        return true;
    }

    @Override
    protected boolean canSurvive(IBlockData iblockdata, IWorldReader iworldreader, BlockPosition blockposition) {
        return this.hasFaces(this.getUpdatedState(iblockdata, iworldreader, blockposition));
    }

    private boolean hasFaces(IBlockData iblockdata) {
        return this.countFaces(iblockdata) > 0;
    }

    private int countFaces(IBlockData iblockdata) {
        int i = 0;

        for (BlockStateBoolean blockstateboolean : BlockVine.PROPERTY_BY_DIRECTION.values()) {
            if ((Boolean) iblockdata.getValue(blockstateboolean)) {
                ++i;
            }
        }

        return i;
    }

    private boolean canSupportAtFace(IBlockAccess iblockaccess, BlockPosition blockposition, EnumDirection enumdirection) {
        if (enumdirection == EnumDirection.DOWN) {
            return false;
        } else {
            BlockPosition blockposition1 = blockposition.relative(enumdirection);

            if (isAcceptableNeighbour(iblockaccess, blockposition1, enumdirection)) {
                return true;
            } else if (enumdirection.getAxis() == EnumDirection.EnumAxis.Y) {
                return false;
            } else {
                BlockStateBoolean blockstateboolean = (BlockStateBoolean) BlockVine.PROPERTY_BY_DIRECTION.get(enumdirection);
                IBlockData iblockdata = iblockaccess.getBlockState(blockposition.above());

                return iblockdata.is(this) && (Boolean) iblockdata.getValue(blockstateboolean);
            }
        }
    }

    public static boolean isAcceptableNeighbour(IBlockAccess iblockaccess, BlockPosition blockposition, EnumDirection enumdirection) {
        return MultifaceBlock.canAttachTo(iblockaccess, enumdirection, blockposition, iblockaccess.getBlockState(blockposition));
    }

    private IBlockData getUpdatedState(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition) {
        BlockPosition blockposition1 = blockposition.above();

        if ((Boolean) iblockdata.getValue(BlockVine.UP)) {
            iblockdata = (IBlockData) iblockdata.setValue(BlockVine.UP, isAcceptableNeighbour(iblockaccess, blockposition1, EnumDirection.DOWN));
        }

        IBlockData iblockdata1 = null;

        for (EnumDirection enumdirection : EnumDirection.EnumDirectionLimit.HORIZONTAL) {
            BlockStateBoolean blockstateboolean = getPropertyForFace(enumdirection);

            if ((Boolean) iblockdata.getValue(blockstateboolean)) {
                boolean flag = this.canSupportAtFace(iblockaccess, blockposition, enumdirection);

                if (!flag) {
                    if (iblockdata1 == null) {
                        iblockdata1 = iblockaccess.getBlockState(blockposition1);
                    }

                    flag = iblockdata1.is(this) && (Boolean) iblockdata1.getValue(blockstateboolean);
                }

                iblockdata = (IBlockData) iblockdata.setValue(blockstateboolean, flag);
            }
        }

        return iblockdata;
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        if (enumdirection == EnumDirection.DOWN) {
            return super.updateShape(iblockdata, iworldreader, scheduledtickaccess, blockposition, enumdirection, blockposition1, iblockdata1, randomsource);
        } else {
            IBlockData iblockdata2 = this.getUpdatedState(iblockdata, iworldreader, blockposition);

            return !this.hasFaces(iblockdata2) ? Blocks.AIR.defaultBlockState() : iblockdata2;
        }
    }

    @Override
    protected void randomTick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        if (worldserver.getGameRules().getBoolean(GameRules.RULE_DO_VINES_SPREAD)) {
            if (randomsource.nextFloat() < (worldserver.spigotConfig.vineModifier / (100.0f * 4))) { // Spigot - SPIGOT-7159: Better modifier resolution
                EnumDirection enumdirection = EnumDirection.getRandom(randomsource);
                BlockPosition blockposition1 = blockposition.above();

                if (enumdirection.getAxis().isHorizontal() && !(Boolean) iblockdata.getValue(getPropertyForFace(enumdirection))) {
                    if (this.canSpread(worldserver, blockposition)) {
                        BlockPosition blockposition2 = blockposition.relative(enumdirection);
                        IBlockData iblockdata1 = worldserver.getBlockState(blockposition2);

                        if (iblockdata1.isAir()) {
                            EnumDirection enumdirection1 = enumdirection.getClockWise();
                            EnumDirection enumdirection2 = enumdirection.getCounterClockWise();
                            boolean flag = (Boolean) iblockdata.getValue(getPropertyForFace(enumdirection1));
                            boolean flag1 = (Boolean) iblockdata.getValue(getPropertyForFace(enumdirection2));
                            BlockPosition blockposition3 = blockposition2.relative(enumdirection1);
                            BlockPosition blockposition4 = blockposition2.relative(enumdirection2);

                            // CraftBukkit start - Call BlockSpreadEvent
                            BlockPosition source = blockposition;

                            if (flag && isAcceptableNeighbour(worldserver, blockposition3, enumdirection1)) {
                                CraftEventFactory.handleBlockSpreadEvent(worldserver, source, blockposition2, (IBlockData) this.defaultBlockState().setValue(getPropertyForFace(enumdirection1), true), 2);
                            } else if (flag1 && isAcceptableNeighbour(worldserver, blockposition4, enumdirection2)) {
                                CraftEventFactory.handleBlockSpreadEvent(worldserver, source, blockposition2, (IBlockData) this.defaultBlockState().setValue(getPropertyForFace(enumdirection2), true), 2);
                            } else {
                                EnumDirection enumdirection3 = enumdirection.getOpposite();

                                if (flag && worldserver.isEmptyBlock(blockposition3) && isAcceptableNeighbour(worldserver, blockposition.relative(enumdirection1), enumdirection3)) {
                                    CraftEventFactory.handleBlockSpreadEvent(worldserver, source, blockposition3, (IBlockData) this.defaultBlockState().setValue(getPropertyForFace(enumdirection3), true), 2);
                                } else if (flag1 && worldserver.isEmptyBlock(blockposition4) && isAcceptableNeighbour(worldserver, blockposition.relative(enumdirection2), enumdirection3)) {
                                    CraftEventFactory.handleBlockSpreadEvent(worldserver, source, blockposition4, (IBlockData) this.defaultBlockState().setValue(getPropertyForFace(enumdirection3), true), 2);
                                } else if ((double) randomsource.nextFloat() < 0.05D && isAcceptableNeighbour(worldserver, blockposition2.above(), EnumDirection.UP)) {
                                    CraftEventFactory.handleBlockSpreadEvent(worldserver, source, blockposition2, (IBlockData) this.defaultBlockState().setValue(BlockVine.UP, true), 2);
                                }
                                // CraftBukkit end
                            }
                        } else if (isAcceptableNeighbour(worldserver, blockposition2, enumdirection)) {
                            CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, (IBlockData) iblockdata.setValue(getPropertyForFace(enumdirection), true), 2); // CraftBukkit
                        }

                    }
                } else {
                    if (enumdirection == EnumDirection.UP && blockposition.getY() < worldserver.getMaxY()) {
                        if (this.canSupportAtFace(worldserver, blockposition, enumdirection)) {
                            CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, (IBlockData) iblockdata.setValue(BlockVine.UP, true), 2); // CraftBukkit
                            return;
                        }

                        if (worldserver.isEmptyBlock(blockposition1)) {
                            if (!this.canSpread(worldserver, blockposition)) {
                                return;
                            }

                            IBlockData iblockdata2 = iblockdata;

                            for (EnumDirection enumdirection4 : EnumDirection.EnumDirectionLimit.HORIZONTAL) {
                                if (randomsource.nextBoolean() || !isAcceptableNeighbour(worldserver, blockposition1.relative(enumdirection4), enumdirection4)) {
                                    iblockdata2 = (IBlockData) iblockdata2.setValue(getPropertyForFace(enumdirection4), false);
                                }
                            }

                            if (this.hasHorizontalConnection(iblockdata2)) {
                                CraftEventFactory.handleBlockSpreadEvent(worldserver, blockposition, blockposition1, iblockdata2, 2); // CraftBukkit
                            }

                            return;
                        }
                    }

                    if (blockposition.getY() > worldserver.getMinY()) {
                        BlockPosition blockposition5 = blockposition.below();
                        IBlockData iblockdata3 = worldserver.getBlockState(blockposition5);

                        if (iblockdata3.isAir() || iblockdata3.is(this)) {
                            IBlockData iblockdata4 = iblockdata3.isAir() ? this.defaultBlockState() : iblockdata3;
                            IBlockData iblockdata5 = this.copyRandomFaces(iblockdata, iblockdata4, randomsource);

                            if (iblockdata4 != iblockdata5 && this.hasHorizontalConnection(iblockdata5)) {
                                CraftEventFactory.handleBlockSpreadEvent(worldserver, blockposition, blockposition5, iblockdata5, 2); // CraftBukkit
                            }
                        }
                    }

                }
            }
        }
    }

    private IBlockData copyRandomFaces(IBlockData iblockdata, IBlockData iblockdata1, RandomSource randomsource) {
        for (EnumDirection enumdirection : EnumDirection.EnumDirectionLimit.HORIZONTAL) {
            if (randomsource.nextBoolean()) {
                BlockStateBoolean blockstateboolean = getPropertyForFace(enumdirection);

                if ((Boolean) iblockdata.getValue(blockstateboolean)) {
                    iblockdata1 = (IBlockData) iblockdata1.setValue(blockstateboolean, true);
                }
            }
        }

        return iblockdata1;
    }

    private boolean hasHorizontalConnection(IBlockData iblockdata) {
        return (Boolean) iblockdata.getValue(BlockVine.NORTH) || (Boolean) iblockdata.getValue(BlockVine.EAST) || (Boolean) iblockdata.getValue(BlockVine.SOUTH) || (Boolean) iblockdata.getValue(BlockVine.WEST);
    }

    private boolean canSpread(IBlockAccess iblockaccess, BlockPosition blockposition) {
        int i = 4;
        Iterable<BlockPosition> iterable = BlockPosition.betweenClosed(blockposition.getX() - 4, blockposition.getY() - 1, blockposition.getZ() - 4, blockposition.getX() + 4, blockposition.getY() + 1, blockposition.getZ() + 4);
        int j = 5;

        for (BlockPosition blockposition1 : iterable) {
            if (iblockaccess.getBlockState(blockposition1).is(this)) {
                --j;
                if (j <= 0) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected boolean canBeReplaced(IBlockData iblockdata, BlockActionContext blockactioncontext) {
        IBlockData iblockdata1 = blockactioncontext.getLevel().getBlockState(blockactioncontext.getClickedPos());

        return iblockdata1.is(this) ? this.countFaces(iblockdata1) < BlockVine.PROPERTY_BY_DIRECTION.size() : super.canBeReplaced(iblockdata, blockactioncontext);
    }

    @Nullable
    @Override
    public IBlockData getStateForPlacement(BlockActionContext blockactioncontext) {
        IBlockData iblockdata = blockactioncontext.getLevel().getBlockState(blockactioncontext.getClickedPos());
        boolean flag = iblockdata.is(this);
        IBlockData iblockdata1 = flag ? iblockdata : this.defaultBlockState();

        for (EnumDirection enumdirection : blockactioncontext.getNearestLookingDirections()) {
            if (enumdirection != EnumDirection.DOWN) {
                BlockStateBoolean blockstateboolean = getPropertyForFace(enumdirection);
                boolean flag1 = flag && (Boolean) iblockdata.getValue(blockstateboolean);

                if (!flag1 && this.canSupportAtFace(blockactioncontext.getLevel(), blockactioncontext.getClickedPos(), enumdirection)) {
                    return (IBlockData) iblockdata1.setValue(blockstateboolean, true);
                }
            }
        }

        return flag ? iblockdata1 : null;
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockVine.UP, BlockVine.NORTH, BlockVine.EAST, BlockVine.SOUTH, BlockVine.WEST);
    }

    @Override
    protected IBlockData rotate(IBlockData iblockdata, EnumBlockRotation enumblockrotation) {
        switch (enumblockrotation) {
            case CLOCKWISE_180:
                return (IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) iblockdata.setValue(BlockVine.NORTH, (Boolean) iblockdata.getValue(BlockVine.SOUTH))).setValue(BlockVine.EAST, (Boolean) iblockdata.getValue(BlockVine.WEST))).setValue(BlockVine.SOUTH, (Boolean) iblockdata.getValue(BlockVine.NORTH))).setValue(BlockVine.WEST, (Boolean) iblockdata.getValue(BlockVine.EAST));
            case COUNTERCLOCKWISE_90:
                return (IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) iblockdata.setValue(BlockVine.NORTH, (Boolean) iblockdata.getValue(BlockVine.EAST))).setValue(BlockVine.EAST, (Boolean) iblockdata.getValue(BlockVine.SOUTH))).setValue(BlockVine.SOUTH, (Boolean) iblockdata.getValue(BlockVine.WEST))).setValue(BlockVine.WEST, (Boolean) iblockdata.getValue(BlockVine.NORTH));
            case CLOCKWISE_90:
                return (IBlockData) ((IBlockData) ((IBlockData) ((IBlockData) iblockdata.setValue(BlockVine.NORTH, (Boolean) iblockdata.getValue(BlockVine.WEST))).setValue(BlockVine.EAST, (Boolean) iblockdata.getValue(BlockVine.NORTH))).setValue(BlockVine.SOUTH, (Boolean) iblockdata.getValue(BlockVine.EAST))).setValue(BlockVine.WEST, (Boolean) iblockdata.getValue(BlockVine.SOUTH));
            default:
                return iblockdata;
        }
    }

    @Override
    protected IBlockData mirror(IBlockData iblockdata, EnumBlockMirror enumblockmirror) {
        switch (enumblockmirror) {
            case LEFT_RIGHT:
                return (IBlockData) ((IBlockData) iblockdata.setValue(BlockVine.NORTH, (Boolean) iblockdata.getValue(BlockVine.SOUTH))).setValue(BlockVine.SOUTH, (Boolean) iblockdata.getValue(BlockVine.NORTH));
            case FRONT_BACK:
                return (IBlockData) ((IBlockData) iblockdata.setValue(BlockVine.EAST, (Boolean) iblockdata.getValue(BlockVine.WEST))).setValue(BlockVine.WEST, (Boolean) iblockdata.getValue(BlockVine.EAST));
            default:
                return super.mirror(iblockdata, enumblockmirror);
        }
    }

    public static BlockStateBoolean getPropertyForFace(EnumDirection enumdirection) {
        return (BlockStateBoolean) BlockVine.PROPERTY_BY_DIRECTION.get(enumdirection);
    }
}

package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.RandomSource;
import net.minecraft.world.EnumHand;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.entity.TileEntitySign;
import net.minecraft.world.level.block.entity.TileEntityTypes;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockPropertyWood;
import net.minecraft.world.level.block.state.properties.BlockStateEnum;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidTypes;
import net.minecraft.world.level.pathfinder.PathMode;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;
import net.minecraft.world.phys.shapes.VoxelShapes;

public class WallHangingSignBlock extends BlockSign {

    public static final MapCodec<WallHangingSignBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(BlockPropertyWood.CODEC.fieldOf("wood_type").forGetter(BlockSign::type), propertiesCodec()).apply(instance, WallHangingSignBlock::new);
    });
    public static final BlockStateEnum<EnumDirection> FACING = BlockFacingHorizontal.FACING;
    private static final Map<EnumDirection.EnumAxis, VoxelShape> SHAPES_PLANK = VoxelShapes.rotateHorizontalAxis(Block.column(16.0D, 4.0D, 14.0D, 16.0D));
    private static final Map<EnumDirection.EnumAxis, VoxelShape> SHAPES = VoxelShapes.rotateHorizontalAxis(VoxelShapes.or((VoxelShape) WallHangingSignBlock.SHAPES_PLANK.get(EnumDirection.EnumAxis.Z), Block.column(14.0D, 2.0D, 0.0D, 10.0D)));

    @Override
    public MapCodec<WallHangingSignBlock> codec() {
        return WallHangingSignBlock.CODEC;
    }

    public WallHangingSignBlock(BlockPropertyWood blockpropertywood, BlockBase.Info blockbase_info) {
        super(blockpropertywood, blockbase_info.sound(blockpropertywood.hangingSignSoundType()));
        this.registerDefaultState((IBlockData) ((IBlockData) (this.stateDefinition.any()).setValue(WallHangingSignBlock.FACING, EnumDirection.NORTH)).setValue(WallHangingSignBlock.WATERLOGGED, false));
    }

    @Override
    protected EnumInteractionResult useItemOn(ItemStack itemstack, IBlockData iblockdata, World world, BlockPosition blockposition, EntityHuman entityhuman, EnumHand enumhand, MovingObjectPositionBlock movingobjectpositionblock) {
        TileEntity tileentity = world.getBlockEntity(blockposition);

        if (tileentity instanceof TileEntitySign tileentitysign) {
            if (this.shouldTryToChainAnotherHangingSign(iblockdata, entityhuman, movingobjectpositionblock, tileentitysign, itemstack)) {
                return EnumInteractionResult.PASS;
            }
        }

        return super.useItemOn(itemstack, iblockdata, world, blockposition, entityhuman, enumhand, movingobjectpositionblock);
    }

    private boolean shouldTryToChainAnotherHangingSign(IBlockData iblockdata, EntityHuman entityhuman, MovingObjectPositionBlock movingobjectpositionblock, TileEntitySign tileentitysign, ItemStack itemstack) {
        return !tileentitysign.canExecuteClickCommands(tileentitysign.isFacingFrontText(entityhuman), entityhuman) && itemstack.getItem() instanceof HangingSignItem && !this.isHittingEditableSide(movingobjectpositionblock, iblockdata);
    }

    private boolean isHittingEditableSide(MovingObjectPositionBlock movingobjectpositionblock, IBlockData iblockdata) {
        return movingobjectpositionblock.getDirection().getAxis() == ((EnumDirection) iblockdata.getValue(WallHangingSignBlock.FACING)).getAxis();
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return (VoxelShape) WallHangingSignBlock.SHAPES.get(((EnumDirection) iblockdata.getValue(WallHangingSignBlock.FACING)).getAxis());
    }

    @Override
    protected VoxelShape getBlockSupportShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition) {
        return this.getShape(iblockdata, iblockaccess, blockposition, VoxelShapeCollision.empty());
    }

    @Override
    protected VoxelShape getCollisionShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return (VoxelShape) WallHangingSignBlock.SHAPES_PLANK.get(((EnumDirection) iblockdata.getValue(WallHangingSignBlock.FACING)).getAxis());
    }

    public boolean canPlace(IBlockData iblockdata, IWorldReader iworldreader, BlockPosition blockposition) {
        EnumDirection enumdirection = ((EnumDirection) iblockdata.getValue(WallHangingSignBlock.FACING)).getClockWise();
        EnumDirection enumdirection1 = ((EnumDirection) iblockdata.getValue(WallHangingSignBlock.FACING)).getCounterClockWise();

        return this.canAttachTo(iworldreader, iblockdata, blockposition.relative(enumdirection), enumdirection1) || this.canAttachTo(iworldreader, iblockdata, blockposition.relative(enumdirection1), enumdirection);
    }

    public boolean canAttachTo(IWorldReader iworldreader, IBlockData iblockdata, BlockPosition blockposition, EnumDirection enumdirection) {
        IBlockData iblockdata1 = iworldreader.getBlockState(blockposition);

        return iblockdata1.is(TagsBlock.WALL_HANGING_SIGNS) ? ((EnumDirection) iblockdata1.getValue(WallHangingSignBlock.FACING)).getAxis().test((EnumDirection) iblockdata.getValue(WallHangingSignBlock.FACING)) : iblockdata1.isFaceSturdy(iworldreader, blockposition, enumdirection, EnumBlockSupport.FULL);
    }

    @Nullable
    @Override
    public IBlockData getStateForPlacement(BlockActionContext blockactioncontext) {
        IBlockData iblockdata = this.defaultBlockState();
        Fluid fluid = blockactioncontext.getLevel().getFluidState(blockactioncontext.getClickedPos());
        IWorldReader iworldreader = blockactioncontext.getLevel();
        BlockPosition blockposition = blockactioncontext.getClickedPos();

        for (EnumDirection enumdirection : blockactioncontext.getNearestLookingDirections()) {
            if (enumdirection.getAxis().isHorizontal() && !enumdirection.getAxis().test(blockactioncontext.getClickedFace())) {
                EnumDirection enumdirection1 = enumdirection.getOpposite();

                iblockdata = (IBlockData) iblockdata.setValue(WallHangingSignBlock.FACING, enumdirection1);
                if (iblockdata.canSurvive(iworldreader, blockposition) && this.canPlace(iblockdata, iworldreader, blockposition)) {
                    return (IBlockData) iblockdata.setValue(WallHangingSignBlock.WATERLOGGED, fluid.getType() == FluidTypes.WATER);
                }
            }
        }

        return null;
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        return enumdirection.getAxis() == ((EnumDirection) iblockdata.getValue(WallHangingSignBlock.FACING)).getClockWise().getAxis() && !iblockdata.canSurvive(iworldreader, blockposition) ? Blocks.AIR.defaultBlockState() : super.updateShape(iblockdata, iworldreader, scheduledtickaccess, blockposition, enumdirection, blockposition1, iblockdata1, randomsource);
    }

    @Override
    public float getYRotationDegrees(IBlockData iblockdata) {
        return ((EnumDirection) iblockdata.getValue(WallHangingSignBlock.FACING)).toYRot();
    }

    @Override
    protected IBlockData rotate(IBlockData iblockdata, EnumBlockRotation enumblockrotation) {
        return (IBlockData) iblockdata.setValue(WallHangingSignBlock.FACING, enumblockrotation.rotate((EnumDirection) iblockdata.getValue(WallHangingSignBlock.FACING)));
    }

    @Override
    protected IBlockData mirror(IBlockData iblockdata, EnumBlockMirror enumblockmirror) {
        return iblockdata.rotate(enumblockmirror.getRotation((EnumDirection) iblockdata.getValue(WallHangingSignBlock.FACING)));
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(WallHangingSignBlock.FACING, WallHangingSignBlock.WATERLOGGED);
    }

    @Override
    public TileEntity newBlockEntity(BlockPosition blockposition, IBlockData iblockdata) {
        return new HangingSignBlockEntity(blockposition, iblockdata);
    }

    @Override
    protected boolean isPathfindable(IBlockData iblockdata, PathMode pathmode) {
        return false;
    }

    @Nullable
    @Override
    public <T extends TileEntity> BlockEntityTicker<T> getTicker(World world, IBlockData iblockdata, TileEntityTypes<T> tileentitytypes) {
        return null; // Craftbukkit - remove unnecessary sign ticking
    }
}

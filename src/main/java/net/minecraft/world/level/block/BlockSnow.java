package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.level.EnumSkyBlock;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockStateInteger;
import net.minecraft.world.level.pathfinder.PathMode;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;

public class BlockSnow extends Block {

    public static final MapCodec<BlockSnow> CODEC = simpleCodec(BlockSnow::new);
    public static final int MAX_HEIGHT = 8;
    public static final BlockStateInteger LAYERS = BlockProperties.LAYERS;
    private static final VoxelShape[] SHAPES = Block.boxes(8, (i) -> {
        return Block.column(16.0D, 0.0D, (double) (i * 2));
    });
    public static final int HEIGHT_IMPASSABLE = 5;

    @Override
    public MapCodec<BlockSnow> codec() {
        return BlockSnow.CODEC;
    }

    protected BlockSnow(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) (this.stateDefinition.any()).setValue(BlockSnow.LAYERS, 1));
    }

    @Override
    protected boolean isPathfindable(IBlockData iblockdata, PathMode pathmode) {
        return pathmode == PathMode.LAND ? (Integer) iblockdata.getValue(BlockSnow.LAYERS) < 5 : false;
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return BlockSnow.SHAPES[(Integer) iblockdata.getValue(BlockSnow.LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return BlockSnow.SHAPES[(Integer) iblockdata.getValue(BlockSnow.LAYERS) - 1];
    }

    @Override
    protected VoxelShape getBlockSupportShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition) {
        return BlockSnow.SHAPES[(Integer) iblockdata.getValue(BlockSnow.LAYERS)];
    }

    @Override
    protected VoxelShape getVisualShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return BlockSnow.SHAPES[(Integer) iblockdata.getValue(BlockSnow.LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(IBlockData iblockdata) {
        return true;
    }

    @Override
    protected float getShadeBrightness(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition) {
        return (Integer) iblockdata.getValue(BlockSnow.LAYERS) == 8 ? 0.2F : 1.0F;
    }

    @Override
    protected boolean canSurvive(IBlockData iblockdata, IWorldReader iworldreader, BlockPosition blockposition) {
        IBlockData iblockdata1 = iworldreader.getBlockState(blockposition.below());

        return iblockdata1.is(TagsBlock.SNOW_LAYER_CANNOT_SURVIVE_ON) ? false : (iblockdata1.is(TagsBlock.SNOW_LAYER_CAN_SURVIVE_ON) ? true : Block.isFaceFull(iblockdata1.getCollisionShape(iworldreader, blockposition.below()), EnumDirection.UP) || iblockdata1.is(this) && (Integer) iblockdata1.getValue(BlockSnow.LAYERS) == 8);
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        return !iblockdata.canSurvive(iworldreader, blockposition) ? Blocks.AIR.defaultBlockState() : super.updateShape(iblockdata, iworldreader, scheduledtickaccess, blockposition, enumdirection, blockposition1, iblockdata1, randomsource);
    }

    @Override
    protected void randomTick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        if (worldserver.getBrightness(EnumSkyBlock.BLOCK, blockposition) > 11) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(worldserver, blockposition, Blocks.AIR.defaultBlockState()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            dropResources(iblockdata, worldserver, blockposition);
            worldserver.removeBlock(blockposition, false);
        }

    }

    @Override
    protected boolean canBeReplaced(IBlockData iblockdata, BlockActionContext blockactioncontext) {
        int i = (Integer) iblockdata.getValue(BlockSnow.LAYERS);

        return blockactioncontext.getItemInHand().is(this.asItem()) && i < 8 ? (blockactioncontext.replacingClickedOnBlock() ? blockactioncontext.getClickedFace() == EnumDirection.UP : true) : i == 1;
    }

    @Nullable
    @Override
    public IBlockData getStateForPlacement(BlockActionContext blockactioncontext) {
        IBlockData iblockdata = blockactioncontext.getLevel().getBlockState(blockactioncontext.getClickedPos());

        if (iblockdata.is(this)) {
            int i = (Integer) iblockdata.getValue(BlockSnow.LAYERS);

            return (IBlockData) iblockdata.setValue(BlockSnow.LAYERS, Math.min(8, i + 1));
        } else {
            return super.getStateForPlacement(blockactioncontext);
        }
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockSnow.LAYERS);
    }
}

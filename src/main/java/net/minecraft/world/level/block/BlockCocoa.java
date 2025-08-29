package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockActionContext;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockStateInteger;
import net.minecraft.world.level.pathfinder.PathMode;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;
import net.minecraft.world.phys.shapes.VoxelShapes;

import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class BlockCocoa extends BlockFacingHorizontal implements IBlockFragilePlantElement {

    public static final MapCodec<BlockCocoa> CODEC = simpleCodec(BlockCocoa::new);
    public static final int MAX_AGE = 2;
    public static final BlockStateInteger AGE = BlockProperties.AGE_2;
    private static final List<Map<EnumDirection, VoxelShape>> SHAPES = IntStream.rangeClosed(0, 2).mapToObj((i) -> {
        return VoxelShapes.rotateHorizontal(Block.column((double) (4 + i * 2), (double) (7 - i * 2), 12.0D).move(0.0D, 0.0D, (double) (i - 5) / 16.0D).optimize());
    }).toList();

    @Override
    public MapCodec<BlockCocoa> codec() {
        return BlockCocoa.CODEC;
    }

    public BlockCocoa(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) ((IBlockData) (this.stateDefinition.any()).setValue(BlockCocoa.FACING, EnumDirection.NORTH)).setValue(BlockCocoa.AGE, 0));
    }

    @Override
    protected boolean isRandomlyTicking(IBlockData iblockdata) {
        return (Integer) iblockdata.getValue(BlockCocoa.AGE) < 2;
    }

    @Override
    protected void randomTick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        if (worldserver.random.nextInt(5) == 0) {
            int i = (Integer) iblockdata.getValue(BlockCocoa.AGE);

            if (i < 2) {
                CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, (IBlockData) iblockdata.setValue(BlockCocoa.AGE, i + 1), 2); // CraftBukkkit
            }
        }

    }

    @Override
    protected boolean canSurvive(IBlockData iblockdata, IWorldReader iworldreader, BlockPosition blockposition) {
        IBlockData iblockdata1 = iworldreader.getBlockState(blockposition.relative((EnumDirection) iblockdata.getValue(BlockCocoa.FACING)));

        return iblockdata1.is(TagsBlock.JUNGLE_LOGS);
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return (VoxelShape) ((Map) BlockCocoa.SHAPES.get((Integer) iblockdata.getValue(BlockCocoa.AGE))).get(iblockdata.getValue(BlockCocoa.FACING));
    }

    @Nullable
    @Override
    public IBlockData getStateForPlacement(BlockActionContext blockactioncontext) {
        IBlockData iblockdata = this.defaultBlockState();
        IWorldReader iworldreader = blockactioncontext.getLevel();
        BlockPosition blockposition = blockactioncontext.getClickedPos();

        for (EnumDirection enumdirection : blockactioncontext.getNearestLookingDirections()) {
            if (enumdirection.getAxis().isHorizontal()) {
                iblockdata = (IBlockData) iblockdata.setValue(BlockCocoa.FACING, enumdirection);
                if (iblockdata.canSurvive(iworldreader, blockposition)) {
                    return iblockdata;
                }
            }
        }

        return null;
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        return enumdirection == iblockdata.getValue(BlockCocoa.FACING) && !iblockdata.canSurvive(iworldreader, blockposition) ? Blocks.AIR.defaultBlockState() : super.updateShape(iblockdata, iworldreader, scheduledtickaccess, blockposition, enumdirection, blockposition1, iblockdata1, randomsource);
    }

    @Override
    public boolean isValidBonemealTarget(IWorldReader iworldreader, BlockPosition blockposition, IBlockData iblockdata) {
        return (Integer) iblockdata.getValue(BlockCocoa.AGE) < 2;
    }

    @Override
    public boolean isBonemealSuccess(World world, RandomSource randomsource, BlockPosition blockposition, IBlockData iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(WorldServer worldserver, RandomSource randomsource, BlockPosition blockposition, IBlockData iblockdata) {
        CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, (IBlockData) iblockdata.setValue(BlockCocoa.AGE, (Integer) iblockdata.getValue(BlockCocoa.AGE) + 1), 2); // CraftBukkit
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockCocoa.FACING, BlockCocoa.AGE);
    }

    @Override
    protected boolean isPathfindable(IBlockData iblockdata, PathMode pathmode) {
        return false;
    }
}

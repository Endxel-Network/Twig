package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagsBlock;
import net.minecraft.tags.TagsFluid;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;

import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class BlockCactus extends Block {

    public static final MapCodec<BlockCactus> CODEC = simpleCodec(BlockCactus::new);
    public static final BlockStateInteger AGE = BlockProperties.AGE_15;
    public static final int MAX_AGE = 15;
    private static final VoxelShape SHAPE = Block.column(14.0D, 0.0D, 16.0D);
    private static final VoxelShape SHAPE_COLLISION = Block.column(14.0D, 0.0D, 15.0D);
    private static final int MAX_CACTUS_GROWING_HEIGHT = 3;
    private static final int ATTEMPT_GROW_CACTUS_FLOWER_AGE = 8;
    private static final double ATTEMPT_GROW_CACTUS_FLOWER_SMALL_CACTUS_CHANCE = 0.1D;
    private static final double ATTEMPT_GROW_CACTUS_FLOWER_TALL_CACTUS_CHANCE = 0.25D;

    @Override
    public MapCodec<BlockCactus> codec() {
        return BlockCactus.CODEC;
    }

    protected BlockCactus(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) (this.stateDefinition.any()).setValue(BlockCactus.AGE, 0));
    }

    @Override
    protected void tick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        if (!iblockdata.canSurvive(worldserver, blockposition)) {
            worldserver.destroyBlock(blockposition, true);
        }

    }

    @Override
    protected void randomTick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        BlockPosition blockposition1 = blockposition.above();

        if (worldserver.isEmptyBlock(blockposition1)) {
            int i = 1;
            int j = (Integer) iblockdata.getValue(BlockCactus.AGE);

            while (worldserver.getBlockState(blockposition.below(i)).is(this)) {
                ++i;
                if (i == 3 && j == 15) {
                    return;
                }
            }

            if (j == 8 && this.canSurvive(this.defaultBlockState(), worldserver, blockposition.above())) {
                double d0 = i >= 3 ? 0.25D : 0.1D;

                if (randomsource.nextDouble() <= d0) {
                    CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition1, Blocks.CACTUS_FLOWER.defaultBlockState()); // CraftBukkit
                }
            } else if (j == 15 && i < 3) {
                CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition1, this.defaultBlockState()); // CraftBukkit
                IBlockData iblockdata1 = (IBlockData) iblockdata.setValue(BlockCactus.AGE, 0);

                worldserver.setBlock(blockposition, iblockdata1, 260);
                worldserver.neighborChanged(iblockdata1, blockposition1, this, (Orientation) null, false);
            }

            if (j < 15) {
                worldserver.setBlock(blockposition, (IBlockData) iblockdata.setValue(BlockCactus.AGE, j + 1), 260);
            }

        }
    }

    @Override
    protected VoxelShape getCollisionShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return BlockCactus.SHAPE_COLLISION;
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return BlockCactus.SHAPE;
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        if (!iblockdata.canSurvive(iworldreader, blockposition)) {
            scheduledtickaccess.scheduleTick(blockposition, (Block) this, 1);
        }

        return super.updateShape(iblockdata, iworldreader, scheduledtickaccess, blockposition, enumdirection, blockposition1, iblockdata1, randomsource);
    }

    @Override
    protected boolean canSurvive(IBlockData iblockdata, IWorldReader iworldreader, BlockPosition blockposition) {
        for (EnumDirection enumdirection : EnumDirection.EnumDirectionLimit.HORIZONTAL) {
            IBlockData iblockdata1 = iworldreader.getBlockState(blockposition.relative(enumdirection));

            if (iblockdata1.isSolid() || iworldreader.getFluidState(blockposition.relative(enumdirection)).is(TagsFluid.LAVA)) {
                return false;
            }
        }

        IBlockData iblockdata2 = iworldreader.getBlockState(blockposition.below());

        return (iblockdata2.is(Blocks.CACTUS) || iblockdata2.is(TagsBlock.SAND)) && !iworldreader.getBlockState(blockposition.above()).liquid();
    }

    @Override
    protected void entityInside(IBlockData iblockdata, World world, BlockPosition blockposition, Entity entity, InsideBlockEffectApplier insideblockeffectapplier) {
        entity.hurt(world.damageSources().cactus().directBlock(world, blockposition), 1.0F); // CraftBukkit
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockCactus.AGE);
    }

    @Override
    protected boolean isPathfindable(IBlockData iblockdata, PathMode pathmode) {
        return false;
    }
}

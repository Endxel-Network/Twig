package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockPropertyBambooSize;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;

public class BlockBambooSapling extends Block implements IBlockFragilePlantElement {

    public static final MapCodec<BlockBambooSapling> CODEC = simpleCodec(BlockBambooSapling::new);
    private static final VoxelShape SHAPE = Block.column(8.0D, 0.0D, 12.0D);

    @Override
    public MapCodec<BlockBambooSapling> codec() {
        return BlockBambooSapling.CODEC;
    }

    public BlockBambooSapling(BlockBase.Info blockbase_info) {
        super(blockbase_info);
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return BlockBambooSapling.SHAPE.move(iblockdata.getOffset(blockposition));
    }

    @Override
    protected void randomTick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        if (randomsource.nextFloat() < (worldserver.spigotConfig.bambooModifier / (100.0f * 3)) && worldserver.isEmptyBlock(blockposition.above()) && worldserver.getRawBrightness(blockposition.above(), 0) >= 9) { // Spigot - SPIGOT-7159: Better modifier resolution
            this.growBamboo(worldserver, blockposition);
        }

    }

    @Override
    protected boolean canSurvive(IBlockData iblockdata, IWorldReader iworldreader, BlockPosition blockposition) {
        return iworldreader.getBlockState(blockposition.below()).is(TagsBlock.BAMBOO_PLANTABLE_ON);
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        return !iblockdata.canSurvive(iworldreader, blockposition) ? Blocks.AIR.defaultBlockState() : (enumdirection == EnumDirection.UP && iblockdata1.is(Blocks.BAMBOO) ? Blocks.BAMBOO.defaultBlockState() : super.updateShape(iblockdata, iworldreader, scheduledtickaccess, blockposition, enumdirection, blockposition1, iblockdata1, randomsource));
    }

    @Override
    protected ItemStack getCloneItemStack(IWorldReader iworldreader, BlockPosition blockposition, IBlockData iblockdata, boolean flag) {
        return new ItemStack(Items.BAMBOO);
    }

    @Override
    public boolean isValidBonemealTarget(IWorldReader iworldreader, BlockPosition blockposition, IBlockData iblockdata) {
        return iworldreader.getBlockState(blockposition.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(World world, RandomSource randomsource, BlockPosition blockposition, IBlockData iblockdata) {
        return true;
    }

    @Override
    public void performBonemeal(WorldServer worldserver, RandomSource randomsource, BlockPosition blockposition, IBlockData iblockdata) {
        this.growBamboo(worldserver, blockposition);
    }

    protected void growBamboo(World world, BlockPosition blockposition) {
        org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockSpreadEvent(world, blockposition, blockposition.above(), (IBlockData) Blocks.BAMBOO.defaultBlockState().setValue(BlockBamboo.LEAVES, BlockPropertyBambooSize.SMALL), 3); // CraftBukkit - BlockSpreadEvent
    }
}

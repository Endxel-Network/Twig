package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockStateInteger;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;

public class BlockNetherWart extends VegetationBlock {

    public static final MapCodec<BlockNetherWart> CODEC = simpleCodec(BlockNetherWart::new);
    public static final int MAX_AGE = 3;
    public static final BlockStateInteger AGE = BlockProperties.AGE_3;
    private static final VoxelShape[] SHAPES = Block.boxes(3, (i) -> {
        return Block.column(16.0D, 0.0D, (double) (5 + i * 3));
    });

    @Override
    public MapCodec<BlockNetherWart> codec() {
        return BlockNetherWart.CODEC;
    }

    protected BlockNetherWart(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) (this.stateDefinition.any()).setValue(BlockNetherWart.AGE, 0));
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return BlockNetherWart.SHAPES[(Integer) iblockdata.getValue(BlockNetherWart.AGE)];
    }

    @Override
    protected boolean mayPlaceOn(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition) {
        return iblockdata.is(Blocks.SOUL_SAND);
    }

    @Override
    protected boolean isRandomlyTicking(IBlockData iblockdata) {
        return (Integer) iblockdata.getValue(BlockNetherWart.AGE) < 3;
    }

    @Override
    protected void randomTick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        int i = (Integer) iblockdata.getValue(BlockNetherWart.AGE);

        if (i < 3 && randomsource.nextFloat() < (worldserver.spigotConfig.wartModifier / (100.0f * 10))) { // Spigot - SPIGOT-7159: Better modifier resolution
            iblockdata = (IBlockData) iblockdata.setValue(BlockNetherWart.AGE, i + 1);
            org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockGrowEvent(worldserver, blockposition, iblockdata, 2); // CraftBukkit
        }

    }

    @Override
    protected ItemStack getCloneItemStack(IWorldReader iworldreader, BlockPosition blockposition, IBlockData iblockdata, boolean flag) {
        return new ItemStack(Items.NETHER_WART);
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockNetherWart.AGE);
    }
}

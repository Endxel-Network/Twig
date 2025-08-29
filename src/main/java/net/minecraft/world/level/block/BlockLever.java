package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleParamRedstone;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.util.RandomSource;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.BlockStateList;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.block.state.properties.BlockPropertyAttachPosition;
import net.minecraft.world.level.block.state.properties.BlockStateBoolean;
import net.minecraft.world.level.block.state.properties.IBlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.MovingObjectPositionBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;
import net.minecraft.world.phys.shapes.VoxelShapes;

import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class BlockLever extends BlockAttachable {

    public static final MapCodec<BlockLever> CODEC = simpleCodec(BlockLever::new);
    public static final BlockStateBoolean POWERED = BlockProperties.POWERED;
    private final Function<IBlockData, VoxelShape> shapes;

    @Override
    public MapCodec<BlockLever> codec() {
        return BlockLever.CODEC;
    }

    protected BlockLever(BlockBase.Info blockbase_info) {
        super(blockbase_info);
        this.registerDefaultState((IBlockData) ((IBlockData) ((IBlockData) (this.stateDefinition.any()).setValue(BlockLever.FACING, EnumDirection.NORTH)).setValue(BlockLever.POWERED, false)).setValue(BlockLever.FACE, BlockPropertyAttachPosition.WALL));
        this.shapes = this.makeShapes();
    }

    private Function<IBlockData, VoxelShape> makeShapes() {
        Map<BlockPropertyAttachPosition, Map<EnumDirection, VoxelShape>> map = VoxelShapes.rotateAttachFace(Block.boxZ(6.0D, 8.0D, 10.0D, 16.0D));

        return this.getShapeForEachState((iblockdata) -> {
            return (VoxelShape) ((Map) map.get(iblockdata.getValue(BlockLever.FACE))).get(iblockdata.getValue(BlockLever.FACING));
        }, new IBlockState[]{BlockLever.POWERED});
    }

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return (VoxelShape) this.shapes.apply(iblockdata);
    }

    @Override
    protected EnumInteractionResult useWithoutItem(IBlockData iblockdata, World world, BlockPosition blockposition, EntityHuman entityhuman, MovingObjectPositionBlock movingobjectpositionblock) {
        if (world.isClientSide) {
            IBlockData iblockdata1 = (IBlockData) iblockdata.cycle(BlockLever.POWERED);

            if ((Boolean) iblockdata1.getValue(BlockLever.POWERED)) {
                makeParticle(iblockdata1, world, blockposition, 1.0F);
            }
        } else {
            // CraftBukkit start - Interact Lever
            boolean powered = iblockdata.getValue(BlockLever.POWERED); // Old powered state
            org.bukkit.block.Block block = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
            int old = (powered) ? 15 : 0;
            int current = (!powered) ? 15 : 0;

            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(block, old, current);
            world.getCraftServer().getPluginManager().callEvent(eventRedstone);

            if ((eventRedstone.getNewCurrent() > 0) != (!powered)) {
                return EnumInteractionResult.SUCCESS;
            }
            // CraftBukkit end

            this.pull(iblockdata, world, blockposition, (EntityHuman) null);
        }

        return EnumInteractionResult.SUCCESS;
    }

    @Override
    protected void onExplosionHit(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, Explosion explosion, BiConsumer<ItemStack, BlockPosition> biconsumer) {
        if (explosion.canTriggerBlocks()) {
            this.pull(iblockdata, worldserver, blockposition, (EntityHuman) null);
        }

        super.onExplosionHit(iblockdata, worldserver, blockposition, explosion, biconsumer);
    }

    public void pull(IBlockData iblockdata, World world, BlockPosition blockposition, @Nullable EntityHuman entityhuman) {
        iblockdata = (IBlockData) iblockdata.cycle(BlockLever.POWERED);
        world.setBlock(blockposition, iblockdata, 3);
        this.updateNeighbours(iblockdata, world, blockposition);
        playSound(entityhuman, world, blockposition, iblockdata);
        world.gameEvent(entityhuman, (Holder) ((Boolean) iblockdata.getValue(BlockLever.POWERED) ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE), blockposition);
    }

    protected static void playSound(@Nullable EntityHuman entityhuman, GeneratorAccess generatoraccess, BlockPosition blockposition, IBlockData iblockdata) {
        float f = (Boolean) iblockdata.getValue(BlockLever.POWERED) ? 0.6F : 0.5F;

        generatoraccess.playSound(entityhuman, blockposition, SoundEffects.LEVER_CLICK, SoundCategory.BLOCKS, 0.3F, f);
    }

    private static void makeParticle(IBlockData iblockdata, GeneratorAccess generatoraccess, BlockPosition blockposition, float f) {
        EnumDirection enumdirection = ((EnumDirection) iblockdata.getValue(BlockLever.FACING)).getOpposite();
        EnumDirection enumdirection1 = getConnectedDirection(iblockdata).getOpposite();
        double d0 = (double) blockposition.getX() + 0.5D + 0.1D * (double) enumdirection.getStepX() + 0.2D * (double) enumdirection1.getStepX();
        double d1 = (double) blockposition.getY() + 0.5D + 0.1D * (double) enumdirection.getStepY() + 0.2D * (double) enumdirection1.getStepY();
        double d2 = (double) blockposition.getZ() + 0.5D + 0.1D * (double) enumdirection.getStepZ() + 0.2D * (double) enumdirection1.getStepZ();

        generatoraccess.addParticle(new ParticleParamRedstone(16711680, f), d0, d1, d2, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public void animateTick(IBlockData iblockdata, World world, BlockPosition blockposition, RandomSource randomsource) {
        if ((Boolean) iblockdata.getValue(BlockLever.POWERED) && randomsource.nextFloat() < 0.25F) {
            makeParticle(iblockdata, world, blockposition, 0.5F);
        }

    }

    @Override
    protected void affectNeighborsAfterRemoval(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, boolean flag) {
        if (!flag && (Boolean) iblockdata.getValue(BlockLever.POWERED)) {
            this.updateNeighbours(iblockdata, worldserver, blockposition);
        }

    }

    @Override
    protected int getSignal(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, EnumDirection enumdirection) {
        return (Boolean) iblockdata.getValue(BlockLever.POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, EnumDirection enumdirection) {
        return (Boolean) iblockdata.getValue(BlockLever.POWERED) && getConnectedDirection(iblockdata) == enumdirection ? 15 : 0;
    }

    @Override
    protected boolean isSignalSource(IBlockData iblockdata) {
        return true;
    }

    private void updateNeighbours(IBlockData iblockdata, World world, BlockPosition blockposition) {
        EnumDirection enumdirection = getConnectedDirection(iblockdata).getOpposite();
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(world, enumdirection, enumdirection.getAxis().isHorizontal() ? EnumDirection.UP : (EnumDirection) iblockdata.getValue(BlockLever.FACING));

        world.updateNeighborsAt(blockposition, this, orientation);
        world.updateNeighborsAt(blockposition.relative(enumdirection), this, orientation);
    }

    @Override
    protected void createBlockStateDefinition(BlockStateList.a<Block, IBlockData> blockstatelist_a) {
        blockstatelist_a.add(BlockLever.FACE, BlockLever.FACING, BlockLever.POWERED);
    }
}

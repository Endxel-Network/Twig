package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.IWorldReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.BlockBase;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.VoxelShapeCollision;

import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public abstract class BlockPressurePlateAbstract extends Block {

    private static final VoxelShape SHAPE_PRESSED = Block.column(14.0D, 0.0D, 0.5D);
    private static final VoxelShape SHAPE = Block.column(14.0D, 0.0D, 1.0D);
    protected static final AxisAlignedBB TOUCH_AABB = (AxisAlignedBB) Block.column(14.0D, 0.0D, 4.0D).toAabbs().getFirst();
    protected final BlockSetType type;

    protected BlockPressurePlateAbstract(BlockBase.Info blockbase_info, BlockSetType blocksettype) {
        super(blockbase_info.sound(blocksettype.soundType()));
        this.type = blocksettype;
    }

    @Override
    protected abstract MapCodec<? extends BlockPressurePlateAbstract> codec();

    @Override
    protected VoxelShape getShape(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, VoxelShapeCollision voxelshapecollision) {
        return this.getSignalForState(iblockdata) > 0 ? BlockPressurePlateAbstract.SHAPE_PRESSED : BlockPressurePlateAbstract.SHAPE;
    }

    protected int getPressedTime() {
        return 20;
    }

    @Override
    public boolean isPossibleToRespawnInThis(IBlockData iblockdata) {
        return true;
    }

    @Override
    protected IBlockData updateShape(IBlockData iblockdata, IWorldReader iworldreader, ScheduledTickAccess scheduledtickaccess, BlockPosition blockposition, EnumDirection enumdirection, BlockPosition blockposition1, IBlockData iblockdata1, RandomSource randomsource) {
        return enumdirection == EnumDirection.DOWN && !iblockdata.canSurvive(iworldreader, blockposition) ? Blocks.AIR.defaultBlockState() : super.updateShape(iblockdata, iworldreader, scheduledtickaccess, blockposition, enumdirection, blockposition1, iblockdata1, randomsource);
    }

    @Override
    protected boolean canSurvive(IBlockData iblockdata, IWorldReader iworldreader, BlockPosition blockposition) {
        BlockPosition blockposition1 = blockposition.below();

        return canSupportRigidBlock(iworldreader, blockposition1) || canSupportCenter(iworldreader, blockposition1, EnumDirection.UP);
    }

    @Override
    protected void tick(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource) {
        int i = this.getSignalForState(iblockdata);

        if (i > 0) {
            this.checkPressed((Entity) null, worldserver, blockposition, iblockdata, i);
        }

    }

    @Override
    protected void entityInside(IBlockData iblockdata, World world, BlockPosition blockposition, Entity entity, InsideBlockEffectApplier insideblockeffectapplier) {
        if (!world.isClientSide) {
            int i = this.getSignalForState(iblockdata);

            if (i == 0) {
                this.checkPressed(entity, world, blockposition, iblockdata, i);
            }

        }
    }

    private void checkPressed(@Nullable Entity entity, World world, BlockPosition blockposition, IBlockData iblockdata, int i) {
        int j = this.getSignalStrength(world, blockposition);
        boolean flag = i > 0;
        boolean flag1 = j > 0;

        // CraftBukkit start - Interact Pressure Plate
        org.bukkit.World bworld = world.getWorld();
        org.bukkit.plugin.PluginManager manager = world.getCraftServer().getPluginManager();

        if (flag != flag1) {
            BlockRedstoneEvent eventRedstone = new BlockRedstoneEvent(bworld.getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ()), i, j);
            manager.callEvent(eventRedstone);

            flag1 = eventRedstone.getNewCurrent() > 0;
            j = eventRedstone.getNewCurrent();
        }
        // CraftBukkit end

        if (i != j) {
            IBlockData iblockdata1 = this.setSignalForState(iblockdata, j);

            world.setBlock(blockposition, iblockdata1, 2);
            this.updateNeighbours(world, blockposition);
            world.setBlocksDirty(blockposition, iblockdata, iblockdata1);
        }

        if (!flag1 && flag) {
            world.playSound((Entity) null, blockposition, this.type.pressurePlateClickOff(), SoundCategory.BLOCKS);
            world.gameEvent(entity, (Holder) GameEvent.BLOCK_DEACTIVATE, blockposition);
        } else if (flag1 && !flag) {
            world.playSound((Entity) null, blockposition, this.type.pressurePlateClickOn(), SoundCategory.BLOCKS);
            world.gameEvent(entity, (Holder) GameEvent.BLOCK_ACTIVATE, blockposition);
        }

        if (flag1) {
            world.scheduleTick(new BlockPosition(blockposition), (Block) this, this.getPressedTime());
        }

    }

    @Override
    protected void affectNeighborsAfterRemoval(IBlockData iblockdata, WorldServer worldserver, BlockPosition blockposition, boolean flag) {
        if (!flag && this.getSignalForState(iblockdata) > 0) {
            this.updateNeighbours(worldserver, blockposition);
        }

    }

    protected void updateNeighbours(World world, BlockPosition blockposition) {
        world.updateNeighborsAt(blockposition, this);
        world.updateNeighborsAt(blockposition.below(), this);
    }

    @Override
    protected int getSignal(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, EnumDirection enumdirection) {
        return this.getSignalForState(iblockdata);
    }

    @Override
    protected int getDirectSignal(IBlockData iblockdata, IBlockAccess iblockaccess, BlockPosition blockposition, EnumDirection enumdirection) {
        return enumdirection == EnumDirection.UP ? this.getSignalForState(iblockdata) : 0;
    }

    @Override
    protected boolean isSignalSource(IBlockData iblockdata) {
        return true;
    }

    protected static int getEntityCount(World world, AxisAlignedBB axisalignedbb, Class<? extends Entity> oclass) {
        // CraftBukkit start
        return getEntities(world, axisalignedbb, oclass).size();
    }

    protected static <T extends Entity> java.util.List<T> getEntities(World world, AxisAlignedBB axisalignedbb, Class<T> oclass) {
        // CraftBukkit end
        return world.getEntitiesOfClass(oclass, axisalignedbb, IEntitySelector.NO_SPECTATORS.and((entity) -> {
            return !entity.isIgnoringBlockTriggers();
        })); // CraftBukkit
    }

    protected abstract int getSignalStrength(World world, BlockPosition blockposition);

    protected abstract int getSignalForState(IBlockData iblockdata);

    protected abstract IBlockData setSignalForState(IBlockData iblockdata, int i);
}

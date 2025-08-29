package net.minecraft.world.level.redstone;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockRedstoneWire;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockPropertyRedstoneSide;
import net.minecraft.world.level.block.state.properties.BlockStateEnum;

// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.BlockRedstoneEvent;
// CraftBukkit end

public class ExperimentalRedstoneWireEvaluator extends RedstoneWireEvaluator {

    private final Deque<BlockPosition> wiresToTurnOff = new ArrayDeque();
    private final Deque<BlockPosition> wiresToTurnOn = new ArrayDeque();
    private final Object2IntMap<BlockPosition> updatedWires = new Object2IntLinkedOpenHashMap();

    public ExperimentalRedstoneWireEvaluator(BlockRedstoneWire blockredstonewire) {
        super(blockredstonewire);
    }

    @Override
    public void updatePowerStrength(World world, BlockPosition blockposition, IBlockData iblockdata, @Nullable Orientation orientation, boolean flag) {
        Orientation orientation1 = getInitialOrientation(world, orientation);

        this.calculateCurrentChanges(world, blockposition, orientation1);
        ObjectIterator<Object2IntMap.Entry<BlockPosition>> objectiterator = this.updatedWires.object2IntEntrySet().iterator();

        for (boolean flag1 = true; objectiterator.hasNext(); flag1 = false) {
            Object2IntMap.Entry<BlockPosition> object2intmap_entry = (Entry) objectiterator.next();
            BlockPosition blockposition1 = (BlockPosition) object2intmap_entry.getKey();
            int i = object2intmap_entry.getIntValue();
            int j = unpackPower(i);
            IBlockData iblockdata1 = world.getBlockState(blockposition1);

            // CraftBukkit start
            int oldPower = iblockdata.getValue(BlockRedstoneWire.POWER);
            if (oldPower != j) {
                BlockRedstoneEvent event = new BlockRedstoneEvent(CraftBlock.at(world, blockposition1), oldPower, j);
                world.getCraftServer().getPluginManager().callEvent(event);

                j = event.getNewCurrent();
            }
            if (iblockdata1.is(this.wireBlock) && oldPower != j) {
                // CraftBukkit end
                int k = 2;

                if (!flag || !flag1) {
                    k |= 128;
                }

                world.setBlock(blockposition1, (IBlockData) iblockdata1.setValue(BlockRedstoneWire.POWER, j), k);
            } else {
                objectiterator.remove();
            }
        }

        this.causeNeighborUpdates(world);
    }

    private void causeNeighborUpdates(World world) {
        this.updatedWires.forEach((blockposition, integer) -> {
            Orientation orientation = unpackOrientation(integer);
            IBlockData iblockdata = world.getBlockState(blockposition);

            for (EnumDirection enumdirection : orientation.getDirections()) {
                if (isConnected(iblockdata, enumdirection)) {
                    BlockPosition blockposition1 = blockposition.relative(enumdirection);
                    IBlockData iblockdata1 = world.getBlockState(blockposition1);
                    Orientation orientation1 = orientation.withFrontPreserveUp(enumdirection);

                    world.neighborChanged(iblockdata1, blockposition1, this.wireBlock, orientation1, false);
                    if (iblockdata1.isRedstoneConductor(world, blockposition1)) {
                        for (EnumDirection enumdirection1 : orientation1.getDirections()) {
                            if (enumdirection1 != enumdirection.getOpposite()) {
                                world.neighborChanged(blockposition1.relative(enumdirection1), this.wireBlock, orientation1.withFrontPreserveUp(enumdirection1));
                            }
                        }
                    }
                }
            }

        });
    }

    private static boolean isConnected(IBlockData iblockdata, EnumDirection enumdirection) {
        BlockStateEnum<BlockPropertyRedstoneSide> blockstateenum = (BlockStateEnum) BlockRedstoneWire.PROPERTY_BY_DIRECTION.get(enumdirection);

        return blockstateenum == null ? enumdirection == EnumDirection.DOWN : ((BlockPropertyRedstoneSide) iblockdata.getValue(blockstateenum)).isConnected();
    }

    private static Orientation getInitialOrientation(World world, @Nullable Orientation orientation) {
        Orientation orientation1;

        if (orientation != null) {
            orientation1 = orientation;
        } else {
            orientation1 = Orientation.random(world.random);
        }

        return orientation1.withUp(EnumDirection.UP).withSideBias(Orientation.a.LEFT);
    }

    private void calculateCurrentChanges(World world, BlockPosition blockposition, Orientation orientation) {
        IBlockData iblockdata = world.getBlockState(blockposition);

        if (iblockdata.is(this.wireBlock)) {
            this.setPower(blockposition, (Integer) iblockdata.getValue(BlockRedstoneWire.POWER), orientation);
            this.wiresToTurnOff.add(blockposition);
        } else {
            this.propagateChangeToNeighbors(world, blockposition, 0, orientation, true);
        }

        BlockPosition blockposition1;
        Orientation orientation1;
        int i;
        int j;
        int k;

        for (; !this.wiresToTurnOff.isEmpty(); this.propagateChangeToNeighbors(world, blockposition1, k, orientation1, i > j)) {
            blockposition1 = (BlockPosition) this.wiresToTurnOff.removeFirst();
            int l = this.updatedWires.getInt(blockposition1);

            orientation1 = unpackOrientation(l);
            i = unpackPower(l);
            int i1 = this.getBlockSignal(world, blockposition1);
            int j1 = this.getIncomingWireSignal(world, blockposition1);

            j = Math.max(i1, j1);
            if (j < i) {
                if (i1 > 0 && !this.wiresToTurnOn.contains(blockposition1)) {
                    this.wiresToTurnOn.add(blockposition1);
                }

                k = 0;
            } else {
                k = j;
            }

            if (k != i) {
                this.setPower(blockposition1, k, orientation1);
            }
        }

        int k1;
        Orientation orientation2; // CraftBukkit - decompile error

        for (; !this.wiresToTurnOn.isEmpty(); this.propagateChangeToNeighbors(world, blockposition1, k1, orientation2, false)) {
            blockposition1 = (BlockPosition) this.wiresToTurnOn.removeFirst();
            int l1 = this.updatedWires.getInt(blockposition1);
            int i2 = unpackPower(l1);

            i = this.getBlockSignal(world, blockposition1);
            int j2 = this.getIncomingWireSignal(world, blockposition1);

            k1 = Math.max(i, j2);
            orientation2 = unpackOrientation(l1);
            if (k1 > i2) {
                this.setPower(blockposition1, k1, orientation2);
            } else if (k1 < i2) {
                throw new IllegalStateException("Turning off wire while trying to turn it on. Should not happen.");
            }
        }

    }

    private static int packOrientationAndPower(Orientation orientation, int i) {
        return orientation.getIndex() << 4 | i;
    }

    private static Orientation unpackOrientation(int i) {
        return Orientation.fromIndex(i >> 4);
    }

    private static int unpackPower(int i) {
        return i & 15;
    }

    private void setPower(BlockPosition blockposition, int i, Orientation orientation) {
        this.updatedWires.compute(blockposition, (blockposition1, integer) -> {
            return integer == null ? packOrientationAndPower(orientation, i) : packOrientationAndPower(unpackOrientation(integer), i);
        });
    }

    private void propagateChangeToNeighbors(World world, BlockPosition blockposition, int i, Orientation orientation, boolean flag) {
        for (EnumDirection enumdirection : orientation.getHorizontalDirections()) {
            BlockPosition blockposition1 = blockposition.relative(enumdirection);

            this.enqueueNeighborWire(world, blockposition1, i, orientation.withFront(enumdirection), flag);
        }

        for (EnumDirection enumdirection1 : orientation.getVerticalDirections()) {
            BlockPosition blockposition2 = blockposition.relative(enumdirection1);
            boolean flag1 = world.getBlockState(blockposition2).isRedstoneConductor(world, blockposition2);

            for (EnumDirection enumdirection2 : orientation.getHorizontalDirections()) {
                BlockPosition blockposition3 = blockposition.relative(enumdirection2);

                if (enumdirection1 == EnumDirection.UP && !flag1) {
                    BlockPosition blockposition4 = blockposition2.relative(enumdirection2);

                    this.enqueueNeighborWire(world, blockposition4, i, orientation.withFront(enumdirection2), flag);
                } else if (enumdirection1 == EnumDirection.DOWN && !world.getBlockState(blockposition3).isRedstoneConductor(world, blockposition3)) {
                    BlockPosition blockposition5 = blockposition2.relative(enumdirection2);

                    this.enqueueNeighborWire(world, blockposition5, i, orientation.withFront(enumdirection2), flag);
                }
            }
        }

    }

    private void enqueueNeighborWire(World world, BlockPosition blockposition, int i, Orientation orientation, boolean flag) {
        IBlockData iblockdata = world.getBlockState(blockposition);

        if (iblockdata.is(this.wireBlock)) {
            int j = this.getWireSignal(blockposition, iblockdata);

            if (j < i - 1 && !this.wiresToTurnOn.contains(blockposition)) {
                this.wiresToTurnOn.add(blockposition);
                this.setPower(blockposition, j, orientation);
            }

            if (flag && j > i && !this.wiresToTurnOff.contains(blockposition)) {
                this.wiresToTurnOff.add(blockposition);
                this.setPower(blockposition, j, orientation);
            }
        }

    }

    @Override
    protected int getWireSignal(BlockPosition blockposition, IBlockData iblockdata) {
        int i = this.updatedWires.getOrDefault(blockposition, -1);

        return i != -1 ? unpackPower(i) : super.getWireSignal(blockposition, iblockdata);
    }
}

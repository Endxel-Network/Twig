package net.minecraft.world.level.portal;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.BlockUtil;
import net.minecraft.core.BaseBlockPosition;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.village.poi.VillagePlace;
import net.minecraft.world.entity.ai.village.poi.VillagePlaceRecord;
import net.minecraft.world.level.block.BlockPortal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.block.state.properties.BlockProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.HeightMap;

public class PortalTravelAgent {

    public static final int TICKET_RADIUS = 3;
    private static final int NETHER_PORTAL_RADIUS = 16;
    private static final int OVERWORLD_PORTAL_RADIUS = 128;
    private static final int FRAME_HEIGHT = 5;
    private static final int FRAME_WIDTH = 4;
    private static final int FRAME_BOX = 3;
    private static final int FRAME_HEIGHT_START = -1;
    private static final int FRAME_HEIGHT_END = 4;
    private static final int FRAME_WIDTH_START = -1;
    private static final int FRAME_WIDTH_END = 3;
    private static final int FRAME_BOX_START = -1;
    private static final int FRAME_BOX_END = 2;
    private static final int NOTHING_FOUND = -1;
    private final WorldServer level;

    public PortalTravelAgent(WorldServer worldserver) {
        this.level = worldserver;
    }

    public Optional<BlockPosition> findClosestPortalPosition(BlockPosition blockposition, boolean flag, WorldBorder worldborder) {
        // CraftBukkit start
        return findClosestPortalPosition(blockposition, worldborder, flag ? 16 : 128); // Search Radius
    }

    public Optional<BlockPosition> findClosestPortalPosition(BlockPosition blockposition, WorldBorder worldborder, int i) {
        VillagePlace villageplace = this.level.getPoiManager();
        // int i = flag ? 16 : 128;
        // CraftBukkit end

        villageplace.ensureLoadedAndValid(this.level, blockposition, i);
        Stream<BlockPosition> stream = villageplace.getInSquare((holder) -> { // CraftBukkit - decompile error
            return holder.is(PoiTypes.NETHER_PORTAL);
        }, blockposition, i, VillagePlace.Occupancy.ANY).map(VillagePlaceRecord::getPos);

        Objects.requireNonNull(worldborder);
        return stream.filter(worldborder::isWithinBounds).filter((blockposition1) -> {
            return this.level.getBlockState(blockposition1).hasProperty(BlockProperties.HORIZONTAL_AXIS);
        }).min(Comparator.comparingDouble((BlockPosition blockposition1) -> { // CraftBukkit - decompile error
            return blockposition1.distSqr(blockposition);
        }).thenComparingInt(BaseBlockPosition::getY));
    }

    public Optional<BlockUtil.Rectangle> createPortal(BlockPosition blockposition, EnumDirection.EnumAxis enumdirection_enumaxis) {
        // CraftBukkit start
        return this.createPortal(blockposition, enumdirection_enumaxis, null, 16);
    }

    public Optional<BlockUtil.Rectangle> createPortal(BlockPosition blockposition, EnumDirection.EnumAxis enumdirection_enumaxis, net.minecraft.world.entity.Entity entity, int createRadius) {
        // CraftBukkit end
        EnumDirection enumdirection = EnumDirection.get(EnumDirection.EnumAxisDirection.POSITIVE, enumdirection_enumaxis);
        double d0 = -1.0D;
        BlockPosition blockposition1 = null;
        double d1 = -1.0D;
        BlockPosition blockposition2 = null;
        WorldBorder worldborder = this.level.getWorldBorder();
        int i = Math.min(this.level.getMaxY(), this.level.getMinY() + this.level.getLogicalHeight() - 1);
        int j = 1;
        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = blockposition.mutable();

        for (BlockPosition.MutableBlockPosition blockposition_mutableblockposition1 : BlockPosition.spiralAround(blockposition, createRadius, EnumDirection.EAST, EnumDirection.SOUTH)) { // CraftBukkit
            int k = Math.min(i, this.level.getHeight(HeightMap.Type.MOTION_BLOCKING, blockposition_mutableblockposition1.getX(), blockposition_mutableblockposition1.getZ()));

            if (worldborder.isWithinBounds((BlockPosition) blockposition_mutableblockposition1) && worldborder.isWithinBounds((BlockPosition) blockposition_mutableblockposition1.move(enumdirection, 1))) {
                blockposition_mutableblockposition1.move(enumdirection.getOpposite(), 1);

                for (int l = k; l >= this.level.getMinY(); --l) {
                    blockposition_mutableblockposition1.setY(l);
                    if (this.canPortalReplaceBlock(blockposition_mutableblockposition1)) {
                        int i1;

                        for (i1 = l; l > this.level.getMinY() && this.canPortalReplaceBlock(blockposition_mutableblockposition1.move(EnumDirection.DOWN)); --l) {
                            ;
                        }

                        if (l + 4 <= i) {
                            int j1 = i1 - l;

                            if (j1 <= 0 || j1 >= 3) {
                                blockposition_mutableblockposition1.setY(l);
                                if (this.canHostFrame(blockposition_mutableblockposition1, blockposition_mutableblockposition, enumdirection, 0)) {
                                    double d2 = blockposition.distSqr(blockposition_mutableblockposition1);

                                    if (this.canHostFrame(blockposition_mutableblockposition1, blockposition_mutableblockposition, enumdirection, -1) && this.canHostFrame(blockposition_mutableblockposition1, blockposition_mutableblockposition, enumdirection, 1) && (d0 == -1.0D || d0 > d2)) {
                                        d0 = d2;
                                        blockposition1 = blockposition_mutableblockposition1.immutable();
                                    }

                                    if (d0 == -1.0D && (d1 == -1.0D || d1 > d2)) {
                                        d1 = d2;
                                        blockposition2 = blockposition_mutableblockposition1.immutable();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (d0 == -1.0D && d1 != -1.0D) {
            blockposition1 = blockposition2;
            d0 = d1;
        }

        org.bukkit.craftbukkit.util.BlockStateListPopulator blockList = new org.bukkit.craftbukkit.util.BlockStateListPopulator(this.level); // CraftBukkit - Use BlockStateListPopulator
        if (d0 == -1.0D) {
            int k1 = Math.max(this.level.getMinY() - -1, 70);
            int l1 = i - 9;

            if (l1 < k1) {
                return Optional.empty();
            }

            blockposition1 = (new BlockPosition(blockposition.getX() - enumdirection.getStepX() * 1, MathHelper.clamp(blockposition.getY(), k1, l1), blockposition.getZ() - enumdirection.getStepZ() * 1)).immutable();
            blockposition1 = worldborder.clampToBounds(blockposition1);
            EnumDirection enumdirection1 = enumdirection.getClockWise();

            for (int i2 = -1; i2 < 2; ++i2) {
                for (int j2 = 0; j2 < 2; ++j2) {
                    for (int k2 = -1; k2 < 3; ++k2) {
                        IBlockData iblockdata = k2 < 0 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();

                        blockposition_mutableblockposition.setWithOffset(blockposition1, j2 * enumdirection.getStepX() + i2 * enumdirection1.getStepX(), k2, j2 * enumdirection.getStepZ() + i2 * enumdirection1.getStepZ());
                        blockList.setBlock(blockposition_mutableblockposition, iblockdata, 3); // CraftBukkit
                    }
                }
            }
        }

        for (int l2 = -1; l2 < 3; ++l2) {
            for (int i3 = -1; i3 < 4; ++i3) {
                if (l2 == -1 || l2 == 2 || i3 == -1 || i3 == 3) {
                    blockposition_mutableblockposition.setWithOffset(blockposition1, l2 * enumdirection.getStepX(), i3, l2 * enumdirection.getStepZ());
                    blockList.setBlock(blockposition_mutableblockposition, Blocks.OBSIDIAN.defaultBlockState(), 3); // CraftBukkit
                }
            }
        }

        IBlockData iblockdata1 = (IBlockData) Blocks.NETHER_PORTAL.defaultBlockState().setValue(BlockPortal.AXIS, enumdirection_enumaxis);

        for (int j3 = 0; j3 < 2; ++j3) {
            for (int k3 = 0; k3 < 3; ++k3) {
                blockposition_mutableblockposition.setWithOffset(blockposition1, j3 * enumdirection.getStepX(), k3, j3 * enumdirection.getStepZ());
                blockList.setBlock(blockposition_mutableblockposition, iblockdata1, 18); // CraftBukkit
            }
        }

        // CraftBukkit start
        org.bukkit.World bworld = this.level.getWorld();
        org.bukkit.event.world.PortalCreateEvent event = new org.bukkit.event.world.PortalCreateEvent((java.util.List<org.bukkit.block.BlockState>) (java.util.List) blockList.getList(), bworld, (entity == null) ? null : entity.getBukkitEntity(), org.bukkit.event.world.PortalCreateEvent.CreateReason.NETHER_PAIR);

        this.level.getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Optional.empty();
        }
        blockList.updateList();
        // CraftBukkit end
        return Optional.of(new BlockUtil.Rectangle(blockposition1.immutable(), 2, 3));
    }

    private boolean canPortalReplaceBlock(BlockPosition.MutableBlockPosition blockposition_mutableblockposition) {
        IBlockData iblockdata = this.level.getBlockState(blockposition_mutableblockposition);

        return iblockdata.canBeReplaced() && iblockdata.getFluidState().isEmpty();
    }

    private boolean canHostFrame(BlockPosition blockposition, BlockPosition.MutableBlockPosition blockposition_mutableblockposition, EnumDirection enumdirection, int i) {
        EnumDirection enumdirection1 = enumdirection.getClockWise();

        for (int j = -1; j < 3; ++j) {
            for (int k = -1; k < 4; ++k) {
                blockposition_mutableblockposition.setWithOffset(blockposition, enumdirection.getStepX() * j + enumdirection1.getStepX() * i, k, enumdirection.getStepZ() * j + enumdirection1.getStepZ() * i);
                if (k < 0 && !this.level.getBlockState(blockposition_mutableblockposition).isSolid()) {
                    return false;
                }

                if (k >= 0 && !this.canPortalReplaceBlock(blockposition_mutableblockposition)) {
                    return false;
                }
            }
        }

        return true;
    }
}

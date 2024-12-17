package org.bukkit.craftbukkit.inventory.view.builder;

import net.minecraft.core.BlockPosition;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.ITileInventory;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.Containers;
import net.minecraft.world.inventory.ITileEntityContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.state.IBlockData;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.view.builder.LocationInventoryViewBuilder;

public class CraftBlockEntityInventoryViewBuilder<V extends InventoryView> extends CraftAbstractLocationInventoryViewBuilder<V> {

    private final Block block;
    private final CraftTileInventoryBuilder builder;

    public CraftBlockEntityInventoryViewBuilder(final Containers<?> handle, final Block block, final CraftTileInventoryBuilder builder) {
        super(handle);
        this.block = block;
        this.builder = builder;
    }

    @Override
    protected Container buildContainer(final EntityPlayer player) {
        if (this.world == null) {
            this.world = player.level();
        }

        if (this.position == null) {
            this.position = player.blockPosition();
        }

        final TileEntity entity = this.world.getBlockEntity(position);
        if (!(entity instanceof ITileEntityContainer container)) {
            return buildFakeTile(player);
        }

        final Container atBlock = container.createMenu(player.nextContainerCounter(), player.getInventory(), player);
        if (atBlock.getType() != super.handle) {
            return buildFakeTile(player);
        }

        return atBlock;
    }

    private Container buildFakeTile(EntityPlayer player) {
        if (this.builder == null) {
            return handle.create(player.nextContainerCounter(), player.getInventory());
        }
        final ITileInventory inventory = this.builder.build(this.position, this.block.defaultBlockState());
        if (inventory instanceof TileEntity tile) {
            tile.setLevel(this.world);
        }
        return inventory.createMenu(player.nextContainerCounter(), player.getInventory(), player);
    }

    @Override
    public LocationInventoryViewBuilder<V> copy() {
        final CraftBlockEntityInventoryViewBuilder<V> copy = new CraftBlockEntityInventoryViewBuilder<>(super.handle, this.block, this.builder);
        copy.world = this.world;
        copy.position = this.position;
        copy.checkReachable = super.checkReachable;
        copy.title = title;
        return copy;
    }

    public interface CraftTileInventoryBuilder {

        ITileInventory build(BlockPosition blockPosition, IBlockData blockData);
    }
}

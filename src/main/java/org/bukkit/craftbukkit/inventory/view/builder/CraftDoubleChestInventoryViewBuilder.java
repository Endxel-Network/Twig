package org.bukkit.craftbukkit.inventory.view.builder;

import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.ITileInventory;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.Containers;
import net.minecraft.world.level.block.BlockChest;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoubleBlockFinder;
import net.minecraft.world.level.block.entity.TileEntityChest;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.view.builder.LocationInventoryViewBuilder;

public class CraftDoubleChestInventoryViewBuilder<V extends InventoryView> extends CraftAbstractLocationInventoryViewBuilder<V> {

    public CraftDoubleChestInventoryViewBuilder(final Containers<?> handle) {
        super(handle);
    }

    @Override
    protected Container buildContainer(final EntityPlayer player) {
        if (super.world == null) {
            return handle.create(player.nextContainerCounter(), player.getInventory());
        }

        BlockChest chest = (BlockChest) Blocks.CHEST;
        final DoubleBlockFinder.Result<? extends TileEntityChest> result = chest.combine(super.world.getBlockState(super.position), super.world, super.position, false);
        if (result instanceof DoubleBlockFinder.Result.Single<? extends TileEntityChest>) {
            return handle.create(player.nextContainerCounter(), player.getInventory());
        }

        final ITileInventory combined = result.apply(BlockChest.MENU_PROVIDER_COMBINER).orElse(null);
        if (combined == null) {
            return handle.create(player.nextContainerCounter(), player.getInventory());
        }
        return combined.createMenu(player.nextContainerCounter(), player.getInventory(), player);
    }

    @Override
    public LocationInventoryViewBuilder<V> copy() {
        final CraftDoubleChestInventoryViewBuilder<V> copy = new CraftDoubleChestInventoryViewBuilder<>(super.handle);
        copy.world = this.world;
        copy.position = this.position;
        copy.checkReachable = super.checkReachable;
        copy.title = title;
        return copy;
    }
}

package org.bukkit.craftbukkit.inventory.view.builder;

import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerAccess;
import net.minecraft.world.inventory.Containers;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.view.builder.LocationInventoryViewBuilder;

public class CraftAccessLocationInventoryViewBuilder<V extends InventoryView> extends CraftAbstractLocationInventoryViewBuilder<V> {

    private final CraftAccessContainerObjectBuilder containerBuilder;

    public CraftAccessLocationInventoryViewBuilder(final Containers<?> handle, CraftAccessContainerObjectBuilder containerBuilder) {
        super(handle);
        this.containerBuilder = containerBuilder;
    }

    @Override
    protected Container buildContainer(final EntityPlayer player) {
        ContainerAccess access;
        if (super.position == null) {
            access = ContainerAccess.create(player.level(), player.blockPosition());
        } else {
            access = ContainerAccess.create(super.world, super.position);
        }

        return this.containerBuilder.build(player.nextContainerCounter(), player.getInventory(), access);
    }

    @Override
    public LocationInventoryViewBuilder<V> copy() {
        CraftAccessLocationInventoryViewBuilder<V> copy = new CraftAccessLocationInventoryViewBuilder<>(this.handle, this.containerBuilder);
        copy.world = super.world;
        copy.position = super.position;
        copy.checkReachable = super.checkReachable;
        copy.title = title;
        return copy;
    }

    public interface CraftAccessContainerObjectBuilder {

        Container build(final int syncId, final PlayerInventory inventory, ContainerAccess access);
    }
}

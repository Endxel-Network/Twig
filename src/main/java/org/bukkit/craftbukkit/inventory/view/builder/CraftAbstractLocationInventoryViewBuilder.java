package org.bukkit.craftbukkit.inventory.view.builder;

import com.google.common.base.Preconditions;
import net.minecraft.core.BlockPosition;
import net.minecraft.world.inventory.Containers;
import net.minecraft.world.level.World;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.view.builder.LocationInventoryViewBuilder;
import org.jetbrains.annotations.NotNull;

public abstract class CraftAbstractLocationInventoryViewBuilder<V extends InventoryView> extends CraftAbstractInventoryViewBuilder<V> implements LocationInventoryViewBuilder<V> {

    protected World world;
    protected BlockPosition position;

    public CraftAbstractLocationInventoryViewBuilder(final Containers<?> handle) {
        super(handle);
    }

    @Override
    public LocationInventoryViewBuilder<V> title(@NotNull final String title) {
        return (LocationInventoryViewBuilder<V>) super.title(title);
    }

    @Override
    public LocationInventoryViewBuilder<V> copy() {
        throw new UnsupportedOperationException("copy is not implemented on CraftAbstractLocationInventoryViewBuilder");
    }

    @Override
    public LocationInventoryViewBuilder<V> checkReachable(final boolean checkReachable) {
        super.checkReachable = checkReachable;
        return this;
    }

    @Override
    public LocationInventoryViewBuilder<V> location(final Location location) {
        Preconditions.checkArgument(location != null, "The provided location must not be null");
        Preconditions.checkArgument(location.getWorld() != null, "The provided location must be associated with a world");
        this.world = ((CraftWorld) location.getWorld()).getHandle();
        this.position = CraftLocation.toBlockPosition(location);
        return this;
    }
}

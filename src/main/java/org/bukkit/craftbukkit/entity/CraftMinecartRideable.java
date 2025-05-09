package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.vehicle.EntityMinecartRideable;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.minecart.RideableMinecart;

public class CraftMinecartRideable extends CraftMinecart implements RideableMinecart {
    public CraftMinecartRideable(CraftServer server, EntityMinecartRideable entity) {
        super(server, entity);
    }

    @Override
    public String toString() {
        return "CraftMinecartRideable";
    }
}

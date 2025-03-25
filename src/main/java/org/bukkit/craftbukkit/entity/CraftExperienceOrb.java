package org.bukkit.craftbukkit.entity;

import net.minecraft.world.entity.EntityExperienceOrb;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.ExperienceOrb;

public class CraftExperienceOrb extends CraftEntity implements ExperienceOrb {
    public CraftExperienceOrb(CraftServer server, EntityExperienceOrb entity) {
        super(server, entity);
    }

    @Override
    public int getExperience() {
        return getHandle().getValue();
    }

    @Override
    public void setExperience(int value) {
        getHandle().setValue(value);
    }

    @Override
    public EntityExperienceOrb getHandle() {
        return (EntityExperienceOrb) entity;
    }

    @Override
    public String toString() {
        return "CraftExperienceOrb";
    }
}

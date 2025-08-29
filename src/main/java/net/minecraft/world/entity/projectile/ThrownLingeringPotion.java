package net.minecraft.world.entity.projectile;

import javax.annotation.Nullable;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAreaEffectCloud;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.World;

// CraftBukkit start
import net.minecraft.world.phys.MovingObjectPosition;
// CraftBukkit end

public class ThrownLingeringPotion extends EntityPotion {

    public ThrownLingeringPotion(EntityTypes<? extends ThrownLingeringPotion> entitytypes, World world) {
        super(entitytypes, world);
    }

    public ThrownLingeringPotion(World world, EntityLiving entityliving, ItemStack itemstack) {
        super(EntityTypes.LINGERING_POTION, world, entityliving, itemstack);
    }

    public ThrownLingeringPotion(World world, double d0, double d1, double d2, ItemStack itemstack) {
        super(EntityTypes.LINGERING_POTION, world, d0, d1, d2, itemstack);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.LINGERING_POTION;
    }

    @Override
    public void onHitAsPotion(WorldServer worldserver, ItemStack itemstack, @Nullable Entity entity, MovingObjectPosition position) { // CraftBukkit - Pass MovingObjectPosition
        EntityAreaEffectCloud entityareaeffectcloud = new EntityAreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        Entity entity1 = this.getOwner();

        if (entity1 instanceof EntityLiving entityliving) {
            entityareaeffectcloud.setOwner(entityliving);
        }

        entityareaeffectcloud.setRadius(3.0F);
        entityareaeffectcloud.setRadiusOnUse(-0.5F);
        entityareaeffectcloud.setDuration(600);
        entityareaeffectcloud.setWaitTime(10);
        entityareaeffectcloud.setRadiusPerTick(-entityareaeffectcloud.getRadius() / (float) entityareaeffectcloud.getDuration());
        entityareaeffectcloud.applyComponentsFromItemStack(itemstack);
        // CraftBukkit start
        org.bukkit.event.entity.LingeringPotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callLingeringPotionSplashEvent(this, position, entityareaeffectcloud);
        if (!(event.isCancelled() || entityareaeffectcloud.isRemoved())) {
            this.level().addFreshEntity(entityareaeffectcloud);
        } else {
            entityareaeffectcloud.discard(null); // CraftBukkit - add Bukkit remove cause
        }
        // CraftBukkit end
    }
}

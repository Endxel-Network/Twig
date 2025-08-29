package net.minecraft.world.entity.projectile;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.World;
import net.minecraft.world.phys.AxisAlignedBB;

// CraftBukkit start
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.MovingObjectPosition;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
// CraftBukkit end

public class ThrownSplashPotion extends EntityPotion {

    public ThrownSplashPotion(EntityTypes<? extends ThrownSplashPotion> entitytypes, World world) {
        super(entitytypes, world);
    }

    public ThrownSplashPotion(World world, EntityLiving entityliving, ItemStack itemstack) {
        super(EntityTypes.SPLASH_POTION, world, entityliving, itemstack);
    }

    public ThrownSplashPotion(World world, double d0, double d1, double d2, ItemStack itemstack) {
        super(EntityTypes.SPLASH_POTION, world, d0, d1, d2, itemstack);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SPLASH_POTION;
    }

    @Override
    public void onHitAsPotion(WorldServer worldserver, ItemStack itemstack, @Nullable Entity entity, MovingObjectPosition position) { // CraftBukkit - Pass MovingObjectPosition
        PotionContents potioncontents = (PotionContents) itemstack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        float f = (Float) itemstack.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F);
        Iterable<MobEffect> iterable = potioncontents.getAllEffects();
        AxisAlignedBB axisalignedbb = this.getBoundingBox().inflate(4.0D, 2.0D, 4.0D);
        List<EntityLiving> list = this.level().<EntityLiving>getEntitiesOfClass(EntityLiving.class, axisalignedbb);
        Map<LivingEntity, Double> affected = new HashMap<LivingEntity, Double>(); // CraftBukkit

        if (!list.isEmpty()) {
            Entity entity1 = this.getEffectSource();

            for (EntityLiving entityliving : list) {
                if (entityliving.isAffectedByPotions()) {
                    double d0 = this.distanceToSqr((Entity) entityliving);

                    if (d0 < 16.0D) {
                        double d1;

                        if (entityliving == entity) {
                            d1 = 1.0D;
                        } else {
                            d1 = 1.0D - Math.sqrt(d0) / 4.0D;
                        }

                        // CraftBukkit start
                        affected.put((LivingEntity) entityliving.getBukkitEntity(), d1);
                    }
                }
            }
        }

        {
            {
                org.bukkit.event.entity.PotionSplashEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callPotionSplashEvent(this, position, affected);
                if (!event.isCancelled() && list != null && !list.isEmpty()) { // do not process effects if there are no effects to process
                    Entity entity1 = this.getEffectSource();
                    for (LivingEntity victim : event.getAffectedEntities()) {
                        if (!(victim instanceof CraftLivingEntity)) {
                            continue;
                        }

                        EntityLiving entityliving = ((CraftLivingEntity) victim).getHandle();
                        double d1 = event.getIntensity(victim);
                        // CraftBukkit end

                        for (MobEffect mobeffect : iterable) {
                            Holder<MobEffectList> holder = mobeffect.getEffect();
                            // CraftBukkit start - Abide by PVP settings - for players only!
                            if (!this.level().pvpMode && this.getOwner() instanceof EntityPlayer && entityliving instanceof EntityPlayer && entityliving != this.getOwner()) {
                                MobEffectList mobeffectlist = (MobEffectList) holder.value();
                                if (mobeffectlist == MobEffects.SLOWNESS || mobeffectlist == MobEffects.MINING_FATIGUE || mobeffectlist == MobEffects.INSTANT_DAMAGE || mobeffectlist == MobEffects.BLINDNESS
                                        || mobeffectlist == MobEffects.HUNGER || mobeffectlist == MobEffects.WEAKNESS || mobeffectlist == MobEffects.POISON) {
                                    continue;
                                }
                            }
                            // CraftBukkit end

                            if (((MobEffectList) holder.value()).isInstantenous()) {
                                ((MobEffectList) holder.value()).applyInstantenousEffect(worldserver, this, this.getOwner(), entityliving, mobeffect.getAmplifier(), d1);
                            } else {
                                int i = mobeffect.mapDuration((j) -> {
                                    return (int) (d1 * (double) j * (double) f + 0.5D);
                                });
                                MobEffect mobeffect1 = new MobEffect(holder, i, mobeffect.getAmplifier(), mobeffect.isAmbient(), mobeffect.isVisible());

                                if (!mobeffect1.endsWithin(20)) {
                                    entityliving.addEffect(mobeffect1, entity1, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.POTION_SPLASH); // CraftBukkit
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}

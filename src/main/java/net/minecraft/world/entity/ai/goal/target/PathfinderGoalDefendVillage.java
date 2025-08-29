package net.minecraft.world.entity.ai.goal.target;

import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.animal.EntityIronGolem;
import net.minecraft.world.entity.npc.EntityVillager;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.phys.AxisAlignedBB;

public class PathfinderGoalDefendVillage extends PathfinderGoalTarget {

    private final EntityIronGolem golem;
    @Nullable
    private EntityLiving potentialTarget;
    private final PathfinderTargetCondition attackTargeting = PathfinderTargetCondition.forCombat().range(64.0D);

    public PathfinderGoalDefendVillage(EntityIronGolem entityirongolem) {
        super(entityirongolem, false, true);
        this.golem = entityirongolem;
        this.setFlags(EnumSet.of(PathfinderGoal.Type.TARGET));
    }

    @Override
    public boolean canUse() {
        AxisAlignedBB axisalignedbb = this.golem.getBoundingBox().inflate(10.0D, 8.0D, 10.0D);
        WorldServer worldserver = getServerLevel((Entity) this.golem);
        List<? extends EntityLiving> list = worldserver.getNearbyEntities(EntityVillager.class, this.attackTargeting, this.golem, axisalignedbb); // CraftBukkit - decompile error
        List<EntityHuman> list1 = worldserver.getNearbyPlayers(this.attackTargeting, this.golem, axisalignedbb);

        for (EntityLiving entityliving : list) {
            EntityVillager entityvillager = (EntityVillager) entityliving;

            for (EntityHuman entityhuman : list1) {
                int i = entityvillager.getPlayerReputation(entityhuman);

                if (i <= -100) {
                    this.potentialTarget = entityhuman;
                }
            }
        }

        if (this.potentialTarget == null) {
            return false;
        } else {
            EntityLiving entityliving1 = this.potentialTarget;

            if (entityliving1 instanceof EntityHuman) {
                EntityHuman entityhuman1 = (EntityHuman) entityliving1;

                if (entityhuman1.isSpectator() || entityhuman1.isCreative()) {
                    return false;
                }
            }

            return true;
        }
    }

    @Override
    public void start() {
        this.golem.setTarget(this.potentialTarget, org.bukkit.event.entity.EntityTargetEvent.TargetReason.DEFEND_VILLAGE, true); // CraftBukkit - reason
        super.start();
    }
}

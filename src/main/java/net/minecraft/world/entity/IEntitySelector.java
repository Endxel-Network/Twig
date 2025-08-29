package net.minecraft.world.entity;

import com.google.common.base.Predicates;
import java.util.function.Predicate;
import net.minecraft.world.IInventory;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.scores.ScoreboardTeamBase;

public final class IEntitySelector {

    public static final Predicate<Entity> ENTITY_STILL_ALIVE = Entity::isAlive;
    public static final Predicate<Entity> LIVING_ENTITY_STILL_ALIVE = (entity) -> {
        return entity.isAlive() && entity instanceof EntityLiving;
    };
    public static final Predicate<Entity> ENTITY_NOT_BEING_RIDDEN = (entity) -> {
        return entity.isAlive() && !entity.isVehicle() && !entity.isPassenger();
    };
    public static final Predicate<Entity> CONTAINER_ENTITY_SELECTOR = (entity) -> {
        return entity instanceof IInventory && entity.isAlive();
    };
    public static final Predicate<Entity> NO_CREATIVE_OR_SPECTATOR = (entity) -> {
        boolean flag;

        if (entity instanceof EntityHuman entityhuman) {
            if (entity.isSpectator() || entityhuman.isCreative()) {
                flag = false;
                return flag;
            }
        }

        flag = true;
        return flag;
    };
    public static final Predicate<Entity> NO_SPECTATORS = (entity) -> {
        return !entity.isSpectator();
    };
    public static final Predicate<Entity> CAN_BE_COLLIDED_WITH = IEntitySelector.NO_SPECTATORS.and(Entity::canBeCollidedWith);
    public static final Predicate<Entity> CAN_BE_PICKED = IEntitySelector.NO_SPECTATORS.and(Entity::isPickable);

    private IEntitySelector() {}

    public static Predicate<Entity> withinDistance(double d0, double d1, double d2, double d3) {
        double d4 = d3 * d3;

        return (entity) -> {
            return entity != null && entity.distanceToSqr(d0, d1, d2) <= d4;
        };
    }

    public static Predicate<Entity> pushableBy(Entity entity) {
        ScoreboardTeamBase scoreboardteambase = entity.getTeam();
        ScoreboardTeamBase.EnumTeamPush scoreboardteambase_enumteampush = scoreboardteambase == null ? ScoreboardTeamBase.EnumTeamPush.ALWAYS : scoreboardteambase.getCollisionRule();

        return (Predicate<Entity>) (scoreboardteambase_enumteampush == ScoreboardTeamBase.EnumTeamPush.NEVER ? Predicates.alwaysFalse() : IEntitySelector.NO_SPECTATORS.and((entity1) -> {
            if (!entity1.canCollideWithBukkit(entity) || !entity.canCollideWithBukkit(entity1)) { // CraftBukkit - collidable API
                return false;
            } else {
                if (entity.level().isClientSide) {
                    if (!(entity1 instanceof EntityHuman)) {
                        return false;
                    }

                    EntityHuman entityhuman = (EntityHuman) entity1;

                    if (!entityhuman.isLocalPlayer()) {
                        return false;
                    }
                }

                ScoreboardTeamBase scoreboardteambase1 = entity1.getTeam();
                ScoreboardTeamBase.EnumTeamPush scoreboardteambase_enumteampush1 = scoreboardteambase1 == null ? ScoreboardTeamBase.EnumTeamPush.ALWAYS : scoreboardteambase1.getCollisionRule();

                if (scoreboardteambase_enumteampush1 == ScoreboardTeamBase.EnumTeamPush.NEVER) {
                    return false;
                } else {
                    boolean flag = scoreboardteambase != null && scoreboardteambase.isAlliedTo(scoreboardteambase1);

                    if ((scoreboardteambase_enumteampush == ScoreboardTeamBase.EnumTeamPush.PUSH_OWN_TEAM || scoreboardteambase_enumteampush1 == ScoreboardTeamBase.EnumTeamPush.PUSH_OWN_TEAM) && flag) {
                        return false;
                    } else if ((scoreboardteambase_enumteampush == ScoreboardTeamBase.EnumTeamPush.PUSH_OTHER_TEAMS || scoreboardteambase_enumteampush1 == ScoreboardTeamBase.EnumTeamPush.PUSH_OTHER_TEAMS) && !flag) {
                        return false;
                    } else {
                        return true;
                    }
                }
            }
        }));
    }

    public static Predicate<Entity> notRiding(Entity entity) {
        return (entity1) -> {
            while (true) {
                if (entity1.isPassenger()) {
                    entity1 = entity1.getVehicle();
                    if (entity1 != entity) {
                        continue;
                    }

                    return false;
                }

                return true;
            }
        };
    }
}

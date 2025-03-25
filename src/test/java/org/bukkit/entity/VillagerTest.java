package org.bukkit.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import java.util.UUID;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.ai.gossip.Reputation;
import net.minecraft.world.entity.ai.gossip.ReputationType;
import net.minecraft.world.entity.npc.EntityVillager;
import net.minecraft.world.flag.FeatureFlags;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.entity.CraftEntityTypes;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

@AllFeatures
public class VillagerTest {

    @Test
    public void getReputation() {
        Villager villager = createVillager();
        UUID uuid = UUID.randomUUID();
        villager.setReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, 20);
        villager.setReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, 10);
        assertEquals(20, villager.getReputation(uuid, Villager.ReputationType.MINOR_POSITIVE), "getReputation should return correct value for a single reputation type");
        assertEquals(10, villager.getReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE), "getReputation should return correct value for a single reputation type");
        assertEquals(10, villager.getReputation(uuid), "getReputation should return correct value for total weighted reputation");
    }

    @Test
    public void getWeightedReputation() {
        Villager villager = createVillager();
        UUID uuid = UUID.randomUUID();
        villager.setReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, 20);
        villager.setReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, 10);
        assertEquals(20, villager.getWeightedReputation(uuid, Villager.ReputationType.MINOR_POSITIVE), "getWeightedReputation should return correct value for a single reputation type");
        assertEquals(-10, villager.getWeightedReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE), "getWeightedReputation should return correct value for a single reputation type");
    }

    @Test
    public void addReputation() {
        Villager villager = createVillager();
        UUID uuid = UUID.randomUUID();
        villager.setReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, 20);
        villager.setReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, 10);
        villager.addReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, 3);
        villager.addReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, 20);
        assertEquals(23, villager.getReputation(uuid, Villager.ReputationType.MINOR_POSITIVE), "addReputation should increase value by given amount");
        assertEquals(30, villager.getReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE), "addReputation should increase value by given amount");

        villager.addReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, ReputationType.MINOR_POSITIVE.max);
        villager.addReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, ReputationType.MINOR_NEGATIVE.max);
        assertEquals(ReputationType.MINOR_POSITIVE.max, villager.getReputation(uuid, Villager.ReputationType.MINOR_POSITIVE), "addReputation should not exceed maximum value");
        assertEquals(ReputationType.MINOR_NEGATIVE.max, villager.getReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE), "addReputation should not exceed maximum value");
    }

    @Test
    public void removeReputation() {
        Villager villager = createVillager();
        UUID uuid = UUID.randomUUID();
        villager.setReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, 20);
        villager.setReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, 10);
        villager.removeReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, 5);
        villager.removeReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, 2);
        assertEquals(15, villager.getReputation(uuid, Villager.ReputationType.MINOR_POSITIVE), "removeReputation should decrease value by given amount");
        assertEquals(8, villager.getReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE), "removeReputation should decrease value by given amount");

        villager.removeReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, 15 - Reputation.DISCARD_THRESHOLD + 1);
        villager.removeReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, Integer.MAX_VALUE);
        assertEquals(0, villager.getReputation(uuid, Villager.ReputationType.MINOR_POSITIVE), "removeReputation should cause reputation removal if value drops below discard threshold");
        assertEquals(0, villager.getReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE), "removeReputation should cause reputation removal if value drops below discard threshold");
    }

    @Test
    public void setReputation() {
        Villager villager = createVillager();
        UUID uuid = UUID.randomUUID();
        villager.setReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, 20);
        villager.setReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, 10);
        villager.setReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, 5);
        villager.setReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, 2);
        assertEquals(5, villager.getReputation(uuid, Villager.ReputationType.MINOR_POSITIVE), "setReputation should set value to given amount");
        assertEquals(2, villager.getReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE), "setReputation should set value to given amount");

        villager.setReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, Reputation.DISCARD_THRESHOLD - 1);
        villager.setReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, Integer.MIN_VALUE);
        assertEquals(0, villager.getReputation(uuid, Villager.ReputationType.MINOR_POSITIVE), "setReputation should cause reputation removal if value drops below discard threshold");
        assertEquals(0, villager.getReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE), "setReputation should cause reputation removal if value drops below discard threshold");

        villager.setReputation(uuid, Villager.ReputationType.MINOR_POSITIVE, ReputationType.MINOR_POSITIVE.max + 1);
        villager.setReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE, Integer.MAX_VALUE);
        assertEquals(ReputationType.MINOR_POSITIVE.max, villager.getReputation(uuid, Villager.ReputationType.MINOR_POSITIVE), "setReputation should be clamped to reputation type maximum value");
        assertEquals(ReputationType.MINOR_NEGATIVE.max, villager.getReputation(uuid, Villager.ReputationType.MINOR_NEGATIVE), "setReputation should be clamped to reputation type maximum value");
    }

    private static Villager createVillager() {
        World world = mock(withSettings().stubOnly());
        WorldServer worldServer = mock(withSettings().stubOnly());
        when(worldServer.getMinecraftWorld()).thenReturn(worldServer);
        when(worldServer.enabledFeatures()).thenReturn(FeatureFlags.VANILLA_SET);
        when(worldServer.registryAccess()).thenReturn(CraftRegistry.getMinecraftRegistry());
        Location location = new Location(world, 0, 0, 0, 0, 0);
        CraftEntityTypes.SpawnData spawnData = new CraftEntityTypes.SpawnData(worldServer, location, false, false);
        EntityVillager entityVillager = (EntityVillager) CraftEntityTypes.getEntityTypeData(EntityType.VILLAGER).spawnFunction().apply(spawnData);
        return (Villager) entityVillager.getBukkitEntity();
    }
}

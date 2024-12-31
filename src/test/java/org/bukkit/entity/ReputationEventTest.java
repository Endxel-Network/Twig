package org.bukkit.entity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.ai.village.ReputationEvent;
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
public class ReputationEventTest {

    @Test
    public void toBukkit() throws IllegalAccessException {
        List<ReputationEvent> reputationEvents = getConstants(ReputationEvent.class);
        List<Villager.ReputationEvent> bukkitReputationEvents = getConstants(Villager.ReputationEvent.class);
        for (ReputationEvent reputationEvent : reputationEvents) {
            Villager.ReputationEvent bukkit = CraftVillager.CraftReputationEvent.minecraftToBukkit(reputationEvent);
            assertNotNull(bukkit, "Reputation event " + reputationEvent.toString() + " should have a Bukkit equivalent");
            assertTrue(bukkitReputationEvents.contains(bukkit), "Reputation event " + reputationEvent.toString() + " should have a Bukkit equivalent");
        }
    }

    @Test
    public void toMinecraft() throws IllegalAccessException {
        List<ReputationEvent> reputationEvents = getConstants(ReputationEvent.class);
        List<Villager.ReputationEvent> bukkitReputationEvents = getConstants(Villager.ReputationEvent.class);
        for (Villager.ReputationEvent reputationEvent : bukkitReputationEvents) {
            ReputationEvent minecraft = CraftVillager.CraftReputationEvent.bukkitToMinecraft(reputationEvent);
            assertNotNull(minecraft, "Reputation event " + reputationEvent.toString() + " should have a Minecraft equivalent");
            assertTrue(reputationEvents.contains(minecraft), "Reputation event " + reputationEvent + " should have a Minecraft equivalent");
        }
    }

    private static boolean isPublicStaticFinal(Field field) {
        return Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()) && Modifier.isFinal(field.getModifiers());
    }

    private static <T> List<T> getConstants(Class<T> clazz) throws IllegalAccessException {
        List<T> list = new ArrayList<>();
        for (Field field : clazz.getFields()) {
            if (isPublicStaticFinal(field) && clazz.isAssignableFrom(field.getType())) {
                list.add((T) field.get(null));
            }
        }
        return list;
    }
}

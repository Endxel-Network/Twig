package org.bukkit.entity;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.ai.gossip.ReputationType;
import org.bukkit.craftbukkit.entity.CraftVillager;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

@Normal
public class ReputationTypeTest {

    @Test
    public void toBukkit() {
        for (ReputationType reputationType : ReputationType.values()) {
            assertNotNull(CraftVillager.CraftReputationType.minecraftToBukkit(reputationType), "ReputationType." + reputationType.name() + ".toBukkit() should not be null");
        }
    }

    @Test
    public void fromBukkit() throws IllegalAccessException {
        for (Villager.ReputationType reputationType : getConstants(Villager.ReputationType.class)) {
            assertNotNull(CraftVillager.CraftReputationType.bukkitToMinecraft(reputationType), "ReputationType.fromBukkit(Villager.ReputationType) should not be null");
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

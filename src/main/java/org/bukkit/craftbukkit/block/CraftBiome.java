package org.bukkit.craftbukkit.block;

import net.minecraft.core.Holder;
import net.minecraft.core.IRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeBase;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.registry.CraftOldEnumRegistryItem;
import org.jetbrains.annotations.NotNull;

public class CraftBiome extends CraftOldEnumRegistryItem<Biome, BiomeBase> implements Biome {

    private static int count = 0;

    public static Biome minecraftToBukkit(BiomeBase minecraft) {
        return CraftRegistry.minecraftToBukkit(minecraft, Registries.BIOME, Registry.BIOME);
    }

    public static Biome minecraftHolderToBukkit(Holder<BiomeBase> minecraft) {
        return minecraftToBukkit(minecraft.value());
    }

    public static BiomeBase bukkitToMinecraft(Biome bukkit) {
        if (bukkit == Biome.CUSTOM) {
            return null;
        }

        return CraftRegistry.bukkitToMinecraft(bukkit);
    }

    public static Holder<BiomeBase> bukkitToMinecraftHolder(Biome bukkit) {
        if (bukkit == Biome.CUSTOM) {
            return null;
        }

        IRegistry<BiomeBase> registry = CraftRegistry.getMinecraftRegistry(Registries.BIOME);

        if (registry.wrapAsHolder(bukkitToMinecraft(bukkit)) instanceof Holder.c<BiomeBase> holder) {
            return holder;
        }

        throw new IllegalArgumentException("No Reference holder found for " + bukkit
                + ", this can happen if a plugin creates its own biome base with out properly registering it.");
    }

    public CraftBiome(NamespacedKey key, Holder<BiomeBase> handle) {
        super(key, handle, count++);
    }

    @NotNull
    @Override
    public NamespacedKey getKey() {
        return getKeyOrThrow();
    }
}

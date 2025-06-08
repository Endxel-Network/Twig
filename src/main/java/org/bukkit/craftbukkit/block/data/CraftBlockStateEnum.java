package org.bukkit.craftbukkit.block.data;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.core.EnumDirection;
import net.minecraft.util.INamable;
import net.minecraft.world.level.block.state.properties.BlockStateEnum;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.block.CraftBlock;

public record CraftBlockStateEnum<N extends Enum<N> & INamable, B extends Enum<B>>(BlockStateEnum<N> nms, Class<B> bukkit, N[] nmsValues, B[] bukkitValues) {

    public CraftBlockStateEnum(BlockStateEnum<N> nms, Class<B> bukkit) {
        this(nms, bukkit, nms.getValueClass().getEnumConstants(), bukkit.getEnumConstants());
    }

    /**
     * Convert an NMS Enum (usually a BlockStateEnum) to its appropriate Bukkit
     * enum from the given class.
     *
     * @throws IllegalStateException if the Enum could not be converted
     */
    B toBukkit(N nms) {
        if (nms instanceof EnumDirection) {
            return (B) CraftBlock.notchToBlockFace((EnumDirection) nms);
        }
        return bukkitValues[nms.ordinal()];
    }

    /**
     * Convert a given Bukkit enum to its matching NMS enum type.
     *
     * @param bukkit the Bukkit enum to convert
     * @param nms the NMS class
     * @return the matching NMS type
     * @throws IllegalStateException if the Enum could not be converted
     */
    N toNMS(B bukkit) {
        if (bukkit instanceof BlockFace) {
            return (N) CraftBlock.blockFaceToNotch((BlockFace) bukkit);
        }
        return nmsValues[bukkit.ordinal()];
    }

    /**
     * Convert all values from the given BlockStateEnum to their appropriate
     * Bukkit counterpart.
     *
     * @param nms the NMS state to get values from
     * @param <B> the Bukkit type
     * @param <N> the NMS type
     * @return an immutable Set of values in their appropriate Bukkit type
     */
    Set<B> getValues() {
        ImmutableSet.Builder<B> values = ImmutableSet.builder();

        for (N e : nms.getPossibleValues()) {
            values.add(toBukkit(e));
        }

        return values.build();
    }
}

package org.bukkit.craftbukkit.block.data.type;

import org.bukkit.block.data.type.TestBlock;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

public abstract class CraftTestBlock extends CraftBlockData implements TestBlock {

    private static final net.minecraft.world.level.block.state.properties.BlockStateEnum<?> MODE = getEnum("mode");

    @Override
    public org.bukkit.block.data.type.TestBlock.Mode getMode() {
        return get(MODE, org.bukkit.block.data.type.TestBlock.Mode.class);
    }

    @Override
    public void setMode(org.bukkit.block.data.type.TestBlock.Mode mode) {
        set(MODE, mode);
    }
}

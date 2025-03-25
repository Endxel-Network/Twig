/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftTest extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.type.TestBlock {

    public CraftTest() {
        super();
    }

    public CraftTest(net.minecraft.world.level.block.state.IBlockData state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.type.CraftTestBlock

    private static final net.minecraft.world.level.block.state.properties.BlockStateEnum<?> MODE = getEnum(net.minecraft.world.level.block.TestBlock.class, "mode");

    @Override
    public org.bukkit.block.data.type.TestBlock.Mode getMode() {
        return get(MODE, org.bukkit.block.data.type.TestBlock.Mode.class);
    }

    @Override
    public void setMode(org.bukkit.block.data.type.TestBlock.Mode mode) {
        set(MODE, mode);
    }
}

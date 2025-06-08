/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftStepAbstract extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.type.Slab, org.bukkit.block.data.Waterlogged {

    public CraftStepAbstract() {
        super();
    }

    public CraftStepAbstract(net.minecraft.world.level.block.state.IBlockData state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.type.CraftSlab

    private static final org.bukkit.craftbukkit.block.data.CraftBlockStateEnum<?, org.bukkit.block.data.type.Slab.Type> TYPE = getEnum(net.minecraft.world.level.block.BlockStepAbstract.class, "type", org.bukkit.block.data.type.Slab.Type.class);

    @Override
    public org.bukkit.block.data.type.Slab.Type getType() {
        return get(TYPE);
    }

    @Override
    public void setType(org.bukkit.block.data.type.Slab.Type type) {
        set(TYPE, type);
    }

    // org.bukkit.craftbukkit.block.data.CraftWaterlogged

    private static final net.minecraft.world.level.block.state.properties.BlockStateBoolean WATERLOGGED = getBoolean(net.minecraft.world.level.block.BlockStepAbstract.class, "waterlogged");

    @Override
    public boolean isWaterlogged() {
        return get(WATERLOGGED);
    }

    @Override
    public void setWaterlogged(boolean waterlogged) {
        set(WATERLOGGED, waterlogged);
    }
}

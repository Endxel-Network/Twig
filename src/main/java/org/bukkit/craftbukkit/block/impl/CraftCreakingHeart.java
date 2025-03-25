/**
 * Automatically generated file, changes will be lost.
 */
package org.bukkit.craftbukkit.block.impl;

public final class CraftCreakingHeart extends org.bukkit.craftbukkit.block.data.CraftBlockData implements org.bukkit.block.data.type.CreakingHeart, org.bukkit.block.data.Orientable {

    public CraftCreakingHeart() {
        super();
    }

    public CraftCreakingHeart(net.minecraft.world.level.block.state.IBlockData state) {
        super(state);
    }

    // org.bukkit.craftbukkit.block.data.type.CraftCreakingHeart

    private static final net.minecraft.world.level.block.state.properties.BlockStateEnum<?> CREAKING_HEART_STATE = getEnum(net.minecraft.world.level.block.CreakingHeartBlock.class, "creaking_heart_state");
    private static final net.minecraft.world.level.block.state.properties.BlockStateBoolean NATURAL = getBoolean(net.minecraft.world.level.block.CreakingHeartBlock.class, "natural");

    @Override
    public boolean isActive() {
        return getCreakingHeartState() == State.AWAKE;
    }

    @Override
    public void setActive(boolean active) {
        setCreakingHeartState(State.AWAKE);
    }

    @Override
    public boolean isNatural() {
        return get(NATURAL);
    }

    @Override
    public void setNatural(boolean natural) {
        set(NATURAL, natural);
    }

    @Override
    public org.bukkit.block.data.type.CreakingHeart.State getCreakingHeartState() {
        return get(CREAKING_HEART_STATE, org.bukkit.block.data.type.CreakingHeart.State.class);
    }

    @Override
    public void setCreakingHeartState(org.bukkit.block.data.type.CreakingHeart.State state) {
        set(CREAKING_HEART_STATE, state);
    }

    // org.bukkit.craftbukkit.block.data.CraftOrientable

    private static final net.minecraft.world.level.block.state.properties.BlockStateEnum<?> AXIS = getEnum(net.minecraft.world.level.block.CreakingHeartBlock.class, "axis");

    @Override
    public org.bukkit.Axis getAxis() {
        return get(AXIS, org.bukkit.Axis.class);
    }

    @Override
    public void setAxis(org.bukkit.Axis axis) {
        set(AXIS, axis);
    }

    @Override
    public java.util.Set<org.bukkit.Axis> getAxes() {
        return getValues(AXIS, org.bukkit.Axis.class);
    }
}

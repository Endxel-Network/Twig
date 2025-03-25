package org.bukkit.craftbukkit.block.data.type;

import org.bukkit.block.data.type.CreakingHeart;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

public abstract class CraftCreakingHeart extends CraftBlockData implements CreakingHeart {

    private static final net.minecraft.world.level.block.state.properties.BlockStateEnum<?> CREAKING_HEART_STATE = getEnum("creaking_heart_state");
    private static final net.minecraft.world.level.block.state.properties.BlockStateBoolean NATURAL = getBoolean("natural");

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
}

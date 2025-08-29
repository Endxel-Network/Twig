package net.minecraft.world.entity;

import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.util.MathHelper;
import net.minecraft.util.RandomSource;

public class SaddleStorage {

    private static final int MIN_BOOST_TIME = 140;
    private static final int MAX_BOOST_TIME = 700;
    private final DataWatcher entityData;
    private final DataWatcherObject<Integer> boostTimeAccessor;
    public boolean boosting;
    public int boostTime;

    public SaddleStorage(DataWatcher datawatcher, DataWatcherObject<Integer> datawatcherobject) {
        this.entityData = datawatcher;
        this.boostTimeAccessor = datawatcherobject;
    }

    public void onSynced() {
        this.boosting = true;
        this.boostTime = 0;
    }

    public boolean boost(RandomSource randomsource) {
        if (this.boosting) {
            return false;
        } else {
            this.boosting = true;
            this.boostTime = 0;
            this.entityData.set(this.boostTimeAccessor, randomsource.nextInt(841) + 140);
            return true;
        }
    }

    public void tickBoost() {
        if (this.boosting && this.boostTime++ > this.boostTimeTotal()) {
            this.boosting = false;
        }

    }

    public float boostFactor() {
        return this.boosting ? 1.0F + 1.15F * MathHelper.sin((float) this.boostTime / (float) this.boostTimeTotal() * (float) Math.PI) : 1.0F;
    }

    public int boostTimeTotal() {
        return (Integer) this.entityData.get(this.boostTimeAccessor);
    }

    // CraftBukkit add setBoostTicks(int)
    public void setBoostTicks(int ticks) {
        this.boosting = true;
        this.boostTime = 0;
        this.entityData.set(this.boostTimeAccessor, ticks);
    }
    // CraftBukkit end
}

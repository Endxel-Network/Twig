package net.minecraft.world.entity.vehicle;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.level.WorldServer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.MathHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.EntityArrow;
import net.minecraft.world.entity.projectile.IProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.material.Fluid;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class EntityMinecartTNT extends EntityMinecartAbstract {

    private static final byte EVENT_PRIME = 10;
    private static final String TAG_EXPLOSION_POWER = "explosion_power";
    private static final String TAG_EXPLOSION_SPEED_FACTOR = "explosion_speed_factor";
    private static final String TAG_FUSE = "fuse";
    private static final float DEFAULT_EXPLOSION_POWER_BASE = 4.0F;
    private static final float DEFAULT_EXPLOSION_SPEED_FACTOR = 1.0F;
    private static final int NO_FUSE = -1;
    @Nullable
    private DamageSource ignitionSource;
    public int fuse = -1;
    public float explosionPowerBase = 4.0F;
    public float explosionSpeedFactor = 1.0F;
    public boolean isIncendiary = false; // CraftBukkit - add field

    public EntityMinecartTNT(EntityTypes<? extends EntityMinecartTNT> entitytypes, World world) {
        super(entitytypes, world);
    }

    @Override
    public IBlockData getDefaultDisplayBlockState() {
        return Blocks.TNT.defaultBlockState();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.fuse > 0) {
            --this.fuse;
            this.level().addParticle(Particles.SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(), 0.0D, 0.0D, 0.0D);
        } else if (this.fuse == 0) {
            this.explode(this.ignitionSource, this.getDeltaMovement().horizontalDistanceSqr());
        }

        if (this.horizontalCollision) {
            double d0 = this.getDeltaMovement().horizontalDistanceSqr();

            if (d0 >= (double) 0.01F) {
                this.explode(d0);
            }
        }

    }

    @Override
    public boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        Entity entity = damagesource.getDirectEntity();

        if (entity instanceof EntityArrow entityarrow) {
            if (entityarrow.isOnFire()) {
                DamageSource damagesource1 = this.damageSources().explosion(this, damagesource.getEntity());

                this.explode(damagesource1, entityarrow.getDeltaMovement().lengthSqr());
            }
        }

        return super.hurtServer(worldserver, damagesource, f);
    }

    @Override
    public void destroy(WorldServer worldserver, DamageSource damagesource) {
        double d0 = this.getDeltaMovement().horizontalDistanceSqr();

        if (!damageSourceIgnitesTnt(damagesource) && d0 < (double) 0.01F) {
            this.destroy(worldserver, this.getDropItem());
        } else {
            if (this.fuse < 0) {
                this.primeFuse(damagesource);
                this.fuse = this.random.nextInt(20) + this.random.nextInt(20);
            }

        }
    }

    @Override
    protected Item getDropItem() {
        return Items.TNT_MINECART;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.TNT_MINECART);
    }

    public void explode(double d0) {
        this.explode((DamageSource) null, d0);
    }

    protected void explode(@Nullable DamageSource damagesource, double d0) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (worldserver.getGameRules().getBoolean(GameRules.RULE_TNT_EXPLODES)) {
                double d1 = Math.min(Math.sqrt(d0), 5.0D);

                // CraftBukkit start
                ExplosionPrimeEvent event = new ExplosionPrimeEvent(this.getBukkitEntity(), (float) ((double) this.explosionPowerBase + (double) this.explosionSpeedFactor * this.random.nextDouble() * 1.5D * d1), this.isIncendiary);
                worldserver.getCraftServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    fuse = -1;
                    return;
                }
                worldserver.explode(this, damagesource, (ExplosionDamageCalculator) null, this.getX(), this.getY(), this.getZ(), event.getRadius(), event.getFire(), World.a.TNT);
                // CraftBukkit end
                this.discard(EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
            } else if (this.isPrimed()) {
                this.discard(EntityRemoveEvent.Cause.EXPLODE); // CraftBukkit - add Bukkit remove cause
            }
        }

    }

    @Override
    public boolean causeFallDamage(double d0, float f, DamageSource damagesource) {
        if (d0 >= 3.0D) {
            double d1 = d0 / 10.0D;

            this.explode(d1 * d1);
        }

        return super.causeFallDamage(d0, f, damagesource);
    }

    @Override
    public void activateMinecart(int i, int j, int k, boolean flag) {
        if (flag && this.fuse < 0) {
            this.primeFuse((DamageSource) null);
        }

    }

    @Override
    public void handleEntityEvent(byte b0) {
        if (b0 == 10) {
            this.primeFuse((DamageSource) null);
        } else {
            super.handleEntityEvent(b0);
        }

    }

    public void primeFuse(@Nullable DamageSource damagesource) {
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            if (!worldserver.getGameRules().getBoolean(GameRules.RULE_TNT_EXPLODES)) {
                return;
            }
        }

        this.fuse = 80;
        if (!this.level().isClientSide) {
            if (damagesource != null && this.ignitionSource == null) {
                this.ignitionSource = this.damageSources().explosion(this, damagesource.getEntity());
            }

            this.level().broadcastEntityEvent(this, (byte) 10);
            if (!this.isSilent()) {
                this.level().playSound((Entity) null, this.getX(), this.getY(), this.getZ(), SoundEffects.TNT_PRIMED, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
        }

    }

    public int getFuse() {
        return this.fuse;
    }

    public boolean isPrimed() {
        return this.fuse > -1;
    }

    @Override
    public float getBlockExplosionResistance(Explosion explosion, IBlockAccess iblockaccess, BlockPosition blockposition, IBlockData iblockdata, Fluid fluid, float f) {
        return !this.isPrimed() || !iblockdata.is(TagsBlock.RAILS) && !iblockaccess.getBlockState(blockposition.above()).is(TagsBlock.RAILS) ? super.getBlockExplosionResistance(explosion, iblockaccess, blockposition, iblockdata, fluid, f) : 0.0F;
    }

    @Override
    public boolean shouldBlockExplode(Explosion explosion, IBlockAccess iblockaccess, BlockPosition blockposition, IBlockData iblockdata, float f) {
        return !this.isPrimed() || !iblockdata.is(TagsBlock.RAILS) && !iblockaccess.getBlockState(blockposition.above()).is(TagsBlock.RAILS) ? super.shouldBlockExplode(explosion, iblockaccess, blockposition, iblockdata, f) : false;
    }

    @Override
    protected void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.readAdditionalSaveData(nbttagcompound);
        this.fuse = nbttagcompound.getIntOr("fuse", -1);
        this.explosionPowerBase = MathHelper.clamp(nbttagcompound.getFloatOr("explosion_power", 4.0F), 0.0F, 128.0F);
        this.explosionSpeedFactor = MathHelper.clamp(nbttagcompound.getFloatOr("explosion_speed_factor", 1.0F), 0.0F, 128.0F);
    }

    @Override
    protected void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        super.addAdditionalSaveData(nbttagcompound);
        nbttagcompound.putInt("fuse", this.fuse);
        if (this.explosionPowerBase != 4.0F) {
            nbttagcompound.putFloat("explosion_power", this.explosionPowerBase);
        }

        if (this.explosionSpeedFactor != 1.0F) {
            nbttagcompound.putFloat("explosion_speed_factor", this.explosionSpeedFactor);
        }

    }

    @Override
    boolean shouldSourceDestroy(DamageSource damagesource) {
        return damageSourceIgnitesTnt(damagesource);
    }

    private static boolean damageSourceIgnitesTnt(DamageSource damagesource) {
        Entity entity = damagesource.getDirectEntity();

        if (entity instanceof IProjectile iprojectile) {
            return iprojectile.isOnFire();
        } else {
            return damagesource.is(DamageTypeTags.IS_FIRE) || damagesource.is(DamageTypeTags.IS_EXPLOSION);
        }
    }
}

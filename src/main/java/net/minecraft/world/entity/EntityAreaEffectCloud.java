package net.minecraft.world.entity;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleParam;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.ARGB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectList;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.World;
import net.minecraft.world.level.material.EnumPistonReaction;

// CraftBukkit start
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class EntityAreaEffectCloud extends Entity implements TraceableEntity {

    private static final int TIME_BETWEEN_APPLICATIONS = 5;
    private static final DataWatcherObject<Float> DATA_RADIUS = DataWatcher.<Float>defineId(EntityAreaEffectCloud.class, DataWatcherRegistry.FLOAT);
    private static final DataWatcherObject<Boolean> DATA_WAITING = DataWatcher.<Boolean>defineId(EntityAreaEffectCloud.class, DataWatcherRegistry.BOOLEAN);
    private static final DataWatcherObject<ParticleParam> DATA_PARTICLE = DataWatcher.<ParticleParam>defineId(EntityAreaEffectCloud.class, DataWatcherRegistry.PARTICLE);
    private static final float MAX_RADIUS = 32.0F;
    private static final int DEFAULT_AGE = 0;
    private static final int DEFAULT_DURATION_ON_USE = 0;
    private static final float DEFAULT_RADIUS_ON_USE = 0.0F;
    private static final float DEFAULT_RADIUS_PER_TICK = 0.0F;
    private static final float DEFAULT_POTION_DURATION_SCALE = 1.0F;
    private static final float MINIMAL_RADIUS = 0.5F;
    private static final float DEFAULT_RADIUS = 3.0F;
    public static final float DEFAULT_WIDTH = 6.0F;
    public static final float HEIGHT = 0.5F;
    public static final int INFINITE_DURATION = -1;
    public static final int DEFAULT_LINGERING_DURATION = 600;
    private static final int DEFAULT_WAIT_TIME = 20;
    private static final int DEFAULT_REAPPLICATION_DELAY = 20;
    private static final ColorParticleOption DEFAULT_PARTICLE = ColorParticleOption.create(Particles.ENTITY_EFFECT, -1);
    public PotionContents potionContents;
    private float potionDurationScale;
    private final Map<Entity, Integer> victims;
    private int duration;
    public int waitTime;
    public int reapplicationDelay;
    public int durationOnUse;
    public float radiusOnUse;
    public float radiusPerTick;
    @Nullable
    private EntityLiving owner;
    @Nullable
    private UUID ownerUUID;

    public EntityAreaEffectCloud(EntityTypes<? extends EntityAreaEffectCloud> entitytypes, World world) {
        super(entitytypes, world);
        this.potionContents = PotionContents.EMPTY;
        this.potionDurationScale = 1.0F;
        this.victims = Maps.newHashMap();
        this.duration = -1;
        this.waitTime = 20;
        this.reapplicationDelay = 20;
        this.durationOnUse = 0;
        this.radiusOnUse = 0.0F;
        this.radiusPerTick = 0.0F;
        this.noPhysics = true;
    }

    public EntityAreaEffectCloud(World world, double d0, double d1, double d2) {
        this(EntityTypes.AREA_EFFECT_CLOUD, world);
        this.setPos(d0, d1, d2);
    }

    @Override
    protected void defineSynchedData(DataWatcher.a datawatcher_a) {
        datawatcher_a.define(EntityAreaEffectCloud.DATA_RADIUS, 3.0F);
        datawatcher_a.define(EntityAreaEffectCloud.DATA_WAITING, false);
        datawatcher_a.define(EntityAreaEffectCloud.DATA_PARTICLE, EntityAreaEffectCloud.DEFAULT_PARTICLE);
    }

    public void setRadius(float f) {
        if (!this.level().isClientSide) {
            this.getEntityData().set(EntityAreaEffectCloud.DATA_RADIUS, MathHelper.clamp(f, 0.0F, 32.0F));
        }

    }

    @Override
    public void refreshDimensions() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.refreshDimensions();
        this.setPos(d0, d1, d2);
    }

    public float getRadius() {
        return (Float) this.getEntityData().get(EntityAreaEffectCloud.DATA_RADIUS);
    }

    public void setPotionContents(PotionContents potioncontents) {
        this.potionContents = potioncontents;
        this.updateColor();
    }

    public void setPotionDurationScale(float f) {
        this.potionDurationScale = f;
    }

    public void updateColor() {
        ParticleParam particleparam = (ParticleParam) this.entityData.get(EntityAreaEffectCloud.DATA_PARTICLE);

        if (particleparam instanceof ColorParticleOption colorparticleoption) {
            int i = this.potionContents.equals(PotionContents.EMPTY) ? 0 : this.potionContents.getColor();

            this.entityData.set(EntityAreaEffectCloud.DATA_PARTICLE, ColorParticleOption.create(colorparticleoption.getType(), ARGB.opaque(i)));
        }

    }

    public void addEffect(MobEffect mobeffect) {
        this.setPotionContents(this.potionContents.withEffectAdded(mobeffect));
    }

    public ParticleParam getParticle() {
        return (ParticleParam) this.getEntityData().get(EntityAreaEffectCloud.DATA_PARTICLE);
    }

    public void setParticle(ParticleParam particleparam) {
        this.getEntityData().set(EntityAreaEffectCloud.DATA_PARTICLE, particleparam);
    }

    protected void setWaiting(boolean flag) {
        this.getEntityData().set(EntityAreaEffectCloud.DATA_WAITING, flag);
    }

    public boolean isWaiting() {
        return (Boolean) this.getEntityData().get(EntityAreaEffectCloud.DATA_WAITING);
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int i) {
        this.duration = i;
    }

    // Spigot start - copied from below
    @Override
    public void inactiveTick() {
        super.inactiveTick();

        if (this.tickCount >= this.waitTime + this.duration) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            return;
        }
    }
    // Spigot end

    @Override
    public void tick() {
        super.tick();
        World world = this.level();

        if (world instanceof WorldServer worldserver) {
            this.serverTick(worldserver);
        } else {
            this.clientTick();
        }

    }

    private void clientTick() {
        boolean flag = this.isWaiting();
        float f = this.getRadius();

        if (!flag || !this.random.nextBoolean()) {
            ParticleParam particleparam = this.getParticle();
            int i;
            float f1;

            if (flag) {
                i = 2;
                f1 = 0.2F;
            } else {
                i = MathHelper.ceil((float) Math.PI * f * f);
                f1 = f;
            }

            for (int j = 0; j < i; ++j) {
                float f2 = this.random.nextFloat() * ((float) Math.PI * 2F);
                float f3 = MathHelper.sqrt(this.random.nextFloat()) * f1;
                double d0 = this.getX() + (double) (MathHelper.cos(f2) * f3);
                double d1 = this.getY();
                double d2 = this.getZ() + (double) (MathHelper.sin(f2) * f3);

                if (particleparam.getType() == Particles.ENTITY_EFFECT) {
                    if (flag && this.random.nextBoolean()) {
                        this.level().addAlwaysVisibleParticle(ColorParticleOption.create(Particles.ENTITY_EFFECT, -1), d0, d1, d2, 0.0D, 0.0D, 0.0D);
                    } else {
                        this.level().addAlwaysVisibleParticle(particleparam, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                    }
                } else if (flag) {
                    this.level().addAlwaysVisibleParticle(particleparam, d0, d1, d2, 0.0D, 0.0D, 0.0D);
                } else {
                    this.level().addAlwaysVisibleParticle(particleparam, d0, d1, d2, (0.5D - this.random.nextDouble()) * 0.15D, (double) 0.01F, (0.5D - this.random.nextDouble()) * 0.15D);
                }
            }

        }
    }

    private void serverTick(WorldServer worldserver) {
        if (this.duration != -1 && this.tickCount >= this.waitTime + this.duration) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            boolean flag = this.isWaiting();
            boolean flag1 = this.tickCount < this.waitTime;

            if (flag != flag1) {
                this.setWaiting(flag1);
            }

            if (!flag1) {
                float f = this.getRadius();

                if (this.radiusPerTick != 0.0F) {
                    f += this.radiusPerTick;
                    if (f < 0.5F) {
                        this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                        return;
                    }

                    this.setRadius(f);
                }

                if (this.tickCount % 5 == 0) {
                    this.victims.entrySet().removeIf((entry) -> {
                        return this.tickCount >= (Integer) entry.getValue();
                    });
                    if (!this.potionContents.hasEffects()) {
                        this.victims.clear();
                    } else {
                        List<MobEffect> list = new ArrayList();
                        PotionContents potioncontents = this.potionContents;

                        Objects.requireNonNull(list);
                        potioncontents.forEachEffect(list::add, this.potionDurationScale);
                        List<EntityLiving> list1 = this.level().<EntityLiving>getEntitiesOfClass(EntityLiving.class, this.getBoundingBox());

                        if (!list1.isEmpty()) {
                            List<LivingEntity> entities = new java.util.ArrayList<LivingEntity>(); // CraftBukkit
                            for (EntityLiving entityliving : list1) {
                                if (!this.victims.containsKey(entityliving) && entityliving.isAffectedByPotions()) {
                                    Stream<MobEffect> stream = list.stream(); // CraftBukkit - decompile error

                                    Objects.requireNonNull(entityliving);
                                    if (!stream.noneMatch(entityliving::canBeAffected)) {
                                        double d0 = entityliving.getX() - this.getX();
                                        double d1 = entityliving.getZ() - this.getZ();
                                        double d2 = d0 * d0 + d1 * d1;

                                        if (d2 <= (double) (f * f)) {
                                            // CraftBukkit start
                                            entities.add((LivingEntity) entityliving.getBukkitEntity());
                                        }
                                    }
                                }
                            }
                            {
                                org.bukkit.event.entity.AreaEffectCloudApplyEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callAreaEffectCloudApplyEvent(this, entities);
                                if (!event.isCancelled()) {
                                    for (LivingEntity entity : event.getAffectedEntities()) {
                                        if (entity instanceof CraftLivingEntity) {
                                            EntityLiving entityliving = ((CraftLivingEntity) entity).getHandle();
                                            // CraftBukkit end
                                            this.victims.put(entityliving, this.tickCount + this.reapplicationDelay);

                                            for (MobEffect mobeffect : list) {
                                                if (((MobEffectList) mobeffect.getEffect().value()).isInstantenous()) {
                                                    ((MobEffectList) mobeffect.getEffect().value()).applyInstantenousEffect(worldserver, this, this.getOwner(), entityliving, mobeffect.getAmplifier(), 0.5D);
                                                } else {
                                                    entityliving.addEffect(new MobEffect(mobeffect), this, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD); // CraftBukkit
                                                }
                                            }

                                            if (this.radiusOnUse != 0.0F) {
                                                f += this.radiusOnUse;
                                                if (f < 0.5F) {
                                                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                                                    return;
                                                }

                                                this.setRadius(f);
                                            }

                                            if (this.durationOnUse != 0 && this.duration != -1) {
                                                this.duration += this.durationOnUse;
                                                if (this.duration <= 0) {
                                                    this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    public float getRadiusOnUse() {
        return this.radiusOnUse;
    }

    public void setRadiusOnUse(float f) {
        this.radiusOnUse = f;
    }

    public float getRadiusPerTick() {
        return this.radiusPerTick;
    }

    public void setRadiusPerTick(float f) {
        this.radiusPerTick = f;
    }

    public int getDurationOnUse() {
        return this.durationOnUse;
    }

    public void setDurationOnUse(int i) {
        this.durationOnUse = i;
    }

    public int getWaitTime() {
        return this.waitTime;
    }

    public void setWaitTime(int i) {
        this.waitTime = i;
    }

    public void setOwner(@Nullable EntityLiving entityliving) {
        this.owner = entityliving;
        this.ownerUUID = entityliving == null ? null : entityliving.getUUID();
    }

    @Nullable
    @Override
    public EntityLiving getOwner() {
        if (this.owner != null && !this.owner.isRemoved()) {
            return this.owner;
        } else {
            if (this.ownerUUID != null) {
                World world = this.level();

                if (world instanceof WorldServer) {
                    WorldServer worldserver = (WorldServer) world;
                    Entity entity = worldserver.getEntity(this.ownerUUID);
                    EntityLiving entityliving;

                    if (entity instanceof EntityLiving) {
                        EntityLiving entityliving1 = (EntityLiving) entity;

                        entityliving = entityliving1;
                    } else {
                        entityliving = null;
                    }

                    this.owner = entityliving;
                }
            }

            return this.owner;
        }
    }

    @Override
    protected void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        this.tickCount = nbttagcompound.getIntOr("Age", 0);
        this.duration = nbttagcompound.getIntOr("Duration", -1);
        this.waitTime = nbttagcompound.getIntOr("WaitTime", 20);
        this.reapplicationDelay = nbttagcompound.getIntOr("ReapplicationDelay", 20);
        this.durationOnUse = nbttagcompound.getIntOr("DurationOnUse", 0);
        this.radiusOnUse = nbttagcompound.getFloatOr("RadiusOnUse", 0.0F);
        this.radiusPerTick = nbttagcompound.getFloatOr("RadiusPerTick", 0.0F);
        this.setRadius(nbttagcompound.getFloatOr("Radius", 3.0F));
        this.ownerUUID = (UUID) nbttagcompound.read("Owner", UUIDUtil.CODEC).orElse(null); // CraftBukkit - decompile error
        RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

        this.setParticle((ParticleParam) nbttagcompound.read("Particle", Particles.CODEC, registryops).orElse(EntityAreaEffectCloud.DEFAULT_PARTICLE));
        this.setPotionContents((PotionContents) nbttagcompound.read("potion_contents", PotionContents.CODEC, registryops).orElse(PotionContents.EMPTY));
        this.potionDurationScale = nbttagcompound.getFloatOr("potion_duration_scale", 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        nbttagcompound.putInt("Age", this.tickCount);
        nbttagcompound.putInt("Duration", this.duration);
        nbttagcompound.putInt("WaitTime", this.waitTime);
        nbttagcompound.putInt("ReapplicationDelay", this.reapplicationDelay);
        nbttagcompound.putInt("DurationOnUse", this.durationOnUse);
        nbttagcompound.putFloat("RadiusOnUse", this.radiusOnUse);
        nbttagcompound.putFloat("RadiusPerTick", this.radiusPerTick);
        nbttagcompound.putFloat("Radius", this.getRadius());
        RegistryOps<NBTBase> registryops = this.registryAccess().<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

        nbttagcompound.store("Particle", Particles.CODEC, registryops, this.getParticle());
        nbttagcompound.storeNullable("Owner", UUIDUtil.CODEC, this.ownerUUID);
        if (!this.potionContents.equals(PotionContents.EMPTY)) {
            nbttagcompound.store("potion_contents", PotionContents.CODEC, registryops, this.potionContents);
        }

        if (this.potionDurationScale != 1.0F) {
            nbttagcompound.putFloat("potion_duration_scale", this.potionDurationScale);
        }

    }

    @Override
    public void onSyncedDataUpdated(DataWatcherObject<?> datawatcherobject) {
        if (EntityAreaEffectCloud.DATA_RADIUS.equals(datawatcherobject)) {
            this.refreshDimensions();
        }

        super.onSyncedDataUpdated(datawatcherobject);
    }

    @Override
    public EnumPistonReaction getPistonPushReaction() {
        return EnumPistonReaction.IGNORE;
    }

    @Override
    public EntitySize getDimensions(EntityPose entitypose) {
        return EntitySize.scalable(this.getRadius() * 2.0F, 0.5F);
    }

    @Override
    public final boolean hurtServer(WorldServer worldserver, DamageSource damagesource, float f) {
        return false;
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> datacomponenttype) {
        return (T) (datacomponenttype == DataComponents.POTION_CONTENTS ? castComponentValue(datacomponenttype, this.potionContents) : (datacomponenttype == DataComponents.POTION_DURATION_SCALE ? castComponentValue(datacomponenttype, this.potionDurationScale) : super.get(datacomponenttype)));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.POTION_CONTENTS);
        this.applyImplicitComponentIfPresent(datacomponentgetter, DataComponents.POTION_DURATION_SCALE);
        super.applyImplicitComponents(datacomponentgetter);
    }

    @Override
    protected <T> boolean applyImplicitComponent(DataComponentType<T> datacomponenttype, T t0) {
        if (datacomponenttype == DataComponents.POTION_CONTENTS) {
            this.setPotionContents((PotionContents) castComponentValue(DataComponents.POTION_CONTENTS, t0));
            return true;
        } else if (datacomponenttype == DataComponents.POTION_DURATION_SCALE) {
            this.setPotionDurationScale((Float) castComponentValue(DataComponents.POTION_DURATION_SCALE, t0));
            return true;
        } else {
            return super.applyImplicitComponent(datacomponenttype, t0);
        }
    }
}

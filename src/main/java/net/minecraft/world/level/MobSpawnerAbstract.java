package net.minecraft.world.level;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.Particles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityPositionTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.IEntitySelector;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AxisAlignedBB;
import net.minecraft.world.phys.Vec3D;

public abstract class MobSpawnerAbstract {

    public static final String SPAWN_DATA_TAG = "SpawnData";
    private static final int EVENT_SPAWN = 1;
    private static final int DEFAULT_SPAWN_DELAY = 20;
    private static final int DEFAULT_MIN_SPAWN_DELAY = 200;
    private static final int DEFAULT_MAX_SPAWN_DELAY = 800;
    private static final int DEFAULT_SPAWN_COUNT = 4;
    private static final int DEFAULT_MAX_NEARBY_ENTITIES = 6;
    private static final int DEFAULT_REQUIRED_PLAYER_RANGE = 16;
    private static final int DEFAULT_SPAWN_RANGE = 4;
    public int spawnDelay = 20;
    public WeightedList<MobSpawnerData> spawnPotentials = WeightedList.<MobSpawnerData>of();
    @Nullable
    public MobSpawnerData nextSpawnData;
    private double spin;
    private double oSpin;
    public int minSpawnDelay = 200;
    public int maxSpawnDelay = 800;
    public int spawnCount = 4;
    @Nullable
    private Entity displayEntity;
    public int maxNearbyEntities = 6;
    public int requiredPlayerRange = 16;
    public int spawnRange = 4;

    public MobSpawnerAbstract() {}

    public void setEntityId(EntityTypes<?> entitytypes, @Nullable World world, RandomSource randomsource, BlockPosition blockposition) {
        this.getOrCreateNextSpawnData(world, randomsource, blockposition).getEntityToSpawn().putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entitytypes).toString());
        this.spawnPotentials = WeightedList.of(); // CraftBukkit - SPIGOT-3496, MC-92282
    }

    private boolean isNearPlayer(World world, BlockPosition blockposition) {
        return world.hasNearbyAlivePlayer((double) blockposition.getX() + 0.5D, (double) blockposition.getY() + 0.5D, (double) blockposition.getZ() + 0.5D, (double) this.requiredPlayerRange);
    }

    public void clientTick(World world, BlockPosition blockposition) {
        if (!this.isNearPlayer(world, blockposition)) {
            this.oSpin = this.spin;
        } else if (this.displayEntity != null) {
            RandomSource randomsource = world.getRandom();
            double d0 = (double) blockposition.getX() + randomsource.nextDouble();
            double d1 = (double) blockposition.getY() + randomsource.nextDouble();
            double d2 = (double) blockposition.getZ() + randomsource.nextDouble();

            world.addParticle(Particles.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
            world.addParticle(Particles.FLAME, d0, d1, d2, 0.0D, 0.0D, 0.0D);
            if (this.spawnDelay > 0) {
                --this.spawnDelay;
            }

            this.oSpin = this.spin;
            this.spin = (this.spin + (double) (1000.0F / ((float) this.spawnDelay + 200.0F))) % 360.0D;
        }

    }

    public void serverTick(WorldServer worldserver, BlockPosition blockposition) {
        if (this.isNearPlayer(worldserver, blockposition)) {
            if (this.spawnDelay == -1) {
                this.delay(worldserver, blockposition);
            }

            if (this.spawnDelay > 0) {
                --this.spawnDelay;
            } else {
                boolean flag = false;
                RandomSource randomsource = worldserver.getRandom();
                MobSpawnerData mobspawnerdata = this.getOrCreateNextSpawnData(worldserver, randomsource, blockposition);

                for (int i = 0; i < this.spawnCount; ++i) {
                    NBTTagCompound nbttagcompound = mobspawnerdata.getEntityToSpawn();
                    Optional<EntityTypes<?>> optional = EntityTypes.by(nbttagcompound);

                    if (optional.isEmpty()) {
                        this.delay(worldserver, blockposition);
                        return;
                    }

                    Vec3D vec3d = (Vec3D) nbttagcompound.read("Pos", Vec3D.CODEC).orElseGet(() -> {
                        return new Vec3D((double) blockposition.getX() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) this.spawnRange + 0.5D, (double) (blockposition.getY() + randomsource.nextInt(3) - 1), (double) blockposition.getZ() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) this.spawnRange + 0.5D);
                    });

                    if (worldserver.noCollision(((EntityTypes) optional.get()).getSpawnAABB(vec3d.x, vec3d.y, vec3d.z))) {
                        BlockPosition blockposition1 = BlockPosition.containing(vec3d);

                        if (mobspawnerdata.getCustomSpawnRules().isPresent()) {
                            if (!((EntityTypes) optional.get()).getCategory().isFriendly() && worldserver.getDifficulty() == EnumDifficulty.PEACEFUL) {
                                continue;
                            }

                            MobSpawnerData.a mobspawnerdata_a = (MobSpawnerData.a) mobspawnerdata.getCustomSpawnRules().get();

                            if (!mobspawnerdata_a.isValidPosition(blockposition1, worldserver)) {
                                continue;
                            }
                        } else if (!EntityPositionTypes.checkSpawnRules((EntityTypes) optional.get(), worldserver, EntitySpawnReason.SPAWNER, blockposition1, worldserver.getRandom())) {
                            continue;
                        }

                        Entity entity = EntityTypes.loadEntityRecursive(nbttagcompound, worldserver, EntitySpawnReason.SPAWNER, (entity1) -> {
                            entity1.snapTo(vec3d.x, vec3d.y, vec3d.z, entity1.getYRot(), entity1.getXRot());
                            return entity1;
                        });

                        if (entity == null) {
                            this.delay(worldserver, blockposition);
                            return;
                        }

                        int j = worldserver.getEntities(EntityTypeTest.forExactClass(entity.getClass()), (new AxisAlignedBB((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), (double) (blockposition.getX() + 1), (double) (blockposition.getY() + 1), (double) (blockposition.getZ() + 1))).inflate((double) this.spawnRange), IEntitySelector.NO_SPECTATORS).size();

                        if (j >= this.maxNearbyEntities) {
                            this.delay(worldserver, blockposition);
                            return;
                        }

                        entity.snapTo(entity.getX(), entity.getY(), entity.getZ(), randomsource.nextFloat() * 360.0F, 0.0F);
                        if (entity instanceof EntityInsentient) {
                            EntityInsentient entityinsentient = (EntityInsentient) entity;

                            if (mobspawnerdata.getCustomSpawnRules().isEmpty() && !entityinsentient.checkSpawnRules(worldserver, EntitySpawnReason.SPAWNER) || !entityinsentient.checkSpawnObstruction(worldserver)) {
                                continue;
                            }

                            boolean flag1 = mobspawnerdata.getEntityToSpawn().size() == 1 && mobspawnerdata.getEntityToSpawn().getString("id").isPresent();

                            if (flag1) {
                                ((EntityInsentient) entity).finalizeSpawn(worldserver, worldserver.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.SPAWNER, (GroupDataEntity) null);
                            }

                            Optional<net.minecraft.world.entity.EquipmentTable> optional1 = mobspawnerdata.getEquipment(); // CraftBukkit - decompile error

                            Objects.requireNonNull(entityinsentient);
                            optional1.ifPresent(entityinsentient::equip);
                        }

                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.callSpawnerSpawnEvent(entity, blockposition).isCancelled()) {
                            continue;
                        }
                        if (!worldserver.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER)) {
                            // CraftBukkit end
                            this.delay(worldserver, blockposition);
                            return;
                        }

                        worldserver.levelEvent(2004, blockposition, 0);
                        worldserver.gameEvent(entity, (Holder) GameEvent.ENTITY_PLACE, blockposition1);
                        if (entity instanceof EntityInsentient) {
                            ((EntityInsentient) entity).spawnAnim();
                        }

                        flag = true;
                    }
                }

                if (flag) {
                    this.delay(worldserver, blockposition);
                }

            }
        }
    }

    private void delay(World world, BlockPosition blockposition) {
        RandomSource randomsource = world.random;

        if (this.maxSpawnDelay <= this.minSpawnDelay) {
            this.spawnDelay = this.minSpawnDelay;
        } else {
            this.spawnDelay = this.minSpawnDelay + randomsource.nextInt(this.maxSpawnDelay - this.minSpawnDelay);
        }

        this.spawnPotentials.getRandom(randomsource).ifPresent((mobspawnerdata) -> {
            this.setNextSpawnData(world, blockposition, mobspawnerdata);
        });
        this.broadcastEvent(world, blockposition, 1);
    }

    public void load(@Nullable World world, BlockPosition blockposition, NBTTagCompound nbttagcompound) {
        this.spawnDelay = nbttagcompound.getShortOr("Delay", (short) 20);
        nbttagcompound.read("SpawnData", MobSpawnerData.CODEC).ifPresent((mobspawnerdata) -> {
            this.setNextSpawnData(world, blockposition, mobspawnerdata);
        });
        this.spawnPotentials = (WeightedList) nbttagcompound.read("SpawnPotentials", MobSpawnerData.LIST_CODEC).orElseGet(() -> {
            return WeightedList.of(this.nextSpawnData != null ? this.nextSpawnData : new MobSpawnerData());
        });
        this.minSpawnDelay = nbttagcompound.getIntOr("MinSpawnDelay", 200);
        this.maxSpawnDelay = nbttagcompound.getIntOr("MaxSpawnDelay", 800);
        this.spawnCount = nbttagcompound.getIntOr("SpawnCount", 4);
        this.maxNearbyEntities = nbttagcompound.getIntOr("MaxNearbyEntities", 6);
        this.requiredPlayerRange = nbttagcompound.getIntOr("RequiredPlayerRange", 16);
        this.spawnRange = nbttagcompound.getIntOr("SpawnRange", 4);
        this.displayEntity = null;
    }

    public NBTTagCompound save(NBTTagCompound nbttagcompound) {
        nbttagcompound.putShort("Delay", (short) this.spawnDelay);
        nbttagcompound.putShort("MinSpawnDelay", (short) this.minSpawnDelay);
        nbttagcompound.putShort("MaxSpawnDelay", (short) this.maxSpawnDelay);
        nbttagcompound.putShort("SpawnCount", (short) this.spawnCount);
        nbttagcompound.putShort("MaxNearbyEntities", (short) this.maxNearbyEntities);
        nbttagcompound.putShort("RequiredPlayerRange", (short) this.requiredPlayerRange);
        nbttagcompound.putShort("SpawnRange", (short) this.spawnRange);
        nbttagcompound.storeNullable("SpawnData", MobSpawnerData.CODEC, this.nextSpawnData);
        nbttagcompound.store("SpawnPotentials", MobSpawnerData.LIST_CODEC, this.spawnPotentials);
        return nbttagcompound;
    }

    @Nullable
    public Entity getOrCreateDisplayEntity(World world, BlockPosition blockposition) {
        if (this.displayEntity == null) {
            NBTTagCompound nbttagcompound = this.getOrCreateNextSpawnData(world, world.getRandom(), blockposition).getEntityToSpawn();

            if (nbttagcompound.getString("id").isEmpty()) {
                return null;
            }

            this.displayEntity = EntityTypes.loadEntityRecursive(nbttagcompound, world, EntitySpawnReason.SPAWNER, Function.identity());
            if (nbttagcompound.size() == 1 && this.displayEntity instanceof EntityInsentient) {
                ;
            }
        }

        return this.displayEntity;
    }

    public boolean onEventTriggered(World world, int i) {
        if (i == 1) {
            if (world.isClientSide) {
                this.spawnDelay = this.minSpawnDelay;
            }

            return true;
        } else {
            return false;
        }
    }

    protected void setNextSpawnData(@Nullable World world, BlockPosition blockposition, MobSpawnerData mobspawnerdata) {
        this.nextSpawnData = mobspawnerdata;
    }

    private MobSpawnerData getOrCreateNextSpawnData(@Nullable World world, RandomSource randomsource, BlockPosition blockposition) {
        if (this.nextSpawnData != null) {
            return this.nextSpawnData;
        } else {
            this.setNextSpawnData(world, blockposition, (MobSpawnerData) this.spawnPotentials.getRandom(randomsource).orElseGet(MobSpawnerData::new));
            return this.nextSpawnData;
        }
    }

    public abstract void broadcastEvent(World world, BlockPosition blockposition, int i);

    public double getSpin() {
        return this.spin;
    }

    public double getoSpin() {
        return this.oSpin;
    }
}

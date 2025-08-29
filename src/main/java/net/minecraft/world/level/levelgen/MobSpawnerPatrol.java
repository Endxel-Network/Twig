package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.GroupDataEntity;
import net.minecraft.world.entity.monster.EntityMonsterPatrolling;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.MobSpawner;
import net.minecraft.world.level.SpawnerCreature;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.block.state.IBlockData;

public class MobSpawnerPatrol implements MobSpawner {

    private int nextTick;

    public MobSpawnerPatrol() {}

    @Override
    public void tick(WorldServer worldserver, boolean flag, boolean flag1) {
        if (flag) {
            if (worldserver.getGameRules().getBoolean(GameRules.RULE_DO_PATROL_SPAWNING)) {
                RandomSource randomsource = worldserver.random;

                --this.nextTick;
                if (this.nextTick <= 0) {
                    this.nextTick += 12000 + randomsource.nextInt(1200);
                    long i = worldserver.getDayTime() / 24000L;

                    if (i >= 5L && worldserver.isBrightOutside()) {
                        if (randomsource.nextInt(5) == 0) {
                            int j = worldserver.players().size();

                            if (j >= 1) {
                                EntityHuman entityhuman = (EntityHuman) worldserver.players().get(randomsource.nextInt(j));

                                if (!entityhuman.isSpectator()) {
                                    if (!worldserver.isCloseToVillage(entityhuman.blockPosition(), 2)) {
                                        int k = (24 + randomsource.nextInt(24)) * (randomsource.nextBoolean() ? -1 : 1);
                                        int l = (24 + randomsource.nextInt(24)) * (randomsource.nextBoolean() ? -1 : 1);
                                        BlockPosition.MutableBlockPosition blockposition_mutableblockposition = entityhuman.blockPosition().mutable().move(k, 0, l);
                                        int i1 = 10;

                                        if (worldserver.hasChunksAt(blockposition_mutableblockposition.getX() - 10, blockposition_mutableblockposition.getZ() - 10, blockposition_mutableblockposition.getX() + 10, blockposition_mutableblockposition.getZ() + 10)) {
                                            Holder<BiomeBase> holder = worldserver.getBiome(blockposition_mutableblockposition);

                                            if (!holder.is(BiomeTags.WITHOUT_PATROL_SPAWNS)) {
                                                int j1 = (int) Math.ceil((double) worldserver.getCurrentDifficultyAt(blockposition_mutableblockposition).getEffectiveDifficulty()) + 1;

                                                for (int k1 = 0; k1 < j1; ++k1) {
                                                    blockposition_mutableblockposition.setY(worldserver.getHeightmapPos(HeightMap.Type.MOTION_BLOCKING_NO_LEAVES, blockposition_mutableblockposition).getY());
                                                    if (k1 == 0) {
                                                        if (!this.spawnPatrolMember(worldserver, blockposition_mutableblockposition, randomsource, true)) {
                                                            break;
                                                        }
                                                    } else {
                                                        this.spawnPatrolMember(worldserver, blockposition_mutableblockposition, randomsource, false);
                                                    }

                                                    blockposition_mutableblockposition.setX(blockposition_mutableblockposition.getX() + randomsource.nextInt(5) - randomsource.nextInt(5));
                                                    blockposition_mutableblockposition.setZ(blockposition_mutableblockposition.getZ() + randomsource.nextInt(5) - randomsource.nextInt(5));
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

    private boolean spawnPatrolMember(WorldServer worldserver, BlockPosition blockposition, RandomSource randomsource, boolean flag) {
        IBlockData iblockdata = worldserver.getBlockState(blockposition);

        if (!SpawnerCreature.isValidEmptySpawnBlock(worldserver, blockposition, iblockdata, iblockdata.getFluidState(), EntityTypes.PILLAGER)) {
            return false;
        } else if (!EntityMonsterPatrolling.checkPatrollingMonsterSpawnRules(EntityTypes.PILLAGER, worldserver, EntitySpawnReason.PATROL, blockposition, randomsource)) {
            return false;
        } else {
            EntityMonsterPatrolling entitymonsterpatrolling = EntityTypes.PILLAGER.create(worldserver, EntitySpawnReason.PATROL);

            if (entitymonsterpatrolling != null) {
                if (flag) {
                    entitymonsterpatrolling.setPatrolLeader(true);
                    entitymonsterpatrolling.findPatrolTarget();
                }

                entitymonsterpatrolling.setPos((double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ());
                entitymonsterpatrolling.finalizeSpawn(worldserver, worldserver.getCurrentDifficultyAt(blockposition), EntitySpawnReason.PATROL, (GroupDataEntity) null);
                worldserver.addFreshEntityWithPassengers(entitymonsterpatrolling, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.PATROL); // CraftBukkit
                return true;
            } else {
                return false;
            }
        }
    }
}

package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.World;
import net.minecraft.world.level.storage.WorldPersistentData;

// CraftBukkit start
import net.minecraft.world.level.dimension.WorldDimension;
// CraftBukkit end

public class PersistentStructureLegacy {

    private static final Map<String, String> CURRENT_TO_LEGACY_MAP = (Map) SystemUtils.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put("Village", "Village");
        hashmap.put("Mineshaft", "Mineshaft");
        hashmap.put("Mansion", "Mansion");
        hashmap.put("Igloo", "Temple");
        hashmap.put("Desert_Pyramid", "Temple");
        hashmap.put("Jungle_Pyramid", "Temple");
        hashmap.put("Swamp_Hut", "Temple");
        hashmap.put("Stronghold", "Stronghold");
        hashmap.put("Monument", "Monument");
        hashmap.put("Fortress", "Fortress");
        hashmap.put("EndCity", "EndCity");
    });
    private static final Map<String, String> LEGACY_TO_CURRENT_MAP = (Map) SystemUtils.make(Maps.newHashMap(), (hashmap) -> {
        hashmap.put("Iglu", "Igloo");
        hashmap.put("TeDP", "Desert_Pyramid");
        hashmap.put("TeJP", "Jungle_Pyramid");
        hashmap.put("TeSH", "Swamp_Hut");
    });
    private static final Set<String> OLD_STRUCTURE_REGISTRY_KEYS = Set.of("pillager_outpost", "mineshaft", "mansion", "jungle_pyramid", "desert_pyramid", "igloo", "ruined_portal", "shipwreck", "swamp_hut", "stronghold", "monument", "ocean_ruin", "fortress", "endcity", "buried_treasure", "village", "nether_fossil", "bastion_remnant");
    private final boolean hasLegacyData;
    private final Map<String, Long2ObjectMap<NBTTagCompound>> dataMap = Maps.newHashMap();
    private final Map<String, PersistentIndexed> indexMap = Maps.newHashMap();
    private final List<String> legacyKeys;
    private final List<String> currentKeys;

    public PersistentStructureLegacy(@Nullable WorldPersistentData worldpersistentdata, List<String> list, List<String> list1) {
        this.legacyKeys = list;
        this.currentKeys = list1;
        this.populateCaches(worldpersistentdata);
        boolean flag = false;

        for (String s : this.currentKeys) {
            flag |= this.dataMap.get(s) != null;
        }

        this.hasLegacyData = flag;
    }

    public void removeIndex(long i) {
        for (String s : this.legacyKeys) {
            PersistentIndexed persistentindexed = (PersistentIndexed) this.indexMap.get(s);

            if (persistentindexed != null && persistentindexed.hasUnhandledIndex(i)) {
                persistentindexed.removeIndex(i);
            }
        }

    }

    public NBTTagCompound updateFromLegacy(NBTTagCompound nbttagcompound) {
        NBTTagCompound nbttagcompound1 = nbttagcompound.getCompoundOrEmpty("Level");
        ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(nbttagcompound1.getIntOr("xPos", 0), nbttagcompound1.getIntOr("zPos", 0));

        if (this.isUnhandledStructureStart(chunkcoordintpair.x, chunkcoordintpair.z)) {
            nbttagcompound = this.updateStructureStart(nbttagcompound, chunkcoordintpair);
        }

        NBTTagCompound nbttagcompound2 = nbttagcompound1.getCompoundOrEmpty("Structures");
        NBTTagCompound nbttagcompound3 = nbttagcompound2.getCompoundOrEmpty("References");

        for (String s : this.currentKeys) {
            boolean flag = PersistentStructureLegacy.OLD_STRUCTURE_REGISTRY_KEYS.contains(s.toLowerCase(Locale.ROOT));

            if (!nbttagcompound3.getLongArray(s).isPresent() && flag) {
                int i = 8;
                LongList longlist = new LongArrayList();

                for (int j = chunkcoordintpair.x - 8; j <= chunkcoordintpair.x + 8; ++j) {
                    for (int k = chunkcoordintpair.z - 8; k <= chunkcoordintpair.z + 8; ++k) {
                        if (this.hasLegacyStart(j, k, s)) {
                            longlist.add(ChunkCoordIntPair.asLong(j, k));
                        }
                    }
                }

                nbttagcompound3.putLongArray(s, longlist.toLongArray());
            }
        }

        nbttagcompound2.put("References", nbttagcompound3);
        nbttagcompound1.put("Structures", nbttagcompound2);
        nbttagcompound.put("Level", nbttagcompound1);
        return nbttagcompound;
    }

    private boolean hasLegacyStart(int i, int j, String s) {
        return !this.hasLegacyData ? false : this.dataMap.get(s) != null && ((PersistentIndexed) this.indexMap.get(PersistentStructureLegacy.CURRENT_TO_LEGACY_MAP.get(s))).hasStartIndex(ChunkCoordIntPair.asLong(i, j));
    }

    private boolean isUnhandledStructureStart(int i, int j) {
        if (!this.hasLegacyData) {
            return false;
        } else {
            for (String s : this.currentKeys) {
                if (this.dataMap.get(s) != null && ((PersistentIndexed) this.indexMap.get(PersistentStructureLegacy.CURRENT_TO_LEGACY_MAP.get(s))).hasUnhandledIndex(ChunkCoordIntPair.asLong(i, j))) {
                    return true;
                }
            }

            return false;
        }
    }

    private NBTTagCompound updateStructureStart(NBTTagCompound nbttagcompound, ChunkCoordIntPair chunkcoordintpair) {
        NBTTagCompound nbttagcompound1 = nbttagcompound.getCompoundOrEmpty("Level");
        NBTTagCompound nbttagcompound2 = nbttagcompound1.getCompoundOrEmpty("Structures");
        NBTTagCompound nbttagcompound3 = nbttagcompound2.getCompoundOrEmpty("Starts");

        for (String s : this.currentKeys) {
            Long2ObjectMap<NBTTagCompound> long2objectmap = (Long2ObjectMap) this.dataMap.get(s);

            if (long2objectmap != null) {
                long i = chunkcoordintpair.toLong();

                if (((PersistentIndexed) this.indexMap.get(PersistentStructureLegacy.CURRENT_TO_LEGACY_MAP.get(s))).hasUnhandledIndex(i)) {
                    NBTTagCompound nbttagcompound4 = (NBTTagCompound) long2objectmap.get(i);

                    if (nbttagcompound4 != null) {
                        nbttagcompound3.put(s, nbttagcompound4);
                    }
                }
            }
        }

        nbttagcompound2.put("Starts", nbttagcompound3);
        nbttagcompound1.put("Structures", nbttagcompound2);
        nbttagcompound.put("Level", nbttagcompound1);
        return nbttagcompound;
    }

    private void populateCaches(@Nullable WorldPersistentData worldpersistentdata) {
        if (worldpersistentdata != null) {
            for (String s : this.legacyKeys) {
                NBTTagCompound nbttagcompound = new NBTTagCompound();

                try {
                    nbttagcompound = worldpersistentdata.readTagFromDisk(s, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES, 1493).getCompoundOrEmpty("data").getCompoundOrEmpty("Features");
                    if (nbttagcompound.isEmpty()) {
                        continue;
                    }
                } catch (IOException ioexception) {
                    ;
                }

                nbttagcompound.forEach((s1, nbtbase) -> {
                    if (nbtbase instanceof NBTTagCompound nbttagcompound1) {
                        long i = ChunkCoordIntPair.asLong(nbttagcompound1.getIntOr("ChunkX", 0), nbttagcompound1.getIntOr("ChunkZ", 0));
                        NBTTagList nbttaglist = nbttagcompound1.getListOrEmpty("Children");

                        if (!nbttaglist.isEmpty()) {
                            Optional<String> optional = nbttaglist.getCompound(0).flatMap((nbttagcompound2) -> {
                                return nbttagcompound2.getString("id");
                            });
                            Map<String, String> map = PersistentStructureLegacy.LEGACY_TO_CURRENT_MAP; // CraftBukkit - decompile error

                            Objects.requireNonNull(map);
                            optional.map(map::get).ifPresent((s2) -> {
                                nbttagcompound1.putString("id", s2);
                            });
                        }

                        nbttagcompound1.getString("id").ifPresent((s2) -> {
                            ((Long2ObjectMap) this.dataMap.computeIfAbsent(s2, (s3) -> {
                                return new Long2ObjectOpenHashMap();
                            })).put(i, nbttagcompound1);
                        });
                    }
                });
                String s1 = s + "_index";
                PersistentIndexed persistentindexed = (PersistentIndexed) worldpersistentdata.computeIfAbsent(PersistentIndexed.type(s1));

                if (persistentindexed.getAll().isEmpty()) {
                    PersistentIndexed persistentindexed1 = new PersistentIndexed();

                    this.indexMap.put(s, persistentindexed1);
                    nbttagcompound.forEach((s2, nbtbase) -> {
                        if (nbtbase instanceof NBTTagCompound nbttagcompound1) {
                            persistentindexed1.addIndex(ChunkCoordIntPair.asLong(nbttagcompound1.getIntOr("ChunkX", 0), nbttagcompound1.getIntOr("ChunkZ", 0)));
                        }

                    });
                } else {
                    this.indexMap.put(s, persistentindexed);
                }
            }

        }
    }

    public static PersistentStructureLegacy getLegacyStructureHandler(ResourceKey<WorldDimension> resourcekey, @Nullable WorldPersistentData worldpersistentdata) { // CraftBukkit
        if (resourcekey == WorldDimension.OVERWORLD) { // CraftBukkit
            return new PersistentStructureLegacy(worldpersistentdata, ImmutableList.of("Monument", "Stronghold", "Village", "Mineshaft", "Temple", "Mansion"), ImmutableList.of("Village", "Mineshaft", "Mansion", "Igloo", "Desert_Pyramid", "Jungle_Pyramid", "Swamp_Hut", "Stronghold", "Monument"));
        } else if (resourcekey == WorldDimension.NETHER) { // CraftBukkit
            List<String> list = ImmutableList.of("Fortress");

            return new PersistentStructureLegacy(worldpersistentdata, list, list);
        } else if (resourcekey == WorldDimension.END) { // CraftBukkit
            List<String> list1 = ImmutableList.of("EndCity");

            return new PersistentStructureLegacy(worldpersistentdata, list1, list1);
        } else {
            throw new RuntimeException(String.format(Locale.ROOT, "Unknown dimension type : %s", resourcekey));
        }
    }
}

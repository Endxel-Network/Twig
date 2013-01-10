package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Optionull;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.IRegistry;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.SectionPosition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.GameProfileSerializer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NbtException;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.VillagePlace;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.EnumSkyBlock;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeBase;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TileEntity;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.Chunk;
import net.minecraft.world.level.chunk.ChunkConverter;
import net.minecraft.world.level.chunk.ChunkSection;
import net.minecraft.world.level.chunk.DataPaletteBlock;
import net.minecraft.world.level.chunk.IChunkAccess;
import net.minecraft.world.level.chunk.IChunkProvider;
import net.minecraft.world.level.chunk.NibbleArray;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunkExtension;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidType;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTickList;
import net.minecraft.world.ticks.TickListChunk;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.nbt.NBTBase;
// CraftBukkit end

// CraftBukkit - persistentDataContainer
public record SerializableChunkData(IRegistry<BiomeBase> biomeRegistry, ChunkCoordIntPair chunkPos, int minSectionY, long lastUpdateTime, long inhabitedTime, ChunkStatus chunkStatus, @Nullable BlendingData.d blendingData, @Nullable BelowZeroRetrogen belowZeroRetrogen, ChunkConverter upgradeData, @Nullable long[] carvingMask, Map<HeightMap.Type, long[]> heightmaps, IChunkAccess.a packedTicks, ShortList[] postProcessingSections, boolean lightCorrect, List<SerializableChunkData.b> sectionData, List<NBTTagCompound> entities, List<NBTTagCompound> blockEntities, NBTTagCompound structureData, @Nullable NBTBase persistentDataContainer) {

    public static final Codec<DataPaletteBlock<IBlockData>> BLOCK_STATE_CODEC = DataPaletteBlock.codecRW(Block.BLOCK_STATE_REGISTRY, IBlockData.CODEC, DataPaletteBlock.d.SECTION_STATES, Blocks.AIR.defaultBlockState());
    private static final Codec<List<TickListChunk<Block>>> BLOCK_TICKS_CODEC = TickListChunk.codec(BuiltInRegistries.BLOCK.byNameCodec()).listOf();
    private static final Codec<List<TickListChunk<FluidType>>> FLUID_TICKS_CODEC = TickListChunk.codec(BuiltInRegistries.FLUID.byNameCodec()).listOf();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_UPGRADE_DATA = "UpgradeData";
    private static final String BLOCK_TICKS_TAG = "block_ticks";
    private static final String FLUID_TICKS_TAG = "fluid_ticks";
    public static final String X_POS_TAG = "xPos";
    public static final String Z_POS_TAG = "zPos";
    public static final String HEIGHTMAPS_TAG = "Heightmaps";
    public static final String IS_LIGHT_ON_TAG = "isLightOn";
    public static final String SECTIONS_TAG = "sections";
    public static final String BLOCK_LIGHT_TAG = "BlockLight";
    public static final String SKY_LIGHT_TAG = "SkyLight";

    @Nullable
    public static SerializableChunkData parse(LevelHeightAccessor levelheightaccessor, IRegistryCustom iregistrycustom, NBTTagCompound nbttagcompound) {
        if (nbttagcompound.getString("Status").isEmpty()) {
            return null;
        } else {
            ChunkCoordIntPair chunkcoordintpair = new ChunkCoordIntPair(nbttagcompound.getIntOr("xPos", 0), nbttagcompound.getIntOr("zPos", 0));
            long i = nbttagcompound.getLongOr("LastUpdate", 0L);
            long j = nbttagcompound.getLongOr("InhabitedTime", 0L);
            ChunkStatus chunkstatus = (ChunkStatus) nbttagcompound.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY);
            ChunkConverter chunkconverter = (ChunkConverter) nbttagcompound.getCompound("UpgradeData").map((nbttagcompound1) -> {
                return new ChunkConverter(nbttagcompound1, levelheightaccessor);
            }).orElse(ChunkConverter.EMPTY);
            boolean flag = nbttagcompound.getBooleanOr("isLightOn", false);
            BlendingData.d blendingdata_d = (BlendingData.d) nbttagcompound.read("blending_data", BlendingData.d.CODEC).orElse(null); // CraftBukkit - decompile error
            BelowZeroRetrogen belowzeroretrogen = (BelowZeroRetrogen) nbttagcompound.read("below_zero_retrogen", BelowZeroRetrogen.CODEC).orElse(null); // CraftBukkit - decompile error
            long[] along = (long[]) nbttagcompound.getLongArray("carving_mask").orElse(null); // CraftBukkit - decompile error
            Map<HeightMap.Type, long[]> map = new EnumMap(HeightMap.Type.class);

            nbttagcompound.getCompound("Heightmaps").ifPresent((nbttagcompound1) -> {
                for (HeightMap.Type heightmap_type : chunkstatus.heightmapsAfter()) {
                    nbttagcompound1.getLongArray(heightmap_type.getSerializationKey()).ifPresent((along1) -> {
                        map.put(heightmap_type, along1);
                    });
                }

            });
            List<TickListChunk<Block>> list = TickListChunk.filterTickListForChunk((List) nbttagcompound.read("block_ticks", SerializableChunkData.BLOCK_TICKS_CODEC).orElse(List.of()), chunkcoordintpair);
            List<TickListChunk<FluidType>> list1 = TickListChunk.filterTickListForChunk((List) nbttagcompound.read("fluid_ticks", SerializableChunkData.FLUID_TICKS_CODEC).orElse(List.of()), chunkcoordintpair);
            IChunkAccess.a ichunkaccess_a = new IChunkAccess.a(list, list1);
            NBTTagList nbttaglist = nbttagcompound.getListOrEmpty("PostProcessing");
            ShortList[] ashortlist = new ShortList[nbttaglist.size()];

            for (int k = 0; k < nbttaglist.size(); ++k) {
                NBTTagList nbttaglist1 = nbttaglist.getListOrEmpty(k);
                ShortList shortlist = new ShortArrayList(nbttaglist1.size());

                for (int l = 0; l < nbttaglist1.size(); ++l) {
                    shortlist.add(nbttaglist1.getShortOr(l, (short) 0));
                }

                ashortlist[k] = shortlist;
            }

            List<NBTTagCompound> list2 = nbttagcompound.getList("entities").stream().flatMap(NBTTagList::compoundStream).toList();
            List<NBTTagCompound> list3 = nbttagcompound.getList("block_entities").stream().flatMap(NBTTagList::compoundStream).toList();
            NBTTagCompound nbttagcompound1 = nbttagcompound.getCompoundOrEmpty("structures");
            NBTTagList nbttaglist2 = nbttagcompound.getListOrEmpty("sections");
            List<SerializableChunkData.b> list4 = new ArrayList(nbttaglist2.size());
            IRegistry<BiomeBase> iregistry = iregistrycustom.lookupOrThrow(Registries.BIOME);
            Codec<DataPaletteBlock<Holder<BiomeBase>>> codec = makeBiomeCodecRW(iregistry); // CraftBukkit - read/write

            for (int i1 = 0; i1 < nbttaglist2.size(); ++i1) {
                Optional<NBTTagCompound> optional = nbttaglist2.getCompound(i1);

                if (!optional.isEmpty()) {
                    NBTTagCompound nbttagcompound2 = (NBTTagCompound) optional.get();
                    int j1 = nbttagcompound2.getByteOr("Y", (byte) 0);
                    ChunkSection chunksection;

                    if (j1 >= levelheightaccessor.getMinSectionY() && j1 <= levelheightaccessor.getMaxSectionY()) {
                        DataPaletteBlock<IBlockData> datapaletteblock = (DataPaletteBlock) nbttagcompound2.getCompound("block_states").map((nbttagcompound3) -> {
                            return (DataPaletteBlock) SerializableChunkData.BLOCK_STATE_CODEC.parse(DynamicOpsNBT.INSTANCE, nbttagcompound3).promotePartial((s) -> {
                                logErrors(chunkcoordintpair, j1, s);
                            }).getOrThrow(SerializableChunkData.a::new);
                        }).orElseGet(() -> {
                            return new DataPaletteBlock(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), DataPaletteBlock.d.SECTION_STATES);
                        });
                        DataPaletteBlock<Holder<BiomeBase>> palettedcontainerro = nbttagcompound2.getCompound("biomes").map((nbttagcompound3) -> { // CraftBukkit - read/write
                            return codec.parse(DynamicOpsNBT.INSTANCE, nbttagcompound3).promotePartial((s) -> { // CraftBukkit - read/write
                                logErrors(chunkcoordintpair, j1, s);
                            }).getOrThrow(SerializableChunkData.a::new);
                        }).orElseGet(() -> {
                            return new DataPaletteBlock(iregistry.asHolderIdMap(), iregistry.getOrThrow(Biomes.PLAINS), DataPaletteBlock.d.SECTION_BIOMES);
                        });

                        chunksection = new ChunkSection(datapaletteblock, palettedcontainerro);
                    } else {
                        chunksection = null;
                    }

                    NibbleArray nibblearray = (NibbleArray) nbttagcompound2.getByteArray("BlockLight").map(NibbleArray::new).orElse(null); // CraftBukkit - decompile error
                    NibbleArray nibblearray1 = (NibbleArray) nbttagcompound2.getByteArray("SkyLight").map(NibbleArray::new).orElse(null); // CraftBukkit - decompile error

                    list4.add(new SerializableChunkData.b(j1, chunksection, nibblearray, nibblearray1));
                }
            }

            // CraftBukkit - ChunkBukkitValues
            return new SerializableChunkData(iregistry, chunkcoordintpair, levelheightaccessor.getMinSectionY(), i, j, chunkstatus, blendingdata_d, belowzeroretrogen, chunkconverter, along, map, ichunkaccess_a, ashortlist, flag, list4, list2, list3, nbttagcompound1, nbttagcompound.get("ChunkBukkitValues"));
        }
    }

    public ProtoChunk read(WorldServer worldserver, VillagePlace villageplace, RegionStorageInfo regionstorageinfo, ChunkCoordIntPair chunkcoordintpair) {
        if (!Objects.equals(chunkcoordintpair, this.chunkPos)) {
            SerializableChunkData.LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", new Object[]{chunkcoordintpair, chunkcoordintpair, this.chunkPos});
            worldserver.getServer().reportMisplacedChunk(this.chunkPos, chunkcoordintpair, regionstorageinfo);
        }

        int i = worldserver.getSectionsCount();
        ChunkSection[] achunksection = new ChunkSection[i];
        boolean flag = worldserver.dimensionType().hasSkyLight();
        IChunkProvider ichunkprovider = worldserver.getChunkSource();
        LevelLightEngine levellightengine = ichunkprovider.getLightEngine();
        IRegistry<BiomeBase> iregistry = worldserver.registryAccess().lookupOrThrow(Registries.BIOME);
        boolean flag1 = false;

        for (SerializableChunkData.b serializablechunkdata_b : this.sectionData) {
            SectionPosition sectionposition = SectionPosition.of(chunkcoordintpair, serializablechunkdata_b.y);

            if (serializablechunkdata_b.chunkSection != null) {
                achunksection[worldserver.getSectionIndexFromSectionY(serializablechunkdata_b.y)] = serializablechunkdata_b.chunkSection;
                villageplace.checkConsistencyWithBlocks(sectionposition, serializablechunkdata_b.chunkSection);
            }

            boolean flag2 = serializablechunkdata_b.blockLight != null;
            boolean flag3 = flag && serializablechunkdata_b.skyLight != null;

            if (flag2 || flag3) {
                if (!flag1) {
                    levellightengine.retainData(chunkcoordintpair, true);
                    flag1 = true;
                }

                if (flag2) {
                    levellightengine.queueSectionData(EnumSkyBlock.BLOCK, sectionposition, serializablechunkdata_b.blockLight);
                }

                if (flag3) {
                    levellightengine.queueSectionData(EnumSkyBlock.SKY, sectionposition, serializablechunkdata_b.skyLight);
                }
            }
        }

        ChunkType chunktype = this.chunkStatus.getChunkType();
        IChunkAccess ichunkaccess;

        if (chunktype == ChunkType.LEVELCHUNK) {
            LevelChunkTicks<Block> levelchunkticks = new LevelChunkTicks<Block>(this.packedTicks.blocks());
            LevelChunkTicks<FluidType> levelchunkticks1 = new LevelChunkTicks<FluidType>(this.packedTicks.fluids());

            ichunkaccess = new Chunk(worldserver.getLevel(), chunkcoordintpair, this.upgradeData, levelchunkticks, levelchunkticks1, this.inhabitedTime, achunksection, postLoadChunk(worldserver, this.entities, this.blockEntities), BlendingData.unpack(this.blendingData));
        } else {
            ProtoChunkTickList<Block> protochunkticklist = ProtoChunkTickList.<Block>load(this.packedTicks.blocks());
            ProtoChunkTickList<FluidType> protochunkticklist1 = ProtoChunkTickList.<FluidType>load(this.packedTicks.fluids());
            ProtoChunk protochunk = new ProtoChunk(chunkcoordintpair, this.upgradeData, achunksection, protochunkticklist, protochunkticklist1, worldserver, iregistry, BlendingData.unpack(this.blendingData));

            ichunkaccess = protochunk;
            ((IChunkAccess) protochunk).setInhabitedTime(this.inhabitedTime);
            if (this.belowZeroRetrogen != null) {
                protochunk.setBelowZeroRetrogen(this.belowZeroRetrogen);
            }

            protochunk.setPersistedStatus(this.chunkStatus);
            if (this.chunkStatus.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                protochunk.setLightEngine(levellightengine);
            }
        }

        // CraftBukkit start - load chunk persistent data from nbt - SPIGOT-6814: Already load PDC here to account for 1.17 to 1.18 chunk upgrading.
        if (persistentDataContainer instanceof NBTTagCompound) {
            ichunkaccess.persistentDataContainer.putAll((NBTTagCompound) persistentDataContainer);
        }
        // CraftBukkit end

        ichunkaccess.setLightCorrect(this.lightCorrect);
        EnumSet<HeightMap.Type> enumset = EnumSet.noneOf(HeightMap.Type.class);

        for (HeightMap.Type heightmap_type : ichunkaccess.getPersistedStatus().heightmapsAfter()) {
            long[] along = (long[]) this.heightmaps.get(heightmap_type);

            if (along != null) {
                ichunkaccess.setHeightmap(heightmap_type, along);
            } else {
                enumset.add(heightmap_type);
            }
        }

        HeightMap.primeHeightmaps(ichunkaccess, enumset);
        ichunkaccess.setAllStarts(unpackStructureStart(StructurePieceSerializationContext.fromLevel(worldserver), this.structureData, worldserver.getSeed()));
        ichunkaccess.setAllReferences(unpackStructureReferences(worldserver.registryAccess(), chunkcoordintpair, this.structureData));

        for (int j = 0; j < this.postProcessingSections.length; ++j) {
            ichunkaccess.addPackedPostProcess(this.postProcessingSections[j], j);
        }

        if (chunktype == ChunkType.LEVELCHUNK) {
            return new ProtoChunkExtension((Chunk) ichunkaccess, false);
        } else {
            ProtoChunk protochunk1 = (ProtoChunk) ichunkaccess;

            for (NBTTagCompound nbttagcompound : this.entities) {
                protochunk1.addEntity(nbttagcompound);
            }

            for (NBTTagCompound nbttagcompound1 : this.blockEntities) {
                protochunk1.setBlockEntityNbt(nbttagcompound1);
            }

            if (this.carvingMask != null) {
                protochunk1.setCarvingMask(new CarvingMask(this.carvingMask, ichunkaccess.getMinY()));
            }

            return protochunk1;
        }
    }

    private static void logErrors(ChunkCoordIntPair chunkcoordintpair, int i, String s) {
        SerializableChunkData.LOGGER.error("Recoverable errors when loading section [{}, {}, {}]: {}", new Object[]{chunkcoordintpair.x, i, chunkcoordintpair.z, s});
    }

    private static Codec<PalettedContainerRO<Holder<BiomeBase>>> makeBiomeCodec(IRegistry<BiomeBase> iregistry) {
        return DataPaletteBlock.codecRO(iregistry.asHolderIdMap(), iregistry.holderByNameCodec(), DataPaletteBlock.d.SECTION_BIOMES, iregistry.getOrThrow(Biomes.PLAINS));
    }

    // CraftBukkit start - read/write
    private static Codec<DataPaletteBlock<Holder<BiomeBase>>> makeBiomeCodecRW(IRegistry<BiomeBase> iregistry) {
        return DataPaletteBlock.codecRW(iregistry.asHolderIdMap(), iregistry.holderByNameCodec(), DataPaletteBlock.d.SECTION_BIOMES, iregistry.getOrThrow(Biomes.PLAINS));
    }
    // CraftBukkit end

    public static SerializableChunkData copyOf(WorldServer worldserver, IChunkAccess ichunkaccess) {
        if (!ichunkaccess.canBeSerialized()) {
            throw new IllegalArgumentException("Chunk can't be serialized: " + String.valueOf(ichunkaccess));
        } else {
            ChunkCoordIntPair chunkcoordintpair = ichunkaccess.getPos();
            List<SerializableChunkData.b> list = new ArrayList();
            ChunkSection[] achunksection = ichunkaccess.getSections();
            LevelLightEngine levellightengine = worldserver.getChunkSource().getLightEngine();

            for (int i = levellightengine.getMinLightSection(); i < ((LevelLightEngine) levellightengine).getMaxLightSection(); ++i) {
                int j = ichunkaccess.getSectionIndexFromSectionY(i);
                boolean flag = j >= 0 && j < achunksection.length;
                NibbleArray nibblearray = levellightengine.getLayerListener(EnumSkyBlock.BLOCK).getDataLayerData(SectionPosition.of(chunkcoordintpair, i));
                NibbleArray nibblearray1 = levellightengine.getLayerListener(EnumSkyBlock.SKY).getDataLayerData(SectionPosition.of(chunkcoordintpair, i));
                NibbleArray nibblearray2 = nibblearray != null && !nibblearray.isEmpty() ? nibblearray.copy() : null;
                NibbleArray nibblearray3 = nibblearray1 != null && !nibblearray1.isEmpty() ? nibblearray1.copy() : null;

                if (flag || nibblearray2 != null || nibblearray3 != null) {
                    ChunkSection chunksection = flag ? achunksection[j].copy() : null;

                    list.add(new SerializableChunkData.b(i, chunksection, nibblearray2, nibblearray3));
                }
            }

            List<NBTTagCompound> list1 = new ArrayList(ichunkaccess.getBlockEntitiesPos().size());

            for (BlockPosition blockposition : ichunkaccess.getBlockEntitiesPos()) {
                NBTTagCompound nbttagcompound = ichunkaccess.getBlockEntityNbtForSaving(blockposition, worldserver.registryAccess());

                if (nbttagcompound != null) {
                    list1.add(nbttagcompound);
                }
            }

            List<NBTTagCompound> list2 = new ArrayList();
            long[] along = null;

            if (ichunkaccess.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
                ProtoChunk protochunk = (ProtoChunk) ichunkaccess;

                list2.addAll(protochunk.getEntities());
                CarvingMask carvingmask = protochunk.getCarvingMask();

                if (carvingmask != null) {
                    along = carvingmask.toArray();
                }
            }

            Map<HeightMap.Type, long[]> map = new EnumMap(HeightMap.Type.class);

            for (Map.Entry<HeightMap.Type, HeightMap> map_entry : ichunkaccess.getHeightmaps()) {
                if (ichunkaccess.getPersistedStatus().heightmapsAfter().contains(map_entry.getKey())) {
                    long[] along1 = ((HeightMap) map_entry.getValue()).getRawData();

                    map.put((HeightMap.Type) map_entry.getKey(), (long[]) along1.clone());
                }
            }

            IChunkAccess.a ichunkaccess_a = ichunkaccess.getTicksForSerialization(worldserver.getGameTime());
            ShortList[] ashortlist = (ShortList[]) Arrays.stream(ichunkaccess.getPostProcessing()).map((shortlist) -> {
                return shortlist != null ? new ShortArrayList(shortlist) : null;
            }).toArray((k) -> {
                return new ShortList[k];
            });
            NBTTagCompound nbttagcompound1 = packStructureData(StructurePieceSerializationContext.fromLevel(worldserver), chunkcoordintpair, ichunkaccess.getAllStarts(), ichunkaccess.getAllReferences());

            // CraftBukkit start - store chunk persistent data in nbt
            NBTTagCompound persistentDataContainer = null;
            if (!ichunkaccess.persistentDataContainer.isEmpty()) { // SPIGOT-6814: Always save PDC to account for 1.17 to 1.18 chunk upgrading.
                persistentDataContainer = ichunkaccess.persistentDataContainer.toTagCompound();
            }

            return new SerializableChunkData(worldserver.registryAccess().lookupOrThrow(Registries.BIOME), chunkcoordintpair, ichunkaccess.getMinSectionY(), worldserver.getGameTime(), ichunkaccess.getInhabitedTime(), ichunkaccess.getPersistedStatus(), (BlendingData.d) Optionull.map(ichunkaccess.getBlendingData(), BlendingData::pack), ichunkaccess.getBelowZeroRetrogen(), ichunkaccess.getUpgradeData().copy(), along, map, ichunkaccess_a, ashortlist, ichunkaccess.isLightCorrect(), list, list2, list1, nbttagcompound1, persistentDataContainer);
            // CraftBukkit end
        }
    }

    public NBTTagCompound write() {
        NBTTagCompound nbttagcompound = GameProfileSerializer.addCurrentDataVersion(new NBTTagCompound());

        nbttagcompound.putInt("xPos", this.chunkPos.x);
        nbttagcompound.putInt("yPos", this.minSectionY);
        nbttagcompound.putInt("zPos", this.chunkPos.z);
        nbttagcompound.putLong("LastUpdate", this.lastUpdateTime);
        nbttagcompound.putLong("InhabitedTime", this.inhabitedTime);
        nbttagcompound.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(this.chunkStatus).toString());
        nbttagcompound.storeNullable("blending_data", BlendingData.d.CODEC, this.blendingData);
        nbttagcompound.storeNullable("below_zero_retrogen", BelowZeroRetrogen.CODEC, this.belowZeroRetrogen);
        if (!this.upgradeData.isEmpty()) {
            nbttagcompound.put("UpgradeData", this.upgradeData.write());
        }

        NBTTagList nbttaglist = new NBTTagList();
        Codec<PalettedContainerRO<Holder<BiomeBase>>> codec = makeBiomeCodec(this.biomeRegistry);

        for (SerializableChunkData.b serializablechunkdata_b : this.sectionData) {
            NBTTagCompound nbttagcompound1 = new NBTTagCompound();
            ChunkSection chunksection = serializablechunkdata_b.chunkSection;

            if (chunksection != null) {
                nbttagcompound1.store("block_states", SerializableChunkData.BLOCK_STATE_CODEC, chunksection.getStates());
                nbttagcompound1.store("biomes", codec, chunksection.getBiomes());
            }

            if (serializablechunkdata_b.blockLight != null) {
                nbttagcompound1.putByteArray("BlockLight", serializablechunkdata_b.blockLight.getData());
            }

            if (serializablechunkdata_b.skyLight != null) {
                nbttagcompound1.putByteArray("SkyLight", serializablechunkdata_b.skyLight.getData());
            }

            if (!nbttagcompound1.isEmpty()) {
                nbttagcompound1.putByte("Y", (byte) serializablechunkdata_b.y);
                nbttaglist.add(nbttagcompound1);
            }
        }

        nbttagcompound.put("sections", nbttaglist);
        if (this.lightCorrect) {
            nbttagcompound.putBoolean("isLightOn", true);
        }

        NBTTagList nbttaglist1 = new NBTTagList();

        nbttaglist1.addAll(this.blockEntities);
        nbttagcompound.put("block_entities", nbttaglist1);
        if (this.chunkStatus.getChunkType() == ChunkType.PROTOCHUNK) {
            NBTTagList nbttaglist2 = new NBTTagList();

            nbttaglist2.addAll(this.entities);
            nbttagcompound.put("entities", nbttaglist2);
            if (this.carvingMask != null) {
                nbttagcompound.putLongArray("carving_mask", this.carvingMask);
            }
        }

        saveTicks(nbttagcompound, this.packedTicks);
        nbttagcompound.put("PostProcessing", packOffsets(this.postProcessingSections));
        NBTTagCompound nbttagcompound2 = new NBTTagCompound();

        this.heightmaps.forEach((heightmap_type, along) -> {
            nbttagcompound2.put(heightmap_type.getSerializationKey(), new NBTTagLongArray(along));
        });
        nbttagcompound.put("Heightmaps", nbttagcompound2);
        nbttagcompound.put("structures", this.structureData);
        // CraftBukkit start - store chunk persistent data in nbt
        if (persistentDataContainer != null) { // SPIGOT-6814: Always save PDC to account for 1.17 to 1.18 chunk upgrading.
            nbttagcompound.put("ChunkBukkitValues", persistentDataContainer);
        }
        // CraftBukkit end
        return nbttagcompound;
    }

    private static void saveTicks(NBTTagCompound nbttagcompound, IChunkAccess.a ichunkaccess_a) {
        nbttagcompound.store("block_ticks", SerializableChunkData.BLOCK_TICKS_CODEC, ichunkaccess_a.blocks());
        nbttagcompound.store("fluid_ticks", SerializableChunkData.FLUID_TICKS_CODEC, ichunkaccess_a.fluids());
    }

    public static ChunkStatus getChunkStatusFromTag(@Nullable NBTTagCompound nbttagcompound) {
        return nbttagcompound != null ? (ChunkStatus) nbttagcompound.read("Status", ChunkStatus.CODEC).orElse(ChunkStatus.EMPTY) : ChunkStatus.EMPTY;
    }

    @Nullable
    private static Chunk.c postLoadChunk(WorldServer worldserver, List<NBTTagCompound> list, List<NBTTagCompound> list1) {
        return list.isEmpty() && list1.isEmpty() ? null : (chunk) -> {
            worldserver.timings.syncChunkLoadEntitiesTimer.startTiming(); // Spigot
            if (!list.isEmpty()) {
                worldserver.addLegacyChunkEntities(EntityTypes.loadEntitiesRecursive(list, worldserver, EntitySpawnReason.LOAD));
            }
            worldserver.timings.syncChunkLoadEntitiesTimer.stopTiming(); // Spigot

            worldserver.timings.syncChunkLoadTileEntitiesTimer.startTiming(); // Spigot
            for (NBTTagCompound nbttagcompound : list1) {
                boolean flag = nbttagcompound.getBooleanOr("keepPacked", false);

                if (flag) {
                    chunk.setBlockEntityNbt(nbttagcompound);
                } else {
                    BlockPosition blockposition = TileEntity.getPosFromTag(chunk.getPos(), nbttagcompound);
                    TileEntity tileentity = TileEntity.loadStatic(blockposition, chunk.getBlockState(blockposition), nbttagcompound, worldserver.registryAccess());

                    if (tileentity != null) {
                        chunk.setBlockEntity(tileentity);
                    }
                }
            }
            worldserver.timings.syncChunkLoadTileEntitiesTimer.stopTiming(); // Spigot

        };
    }

    private static NBTTagCompound packStructureData(StructurePieceSerializationContext structurepieceserializationcontext, ChunkCoordIntPair chunkcoordintpair, Map<Structure, StructureStart> map, Map<Structure, LongSet> map1) {
        NBTTagCompound nbttagcompound = new NBTTagCompound();
        NBTTagCompound nbttagcompound1 = new NBTTagCompound();
        IRegistry<Structure> iregistry = structurepieceserializationcontext.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        for (Map.Entry<Structure, StructureStart> map_entry : map.entrySet()) {
            MinecraftKey minecraftkey = iregistry.getKey((Structure) map_entry.getKey());

            nbttagcompound1.put(minecraftkey.toString(), ((StructureStart) map_entry.getValue()).createTag(structurepieceserializationcontext, chunkcoordintpair));
        }

        nbttagcompound.put("starts", nbttagcompound1);
        NBTTagCompound nbttagcompound2 = new NBTTagCompound();

        for (Map.Entry<Structure, LongSet> map_entry1 : map1.entrySet()) {
            if (!((LongSet) map_entry1.getValue()).isEmpty()) {
                MinecraftKey minecraftkey1 = iregistry.getKey((Structure) map_entry1.getKey());

                nbttagcompound2.putLongArray(minecraftkey1.toString(), ((LongSet) map_entry1.getValue()).toLongArray());
            }
        }

        nbttagcompound.put("References", nbttagcompound2);
        return nbttagcompound;
    }

    private static Map<Structure, StructureStart> unpackStructureStart(StructurePieceSerializationContext structurepieceserializationcontext, NBTTagCompound nbttagcompound, long i) {
        Map<Structure, StructureStart> map = Maps.newHashMap();
        IRegistry<Structure> iregistry = structurepieceserializationcontext.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        NBTTagCompound nbttagcompound1 = nbttagcompound.getCompoundOrEmpty("starts");

        for (String s : nbttagcompound1.keySet()) {
            MinecraftKey minecraftkey = MinecraftKey.tryParse(s);
            Structure structure = (Structure) iregistry.getValue(minecraftkey);

            if (structure == null) {
                SerializableChunkData.LOGGER.error("Unknown structure start: {}", minecraftkey);
            } else {
                StructureStart structurestart = StructureStart.loadStaticStart(structurepieceserializationcontext, nbttagcompound1.getCompoundOrEmpty(s), i);

                if (structurestart != null) {
                    // CraftBukkit start - load persistent data for structure start
                    net.minecraft.nbt.NBTBase persistentBase = nbttagcompound1.getCompoundOrEmpty(s).get("StructureBukkitValues");
                    if (persistentBase instanceof NBTTagCompound) {
                        structurestart.persistentDataContainer.putAll((NBTTagCompound) persistentBase);
                    }
                    // CraftBukkit end
                    map.put(structure, structurestart);
                }
            }
        }

        return map;
    }

    private static Map<Structure, LongSet> unpackStructureReferences(IRegistryCustom iregistrycustom, ChunkCoordIntPair chunkcoordintpair, NBTTagCompound nbttagcompound) {
        Map<Structure, LongSet> map = Maps.newHashMap();
        IRegistry<Structure> iregistry = iregistrycustom.lookupOrThrow(Registries.STRUCTURE);
        NBTTagCompound nbttagcompound1 = nbttagcompound.getCompoundOrEmpty("References");

        nbttagcompound1.forEach((s, nbtbase) -> {
            MinecraftKey minecraftkey = MinecraftKey.tryParse(s);
            Structure structure = (Structure) iregistry.getValue(minecraftkey);

            if (structure == null) {
                SerializableChunkData.LOGGER.warn("Found reference to unknown structure '{}' in chunk {}, discarding", minecraftkey, chunkcoordintpair);
            } else {
                Optional<long[]> optional = nbtbase.asLongArray();

                if (!optional.isEmpty()) {
                    map.put(structure, new LongOpenHashSet(Arrays.stream((long[]) optional.get()).filter((i) -> {
                        ChunkCoordIntPair chunkcoordintpair1 = new ChunkCoordIntPair(i);

                        if (chunkcoordintpair1.getChessboardDistance(chunkcoordintpair) > 8) {
                            SerializableChunkData.LOGGER.warn("Found invalid structure reference [ {} @ {} ] for chunk {}.", new Object[]{minecraftkey, chunkcoordintpair1, chunkcoordintpair});
                            return false;
                        } else {
                            return true;
                        }
                    }).toArray()));
                }
            }
        });
        return map;
    }

    private static NBTTagList packOffsets(ShortList[] ashortlist) {
        NBTTagList nbttaglist = new NBTTagList();

        for (ShortList shortlist : ashortlist) {
            NBTTagList nbttaglist1 = new NBTTagList();

            if (shortlist != null) {
                for (int i = 0; i < shortlist.size(); ++i) {
                    nbttaglist1.add(NBTTagShort.valueOf(shortlist.getShort(i)));
                }
            }

            nbttaglist.add(nbttaglist1);
        }

        return nbttaglist;
    }

    public static record b(int y, @Nullable ChunkSection chunkSection, @Nullable NibbleArray blockLight, @Nullable NibbleArray skyLight) {

    }

    public static class a extends NbtException {

        public a(String s) {
            super(s);
        }
    }
}

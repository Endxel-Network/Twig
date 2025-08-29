package net.minecraft.world.entity.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketDebug;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.ai.village.poi.VillagePlace;
import net.minecraft.world.entity.ai.village.poi.VillagePlaceRecord;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionManager;
import net.minecraft.world.level.saveddata.PersistentBase;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3D;

public class PersistentRaid extends PersistentBase {

    private static final String RAID_FILE_ID = "raids";
    public static final Codec<PersistentRaid> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(PersistentRaid.a.CODEC.listOf().optionalFieldOf("raids", List.of()).forGetter((persistentraid) -> {
            return persistentraid.raidMap.int2ObjectEntrySet().stream().map(PersistentRaid.a::from).toList();
        }), Codec.INT.fieldOf("next_id").forGetter((persistentraid) -> {
            return persistentraid.nextId;
        }), Codec.INT.fieldOf("tick").forGetter((persistentraid) -> {
            return persistentraid.tick;
        })).apply(instance, PersistentRaid::new);
    });
    public static final SavedDataType<PersistentRaid> TYPE = new SavedDataType<PersistentRaid>("raids", PersistentRaid::new, PersistentRaid.CODEC, DataFixTypes.SAVED_DATA_RAIDS);
    public static final SavedDataType<PersistentRaid> TYPE_END = new SavedDataType<PersistentRaid>("raids_end", PersistentRaid::new, PersistentRaid.CODEC, DataFixTypes.SAVED_DATA_RAIDS);
    public final Int2ObjectMap<Raid> raidMap = new Int2ObjectOpenHashMap();
    private int nextId = 1;
    private int tick;

    public static SavedDataType<PersistentRaid> getType(Holder<DimensionManager> holder) {
        return holder.is(BuiltinDimensionTypes.END) ? PersistentRaid.TYPE_END : PersistentRaid.TYPE;
    }

    public PersistentRaid() {
        this.setDirty();
    }

    private PersistentRaid(List<PersistentRaid.a> list, int i, int j) {
        for (PersistentRaid.a persistentraid_a : list) {
            this.raidMap.put(persistentraid_a.id, persistentraid_a.raid);
        }

        this.nextId = i;
        this.tick = j;
    }

    @Nullable
    public Raid get(int i) {
        return (Raid) this.raidMap.get(i);
    }

    public OptionalInt getId(Raid raid) {
        ObjectIterator objectiterator = this.raidMap.int2ObjectEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Int2ObjectMap.Entry<Raid> int2objectmap_entry = (Entry) objectiterator.next();

            if (int2objectmap_entry.getValue() == raid) {
                return OptionalInt.of(int2objectmap_entry.getIntKey());
            }
        }

        return OptionalInt.empty();
    }

    public void tick(WorldServer worldserver) {
        ++this.tick;
        Iterator<Raid> iterator = this.raidMap.values().iterator();

        while (((Iterator) iterator).hasNext()) {
            Raid raid = (Raid) iterator.next();

            if (worldserver.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
                raid.stop();
            }

            if (raid.isStopped()) {
                iterator.remove();
                this.setDirty();
            } else {
                raid.tick(worldserver);
            }
        }

        if (this.tick % 200 == 0) {
            this.setDirty();
        }

        PacketDebug.sendRaids(worldserver, this.raidMap.values());
    }

    public static boolean canJoinRaid(EntityRaider entityraider) {
        return entityraider.isAlive() && entityraider.canJoinRaid() && entityraider.getNoActionTime() <= 2400;
    }

    @Nullable
    public Raid createOrExtendRaid(EntityPlayer entityplayer, BlockPosition blockposition) {
        if (entityplayer.isSpectator()) {
            return null;
        } else {
            WorldServer worldserver = entityplayer.serverLevel();

            if (worldserver.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
                return null;
            } else {
                DimensionManager dimensionmanager = worldserver.dimensionType();

                if (!dimensionmanager.hasRaids()) {
                    return null;
                } else {
                    List<VillagePlaceRecord> list = worldserver.getPoiManager().getInRange((holder) -> {
                        return holder.is(PoiTypeTags.VILLAGE);
                    }, blockposition, 64, VillagePlace.Occupancy.IS_OCCUPIED).toList();
                    int i = 0;
                    Vec3D vec3d = Vec3D.ZERO;

                    for (VillagePlaceRecord villageplacerecord : list) {
                        BlockPosition blockposition1 = villageplacerecord.getPos();

                        vec3d = vec3d.add((double) blockposition1.getX(), (double) blockposition1.getY(), (double) blockposition1.getZ());
                        ++i;
                    }

                    BlockPosition blockposition2;

                    if (i > 0) {
                        vec3d = vec3d.scale(1.0D / (double) i);
                        blockposition2 = BlockPosition.containing(vec3d);
                    } else {
                        blockposition2 = blockposition;
                    }

                    Raid raid = this.getOrCreateRaid(worldserver, blockposition2);

                    /* CraftBukkit - moved down
                    if (!raid.isStarted() && !this.raidMap.containsValue(raid)) {
                        this.raidMap.put(this.getUniqueId(), raid);
                    }
                    */

                    if (!raid.isStarted() || (raid.isInProgress() && raid.getRaidOmenLevel() < raid.getMaxRaidOmenLevel())) { // CraftBukkit - fixed a bug with raid: players could add up Bad Omen level even when the raid had finished
                        // CraftBukkit start
                        if (!org.bukkit.craftbukkit.event.CraftEventFactory.callRaidTriggerEvent(raid, worldserver, entityplayer)) {
                            entityplayer.removeEffect(net.minecraft.world.effect.MobEffects.RAID_OMEN);
                            return null;
                        }

                        if (!raid.isStarted() && !this.raidMap.containsValue(raid)) {
                            this.raidMap.put(this.getUniqueId(), raid);
                        }
                        // CraftBukkit end
                        raid.absorbRaidOmen(entityplayer);
                    }

                    this.setDirty();
                    return raid;
                }
            }
        }
    }

    private Raid getOrCreateRaid(WorldServer worldserver, BlockPosition blockposition) {
        Raid raid = worldserver.getRaidAt(blockposition);

        return raid != null ? raid : new Raid(blockposition, worldserver.getDifficulty());
    }

    public static PersistentRaid load(NBTTagCompound nbttagcompound) {
        return (PersistentRaid) PersistentRaid.CODEC.parse(DynamicOpsNBT.INSTANCE, nbttagcompound).resultOrPartial().orElseGet(PersistentRaid::new);
    }

    private int getUniqueId() {
        return ++this.nextId;
    }

    @Nullable
    public Raid getNearbyRaid(BlockPosition blockposition, int i) {
        Raid raid = null;
        double d0 = (double) i;
        ObjectIterator objectiterator = this.raidMap.values().iterator();

        while (objectiterator.hasNext()) {
            Raid raid1 = (Raid) objectiterator.next();
            double d1 = raid1.getCenter().distSqr(blockposition);

            if (raid1.isActive() && d1 < d0) {
                raid = raid1;
                d0 = d1;
            }
        }

        return raid;
    }

    private static record a(int id, Raid raid) {

        public static final Codec<PersistentRaid.a> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(Codec.INT.fieldOf("id").forGetter(PersistentRaid.a::id), Raid.MAP_CODEC.forGetter(PersistentRaid.a::raid)).apply(instance, PersistentRaid.a::new);
        });

        public static PersistentRaid.a from(Int2ObjectMap.Entry<Raid> int2objectmap_entry) {
            return new PersistentRaid.a(int2objectmap_entry.getIntKey(), (Raid) int2objectmap_entry.getValue());
        }
    }
}

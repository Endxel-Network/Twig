package net.minecraft.world.level.saveddata.maps;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.PacketDataSerializer;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayOutMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagsItem;
import net.minecraft.util.MathHelper;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.decoration.EntityItemFrame;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.GeneratorAccess;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.World;
import net.minecraft.world.level.saveddata.PersistentBase;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.map.CraftMapCursor;
import org.bukkit.craftbukkit.map.CraftMapView;
import org.bukkit.craftbukkit.util.CraftChatMessage;
// CraftBukkit end

public class WorldMap extends PersistentBase {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAP_SIZE = 128;
    private static final int HALF_MAP_SIZE = 64;
    public static final int MAX_SCALE = 4;
    public static final int TRACKED_DECORATION_LIMIT = 256;
    private static final String FRAME_PREFIX = "frame-";
    public static final Codec<WorldMap> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(World.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter((worldmap) -> {
            return worldmap.dimension;
        }), Codec.INT.fieldOf("xCenter").forGetter((worldmap) -> {
            return worldmap.centerX;
        }), Codec.INT.fieldOf("zCenter").forGetter((worldmap) -> {
            return worldmap.centerZ;
        }), Codec.BYTE.optionalFieldOf("scale", (byte) 0).forGetter((worldmap) -> {
            return worldmap.scale;
        }), Codec.BYTE_BUFFER.fieldOf("colors").forGetter((worldmap) -> {
            return ByteBuffer.wrap(worldmap.colors);
        }), Codec.BOOL.optionalFieldOf("trackingPosition", true).forGetter((worldmap) -> {
            return worldmap.trackingPosition;
        }), Codec.BOOL.optionalFieldOf("unlimitedTracking", false).forGetter((worldmap) -> {
            return worldmap.unlimitedTracking;
        }), Codec.BOOL.optionalFieldOf("locked", false).forGetter((worldmap) -> {
            return worldmap.locked;
        }), MapIconBanner.CODEC.listOf().optionalFieldOf("banners", List.of()).forGetter((worldmap) -> {
            return List.copyOf(worldmap.bannerMarkers.values());
        }), WorldMapFrame.CODEC.listOf().optionalFieldOf("frames", List.of()).forGetter((worldmap) -> {
            return List.copyOf(worldmap.frameMarkers.values());
            // CraftBukkit start
        }), Codec.LONG.optionalFieldOf("UUIDLeast", 0L).forGetter((worldmap) -> {
            UUID uuid = worldmap.updateUUID();
            return (uuid != null) ? uuid.getLeastSignificantBits() : 0L;
        }), Codec.LONG.optionalFieldOf("UUIDMost", 0L).forGetter((worldmap) -> {
            UUID uuid = worldmap.updateUUID();
            return (uuid != null) ? uuid.getMostSignificantBits() : 0L;
            // CraftBukkit end
        })).apply(instance, WorldMap::new);
    });
    public int centerX;
    public int centerZ;
    public ResourceKey<World> dimension;
    public boolean trackingPosition;
    public boolean unlimitedTracking;
    public byte scale;
    public byte[] colors;
    public boolean locked;
    public final List<WorldMap.WorldMapHumanTracker> carriedBy;
    public final Map<EntityHuman, WorldMap.WorldMapHumanTracker> carriedByPlayers;
    private final Map<String, MapIconBanner> bannerMarkers;
    public final Map<String, MapIcon> decorations;
    private final Map<String, WorldMapFrame> frameMarkers;
    private int trackedDecorationCount;

    // CraftBukkit start
    public final CraftMapView mapView;
    public UUID uniqueId = null;
    public MapId id;

    private static ResourceKey<World> getWorldKey(ResourceKey<World> resourcekey, long uuidLeast, long uuidMost) {
        World lookup = MinecraftServer.getServer().getLevel(resourcekey);
        if (lookup != null) {
            return resourcekey;
        }

        if (uuidLeast != 0L && uuidMost != 0L) {
            UUID uniqueId = new UUID(uuidMost, uuidLeast);

            CraftWorld world = (CraftWorld) Bukkit.getWorld(uniqueId);
            // Check if the stored world details are correct.
            if (world == null) {
                /* All Maps which do not have their valid world loaded are set to a dimension which hopefully won't be reached.
                   This is to prevent them being corrupted with the wrong map data. */
                // PAIL: Use Vanilla exception handling for now
            } else {
                return world.getHandle().dimension();
            }
        }
        throw new IllegalArgumentException("Invalid map dimension: " + resourcekey);
    }

    @Nullable
    private UUID updateUUID() {
        if (this.uniqueId == null) {
            World world = MinecraftServer.getServer().getLevel(this.dimension);
            if (world != null) {
                this.uniqueId = world.getWorld().getUID();
            }
        }

        return this.uniqueId;
    }
    // CraftBukkit end

    public static SavedDataType<WorldMap> type(MapId mapid) {
        return new SavedDataType<WorldMap>(mapid.key(), () -> {
            throw new IllegalStateException("Should never create an empty map saved data");
        }, WorldMap.CODEC, DataFixTypes.SAVED_DATA_MAP_DATA);
    }

    private WorldMap(int i, int j, byte b0, boolean flag, boolean flag1, boolean flag2, ResourceKey<World> resourcekey) {
        this.colors = new byte[16384];
        this.carriedBy = Lists.newArrayList();
        this.carriedByPlayers = Maps.newHashMap();
        this.bannerMarkers = Maps.newHashMap();
        this.decorations = Maps.newLinkedHashMap();
        this.frameMarkers = Maps.newHashMap();
        this.scale = b0;
        this.centerX = i;
        this.centerZ = j;
        this.dimension = resourcekey;
        this.trackingPosition = flag;
        this.unlimitedTracking = flag1;
        this.locked = flag2;
        // CraftBukkit start
        updateUUID();
        mapView = new CraftMapView(this);
    }

    private WorldMap(ResourceKey<World> resourcekey, int i, int j, byte b0, ByteBuffer bytebuffer, boolean flag, boolean flag1, boolean flag2, List<MapIconBanner> list, List<WorldMapFrame> list1, long uuidLeast, long uuidMost) {
        this(i, j, (byte) MathHelper.clamp(b0, 0, 4), flag, flag1, flag2, getWorldKey(resourcekey, uuidLeast, uuidMost));
        // CraftBukkit end
        if (bytebuffer.array().length == 16384) {
            this.colors = bytebuffer.array();
        }

        for (MapIconBanner mapiconbanner : list) {
            this.bannerMarkers.put(mapiconbanner.getId(), mapiconbanner);
            this.addDecoration(mapiconbanner.getDecoration(), (GeneratorAccess) null, mapiconbanner.getId(), (double) mapiconbanner.pos().getX(), (double) mapiconbanner.pos().getZ(), 180.0D, (IChatBaseComponent) mapiconbanner.name().orElse(null)); // CraftBukkit - decompile error
        }

        for (WorldMapFrame worldmapframe : list1) {
            this.frameMarkers.put(worldmapframe.getId(), worldmapframe);
            this.addDecoration(MapDecorationTypes.FRAME, (GeneratorAccess) null, getFrameKey(worldmapframe.entityId()), (double) worldmapframe.pos().getX(), (double) worldmapframe.pos().getZ(), (double) worldmapframe.rotation(), (IChatBaseComponent) null);
        }

    }

    public static WorldMap createFresh(double d0, double d1, byte b0, boolean flag, boolean flag1, ResourceKey<World> resourcekey) {
        int i = 128 * (1 << b0);
        int j = MathHelper.floor((d0 + 64.0D) / (double) i);
        int k = MathHelper.floor((d1 + 64.0D) / (double) i);
        int l = j * i + i / 2 - 64;
        int i1 = k * i + i / 2 - 64;

        return new WorldMap(l, i1, b0, flag, flag1, false, resourcekey);
    }

    public static WorldMap createForClient(byte b0, boolean flag, ResourceKey<World> resourcekey) {
        return new WorldMap(0, 0, b0, false, false, flag, resourcekey);
    }

    public WorldMap locked() {
        WorldMap worldmap = new WorldMap(this.centerX, this.centerZ, this.scale, this.trackingPosition, this.unlimitedTracking, true, this.dimension);

        worldmap.bannerMarkers.putAll(this.bannerMarkers);
        worldmap.decorations.putAll(this.decorations);
        worldmap.trackedDecorationCount = this.trackedDecorationCount;
        System.arraycopy(this.colors, 0, worldmap.colors, 0, this.colors.length);
        return worldmap;
    }

    public WorldMap scaled() {
        return createFresh((double) this.centerX, (double) this.centerZ, (byte) MathHelper.clamp(this.scale + 1, 0, 4), this.trackingPosition, this.unlimitedTracking, this.dimension);
    }

    private static Predicate<ItemStack> mapMatcher(ItemStack itemstack) {
        MapId mapid = (MapId) itemstack.get(DataComponents.MAP_ID);

        return (itemstack1) -> {
            return itemstack1 == itemstack ? true : itemstack1.is(itemstack.getItem()) && Objects.equals(mapid, itemstack1.get(DataComponents.MAP_ID));
        };
    }

    public void tickCarriedBy(EntityHuman entityhuman, ItemStack itemstack) {
        if (!this.carriedByPlayers.containsKey(entityhuman)) {
            WorldMap.WorldMapHumanTracker worldmap_worldmaphumantracker = new WorldMap.WorldMapHumanTracker(entityhuman);

            this.carriedByPlayers.put(entityhuman, worldmap_worldmaphumantracker);
            this.carriedBy.add(worldmap_worldmaphumantracker);
        }

        Predicate<ItemStack> predicate = mapMatcher(itemstack);

        if (!entityhuman.getInventory().contains(predicate)) {
            this.removeDecoration(entityhuman.getName().getString());
        }

        for (int i = 0; i < this.carriedBy.size(); ++i) {
            WorldMap.WorldMapHumanTracker worldmap_worldmaphumantracker1 = (WorldMap.WorldMapHumanTracker) this.carriedBy.get(i);
            EntityHuman entityhuman1 = worldmap_worldmaphumantracker1.player;
            String s = entityhuman1.getName().getString();

            if (!entityhuman1.isRemoved() && (entityhuman1.getInventory().contains(predicate) || itemstack.isFramed())) {
                if (!itemstack.isFramed() && entityhuman1.level().dimension() == this.dimension && this.trackingPosition) {
                    this.addDecoration(MapDecorationTypes.PLAYER, entityhuman1.level(), s, entityhuman1.getX(), entityhuman1.getZ(), (double) entityhuman1.getYRot(), (IChatBaseComponent) null);
                }
            } else {
                this.carriedByPlayers.remove(entityhuman1);
                this.carriedBy.remove(worldmap_worldmaphumantracker1);
                this.removeDecoration(s);
            }

            if (!entityhuman1.equals(entityhuman) && hasMapInvisibilityItemEquipped(entityhuman1)) {
                this.removeDecoration(s);
            }
        }

        if (itemstack.isFramed() && this.trackingPosition) {
            EntityItemFrame entityitemframe = itemstack.getFrame();
            BlockPosition blockposition = entityitemframe.getPos();
            WorldMapFrame worldmapframe = (WorldMapFrame) this.frameMarkers.get(WorldMapFrame.frameId(blockposition));

            if (worldmapframe != null && entityitemframe.getId() != worldmapframe.entityId() && this.frameMarkers.containsKey(worldmapframe.getId())) {
                this.removeDecoration(getFrameKey(worldmapframe.entityId()));
            }

            WorldMapFrame worldmapframe1 = new WorldMapFrame(blockposition, entityitemframe.getDirection().get2DDataValue() * 90, entityitemframe.getId());

            this.addDecoration(MapDecorationTypes.FRAME, entityhuman.level(), getFrameKey(entityitemframe.getId()), (double) blockposition.getX(), (double) blockposition.getZ(), (double) (entityitemframe.getDirection().get2DDataValue() * 90), (IChatBaseComponent) null);
            WorldMapFrame worldmapframe2 = (WorldMapFrame) this.frameMarkers.put(worldmapframe1.getId(), worldmapframe1);

            if (!worldmapframe1.equals(worldmapframe2)) {
                this.setDirty();
            }
        }

        MapDecorations mapdecorations = (MapDecorations) itemstack.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);

        if (!this.decorations.keySet().containsAll(mapdecorations.decorations().keySet())) {
            mapdecorations.decorations().forEach((s1, mapdecorations_a) -> {
                if (!this.decorations.containsKey(s1)) {
                    this.addDecoration(mapdecorations_a.type(), entityhuman.level(), s1, mapdecorations_a.x(), mapdecorations_a.z(), (double) mapdecorations_a.rotation(), (IChatBaseComponent) null);
                }

            });
        }

    }

    private static boolean hasMapInvisibilityItemEquipped(EntityHuman entityhuman) {
        for (EnumItemSlot enumitemslot : EnumItemSlot.values()) {
            if (enumitemslot != EnumItemSlot.MAINHAND && enumitemslot != EnumItemSlot.OFFHAND && entityhuman.getItemBySlot(enumitemslot).is(TagsItem.MAP_INVISIBILITY_EQUIPMENT)) {
                return true;
            }
        }

        return false;
    }

    private void removeDecoration(String s) {
        MapIcon mapicon = (MapIcon) this.decorations.remove(s);

        if (mapicon != null && ((MapDecorationType) mapicon.type().value()).trackCount()) {
            --this.trackedDecorationCount;
        }

        this.setDecorationsDirty();
    }

    public static void addTargetDecoration(ItemStack itemstack, BlockPosition blockposition, String s, Holder<MapDecorationType> holder) {
        MapDecorations.a mapdecorations_a = new MapDecorations.a(holder, (double) blockposition.getX(), (double) blockposition.getZ(), 180.0F);

        itemstack.update(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY, (mapdecorations) -> {
            return mapdecorations.withDecoration(s, mapdecorations_a);
        });
        if (((MapDecorationType) holder.value()).hasMapColor()) {
            itemstack.set(DataComponents.MAP_COLOR, new MapItemColor(((MapDecorationType) holder.value()).mapColor()));
        }

    }

    private void addDecoration(Holder<MapDecorationType> holder, @Nullable GeneratorAccess generatoraccess, String s, double d0, double d1, double d2, @Nullable IChatBaseComponent ichatbasecomponent) {
        int i = 1 << this.scale;
        float f = (float) (d0 - (double) this.centerX) / (float) i;
        float f1 = (float) (d1 - (double) this.centerZ) / (float) i;
        WorldMap.b worldmap_b = this.calculateDecorationLocationAndType(holder, generatoraccess, d2, f, f1);

        if (worldmap_b == null) {
            this.removeDecoration(s);
        } else {
            MapIcon mapicon = new MapIcon(worldmap_b.type(), worldmap_b.x(), worldmap_b.y(), worldmap_b.rot(), Optional.ofNullable(ichatbasecomponent));
            MapIcon mapicon1 = (MapIcon) this.decorations.put(s, mapicon);

            if (!mapicon.equals(mapicon1)) {
                if (mapicon1 != null && ((MapDecorationType) mapicon1.type().value()).trackCount()) {
                    --this.trackedDecorationCount;
                }

                if (((MapDecorationType) worldmap_b.type().value()).trackCount()) {
                    ++this.trackedDecorationCount;
                }

                this.setDecorationsDirty();
            }

        }
    }

    @Nullable
    private WorldMap.b calculateDecorationLocationAndType(Holder<MapDecorationType> holder, @Nullable GeneratorAccess generatoraccess, double d0, float f, float f1) {
        byte b0 = clampMapCoordinate(f);
        byte b1 = clampMapCoordinate(f1);

        if (holder.is(MapDecorationTypes.PLAYER)) {
            Pair<Holder<MapDecorationType>, Byte> pair = this.playerDecorationTypeAndRotation(holder, generatoraccess, d0, f, f1);

            return pair == null ? null : new WorldMap.b((Holder) pair.getFirst(), b0, b1, (Byte) pair.getSecond());
        } else {
            return !isInsideMap(f, f1) && !this.unlimitedTracking ? null : new WorldMap.b(holder, b0, b1, this.calculateRotation(generatoraccess, d0));
        }
    }

    @Nullable
    private Pair<Holder<MapDecorationType>, Byte> playerDecorationTypeAndRotation(Holder<MapDecorationType> holder, @Nullable GeneratorAccess generatoraccess, double d0, float f, float f1) {
        if (isInsideMap(f, f1)) {
            return Pair.of(holder, this.calculateRotation(generatoraccess, d0));
        } else {
            Holder<MapDecorationType> holder1 = this.decorationTypeForPlayerOutsideMap(f, f1);

            return holder1 == null ? null : Pair.of(holder1, (byte) 0);
        }
    }

    private byte calculateRotation(@Nullable GeneratorAccess generatoraccess, double d0) {
        if (this.dimension == World.NETHER && generatoraccess != null) {
            int i = (int) (generatoraccess.getLevelData().getDayTime() / 10L);

            return (byte) (i * i * 34187121 + i * 121 >> 15 & 15);
        } else {
            double d1 = d0 < 0.0D ? d0 - 8.0D : d0 + 8.0D;

            return (byte) ((int) (d1 * 16.0D / 360.0D));
        }
    }

    private static boolean isInsideMap(float f, float f1) {
        int i = 63;

        return f >= -63.0F && f1 >= -63.0F && f <= 63.0F && f1 <= 63.0F;
    }

    @Nullable
    private Holder<MapDecorationType> decorationTypeForPlayerOutsideMap(float f, float f1) {
        int i = 320;
        boolean flag = Math.abs(f) < 320.0F && Math.abs(f1) < 320.0F;

        return flag ? MapDecorationTypes.PLAYER_OFF_MAP : (this.unlimitedTracking ? MapDecorationTypes.PLAYER_OFF_LIMITS : null);
    }

    private static byte clampMapCoordinate(float f) {
        int i = 63;

        return f <= -63.0F ? Byte.MIN_VALUE : (f >= 63.0F ? 127 : (byte) ((int) ((double) (f * 2.0F) + 0.5D)));
    }

    @Nullable
    public Packet<?> getUpdatePacket(MapId mapid, EntityHuman entityhuman) {
        WorldMap.WorldMapHumanTracker worldmap_worldmaphumantracker = (WorldMap.WorldMapHumanTracker) this.carriedByPlayers.get(entityhuman);

        return worldmap_worldmaphumantracker == null ? null : worldmap_worldmaphumantracker.nextUpdatePacket(mapid);
    }

    public void setColorsDirty(int i, int j) {
        this.setDirty();

        for (WorldMap.WorldMapHumanTracker worldmap_worldmaphumantracker : this.carriedBy) {
            worldmap_worldmaphumantracker.markColorsDirty(i, j);
        }

    }

    public void setDecorationsDirty() {
        this.carriedBy.forEach(WorldMap.WorldMapHumanTracker::markDecorationsDirty);
    }

    public WorldMap.WorldMapHumanTracker getHoldingPlayer(EntityHuman entityhuman) {
        WorldMap.WorldMapHumanTracker worldmap_worldmaphumantracker = (WorldMap.WorldMapHumanTracker) this.carriedByPlayers.get(entityhuman);

        if (worldmap_worldmaphumantracker == null) {
            worldmap_worldmaphumantracker = new WorldMap.WorldMapHumanTracker(entityhuman);
            this.carriedByPlayers.put(entityhuman, worldmap_worldmaphumantracker);
            this.carriedBy.add(worldmap_worldmaphumantracker);
        }

        return worldmap_worldmaphumantracker;
    }

    public boolean toggleBanner(GeneratorAccess generatoraccess, BlockPosition blockposition) {
        double d0 = (double) blockposition.getX() + 0.5D;
        double d1 = (double) blockposition.getZ() + 0.5D;
        int i = 1 << this.scale;
        double d2 = (d0 - (double) this.centerX) / (double) i;
        double d3 = (d1 - (double) this.centerZ) / (double) i;
        int j = 63;

        if (d2 >= -63.0D && d3 >= -63.0D && d2 <= 63.0D && d3 <= 63.0D) {
            MapIconBanner mapiconbanner = MapIconBanner.fromWorld(generatoraccess, blockposition);

            if (mapiconbanner == null) {
                return false;
            }

            if (this.bannerMarkers.remove(mapiconbanner.getId(), mapiconbanner)) {
                this.removeDecoration(mapiconbanner.getId());
                this.setDirty();
                return true;
            }

            if (!this.isTrackedCountOverLimit(256)) {
                this.bannerMarkers.put(mapiconbanner.getId(), mapiconbanner);
                this.addDecoration(mapiconbanner.getDecoration(), generatoraccess, mapiconbanner.getId(), d0, d1, 180.0D, (IChatBaseComponent) mapiconbanner.name().orElse(null)); // CraftBukkit - decompile error
                this.setDirty();
                return true;
            }
        }

        return false;
    }

    public void checkBanners(IBlockAccess iblockaccess, int i, int j) {
        Iterator<MapIconBanner> iterator = this.bannerMarkers.values().iterator();

        while (iterator.hasNext()) {
            MapIconBanner mapiconbanner = (MapIconBanner) iterator.next();

            if (mapiconbanner.pos().getX() == i && mapiconbanner.pos().getZ() == j) {
                MapIconBanner mapiconbanner1 = MapIconBanner.fromWorld(iblockaccess, mapiconbanner.pos());

                if (!mapiconbanner.equals(mapiconbanner1)) {
                    iterator.remove();
                    this.removeDecoration(mapiconbanner.getId());
                    this.setDirty();
                }
            }
        }

    }

    public Collection<MapIconBanner> getBanners() {
        return this.bannerMarkers.values();
    }

    public void removedFromFrame(BlockPosition blockposition, int i) {
        this.removeDecoration(getFrameKey(i));
        this.frameMarkers.remove(WorldMapFrame.frameId(blockposition));
        this.setDirty();
    }

    public boolean updateColor(int i, int j, byte b0) {
        byte b1 = this.colors[i + j * 128];

        if (b1 != b0) {
            this.setColor(i, j, b0);
            return true;
        } else {
            return false;
        }
    }

    public void setColor(int i, int j, byte b0) {
        this.colors[i + j * 128] = b0;
        this.setColorsDirty(i, j);
    }

    public boolean isExplorationMap() {
        for (MapIcon mapicon : this.decorations.values()) {
            if (((MapDecorationType) mapicon.type().value()).explorationMapElement()) {
                return true;
            }
        }

        return false;
    }

    public void addClientSideDecorations(List<MapIcon> list) {
        this.decorations.clear();
        this.trackedDecorationCount = 0;

        for (int i = 0; i < list.size(); ++i) {
            MapIcon mapicon = (MapIcon) list.get(i);

            this.decorations.put("icon-" + i, mapicon);
            if (((MapDecorationType) mapicon.type().value()).trackCount()) {
                ++this.trackedDecorationCount;
            }
        }

    }

    public Iterable<MapIcon> getDecorations() {
        return this.decorations.values();
    }

    public boolean isTrackedCountOverLimit(int i) {
        return this.trackedDecorationCount >= i;
    }

    private static String getFrameKey(int i) {
        return "frame-" + i;
    }

    public static record c(int startX, int startY, int width, int height, byte[] mapColors) {

        public static final StreamCodec<ByteBuf, Optional<WorldMap.c>> STREAM_CODEC = StreamCodec.<ByteBuf, Optional<WorldMap.c>>of(WorldMap.c::write, WorldMap.c::read);

        private static void write(ByteBuf bytebuf, Optional<WorldMap.c> optional) {
            if (optional.isPresent()) {
                WorldMap.c worldmap_c = (WorldMap.c) optional.get();

                bytebuf.writeByte(worldmap_c.width);
                bytebuf.writeByte(worldmap_c.height);
                bytebuf.writeByte(worldmap_c.startX);
                bytebuf.writeByte(worldmap_c.startY);
                PacketDataSerializer.writeByteArray(bytebuf, worldmap_c.mapColors);
            } else {
                bytebuf.writeByte(0);
            }

        }

        private static Optional<WorldMap.c> read(ByteBuf bytebuf) {
            int i = bytebuf.readUnsignedByte();

            if (i > 0) {
                int j = bytebuf.readUnsignedByte();
                int k = bytebuf.readUnsignedByte();
                int l = bytebuf.readUnsignedByte();
                byte[] abyte = PacketDataSerializer.readByteArray(bytebuf);

                return Optional.of(new WorldMap.c(k, l, i, j, abyte));
            } else {
                return Optional.empty();
            }
        }

        public void applyToMap(WorldMap worldmap) {
            for (int i = 0; i < this.width; ++i) {
                for (int j = 0; j < this.height; ++j) {
                    worldmap.setColor(this.startX + i, this.startY + j, this.mapColors[i + j * this.width]);
                }
            }

        }
    }

    public class WorldMapHumanTracker {

        public final EntityHuman player;
        private boolean dirtyData = true;
        private int minDirtyX;
        private int minDirtyY;
        private int maxDirtyX = 127;
        private int maxDirtyY = 127;
        private boolean dirtyDecorations = true;
        private int tick;
        public int step;

        WorldMapHumanTracker(final EntityHuman entityhuman) {
            this.player = entityhuman;
        }

        private WorldMap.c createPatch(byte[] buffer) { // CraftBukkit
            int i = this.minDirtyX;
            int j = this.minDirtyY;
            int k = this.maxDirtyX + 1 - this.minDirtyX;
            int l = this.maxDirtyY + 1 - this.minDirtyY;
            byte[] abyte = new byte[k * l];

            for (int i1 = 0; i1 < k; ++i1) {
                for (int j1 = 0; j1 < l; ++j1) {
                    abyte[i1 + j1 * k] = buffer[i + i1 + (j + j1) * 128]; // CraftBukkit
                }
            }

            return new WorldMap.c(i, j, k, l, abyte);
        }

        @Nullable
        Packet<?> nextUpdatePacket(MapId mapid) {
            WorldMap.c worldmap_c;
            org.bukkit.craftbukkit.map.RenderData render = WorldMap.this.mapView.render((org.bukkit.craftbukkit.entity.CraftPlayer) this.player.getBukkitEntity()); // CraftBukkit

            if (this.dirtyData) {
                this.dirtyData = false;
                worldmap_c = this.createPatch(render.buffer); // CraftBukkit
            } else {
                worldmap_c = null;
            }

            Collection<MapIcon> collection;

            if ((true || this.dirtyDecorations) && this.tick++ % 5 == 0) { // CraftBukkit - custom maps don't update this yet
                this.dirtyDecorations = false;
                // CraftBukkit start
                java.util.Collection<MapIcon> icons = new java.util.ArrayList<MapIcon>();

                for (org.bukkit.map.MapCursor cursor : render.cursors) {
                    if (cursor.isVisible()) {
                        icons.add(new MapIcon(CraftMapCursor.CraftType.bukkitToMinecraftHolder(cursor.getType()), cursor.getX(), cursor.getY(), cursor.getDirection(), CraftChatMessage.fromStringOrOptional(cursor.getCaption())));
                    }
                }
                collection = icons;
                // CraftBukkit end
            } else {
                collection = null;
            }

            return collection == null && worldmap_c == null ? null : new PacketPlayOutMap(mapid, WorldMap.this.scale, WorldMap.this.locked, collection, worldmap_c);
        }

        void markColorsDirty(int i, int j) {
            if (this.dirtyData) {
                this.minDirtyX = Math.min(this.minDirtyX, i);
                this.minDirtyY = Math.min(this.minDirtyY, j);
                this.maxDirtyX = Math.max(this.maxDirtyX, i);
                this.maxDirtyY = Math.max(this.maxDirtyY, j);
            } else {
                this.dirtyData = true;
                this.minDirtyX = i;
                this.minDirtyY = j;
                this.maxDirtyX = i;
                this.maxDirtyY = j;
            }

        }

        private void markDecorationsDirty() {
            this.dirtyDecorations = true;
        }
    }

    private static record b(Holder<MapDecorationType> type, byte x, byte y, byte rot) {

    }
}

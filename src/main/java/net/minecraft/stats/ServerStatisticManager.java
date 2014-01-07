// mc-dev import
package net.minecraft.stats;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.SystemUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.GameProfileSerializer;
import net.minecraft.network.protocol.game.PacketPlayOutStatistic;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.EntityHuman;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class ServerStatisticManager extends StatisticManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Map<Statistic<?>, Integer>> STATS_CODEC = Codec.dispatchedMap(BuiltInRegistries.STAT_TYPE.byNameCodec(), SystemUtils.memoize(ServerStatisticManager::createTypedStatsCodec)).xmap((map) -> {
        Map<Statistic<?>, Integer> map1 = new HashMap();

        map.forEach((statisticwrapper, map2) -> {
            map1.putAll(map2);
        });
        return map1;
    }, (map) -> {
        return (Map) map.entrySet().stream().collect(Collectors.groupingBy((entry) -> {
            return ((Statistic) entry.getKey()).getType();
        }, SystemUtils.toMap()));
    });
    private final MinecraftServer server;
    private final File file;
    private final Set<Statistic<?>> dirty = Sets.newHashSet();

    private static <T> Codec<Map<Statistic<T>, Integer>> createTypedStatsCodec(StatisticWrapper<T> statisticwrapper) { // CraftBukkit - decompile error
        Codec<T> codec = statisticwrapper.getRegistry().byNameCodec();

        Objects.requireNonNull(statisticwrapper);
        Codec<Statistic<T>> codec1 = codec.flatComapMap(statisticwrapper::get, (statistic) -> { // CraftBukkit - decompile error
            return statistic.getType() == statisticwrapper ? DataResult.success(statistic.getValue()) : DataResult.error(() -> {
                String s = String.valueOf(statisticwrapper);

                return "Expected type " + s + ", but got " + String.valueOf(statistic.getType());
            });
        });

        return Codec.unboundedMap(codec1, Codec.INT);
    }

    public ServerStatisticManager(MinecraftServer minecraftserver, File file) {
        this.server = minecraftserver;
        this.file = file;
        // Spigot start
        for ( Map.Entry<net.minecraft.resources.MinecraftKey, Integer> entry : org.spigotmc.SpigotConfig.forcedStats.entrySet() )
        {
            Statistic<net.minecraft.resources.MinecraftKey> wrapper = StatisticList.CUSTOM.get( entry.getKey() );
            this.stats.put( wrapper, entry.getValue().intValue() );
        }
        // Spigot end
        if (file.isFile()) {
            try {
                this.parseLocal(minecraftserver.getFixerUpper(), FileUtils.readFileToString(file));
            } catch (IOException ioexception) {
                ServerStatisticManager.LOGGER.error("Couldn't read statistics file {}", file, ioexception);
            } catch (JsonParseException jsonparseexception) {
                ServerStatisticManager.LOGGER.error("Couldn't parse statistics file {}", file, jsonparseexception);
            }
        }

    }

    public void save() {
        if ( org.spigotmc.SpigotConfig.disableStatSaving ) return; // Spigot
        try {
            FileUtils.writeStringToFile(this.file, this.toJson());
        } catch (IOException ioexception) {
            ServerStatisticManager.LOGGER.error("Couldn't save stats", ioexception);
        }

    }

    @Override
    public void setValue(EntityHuman entityhuman, Statistic<?> statistic, int i) {
        if ( org.spigotmc.SpigotConfig.disableStatSaving ) return; // Spigot
        super.setValue(entityhuman, statistic, i);
        this.dirty.add(statistic);
    }

    private Set<Statistic<?>> getDirty() {
        Set<Statistic<?>> set = Sets.newHashSet(this.dirty);

        this.dirty.clear();
        return set;
    }

    public void parseLocal(DataFixer datafixer, String s) {
        try {
            try (JsonReader jsonreader = new JsonReader(new StringReader(s))) {
                jsonreader.setLenient(false);
                JsonElement jsonelement = Streams.parse(jsonreader);

                if (!jsonelement.isJsonNull()) {
                    Dynamic<JsonElement> dynamic = new Dynamic(JsonOps.INSTANCE, jsonelement);

                    dynamic = DataFixTypes.STATS.updateToCurrentVersion(datafixer, dynamic, GameProfileSerializer.getDataVersion(dynamic, 1343));
                    this.stats.putAll((Map) ServerStatisticManager.STATS_CODEC.parse(dynamic.get("stats").orElseEmptyMap()).resultOrPartial((s1) -> {
                        ServerStatisticManager.LOGGER.error("Failed to parse statistics for {}: {}", this.file, s1);
                    }).orElse(Map.of()));
                    return;
                }

                ServerStatisticManager.LOGGER.error("Unable to parse Stat data from {}", this.file);
            }

        } catch (IOException | JsonParseException jsonparseexception) {
            ServerStatisticManager.LOGGER.error("Unable to parse Stat data from {}", this.file, jsonparseexception);
        }
    }

    protected String toJson() {
        JsonObject jsonobject = new JsonObject();

        jsonobject.add("stats", (JsonElement) ServerStatisticManager.STATS_CODEC.encodeStart(JsonOps.INSTANCE, this.stats).getOrThrow());
        jsonobject.addProperty("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return jsonobject.toString();
    }

    public void markAllDirty() {
        this.dirty.addAll(this.stats.keySet());
    }

    public void sendStats(EntityPlayer entityplayer) {
        Object2IntMap<Statistic<?>> object2intmap = new Object2IntOpenHashMap();

        for (Statistic<?> statistic : this.getDirty()) {
            object2intmap.put(statistic, this.getValue(statistic));
        }

        entityplayer.connection.send(new PacketPlayOutStatistic(object2intmap));
    }
}

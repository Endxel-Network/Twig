package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.SystemUtils;
import net.minecraft.util.ChatDeserializer;
import org.slf4j.Logger;

public abstract class JsonList<K, V extends JsonListEntry<K>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private final File file;
    private final Map<String, V> map = Maps.newHashMap();

    public JsonList(File file) {
        this.file = file;
    }

    public File getFile() {
        return this.file;
    }

    public void add(V v0) {
        this.map.put(this.getKeyForUser((v0).getUser()), v0); // CraftBukkit - decompile error

        try {
            this.save();
        } catch (IOException ioexception) {
            JsonList.LOGGER.warn("Could not save the list after adding a user.", ioexception);
        }

    }

    @Nullable
    public V get(K k0) {
        this.removeExpired();
        return (V) (this.map.get(this.getKeyForUser(k0)));
    }

    public void remove(K k0) {
        this.map.remove(this.getKeyForUser(k0));

        try {
            this.save();
        } catch (IOException ioexception) {
            JsonList.LOGGER.warn("Could not save the list after removing a user.", ioexception);
        }

    }

    public void remove(JsonListEntry<K> jsonlistentry) {
        this.remove(jsonlistentry.getUser());
    }

    public String[] getUserList() {
        return (String[]) this.map.keySet().toArray(new String[0]);
    }

    public boolean isEmpty() {
        return this.map.size() < 1;
    }

    protected String getKeyForUser(K k0) {
        return k0.toString();
    }

    protected boolean contains(K k0) {
        this.removeExpired(); // CraftBukkit - SPIGOT-7589: Consistently remove expired entries to mirror .get(...)
        return this.map.containsKey(this.getKeyForUser(k0));
    }

    private void removeExpired() {
        List<K> list = Lists.newArrayList();

        for (V v0 : this.map.values()) {
            if (v0.hasExpired()) {
                list.add((v0).getUser()); // CraftBukkit - decompile error
            }
        }

        for (K k0 : list) {
            this.map.remove(this.getKeyForUser(k0));
        }

    }

    protected abstract JsonListEntry<K> createEntry(JsonObject jsonobject);

    public Collection<V> getEntries() {
        return this.map.values();
    }

    public void save() throws IOException {
        JsonArray jsonarray = new JsonArray();
        Stream<JsonObject> stream = this.map.values().stream().map((jsonlistentry) -> { // CraftBukkit - decompile error
            JsonObject jsonobject = new JsonObject();

            Objects.requireNonNull(jsonlistentry);
            return (JsonObject) SystemUtils.make(jsonobject, jsonlistentry::serialize);
        });

        Objects.requireNonNull(jsonarray);
        stream.forEach(jsonarray::add);

        try (BufferedWriter bufferedwriter = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
            JsonList.GSON.toJson(jsonarray, JsonList.GSON.newJsonWriter(bufferedwriter));
        }

    }

    public void load() throws IOException {
        if (this.file.exists()) {
            try (BufferedReader bufferedreader = Files.newReader(this.file, StandardCharsets.UTF_8)) {
                this.map.clear();
                JsonArray jsonarray = (JsonArray) JsonList.GSON.fromJson(bufferedreader, JsonArray.class);

                if (jsonarray == null) {
                    return;
                }

                for (JsonElement jsonelement : jsonarray) {
                    JsonObject jsonobject = ChatDeserializer.convertToJsonObject(jsonelement, "entry");
                    JsonListEntry<K> jsonlistentry = this.createEntry(jsonobject);

                    if (jsonlistentry.getUser() != null) {
                        this.map.put(this.getKeyForUser(jsonlistentry.getUser()), (V) jsonlistentry); // CraftBukkit - decompile error
                    }
                }
            // Spigot Start
            } catch ( com.google.gson.JsonParseException | NullPointerException ex )
            {
                org.bukkit.Bukkit.getLogger().log( java.util.logging.Level.WARNING, "Unable to read file " + this.file + ", backing it up to {0}.backup and creating new copy.", ex );
                File backup = new File( this.file + ".backup" );
                this.file.renameTo( backup );
                this.file.delete();
                // Spigot End
            }

        }
    }
}

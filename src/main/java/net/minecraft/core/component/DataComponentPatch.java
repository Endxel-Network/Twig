package net.minecraft.core.component;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.util.Unit;

public final class DataComponentPatch {

    public static final DataComponentPatch EMPTY = new DataComponentPatch(Reference2ObjectMaps.emptyMap());
    public static final Codec<DataComponentPatch> CODEC = Codec.dispatchedMap(DataComponentPatch.c.CODEC, DataComponentPatch.c::valueCodec).xmap((map) -> {
        if (map.isEmpty()) {
            return DataComponentPatch.EMPTY;
        } else {
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap(map.size());

            for (Map.Entry<DataComponentPatch.c, ?> map_entry : map.entrySet()) {
                DataComponentPatch.c datacomponentpatch_c = (DataComponentPatch.c) map_entry.getKey();

                if (datacomponentpatch_c.removed()) {
                    reference2objectmap.put(datacomponentpatch_c.type(), Optional.empty());
                } else {
                    reference2objectmap.put(datacomponentpatch_c.type(), Optional.of(map_entry.getValue()));
                }
            }

            return new DataComponentPatch(reference2objectmap);
        }
    }, (datacomponentpatch) -> {
        Reference2ObjectMap<DataComponentPatch.c, Object> reference2objectmap = new Reference2ObjectArrayMap(datacomponentpatch.map.size());
        ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(datacomponentpatch.map).iterator();

        while (objectiterator.hasNext()) {
            Map.Entry<DataComponentType<?>, Optional<?>> map_entry = (Entry) objectiterator.next();
            DataComponentType<?> datacomponenttype = (DataComponentType) map_entry.getKey();

            if (!datacomponenttype.isTransient()) {
                Optional<?> optional = (Optional) map_entry.getValue();

                if (optional.isPresent()) {
                    reference2objectmap.put(new DataComponentPatch.c(datacomponenttype, false), optional.get());
                } else {
                    reference2objectmap.put(new DataComponentPatch.c(datacomponenttype, true), Unit.INSTANCE);
                }
            }
        }

        return (Reference2ObjectMap) reference2objectmap; // CraftBukkit - decompile error
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> STREAM_CODEC = createStreamCodec(new DataComponentPatch.b() {
        @Override
        public <T> StreamCodec<RegistryFriendlyByteBuf, T> apply(DataComponentType<T> datacomponenttype) {
            return datacomponenttype.streamCodec().cast();
        }
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> DELIMITED_STREAM_CODEC = createStreamCodec(new DataComponentPatch.b() {
        @Override
        public <T> StreamCodec<RegistryFriendlyByteBuf, T> apply(DataComponentType<T> datacomponenttype) {
            StreamCodec<RegistryFriendlyByteBuf, T> streamcodec = datacomponenttype.streamCodec().cast();

            return streamcodec.apply(ByteBufCodecs.lengthPrefixed(Integer.MAX_VALUE));
        }
    });
    private static final String REMOVED_PREFIX = "!";
    final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map;

    private static StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> createStreamCodec(final DataComponentPatch.b datacomponentpatch_b) {
        return new StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch>() {
            public DataComponentPatch decode(RegistryFriendlyByteBuf registryfriendlybytebuf) {
                int i = registryfriendlybytebuf.readVarInt();
                int j = registryfriendlybytebuf.readVarInt();

                if (i == 0 && j == 0) {
                    return DataComponentPatch.EMPTY;
                } else {
                    int k = i + j;
                    Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap(Math.min(k, 65536));

                    for (int l = 0; l < i; ++l) {
                        DataComponentType<?> datacomponenttype = (DataComponentType) DataComponentType.STREAM_CODEC.decode(registryfriendlybytebuf);
                        Object object = datacomponentpatch_b.apply(datacomponenttype).decode(registryfriendlybytebuf);

                        reference2objectmap.put(datacomponenttype, Optional.of(object));
                    }

                    for (int i1 = 0; i1 < j; ++i1) {
                        DataComponentType<?> datacomponenttype1 = (DataComponentType) DataComponentType.STREAM_CODEC.decode(registryfriendlybytebuf);

                        reference2objectmap.put(datacomponenttype1, Optional.empty());
                    }

                    return new DataComponentPatch(reference2objectmap);
                }
            }

            public void encode(RegistryFriendlyByteBuf registryfriendlybytebuf, DataComponentPatch datacomponentpatch) {
                if (datacomponentpatch.isEmpty()) {
                    registryfriendlybytebuf.writeVarInt(0);
                    registryfriendlybytebuf.writeVarInt(0);
                } else {
                    int i = 0;
                    int j = 0;
                    ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(datacomponentpatch.map).iterator();

                    while (objectiterator.hasNext()) {
                        Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> reference2objectmap_entry = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();

                        if (((Optional) reference2objectmap_entry.getValue()).isPresent()) {
                            ++i;
                        } else {
                            ++j;
                        }
                    }

                    registryfriendlybytebuf.writeVarInt(i);
                    registryfriendlybytebuf.writeVarInt(j);
                    objectiterator = Reference2ObjectMaps.fastIterable(datacomponentpatch.map).iterator();

                    while (objectiterator.hasNext()) {
                        Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> reference2objectmap_entry1 = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();
                        Optional<?> optional = (Optional) reference2objectmap_entry1.getValue();

                        if (optional.isPresent()) {
                            DataComponentType<?> datacomponenttype = (DataComponentType) reference2objectmap_entry1.getKey();

                            DataComponentType.STREAM_CODEC.encode(registryfriendlybytebuf, datacomponenttype);
                            this.encodeComponent(registryfriendlybytebuf, datacomponenttype, optional.get());
                        }
                    }

                    objectiterator = Reference2ObjectMaps.fastIterable(datacomponentpatch.map).iterator();

                    while (objectiterator.hasNext()) {
                        Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> reference2objectmap_entry2 = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();

                        if (((Optional) reference2objectmap_entry2.getValue()).isEmpty()) {
                            DataComponentType<?> datacomponenttype1 = (DataComponentType) reference2objectmap_entry2.getKey();

                            DataComponentType.STREAM_CODEC.encode(registryfriendlybytebuf, datacomponenttype1);
                        }
                    }

                }
            }

            private <T> void encodeComponent(RegistryFriendlyByteBuf registryfriendlybytebuf, DataComponentType<T> datacomponenttype, Object object) {
                datacomponentpatch_b.apply(datacomponenttype).encode(registryfriendlybytebuf, (T) object); // CraftBukkit - decompile error
            }
        };
    }

    DataComponentPatch(Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap) {
        this.map = reference2objectmap;
    }

    public static DataComponentPatch.a builder() {
        return new DataComponentPatch.a();
    }

    @Nullable
    public <T> Optional<? extends T> get(DataComponentType<? extends T> datacomponenttype) {
        return (Optional) this.map.get(datacomponenttype);
    }

    public Set<Map.Entry<DataComponentType<?>, Optional<?>>> entrySet() {
        return this.map.entrySet();
    }

    public int size() {
        return this.map.size();
    }

    public DataComponentPatch forget(Predicate<DataComponentType<?>> predicate) {
        if (this.isEmpty()) {
            return DataComponentPatch.EMPTY;
        } else {
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap(this.map);

            reference2objectmap.keySet().removeIf(predicate);
            return reference2objectmap.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(reference2objectmap);
        }
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    public DataComponentPatch.d split() {
        if (this.isEmpty()) {
            return DataComponentPatch.d.EMPTY;
        } else {
            DataComponentMap.a datacomponentmap_a = DataComponentMap.builder();
            Set<DataComponentType<?>> set = Sets.newIdentityHashSet();

            this.map.forEach((datacomponenttype, optional) -> {
                if (optional.isPresent()) {
                    datacomponentmap_a.setUnchecked(datacomponenttype, optional.get());
                } else {
                    set.add(datacomponenttype);
                }

            });
            return new DataComponentPatch.d(datacomponentmap_a.build(), set);
        }
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            boolean flag;

            if (object instanceof DataComponentPatch) {
                DataComponentPatch datacomponentpatch = (DataComponentPatch) object;

                if (this.map.equals(datacomponentpatch.map)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.map.hashCode();
    }

    public String toString() {
        return toString(this.map);
    }

    static String toString(Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap) {
        StringBuilder stringbuilder = new StringBuilder();

        stringbuilder.append('{');
        boolean flag = true;
        ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(reference2objectmap).iterator();

        while (objectiterator.hasNext()) {
            Map.Entry<DataComponentType<?>, Optional<?>> map_entry = (Entry) objectiterator.next();

            if (flag) {
                flag = false;
            } else {
                stringbuilder.append(", ");
            }

            Optional<?> optional = (Optional) map_entry.getValue();

            if (optional.isPresent()) {
                stringbuilder.append(map_entry.getKey());
                stringbuilder.append("=>");
                stringbuilder.append(optional.get());
            } else {
                stringbuilder.append("!");
                stringbuilder.append(map_entry.getKey());
            }
        }

        stringbuilder.append('}');
        return stringbuilder.toString();
    }

    public static record d(DataComponentMap added, Set<DataComponentType<?>> removed) {

        public static final DataComponentPatch.d EMPTY = new DataComponentPatch.d(DataComponentMap.EMPTY, Set.of());
    }

    private static record c(DataComponentType<?> type, boolean removed) {

        public static final Codec<DataComponentPatch.c> CODEC = Codec.STRING.flatXmap((s) -> {
            boolean flag = s.startsWith("!");

            if (flag) {
                s = s.substring("!".length());
            }

            MinecraftKey minecraftkey = MinecraftKey.tryParse(s);
            DataComponentType<?> datacomponenttype = (DataComponentType) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(minecraftkey);

            return datacomponenttype == null ? DataResult.error(() -> {
                return "No component with type: '" + String.valueOf(minecraftkey) + "'";
            }) : (datacomponenttype.isTransient() ? DataResult.error(() -> {
                return "'" + String.valueOf(minecraftkey) + "' is not a persistent component";
            }) : DataResult.success(new DataComponentPatch.c(datacomponenttype, flag)));
        }, (datacomponentpatch_c) -> {
            DataComponentType<?> datacomponenttype = datacomponentpatch_c.type();
            MinecraftKey minecraftkey = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(datacomponenttype);

            return minecraftkey == null ? DataResult.error(() -> {
                return "Unregistered component: " + String.valueOf(datacomponenttype);
            }) : DataResult.success(datacomponentpatch_c.removed() ? "!" + String.valueOf(minecraftkey) : minecraftkey.toString());
        });

        public Codec<?> valueCodec() {
            return this.removed ? Codec.EMPTY.codec() : this.type.codecOrThrow();
        }
    }

    public static class a {

        private final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map = new Reference2ObjectArrayMap();

        a() {}

        // CraftBukkit start
        public void copy(DataComponentPatch orig) {
            this.map.putAll(orig.map);
        }

        public void clear(DataComponentType<?> type) {
            this.map.remove(type);
        }

        public boolean isSet(DataComponentType<?> type) {
            return map.containsKey(type);
        }

        public boolean isEmpty() {
            return this.map.isEmpty();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (object instanceof DataComponentPatch.a patch) {
                return this.map.equals(patch.map);
            }

            return false;
        }

        @Override
        public int hashCode() {
            return this.map.hashCode();
        }
        // CraftBukkit end

        public <T> DataComponentPatch.a set(DataComponentType<T> datacomponenttype, T t0) {
            this.map.put(datacomponenttype, Optional.of(t0));
            return this;
        }

        public <T> DataComponentPatch.a remove(DataComponentType<T> datacomponenttype) {
            this.map.put(datacomponenttype, Optional.empty());
            return this;
        }

        public <T> DataComponentPatch.a set(TypedDataComponent<T> typeddatacomponent) {
            return this.set(typeddatacomponent.type(), typeddatacomponent.value());
        }

        public DataComponentPatch build() {
            return this.map.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(this.map);
        }
    }

    @FunctionalInterface
    private interface b {

        <T> StreamCodec<? super RegistryFriendlyByteBuf, T> apply(DataComponentType<T> datacomponenttype);
    }
}

package org.bukkit.craftbukkit.inventory.components;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.component.BlocksAttacks;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftSound;
import org.bukkit.craftbukkit.damage.CraftDamageType;
import org.bukkit.craftbukkit.inventory.SerializableMeta;
import org.bukkit.craftbukkit.tag.CraftDamageTag;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.bukkit.damage.DamageType;
import org.bukkit.inventory.meta.components.BlocksAttacksComponent;
import org.bukkit.tag.DamageTypeTags;

@SerializableAs("BlocksAttacks")
public final class CraftBlocksAttacksComponent implements BlocksAttacksComponent {

    private BlocksAttacks handle;

    public CraftBlocksAttacksComponent(BlocksAttacks blocksAttacks) {
        this.handle = blocksAttacks;
    }

    public CraftBlocksAttacksComponent(CraftBlocksAttacksComponent blocksAttacks) {
        this.handle = blocksAttacks.handle;
    }

    public CraftBlocksAttacksComponent(Map<String, Object> map) {
        Float blockDelaySeconds = SerializableMeta.getObject(Float.class, map, "default-mining-speed", false);
        Float disableCooldownScale = SerializableMeta.getObject(Float.class, map, "disable-cooldown-scale", false);

        ImmutableList.Builder<DamageReduction> reduction = ImmutableList.builder();
        Iterable<?> rawReductionList = SerializableMeta.getObject(Iterable.class, map, "damage-reductions", true);
        if (rawReductionList != null) {
            for (Object obj : rawReductionList) {
                Preconditions.checkArgument(obj instanceof DamageReduction, "Object (%s) in reduction list is not valid", obj.getClass());

                CraftDamageReduction rule = new CraftDamageReduction((DamageReduction) obj);
                reduction.add(rule);
            }
        }

        Float itemDamageThreshold = SerializableMeta.getObject(Float.class, map, "item-damage-threshold", false);
        Float itemDamageBase = SerializableMeta.getObject(Float.class, map, "item-damage-base", false);
        Float itemDamageFactor = SerializableMeta.getObject(Float.class, map, "item-damage-factor", false);

        String bypassedBy = SerializableMeta.getString(map, "bypassed-by", true);
        TagKey<net.minecraft.world.damagesource.DamageType> tag = null;
        if (bypassedBy != null) {
            tag = TagKey.create(Registries.DAMAGE_TYPE, MinecraftKey.parse(bypassedBy));
        }

        Holder<SoundEffect> blockSound = null;
        String blockSnd = SerializableMeta.getString(map, "block-sound", true);
        if (blockSnd != null) {
            blockSound = BuiltInRegistries.SOUND_EVENT.get(MinecraftKey.parse(blockSnd)).orElse(null);
        }

        Holder<SoundEffect> disableSound = null;
        String disableSnd = SerializableMeta.getString(map, "disable-sound", true);
        if (disableSnd != null) {
            disableSound = BuiltInRegistries.SOUND_EVENT.get(MinecraftKey.parse(disableSnd)).orElse(null);
        }

        this.handle = new BlocksAttacks(blockDelaySeconds, disableCooldownScale, reduction.build().stream().map(CraftDamageReduction::new).map(CraftDamageReduction::getHandle).toList(),
                new net.minecraft.world.item.component.BlocksAttacks.b(itemDamageThreshold, itemDamageBase, itemDamageFactor),
                Optional.ofNullable(tag), Optional.ofNullable(blockSound), Optional.ofNullable(disableSound)
        );
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("block-delay-seconds", getBlockDelaySeconds());
        result.put("disable-cooldown-scale", getDisableCooldownScale());
        result.put("damage-reductions", getDamageReductions());
        result.put("item-damage-threshold", getItemDamageThreshold());
        result.put("item-damage-base", getItemDamageBase());
        result.put("item-damage-factor", getItemDamageFactor());

        handle.bypassedBy().ifPresent((bypassedBy) -> {
            result.put("bypassed-by", bypassedBy.location().toString());
        });

        Sound blockSound = getBlockSound();
        if (blockSound != null) {
            result.put("block-sound", blockSound);
        }

        Sound disableSound = getDisableSound();
        if (disableSound != null) {
            result.put("disable-sound", disableSound);
        }

        return result;
    }

    public BlocksAttacks getHandle() {
        return handle;
    }

    @Override
    public float getBlockDelaySeconds() {
        return handle.blockDelaySeconds();
    }

    @Override
    public void setBlockDelaySeconds(float seconds) {
        Preconditions.checkArgument(seconds >= 0, "seconds cannot be negative");

        handle = new BlocksAttacks(seconds, handle.disableCooldownScale(), handle.damageReductions(), handle.itemDamage(), handle.bypassedBy(), handle.blockSound(), handle.disableSound());
    }

    @Override
    public float getDisableCooldownScale() {
        return handle.disableCooldownScale();
    }

    @Override
    public void setDisableCooldownScale(float scale) {
        Preconditions.checkArgument(scale >= 0, "scale cannot be negative");

        handle = new BlocksAttacks(handle.blockDelaySeconds(), scale, handle.damageReductions(), handle.itemDamage(), handle.bypassedBy(), handle.blockSound(), handle.disableSound());
    }

    @Override
    public List<DamageReduction> getDamageReductions() {
        return handle.damageReductions().stream().map(CraftDamageReduction::new).collect(Collectors.toList());
    }

    @Override
    public void setDamageReductions(List<DamageReduction> reductions) {
        Preconditions.checkArgument(reductions != null, "reductions must not be null");
        handle = new BlocksAttacks(handle.blockDelaySeconds(), handle.disableCooldownScale(),
                reductions.stream().map(CraftDamageReduction::new).map(CraftDamageReduction::getHandle).toList(),
                handle.itemDamage(), handle.bypassedBy(), handle.blockSound(), handle.disableSound());
    }

    @Override
    public DamageReduction addDamageReduction(DamageType type, float base, float factor, float horizontalBlockingAngle) {
        return addRule((type != null) ? HolderSet.direct(CraftDamageType.bukkitToMinecraftHolder(type)) : null, base, factor, horizontalBlockingAngle);
    }

    @Override
    public DamageReduction addDamageReduction(Collection<DamageType> types, float base, float factor, float horizontalBlockingAngle) {
        return addRule((types != null) ? HolderSet.direct(types.stream().map(CraftDamageType::bukkitToMinecraftHolder).toList()) : null, base, factor, horizontalBlockingAngle);
    }

    @Override
    public DamageReduction addDamageReduction(Tag<DamageType> tag, float base, float factor, float horizontalBlockingAngle) {
        Preconditions.checkArgument(tag == null || tag instanceof CraftDamageTag, "tag must be a damage tag");
        return addRule((tag != null) ? ((CraftDamageTag) tag).getHandle() : null, base, factor, horizontalBlockingAngle);
    }

    private DamageReduction addRule(HolderSet<net.minecraft.world.damagesource.DamageType> types, float base, float factor, float horizontalBlockingAngle) {
        BlocksAttacks.a reduction = new net.minecraft.world.item.component.BlocksAttacks.a(horizontalBlockingAngle, Optional.ofNullable(types), base, factor);

        List<BlocksAttacks.a> reductions = new ArrayList<>(handle.damageReductions().size() + 1);
        reductions.addAll(handle.damageReductions());
        reductions.add(reduction);

        handle = new BlocksAttacks(handle.blockDelaySeconds(), handle.disableCooldownScale(), reductions, handle.itemDamage(), handle.bypassedBy(), handle.blockSound(), handle.disableSound());
        return new CraftDamageReduction(reduction);
    }

    @Override
    public boolean removeDamageReduction(DamageReduction reduction) {
        Preconditions.checkArgument(reduction != null, "reduction must not be null");

        List<BlocksAttacks.a> reductions = new ArrayList<>(handle.damageReductions());
        boolean removed = reductions.remove(((CraftDamageReduction) reduction).handle);
        handle = new BlocksAttacks(handle.blockDelaySeconds(), handle.disableCooldownScale(), reductions, handle.itemDamage(), handle.bypassedBy(), handle.blockSound(), handle.disableSound());

        return removed;
    }

    @Override
    public float getItemDamageThreshold() {
        return handle.itemDamage().threshold();
    }

    @Override
    public void setItemDamageThreshold(float threshold) {
        BlocksAttacks.b itemDamage = handle.itemDamage();

        handle = new BlocksAttacks(handle.blockDelaySeconds(), handle.disableCooldownScale(), handle.damageReductions(),
                new net.minecraft.world.item.component.BlocksAttacks.b(threshold, itemDamage.base(), itemDamage.factor()),
                handle.bypassedBy(), handle.blockSound(), handle.disableSound());
    }

    @Override
    public float getItemDamageBase() {
        return handle.itemDamage().base();
    }

    @Override
    public void setItemDamageBase(float base) {
        BlocksAttacks.b itemDamage = handle.itemDamage();

        handle = new BlocksAttacks(handle.blockDelaySeconds(), handle.disableCooldownScale(), handle.damageReductions(),
                new net.minecraft.world.item.component.BlocksAttacks.b(itemDamage.threshold(), base, itemDamage.factor()),
                handle.bypassedBy(), handle.blockSound(), handle.disableSound());
    }

    @Override
    public float getItemDamageFactor() {
        return handle.itemDamage().factor();
    }

    @Override
    public void setItemDamageFactor(float factor) {
        BlocksAttacks.b itemDamage = handle.itemDamage();

        handle = new BlocksAttacks(handle.blockDelaySeconds(), handle.disableCooldownScale(), handle.damageReductions(),
                new net.minecraft.world.item.component.BlocksAttacks.b(itemDamage.threshold(), itemDamage.base(), factor),
                handle.bypassedBy(), handle.blockSound(), handle.disableSound());
    }

    @Override
    public Sound getBlockSound() {
        return handle.blockSound().map(CraftSound::minecraftHolderToBukkit).orElse(null);
    }

    @Override
    public void setBlockSound(Sound sound) {
        handle = new BlocksAttacks(handle.blockDelaySeconds(), handle.disableCooldownScale(), handle.damageReductions(), handle.itemDamage(), handle.bypassedBy(), Optional.ofNullable(sound).map(CraftSound::bukkitToMinecraftHolder), handle.disableSound());
    }

    @Override
    public Sound getDisableSound() {
        return handle.disableSound().map(CraftSound::minecraftHolderToBukkit).orElse(null);
    }

    @Override
    public void setDisableSound(Sound sound) {
        handle = new BlocksAttacks(handle.blockDelaySeconds(), handle.disableCooldownScale(), handle.damageReductions(), handle.itemDamage(), handle.bypassedBy(), handle.blockSound(), Optional.ofNullable(sound).map(CraftSound::bukkitToMinecraftHolder));
    }

    @Override
    public Tag<DamageType> getBypassedBy() {
        return handle.bypassedBy().map((bypassedBy) -> Bukkit.getTag(DamageTypeTags.REGISTRY_DAMAGE_TYPES, CraftNamespacedKey.fromMinecraft(bypassedBy.location()), DamageType.class)).orElse(null);
    }

    @Override
    public void setBypassedBy(Tag<DamageType> tag) {
        handle = new BlocksAttacks(handle.blockDelaySeconds(), handle.disableCooldownScale(), handle.damageReductions(), handle.itemDamage(),
                Optional.ofNullable(tag).map((t) -> ((CraftDamageTag) t).getHandle().key()),
                handle.blockSound(), handle.disableSound());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + Objects.hashCode(this.handle);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CraftBlocksAttacksComponent other = (CraftBlocksAttacksComponent) obj;
        return Objects.equals(this.handle, other.handle);
    }

    @Override
    public String toString() {
        return "CraftBlocksAttacksComponent{" + "handle=" + handle + '}';
    }

    @SerializableAs("DamageReduction")
    public static class CraftDamageReduction implements DamageReduction {

        private BlocksAttacks.a handle;

        public CraftDamageReduction(BlocksAttacks.a handle) {
            this.handle = handle;
        }

        public CraftDamageReduction(DamageReduction bukkit) {
            BlocksAttacks.a toCopy = ((CraftDamageReduction) bukkit).handle;
            this.handle = new BlocksAttacks.a(toCopy.horizontalBlockingAngle(), toCopy.type(), toCopy.base(), toCopy.factor());
        }

        public CraftDamageReduction(Map<String, Object> map) {
            Float base = SerializableMeta.getObject(Float.class, map, "base", false);
            Float factor = SerializableMeta.getObject(Float.class, map, "factor", false);
            Float horizontalBlockingAngle = SerializableMeta.getObject(Float.class, map, "horizontal-blocking-angle", false);

            HolderSet<net.minecraft.world.damagesource.DamageType> typesSet = null;
            Object types = SerializableMeta.getObject(Object.class, map, "types", true);
            if (types != null) {
                typesSet = CraftHolderUtil.parse(types, Registries.DAMAGE_TYPE, CraftRegistry.getMinecraftRegistry(Registries.DAMAGE_TYPE));
            }

            this.handle = new net.minecraft.world.item.component.BlocksAttacks.a(horizontalBlockingAngle, Optional.ofNullable(typesSet), base, factor);
        }

        @Override
        public Map<String, Object> serialize() {
            Map<String, Object> result = new LinkedHashMap<>();

            result.put("base", getBase());
            result.put("factor", getFactor());
            result.put("horizontal-blocking-angle", getHorizontalBlockingAngle());

            handle.type().ifPresent((type) -> {
                CraftHolderUtil.serialize(result, "types", type);
            });

            return result;
        }

        public BlocksAttacks.a getHandle() {
            return handle;
        }

        @Override
        public Collection<DamageType> getTypes() {
            return handle.type().map(type -> type.stream().map(CraftDamageType::minecraftHolderToBukkit).toList()).orElse(null);
        }

        @Override
        public void setTypes(DamageType type) {
            handle = new net.minecraft.world.item.component.BlocksAttacks.a(handle.horizontalBlockingAngle(),
                    Optional.ofNullable(type).map((t) -> HolderSet.direct(CraftDamageType.bukkitToMinecraftHolder(t))),
                    handle.base(), handle.factor());
        }

        @Override
        public void setTypes(Collection<DamageType> types) {
            handle = new net.minecraft.world.item.component.BlocksAttacks.a(handle.horizontalBlockingAngle(),
                    Optional.ofNullable(types).map((t) -> HolderSet.direct(t.stream().map(CraftDamageType::bukkitToMinecraftHolder).toList())),
                    handle.base(), handle.factor());
        }

        @Override
        public void setTypes(Tag<DamageType> tag) {
            Preconditions.checkArgument(tag == null || tag instanceof CraftDamageTag, "tag must be a damage tag");
            handle = new net.minecraft.world.item.component.BlocksAttacks.a(handle.horizontalBlockingAngle(),
                    Optional.ofNullable(tag).map((t) -> ((CraftDamageTag) t).getHandle()),
                    handle.base(), handle.factor());
        }

        @Override
        public float getBase() {
            return handle.base();
        }

        @Override
        public void setBase(float base) {
            handle = new net.minecraft.world.item.component.BlocksAttacks.a(handle.horizontalBlockingAngle(), handle.type(), base, handle.factor());
        }

        @Override
        public float getFactor() {
            return handle.factor();
        }

        @Override
        public void setFactor(float factor) {
            handle = new net.minecraft.world.item.component.BlocksAttacks.a(handle.horizontalBlockingAngle(), handle.type(), handle.base(), factor);
        }

        @Override
        public float getHorizontalBlockingAngle() {
            return handle.horizontalBlockingAngle();
        }

        @Override
        public void setHorizontalBlockingAngle(float horizontalBlockingAngle) {
            handle = new net.minecraft.world.item.component.BlocksAttacks.a(horizontalBlockingAngle, handle.type(), handle.base(), handle.factor());
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 97 * hash + Objects.hashCode(this.handle);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CraftDamageReduction other = (CraftDamageReduction) obj;
            return Objects.equals(this.handle, other.handle);
        }

        @Override
        public String toString() {
            return "CraftDamageReduction{" + "handle=" + handle + '}';
        }
    }
}

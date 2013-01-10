package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPosition;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketListenerPlayOut;
import net.minecraft.world.IInventory;
import net.minecraft.world.InventoryUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkCoordIntPair;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.state.IBlockData;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry;
import org.bukkit.inventory.InventoryHolder;
// CraftBukkit end

import org.spigotmc.CustomTimingsHandler; // Spigot

public abstract class TileEntity {

    public CustomTimingsHandler tickTimer = org.bukkit.craftbukkit.SpigotTimings.getTileEntityTimings(this); // Spigot
    // CraftBukkit start - data containers
    private static final CraftPersistentDataTypeRegistry DATA_TYPE_REGISTRY = new CraftPersistentDataTypeRegistry();
    public CraftPersistentDataContainer persistentDataContainer;
    // CraftBukkit end
    private static final Codec<TileEntityTypes<?>> TYPE_CODEC = BuiltInRegistries.BLOCK_ENTITY_TYPE.byNameCodec();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final TileEntityTypes<?> type;
    @Nullable
    protected World level;
    protected final BlockPosition worldPosition;
    protected boolean remove;
    private IBlockData blockState;
    private DataComponentMap components;

    public TileEntity(TileEntityTypes<?> tileentitytypes, BlockPosition blockposition, IBlockData iblockdata) {
        this.components = DataComponentMap.EMPTY;
        this.type = tileentitytypes;
        this.worldPosition = blockposition.immutable();
        this.validateBlockState(iblockdata);
        this.blockState = iblockdata;
    }

    private void validateBlockState(IBlockData iblockdata) {
        if (!this.isValidBlockState(iblockdata)) {
            String s = this.getNameForReporting();

            throw new IllegalStateException("Invalid block entity " + s + " state at " + String.valueOf(this.worldPosition) + ", got " + String.valueOf(iblockdata));
        }
    }

    public boolean isValidBlockState(IBlockData iblockdata) {
        return this.type.isValid(iblockdata);
    }

    public static BlockPosition getPosFromTag(ChunkCoordIntPair chunkcoordintpair, NBTTagCompound nbttagcompound) {
        int i = nbttagcompound.getIntOr("x", 0);
        int j = nbttagcompound.getIntOr("y", 0);
        int k = nbttagcompound.getIntOr("z", 0);
        int l = SectionPosition.blockToSectionCoord(i);
        int i1 = SectionPosition.blockToSectionCoord(k);

        if (chunkcoordintpair != null && (l != chunkcoordintpair.x || i1 != chunkcoordintpair.z)) { // CraftBukkit - allow null
            TileEntity.LOGGER.warn("Block entity {} found in a wrong chunk, expected position from chunk {}", nbttagcompound, chunkcoordintpair);
            i = chunkcoordintpair.getBlockX(SectionPosition.sectionRelative(i));
            k = chunkcoordintpair.getBlockZ(SectionPosition.sectionRelative(k));
        }

        return new BlockPosition(i, j, k);
    }

    @Nullable
    public World getLevel() {
        return this.level;
    }

    public void setLevel(World world) {
        this.level = world;
    }

    public boolean hasLevel() {
        return this.level != null;
    }

    // CraftBukkit start - read container
    protected void loadAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        this.persistentDataContainer = new CraftPersistentDataContainer(DATA_TYPE_REGISTRY);

        net.minecraft.nbt.NBTBase persistentDataTag = nbttagcompound.get("PublicBukkitValues");
        if (persistentDataTag instanceof NBTTagCompound) {
            this.persistentDataContainer.putAll((NBTTagCompound) persistentDataTag);
        }
    }
    // CraftBukkit end

    public final void loadWithComponents(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        this.loadAdditional(nbttagcompound, holderlookup_a);
        this.components = (DataComponentMap) nbttagcompound.read(TileEntity.a.COMPONENTS_CODEC, holderlookup_a.createSerializationContext(DynamicOpsNBT.INSTANCE)).orElse(DataComponentMap.EMPTY);
    }

    public final void loadCustomOnly(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        this.loadAdditional(nbttagcompound, holderlookup_a);
    }

    protected void saveAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {}

    public final NBTTagCompound saveWithFullMetadata(HolderLookup.a holderlookup_a) {
        NBTTagCompound nbttagcompound = this.saveWithoutMetadata(holderlookup_a);

        this.saveMetadata(nbttagcompound);
        return nbttagcompound;
    }

    public final NBTTagCompound saveWithId(HolderLookup.a holderlookup_a) {
        NBTTagCompound nbttagcompound = this.saveWithoutMetadata(holderlookup_a);

        this.saveId(nbttagcompound);
        return nbttagcompound;
    }

    public final NBTTagCompound saveWithoutMetadata(HolderLookup.a holderlookup_a) {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        this.saveAdditional(nbttagcompound, holderlookup_a);
        nbttagcompound.store(TileEntity.a.COMPONENTS_CODEC, holderlookup_a.createSerializationContext(DynamicOpsNBT.INSTANCE), this.components);
        // CraftBukkit start - store container
        if (this.persistentDataContainer != null && !this.persistentDataContainer.isEmpty()) {
            nbttagcompound.put("PublicBukkitValues", this.persistentDataContainer.toTagCompound());
        }
        // CraftBukkit end
        return nbttagcompound;
    }

    public final NBTTagCompound saveCustomOnly(HolderLookup.a holderlookup_a) {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        this.saveAdditional(nbttagcompound, holderlookup_a);
        return nbttagcompound;
    }

    public final NBTTagCompound saveCustomAndMetadata(HolderLookup.a holderlookup_a) {
        NBTTagCompound nbttagcompound = this.saveCustomOnly(holderlookup_a);

        this.saveMetadata(nbttagcompound);
        return nbttagcompound;
    }

    private void saveId(NBTTagCompound nbttagcompound) {
        addEntityType(nbttagcompound, this.getType());
    }

    public static void addEntityType(NBTTagCompound nbttagcompound, TileEntityTypes<?> tileentitytypes) {
        nbttagcompound.store("id", TileEntity.TYPE_CODEC, tileentitytypes);
    }

    private void saveMetadata(NBTTagCompound nbttagcompound) {
        this.saveId(nbttagcompound);
        nbttagcompound.putInt("x", this.worldPosition.getX());
        nbttagcompound.putInt("y", this.worldPosition.getY());
        nbttagcompound.putInt("z", this.worldPosition.getZ());
    }

    @Nullable
    public static TileEntity loadStatic(BlockPosition blockposition, IBlockData iblockdata, NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        TileEntityTypes<?> tileentitytypes = (TileEntityTypes) nbttagcompound.read("id", TileEntity.TYPE_CODEC).orElse(null); // CraftBukkit - decompile error

        if (tileentitytypes == null) {
            TileEntity.LOGGER.error("Skipping block entity with invalid type: {}", nbttagcompound.get("id"));
            return null;
        } else {
            TileEntity tileentity;

            try {
                tileentity = tileentitytypes.create(blockposition, iblockdata);
            } catch (Throwable throwable) {
                TileEntity.LOGGER.error("Failed to create block entity {} for block {} at position {} ", new Object[]{tileentitytypes, blockposition, iblockdata, throwable});
                return null;
            }

            try {
                tileentity.loadWithComponents(nbttagcompound, holderlookup_a);
                return tileentity;
            } catch (Throwable throwable1) {
                TileEntity.LOGGER.error("Failed to load data for block entity {} for block {} at position {}", new Object[]{tileentitytypes, blockposition, iblockdata, throwable1});
                return null;
            }
        }
    }

    public void setChanged() {
        if (this.level != null) {
            setChanged(this.level, this.worldPosition, this.blockState);
        }

    }

    protected static void setChanged(World world, BlockPosition blockposition, IBlockData iblockdata) {
        world.blockEntityChanged(blockposition);
        if (!iblockdata.isAir()) {
            world.updateNeighbourForOutputSignal(blockposition, iblockdata.getBlock());
        }

    }

    public BlockPosition getBlockPos() {
        return this.worldPosition;
    }

    public IBlockData getBlockState() {
        return this.blockState;
    }

    @Nullable
    public Packet<PacketListenerPlayOut> getUpdatePacket() {
        return null;
    }

    public NBTTagCompound getUpdateTag(HolderLookup.a holderlookup_a) {
        return new NBTTagCompound();
    }

    public boolean isRemoved() {
        return this.remove;
    }

    public void setRemoved() {
        this.remove = true;
    }

    public void clearRemoved() {
        this.remove = false;
    }

    public void preRemoveSideEffects(BlockPosition blockposition, IBlockData iblockdata) {
        if (this instanceof IInventory iinventory) {
            if (this.level != null) {
                InventoryUtils.dropContents(this.level, blockposition, iinventory);
            }
        }

    }

    public boolean triggerEvent(int i, int j) {
        return false;
    }

    public void fillCrashReportCategory(CrashReportSystemDetails crashreportsystemdetails) {
        crashreportsystemdetails.setDetail("Name", this::getNameForReporting);
        IBlockData iblockdata = this.getBlockState();

        Objects.requireNonNull(iblockdata);
        crashreportsystemdetails.setDetail("Cached block", iblockdata::toString);
        if (this.level == null) {
            crashreportsystemdetails.setDetail("Block location", () -> {
                return String.valueOf(this.worldPosition) + " (world missing)";
            });
        } else {
            iblockdata = this.level.getBlockState(this.worldPosition);
            Objects.requireNonNull(iblockdata);
            crashreportsystemdetails.setDetail("Actual block", iblockdata::toString);
            CrashReportSystemDetails.populateBlockLocationDetails(crashreportsystemdetails, this.level, this.worldPosition);
        }

    }

    private String getNameForReporting() {
        String s = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(this.getType()));

        return s + " // " + this.getClass().getCanonicalName();
    }

    public TileEntityTypes<?> getType() {
        return this.type;
    }

    /** @deprecated */
    @Deprecated
    public void setBlockState(IBlockData iblockdata) {
        this.validateBlockState(iblockdata);
        this.blockState = iblockdata;
    }

    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {}

    public final void applyComponentsFromItemStack(ItemStack itemstack) {
        this.applyComponents(itemstack.getPrototype(), itemstack.getComponentsPatch());
    }

    public final void applyComponents(DataComponentMap datacomponentmap, DataComponentPatch datacomponentpatch) {
        // CraftBukkit start
        this.applyComponentsSet(datacomponentmap, datacomponentpatch);
    }

    public final Set<DataComponentType<?>> applyComponentsSet(DataComponentMap datacomponentmap, DataComponentPatch datacomponentpatch) {
        // CraftBukkit end
        final Set<DataComponentType<?>> set = new HashSet();

        set.add(DataComponents.BLOCK_ENTITY_DATA);
        set.add(DataComponents.BLOCK_STATE);
        final DataComponentMap datacomponentmap1 = PatchedDataComponentMap.fromPatch(datacomponentmap, datacomponentpatch);

        this.applyImplicitComponents(new DataComponentGetter() {
            @Nullable
            @Override
            public <T> T get(DataComponentType<? extends T> datacomponenttype) {
                set.add(datacomponenttype);
                return (T) datacomponentmap1.get(datacomponenttype);
            }

            @Override
            public <T> T getOrDefault(DataComponentType<? extends T> datacomponenttype, T t0) {
                set.add(datacomponenttype);
                return (T) datacomponentmap1.getOrDefault(datacomponenttype, t0);
            }
        });
        Objects.requireNonNull(set);
        DataComponentPatch datacomponentpatch1 = datacomponentpatch.forget(set::contains);

        this.components = datacomponentpatch1.split().added();
        // CraftBukkit start
        set.remove(DataComponents.BLOCK_ENTITY_DATA); // Remove as never actually added by applyImplicitComponents
        set.remove(DataComponents.BLOCK_STATE); // Remove as never actually added by applyImplicitComponents
        return set;
        // CraftBukkit end
    }

    protected void collectImplicitComponents(DataComponentMap.a datacomponentmap_a) {}

    /** @deprecated */
    @Deprecated
    public void removeComponentsFromTag(NBTTagCompound nbttagcompound) {}

    public final DataComponentMap collectComponents() {
        DataComponentMap.a datacomponentmap_a = DataComponentMap.builder();

        datacomponentmap_a.addAll(this.components);
        this.collectImplicitComponents(datacomponentmap_a);
        return datacomponentmap_a.build();
    }

    public DataComponentMap components() {
        return this.components;
    }

    public void setComponents(DataComponentMap datacomponentmap) {
        this.components = datacomponentmap;
    }

    @Nullable
    public static IChatBaseComponent parseCustomNameSafe(@Nullable NBTBase nbtbase, HolderLookup.a holderlookup_a) {
        return nbtbase == null ? null : (IChatBaseComponent) ComponentSerialization.CODEC.parse(holderlookup_a.createSerializationContext(DynamicOpsNBT.INSTANCE), nbtbase).resultOrPartial((s) -> {
            TileEntity.LOGGER.warn("Failed to parse custom name, discarding: {}", s);
        }).orElse(null); // CraftBukkit - decompile error
    }

    // CraftBukkit start - add method
    public InventoryHolder getOwner() {
        if (level == null) return null;
        org.bukkit.block.BlockState state = level.getWorld().getBlockAt(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).getState();
        if (state instanceof InventoryHolder) return (InventoryHolder) state;
        return null;
    }
    // CraftBukkit end

    private static class a {

        public static final MapCodec<DataComponentMap> COMPONENTS_CODEC = DataComponentMap.CODEC.optionalFieldOf("components", DataComponentMap.EMPTY);

        private a() {}
    }
}

package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.INamableTileEntity;
import net.minecraft.world.item.EnumColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BlockBanner;
import net.minecraft.world.level.block.BlockBannerAbstract;
import net.minecraft.world.level.block.state.IBlockData;

// CraftBukkit start
import java.util.List;
// CraftBukkit end

public class TileEntityBanner extends TileEntity implements INamableTileEntity {

    public static final int MAX_PATTERNS = 6;
    private static final String TAG_PATTERNS = "patterns";
    @Nullable
    private IChatBaseComponent name;
    public EnumColor baseColor;
    private BannerPatternLayers patterns;

    public TileEntityBanner(BlockPosition blockposition, IBlockData iblockdata) {
        this(blockposition, iblockdata, ((BlockBannerAbstract) iblockdata.getBlock()).getColor());
    }

    public TileEntityBanner(BlockPosition blockposition, IBlockData iblockdata, EnumColor enumcolor) {
        super(TileEntityTypes.BANNER, blockposition, iblockdata);
        this.patterns = BannerPatternLayers.EMPTY;
        this.baseColor = enumcolor;
    }

    @Override
    public IChatBaseComponent getName() {
        return (IChatBaseComponent) (this.name != null ? this.name : IChatBaseComponent.translatable("block.minecraft.banner"));
    }

    @Nullable
    @Override
    public IChatBaseComponent getCustomName() {
        return this.name;
    }

    @Override
    protected void saveAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        super.saveAdditional(nbttagcompound, holderlookup_a);
        RegistryOps<NBTBase> registryops = holderlookup_a.<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

        if (!this.patterns.equals(BannerPatternLayers.EMPTY)) {
            nbttagcompound.store("patterns", BannerPatternLayers.CODEC, registryops, this.patterns);
        }

        nbttagcompound.storeNullable("CustomName", ComponentSerialization.CODEC, registryops, this.name);
    }

    @Override
    protected void loadAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        super.loadAdditional(nbttagcompound, holderlookup_a);
        this.name = parseCustomNameSafe(nbttagcompound.get("CustomName"), holderlookup_a);
        RegistryOps<NBTBase> registryops = holderlookup_a.<NBTBase>createSerializationContext(DynamicOpsNBT.INSTANCE);

        this.setPatterns((BannerPatternLayers) nbttagcompound.read("patterns", BannerPatternLayers.CODEC, registryops).orElse(BannerPatternLayers.EMPTY)); // CraftBukkit - apply limits
    }

    @Override
    public PacketPlayOutTileEntityData getUpdatePacket() {
        return PacketPlayOutTileEntityData.create(this);
    }

    @Override
    public NBTTagCompound getUpdateTag(HolderLookup.a holderlookup_a) {
        return this.saveWithoutMetadata(holderlookup_a);
    }

    public BannerPatternLayers getPatterns() {
        return this.patterns;
    }

    public ItemStack getItem() {
        ItemStack itemstack = new ItemStack(BlockBanner.byColor(this.baseColor));

        itemstack.applyComponents(this.collectComponents());
        return itemstack;
    }

    public EnumColor getBaseColor() {
        return this.baseColor;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        super.applyImplicitComponents(datacomponentgetter);
        this.setPatterns((BannerPatternLayers) datacomponentgetter.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)); // CraftBukkit - apply limits
        this.name = (IChatBaseComponent) datacomponentgetter.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.a datacomponentmap_a) {
        super.collectImplicitComponents(datacomponentmap_a);
        datacomponentmap_a.set(DataComponents.BANNER_PATTERNS, this.patterns);
        datacomponentmap_a.set(DataComponents.CUSTOM_NAME, this.name);
    }

    @Override
    public void removeComponentsFromTag(NBTTagCompound nbttagcompound) {
        nbttagcompound.remove("patterns");
        nbttagcompound.remove("CustomName");
    }

    // CraftBukkit start
    public void setPatterns(BannerPatternLayers bannerpatternlayers) {
        if (bannerpatternlayers.layers().size() > 20) {
            bannerpatternlayers = new BannerPatternLayers(List.copyOf(bannerpatternlayers.layers().subList(0, 20)));
        }
        this.patterns = bannerpatternlayers;
    }
    // CraftBukkit end
}

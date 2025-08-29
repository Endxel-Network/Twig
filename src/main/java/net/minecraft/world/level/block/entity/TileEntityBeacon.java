package net.minecraft.world.level.block.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.DynamicOpsNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.PacketPlayOutTileEntityData;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.sounds.SoundCategory;
import net.minecraft.sounds.SoundEffect;
import net.minecraft.sounds.SoundEffects;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.ARGB;
import net.minecraft.world.ChestLock;
import net.minecraft.world.INamableTileEntity;
import net.minecraft.world.ITileInventory;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectList;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerAccess;
import net.minecraft.world.inventory.ContainerBeacon;
import net.minecraft.world.inventory.IContainerProperties;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IBeaconBeam;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.level.levelgen.HeightMap;
import net.minecraft.world.phys.AxisAlignedBB;

// CraftBukkit start
import org.bukkit.craftbukkit.potion.CraftPotionUtil;
import org.bukkit.potion.PotionEffect;
// CraftBukkit end

public class TileEntityBeacon extends TileEntity implements ITileInventory, INamableTileEntity, BeaconBeamOwner {

    private static final int MAX_LEVELS = 4;
    public static final List<List<Holder<MobEffectList>>> BEACON_EFFECTS = List.of(List.of(MobEffects.SPEED, MobEffects.HASTE), List.of(MobEffects.RESISTANCE, MobEffects.JUMP_BOOST), List.of(MobEffects.STRENGTH), List.of(MobEffects.REGENERATION));
    private static final Set<Holder<MobEffectList>> VALID_EFFECTS = (Set) TileEntityBeacon.BEACON_EFFECTS.stream().flatMap(Collection::stream).collect(Collectors.toSet());
    public static final int DATA_LEVELS = 0;
    public static final int DATA_PRIMARY = 1;
    public static final int DATA_SECONDARY = 2;
    public static final int NUM_DATA_VALUES = 3;
    private static final int BLOCKS_CHECK_PER_TICK = 10;
    private static final IChatBaseComponent DEFAULT_NAME = IChatBaseComponent.translatable("container.beacon");
    private static final String TAG_PRIMARY = "primary_effect";
    private static final String TAG_SECONDARY = "secondary_effect";
    List<BeaconBeamOwner.a> beamSections = new ArrayList();
    private List<BeaconBeamOwner.a> checkingBeamSections = new ArrayList();
    public int levels;
    private int lastCheckY;
    @Nullable
    public Holder<MobEffectList> primaryPower;
    @Nullable
    public Holder<MobEffectList> secondaryPower;
    @Nullable
    public IChatBaseComponent name;
    public ChestLock lockKey;
    private final IContainerProperties dataAccess;
    // CraftBukkit start - add fields and methods
    public PotionEffect getPrimaryEffect() {
        return (this.primaryPower != null) ? CraftPotionUtil.toBukkit(new MobEffect(this.primaryPower, getLevel(this.levels), getAmplification(levels, primaryPower, secondaryPower), true, true)) : null;
    }

    public PotionEffect getSecondaryEffect() {
        return (hasSecondaryEffect(levels, primaryPower, secondaryPower)) ? CraftPotionUtil.toBukkit(new MobEffect(this.secondaryPower, getLevel(this.levels), getAmplification(levels, primaryPower, secondaryPower), true, true)) : null;
    }
    // CraftBukkit end

    @Nullable
    static Holder<MobEffectList> filterEffect(@Nullable Holder<MobEffectList> holder) {
        return TileEntityBeacon.VALID_EFFECTS.contains(holder) ? holder : null;
    }

    public TileEntityBeacon(BlockPosition blockposition, IBlockData iblockdata) {
        super(TileEntityTypes.BEACON, blockposition, iblockdata);
        this.lockKey = ChestLock.NO_LOCK;
        this.dataAccess = new IContainerProperties() {
            @Override
            public int get(int i) {
                int j;

                switch (i) {
                    case 0:
                        j = TileEntityBeacon.this.levels;
                        break;
                    case 1:
                        j = ContainerBeacon.encodeEffect(TileEntityBeacon.this.primaryPower);
                        break;
                    case 2:
                        j = ContainerBeacon.encodeEffect(TileEntityBeacon.this.secondaryPower);
                        break;
                    default:
                        j = 0;
                }

                return j;
            }

            @Override
            public void set(int i, int j) {
                switch (i) {
                    case 0:
                        TileEntityBeacon.this.levels = j;
                        break;
                    case 1:
                        if (!TileEntityBeacon.this.level.isClientSide && !TileEntityBeacon.this.beamSections.isEmpty()) {
                            TileEntityBeacon.playSound(TileEntityBeacon.this.level, TileEntityBeacon.this.worldPosition, SoundEffects.BEACON_POWER_SELECT);
                        }

                        TileEntityBeacon.this.primaryPower = TileEntityBeacon.filterEffect(ContainerBeacon.decodeEffect(j));
                        break;
                    case 2:
                        TileEntityBeacon.this.secondaryPower = TileEntityBeacon.filterEffect(ContainerBeacon.decodeEffect(j));
                }

            }

            @Override
            public int getCount() {
                return 3;
            }
        };
    }

    public static void tick(World world, BlockPosition blockposition, IBlockData iblockdata, TileEntityBeacon tileentitybeacon) {
        int i = blockposition.getX();
        int j = blockposition.getY();
        int k = blockposition.getZ();
        BlockPosition blockposition1;

        if (tileentitybeacon.lastCheckY < j) {
            blockposition1 = blockposition;
            tileentitybeacon.checkingBeamSections = Lists.newArrayList();
            tileentitybeacon.lastCheckY = blockposition.getY() - 1;
        } else {
            blockposition1 = new BlockPosition(i, tileentitybeacon.lastCheckY + 1, k);
        }

        BeaconBeamOwner.a beaconbeamowner_a = tileentitybeacon.checkingBeamSections.isEmpty() ? null : (BeaconBeamOwner.a) tileentitybeacon.checkingBeamSections.get(tileentitybeacon.checkingBeamSections.size() - 1);
        int l = world.getHeight(HeightMap.Type.WORLD_SURFACE, i, k);

        for (int i1 = 0; i1 < 10 && blockposition1.getY() <= l; ++i1) {
            IBlockData iblockdata1 = world.getBlockState(blockposition1);
            Block block = iblockdata1.getBlock();

            if (block instanceof IBeaconBeam ibeaconbeam) {
                int j1 = ibeaconbeam.getColor().getTextureDiffuseColor();

                if (tileentitybeacon.checkingBeamSections.size() <= 1) {
                    beaconbeamowner_a = new BeaconBeamOwner.a(j1);
                    tileentitybeacon.checkingBeamSections.add(beaconbeamowner_a);
                } else if (beaconbeamowner_a != null) {
                    if (j1 == beaconbeamowner_a.getColor()) {
                        beaconbeamowner_a.increaseHeight();
                    } else {
                        beaconbeamowner_a = new BeaconBeamOwner.a(ARGB.average(beaconbeamowner_a.getColor(), j1));
                        tileentitybeacon.checkingBeamSections.add(beaconbeamowner_a);
                    }
                }
            } else {
                if (beaconbeamowner_a == null || iblockdata1.getLightBlock() >= 15 && !iblockdata1.is(Blocks.BEDROCK)) {
                    tileentitybeacon.checkingBeamSections.clear();
                    tileentitybeacon.lastCheckY = l;
                    break;
                }

                beaconbeamowner_a.increaseHeight();
            }

            blockposition1 = blockposition1.above();
            ++tileentitybeacon.lastCheckY;
        }

        int k1 = tileentitybeacon.levels;

        if (world.getGameTime() % 80L == 0L) {
            if (!tileentitybeacon.beamSections.isEmpty()) {
                tileentitybeacon.levels = updateBase(world, i, j, k);
            }

            if (tileentitybeacon.levels > 0 && !tileentitybeacon.beamSections.isEmpty()) {
                applyEffects(world, blockposition, tileentitybeacon.levels, tileentitybeacon.primaryPower, tileentitybeacon.secondaryPower);
                playSound(world, blockposition, SoundEffects.BEACON_AMBIENT);
            }
        }

        if (tileentitybeacon.lastCheckY >= l) {
            tileentitybeacon.lastCheckY = world.getMinY() - 1;
            boolean flag = k1 > 0;

            tileentitybeacon.beamSections = tileentitybeacon.checkingBeamSections;
            if (!world.isClientSide) {
                boolean flag1 = tileentitybeacon.levels > 0;

                if (!flag && flag1) {
                    playSound(world, blockposition, SoundEffects.BEACON_ACTIVATE);

                    for (EntityPlayer entityplayer : world.getEntitiesOfClass(EntityPlayer.class, (new AxisAlignedBB((double) i, (double) j, (double) k, (double) i, (double) (j - 4), (double) k)).inflate(10.0D, 5.0D, 10.0D))) {
                        CriterionTriggers.CONSTRUCT_BEACON.trigger(entityplayer, tileentitybeacon.levels);
                    }
                } else if (flag && !flag1) {
                    playSound(world, blockposition, SoundEffects.BEACON_DEACTIVATE);
                }
            }
        }

    }

    private static int updateBase(World world, int i, int j, int k) {
        int l = 0;

        for (int i1 = 1; i1 <= 4; l = i1++) {
            int j1 = j - i1;

            if (j1 < world.getMinY()) {
                break;
            }

            boolean flag = true;

            for (int k1 = i - i1; k1 <= i + i1 && flag; ++k1) {
                for (int l1 = k - i1; l1 <= k + i1; ++l1) {
                    if (!world.getBlockState(new BlockPosition(k1, j1, l1)).is(TagsBlock.BEACON_BASE_BLOCKS)) {
                        flag = false;
                        break;
                    }
                }
            }

            if (!flag) {
                break;
            }
        }

        return l;
    }

    @Override
    public void setRemoved() {
        playSound(this.level, this.worldPosition, SoundEffects.BEACON_DEACTIVATE);
        super.setRemoved();
    }

    // CraftBukkit start - split into components
    private static int getAmplification(int i, @Nullable Holder<MobEffectList> holder, @Nullable Holder<MobEffectList> holder1) {
        {
            int j = 0;

            if (i >= 4 && Objects.equals(holder, holder1)) {
                j = 1;
            }

            return j;
        }
    }

    private static int getLevel(int i) {
        {
            int k = (9 + i * 2) * 20;
            return k;
        }
    }

    public static List getHumansInRange(World world, BlockPosition blockposition, int i) {
        {
            double d0 = (double) (i * 10 + 10);

            AxisAlignedBB axisalignedbb = (new AxisAlignedBB(blockposition)).inflate(d0).expandTowards(0.0D, (double) world.getHeight(), 0.0D);
            List<EntityHuman> list = world.<EntityHuman>getEntitiesOfClass(EntityHuman.class, axisalignedbb);

            return list;
        }
    }

    private static void applyEffect(List<EntityHuman> list, @Nullable Holder<MobEffectList> holder, int k, int j) {
        {
            for (EntityHuman entityhuman : list) {
                entityhuman.addEffect(new MobEffect(holder, k, j, true, true), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.BEACON);
            }
        }
    }

    private static boolean hasSecondaryEffect(int i, @Nullable Holder<MobEffectList> holder, @Nullable Holder<MobEffectList> holder1) {
        {
            if (i >= 4 && !Objects.equals(holder, holder1) && holder1 != null) {
                return true;
            }

            return false;
        }
    }

    private static void applyEffects(World world, BlockPosition blockposition, int i, @Nullable Holder<MobEffectList> holder, @Nullable Holder<MobEffectList> holder1) {
        if (!world.isClientSide && holder != null) {
            int j = getAmplification(i, holder, holder1);

            int k = getLevel(i);
            List list = getHumansInRange(world, blockposition, i);

            applyEffect(list, holder, k, j);

            if (hasSecondaryEffect(i, holder, holder1)) {
                applyEffect(list, holder1, k, 0);
            }
        }

    }
    // CraftBukkit end

    public static void playSound(World world, BlockPosition blockposition, SoundEffect soundeffect) {
        world.playSound((Entity) null, blockposition, soundeffect, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    @Override
    public List<BeaconBeamOwner.a> getBeamSections() {
        return (List<BeaconBeamOwner.a>) (this.levels == 0 ? ImmutableList.of() : this.beamSections);
    }

    @Override
    public PacketPlayOutTileEntityData getUpdatePacket() {
        return PacketPlayOutTileEntityData.create(this);
    }

    @Override
    public NBTTagCompound getUpdateTag(HolderLookup.a holderlookup_a) {
        return this.saveCustomOnly(holderlookup_a);
    }

    private static void storeEffect(NBTTagCompound nbttagcompound, String s, @Nullable Holder<MobEffectList> holder) {
        if (holder != null) {
            holder.unwrapKey().ifPresent((resourcekey) -> {
                nbttagcompound.putString(s, resourcekey.location().toString());
            });
        }

    }

    @Nullable
    private static Holder<MobEffectList> loadEffect(NBTTagCompound nbttagcompound, String s) {
        Optional optional = nbttagcompound.read(s, BuiltInRegistries.MOB_EFFECT.holderByNameCodec());
        Set set = TileEntityBeacon.VALID_EFFECTS;

        Objects.requireNonNull(set);
        return (Holder) optional.orElse((Object) null); // CraftBukkit - persist manually set non-default beacon effects (SPIGOT-3598)
    }

    @Override
    protected void loadAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        super.loadAdditional(nbttagcompound, holderlookup_a);
        this.primaryPower = loadEffect(nbttagcompound, "primary_effect");
        this.secondaryPower = loadEffect(nbttagcompound, "secondary_effect");
        this.levels = nbttagcompound.getIntOr("Levels", this.levels); // CraftBukkit - SPIGOT-5053, use where available
        this.name = parseCustomNameSafe(nbttagcompound.get("CustomName"), holderlookup_a);
        this.lockKey = ChestLock.fromTag(nbttagcompound, holderlookup_a);
    }

    @Override
    protected void saveAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        super.saveAdditional(nbttagcompound, holderlookup_a);
        storeEffect(nbttagcompound, "primary_effect", this.primaryPower);
        storeEffect(nbttagcompound, "secondary_effect", this.secondaryPower);
        nbttagcompound.putInt("Levels", this.levels);
        nbttagcompound.storeNullable("CustomName", ComponentSerialization.CODEC, holderlookup_a.createSerializationContext(DynamicOpsNBT.INSTANCE), this.name);
        this.lockKey.addToTag(nbttagcompound, holderlookup_a);
    }

    public void setCustomName(@Nullable IChatBaseComponent ichatbasecomponent) {
        this.name = ichatbasecomponent;
    }

    @Nullable
    @Override
    public IChatBaseComponent getCustomName() {
        return this.name;
    }

    @Nullable
    @Override
    public Container createMenu(int i, PlayerInventory playerinventory, EntityHuman entityhuman) {
        return TileEntityContainer.canUnlock(entityhuman, this.lockKey, this.getDisplayName()) ? new ContainerBeacon(i, playerinventory, this.dataAccess, ContainerAccess.create(this.level, this.getBlockPos())) : null;
    }

    @Override
    public IChatBaseComponent getDisplayName() {
        return this.getName();
    }

    @Override
    public IChatBaseComponent getName() {
        return this.name != null ? this.name : TileEntityBeacon.DEFAULT_NAME;
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter datacomponentgetter) {
        super.applyImplicitComponents(datacomponentgetter);
        this.name = (IChatBaseComponent) datacomponentgetter.get(DataComponents.CUSTOM_NAME);
        this.lockKey = (ChestLock) datacomponentgetter.getOrDefault(DataComponents.LOCK, ChestLock.NO_LOCK);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.a datacomponentmap_a) {
        super.collectImplicitComponents(datacomponentmap_a);
        datacomponentmap_a.set(DataComponents.CUSTOM_NAME, this.name);
        if (!this.lockKey.equals(ChestLock.NO_LOCK)) {
            datacomponentmap_a.set(DataComponents.LOCK, this.lockKey);
        }

    }

    @Override
    public void removeComponentsFromTag(NBTTagCompound nbttagcompound) {
        nbttagcompound.remove("CustomName");
        nbttagcompound.remove("lock");
    }

    @Override
    public void setLevel(World world) {
        super.setLevel(world);
        this.lastCheckY = world.getMinY() - 1;
    }
}

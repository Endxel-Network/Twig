package net.minecraft.world.inventory;

import com.google.common.base.Suppliers;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IInventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.TileEntity;
import org.slf4j.Logger;

// CraftBukkit start
import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.PacketPlayOutSetSlot;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.Event.Result;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
// CraftBukkit end

public abstract class Container {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int SLOT_CLICKED_OUTSIDE = -999;
    public static final int QUICKCRAFT_TYPE_CHARITABLE = 0;
    public static final int QUICKCRAFT_TYPE_GREEDY = 1;
    public static final int QUICKCRAFT_TYPE_CLONE = 2;
    public static final int QUICKCRAFT_HEADER_START = 0;
    public static final int QUICKCRAFT_HEADER_CONTINUE = 1;
    public static final int QUICKCRAFT_HEADER_END = 2;
    public static final int CARRIED_SLOT_SIZE = Integer.MAX_VALUE;
    public static final int SLOTS_PER_ROW = 9;
    public static final int SLOT_SIZE = 18;
    public NonNullList<ItemStack> lastSlots = NonNullList.<ItemStack>create();
    public NonNullList<Slot> slots = NonNullList.<Slot>create();
    private final List<ContainerProperty> dataSlots = Lists.newArrayList();
    private ItemStack carried;
    public NonNullList<RemoteSlot> remoteSlots;
    private final IntList remoteDataSlots;
    private RemoteSlot remoteCarried;
    private int stateId;
    @Nullable
    private final Containers<?> menuType;
    public final int containerId;
    private int quickcraftType;
    private int quickcraftStatus;
    private final Set<Slot> quickcraftSlots;
    private final List<ICrafting> containerListeners;
    @Nullable
    private ContainerSynchronizer synchronizer;
    private boolean suppressRemoteUpdates;

    // CraftBukkit start
    public boolean checkReachable = true;
    public abstract InventoryView getBukkitView();
    public void transferTo(Container other, org.bukkit.craftbukkit.entity.CraftHumanEntity player) {
        InventoryView source = this.getBukkitView(), destination = other.getBukkitView();
        ((CraftInventory) source.getTopInventory()).getInventory().onClose(player);
        ((CraftInventory) source.getBottomInventory()).getInventory().onClose(player);
        ((CraftInventory) destination.getTopInventory()).getInventory().onOpen(player);
        ((CraftInventory) destination.getBottomInventory()).getInventory().onOpen(player);
    }
    private IChatBaseComponent title;
    public final IChatBaseComponent getTitle() {
        Preconditions.checkState(this.title != null, "Title not set");
        return this.title;
    }
    public final void setTitle(IChatBaseComponent title) {
        Preconditions.checkState(this.title == null, "Title already set");
        this.title = title;
    }
    protected boolean opened;
    public void startOpen() {
        this.opened = true;
    }
    // CraftBukkit end

    protected Container(@Nullable Containers<?> containers, int i) {
        this.carried = ItemStack.EMPTY;
        this.remoteSlots = NonNullList.<RemoteSlot>create();
        this.remoteDataSlots = new IntArrayList();
        this.remoteCarried = RemoteSlot.PLACEHOLDER;
        this.quickcraftType = -1;
        this.quickcraftSlots = Sets.newHashSet();
        this.containerListeners = Lists.newArrayList();
        this.menuType = containers;
        this.containerId = i;
    }

    protected void addInventoryHotbarSlots(IInventory iinventory, int i, int j) {
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(iinventory, k, i + k * 18, j));
        }

    }

    protected void addInventoryExtendedSlots(IInventory iinventory, int i, int j) {
        for (int k = 0; k < 3; ++k) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(iinventory, l + (k + 1) * 9, i + l * 18, j + k * 18));
            }
        }

    }

    protected void addStandardInventorySlots(IInventory iinventory, int i, int j) {
        this.addInventoryExtendedSlots(iinventory, i, j);
        int k = 4;
        int l = 58;

        this.addInventoryHotbarSlots(iinventory, i, j + 58);
    }

    protected static boolean stillValid(ContainerAccess containeraccess, EntityHuman entityhuman, Block block) {
        return (Boolean) containeraccess.evaluate((world, blockposition) -> {
            return !world.getBlockState(blockposition).is(block) ? false : entityhuman.canInteractWithBlock(blockposition, 4.0D);
        }, true);
    }

    public Containers<?> getType() {
        if (this.menuType == null) {
            throw new UnsupportedOperationException("Unable to construct this menu by type");
        } else {
            return this.menuType;
        }
    }

    protected static void checkContainerSize(IInventory iinventory, int i) {
        int j = iinventory.getContainerSize();

        if (j < i) {
            throw new IllegalArgumentException("Container size " + j + " is smaller than expected " + i);
        }
    }

    protected static void checkContainerDataCount(IContainerProperties icontainerproperties, int i) {
        int j = icontainerproperties.getCount();

        if (j < i) {
            throw new IllegalArgumentException("Container data count " + j + " is smaller than expected " + i);
        }
    }

    public boolean isValidSlotIndex(int i) {
        return i == -1 || i == -999 || i < this.slots.size();
    }

    protected Slot addSlot(Slot slot) {
        slot.index = this.slots.size();
        this.slots.add(slot);
        this.lastSlots.add(ItemStack.EMPTY);
        this.remoteSlots.add(this.synchronizer != null ? this.synchronizer.createSlot() : RemoteSlot.PLACEHOLDER);
        return slot;
    }

    protected ContainerProperty addDataSlot(ContainerProperty containerproperty) {
        this.dataSlots.add(containerproperty);
        this.remoteDataSlots.add(0);
        return containerproperty;
    }

    protected void addDataSlots(IContainerProperties icontainerproperties) {
        for (int i = 0; i < icontainerproperties.getCount(); ++i) {
            this.addDataSlot(ContainerProperty.forContainer(icontainerproperties, i));
        }

    }

    public void addSlotListener(ICrafting icrafting) {
        if (!this.containerListeners.contains(icrafting)) {
            this.containerListeners.add(icrafting);
            this.broadcastChanges();
        }
    }

    public void setSynchronizer(ContainerSynchronizer containersynchronizer) {
        this.synchronizer = containersynchronizer;
        this.remoteCarried = containersynchronizer.createSlot();
        this.remoteSlots.replaceAll((remoteslot) -> {
            return containersynchronizer.createSlot();
        });
        this.sendAllDataToRemote();
    }

    public void sendAllDataToRemote() {
        List<ItemStack> list = new ArrayList(this.slots.size());
        int i = 0;

        for (int j = this.slots.size(); i < j; ++i) {
            ItemStack itemstack = ((Slot) this.slots.get(i)).getItem();

            list.add(itemstack.copy());
            ((RemoteSlot) this.remoteSlots.get(i)).force(itemstack);
        }

        ItemStack itemstack1 = this.getCarried();

        this.remoteCarried.force(itemstack1);
        int k = 0;

        for (int l = this.dataSlots.size(); k < l; ++k) {
            this.remoteDataSlots.set(k, ((ContainerProperty) this.dataSlots.get(k)).get());
        }

        if (this.synchronizer != null) {
            this.synchronizer.sendInitialData(this, list, itemstack1.copy(), this.remoteDataSlots.toIntArray());
        }

    }

    // CraftBukkit start
    public void broadcastCarriedItem() {
        ItemStack itemstack = this.getCarried().copy();
        this.remoteCarried.force(itemstack);
        if (this.synchronizer != null) {
            this.synchronizer.sendCarriedChange(this, itemstack);
        }
    }
    // CraftBukkit end

    public void removeSlotListener(ICrafting icrafting) {
        this.containerListeners.remove(icrafting);
    }

    public NonNullList<ItemStack> getItems() {
        NonNullList<ItemStack> nonnulllist = NonNullList.<ItemStack>create();

        for (Slot slot : this.slots) {
            nonnulllist.add(slot.getItem());
        }

        return nonnulllist;
    }

    public void broadcastChanges() {
        for (int i = 0; i < this.slots.size(); ++i) {
            ItemStack itemstack = ((Slot) this.slots.get(i)).getItem();

            Objects.requireNonNull(itemstack);
            Supplier<ItemStack> supplier = Suppliers.memoize(itemstack::copy);

            this.triggerSlotListeners(i, itemstack, supplier);
            this.synchronizeSlotToRemote(i, itemstack, supplier);
        }

        this.synchronizeCarriedToRemote();

        for (int j = 0; j < this.dataSlots.size(); ++j) {
            ContainerProperty containerproperty = (ContainerProperty) this.dataSlots.get(j);
            int k = containerproperty.get();

            if (containerproperty.checkAndClearUpdateFlag()) {
                this.updateDataSlotListeners(j, k);
            }

            this.synchronizeDataSlotToRemote(j, k);
        }

    }

    public void broadcastFullState() {
        for (int i = 0; i < this.slots.size(); ++i) {
            ItemStack itemstack = ((Slot) this.slots.get(i)).getItem();

            Objects.requireNonNull(itemstack);
            this.triggerSlotListeners(i, itemstack, itemstack::copy);
        }

        for (int j = 0; j < this.dataSlots.size(); ++j) {
            ContainerProperty containerproperty = (ContainerProperty) this.dataSlots.get(j);

            if (containerproperty.checkAndClearUpdateFlag()) {
                this.updateDataSlotListeners(j, containerproperty.get());
            }
        }

        this.sendAllDataToRemote();
    }

    private void updateDataSlotListeners(int i, int j) {
        for (ICrafting icrafting : this.containerListeners) {
            icrafting.dataChanged(this, i, j);
        }

    }

    private void triggerSlotListeners(int i, ItemStack itemstack, Supplier<ItemStack> supplier) {
        ItemStack itemstack1 = this.lastSlots.get(i);

        if (!ItemStack.matches(itemstack1, itemstack)) {
            ItemStack itemstack2 = (ItemStack) supplier.get();

            this.lastSlots.set(i, itemstack2);

            for (ICrafting icrafting : this.containerListeners) {
                icrafting.slotChanged(this, i, itemstack2);
            }
        }

    }

    private void synchronizeSlotToRemote(int i, ItemStack itemstack, Supplier<ItemStack> supplier) {
        if (!this.suppressRemoteUpdates) {
            RemoteSlot remoteslot = this.remoteSlots.get(i);

            if (!remoteslot.matches(itemstack)) {
                remoteslot.force(itemstack);
                if (this.synchronizer != null) {
                    this.synchronizer.sendSlotChange(this, i, (ItemStack) supplier.get());
                }
            }

        }
    }

    private void synchronizeDataSlotToRemote(int i, int j) {
        if (!this.suppressRemoteUpdates) {
            int k = this.remoteDataSlots.getInt(i);

            if (k != j) {
                this.remoteDataSlots.set(i, j);
                if (this.synchronizer != null) {
                    this.synchronizer.sendDataChange(this, i, j);
                }
            }

        }
    }

    private void synchronizeCarriedToRemote() {
        if (!this.suppressRemoteUpdates) {
            ItemStack itemstack = this.getCarried();

            if (!this.remoteCarried.matches(itemstack)) {
                this.remoteCarried.force(itemstack);
                if (this.synchronizer != null) {
                    this.synchronizer.sendCarriedChange(this, itemstack.copy());
                }
            }

        }
    }

    public void setRemoteSlot(int i, ItemStack itemstack) {
        ((RemoteSlot) this.remoteSlots.get(i)).force(itemstack);
    }

    public void setRemoteSlotUnsafe(int i, HashedStack hashedstack) {
        if (i >= 0 && i < this.remoteSlots.size()) {
            ((RemoteSlot) this.remoteSlots.get(i)).receive(hashedstack);
        } else {
            Container.LOGGER.debug("Incorrect slot index: {} available slots: {}", i, this.remoteSlots.size());
        }
    }

    public void setRemoteCarried(HashedStack hashedstack) {
        this.remoteCarried.receive(hashedstack);
    }

    public boolean clickMenuButton(EntityHuman entityhuman, int i) {
        return false;
    }

    public Slot getSlot(int i) {
        return this.slots.get(i);
    }

    public abstract ItemStack quickMoveStack(EntityHuman entityhuman, int i);

    public void setSelectedBundleItemIndex(int i, int j) {
        if (i >= 0 && i < this.slots.size()) {
            ItemStack itemstack = ((Slot) this.slots.get(i)).getItem();

            BundleItem.toggleSelectedItem(itemstack, j);
        }

    }

    public void clicked(int i, int j, InventoryClickType inventoryclicktype, EntityHuman entityhuman) {
        try {
            this.doClick(i, j, inventoryclicktype, entityhuman);
        } catch (Exception exception) {
            CrashReport crashreport = CrashReport.forThrowable(exception, "Container click");
            CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Click info");

            crashreportsystemdetails.setDetail("Menu Type", () -> {
                return this.menuType != null ? BuiltInRegistries.MENU.getKey(this.menuType).toString() : "<no type>";
            });
            crashreportsystemdetails.setDetail("Menu Class", () -> {
                return this.getClass().getCanonicalName();
            });
            crashreportsystemdetails.setDetail("Slot Count", this.slots.size());
            crashreportsystemdetails.setDetail("Slot", i);
            crashreportsystemdetails.setDetail("Button", j);
            crashreportsystemdetails.setDetail("Type", inventoryclicktype);
            throw new ReportedException(crashreport);
        }
    }

    private void doClick(int i, int j, InventoryClickType inventoryclicktype, EntityHuman entityhuman) {
        PlayerInventory playerinventory = entityhuman.getInventory();

        if (inventoryclicktype == InventoryClickType.QUICK_CRAFT) {
            int k = this.quickcraftStatus;

            this.quickcraftStatus = getQuickcraftHeader(j);
            if ((k != 1 || this.quickcraftStatus != 2) && k != this.quickcraftStatus) {
                this.resetQuickCraft();
            } else if (this.getCarried().isEmpty()) {
                this.resetQuickCraft();
            } else if (this.quickcraftStatus == 0) {
                this.quickcraftType = getQuickcraftType(j);
                if (isValidQuickcraftType(this.quickcraftType, entityhuman)) {
                    this.quickcraftStatus = 1;
                    this.quickcraftSlots.clear();
                } else {
                    this.resetQuickCraft();
                }
            } else if (this.quickcraftStatus == 1) {
                Slot slot = this.slots.get(i);
                ItemStack itemstack = this.getCarried();

                if (canItemQuickReplace(slot, itemstack, true) && slot.mayPlace(itemstack) && (this.quickcraftType == 2 || itemstack.getCount() > this.quickcraftSlots.size()) && this.canDragTo(slot)) {
                    this.quickcraftSlots.add(slot);
                }
            } else if (this.quickcraftStatus == 2) {
                if (!this.quickcraftSlots.isEmpty()) {
                    if (false && this.quickcraftSlots.size() == 1) { // CraftBukkit - treat everything as a drag since we are unable to easily call InventoryClickEvent instead
                        int l = ((Slot) this.quickcraftSlots.iterator().next()).index;

                        this.resetQuickCraft();
                        this.doClick(l, this.quickcraftType, InventoryClickType.PICKUP, entityhuman);
                        return;
                    }

                    ItemStack itemstack1 = this.getCarried().copy();

                    if (itemstack1.isEmpty()) {
                        this.resetQuickCraft();
                        return;
                    }

                    int i1 = this.getCarried().getCount();
                    Map<Integer, ItemStack> draggedSlots = new HashMap<Integer, ItemStack>(); // CraftBukkit - Store slots from drag in map (raw slot id -> new stack)

                    for (Slot slot1 : this.quickcraftSlots) {
                        ItemStack itemstack2 = this.getCarried();

                        if (slot1 != null && canItemQuickReplace(slot1, itemstack2, true) && slot1.mayPlace(itemstack2) && (this.quickcraftType == 2 || itemstack2.getCount() >= this.quickcraftSlots.size()) && this.canDragTo(slot1)) {
                            int j1 = slot1.hasItem() ? slot1.getItem().getCount() : 0;
                            int k1 = Math.min(itemstack1.getMaxStackSize(), slot1.getMaxStackSize(itemstack1));
                            int l1 = Math.min(getQuickCraftPlaceCount(this.quickcraftSlots, this.quickcraftType, itemstack1) + j1, k1);

                            i1 -= l1 - j1;
                            // slot1.setByPlayer(itemstack1.copyWithCount(l1));
                            draggedSlots.put(slot1.index, itemstack1.copyWithCount(l1)); // CraftBukkit - Put in map instead of setting
                        }
                    }

                    // CraftBukkit start - InventoryDragEvent
                    InventoryView view = getBukkitView();
                    org.bukkit.inventory.ItemStack newcursor = CraftItemStack.asCraftMirror(itemstack1);
                    newcursor.setAmount(i1);
                    Map<Integer, org.bukkit.inventory.ItemStack> eventmap = new HashMap<Integer, org.bukkit.inventory.ItemStack>();
                    for (Map.Entry<Integer, ItemStack> ditem : draggedSlots.entrySet()) {
                        eventmap.put(ditem.getKey(), CraftItemStack.asBukkitCopy(ditem.getValue()));
                    }

                    // It's essential that we set the cursor to the new value here to prevent item duplication if a plugin closes the inventory.
                    ItemStack oldCursor = this.getCarried();
                    this.setCarried(CraftItemStack.asNMSCopy(newcursor));

                    InventoryDragEvent event = new InventoryDragEvent(view, (newcursor.getType() != org.bukkit.Material.AIR ? newcursor : null), CraftItemStack.asBukkitCopy(oldCursor), this.quickcraftType == 1, eventmap);
                    entityhuman.level().getCraftServer().getPluginManager().callEvent(event);

                    // Whether or not a change was made to the inventory that requires an update.
                    boolean needsUpdate = event.getResult() != Result.DEFAULT;

                    if (event.getResult() != Result.DENY) {
                        for (Map.Entry<Integer, ItemStack> dslot : draggedSlots.entrySet()) {
                            view.setItem(dslot.getKey(), CraftItemStack.asBukkitCopy(dslot.getValue()));
                        }
                        // The only time the carried item will be set to null is if the inventory is closed by the server.
                        // If the inventory is closed by the server, then the cursor items are dropped.  This is why we change the cursor early.
                        if (this.getCarried() != null) {
                            this.setCarried(CraftItemStack.asNMSCopy(event.getCursor()));
                            needsUpdate = true;
                        }
                    } else {
                        this.setCarried(oldCursor);
                    }

                    if (needsUpdate && entityhuman instanceof EntityPlayer) {
                        this.sendAllDataToRemote();
                    }
                    // CraftBukkit end
                }

                this.resetQuickCraft();
            } else {
                this.resetQuickCraft();
            }
        } else if (this.quickcraftStatus != 0) {
            this.resetQuickCraft();
        } else if ((inventoryclicktype == InventoryClickType.PICKUP || inventoryclicktype == InventoryClickType.QUICK_MOVE) && (j == 0 || j == 1)) {
            ClickAction clickaction = j == 0 ? ClickAction.PRIMARY : ClickAction.SECONDARY;

            if (i == -999) {
                if (!this.getCarried().isEmpty()) {
                    if (clickaction == ClickAction.PRIMARY) {
                        // CraftBukkit start
                        ItemStack carried = this.getCarried();
                        this.setCarried(ItemStack.EMPTY);
                        entityhuman.drop(carried, true);
                        // CraftBukkit start
                    } else {
                        entityhuman.drop(this.getCarried().split(1), true);
                    }
                }
            } else if (inventoryclicktype == InventoryClickType.QUICK_MOVE) {
                if (i < 0) {
                    return;
                }

                Slot slot2 = this.slots.get(i);

                if (!slot2.mayPickup(entityhuman)) {
                    return;
                }

                for (ItemStack itemstack3 = this.quickMoveStack(entityhuman, i); !itemstack3.isEmpty() && ItemStack.isSameItem(slot2.getItem(), itemstack3); itemstack3 = this.quickMoveStack(entityhuman, i)) {
                    ;
                }
            } else {
                if (i < 0) {
                    return;
                }

                Slot slot3 = this.slots.get(i);
                ItemStack itemstack4 = slot3.getItem();
                ItemStack itemstack5 = this.getCarried();

                entityhuman.updateTutorialInventoryAction(itemstack5, slot3.getItem(), clickaction);
                if (!this.tryItemClickBehaviourOverride(entityhuman, clickaction, slot3, itemstack4, itemstack5)) {
                    if (itemstack4.isEmpty()) {
                        if (!itemstack5.isEmpty()) {
                            int i2 = clickaction == ClickAction.PRIMARY ? itemstack5.getCount() : 1;

                            this.setCarried(slot3.safeInsert(itemstack5, i2));
                        }
                    } else if (slot3.mayPickup(entityhuman)) {
                        if (itemstack5.isEmpty()) {
                            int j2 = clickaction == ClickAction.PRIMARY ? itemstack4.getCount() : (itemstack4.getCount() + 1) / 2;
                            Optional<ItemStack> optional = slot3.tryRemove(j2, Integer.MAX_VALUE, entityhuman);

                            optional.ifPresent((itemstack6) -> {
                                this.setCarried(itemstack6);
                                slot3.onTake(entityhuman, itemstack6);
                            });
                        } else if (slot3.mayPlace(itemstack5)) {
                            if (ItemStack.isSameItemSameComponents(itemstack4, itemstack5)) {
                                int k2 = clickaction == ClickAction.PRIMARY ? itemstack5.getCount() : 1;

                                this.setCarried(slot3.safeInsert(itemstack5, k2));
                            } else if (itemstack5.getCount() <= slot3.getMaxStackSize(itemstack5)) {
                                this.setCarried(itemstack4);
                                slot3.setByPlayer(itemstack5);
                            }
                        } else if (ItemStack.isSameItemSameComponents(itemstack4, itemstack5)) {
                            Optional<ItemStack> optional1 = slot3.tryRemove(itemstack4.getCount(), itemstack5.getMaxStackSize() - itemstack5.getCount(), entityhuman);

                            optional1.ifPresent((itemstack6) -> {
                                itemstack5.grow(itemstack6.getCount());
                                slot3.onTake(entityhuman, itemstack6);
                            });
                        }
                    }
                }

                slot3.setChanged();
                // CraftBukkit start - Make sure the client has the right slot contents
                if (entityhuman instanceof EntityPlayer && slot3.getMaxStackSize() != IInventory.MAX_STACK) {
                    ((EntityPlayer) entityhuman).connection.send(new PacketPlayOutSetSlot(this.containerId, this.incrementStateId(), slot3.index, slot3.getItem()));
                    // Updating a crafting inventory makes the client reset the result slot, have to send it again
                    if (this.getBukkitView().getType() == InventoryType.WORKBENCH || this.getBukkitView().getType() == InventoryType.CRAFTING) {
                        ((EntityPlayer) entityhuman).connection.send(new PacketPlayOutSetSlot(this.containerId, this.incrementStateId(), 0, this.getSlot(0).getItem()));
                    }
                }
                // CraftBukkit end
            }
        } else if (inventoryclicktype == InventoryClickType.SWAP && (j >= 0 && j < 9 || j == 40)) {
            ItemStack itemstack6 = playerinventory.getItem(j);
            Slot slot4 = this.slots.get(i);
            ItemStack itemstack7 = slot4.getItem();

            if (!itemstack6.isEmpty() || !itemstack7.isEmpty()) {
                if (itemstack6.isEmpty()) {
                    if (slot4.mayPickup(entityhuman)) {
                        playerinventory.setItem(j, itemstack7);
                        slot4.onSwapCraft(itemstack7.getCount());
                        slot4.setByPlayer(ItemStack.EMPTY);
                        slot4.onTake(entityhuman, itemstack7);
                    }
                } else if (itemstack7.isEmpty()) {
                    if (slot4.mayPlace(itemstack6)) {
                        int l2 = slot4.getMaxStackSize(itemstack6);

                        if (itemstack6.getCount() > l2) {
                            slot4.setByPlayer(itemstack6.split(l2));
                        } else {
                            playerinventory.setItem(j, ItemStack.EMPTY);
                            slot4.setByPlayer(itemstack6);
                        }
                    }
                } else if (slot4.mayPickup(entityhuman) && slot4.mayPlace(itemstack6)) {
                    int i3 = slot4.getMaxStackSize(itemstack6);

                    if (itemstack6.getCount() > i3) {
                        slot4.setByPlayer(itemstack6.split(i3));
                        slot4.onTake(entityhuman, itemstack7);
                        if (!playerinventory.add(itemstack7)) {
                            entityhuman.drop(itemstack7, true);
                        }
                    } else {
                        playerinventory.setItem(j, itemstack7);
                        slot4.setByPlayer(itemstack6);
                        slot4.onTake(entityhuman, itemstack7);
                    }
                }
            }
        } else if (inventoryclicktype == InventoryClickType.CLONE && entityhuman.hasInfiniteMaterials() && this.getCarried().isEmpty() && i >= 0) {
            Slot slot5 = this.slots.get(i);

            if (slot5.hasItem()) {
                ItemStack itemstack8 = slot5.getItem();

                this.setCarried(itemstack8.copyWithCount(itemstack8.getMaxStackSize()));
            }
        } else if (inventoryclicktype == InventoryClickType.THROW && this.getCarried().isEmpty() && i >= 0) {
            Slot slot6 = this.slots.get(i);
            int j3 = j == 0 ? 1 : slot6.getItem().getCount();

            if (!entityhuman.canDropItems()) {
                return;
            }

            ItemStack itemstack9 = slot6.safeTake(j3, Integer.MAX_VALUE, entityhuman);

            entityhuman.drop(itemstack9, true);
            entityhuman.handleCreativeModeItemDrop(itemstack9);
            if (j == 1) {
                while (!itemstack9.isEmpty() && ItemStack.isSameItem(slot6.getItem(), itemstack9)) {
                    if (!entityhuman.canDropItems()) {
                        return;
                    }

                    itemstack9 = slot6.safeTake(j3, Integer.MAX_VALUE, entityhuman);
                    // CraftBukkit start - SPIGOT-8010: break loop
                    if (entityhuman.drop(itemstack9, true) == null) {
                        break;
                    }
                    // CraftBukkit end
                    entityhuman.handleCreativeModeItemDrop(itemstack9);
                }
            }
        } else if (inventoryclicktype == InventoryClickType.PICKUP_ALL && i >= 0) {
            Slot slot7 = this.slots.get(i);
            ItemStack itemstack10 = this.getCarried();

            if (!itemstack10.isEmpty() && (!slot7.hasItem() || !slot7.mayPickup(entityhuman))) {
                int k3 = j == 0 ? 0 : this.slots.size() - 1;
                int l3 = j == 0 ? 1 : -1;

                for (int i4 = 0; i4 < 2; ++i4) {
                    for (int j4 = k3; j4 >= 0 && j4 < this.slots.size() && itemstack10.getCount() < itemstack10.getMaxStackSize(); j4 += l3) {
                        Slot slot8 = this.slots.get(j4);

                        if (slot8.hasItem() && canItemQuickReplace(slot8, itemstack10, true) && slot8.mayPickup(entityhuman) && this.canTakeItemForPickAll(itemstack10, slot8)) {
                            ItemStack itemstack11 = slot8.getItem();

                            if (i4 != 0 || itemstack11.getCount() != itemstack11.getMaxStackSize()) {
                                ItemStack itemstack12 = slot8.safeTake(itemstack11.getCount(), itemstack10.getMaxStackSize() - itemstack10.getCount(), entityhuman);

                                itemstack10.grow(itemstack12.getCount());
                            }
                        }
                    }
                }
            }
        }

    }

    private boolean tryItemClickBehaviourOverride(EntityHuman entityhuman, ClickAction clickaction, Slot slot, ItemStack itemstack, ItemStack itemstack1) {
        FeatureFlagSet featureflagset = entityhuman.level().enabledFeatures();

        return itemstack1.isItemEnabled(featureflagset) && itemstack1.overrideStackedOnOther(slot, clickaction, entityhuman) ? true : itemstack.isItemEnabled(featureflagset) && itemstack.overrideOtherStackedOnMe(itemstack1, slot, clickaction, entityhuman, this.createCarriedSlotAccess());
    }

    private SlotAccess createCarriedSlotAccess() {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return Container.this.getCarried();
            }

            @Override
            public boolean set(ItemStack itemstack) {
                Container.this.setCarried(itemstack);
                return true;
            }
        };
    }

    public boolean canTakeItemForPickAll(ItemStack itemstack, Slot slot) {
        return true;
    }

    public void removed(EntityHuman entityhuman) {
        if (entityhuman instanceof EntityPlayer) {
            ItemStack itemstack = this.getCarried();

            if (!itemstack.isEmpty()) {
                this.setCarried(ItemStack.EMPTY); // CraftBukkit - SPIGOT-4556 - from below
                dropOrPlaceInInventory(entityhuman, itemstack);
                // this.setCarried(ItemStack.EMPTY); // CraftBukkit - moved up
            }

        }
    }

    private static void dropOrPlaceInInventory(EntityHuman entityhuman, ItemStack itemstack) {
        boolean flag;
        boolean flag1;
        label27:
        {
            flag = entityhuman.isRemoved() && entityhuman.getRemovalReason() != Entity.RemovalReason.CHANGED_DIMENSION;
            if (entityhuman instanceof EntityPlayer entityplayer) {
                if (entityplayer.hasDisconnected()) {
                    flag1 = true;
                    break label27;
                }
            }

            flag1 = false;
        }

        boolean flag2 = flag1;

        if (!flag && !flag2) {
            if (entityhuman instanceof EntityPlayer) {
                entityhuman.getInventory().placeItemBackInInventory(itemstack);
            }
        } else {
            entityhuman.drop(itemstack, false);
        }

    }

    protected void clearContainer(EntityHuman entityhuman, IInventory iinventory) {
        for (int i = 0; i < iinventory.getContainerSize(); ++i) {
            dropOrPlaceInInventory(entityhuman, iinventory.removeItemNoUpdate(i));
        }

    }

    public void slotsChanged(IInventory iinventory) {
        this.broadcastChanges();
    }

    public void setItem(int i, int j, ItemStack itemstack) {
        this.getSlot(i).set(itemstack);
        this.stateId = j;
    }

    public void initializeContents(int i, List<ItemStack> list, ItemStack itemstack) {
        for (int j = 0; j < list.size(); ++j) {
            this.getSlot(j).set((ItemStack) list.get(j));
        }

        this.carried = itemstack;
        this.stateId = i;
    }

    public void setData(int i, int j) {
        ((ContainerProperty) this.dataSlots.get(i)).set(j);
    }

    public abstract boolean stillValid(EntityHuman entityhuman);

    protected boolean moveItemStackTo(ItemStack itemstack, int i, int j, boolean flag) {
        boolean flag1 = false;
        int k = i;

        if (flag) {
            k = j - 1;
        }

        if (itemstack.isStackable()) {
            while (!itemstack.isEmpty()) {
                if (flag) {
                    if (k < i) {
                        break;
                    }
                } else if (k >= j) {
                    break;
                }

                Slot slot = this.slots.get(k);
                ItemStack itemstack1 = slot.getItem();

                if (!itemstack1.isEmpty() && ItemStack.isSameItemSameComponents(itemstack, itemstack1)) {
                    int l = itemstack1.getCount() + itemstack.getCount();
                    int i1 = slot.getMaxStackSize(itemstack1);

                    if (l <= i1) {
                        itemstack.setCount(0);
                        itemstack1.setCount(l);
                        slot.setChanged();
                        flag1 = true;
                    } else if (itemstack1.getCount() < i1) {
                        itemstack.shrink(i1 - itemstack1.getCount());
                        itemstack1.setCount(i1);
                        slot.setChanged();
                        flag1 = true;
                    }
                }

                if (flag) {
                    --k;
                } else {
                    ++k;
                }
            }
        }

        if (!itemstack.isEmpty()) {
            if (flag) {
                k = j - 1;
            } else {
                k = i;
            }

            while (true) {
                if (flag) {
                    if (k < i) {
                        break;
                    }
                } else if (k >= j) {
                    break;
                }

                Slot slot1 = this.slots.get(k);
                ItemStack itemstack2 = slot1.getItem();

                if (itemstack2.isEmpty() && slot1.mayPlace(itemstack)) {
                    int j1 = slot1.getMaxStackSize(itemstack);

                    slot1.setByPlayer(itemstack.split(Math.min(itemstack.getCount(), j1)));
                    slot1.setChanged();
                    flag1 = true;
                    break;
                }

                if (flag) {
                    --k;
                } else {
                    ++k;
                }
            }
        }

        return flag1;
    }

    public static int getQuickcraftType(int i) {
        return i >> 2 & 3;
    }

    public static int getQuickcraftHeader(int i) {
        return i & 3;
    }

    public static int getQuickcraftMask(int i, int j) {
        return i & 3 | (j & 3) << 2;
    }

    public static boolean isValidQuickcraftType(int i, EntityHuman entityhuman) {
        return i == 0 ? true : (i == 1 ? true : i == 2 && entityhuman.hasInfiniteMaterials());
    }

    protected void resetQuickCraft() {
        this.quickcraftStatus = 0;
        this.quickcraftSlots.clear();
    }

    public static boolean canItemQuickReplace(@Nullable Slot slot, ItemStack itemstack, boolean flag) {
        boolean flag1 = slot == null || !slot.hasItem();

        return !flag1 && ItemStack.isSameItemSameComponents(itemstack, slot.getItem()) ? slot.getItem().getCount() + (flag ? 0 : itemstack.getCount()) <= itemstack.getMaxStackSize() : flag1;
    }

    public static int getQuickCraftPlaceCount(Set<Slot> set, int i, ItemStack itemstack) {
        int j;

        switch (i) {
            case 0:
                j = MathHelper.floor((float) itemstack.getCount() / (float) set.size());
                break;
            case 1:
                j = 1;
                break;
            case 2:
                j = itemstack.getMaxStackSize();
                break;
            default:
                j = itemstack.getCount();
        }

        return j;
    }

    public boolean canDragTo(Slot slot) {
        return true;
    }

    public static int getRedstoneSignalFromBlockEntity(@Nullable TileEntity tileentity) {
        return tileentity instanceof IInventory ? getRedstoneSignalFromContainer((IInventory) tileentity) : 0;
    }

    public static int getRedstoneSignalFromContainer(@Nullable IInventory iinventory) {
        if (iinventory == null) {
            return 0;
        } else {
            float f = 0.0F;

            for (int i = 0; i < iinventory.getContainerSize(); ++i) {
                ItemStack itemstack = iinventory.getItem(i);

                if (!itemstack.isEmpty()) {
                    f += (float) itemstack.getCount() / (float) iinventory.getMaxStackSize(itemstack);
                }
            }

            f /= (float) iinventory.getContainerSize();
            return MathHelper.lerpDiscrete(f, 0, 15);
        }
    }

    public void setCarried(ItemStack itemstack) {
        this.carried = itemstack;
    }

    public ItemStack getCarried() {
        // CraftBukkit start
        if (this.carried.isEmpty()) {
            this.setCarried(ItemStack.EMPTY);
        }
        // CraftBukkit end
        return this.carried;
    }

    public void suppressRemoteUpdates() {
        this.suppressRemoteUpdates = true;
    }

    public void resumeRemoteUpdates() {
        this.suppressRemoteUpdates = false;
    }

    public void transferState(Container container) {
        Table<IInventory, Integer, Integer> table = HashBasedTable.create();

        for (int i = 0; i < container.slots.size(); ++i) {
            Slot slot = container.slots.get(i);

            table.put(slot.container, slot.getContainerSlot(), i);
        }

        for (int j = 0; j < this.slots.size(); ++j) {
            Slot slot1 = this.slots.get(j);
            Integer integer = (Integer) table.get(slot1.container, slot1.getContainerSlot());

            if (integer != null) {
                this.lastSlots.set(j, container.lastSlots.get(integer));
                RemoteSlot remoteslot = container.remoteSlots.get(integer);
                RemoteSlot remoteslot1 = this.remoteSlots.get(j);

                if (remoteslot instanceof RemoteSlot.a) {
                    RemoteSlot.a remoteslot_a = (RemoteSlot.a) remoteslot;

                    if (remoteslot1 instanceof RemoteSlot.a) {
                        RemoteSlot.a remoteslot_a1 = (RemoteSlot.a) remoteslot1;

                        remoteslot_a1.copyFrom(remoteslot_a);
                    }
                }
            }
        }

    }

    public OptionalInt findSlot(IInventory iinventory, int i) {
        for (int j = 0; j < this.slots.size(); ++j) {
            Slot slot = this.slots.get(j);

            if (slot.container == iinventory && i == slot.getContainerSlot()) {
                return OptionalInt.of(j);
            }
        }

        return OptionalInt.empty();
    }

    public int getStateId() {
        return this.stateId;
    }

    public int incrementStateId() {
        this.stateId = this.stateId + 1 & 32767;
        return this.stateId;
    }
}

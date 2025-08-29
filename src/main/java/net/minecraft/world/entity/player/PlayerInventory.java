package net.minecraft.world.entity.player;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportSystemDetails;
import net.minecraft.ReportedException;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerUtil;
import net.minecraft.world.IInventory;
import net.minecraft.world.INamableTileEntity;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// CraftBukkit start
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.entity.HumanEntity;
// CraftBukkit end

public class PlayerInventory implements IInventory, INamableTileEntity {

    public static final int POP_TIME_DURATION = 5;
    public static final int INVENTORY_SIZE = 36;
    public static final int SELECTION_SIZE = 9;
    public static final int SLOT_OFFHAND = 40;
    public static final int NOT_FOUND_INDEX = -1;
    public static final Int2ObjectMap<EnumItemSlot> EQUIPMENT_SLOT_MAPPING = new Int2ObjectArrayMap(Map.of(EnumItemSlot.FEET.getIndex(36), EnumItemSlot.FEET, EnumItemSlot.LEGS.getIndex(36), EnumItemSlot.LEGS, EnumItemSlot.CHEST.getIndex(36), EnumItemSlot.CHEST, EnumItemSlot.HEAD.getIndex(36), EnumItemSlot.HEAD, 40, EnumItemSlot.OFFHAND));
    private final NonNullList<ItemStack> items;
    private int selected;
    public final EntityHuman player;
    private final EntityEquipment equipment;
    private int timesChanged;

    // CraftBukkit start - add fields and methods
    public List<HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = MAX_STACK;

    @Override
    public List<ItemStack> getContents() {
        List<ItemStack> combined = new ArrayList<>(getContainerSize());
        for (net.minecraft.world.item.ItemStack sub : this) {
            combined.add(sub);
        }

        return combined;
    }

    public List<ItemStack> getArmorContents() {
        List<ItemStack> combined = new ArrayList<>(SLOT_OFFHAND - INVENTORY_SIZE);
        for (int i = INVENTORY_SIZE; i < SLOT_OFFHAND; i++) {
            combined.add(getItem(i));
        }

        return combined;
    }

    @Override
    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    @Override
    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    @Override
    public List<HumanEntity> getViewers() {
        return transaction;
    }

    @Override
    public org.bukkit.inventory.InventoryHolder getOwner() {
        return this.player.getBukkitEntity();
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    @Override
    public void setMaxStackSize(int size) {
        maxStack = size;
    }

    @Override
    public Location getLocation() {
        return player.getBukkitEntity().getLocation();
    }
    // CraftBukkit end

    public PlayerInventory(EntityHuman entityhuman, EntityEquipment entityequipment) {
        this.items = NonNullList.<ItemStack>withSize(36, ItemStack.EMPTY);
        this.player = entityhuman;
        this.equipment = entityequipment;
    }

    public int getSelectedSlot() {
        return this.selected;
    }

    public void setSelectedSlot(int i) {
        if (!isHotbarSlot(i)) {
            throw new IllegalArgumentException("Invalid selected slot");
        } else {
            this.selected = i;
        }
    }

    public ItemStack getSelectedItem() {
        return this.items.get(this.selected);
    }

    public ItemStack setSelectedItem(ItemStack itemstack) {
        return this.items.set(this.selected, itemstack);
    }

    public static int getSelectionSize() {
        return 9;
    }

    public NonNullList<ItemStack> getNonEquipmentItems() {
        return this.items;
    }

    private boolean hasRemainingSpaceForItem(ItemStack itemstack, ItemStack itemstack1) {
        return !itemstack.isEmpty() && ItemStack.isSameItemSameComponents(itemstack, itemstack1) && itemstack.isStackable() && itemstack.getCount() < this.getMaxStackSize(itemstack);
    }

    // CraftBukkit start - Watch method above! :D
    public int canHold(ItemStack itemstack) {
        int remains = itemstack.getCount();
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack1 = this.getItem(i);
            if (itemstack1.isEmpty()) return itemstack.getCount();

            if (this.hasRemainingSpaceForItem(itemstack1, itemstack)) {
                remains -= (itemstack1.getMaxStackSize() < this.getMaxStackSize() ? itemstack1.getMaxStackSize() : this.getMaxStackSize()) - itemstack1.getCount();
            }
            if (remains <= 0) return itemstack.getCount();
        }
        ItemStack offhandItemStack = this.equipment.get(EnumItemSlot.OFFHAND);
        if (this.hasRemainingSpaceForItem(offhandItemStack, itemstack)) {
            remains -= (offhandItemStack.getMaxStackSize() < this.getMaxStackSize() ? offhandItemStack.getMaxStackSize() : this.getMaxStackSize()) - offhandItemStack.getCount();
        }
        if (remains <= 0) return itemstack.getCount();

        return itemstack.getCount() - remains;
    }
    // CraftBukkit end

    public int getFreeSlot() {
        for (int i = 0; i < this.items.size(); ++i) {
            if (((ItemStack) this.items.get(i)).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    public void addAndPickItem(ItemStack itemstack) {
        this.setSelectedSlot(this.getSuitableHotbarSlot());
        if (!((ItemStack) this.items.get(this.selected)).isEmpty()) {
            int i = this.getFreeSlot();

            if (i != -1) {
                this.items.set(i, this.items.get(this.selected));
            }
        }

        this.items.set(this.selected, itemstack);
    }

    public void pickSlot(int i) {
        this.setSelectedSlot(this.getSuitableHotbarSlot());
        ItemStack itemstack = this.items.get(this.selected);

        this.items.set(this.selected, this.items.get(i));
        this.items.set(i, itemstack);
    }

    public static boolean isHotbarSlot(int i) {
        return i >= 0 && i < 9;
    }

    public int findSlotMatchingItem(ItemStack itemstack) {
        for (int i = 0; i < this.items.size(); ++i) {
            if (!((ItemStack) this.items.get(i)).isEmpty() && ItemStack.isSameItemSameComponents(itemstack, this.items.get(i))) {
                return i;
            }
        }

        return -1;
    }

    public static boolean isUsableForCrafting(ItemStack itemstack) {
        return !itemstack.isDamaged() && !itemstack.isEnchanted() && !itemstack.has(DataComponents.CUSTOM_NAME);
    }

    public int findSlotMatchingCraftingIngredient(Holder<Item> holder, ItemStack itemstack) {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack1 = this.items.get(i);

            if (!itemstack1.isEmpty() && itemstack1.is(holder) && isUsableForCrafting(itemstack1) && (itemstack.isEmpty() || ItemStack.isSameItemSameComponents(itemstack, itemstack1))) {
                return i;
            }
        }

        return -1;
    }

    public int getSuitableHotbarSlot() {
        for (int i = 0; i < 9; ++i) {
            int j = (this.selected + i) % 9;

            if (((ItemStack) this.items.get(j)).isEmpty()) {
                return j;
            }
        }

        for (int k = 0; k < 9; ++k) {
            int l = (this.selected + k) % 9;

            if (!((ItemStack) this.items.get(l)).isEnchanted()) {
                return l;
            }
        }

        return this.selected;
    }

    public int clearOrCountMatchingItems(Predicate<ItemStack> predicate, int i, IInventory iinventory) {
        int j = 0;
        boolean flag = i == 0;

        j += ContainerUtil.clearOrCountMatchingItems((IInventory) this, predicate, i - j, flag);
        j += ContainerUtil.clearOrCountMatchingItems(iinventory, predicate, i - j, flag);
        ItemStack itemstack = this.player.containerMenu.getCarried();

        j += ContainerUtil.clearOrCountMatchingItems(itemstack, predicate, i - j, flag);
        if (itemstack.isEmpty()) {
            this.player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        return j;
    }

    private int addResource(ItemStack itemstack) {
        int i = this.getSlotWithRemainingSpace(itemstack);

        if (i == -1) {
            i = this.getFreeSlot();
        }

        return i == -1 ? itemstack.getCount() : this.addResource(i, itemstack);
    }

    private int addResource(int i, ItemStack itemstack) {
        int j = itemstack.getCount();
        ItemStack itemstack1 = this.getItem(i);

        if (itemstack1.isEmpty()) {
            itemstack1 = itemstack.copyWithCount(0);
            this.setItem(i, itemstack1);
        }

        int k = this.getMaxStackSize(itemstack1) - itemstack1.getCount();
        int l = Math.min(j, k);

        if (l == 0) {
            return j;
        } else {
            j -= l;
            itemstack1.grow(l);
            itemstack1.setPopTime(5);
            return j;
        }
    }

    public int getSlotWithRemainingSpace(ItemStack itemstack) {
        if (this.hasRemainingSpaceForItem(this.getItem(this.selected), itemstack)) {
            return this.selected;
        } else if (this.hasRemainingSpaceForItem(this.getItem(40), itemstack)) {
            return 40;
        } else {
            for (int i = 0; i < this.items.size(); ++i) {
                if (this.hasRemainingSpaceForItem(this.items.get(i), itemstack)) {
                    return i;
                }
            }

            return -1;
        }
    }

    public void tick() {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack = this.getItem(i);

            if (!itemstack.isEmpty()) {
                itemstack.inventoryTick(this.player.level(), this.player, i == this.selected ? EnumItemSlot.MAINHAND : null);
            }
        }

    }

    public boolean add(ItemStack itemstack) {
        return this.add(-1, itemstack);
    }

    public boolean add(int i, ItemStack itemstack) {
        if (itemstack.isEmpty()) {
            return false;
        } else {
            try {
                if (itemstack.isDamaged()) {
                    if (i == -1) {
                        i = this.getFreeSlot();
                    }

                    if (i >= 0) {
                        this.items.set(i, itemstack.copyAndClear());
                        ((ItemStack) this.items.get(i)).setPopTime(5);
                        return true;
                    } else if (this.player.hasInfiniteMaterials()) {
                        itemstack.setCount(0);
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    int j;

                    do {
                        j = itemstack.getCount();
                        if (i == -1) {
                            itemstack.setCount(this.addResource(itemstack));
                        } else {
                            itemstack.setCount(this.addResource(i, itemstack));
                        }
                    } while (!itemstack.isEmpty() && itemstack.getCount() < j);

                    if (itemstack.getCount() == j && this.player.hasInfiniteMaterials()) {
                        itemstack.setCount(0);
                        return true;
                    } else {
                        return itemstack.getCount() < j;
                    }
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.forThrowable(throwable, "Adding item to inventory");
                CrashReportSystemDetails crashreportsystemdetails = crashreport.addCategory("Item being added");

                crashreportsystemdetails.setDetail("Item ID", Item.getId(itemstack.getItem()));
                crashreportsystemdetails.setDetail("Item data", itemstack.getDamageValue());
                crashreportsystemdetails.setDetail("Item name", () -> {
                    return itemstack.getHoverName().getString();
                });
                throw new ReportedException(crashreport);
            }
        }
    }

    public void placeItemBackInInventory(ItemStack itemstack) {
        this.placeItemBackInInventory(itemstack, true);
    }

    public void placeItemBackInInventory(ItemStack itemstack, boolean flag) {
        while (true) {
            if (!itemstack.isEmpty()) {
                int i = this.getSlotWithRemainingSpace(itemstack);

                if (i == -1) {
                    i = this.getFreeSlot();
                }

                if (i != -1) {
                    int j = itemstack.getMaxStackSize() - this.getItem(i).getCount();

                    if (!this.add(i, itemstack.split(j)) || !flag) {
                        continue;
                    }

                    EntityHuman entityhuman = this.player;

                    if (entityhuman instanceof EntityPlayer) {
                        EntityPlayer entityplayer = (EntityPlayer) entityhuman;

                        entityplayer.connection.send(this.createInventoryUpdatePacket(i));
                    }
                    continue;
                }

                this.player.drop(itemstack, false);
            }

            return;
        }
    }

    public ClientboundSetPlayerInventoryPacket createInventoryUpdatePacket(int i) {
        return new ClientboundSetPlayerInventoryPacket(i, this.getItem(i).copy());
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        if (i < this.items.size()) {
            return ContainerUtil.removeItem(this.items, i, j);
        } else {
            EnumItemSlot enumitemslot = (EnumItemSlot) PlayerInventory.EQUIPMENT_SLOT_MAPPING.get(i);

            if (enumitemslot != null) {
                ItemStack itemstack = this.equipment.get(enumitemslot);

                if (!itemstack.isEmpty()) {
                    return itemstack.split(j);
                }
            }

            return ItemStack.EMPTY;
        }
    }

    public void removeItem(ItemStack itemstack) {
        for (int i = 0; i < this.items.size(); ++i) {
            if (this.items.get(i) == itemstack) {
                this.items.set(i, ItemStack.EMPTY);
                return;
            }
        }

        ObjectIterator objectiterator = PlayerInventory.EQUIPMENT_SLOT_MAPPING.values().iterator();

        while (objectiterator.hasNext()) {
            EnumItemSlot enumitemslot = (EnumItemSlot) objectiterator.next();
            ItemStack itemstack1 = this.equipment.get(enumitemslot);

            if (itemstack1 == itemstack) {
                this.equipment.set(enumitemslot, ItemStack.EMPTY);
                return;
            }
        }

    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        if (i < this.items.size()) {
            ItemStack itemstack = this.items.get(i);

            this.items.set(i, ItemStack.EMPTY);
            return itemstack;
        } else {
            EnumItemSlot enumitemslot = (EnumItemSlot) PlayerInventory.EQUIPMENT_SLOT_MAPPING.get(i);

            return enumitemslot != null ? this.equipment.set(enumitemslot, ItemStack.EMPTY) : ItemStack.EMPTY;
        }
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        if (i < this.items.size()) {
            this.items.set(i, itemstack);
        }

        EnumItemSlot enumitemslot = (EnumItemSlot) PlayerInventory.EQUIPMENT_SLOT_MAPPING.get(i);

        if (enumitemslot != null) {
            this.equipment.set(enumitemslot, itemstack);
        }

    }

    public NBTTagList save(NBTTagList nbttaglist) {
        for (int i = 0; i < this.items.size(); ++i) {
            if (!((ItemStack) this.items.get(i)).isEmpty()) {
                NBTTagCompound nbttagcompound = new NBTTagCompound();

                nbttagcompound.putByte("Slot", (byte) i);
                nbttaglist.add(((ItemStack) this.items.get(i)).save(this.player.registryAccess(), nbttagcompound));
            }
        }

        return nbttaglist;
    }

    public void load(NBTTagList nbttaglist) {
        this.items.clear();

        for (int i = 0; i < nbttaglist.size(); ++i) {
            NBTTagCompound nbttagcompound = nbttaglist.getCompoundOrEmpty(i);
            int j = nbttagcompound.getByteOr("Slot", (byte) 0) & 255;
            ItemStack itemstack = (ItemStack) ItemStack.parse(this.player.registryAccess(), nbttagcompound).orElse(ItemStack.EMPTY);

            if (j < this.items.size()) {
                this.setItem(j, itemstack);
            }
        }

    }

    @Override
    public int getContainerSize() {
        return this.items.size() + PlayerInventory.EQUIPMENT_SLOT_MAPPING.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        ObjectIterator objectiterator = PlayerInventory.EQUIPMENT_SLOT_MAPPING.values().iterator();

        while (objectiterator.hasNext()) {
            EnumItemSlot enumitemslot = (EnumItemSlot) objectiterator.next();

            if (!this.equipment.get(enumitemslot).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        if (i < this.items.size()) {
            return this.items.get(i);
        } else {
            EnumItemSlot enumitemslot = (EnumItemSlot) PlayerInventory.EQUIPMENT_SLOT_MAPPING.get(i);

            return enumitemslot != null ? this.equipment.get(enumitemslot) : ItemStack.EMPTY;
        }
    }

    @Override
    public IChatBaseComponent getName() {
        return IChatBaseComponent.translatable("container.inventory");
    }

    public void dropAll() {
        for (int i = 0; i < this.items.size(); ++i) {
            ItemStack itemstack = this.items.get(i);

            if (!itemstack.isEmpty()) {
                this.player.drop(itemstack, true, false);
                this.items.set(i, ItemStack.EMPTY);
            }
        }

        this.equipment.dropAll(this.player);
    }

    @Override
    public void setChanged() {
        ++this.timesChanged;
    }

    public int getTimesChanged() {
        return this.timesChanged;
    }

    @Override
    public boolean stillValid(EntityHuman entityhuman) {
        return true;
    }

    public boolean contains(ItemStack itemstack) {
        for (ItemStack itemstack1 : this) {
            if (!itemstack1.isEmpty() && ItemStack.isSameItemSameComponents(itemstack1, itemstack)) {
                return true;
            }
        }

        return false;
    }

    public boolean contains(TagKey<Item> tagkey) {
        for (ItemStack itemstack : this) {
            if (!itemstack.isEmpty() && itemstack.is(tagkey)) {
                return true;
            }
        }

        return false;
    }

    public boolean contains(Predicate<ItemStack> predicate) {
        for (ItemStack itemstack : this) {
            if (predicate.test(itemstack)) {
                return true;
            }
        }

        return false;
    }

    public void replaceWith(PlayerInventory playerinventory) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            this.setItem(i, playerinventory.getItem(i));
        }

        this.setSelectedSlot(playerinventory.getSelectedSlot());
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.equipment.clear();
    }

    public void fillStackedContents(StackedItemContents stackeditemcontents) {
        for (ItemStack itemstack : this.items) {
            stackeditemcontents.accountSimpleStack(itemstack);
        }

    }

    public ItemStack removeFromSelected(boolean flag) {
        ItemStack itemstack = this.getSelectedItem();

        return itemstack.isEmpty() ? ItemStack.EMPTY : this.removeItem(this.selected, flag ? itemstack.getCount() : 1);
    }
}

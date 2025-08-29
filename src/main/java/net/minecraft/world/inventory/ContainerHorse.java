package net.minecraft.world.inventory;

import net.minecraft.resources.MinecraftKey;
import net.minecraft.tags.TagsEntity;
import net.minecraft.world.IInventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EnumItemSlot;
import net.minecraft.world.entity.animal.horse.EntityHorseAbstract;
import net.minecraft.world.entity.animal.horse.EntityLlama;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.item.ItemStack;

// CraftBukkit start
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.inventory.InventoryView;
// CraftBukkit end

public class ContainerHorse extends Container {

    private static final MinecraftKey SADDLE_SLOT_SPRITE = MinecraftKey.withDefaultNamespace("container/slot/saddle");
    private static final MinecraftKey LLAMA_ARMOR_SLOT_SPRITE = MinecraftKey.withDefaultNamespace("container/slot/llama_armor");
    private static final MinecraftKey ARMOR_SLOT_SPRITE = MinecraftKey.withDefaultNamespace("container/slot/horse_armor");
    private final IInventory horseContainer;
    private final EntityHorseAbstract horse;
    private static final int SLOT_SADDLE = 0;
    private static final int SLOT_BODY_ARMOR = 1;
    private static final int SLOT_HORSE_INVENTORY_START = 2;

    // CraftBukkit start
    org.bukkit.craftbukkit.inventory.CraftInventoryView bukkitEntity;
    PlayerInventory player;

    @Override
    public InventoryView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        return bukkitEntity = new CraftInventoryView(player.player.getBukkitEntity(), horseContainer.getOwner().getInventory(), this);
    }

    public ContainerHorse(int i, PlayerInventory playerinventory, IInventory iinventory, final EntityHorseAbstract entityhorseabstract, int j) {
        super((Containers) null, i);
        player = playerinventory;
        // CraftBukkit end
        this.horseContainer = iinventory;
        this.horse = entityhorseabstract;
        iinventory.startOpen(playerinventory.player);
        IInventory iinventory1 = entityhorseabstract.createEquipmentSlotContainer(EnumItemSlot.SADDLE);

        this.addSlot(new ArmorSlot(iinventory1, entityhorseabstract, EnumItemSlot.SADDLE, 0, 8, 18, ContainerHorse.SADDLE_SLOT_SPRITE) {
            @Override
            public boolean isActive() {
                return entityhorseabstract.canUseSlot(EnumItemSlot.SADDLE) && entityhorseabstract.getType().is(TagsEntity.CAN_EQUIP_SADDLE);
            }
        });
        final boolean flag = entityhorseabstract instanceof EntityLlama;
        MinecraftKey minecraftkey = flag ? ContainerHorse.LLAMA_ARMOR_SLOT_SPRITE : ContainerHorse.ARMOR_SLOT_SPRITE;
        IInventory iinventory2 = entityhorseabstract.createEquipmentSlotContainer(EnumItemSlot.BODY);

        this.addSlot(new ArmorSlot(iinventory2, entityhorseabstract, EnumItemSlot.BODY, 0, 8, 36, minecraftkey) {
            @Override
            public boolean isActive() {
                return entityhorseabstract.canUseSlot(EnumItemSlot.BODY) && (entityhorseabstract.getType().is(TagsEntity.CAN_WEAR_HORSE_ARMOR) || flag);
            }
        });
        if (j > 0) {
            for (int k = 0; k < 3; ++k) {
                for (int l = 0; l < j; ++l) {
                    this.addSlot(new Slot(iinventory, l + k * j, 80 + l * 18, 18 + k * 18));
                }
            }
        }

        this.addStandardInventorySlots(playerinventory, 8, 84);
    }

    @Override
    public boolean stillValid(EntityHuman entityhuman) {
        return !this.horse.hasInventoryChanged(this.horseContainer) && this.horseContainer.stillValid(entityhuman) && this.horse.isAlive() && entityhuman.canInteractWithEntity((Entity) this.horse, 4.0D);
    }

    @Override
    public ItemStack quickMoveStack(EntityHuman entityhuman, int i) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(i);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();

            itemstack = itemstack1.copy();
            int j = 2 + this.horseContainer.getContainerSize();

            if (i < j) {
                if (!this.moveItemStackTo(itemstack1, j, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(1).mayPlace(itemstack1) && !this.getSlot(1).hasItem()) {
                if (!this.moveItemStackTo(itemstack1, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(0).mayPlace(itemstack1) && !this.getSlot(0).hasItem()) {
                if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.horseContainer.getContainerSize() == 0 || !this.moveItemStackTo(itemstack1, 2, j, false)) {
                int k = j + 27;
                int l = k + 9;

                if (i >= k && i < l) {
                    if (!this.moveItemStackTo(itemstack1, j, k, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (i >= j && i < k) {
                    if (!this.moveItemStackTo(itemstack1, k, l, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, k, k, false)) {
                    return ItemStack.EMPTY;
                }

                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public void removed(EntityHuman entityhuman) {
        super.removed(entityhuman);
        this.horseContainer.stopOpen(entityhuman);
    }
}

package org.bukkit.craftbukkit.inventory;

import net.minecraft.world.IInventory;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EnumItemSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LlamaInventory;

public class CraftInventoryLlama extends CraftInventoryAbstractHorse implements LlamaInventory {

    public CraftInventoryLlama(IInventory inventory, EntityEquipment equipment) {
        super(inventory, equipment);
    }

    @Override
    public ItemStack getDecor() {
        net.minecraft.world.item.ItemStack item = equipment.get(EnumItemSlot.BODY);
        return item.isEmpty() ? null : CraftItemStack.asCraftMirror(item);
    }

    @Override
    public void setDecor(ItemStack stack) {
        equipment.set(EnumItemSlot.BODY, CraftItemStack.asNMSCopy(stack));
    }
}

package org.bukkit.craftbukkit.inventory;

import net.minecraft.world.IInventory;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EnumItemSlot;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.ItemStack;

public class CraftInventoryAbstractHorse extends CraftInventory implements AbstractHorseInventory {

    protected final EntityEquipment equipment;

    public CraftInventoryAbstractHorse(IInventory inventory, EntityEquipment equipment) {
        super(inventory);
        this.equipment = equipment;
    }

    @Override
    public ItemStack getSaddle() {
        net.minecraft.world.item.ItemStack item = equipment.get(EnumItemSlot.SADDLE);
        return item.isEmpty() ? null : CraftItemStack.asCraftMirror(item);
    }

    @Override
    public void setSaddle(ItemStack stack) {
        equipment.set(EnumItemSlot.SADDLE, CraftItemStack.asNMSCopy(stack));
    }
}

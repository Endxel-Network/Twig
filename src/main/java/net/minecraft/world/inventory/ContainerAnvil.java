package net.minecraft.world.inventory;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.tags.TagsBlock;
import net.minecraft.util.MathHelper;
import net.minecraft.util.UtilColor;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.PlayerInventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentManager;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.BlockAnvil;
import net.minecraft.world.level.block.state.IBlockData;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.inventory.view.CraftAnvilView;
// CraftBukkit end

public class ContainerAnvil extends ContainerAnvilAbstract {

    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_COST = false;
    public static final int MAX_NAME_LENGTH = 50;
    public int repairItemCountCost;
    @Nullable
    public String itemName;
    public final ContainerProperty cost;
    private boolean onlyRenaming;
    private static final int COST_FAIL = 0;
    private static final int COST_BASE = 1;
    private static final int COST_ADDED_BASE = 1;
    private static final int COST_REPAIR_MATERIAL = 1;
    private static final int COST_REPAIR_SACRIFICE = 2;
    private static final int COST_INCOMPATIBLE_PENALTY = 1;
    private static final int COST_RENAME = 1;
    private static final int INPUT_SLOT_X_PLACEMENT = 27;
    private static final int ADDITIONAL_SLOT_X_PLACEMENT = 76;
    private static final int RESULT_SLOT_X_PLACEMENT = 134;
    private static final int SLOT_Y_PLACEMENT = 47;
    // CraftBukkit start
    public static final int DEFAULT_DENIED_COST = -1;
    public int maximumRepairCost = 40;
    private CraftAnvilView bukkitEntity;
    // CraftBukkit end

    public ContainerAnvil(int i, PlayerInventory playerinventory) {
        this(i, playerinventory, ContainerAccess.NULL);
    }

    public ContainerAnvil(int i, PlayerInventory playerinventory, ContainerAccess containeraccess) {
        super(Containers.ANVIL, i, playerinventory, containeraccess, createInputSlotDefinitions());
        this.cost = ContainerProperty.standalone();
        this.onlyRenaming = false;
        this.addDataSlot(this.cost);
    }

    private static ItemCombinerMenuSlotDefinition createInputSlotDefinitions() {
        return ItemCombinerMenuSlotDefinition.create().withSlot(0, 27, 47, (itemstack) -> {
            return true;
        }).withSlot(1, 76, 47, (itemstack) -> {
            return true;
        }).withResultSlot(2, 134, 47).build();
    }

    @Override
    protected boolean isValidBlock(IBlockData iblockdata) {
        return iblockdata.is(TagsBlock.ANVIL);
    }

    @Override
    protected boolean mayPickup(EntityHuman entityhuman, boolean flag) {
        return (entityhuman.hasInfiniteMaterials() || entityhuman.experienceLevel >= this.cost.get()) && this.cost.get() > ContainerAnvil.DEFAULT_DENIED_COST && flag; // CraftBukkit - allow cost 0 like a free item
    }

    @Override
    protected void onTake(EntityHuman entityhuman, ItemStack itemstack) {
        if (!entityhuman.hasInfiniteMaterials()) {
            entityhuman.giveExperienceLevels(-this.cost.get());
        }

        if (this.repairItemCountCost > 0) {
            ItemStack itemstack1 = this.inputSlots.getItem(1);

            if (!itemstack1.isEmpty() && itemstack1.getCount() > this.repairItemCountCost) {
                itemstack1.shrink(this.repairItemCountCost);
                this.inputSlots.setItem(1, itemstack1);
            } else {
                this.inputSlots.setItem(1, ItemStack.EMPTY);
            }
        } else if (!this.onlyRenaming) {
            this.inputSlots.setItem(1, ItemStack.EMPTY);
        }

        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        this.inputSlots.setItem(0, ItemStack.EMPTY);
        this.access.execute((world, blockposition) -> {
            IBlockData iblockdata = world.getBlockState(blockposition);

            if (!entityhuman.hasInfiniteMaterials() && iblockdata.is(TagsBlock.ANVIL) && entityhuman.getRandom().nextFloat() < 0.12F) {
                IBlockData iblockdata1 = BlockAnvil.damage(iblockdata);

                if (iblockdata1 == null) {
                    world.removeBlock(blockposition, false);
                    world.levelEvent(1029, blockposition, 0);
                } else {
                    world.setBlock(blockposition, iblockdata1, 2);
                    world.levelEvent(1030, blockposition, 0);
                }
            } else {
                world.levelEvent(1030, blockposition, 0);
            }

        });
    }

    @Override
    public void createResult() {
        ItemStack itemstack = this.inputSlots.getItem(0);

        this.onlyRenaming = false;
        this.cost.set(1);
        int i = 0;
        long j = 0L;
        int k = 0;

        if (!itemstack.isEmpty() && EnchantmentManager.canStoreEnchantments(itemstack)) {
            ItemStack itemstack1 = itemstack.copy();
            ItemStack itemstack2 = this.inputSlots.getItem(1);
            ItemEnchantments.a itemenchantments_a = new ItemEnchantments.a(EnchantmentManager.getEnchantmentsForCrafting(itemstack1));

            j += (long) (Integer) itemstack.getOrDefault(DataComponents.REPAIR_COST, 0) + (long) (Integer) itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0);
            this.repairItemCountCost = 0;
            if (!itemstack2.isEmpty()) {
                boolean flag = itemstack2.has(DataComponents.STORED_ENCHANTMENTS);

                if (itemstack1.isDamageableItem() && itemstack.isValidRepairItem(itemstack2)) {
                    int l = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);

                    if (l <= 0) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    int i1;

                    for (i1 = 0; l > 0 && i1 < itemstack2.getCount(); ++i1) {
                        int j1 = itemstack1.getDamageValue() - l;

                        itemstack1.setDamageValue(j1);
                        ++i;
                        l = Math.min(itemstack1.getDamageValue(), itemstack1.getMaxDamage() / 4);
                    }

                    this.repairItemCountCost = i1;
                } else {
                    if (!flag && (!itemstack1.is(itemstack2.getItem()) || !itemstack1.isDamageableItem())) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }

                    if (itemstack1.isDamageableItem() && !flag) {
                        int k1 = itemstack.getMaxDamage() - itemstack.getDamageValue();
                        int l1 = itemstack2.getMaxDamage() - itemstack2.getDamageValue();
                        int i2 = l1 + itemstack1.getMaxDamage() * 12 / 100;
                        int j2 = k1 + i2;
                        int k2 = itemstack1.getMaxDamage() - j2;

                        if (k2 < 0) {
                            k2 = 0;
                        }

                        if (k2 < itemstack1.getDamageValue()) {
                            itemstack1.setDamageValue(k2);
                            i += 2;
                        }
                    }

                    ItemEnchantments itemenchantments = EnchantmentManager.getEnchantmentsForCrafting(itemstack2);
                    boolean flag1 = false;
                    boolean flag2 = false;

                    for (Object2IntMap.Entry<Holder<Enchantment>> object2intmap_entry : itemenchantments.entrySet()) {
                        Holder<Enchantment> holder = (Holder) object2intmap_entry.getKey();
                        int l2 = itemenchantments_a.getLevel(holder);
                        int i3 = object2intmap_entry.getIntValue();

                        i3 = l2 == i3 ? i3 + 1 : Math.max(i3, l2);
                        Enchantment enchantment = holder.value();
                        boolean flag3 = enchantment.canEnchant(itemstack);

                        if (this.player.hasInfiniteMaterials() || itemstack.is(Items.ENCHANTED_BOOK)) {
                            flag3 = true;
                        }

                        for (Holder<Enchantment> holder1 : itemenchantments_a.keySet()) {
                            if (!holder1.equals(holder) && !Enchantment.areCompatible(holder, holder1)) {
                                flag3 = false;
                                ++i;
                            }
                        }

                        if (!flag3) {
                            flag2 = true;
                        } else {
                            flag1 = true;
                            if (i3 > enchantment.getMaxLevel()) {
                                i3 = enchantment.getMaxLevel();
                            }

                            itemenchantments_a.set(holder, i3);
                            int j3 = enchantment.getAnvilCost();

                            if (flag) {
                                j3 = Math.max(1, j3 / 2);
                            }

                            i += j3 * i3;
                            if (itemstack.getCount() > 1) {
                                i = 40;
                            }
                        }
                    }

                    if (flag2 && !flag1) {
                        org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
                        this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
                        return;
                    }
                }
            }

            if (this.itemName != null && !UtilColor.isBlank(this.itemName)) {
                if (!this.itemName.equals(itemstack.getHoverName().getString())) {
                    k = 1;
                    i += k;
                    itemstack1.set(DataComponents.CUSTOM_NAME, IChatBaseComponent.literal(this.itemName));
                }
            } else if (itemstack.has(DataComponents.CUSTOM_NAME)) {
                k = 1;
                i += k;
                itemstack1.remove(DataComponents.CUSTOM_NAME);
            }

            int k3 = i <= 0 ? 0 : (int) MathHelper.clamp(j + (long) i, 0L, 2147483647L);

            this.cost.set(k3);
            if (i <= 0) {
                itemstack1 = ItemStack.EMPTY;
            }

            if (k == i && k > 0) {
                if (this.cost.get() >= maximumRepairCost) { // CraftBukkit
                    this.cost.set(maximumRepairCost - 1); // CraftBukkit
                }

                this.onlyRenaming = true;
            }

            if (this.cost.get() >= maximumRepairCost && !this.player.hasInfiniteMaterials()) { // CraftBukkit
                itemstack1 = ItemStack.EMPTY;
            }

            if (!itemstack1.isEmpty()) {
                int l3 = (Integer) itemstack1.getOrDefault(DataComponents.REPAIR_COST, 0);

                if (l3 < (Integer) itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0)) {
                    l3 = (Integer) itemstack2.getOrDefault(DataComponents.REPAIR_COST, 0);
                }

                if (k != i || k == 0) {
                    l3 = calculateIncreasedRepairCost(l3);
                }

                itemstack1.set(DataComponents.REPAIR_COST, l3);
                EnchantmentManager.setEnchantments(itemstack1, itemenchantments_a.toImmutable());
            }

            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), itemstack1); // CraftBukkit
            this.broadcastChanges();
        } else {
            org.bukkit.craftbukkit.event.CraftEventFactory.callPrepareAnvilEvent(getBukkitView(), ItemStack.EMPTY); // CraftBukkit
            this.cost.set(DEFAULT_DENIED_COST); // CraftBukkit - use a variable for set a cost for denied item
        }
        sendAllDataToRemote(); // CraftBukkit - SPIGOT-6686, SPIGOT-7931: Always send completed inventory to stay in sync with client
    }

    public static int calculateIncreasedRepairCost(int i) {
        return (int) Math.min((long) i * 2L + 1L, 2147483647L);
    }

    public boolean setItemName(String s) {
        String s1 = validateName(s);

        if (s1 != null && !s1.equals(this.itemName)) {
            this.itemName = s1;
            if (this.getSlot(2).hasItem()) {
                ItemStack itemstack = this.getSlot(2).getItem();

                if (UtilColor.isBlank(s1)) {
                    itemstack.remove(DataComponents.CUSTOM_NAME);
                } else {
                    itemstack.set(DataComponents.CUSTOM_NAME, IChatBaseComponent.literal(s1));
                }
            }

            this.createResult();
            return true;
        } else {
            return false;
        }
    }

    @Nullable
    private static String validateName(String s) {
        String s1 = UtilColor.filterText(s);

        return s1.length() <= 50 ? s1 : null;
    }

    public int getCost() {
        return this.cost.get();
    }

    // CraftBukkit start
    @Override
    public CraftAnvilView getBukkitView() {
        if (bukkitEntity != null) {
            return bukkitEntity;
        }

        org.bukkit.craftbukkit.inventory.CraftInventoryAnvil inventory = new org.bukkit.craftbukkit.inventory.CraftInventoryAnvil(
                access.getLocation(), this.inputSlots, this.resultSlots);
        bukkitEntity = new CraftAnvilView(this.player.getBukkitEntity(), inventory, this);
        bukkitEntity.updateFromLegacy(inventory);
        return bukkitEntity;
    }
    // CraftBukkit end
}

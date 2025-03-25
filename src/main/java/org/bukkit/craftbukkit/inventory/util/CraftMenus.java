package org.bukkit.craftbukkit.inventory.util;

import java.util.function.Supplier;
import net.minecraft.network.protocol.game.PacketPlayOutOpenWindow;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.entity.npc.EntityVillager;
import net.minecraft.world.inventory.ContainerAnvil;
import net.minecraft.world.inventory.ContainerCartography;
import net.minecraft.world.inventory.ContainerEnchantTable;
import net.minecraft.world.inventory.ContainerGrindstone;
import net.minecraft.world.inventory.ContainerMerchant;
import net.minecraft.world.inventory.ContainerSmithing;
import net.minecraft.world.inventory.ContainerStonecutter;
import net.minecraft.world.inventory.ContainerWorkbench;
import net.minecraft.world.inventory.Containers;
import net.minecraft.world.item.trading.IMerchant;
import net.minecraft.world.item.trading.MerchantRecipeList;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.TileEntityBeacon;
import net.minecraft.world.level.block.entity.TileEntityBlastFurnace;
import net.minecraft.world.level.block.entity.TileEntityBrewingStand;
import net.minecraft.world.level.block.entity.TileEntityDispenser;
import net.minecraft.world.level.block.entity.TileEntityFurnaceFurnace;
import net.minecraft.world.level.block.entity.TileEntityHopper;
import net.minecraft.world.level.block.entity.TileEntityLectern;
import net.minecraft.world.level.block.entity.TileEntitySmoker;
import org.bukkit.craftbukkit.inventory.CraftMenuType;
import org.bukkit.craftbukkit.inventory.CraftMerchant;
import org.bukkit.craftbukkit.inventory.view.builder.CraftAccessLocationInventoryViewBuilder;
import org.bukkit.craftbukkit.inventory.view.builder.CraftBlockEntityInventoryViewBuilder;
import org.bukkit.craftbukkit.inventory.view.builder.CraftDoubleChestInventoryViewBuilder;
import org.bukkit.craftbukkit.inventory.view.builder.CraftMerchantInventoryViewBuilder;
import org.bukkit.craftbukkit.inventory.view.builder.CraftStandardInventoryViewBuilder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.inventory.view.BeaconView;
import org.bukkit.inventory.view.BrewingStandView;
import org.bukkit.inventory.view.CrafterView;
import org.bukkit.inventory.view.EnchantmentView;
import org.bukkit.inventory.view.FurnaceView;
import org.bukkit.inventory.view.LecternView;
import org.bukkit.inventory.view.LoomView;
import org.bukkit.inventory.view.MerchantView;
import org.bukkit.inventory.view.StonecutterView;
import org.bukkit.inventory.view.builder.InventoryViewBuilder;

public final class CraftMenus {

    public record MenuTypeData<V extends InventoryView, B extends InventoryViewBuilder<V>>(Class<V> viewClass, Supplier<B> viewBuilder) {
    }

    // This is a temporary measure that will likely be removed with the rewrite of HumanEntity#open[] methods
    public static void openMerchantMenu(EntityPlayer player, ContainerMerchant merchant) {
        final IMerchant minecraftMerchant = ((CraftMerchant) merchant.getBukkitView().getMerchant()).getMerchant();
        int level = 1;
        if (minecraftMerchant instanceof EntityVillager villager) {
            level = villager.getVillagerData().level();
        }

        if (minecraftMerchant.getTradingPlayer() != null) { // merchant's can only have one trader
            minecraftMerchant.getTradingPlayer().closeContainer();
        }

        minecraftMerchant.setTradingPlayer(player);

        player.connection.send(new PacketPlayOutOpenWindow(merchant.containerId, Containers.MERCHANT, merchant.getTitle()));
        player.containerMenu = merchant;
        player.initMenu(merchant);
        // Copy IMerchant#openTradingScreen
        MerchantRecipeList merchantrecipelist = minecraftMerchant.getOffers();

        if (!merchantrecipelist.isEmpty()) {
            player.sendMerchantOffers(merchant.containerId, merchantrecipelist, level, minecraftMerchant.getVillagerXp(), minecraftMerchant.showProgressBar(), minecraftMerchant.canRestock());
        }
        // End Copy IMerchant#openTradingScreen
    }

    public static <V extends InventoryView, B extends InventoryViewBuilder<V>> MenuTypeData<V, B> getMenuTypeData(CraftMenuType<?, ?> menuType) {
        final Containers<?> handle = menuType.getHandle();
        // this sucks horribly but it should work for now
        if (menuType == MenuType.GENERIC_9X6) {
            return asType(new MenuTypeData<>(InventoryView.class, () -> new CraftDoubleChestInventoryViewBuilder<>(handle)));
        }
        if (menuType == MenuType.GENERIC_9X3) {
            return asType(new MenuTypeData<>(InventoryView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.CHEST, null)));
        }
        // this isn't ideal as both dispenser and dropper are 3x3, InventoryType can't currently handle generic 3x3s with size 9
        // this needs to be removed when inventory creation is overhauled
        if (menuType == MenuType.GENERIC_3X3) {
            return asType(new MenuTypeData<>(InventoryView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.DISPENSER, TileEntityDispenser::new)));
        }
        if (menuType == MenuType.CRAFTER_3X3) {
            return asType(new MenuTypeData<>(CrafterView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.CRAFTER, CrafterBlockEntity::new)));
        }
        if (menuType == MenuType.ANVIL) {
            return asType(new MenuTypeData<>(AnvilView.class, () -> new CraftAccessLocationInventoryViewBuilder<>(handle, ContainerAnvil::new)));
        }
        if (menuType == MenuType.BEACON) {
            return asType(new MenuTypeData<>(BeaconView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.BEACON, TileEntityBeacon::new)));
        }
        if (menuType == MenuType.BLAST_FURNACE) {
            return asType(new MenuTypeData<>(FurnaceView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.BLAST_FURNACE, TileEntityBlastFurnace::new)));
        }
        if (menuType == MenuType.BREWING_STAND) {
            return asType(new MenuTypeData<>(BrewingStandView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.BREWING_STAND, TileEntityBrewingStand::new)));
        }
        if (menuType == MenuType.CRAFTING) {
            return asType(new MenuTypeData<>(InventoryView.class, () -> new CraftAccessLocationInventoryViewBuilder<>(handle, ContainerWorkbench::new)));
        }
        if (menuType == MenuType.ENCHANTMENT) {
            return asType(new MenuTypeData<>(EnchantmentView.class, () -> new CraftAccessLocationInventoryViewBuilder<>(handle, ContainerEnchantTable::new)));
        }
        if (menuType == MenuType.FURNACE) {
            return asType(new MenuTypeData<>(FurnaceView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.FURNACE, TileEntityFurnaceFurnace::new)));
        }
        if (menuType == MenuType.GRINDSTONE) {
            return asType(new MenuTypeData<>(InventoryView.class, () -> new CraftAccessLocationInventoryViewBuilder<>(handle, ContainerGrindstone::new)));
        }
        // We really don't need to be creating a tile entity for hopper but currently InventoryType doesn't have capacity
        // to understand otherwise
        if (menuType == MenuType.HOPPER) {
            return asType(new MenuTypeData<>(InventoryView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.HOPPER, TileEntityHopper::new)));
        }
        // We also don't need to create a tile entity for lectern, but again InventoryType isn't smart enough to know any better
        if (menuType == MenuType.LECTERN) {
            return asType(new MenuTypeData<>(LecternView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.LECTERN, TileEntityLectern::new)));
        }
        if (menuType == MenuType.LOOM) {
            return asType(new MenuTypeData<>(LoomView.class, () -> new CraftStandardInventoryViewBuilder<>(handle)));
        }
        if (menuType == MenuType.MERCHANT) {
            return asType(new MenuTypeData<>(MerchantView.class, () -> new CraftMerchantInventoryViewBuilder<>(handle)));
        }
        if (menuType == MenuType.SHULKER_BOX) {
            return asType(new MenuTypeData<>(InventoryView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.SHULKER_BOX, null)));
        }
        if (menuType == MenuType.SMITHING) {
            return asType(new MenuTypeData<>(InventoryView.class, () -> new CraftAccessLocationInventoryViewBuilder<>(handle, ContainerSmithing::new)));
        }
        if (menuType == MenuType.SMOKER) {
            return asType(new MenuTypeData<>(FurnaceView.class, () -> new CraftBlockEntityInventoryViewBuilder<>(handle, Blocks.SMOKER, TileEntitySmoker::new)));
        }
        if (menuType == MenuType.CARTOGRAPHY_TABLE) {
            return asType(new MenuTypeData<>(InventoryView.class, () -> new CraftAccessLocationInventoryViewBuilder<>(handle, ContainerCartography::new)));
        }
        if (menuType == MenuType.STONECUTTER) {
            return asType(new MenuTypeData<>(StonecutterView.class, () -> new CraftAccessLocationInventoryViewBuilder<>(handle, ContainerStonecutter::new)));
        }

        return asType(new MenuTypeData<>(InventoryView.class, () -> new CraftStandardInventoryViewBuilder<>(handle)));
    }

    private static <V extends InventoryView, B extends InventoryViewBuilder<V>> MenuTypeData<V, B> asType(MenuTypeData<?, ?> data) {
        return (MenuTypeData<V, B>) data;
    }
}

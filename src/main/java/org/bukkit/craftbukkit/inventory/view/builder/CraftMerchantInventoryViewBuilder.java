package org.bukkit.craftbukkit.inventory.view.builder;

import com.google.common.base.Preconditions;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.inventory.Container;
import net.minecraft.world.inventory.ContainerMerchant;
import net.minecraft.world.inventory.Containers;
import net.minecraft.world.item.trading.IMerchant;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftMerchant;
import org.bukkit.craftbukkit.inventory.CraftMerchantCustom;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.view.builder.MerchantInventoryViewBuilder;

public class CraftMerchantInventoryViewBuilder<V extends InventoryView> extends CraftAbstractInventoryViewBuilder<V> implements MerchantInventoryViewBuilder<V> {

    private IMerchant merchant;

    public CraftMerchantInventoryViewBuilder(final Containers<?> handle) {
        super(handle);
    }

    @Override
    public MerchantInventoryViewBuilder<V> title(final String title) {
        return (MerchantInventoryViewBuilder<V>) super.title(title);
    }

    @Override
    public MerchantInventoryViewBuilder<V> merchant(final Merchant merchant) {
        this.merchant = ((CraftMerchant) merchant).getMerchant();
        return this;
    }

    @Override
    public MerchantInventoryViewBuilder<V> checkReachable(final boolean checkReachable) {
        super.checkReachable = checkReachable;
        return this;
    }

    @Override
    public V build(final HumanEntity player) {
        Preconditions.checkArgument(player != null, "The given player must not be null");
        Preconditions.checkArgument(this.title != null, "The given title must not be null");
        Preconditions.checkArgument(player instanceof CraftHumanEntity, "The given player must be a CraftHumanEntity");
        final CraftHumanEntity craftHuman = (CraftHumanEntity) player;
        Preconditions.checkArgument(craftHuman.getHandle() instanceof EntityPlayer, "The given player must be an EntityPlayer");
        final EntityPlayer serverPlayer = (EntityPlayer) craftHuman.getHandle();

        final ContainerMerchant container;
        if (this.merchant == null) {
            container = new ContainerMerchant(serverPlayer.nextContainerCounter(), serverPlayer.getInventory(), new CraftMerchantCustom(title).getMerchant());
        } else {
            container = new ContainerMerchant(serverPlayer.nextContainerCounter(), serverPlayer.getInventory(), this.merchant);
        }

        container.checkReachable = super.checkReachable;
        container.setTitle(CraftChatMessage.fromString(title)[0]);
        return (V) container.getBukkitView();
    }

    @Override
    protected Container buildContainer(final EntityPlayer player) {
        throw new UnsupportedOperationException("buildContainer is not supported for CraftMerchantInventoryViewBuilder");
    }

    @Override
    public MerchantInventoryViewBuilder<V> copy() {
        CraftMerchantInventoryViewBuilder<V> copy = new CraftMerchantInventoryViewBuilder<>(super.handle);
        copy.checkReachable = super.checkReachable;
        copy.merchant = this.merchant;
        copy.title = title;
        return copy;
    }
}

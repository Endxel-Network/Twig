package net.minecraft.world.item;

import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.EnumChatFormat;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.world.EnumInteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.EntityHanging;
import net.minecraft.world.entity.decoration.EntityItemFrame;
import net.minecraft.world.entity.decoration.EntityPainting;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.ItemActionContext;
import net.minecraft.world.level.World;
import net.minecraft.world.level.gameevent.GameEvent;

// CraftBukkit start
import org.bukkit.entity.Player;
import org.bukkit.event.hanging.HangingPlaceEvent;
// CraftBukkit end

public class ItemHanging extends Item {

    private static final IChatBaseComponent TOOLTIP_RANDOM_VARIANT = IChatBaseComponent.translatable("painting.random").withStyle(EnumChatFormat.GRAY);
    private final EntityTypes<? extends EntityHanging> type;

    public ItemHanging(EntityTypes<? extends EntityHanging> entitytypes, Item.Info item_info) {
        super(item_info);
        this.type = entitytypes;
    }

    @Override
    public EnumInteractionResult useOn(ItemActionContext itemactioncontext) {
        BlockPosition blockposition = itemactioncontext.getClickedPos();
        EnumDirection enumdirection = itemactioncontext.getClickedFace();
        BlockPosition blockposition1 = blockposition.relative(enumdirection);
        EntityHuman entityhuman = itemactioncontext.getPlayer();
        ItemStack itemstack = itemactioncontext.getItemInHand();

        if (entityhuman != null && !this.mayPlace(entityhuman, enumdirection, itemstack, blockposition1)) {
            return EnumInteractionResult.FAIL;
        } else {
            World world = itemactioncontext.getLevel();
            EntityHanging entityhanging;

            if (this.type == EntityTypes.PAINTING) {
                Optional<EntityPainting> optional = EntityPainting.create(world, blockposition1, enumdirection);

                if (optional.isEmpty()) {
                    return EnumInteractionResult.CONSUME;
                }

                entityhanging = (EntityHanging) optional.get();
            } else if (this.type == EntityTypes.ITEM_FRAME) {
                entityhanging = new EntityItemFrame(world, blockposition1, enumdirection);
            } else {
                if (this.type != EntityTypes.GLOW_ITEM_FRAME) {
                    return EnumInteractionResult.SUCCESS;
                }

                entityhanging = new GlowItemFrame(world, blockposition1, enumdirection);
            }

            EntityTypes.createDefaultStackConfig(world, itemstack, entityhuman).accept(entityhanging);
            if (entityhanging.survives()) {
                if (!world.isClientSide) {
                    // CraftBukkit start - fire HangingPlaceEvent
                    Player who = (itemactioncontext.getPlayer() == null) ? null : (Player) itemactioncontext.getPlayer().getBukkitEntity();
                    org.bukkit.block.Block blockClicked = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
                    org.bukkit.block.BlockFace blockFace = org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(enumdirection);
                    org.bukkit.inventory.EquipmentSlot hand = org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(itemactioncontext.getHand());

                    HangingPlaceEvent event = new HangingPlaceEvent((org.bukkit.entity.Hanging) entityhanging.getBukkitEntity(), who, blockClicked, blockFace, hand, org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemstack));
                    world.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return EnumInteractionResult.FAIL;
                    }
                    // CraftBukkit end
                    entityhanging.playPlacementSound();
                    world.gameEvent(entityhuman, (Holder) GameEvent.ENTITY_PLACE, entityhanging.position());
                    world.addFreshEntity(entityhanging);
                }

                itemstack.shrink(1);
                return EnumInteractionResult.SUCCESS;
            } else {
                return EnumInteractionResult.CONSUME;
            }
        }
    }

    protected boolean mayPlace(EntityHuman entityhuman, EnumDirection enumdirection, ItemStack itemstack, BlockPosition blockposition) {
        return !enumdirection.getAxis().isVertical() && entityhuman.mayUseItemAt(blockposition, enumdirection, itemstack);
    }

    @Override
    public void appendHoverText(ItemStack itemstack, Item.b item_b, TooltipDisplay tooltipdisplay, Consumer<IChatBaseComponent> consumer, TooltipFlag tooltipflag) {
        if (this.type == EntityTypes.PAINTING && tooltipdisplay.shows(DataComponents.PAINTING_VARIANT)) {
            Holder<PaintingVariant> holder = (Holder) itemstack.get(DataComponents.PAINTING_VARIANT);

            if (holder != null) {
                ((PaintingVariant) holder.value()).title().ifPresent(consumer);
                ((PaintingVariant) holder.value()).author().ifPresent(consumer);
                consumer.accept(IChatBaseComponent.translatable("painting.dimensions", ((PaintingVariant) holder.value()).width(), ((PaintingVariant) holder.value()).height()));
            } else if (tooltipflag.isCreative()) {
                consumer.accept(ItemHanging.TOOLTIP_RANDOM_VARIANT);
            }
        }

    }
}

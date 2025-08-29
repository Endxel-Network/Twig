package net.minecraft.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.advancements.CriterionTriggers;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.item.crafting.IRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.slf4j.Logger;

import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class RecipeBookServer extends RecipeBook {

    public static final String RECIPE_BOOK_TAG = "recipeBook";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<List<ResourceKey<IRecipe<?>>>> RECIPE_LIST_CODEC = IRecipe.KEY_CODEC.listOf();
    private final RecipeBookServer.a displayResolver;
    @VisibleForTesting
    public final Set<ResourceKey<IRecipe<?>>> known = Sets.newIdentityHashSet();
    @VisibleForTesting
    protected final Set<ResourceKey<IRecipe<?>>> highlight = Sets.newIdentityHashSet();

    public RecipeBookServer(RecipeBookServer.a recipebookserver_a) {
        this.displayResolver = recipebookserver_a;
    }

    public void add(ResourceKey<IRecipe<?>> resourcekey) {
        this.known.add(resourcekey);
    }

    public boolean contains(ResourceKey<IRecipe<?>> resourcekey) {
        return this.known.contains(resourcekey);
    }

    public void remove(ResourceKey<IRecipe<?>> resourcekey) {
        this.known.remove(resourcekey);
        this.highlight.remove(resourcekey);
    }

    public void removeHighlight(ResourceKey<IRecipe<?>> resourcekey) {
        this.highlight.remove(resourcekey);
    }

    private void addHighlight(ResourceKey<IRecipe<?>> resourcekey) {
        this.highlight.add(resourcekey);
    }

    public int addRecipes(Collection<RecipeHolder<?>> collection, EntityPlayer entityplayer) {
        List<ClientboundRecipeBookAddPacket.a> list = new ArrayList();

        for (RecipeHolder<?> recipeholder : collection) {
            ResourceKey<IRecipe<?>> resourcekey = recipeholder.id();

            if (!this.known.contains(resourcekey) && !recipeholder.value().isSpecial() && CraftEventFactory.handlePlayerRecipeListUpdateEvent(entityplayer, resourcekey.location())) { // CraftBukkit
                this.add(resourcekey);
                this.addHighlight(resourcekey);
                this.displayResolver.displaysForRecipe(resourcekey, (recipedisplayentry) -> {
                    list.add(new ClientboundRecipeBookAddPacket.a(recipedisplayentry, recipeholder.value().showNotification(), true));
                });
                CriterionTriggers.RECIPE_UNLOCKED.trigger(entityplayer, recipeholder);
            }
        }

        if (!list.isEmpty() && entityplayer.connection != null) { // SPIGOT-4478 during PlayerLoginEvent
            entityplayer.connection.send(new ClientboundRecipeBookAddPacket(list, false));
        }

        return list.size();
    }

    public int removeRecipes(Collection<RecipeHolder<?>> collection, EntityPlayer entityplayer) {
        List<RecipeDisplayId> list = Lists.newArrayList();

        for (RecipeHolder<?> recipeholder : collection) {
            ResourceKey<IRecipe<?>> resourcekey = recipeholder.id();

            if (this.known.contains(resourcekey)) {
                this.remove(resourcekey);
                this.displayResolver.displaysForRecipe(resourcekey, (recipedisplayentry) -> {
                    list.add(recipedisplayentry.id());
                });
            }
        }

        if (!list.isEmpty() && entityplayer.connection != null) { // SPIGOT-4478 during PlayerLoginEvent
            entityplayer.connection.send(new ClientboundRecipeBookRemovePacket(list));
        }

        return list.size();
    }

    public NBTTagCompound toNbt() {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        this.getBookSettings().write(nbttagcompound);
        NBTTagList nbttaglist = new NBTTagList();

        for (ResourceKey<IRecipe<?>> resourcekey : this.known) {
            nbttaglist.add(NBTTagString.valueOf(resourcekey.location().toString()));
        }

        nbttagcompound.put("recipes", nbttaglist);
        NBTTagList nbttaglist1 = new NBTTagList();

        for (ResourceKey<IRecipe<?>> resourcekey1 : this.highlight) {
            nbttaglist1.add(NBTTagString.valueOf(resourcekey1.location().toString()));
        }

        nbttagcompound.put("toBeDisplayed", nbttaglist1);
        return nbttagcompound;
    }

    public void fromNbt(NBTTagCompound nbttagcompound, Predicate<ResourceKey<IRecipe<?>>> predicate) {
        this.setBookSettings(RecipeBookSettings.read(nbttagcompound));
        List<ResourceKey<IRecipe<?>>> list = (List) nbttagcompound.read("recipes", RecipeBookServer.RECIPE_LIST_CODEC).orElse(List.of());

        this.loadRecipes(list, this::add, predicate);
        List<ResourceKey<IRecipe<?>>> list1 = (List) nbttagcompound.read("toBeDisplayed", RecipeBookServer.RECIPE_LIST_CODEC).orElse(List.of());

        this.loadRecipes(list1, this::addHighlight, predicate);
    }

    private void loadRecipes(List<ResourceKey<IRecipe<?>>> list, Consumer<ResourceKey<IRecipe<?>>> consumer, Predicate<ResourceKey<IRecipe<?>>> predicate) {
        for (ResourceKey<IRecipe<?>> resourcekey : list) {
            if (!predicate.test(resourcekey)) {
                RecipeBookServer.LOGGER.error("Tried to load unrecognized recipe: {} removed now.", resourcekey);
            } else {
                consumer.accept(resourcekey);
            }
        }

    }

    public void sendInitialRecipeBook(EntityPlayer entityplayer) {
        entityplayer.connection.send(new ClientboundRecipeBookSettingsPacket(this.getBookSettings()));
        List<ClientboundRecipeBookAddPacket.a> list = new ArrayList(this.known.size());

        for (ResourceKey<IRecipe<?>> resourcekey : this.known) {
            this.displayResolver.displaysForRecipe(resourcekey, (recipedisplayentry) -> {
                list.add(new ClientboundRecipeBookAddPacket.a(recipedisplayentry, false, this.highlight.contains(resourcekey)));
            });
        }

        entityplayer.connection.send(new ClientboundRecipeBookAddPacket(list, true));
    }

    public void copyOverData(RecipeBookServer recipebookserver) {
        this.known.clear();
        this.highlight.clear();
        this.bookSettings.replaceFrom(recipebookserver.bookSettings);
        this.known.addAll(recipebookserver.known);
        this.highlight.addAll(recipebookserver.highlight);
    }

    @FunctionalInterface
    public interface a {

        void displaysForRecipe(ResourceKey<IRecipe<?>> resourcekey, Consumer<RecipeDisplayEntry> consumer);
    }
}

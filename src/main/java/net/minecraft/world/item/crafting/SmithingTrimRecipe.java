package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;

// CraftBukkit start
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftSmithingTrimRecipe;
import org.bukkit.craftbukkit.inventory.trim.CraftTrimPattern;
import org.bukkit.inventory.Recipe;
// CraftBukkit end

public class SmithingTrimRecipe implements SmithingRecipe {

    final RecipeItemStack template;
    final RecipeItemStack base;
    final RecipeItemStack addition;
    final Holder<TrimPattern> pattern;
    @Nullable
    private PlacementInfo placementInfo;

    public SmithingTrimRecipe(RecipeItemStack recipeitemstack, RecipeItemStack recipeitemstack1, RecipeItemStack recipeitemstack2, Holder<TrimPattern> holder) {
        this.template = recipeitemstack;
        this.base = recipeitemstack1;
        this.addition = recipeitemstack2;
        this.pattern = holder;
    }

    public ItemStack assemble(SmithingRecipeInput smithingrecipeinput, HolderLookup.a holderlookup_a) {
        return applyTrim(holderlookup_a, smithingrecipeinput.base(), smithingrecipeinput.addition(), this.pattern);
    }

    public static ItemStack applyTrim(HolderLookup.a holderlookup_a, ItemStack itemstack, ItemStack itemstack1, Holder<TrimPattern> holder) {
        Optional<Holder<TrimMaterial>> optional = TrimMaterials.getFromIngredient(holderlookup_a, itemstack1);

        if (optional.isPresent()) {
            ArmorTrim armortrim = (ArmorTrim) itemstack.get(DataComponents.TRIM);
            ArmorTrim armortrim1 = new ArmorTrim((Holder) optional.get(), holder);

            if (Objects.equals(armortrim, armortrim1)) {
                return ItemStack.EMPTY;
            } else {
                ItemStack itemstack2 = itemstack.copyWithCount(1);

                itemstack2.set(DataComponents.TRIM, armortrim1);
                return itemstack2;
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public Optional<RecipeItemStack> templateIngredient() {
        return Optional.of(this.template);
    }

    @Override
    public RecipeItemStack baseIngredient() {
        return this.base;
    }

    @Override
    public Optional<RecipeItemStack> additionIngredient() {
        return Optional.of(this.addition);
    }

    @Override
    public RecipeSerializer<SmithingTrimRecipe> getSerializer() {
        return RecipeSerializer.SMITHING_TRIM;
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.create(List.of(this.template, this.base, this.addition));
        }

        return this.placementInfo;
    }

    @Override
    public List<RecipeDisplay> display() {
        SlotDisplay slotdisplay = this.base.display();
        SlotDisplay slotdisplay1 = this.addition.display();
        SlotDisplay slotdisplay2 = this.template.display();

        return List.of(new SmithingRecipeDisplay(slotdisplay2, slotdisplay, slotdisplay1, new SlotDisplay.g(slotdisplay, slotdisplay1, this.pattern), new SlotDisplay.d(Items.SMITHING_TABLE)));
    }

    // CraftBukkit start
    @Override
    public Recipe toBukkitRecipe(NamespacedKey id) {
        return new CraftSmithingTrimRecipe(id, CraftRecipe.toBukkit(this.template), CraftRecipe.toBukkit(this.base), CraftRecipe.toBukkit(this.addition), CraftTrimPattern.minecraftHolderToBukkit(this.pattern));
    }
    // CraftBukkit end

    public static class a implements RecipeSerializer<SmithingTrimRecipe> {

        private static final MapCodec<SmithingTrimRecipe> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(RecipeItemStack.CODEC.fieldOf("template").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.template;
            }), RecipeItemStack.CODEC.fieldOf("base").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.base;
            }), RecipeItemStack.CODEC.fieldOf("addition").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.addition;
            }), TrimPattern.CODEC.fieldOf("pattern").forGetter((smithingtrimrecipe) -> {
                return smithingtrimrecipe.pattern;
            })).apply(instance, SmithingTrimRecipe::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> STREAM_CODEC = StreamCodec.composite(RecipeItemStack.CONTENTS_STREAM_CODEC, (smithingtrimrecipe) -> {
            return smithingtrimrecipe.template;
        }, RecipeItemStack.CONTENTS_STREAM_CODEC, (smithingtrimrecipe) -> {
            return smithingtrimrecipe.base;
        }, RecipeItemStack.CONTENTS_STREAM_CODEC, (smithingtrimrecipe) -> {
            return smithingtrimrecipe.addition;
        }, TrimPattern.STREAM_CODEC, (smithingtrimrecipe) -> {
            return smithingtrimrecipe.pattern;
        }, SmithingTrimRecipe::new);

        public a() {}

        @Override
        public MapCodec<SmithingTrimRecipe> codec() {
            return SmithingTrimRecipe.a.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingTrimRecipe> streamCodec() {
            return SmithingTrimRecipe.a.STREAM_CODEC;
        }
    }
}

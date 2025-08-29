package net.minecraft.world.item.crafting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;

// CraftBukkit start
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.inventory.CraftRecipe;
import org.bukkit.craftbukkit.inventory.CraftSmithingTransformRecipe;
import org.bukkit.inventory.Recipe;
// CraftBukkit end

public class SmithingTransformRecipe implements SmithingRecipe {

    final Optional<RecipeItemStack> template;
    final RecipeItemStack base;
    final Optional<RecipeItemStack> addition;
    final TransmuteResult result;
    @Nullable
    private PlacementInfo placementInfo;

    public SmithingTransformRecipe(Optional<RecipeItemStack> optional, RecipeItemStack recipeitemstack, Optional<RecipeItemStack> optional1, TransmuteResult transmuteresult) {
        this.template = optional;
        this.base = recipeitemstack;
        this.addition = optional1;
        this.result = transmuteresult;
    }

    public ItemStack assemble(SmithingRecipeInput smithingrecipeinput, HolderLookup.a holderlookup_a) {
        return this.result.apply(smithingrecipeinput.base());
    }

    @Override
    public Optional<RecipeItemStack> templateIngredient() {
        return this.template;
    }

    @Override
    public RecipeItemStack baseIngredient() {
        return this.base;
    }

    @Override
    public Optional<RecipeItemStack> additionIngredient() {
        return this.addition;
    }

    @Override
    public RecipeSerializer<SmithingTransformRecipe> getSerializer() {
        return RecipeSerializer.SMITHING_TRANSFORM;
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.createFromOptionals(List.of(this.template, Optional.of(this.base), this.addition));
        }

        return this.placementInfo;
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(new SmithingRecipeDisplay(RecipeItemStack.optionalIngredientToDisplay(this.template), this.base.display(), RecipeItemStack.optionalIngredientToDisplay(this.addition), this.result.display(), new SlotDisplay.d(Items.SMITHING_TABLE)));
    }

    // CraftBukkit start
    @Override
    public Recipe toBukkitRecipe(NamespacedKey id) {
        org.bukkit.inventory.ItemStack result = CraftRecipe.toBukkit(this.result);

        CraftSmithingTransformRecipe recipe = new CraftSmithingTransformRecipe(id, result, CraftRecipe.toBukkit(this.template), CraftRecipe.toBukkit(this.base), CraftRecipe.toBukkit(this.addition));

        return recipe;
    }
    // CraftBukkit end

    public static class a implements RecipeSerializer<SmithingTransformRecipe> {

        private static final MapCodec<SmithingTransformRecipe> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
            return instance.group(RecipeItemStack.CODEC.optionalFieldOf("template").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.template;
            }), RecipeItemStack.CODEC.fieldOf("base").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.base;
            }), RecipeItemStack.CODEC.optionalFieldOf("addition").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.addition;
            }), TransmuteResult.CODEC.fieldOf("result").forGetter((smithingtransformrecipe) -> {
                return smithingtransformrecipe.result;
            })).apply(instance, SmithingTransformRecipe::new);
        });
        public static final StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> STREAM_CODEC = StreamCodec.composite(RecipeItemStack.OPTIONAL_CONTENTS_STREAM_CODEC, (smithingtransformrecipe) -> {
            return smithingtransformrecipe.template;
        }, RecipeItemStack.CONTENTS_STREAM_CODEC, (smithingtransformrecipe) -> {
            return smithingtransformrecipe.base;
        }, RecipeItemStack.OPTIONAL_CONTENTS_STREAM_CODEC, (smithingtransformrecipe) -> {
            return smithingtransformrecipe.addition;
        }, TransmuteResult.STREAM_CODEC, (smithingtransformrecipe) -> {
            return smithingtransformrecipe.result;
        }, SmithingTransformRecipe::new);

        public a() {}

        @Override
        public MapCodec<SmithingTransformRecipe> codec() {
            return SmithingTransformRecipe.a.CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SmithingTransformRecipe> streamCodec() {
            return SmithingTransformRecipe.a.STREAM_CODEC;
        }
    }
}

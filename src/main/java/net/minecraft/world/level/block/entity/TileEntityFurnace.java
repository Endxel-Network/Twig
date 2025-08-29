package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.EnumDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ContainerUtil;
import net.minecraft.world.IWorldInventory;
import net.minecraft.world.entity.EntityExperienceOrb;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AutoRecipeOutput;
import net.minecraft.world.inventory.IContainerProperties;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingManager;
import net.minecraft.world.item.crafting.IRecipe;
import net.minecraft.world.item.crafting.RecipeCooking;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipes;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.World;
import net.minecraft.world.level.block.BlockFurnace;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.IBlockData;
import net.minecraft.world.phys.Vec3D;

// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.inventory.CookingRecipe;
// CraftBukkit end

public abstract class TileEntityFurnace extends TileEntityContainer implements IWorldInventory, RecipeCraftingHolder, AutoRecipeOutput {

    protected static final int SLOT_INPUT = 0;
    protected static final int SLOT_FUEL = 1;
    protected static final int SLOT_RESULT = 2;
    public static final int DATA_LIT_TIME = 0;
    private static final int[] SLOTS_FOR_UP = new int[]{0};
    private static final int[] SLOTS_FOR_DOWN = new int[]{2, 1};
    private static final int[] SLOTS_FOR_SIDES = new int[]{1};
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_COOKING_PROGRESS = 2;
    public static final int DATA_COOKING_TOTAL_TIME = 3;
    public static final int NUM_DATA_VALUES = 4;
    public static final int BURN_TIME_STANDARD = 200;
    public static final int BURN_COOL_SPEED = 2;
    private static final Codec<Map<ResourceKey<IRecipe<?>>, Integer>> RECIPES_USED_CODEC = Codec.unboundedMap(IRecipe.KEY_CODEC, Codec.INT);
    private static final short DEFAULT_COOKING_TIMER = 0;
    private static final short DEFAULT_COOKING_TOTAL_TIME = 0;
    private static final short DEFAULT_LIT_TIME_REMAINING = 0;
    private static final short DEFAULT_LIT_TOTAL_TIME = 0;
    protected NonNullList<ItemStack> items;
    public int litTimeRemaining;
    int litTotalTime;
    public int cookingTimer;
    public int cookingTotalTime;
    protected final IContainerProperties dataAccess;
    public final Reference2IntOpenHashMap<ResourceKey<IRecipe<?>>> recipesUsed;
    private final CraftingManager.a<SingleRecipeInput, ? extends RecipeCooking> quickCheck;

    protected TileEntityFurnace(TileEntityTypes<?> tileentitytypes, BlockPosition blockposition, IBlockData iblockdata, Recipes<? extends RecipeCooking> recipes) {
        super(tileentitytypes, blockposition, iblockdata);
        this.items = NonNullList.<ItemStack>withSize(3, ItemStack.EMPTY);
        this.dataAccess = new IContainerProperties() {
            @Override
            public int get(int i) {
                switch (i) {
                    case 0:
                        return TileEntityFurnace.this.litTimeRemaining;
                    case 1:
                        return TileEntityFurnace.this.litTotalTime;
                    case 2:
                        return TileEntityFurnace.this.cookingTimer;
                    case 3:
                        return TileEntityFurnace.this.cookingTotalTime;
                    default:
                        return 0;
                }
            }

            @Override
            public void set(int i, int j) {
                switch (i) {
                    case 0:
                        TileEntityFurnace.this.litTimeRemaining = j;
                        break;
                    case 1:
                        TileEntityFurnace.this.litTotalTime = j;
                        break;
                    case 2:
                        TileEntityFurnace.this.cookingTimer = j;
                        break;
                    case 3:
                        TileEntityFurnace.this.cookingTotalTime = j;
                }

            }

            @Override
            public int getCount() {
                return 4;
            }
        };
        this.recipesUsed = new Reference2IntOpenHashMap();
        this.quickCheck = CraftingManager.<SingleRecipeInput, RecipeCooking>createCheck((Recipes<RecipeCooking>) recipes); // CraftBukkit - decompile error
    }

    // CraftBukkit start - add fields and methods
    private int maxStack = MAX_STACK;
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();

    public List<ItemStack> getContents() {
        return this.items;
    }

    public void onOpen(CraftHumanEntity who) {
        transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return transaction;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    public void setMaxStackSize(int size) {
        maxStack = size;
    }
    // CraftBukkit end

    private boolean isLit() {
        return this.litTimeRemaining > 0;
    }

    @Override
    protected void loadAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        super.loadAdditional(nbttagcompound, holderlookup_a);
        this.items = NonNullList.<ItemStack>withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerUtil.loadAllItems(nbttagcompound, this.items, holderlookup_a);
        this.cookingTimer = nbttagcompound.getShortOr("cooking_time_spent", (short) 0);
        this.cookingTotalTime = nbttagcompound.getShortOr("cooking_total_time", (short) 0);
        this.litTimeRemaining = nbttagcompound.getShortOr("lit_time_remaining", (short) 0);
        this.litTotalTime = nbttagcompound.getShortOr("lit_total_time", (short) 0);
        this.recipesUsed.clear();
        this.recipesUsed.putAll((Map) nbttagcompound.read("RecipesUsed", TileEntityFurnace.RECIPES_USED_CODEC).orElse(Map.of()));
    }

    @Override
    protected void saveAdditional(NBTTagCompound nbttagcompound, HolderLookup.a holderlookup_a) {
        super.saveAdditional(nbttagcompound, holderlookup_a);
        nbttagcompound.putShort("cooking_time_spent", (short) this.cookingTimer);
        nbttagcompound.putShort("cooking_total_time", (short) this.cookingTotalTime);
        nbttagcompound.putShort("lit_time_remaining", (short) this.litTimeRemaining);
        nbttagcompound.putShort("lit_total_time", (short) this.litTotalTime);
        ContainerUtil.saveAllItems(nbttagcompound, this.items, holderlookup_a);
        nbttagcompound.store("RecipesUsed", TileEntityFurnace.RECIPES_USED_CODEC, this.recipesUsed);
    }

    public static void serverTick(WorldServer worldserver, BlockPosition blockposition, IBlockData iblockdata, TileEntityFurnace tileentityfurnace) {
        boolean flag = tileentityfurnace.isLit();
        boolean flag1 = false;

        if (tileentityfurnace.isLit()) {
            --tileentityfurnace.litTimeRemaining;
        }

        ItemStack itemstack = tileentityfurnace.items.get(1);
        ItemStack itemstack1 = tileentityfurnace.items.get(0);
        boolean flag2 = !itemstack1.isEmpty();
        boolean flag3 = !itemstack.isEmpty();

        if (tileentityfurnace.isLit() || flag3 && flag2) {
            SingleRecipeInput singlerecipeinput = new SingleRecipeInput(itemstack1);
            RecipeHolder<? extends RecipeCooking> recipeholder;

            if (flag2) {
                recipeholder = (RecipeHolder) tileentityfurnace.quickCheck.getRecipeFor(singlerecipeinput, worldserver).orElse(null); // CraftBukkit - decompile error
            } else {
                recipeholder = null;
            }

            int i = tileentityfurnace.getMaxStackSize();

            if (!tileentityfurnace.isLit() && canBurn(worldserver.registryAccess(), recipeholder, singlerecipeinput, tileentityfurnace.items, i)) {
                // CraftBukkit start
                CraftItemStack fuel = CraftItemStack.asCraftMirror(itemstack);

                FurnaceBurnEvent furnaceBurnEvent = new FurnaceBurnEvent(CraftBlock.at(worldserver, blockposition), fuel, tileentityfurnace.getBurnDuration(worldserver.fuelValues(), itemstack));
                worldserver.getCraftServer().getPluginManager().callEvent(furnaceBurnEvent);

                if (furnaceBurnEvent.isCancelled()) {
                    return;
                }

                tileentityfurnace.litTimeRemaining = furnaceBurnEvent.getBurnTime();
                tileentityfurnace.litTotalTime = tileentityfurnace.litTimeRemaining;
                if (tileentityfurnace.isLit() && furnaceBurnEvent.isBurning()) {
                    // CraftBukkit end
                    flag1 = true;
                    if (flag3) {
                        Item item = itemstack.getItem();

                        itemstack.shrink(1);
                        if (itemstack.isEmpty()) {
                            tileentityfurnace.items.set(1, item.getCraftingRemainder());
                        }
                    }
                }
            }

            if (tileentityfurnace.isLit() && canBurn(worldserver.registryAccess(), recipeholder, singlerecipeinput, tileentityfurnace.items, i)) {
                // CraftBukkit start
                if (recipeholder != null && tileentityfurnace.cookingTimer == 0) {
                    CraftItemStack source = CraftItemStack.asCraftMirror(tileentityfurnace.items.get(0));
                    CookingRecipe<?> recipe = (CookingRecipe<?>) recipeholder.toBukkitRecipe();

                    FurnaceStartSmeltEvent event = new FurnaceStartSmeltEvent(CraftBlock.at(worldserver, blockposition), source, recipe);
                    worldserver.getCraftServer().getPluginManager().callEvent(event);

                    tileentityfurnace.cookingTotalTime = event.getTotalCookTime();
                }
                // CraftBukkit end

                ++tileentityfurnace.cookingTimer;
                if (tileentityfurnace.cookingTimer == tileentityfurnace.cookingTotalTime) {
                    tileentityfurnace.cookingTimer = 0;
                    tileentityfurnace.cookingTotalTime = getTotalCookTime(worldserver, tileentityfurnace);
                    if (burn(tileentityfurnace.level, tileentityfurnace.worldPosition, worldserver.registryAccess(), recipeholder, singlerecipeinput, tileentityfurnace.items, i)) { // CraftBukkit
                        tileentityfurnace.setRecipeUsed(recipeholder);
                    }

                    flag1 = true;
                }
            } else {
                tileentityfurnace.cookingTimer = 0;
            }
        } else if (!tileentityfurnace.isLit() && tileentityfurnace.cookingTimer > 0) {
            tileentityfurnace.cookingTimer = MathHelper.clamp(tileentityfurnace.cookingTimer - 2, 0, tileentityfurnace.cookingTotalTime);
        }

        if (flag != tileentityfurnace.isLit()) {
            flag1 = true;
            iblockdata = (IBlockData) iblockdata.setValue(BlockFurnace.LIT, tileentityfurnace.isLit());
            worldserver.setBlock(blockposition, iblockdata, 3);
        }

        if (flag1) {
            setChanged(worldserver, blockposition, iblockdata);
        }

    }

    private static boolean canBurn(IRegistryCustom iregistrycustom, @Nullable RecipeHolder<? extends RecipeCooking> recipeholder, SingleRecipeInput singlerecipeinput, NonNullList<ItemStack> nonnulllist, int i) {
        if (!((ItemStack) nonnulllist.get(0)).isEmpty() && recipeholder != null) {
            ItemStack itemstack = ((RecipeCooking) recipeholder.value()).assemble(singlerecipeinput, iregistrycustom);

            if (itemstack.isEmpty()) {
                return false;
            } else {
                ItemStack itemstack1 = nonnulllist.get(2);

                return itemstack1.isEmpty() ? true : (!ItemStack.isSameItemSameComponents(itemstack1, itemstack) ? false : (itemstack1.getCount() < i && itemstack1.getCount() < itemstack1.getMaxStackSize() ? true : itemstack1.getCount() < itemstack.getMaxStackSize()));
            }
        } else {
            return false;
        }
    }

    private static boolean burn(World world, BlockPosition blockposition, IRegistryCustom iregistrycustom, @Nullable RecipeHolder<? extends RecipeCooking> recipeholder, SingleRecipeInput singlerecipeinput, NonNullList<ItemStack> nonnulllist, int i) { // CraftBukkit
        if (recipeholder != null && canBurn(iregistrycustom, recipeholder, singlerecipeinput, nonnulllist, i)) {
            ItemStack itemstack = nonnulllist.get(0);
            ItemStack itemstack1 = ((RecipeCooking) recipeholder.value()).assemble(singlerecipeinput, iregistrycustom);
            ItemStack itemstack2 = nonnulllist.get(2);

            // CraftBukkit start - fire FurnaceSmeltEvent
            CraftItemStack source = CraftItemStack.asCraftMirror(itemstack);
            org.bukkit.inventory.ItemStack result = CraftItemStack.asBukkitCopy(itemstack1);

            FurnaceSmeltEvent furnaceSmeltEvent = new FurnaceSmeltEvent(CraftBlock.at(world, blockposition), source, result);
            world.getCraftServer().getPluginManager().callEvent(furnaceSmeltEvent);

            if (furnaceSmeltEvent.isCancelled()) {
                return false;
            }

            result = furnaceSmeltEvent.getResult();
            itemstack1 = CraftItemStack.asNMSCopy(result);

            if (!itemstack1.isEmpty()) {
                if (itemstack2.isEmpty()) {
                    nonnulllist.set(2, itemstack1.copy());
                } else if (CraftItemStack.asCraftMirror(itemstack2).isSimilar(result)) {
                    itemstack2.grow(itemstack1.getCount());
                } else {
                    return false;
                }
            }

            /*
            if (itemstack2.isEmpty()) {
                nonnulllist.set(2, itemstack1.copy());
            } else if (ItemStack.isSameItemSameComponents(itemstack2, itemstack1)) {
                itemstack2.grow(1);
            }
            */
            // CraftBukkit end

            if (itemstack.is(Blocks.WET_SPONGE.asItem()) && !((ItemStack) nonnulllist.get(1)).isEmpty() && ((ItemStack) nonnulllist.get(1)).is(Items.BUCKET)) {
                nonnulllist.set(1, new ItemStack(Items.WATER_BUCKET));
            }

            itemstack.shrink(1);
            return true;
        } else {
            return false;
        }
    }

    protected int getBurnDuration(FuelValues fuelvalues, ItemStack itemstack) {
        return fuelvalues.burnDuration(itemstack);
    }

    private static int getTotalCookTime(WorldServer worldserver, TileEntityFurnace tileentityfurnace) {
        if (worldserver == null) return 200; // CraftBukkit - SPIGOT-4302
        SingleRecipeInput singlerecipeinput = new SingleRecipeInput(tileentityfurnace.getItem(0));

        return (Integer) tileentityfurnace.quickCheck.getRecipeFor(singlerecipeinput, worldserver).map((recipeholder) -> {
            return ((RecipeCooking) recipeholder.value()).cookingTime();
        }).orElse(200);
    }

    @Override
    public int[] getSlotsForFace(EnumDirection enumdirection) {
        return enumdirection == EnumDirection.DOWN ? TileEntityFurnace.SLOTS_FOR_DOWN : (enumdirection == EnumDirection.UP ? TileEntityFurnace.SLOTS_FOR_UP : TileEntityFurnace.SLOTS_FOR_SIDES);
    }

    @Override
    public boolean canPlaceItemThroughFace(int i, ItemStack itemstack, @Nullable EnumDirection enumdirection) {
        return this.canPlaceItem(i, itemstack);
    }

    @Override
    public boolean canTakeItemThroughFace(int i, ItemStack itemstack, EnumDirection enumdirection) {
        return enumdirection == EnumDirection.DOWN && i == 1 ? itemstack.is(Items.WATER_BUCKET) || itemstack.is(Items.BUCKET) : true;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> nonnulllist) {
        this.items = nonnulllist;
    }

    @Override
    public void setItem(int i, ItemStack itemstack) {
        ItemStack itemstack1 = this.items.get(i);
        boolean flag = !itemstack.isEmpty() && ItemStack.isSameItemSameComponents(itemstack1, itemstack);

        this.items.set(i, itemstack);
        itemstack.limitSize(this.getMaxStackSize(itemstack));
        if (i == 0 && !flag) {
            World world = this.level;

            if (world instanceof WorldServer) {
                WorldServer worldserver = (WorldServer) world;

                this.cookingTotalTime = getTotalCookTime(worldserver, this);
                this.cookingTimer = 0;
                this.setChanged();
            }
        }

    }

    @Override
    public boolean canPlaceItem(int i, ItemStack itemstack) {
        if (i == 2) {
            return false;
        } else if (i != 1) {
            return true;
        } else {
            ItemStack itemstack1 = this.items.get(1);

            return this.level.fuelValues().isFuel(itemstack) || itemstack.is(Items.BUCKET) && !itemstack1.is(Items.BUCKET);
        }
    }

    @Override
    public void setRecipeUsed(@Nullable RecipeHolder<?> recipeholder) {
        if (recipeholder != null) {
            ResourceKey<IRecipe<?>> resourcekey = recipeholder.id();

            this.recipesUsed.addTo(resourcekey, 1);
        }

    }

    @Nullable
    @Override
    public RecipeHolder<?> getRecipeUsed() {
        return null;
    }

    @Override
    public void awardUsedRecipes(EntityHuman entityhuman, List<ItemStack> list) {}

    public void awardUsedRecipesAndPopExperience(EntityPlayer entityplayer, ItemStack itemstack, int amount) { // CraftBukkit
        List<RecipeHolder<?>> list = this.getRecipesToAwardAndPopExperience(entityplayer.serverLevel(), entityplayer.position(), this.worldPosition, entityplayer, itemstack, amount); // CraftBukkit

        entityplayer.awardRecipes(list);

        for (RecipeHolder<?> recipeholder : list) {
            if (recipeholder != null) {
                entityplayer.triggerRecipeCrafted(recipeholder, this.items);
            }
        }

        this.recipesUsed.clear();
    }

    public List<RecipeHolder<?>> getRecipesToAwardAndPopExperience(WorldServer worldserver, Vec3D vec3d) {
        // CraftBukkit start
        return this.getRecipesToAwardAndPopExperience(worldserver, vec3d, this.worldPosition, null, null, 0);
    }

    public List<RecipeHolder<?>> getRecipesToAwardAndPopExperience(WorldServer worldserver, Vec3D vec3d, BlockPosition blockposition, EntityPlayer entityplayer, ItemStack itemstack, int amount) {
        // CraftBukkit end
        List<RecipeHolder<?>> list = Lists.newArrayList();
        ObjectIterator objectiterator = this.recipesUsed.reference2IntEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Reference2IntMap.Entry<ResourceKey<IRecipe<?>>> reference2intmap_entry = (Entry) objectiterator.next();

            worldserver.recipeAccess().byKey(reference2intmap_entry.getKey()).ifPresent((recipeholder) -> {// CraftBukkit - decompile error
                list.add(recipeholder);
                createExperience(worldserver, vec3d, reference2intmap_entry.getIntValue(), ((RecipeCooking) recipeholder.value()).experience(), blockposition, entityplayer, itemstack, amount); // CraftBukkit
            });
        }

        return list;
    }

    private static void createExperience(WorldServer worldserver, Vec3D vec3d, int i, float f, BlockPosition blockposition, EntityHuman entityhuman, ItemStack itemstack, int amount) { // CraftBukkit
        int j = MathHelper.floor((float) i * f);
        float f1 = MathHelper.frac((float) i * f);

        if (f1 != 0.0F && Math.random() < (double) f1) {
            ++j;
        }

        // CraftBukkit start - fire FurnaceExtractEvent / BlockExpEvent
        BlockExpEvent event;
        if (amount != 0) {
            event = new FurnaceExtractEvent((Player) entityhuman.getBukkitEntity(), CraftBlock.at(worldserver, blockposition), CraftItemType.minecraftToBukkit(itemstack.getItem()), amount, j);
        } else {
            event = new BlockExpEvent(CraftBlock.at(worldserver, blockposition), j);
        }
        worldserver.getCraftServer().getPluginManager().callEvent(event);
        j = event.getExpToDrop();
        // CraftBukkit end

        EntityExperienceOrb.award(worldserver, vec3d, j);
    }

    @Override
    public void fillStackedContents(StackedItemContents stackeditemcontents) {
        for (ItemStack itemstack : this.items) {
            stackeditemcontents.accountStack(itemstack);
        }

    }

    @Override
    public void preRemoveSideEffects(BlockPosition blockposition, IBlockData iblockdata) {
        super.preRemoveSideEffects(blockposition, iblockdata);
        World world = this.level;

        if (world instanceof WorldServer worldserver) {
            this.getRecipesToAwardAndPopExperience(worldserver, Vec3D.atCenterOf(blockposition));
        }

    }
}

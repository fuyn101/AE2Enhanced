package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Extended Crafting 工作台虚拟合成处理器.
 *
 * <p>支持 Basic(3x3)、Advanced(5x5)、Elite(7x7)、Ultimate(9x9) 四种工作台.
 * 通过 {@link IVirtualCraftingHandler} 实现即时虚拟合成：
 * 不将物品推送到物理工作台,直接验证输入材料是否满足配方即可返回产物.</p>
 *
 * <p>核心策略：忽略槽位与顺序,将输入材料按数量拆分为单件列表,
 * 与配方的非空 ingredients 进行多对多匹配.输出匹配忽略 NBT.</p>
 */
public class ExtendedCraftingTableHandler implements IVirtualBatchCraftingHandler {

    private static final String[] TABLE_IDS = {
        "extendedcrafting:table_basic",
        "extendedcrafting:table_advanced",
        "extendedcrafting:table_elite",
        "extendedcrafting:table_ultimate"
    };

    // 反射缓存
    private static Class<?> CLASS_ABSTRACT_TABLE;
    private static Method METHOD_GET_LINE_SIZE;
    private static Method METHOD_GET_RECIPES;
    private static Method METHOD_GET_TIER;
    private static Object RECIPE_MANAGER_INSTANCE;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_ABSTRACT_TABLE = Class.forName("com.blakebr0.extendedcrafting.tile.AbstractExtendedTable");
            METHOD_GET_LINE_SIZE = CLASS_ABSTRACT_TABLE.getMethod("getLineSize");
            Class<?> recipeManagerClass = Class.forName("com.blakebr0.extendedcrafting.crafting.table.TableRecipeManager");
            Method getInstance = recipeManagerClass.getMethod("getInstance");
            RECIPE_MANAGER_INSTANCE = getInstance.invoke(null);
            METHOD_GET_RECIPES = recipeManagerClass.getMethod("getRecipes");
            Class<?> tieredRecipeClass = Class.forName("com.blakebr0.extendedcrafting.crafting.table.ITieredRecipe");
            METHOD_GET_TIER = tieredRecipeClass.getMethod("getTier");
            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ExtendedCraftingTable reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        for (String id : TABLE_IDS) {
            if (id.equals(blockId)) return true;
        }
        return false;
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_ABSTRACT_TABLE.isInstance(te);
    }

    @Override
    public EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.virtualOnly();
    }

    @Override
    public long getDefaultParallel() {
        return 8;
    }

    // ---- IRemoteHandler 物理模式(虚拟合成不占用设备,这些方法均为空实现) ----

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        return true;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        return Collections.emptyList();
    }

    // ---- IVirtualCraftingHandler ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_ABSTRACT_TABLE.isInstance(te)) return false;

        int lineSize = getLineSize(te);
        if (lineSize <= 0) return false;

        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        ItemStack expectedOutput = outputs[0].createItemStack();
        if (expectedOutput.isEmpty()) return false;

        List<IRecipe> recipes = findRecipesByOutput(expectedOutput, lineSize);
        if (recipes.isEmpty()) return false;

        for (IRecipe recipe : recipes) {
            if (ingredientsMatch(recipe, ingredients)) {
                return true;
            }
        }
        return false;
    }

    public List<ItemStack> virtualCraft(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, IActionSource source) {
        return virtualCraftBatch(world, pos, ingredients, outputs, 1, source);
    }

    @Override
    public List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return java.util.Arrays.asList(
                EnumParticleTypes.PORTAL,
                EnumParticleTypes.ENCHANTMENT_TABLE,
                EnumParticleTypes.SPELL_WITCH,
                EnumParticleTypes.END_ROD
        );
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                IAEItemStack cost = AEItemStack.fromItemStack(stack.copy());
                cost.setStackSize((long) stack.getCount() * count);
                costs.add(cost);
            }
        }
        return costs;
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count, IActionSource source) {
        initReflection();
        List<ItemStack> products = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_ABSTRACT_TABLE.isInstance(te)) return products;

        int lineSize = getLineSize(te);
        if (lineSize <= 0) return products;

        if (outputs == null || outputs.length == 0 || outputs[0] == null) return products;
        ItemStack expectedOutput = outputs[0].createItemStack();
        if (expectedOutput.isEmpty()) return products;

        List<IRecipe> recipes = findRecipesByOutput(expectedOutput, lineSize);
        for (IRecipe recipe : recipes) {
            if (ingredientsMatch(recipe, ingredients)) {
                return scaleOutputsByCount(outputs, count);
            }
        }
        return products;
    }

    // ---- 配方查找与匹配 ----

    @SuppressWarnings("unchecked")
    private static List<IRecipe> findRecipesByOutput(ItemStack output, int lineSize) {
        List<IRecipe> matches = new ArrayList<>();
        try {
            List<IRecipe> recipes = (List<IRecipe>) METHOD_GET_RECIPES.invoke(RECIPE_MANAGER_INSTANCE);
            if (recipes == null) return matches;

            int gridTier = getTierFromGridSize(lineSize * lineSize);

            for (IRecipe recipe : recipes) {
                int tier = getRecipeTier(recipe);
                if (tier > 0 && tier != gridTier) continue;

                if (outputsMatch(recipe.getRecipeOutput(), output)) {
                    matches.add(recipe);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return matches;
    }

    private static int getRecipeTier(IRecipe recipe) {
        try {
            return (int) METHOD_GET_TIER.invoke(recipe);
        } catch (Exception e) {
            return 0;
        }
    }

    private static int getTierFromGridSize(int size) {
        if (size < 10) return 1;
        if (size < 26) return 2;
        if (size < 50) return 3;
        return 4;
    }

    /**
     * 检查 ingredients 中的材料是否满足 recipe 的所有非空 ingredients.
     *
     * <p>关键处理：AE2 传来的 InventoryCrafting 中同一物品可能堆叠在一个槽位(count>1),
     * 而 Extended Crafting 配方中每个 ingredient 槽位固定最多 1 个物品.
     * 因此将输入按 count 拆分为单件列表后再进行一对一匹配.</p>
     */
    private static boolean ingredientsMatch(IRecipe recipe, InventoryCrafting ingredients) {
        NonNullList<Ingredient> recipeIngredients = recipe.getIngredients();
        if (recipeIngredients == null || recipeIngredients.isEmpty()) return false;

        // 收集 recipe 中所有非空 ingredient(每个代表 1 个物品需求)
        List<Ingredient> required = new ArrayList<>();
        for (Ingredient ing : recipeIngredients) {
            if (ing != null && ing != Ingredient.EMPTY) {
                required.add(ing);
            }
        }

        // 收集输入物品并按 count 拆分为单件
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                int count = stack.getCount();
                for (int c = 0; c < count; c++) {
                    ItemStack single = stack.copy();
                    single.setCount(1);
                    available.add(single);
                }
            }
        }

        // 输入物品数必须不少于配方需求数(AE2 提取时可能带入了网络中的多余物品)
        if (available.size() < required.size()) return false;

        // 贪心匹配
        for (Ingredient ing : required) {
            boolean found = false;
            for (int i = 0; i < available.size(); i++) {
                ItemStack stack = available.get(i);
                if (ing.apply(stack)) {
                    available.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        return true;
    }

    // ---- 辅助方法 ----

    private static int getLineSize(TileEntity te) {
        try {
            return (int) METHOD_GET_LINE_SIZE.invoke(te);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 输出匹配：忽略 NBT,比较 Item + Metadata + Count.
     * <p>必须比较数量,以区分相同产物但不同输出数量的配方
     *(如一种输出 4 个,另一种输出 8 个).</p>
     */
    private static boolean outputsMatch(ItemStack recipeOutput, ItemStack expected) {
        if (recipeOutput.isEmpty() || expected.isEmpty()) return false;
        if (recipeOutput.getItem() != expected.getItem()) return false;
        if (recipeOutput.getMetadata() != expected.getMetadata()) return false;
        if (recipeOutput.getCount() != expected.getCount()) return false;
        return true;
    }
}

package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualCraftingHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extended Crafting 工作台虚拟合成处理器。
 *
 * <p>支持 Basic(3x3)、Advanced(5x5)、Elite(7x7)、Ultimate(9x9) 四种工作台。
 * 通过 {@link IVirtualCraftingHandler} 实现即时虚拟合成：
 * 不将物品推送到物理工作台，而是直接查询 {@code TableRecipeManager} 获取产物和残余。</p>
 *
 * <p>配方匹配策略（P8 重构）：
 * 由于 AE 处理样板提供的是扁平数组，没有位置信息，因此不再使用严格位置匹配的
 * {@code findMatchingRecipe}。改为：<strong>先通过产物输出找到唯一配方，再验证输入
 * 物品种类与数量是否满足该配方的 ingredients</strong>（忽略空位与位置）。</p>
 */
public class ExtendedCraftingTableHandler implements IVirtualCraftingHandler {

    private static final String[] TABLE_IDS = {
        "extendedcrafting:table_basic",
        "extendedcrafting:table_advanced",
        "extendedcrafting:table_elite",
        "extendedcrafting:table_ultimate"
    };

    // 反射缓存
    private static Class<?> CLASS_ABSTRACT_TABLE;
    private static Method METHOD_GET_LINE_SIZE;
    private static Method METHOD_GET_REMAINING_ITEMS;
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
            METHOD_GET_REMAINING_ITEMS = recipeManagerClass.getMethod("getRemainingItems", InventoryCrafting.class, World.class);
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

    // ---- IRemoteHandler 物理模式（虚拟合成不占用设备，这些方法均为空实现） ----

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        return true; // 虚拟合成不占用物理设备
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        return true; // 虚拟合成不推送物理材料
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        return true; // 虚拟合成不需要启动
    }

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        return true; // 虚拟合成不占用设备，始终空闲
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        return Collections.emptyList(); // 产物在 virtualCraft 中直接返回
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

        IRecipe recipe = findRecipeByOutput(expectedOutput, lineSize);
        if (recipe == null) return false;

        // 验证输入物品种类与数量是否满足配方 ingredients（忽略位置）
        return ingredientsMatch(recipe, ingredients);
    }

    @Override
    public List<ItemStack> virtualCraft(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, IActionSource source) {
        initReflection();
        List<ItemStack> products = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_ABSTRACT_TABLE.isInstance(te)) return products;

        int lineSize = getLineSize(te);
        if (lineSize <= 0) return products;

        if (outputs == null || outputs.length == 0 || outputs[0] == null) return products;
        ItemStack expectedOutput = outputs[0].createItemStack();
        if (expectedOutput.isEmpty()) return products;

        IRecipe recipe = findRecipeByOutput(expectedOutput, lineSize);
        if (recipe == null) return products;

        // 构造正确位置的 InventoryCrafting
        InventoryCrafting matrix = createMatrix(lineSize);
        fillMatrixByRecipe(matrix, recipe, ingredients);

        // 产物
        ItemStack result = recipe.getCraftingResult(matrix);
        if (result != null && !result.isEmpty()) {
            products.add(result.copy());
        }

        // 残余物品
        List<ItemStack> remaining = getRemainingItems(matrix, world);
        for (ItemStack stack : remaining) {
            if (!stack.isEmpty()) {
                products.add(stack.copy());
            }
        }

        return products;
    }

    // ---- 配方查找与匹配 ----

    /**
     * 通过产物输出在 TableRecipeManager 中查找匹配的配方。
     *
     * @param output   预期产物
     * @param lineSize 工作台边长（3/5/7/9）
     * @return 匹配的 IRecipe；未找到返回 null
     */
    @SuppressWarnings("unchecked")
    private static IRecipe findRecipeByOutput(ItemStack output, int lineSize) {
        try {
            List<IRecipe> recipes = (List<IRecipe>) METHOD_GET_RECIPES.invoke(RECIPE_MANAGER_INSTANCE);
            if (recipes == null) return null;

            int gridTier = getTierFromGridSize(lineSize * lineSize);

            for (IRecipe recipe : recipes) {
                // tier 校验：tier > 0 时必须与 grid size 匹配
                int tier = getRecipeTier(recipe);
                if (tier > 0 && tier != gridTier) continue;

                // 产物匹配
                if (outputsMatch(recipe.getRecipeOutput(), output)) {
                    return recipe;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static int getRecipeTier(IRecipe recipe) {
        try {
            return (int) METHOD_GET_TIER.invoke(recipe);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 根据 grid 总 slot 数计算 ExtendedCrafting 的 tier。
     * 与 TableRecipeBase.getTierFromGridSize 一致。
     */
    private static int getTierFromGridSize(int size) {
        if (size < 10) return 1;
        if (size < 26) return 2;
        if (size < 50) return 3;
        return 4;
    }

    /**
     * 检查 ingredients 中的非空物品是否满足 recipe 的所有非空 ingredients。
     * 忽略位置与空位，只看种类和数量。
     */
    private static boolean ingredientsMatch(IRecipe recipe, InventoryCrafting ingredients) {
        NonNullList<Ingredient> recipeIngredients = recipe.getIngredients();
        if (recipeIngredients == null || recipeIngredients.isEmpty()) return false;

        // 收集 recipe 中所有非空 ingredient
        List<Ingredient> required = new ArrayList<>();
        for (Ingredient ing : recipeIngredients) {
            if (ing != null && ing != Ingredient.EMPTY) {
                required.add(ing);
            }
        }

        // 收集 ingredients 中所有非空物品
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                available.add(stack.copy());
            }
        }

        // 数量必须一致
        if (required.size() != available.size()) return false;

        // 贪心匹配：每个 required ingredient 在 available 中找一个匹配项并移除
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

    /**
     * 根据 recipe 的 ingredient 布局，将 available items 放入 matrix 的正确位置。
     */
    private static void fillMatrixByRecipe(InventoryCrafting matrix, IRecipe recipe, InventoryCrafting ingredients) {
        NonNullList<Ingredient> recipeIngredients = recipe.getIngredients();

        // 复制可用物品列表
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                available.add(stack.copy());
            }
        }

        // 按 ingredient 顺序填充 matrix
        for (int slot = 0; slot < recipeIngredients.size() && slot < matrix.getSizeInventory(); slot++) {
            Ingredient ing = recipeIngredients.get(slot);
            if (ing == null || ing == Ingredient.EMPTY) continue;

            for (int i = 0; i < available.size(); i++) {
                ItemStack stack = available.get(i);
                if (ing.apply(stack)) {
                    matrix.setInventorySlotContents(slot, stack);
                    available.remove(i);
                    break;
                }
            }
        }
    }

    // ---- 辅助方法 ----

    private static int getLineSize(TileEntity te) {
        try {
            return (int) METHOD_GET_LINE_SIZE.invoke(te);
        } catch (Exception e) {
            return -1;
        }
    }

    private static InventoryCrafting createMatrix(int lineSize) {
        Container dummy = new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer playerIn) {
                return false;
            }
            @Override
            public void onCraftMatrixChanged(IInventory inventoryIn) {
                // no-op
            }
        };
        return new InventoryCrafting(dummy, lineSize, lineSize);
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> getRemainingItems(InventoryCrafting matrix, World world) {
        try {
            return (List<ItemStack>) METHOD_GET_REMAINING_ITEMS.invoke(RECIPE_MANAGER_INSTANCE, matrix, world);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static boolean outputsMatch(ItemStack recipeOutput, ItemStack expected) {
        if (recipeOutput.isEmpty() || expected.isEmpty()) return false;
        if (recipeOutput.getItem() != expected.getItem()) return false;
        if (recipeOutput.getMetadata() != expected.getMetadata()) return false;
        if (recipeOutput.hasTagCompound() != expected.hasTagCompound()) return false;
        if (recipeOutput.hasTagCompound() && expected.hasTagCompound()) {
            return recipeOutput.getTagCompound().equals(expected.getTagCompound());
        }
        return true;
    }
}

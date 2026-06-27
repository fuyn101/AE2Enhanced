package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
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
    private static Class<?> CLASS_TABLE_RECIPE_BASE;
    private static java.lang.reflect.Field FIELD_RECIPE_TIER;
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
            // 用于区分配方的"显式 tier"与"计算 tier"，避免 tier=0 的配方被错误过滤
            CLASS_TABLE_RECIPE_BASE = Class.forName("com.blakebr0.extendedcrafting.crafting.table.TableRecipeBase");
            FIELD_RECIPE_TIER = CLASS_TABLE_RECIPE_BASE.getDeclaredField("tier");
            FIELD_RECIPE_TIER.setAccessible(true);
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
        AE2Enhanced.LOGGER.info("[AE2E-Diag] ECTable.canCraft recipesFound={} output={} lineSize={}", recipes.size(), expectedOutput, lineSize);
        if (recipes.isEmpty()) return false;

        // 打印传入的 ingredients，便于诊断匹配失败
        StringBuilder ingSb = new StringBuilder();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (ingSb.length() > 0) ingSb.append(", ");
                ingSb.append("[").append(i).append("]").append(stack);
            }
        }
        AE2Enhanced.LOGGER.info("[AE2E-Diag] ECTable.canCraft inputs={}", ingSb);

        for (IRecipe recipe : recipes) {
            if (ingredientsMatch(recipe, ingredients)) {
                AE2Enhanced.LOGGER.info("[AE2E-Diag] ECTable.canCraft matched recipe output={}", recipe.getRecipeOutput());
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
                // 只使用配方的显式 tier 字段做过滤；tier=0 表示未限制工作台等级，
                // 此时 ITieredRecipe.getTier() 会返回按材料数/尺寸计算的值，不能用于过滤。
                int explicitTier = getExplicitRecipeTier(recipe);
                if (explicitTier > 0 && explicitTier != gridTier) continue;

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

    private static int getExplicitRecipeTier(IRecipe recipe) {
        try {
            if (FIELD_RECIPE_TIER != null) {
                return FIELD_RECIPE_TIER.getInt(recipe);
            }
        } catch (Exception ignored) {
        }
        // fallback：无法读取字段时退回到 getTier()（可能过滤更严格，但安全）
        return getRecipeTier(recipe);
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
        if (available.size() < required.size()) {
            AE2Enhanced.LOGGER.info("[AE2E-Diag] ECTable.ingredientsMatch fail available={} < required={}", available.size(), required.size());
            return false;
        }

        // 贪心匹配
        int matchedCount = 0;
        for (Ingredient ing : required) {
            boolean found = false;
            for (int i = 0; i < available.size(); i++) {
                ItemStack stack = available.get(i);
                if (ing.apply(stack)) {
                    available.remove(i);
                    found = true;
                    matchedCount++;
                    break;
                }
            }
            if (!found) {
                AE2Enhanced.LOGGER.info("[AE2E-Diag] ECTable.ingredientsMatch fail at requiredIndex={} matched={}/{} availableRemaining={}",
                        matchedCount, matchedCount, required.size(), available.size());
                // 打印前几个未匹配的可用物品，帮助诊断 ingredient 不匹配原因
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(available.size(), 9); i++) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(available.get(i));
                }
                AE2Enhanced.LOGGER.info("[AE2E-Diag] ECTable.ingredientsMatch availableSamples=[{}]", sb);
                return false;
            }
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

package com.github.aeddddd.ae2enhanced.centralinterface.handler.astralsorcery;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Astral Sorcery 星辉祭坛远程处理器。
 *
 * <p>支持全部 5 个等级（DISCOVERY~BRILLIANCE）。
 * 配方匹配策略：在 {@code canStart} 中通过输入物品种类+数量遍历祭坛配方列表，
 * 找到唯一匹配后缓存；{@code pushMaterials} 按缓存配方的 slot 布局精确放置物品；
 * {@code startProcess} 调用 {@code AltarRecipeRegistry.findMatchingRecipe} 触发合成。</p>
 */
public class AstralSorceryHandler implements IRemoteHandler {

    private static final String BLOCK_ID = "astralsorcery:blockaltar";

    // 反射缓存
    private static Class<?> CLASS_TILE_ALTAR;
    private static Class<?> CLASS_ABSTRACT_ALTAR_RECIPE;
    private static Class<?> CLASS_ALTAR_RECIPE_REGISTRY;
    private static Class<?> CLASS_ACCESSIBLE_RECIPE;
    private static Class<?> CLASS_ITEM_HANDLE;
    private static Class<?> CLASS_ACTIVE_CRAFTING_TASK;
    private static Method METHOD_GET_INVENTORY_HANDLER;
    private static Method METHOD_GET_ACTIVE_CRAFTING_TASK;
    private static Method METHOD_GET_MULTIBLOCK_STATE;
    private static Method METHOD_GET_STARLIGHT_STORED;
    private static Method METHOD_GET_ALTAR_LEVEL;
    private static Method METHOD_GET_NEEDED_LEVEL;
    private static Method METHOD_GET_OUTPUT_FOR_MATCHING;
    private static Method METHOD_GET_NATIVE_RECIPE;
    private static Method METHOD_FULFILLES_STARLIGHT;
    private static Method METHOD_FIND_MATCHING_RECIPE;
    private static Method METHOD_MATCH_CRAFTING;
    private static Method METHOD_GET_SLOT_ID;
    private static Method METHOD_MARK_FOR_UPDATE;
    private static Method METHOD_CRAFTING_TICK_TIME;
    private static Field FIELD_CRAFTING_TASK;
    private static Field FIELD_RECIPES_MAP;
    private static Field FIELD_ADDITIONAL_SLOTS;
    private static Constructor<?> CTOR_ACTIVE_CRAFTING_TASK;
    private static boolean reflectionReady = false;

    // 配方缓存：BlockPos → AbstractAltarRecipe（canStart 与 pushMaterials 之间传递）
    private final Map<BlockPos, Object> recipeCache = new ConcurrentHashMap<>();

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_ALTAR = Class.forName("hellfirepvp.astralsorcery.common.tile.TileAltar");
            CLASS_ABSTRACT_ALTAR_RECIPE = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.AbstractAltarRecipe");
            CLASS_ALTAR_RECIPE_REGISTRY = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.AltarRecipeRegistry");
            CLASS_ACCESSIBLE_RECIPE = Class.forName("hellfirepvp.astralsorcery.common.crafting.helper.AccessibleRecipe");
            CLASS_ITEM_HANDLE = Class.forName("hellfirepvp.astralsorcery.common.crafting.ItemHandle");
            CLASS_ACTIVE_CRAFTING_TASK = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.ActiveCraftingTask");

            METHOD_GET_INVENTORY_HANDLER = CLASS_TILE_ALTAR.getMethod("getInventoryHandler");
            METHOD_GET_ACTIVE_CRAFTING_TASK = CLASS_TILE_ALTAR.getMethod("getActiveCraftingTask");
            METHOD_GET_MULTIBLOCK_STATE = CLASS_TILE_ALTAR.getMethod("getMultiblockState");
            METHOD_GET_STARLIGHT_STORED = CLASS_TILE_ALTAR.getMethod("getStarlightStored");
            METHOD_GET_ALTAR_LEVEL = CLASS_TILE_ALTAR.getMethod("getAltarLevel");
            METHOD_MARK_FOR_UPDATE = CLASS_TILE_ALTAR.getMethod("markForUpdate");

            METHOD_GET_NEEDED_LEVEL = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("getNeededLevel");
            METHOD_GET_OUTPUT_FOR_MATCHING = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("getOutputForMatching");
            METHOD_GET_NATIVE_RECIPE = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("getNativeRecipe");
            METHOD_FULFILLES_STARLIGHT = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("fulfillesStarlightRequirement", CLASS_TILE_ALTAR);
            METHOD_CRAFTING_TICK_TIME = CLASS_ABSTRACT_ALTAR_RECIPE.getMethod("craftingTickTime");

            METHOD_FIND_MATCHING_RECIPE = CLASS_ALTAR_RECIPE_REGISTRY.getMethod("findMatchingRecipe", CLASS_TILE_ALTAR, boolean.class);
            FIELD_RECIPES_MAP = CLASS_ALTAR_RECIPE_REGISTRY.getField("recipes");

            METHOD_MATCH_CRAFTING = CLASS_ITEM_HANDLE.getMethod("matchCrafting", ItemStack.class);
            METHOD_GET_SLOT_ID = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.recipes.AttunementRecipe$AttunementAltarSlot").getMethod("getSlotId");
            FIELD_ADDITIONAL_SLOTS = Class.forName("hellfirepvp.astralsorcery.common.crafting.altar.recipes.AttunementRecipe").getDeclaredField("additionalSlots");
            FIELD_ADDITIONAL_SLOTS.setAccessible(true);

            FIELD_CRAFTING_TASK = CLASS_TILE_ALTAR.getDeclaredField("craftingTask");
            FIELD_CRAFTING_TASK.setAccessible(true);

            CTOR_ACTIVE_CRAFTING_TASK = CLASS_ACTIVE_CRAFTING_TASK.getConstructor(CLASS_ABSTRACT_ALTAR_RECIPE, int.class, UUID.class);

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] AstralSorceryHandler reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return BLOCK_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_TILE_ALTAR.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ALTAR.isInstance(te)) return false;

        // 必须空闲
        if (getActiveCraftingTask(te) != null) return false;
        // 结构必须匹配
        if (!getMultiblockState(te)) return false;

        // 祭坛所有可访问 slot 必须为空
        IItemHandler handler = getInventoryHandler(te);
        if (handler == null) return false;
        int accessibleSize = getAccessibleSize(te);
        for (int i = 0; i < accessibleSize; i++) {
            if (!handler.getStackInSlot(i).isEmpty()) return false;
        }

        // 通过输入物品种类+数量查找配方并缓存
        Object recipe = findRecipeByIngredients(te, ingredients);
        if (recipe == null) return false;

        // 星能检查
        if (!fulfillesStarlightRequirement(recipe, te)) return false;

        recipeCache.put(pos, recipe);
        return true;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ALTAR.isInstance(te)) return false;

        IItemHandler handler = getInventoryHandler(te);
        if (handler == null) return false;

        Object recipe = recipeCache.get(pos);
        if (recipe == null) {
            // 回退：尝试重新查找
            recipe = findRecipeByIngredients(te, ingredients);
            if (recipe == null) return false;
        }

        // 清空祭坛所有可访问 slot
        int accessibleSize = getAccessibleSize(te);
        for (int i = 0; i < accessibleSize; i++) {
            ItemStack current = handler.getStackInSlot(i);
            if (!current.isEmpty()) {
                handler.extractItem(i, current.getCount(), false);
            }
        }

        // 收集可用物品
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                available.add(stack.copy());
            }
        }

        // 填充 slot 0~8（3x3 grid）
        Object nativeRecipe = getNativeRecipe(recipe);
        if (nativeRecipe != null && CLASS_ACCESSIBLE_RECIPE.isInstance(nativeRecipe)) {
            NonNullList<Ingredient> recipeIngs = ((net.minecraft.item.crafting.IRecipe) nativeRecipe).getIngredients();
            for (int slot = 0; slot < Math.min(recipeIngs.size(), 9); slot++) {
                Ingredient ing = recipeIngs.get(slot);
                if (ing == null || ing == Ingredient.EMPTY) continue;
                for (int i = 0; i < available.size(); i++) {
                    ItemStack stack = available.get(i);
                    if (ing.apply(stack)) {
                        handler.insertItem(slot, stack, false);
                        available.remove(i);
                        break;
                    }
                }
            }
        }

        // 填充额外 slot（AttunementRecipe 的 additionalSlots）
        try {
            if (FIELD_ADDITIONAL_SLOTS.getDeclaringClass().isInstance(recipe)) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> additionalSlots = (Map<Object, Object>) FIELD_ADDITIONAL_SLOTS.get(recipe);
                if (additionalSlots != null) {
                    for (Map.Entry<Object, Object> entry : additionalSlots.entrySet()) {
                        Object slotEnum = entry.getKey();
                        Object itemHandle = entry.getValue();
                        if (slotEnum == null || itemHandle == null) continue;
                        int slotId = (int) METHOD_GET_SLOT_ID.invoke(slotEnum);
                        for (int i = 0; i < available.size(); i++) {
                            ItemStack stack = available.get(i);
                            boolean match = (boolean) METHOD_MATCH_CRAFTING.invoke(itemHandle, stack);
                            if (match) {
                                handler.insertItem(slotId, stack, false);
                                available.remove(i);
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        // 如果有剩余物品未放置，说明推断有误，但不阻止流程
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ALTAR.isInstance(te)) return false;

        // 已经处于合成中
        if (getActiveCraftingTask(te) != null) {
            recipeCache.remove(pos);
            return true;
        }

        try {
            // 调用 AltarRecipeRegistry.findMatchingRecipe(tile, false)
            Object recipe = METHOD_FIND_MATCHING_RECIPE.invoke(null, te, false);
            if (recipe == null) {
                recipeCache.remove(pos);
                return false;
            }

            // 计算 crafting time multiplier
            Object altarLevel = METHOD_GET_ALTAR_LEVEL.invoke(te);
            Object neededLevel = METHOD_GET_NEEDED_LEVEL.invoke(recipe);
            int diff = Math.max(0, ((Enum<?>) altarLevel).ordinal() - ((Enum<?>) neededLevel).ordinal());
            int multiplier = (int) Math.round(Math.pow(2, diff));

            // 创建 ActiveCraftingTask
            Object task = CTOR_ACTIVE_CRAFTING_TASK.newInstance(recipe, multiplier, (UUID) null);
            FIELD_CRAFTING_TASK.set(te, task);
            METHOD_MARK_FOR_UPDATE.invoke(te);
            recipeCache.remove(pos);
            return true;
        } catch (Exception e) {
            recipeCache.remove(pos);
            return false;
        }
    }

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_ALTAR.isInstance(te)) return false;
        return getActiveCraftingTask(te) == null;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();

        // 产物掉落在祭坛上方 y+1.3 附近
        AxisAlignedBB aabb = new AxisAlignedBB(
            pos.getX(), pos.getY() + 1.0, pos.getZ(),
            pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0
        );
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, aabb);
        for (EntityItem entity : items) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) continue;
            if (expectedOutputs != null && expectedOutputs.length > 0 && expectedOutputs[0] != null) {
                if (outputsMatch(stack, expectedOutputs[0].createItemStack())) {
                    result.add(stack.copy());
                    entity.setDead();
                }
            } else {
                result.add(stack.copy());
                entity.setDead();
            }
        }
        return result;
    }

    // ---- 配方查找与匹配 ----

    /**
     * 通过输入物品种类+数量在 AltarRecipeRegistry 中查找匹配的配方。
     * 只搜索当前祭坛等级及以下的配方。
     */
    @SuppressWarnings("unchecked")
    private static Object findRecipeByIngredients(TileEntity altar, InventoryCrafting ingredients) {
        try {
            Map<Object, List<Object>> recipesMap = (Map<Object, List<Object>>) FIELD_RECIPES_MAP.get(null);
            if (recipesMap == null) return null;

            Object altarLevel = METHOD_GET_ALTAR_LEVEL.invoke(altar);
            int altarOrdinal = ((Enum<?>) altarLevel).ordinal();

            for (Map.Entry<Object, List<Object>> entry : recipesMap.entrySet()) {
                Object level = entry.getKey();
                int levelOrdinal = ((Enum<?>) level).ordinal();
                if (levelOrdinal > altarOrdinal) continue;

                for (Object recipe : entry.getValue()) {
                    if (ingredientsMatch(recipe, ingredients)) {
                        return recipe;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static boolean ingredientsMatch(Object recipe, InventoryCrafting ingredients) {
        try {
            Object nativeRecipe = getNativeRecipe(recipe);
            if (nativeRecipe == null) return false;

            NonNullList<Ingredient> recipeIngs = ((net.minecraft.item.crafting.IRecipe) nativeRecipe).getIngredients();
            List<Ingredient> required = new ArrayList<>();
            for (Ingredient ing : recipeIngs) {
                if (ing != null && ing != Ingredient.EMPTY) {
                    required.add(ing);
                }
            }

            // AttunementRecipe 额外 slot
            try {
                if (FIELD_ADDITIONAL_SLOTS.getDeclaringClass().isInstance(recipe)) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> additionalSlots = (Map<Object, Object>) FIELD_ADDITIONAL_SLOTS.get(recipe);
                    if (additionalSlots != null) {
                        for (Object itemHandle : additionalSlots.values()) {
                            if (itemHandle != null) {
                                Ingredient ing = (Ingredient) CLASS_ITEM_HANDLE.getMethod("getRecipeIngredient").invoke(itemHandle);
                                if (ing != null && ing != Ingredient.EMPTY) {
                                    required.add(ing);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }

            List<ItemStack> available = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    available.add(stack.copy());
                }
            }

            if (required.size() != available.size()) return false;

            for (Ingredient ing : required) {
                boolean found = false;
                for (int i = 0; i < available.size(); i++) {
                    if (ing.apply(available.get(i))) {
                        available.remove(i);
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Object getNativeRecipe(Object recipe) {
        try {
            return METHOD_GET_NATIVE_RECIPE.invoke(recipe);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean fulfillesStarlightRequirement(Object recipe, TileEntity altar) {
        try {
            return (boolean) METHOD_FULFILLES_STARLIGHT.invoke(recipe, altar);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 辅助方法 ----

    private static Object getActiveCraftingTask(TileEntity te) {
        try {
            return METHOD_GET_ACTIVE_CRAFTING_TASK.invoke(te);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean getMultiblockState(TileEntity te) {
        try {
            return (boolean) METHOD_GET_MULTIBLOCK_STATE.invoke(te);
        } catch (Exception e) {
            return false;
        }
    }

    private static IItemHandler getInventoryHandler(TileEntity te) {
        try {
            return (IItemHandler) METHOD_GET_INVENTORY_HANDLER.invoke(te);
        } catch (Exception e) {
            return null;
        }
    }

    private static int getAccessibleSize(TileEntity te) {
        try {
            Object level = METHOD_GET_ALTAR_LEVEL.invoke(te);
            return (int) level.getClass().getMethod("getAccessibleInventorySize").invoke(level);
        } catch (Exception e) {
            return 9; // fallback
        }
    }

    private static boolean outputsMatch(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        if (a.getMetadata() != b.getMetadata()) return false;
        if (a.hasTagCompound() != b.hasTagCompound()) return false;
        if (a.hasTagCompound() && b.hasTagCompound()) {
            return a.getTagCompound().equals(b.getTagCompound());
        }
        return true;
    }
}

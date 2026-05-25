package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Extended Crafting 量子压缩机处理器。
 *
 * <p>突破压缩机输入限制，直接将所需全部材料注入内部，
 * 绕过 {@code inputLimit} 和逐次堆叠限制。</p>
 */
public class CompressorHandler implements IRemoteHandler {

    private static final String BLOCK_ID = "extendedcrafting:compressor";

    // 反射缓存
    private static Class<?> CLASS_TILE_COMPRESSOR;
    private static Class<?> CLASS_COMPRESSOR_RECIPE;
    private static Class<?> CLASS_RECIPE_MANAGER;
    private static Object RECIPE_MANAGER_INSTANCE;
    private static Method METHOD_GET_RECIPES;
    private static Method METHOD_GET_RECIPE;
    private static Method METHOD_GET_OUTPUT;
    private static Method METHOD_GET_INPUT;
    private static Method METHOD_GET_INPUT_COUNT;
    private static Method METHOD_GET_CATALYST;
    private static Method METHOD_CONSUME_CATALYST;
    private static Method METHOD_GET_POWER_COST;
    private static Field FIELD_MATERIAL_STACK;
    private static Field FIELD_MATERIAL_COUNT;
    private static Field FIELD_PROGRESS;
    private static Field FIELD_EJECTING;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_COMPRESSOR = Class.forName("com.blakebr0.extendedcrafting.tile.TileCompressor");
            CLASS_COMPRESSOR_RECIPE = Class.forName("com.blakebr0.extendedcrafting.crafting.CompressorRecipe");
            CLASS_RECIPE_MANAGER = Class.forName("com.blakebr0.extendedcrafting.crafting.CompressorRecipeManager");
            Method getInstance = CLASS_RECIPE_MANAGER.getMethod("getInstance");
            RECIPE_MANAGER_INSTANCE = getInstance.invoke(null);
            METHOD_GET_RECIPES = CLASS_RECIPE_MANAGER.getMethod("getRecipes");

            METHOD_GET_RECIPE = CLASS_TILE_COMPRESSOR.getMethod("getRecipe");
            METHOD_GET_OUTPUT = CLASS_COMPRESSOR_RECIPE.getMethod("getOutput");
            METHOD_GET_INPUT = CLASS_COMPRESSOR_RECIPE.getMethod("getInput");
            METHOD_GET_INPUT_COUNT = CLASS_COMPRESSOR_RECIPE.getMethod("getInputCount");
            METHOD_GET_CATALYST = CLASS_COMPRESSOR_RECIPE.getMethod("getCatalyst");
            METHOD_CONSUME_CATALYST = CLASS_COMPRESSOR_RECIPE.getMethod("consumeCatalyst");
            METHOD_GET_POWER_COST = CLASS_COMPRESSOR_RECIPE.getMethod("getPowerCost");

            FIELD_MATERIAL_STACK = CLASS_TILE_COMPRESSOR.getDeclaredField("materialStack");
            FIELD_MATERIAL_STACK.setAccessible(true);
            FIELD_MATERIAL_COUNT = CLASS_TILE_COMPRESSOR.getDeclaredField("materialCount");
            FIELD_MATERIAL_COUNT.setAccessible(true);
            FIELD_PROGRESS = CLASS_TILE_COMPRESSOR.getDeclaredField("progress");
            FIELD_PROGRESS.setAccessible(true);
            FIELD_EJECTING = CLASS_TILE_COMPRESSOR.getDeclaredField("ejecting");
            FIELD_EJECTING.setAccessible(true);

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] Compressor reflection init failed", e);
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
        return CLASS_TILE_COMPRESSOR.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_COMPRESSOR.isInstance(te)) return false;
        return getProgress(te) == 0;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_COMPRESSOR.isInstance(te)) return false;
        if (getProgress(te) != 0) return false;

        // 收集输入材料（忽略空槽）
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                inputs.add(stack.copy());
            }
        }
        if (inputs.isEmpty()) return false;

        // 通过产物反查配方
        // 注意：ingredients slot 0 通常是主要材料，我们用它来找配方
        // 但更可靠的方式是：已经有一个 recipe 在压缩机里，或者通过 expected output 查找
        // 由于 pushMaterials 不知道 expected output，我们用 slot 0 的物品 + 所有输入来匹配
        Object recipe = findRecipeByInputs(inputs);
        if (recipe == null) return false;

        // 注入能量（如果不够）
        int powerCost = getPowerCost(recipe);
        if (powerCost > 0) {
            injectEnergy(te, powerCost);
        }

        // 设置催化剂（slot 2）
        net.minecraft.item.crafting.Ingredient catalystIng = getCatalyst(recipe);
        if (catalystIng != null && catalystIng != net.minecraft.item.crafting.Ingredient.EMPTY) {
            ItemStack catalystStack = findMatchingStack(inputs, catalystIng);
            if (!catalystStack.isEmpty()) {
                setSlot(te, 2, catalystStack.copy());
            }
        }

        // 主材料：取第一个匹配 recipe input 的物品
        net.minecraft.item.crafting.Ingredient recipeInput = getRecipeInput(recipe);
        ItemStack mainMaterial = findMatchingStack(inputs, recipeInput);
        if (mainMaterial.isEmpty()) return false;

        int neededCount = getInputCount(recipe);

        // 突破输入限制：直接设置 materialStack / materialCount 和 slot 1
        try {
            ItemStack material = mainMaterial.copy();
            material.setCount(Math.min(material.getCount(), neededCount));
            setSlot(te, 1, material.copy());
            FIELD_MATERIAL_STACK.set(te, material.copy());
            FIELD_MATERIAL_COUNT.setInt(te, material.getCount());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to inject compressor materials", e);
            return false;
        }

        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        // 压缩机在 materialCount >= inputCount 时自动开始
        // 将 ejecting 设为 false 防止它立刻弹出产物
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_TILE_COMPRESSOR.isInstance(te)) {
            try {
                FIELD_EJECTING.setBoolean(te, false);
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_COMPRESSOR.isInstance(te)) return false;
        if (getProgress(te) != 0) return false;

        // 检查 slot 0 是否有产物：有产物说明已完成，可以收集
        ItemStack output = getSlot(te, 0);
        return !output.isEmpty();
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_COMPRESSOR.isInstance(te)) return result;

        // 取出 slot 0 的产物
        ItemStack output = extractSlot(te, 0, 64);
        if (!output.isEmpty()) {
            result.add(output);
        }

        // 清理催化剂（如果不消耗）
        try {
            Object recipe = METHOD_GET_RECIPE.invoke(te);
            if (recipe != null) {
                boolean consume = (boolean) METHOD_CONSUME_CATALYST.invoke(recipe);
                if (consume) {
                    extractSlot(te, 2, 64);
                }
            }
        } catch (Exception ignored) {
        }

        // 重置 materialStack / materialCount，防止干扰下一次
        try {
            FIELD_MATERIAL_STACK.set(te, ItemStack.EMPTY);
            FIELD_MATERIAL_COUNT.setInt(te, 0);
            setSlot(te, 1, ItemStack.EMPTY);
        } catch (Exception ignored) {
        }

        return result;
    }

    // ---- 反射辅助 ----

    private static int getProgress(TileEntity te) {
        try {
            return FIELD_PROGRESS.getInt(te);
        } catch (Exception e) {
            return -1;
        }
    }

    private static void setSlot(TileEntity te, int slot, ItemStack stack) {
        if (te instanceof IInventory) {
            ((IInventory) te).setInventorySlotContents(slot, stack);
        }
    }

    private static ItemStack getSlot(TileEntity te, int slot) {
        if (te instanceof IInventory) {
            return ((IInventory) te).getStackInSlot(slot);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractSlot(TileEntity te, int slot, int amount) {
        if (te instanceof IInventory) {
            IInventory inv = (IInventory) te;
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                ItemStack extracted = stack.splitStack(Math.min(amount, stack.getCount()));
                if (stack.isEmpty()) {
                    inv.setInventorySlotContents(slot, ItemStack.EMPTY);
                }
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void injectEnergy(TileEntity te, int amount) {
        IEnergyStorage energy = te.getCapability(CapabilityEnergy.ENERGY, EnumFacing.UP);
        if (energy != null) {
            int needed = amount - energy.getEnergyStored();
            if (needed > 0) {
                energy.receiveEnergy(needed, false);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object findRecipeByInputs(List<ItemStack> inputs) {
        try {
            List<Object> recipes = (List<Object>) METHOD_GET_RECIPES.invoke(RECIPE_MANAGER_INSTANCE);
            if (recipes == null) return null;

            for (Object recipe : recipes) {
                net.minecraft.item.crafting.Ingredient recipeInput = getRecipeInput(recipe);
                net.minecraft.item.crafting.Ingredient recipeCatalyst = getCatalyst(recipe);

                // 检查是否有输入匹配 recipe input
                boolean hasInput = false;
                for (ItemStack stack : inputs) {
                    if (recipeInput.apply(stack)) {
                        hasInput = true;
                        break;
                    }
                }
                if (!hasInput) continue;

                // 检查是否有输入匹配 recipe catalyst（如果需要）
                if (recipeCatalyst != null && recipeCatalyst != net.minecraft.item.crafting.Ingredient.EMPTY) {
                    boolean hasCatalyst = false;
                    for (ItemStack stack : inputs) {
                        if (recipeCatalyst.apply(stack)) {
                            hasCatalyst = true;
                            break;
                        }
                    }
                    if (!hasCatalyst) continue;
                }

                return recipe;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] findRecipeByInputs failed", e);
        }
        return null;
    }

    private static ItemStack findMatchingStack(List<ItemStack> inputs, net.minecraft.item.crafting.Ingredient recipeInput) {
        if (recipeInput == null || recipeInput == net.minecraft.item.crafting.Ingredient.EMPTY) return ItemStack.EMPTY;
        for (ItemStack stack : inputs) {
            if (recipeInput.apply(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static net.minecraft.item.crafting.Ingredient getRecipeInput(Object recipe) {
        try {
            return (net.minecraft.item.crafting.Ingredient) METHOD_GET_INPUT.invoke(recipe);
        } catch (Exception e) {
            return net.minecraft.item.crafting.Ingredient.EMPTY;
        }
    }

    private static int getInputCount(Object recipe) {
        try {
            return (int) METHOD_GET_INPUT_COUNT.invoke(recipe);
        } catch (Exception e) {
            return 0;
        }
    }

    private static net.minecraft.item.crafting.Ingredient getCatalyst(Object recipe) {
        try {
            return (net.minecraft.item.crafting.Ingredient) METHOD_GET_CATALYST.invoke(recipe);
        } catch (Exception e) {
            return net.minecraft.item.crafting.Ingredient.EMPTY;
        }
    }

    private static int getPowerCost(Object recipe) {
        try {
            return (int) METHOD_GET_POWER_COST.invoke(recipe);
        } catch (Exception e) {
            return 0;
        }
    }
}

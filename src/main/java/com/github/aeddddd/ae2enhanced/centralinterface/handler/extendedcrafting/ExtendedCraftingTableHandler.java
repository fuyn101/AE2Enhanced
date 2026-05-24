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
import net.minecraft.tileentity.TileEntity;
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
    private static Method METHOD_FIND_MATCHING_RECIPE;
    private static Method METHOD_GET_REMAINING_ITEMS;
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
            METHOD_FIND_MATCHING_RECIPE = recipeManagerClass.getMethod("findMatchingRecipe", InventoryCrafting.class, World.class);
            METHOD_GET_REMAINING_ITEMS = recipeManagerClass.getMethod("getRemainingItems", InventoryCrafting.class, World.class);
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

        // 检查 ingredients 尺寸是否与工作台匹配
        if (ingredients.getSizeInventory() != lineSize * lineSize) return false;

        // 构造正确尺寸的 InventoryCrafting
        InventoryCrafting matrix = createMatrix(lineSize);
        copyIngredients(ingredients, matrix);

        // 查找配方
        ItemStack result = findMatchingRecipe(matrix, world);
        if (result.isEmpty()) return false;

        // 验证产物是否与预期匹配
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        return outputsMatch(result, outputs[0].createItemStack());
    }

    @Override
    public List<ItemStack> virtualCraft(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, IActionSource source) {
        initReflection();
        List<ItemStack> products = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_ABSTRACT_TABLE.isInstance(te)) return products;

        int lineSize = getLineSize(te);
        if (lineSize <= 0) return products;

        InventoryCrafting matrix = createMatrix(lineSize);
        copyIngredients(ingredients, matrix);

        // 产物
        ItemStack result = findMatchingRecipe(matrix, world);
        if (!result.isEmpty()) {
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

    // ---- 辅助方法 ----

    private static int getLineSize(TileEntity te) {
        try {
            return (int) METHOD_GET_LINE_SIZE.invoke(te);
        } catch (Exception e) {
            return -1;
        }
    }

    private static InventoryCrafting createMatrix(int lineSize) {
        // 使用 dummy Container 避免 InventoryCrafting.setInventorySlotContents 时的 NPE
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

    private static void copyIngredients(InventoryCrafting src, InventoryCrafting dst) {
        int size = Math.min(src.getSizeInventory(), dst.getSizeInventory());
        for (int i = 0; i < size; i++) {
            ItemStack stack = src.getStackInSlot(i);
            if (!stack.isEmpty()) {
                dst.setInventorySlotContents(i, stack.copy());
            }
        }
    }

    private static ItemStack findMatchingRecipe(InventoryCrafting matrix, World world) {
        try {
            return (ItemStack) METHOD_FIND_MATCHING_RECIPE.invoke(RECIPE_MANAGER_INSTANCE, matrix, world);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
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
        // NBT 比较
        if (recipeOutput.hasTagCompound() != expected.hasTagCompound()) return false;
        if (recipeOutput.hasTagCompound() && expected.hasTagCompound()) {
            return recipeOutput.getTagCompound().equals(expected.getTagCompound());
        }
        return true;
    }
}

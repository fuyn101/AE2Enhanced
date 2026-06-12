package com.github.aeddddd.ae2enhanced.centralinterface.handler.thermalexpansion;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thermal Expansion 机器远程处理器.
 *
 * <p>通过反射直接访问 CoFH {@code TileInventory#inventory} 字段,
 * 绕过 Thermal Expansion 机器的 SideCache 配置限制,避免因为配置面导致中枢 ME 接口无法输入输出.</p>
 */
public class ThermalExpansionMachineHandler implements IRemoteHandler {

    private static final boolean AVAILABLE;
    private static Class<?> TILE_INVENTORY_CLASS;
    private static Field INVENTORY_FIELD;

    static {
        boolean available = false;
        try {
            if (Loader.isModLoaded("thermalexpansion")) {
                TILE_INVENTORY_CLASS = Class.forName("cofh.core.block.TileInventory");
                INVENTORY_FIELD = TILE_INVENTORY_CLASS.getField("inventory");
                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Thermal Expansion handler", e);
        }
        AVAILABLE = available;
    }

    @Override
    public boolean canHandle(String blockId) {
        return blockId != null && blockId.startsWith("thermalexpansion:");
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof IInventory && TILE_INVENTORY_CLASS.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        return isValidTarget(world, pos);
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return false;

        IInventory inv = (IInventory) te;
        ItemStack[] inventory = getInventoryArray(te);
        if (inventory == null) return false;

        List<ItemStack> toPush = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                toPush.add(stack.copy());
            }
        }

        for (ItemStack stack : toPush) {
            if (!pushItemToInventory(inv, inventory, stack)) {
                return false;
            }
        }
        markDirty(te);
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        return true;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return Collections.emptyList();

        IInventory inv = (IInventory) te;
        ItemStack[] inventory = getInventoryArray(te);
        if (inventory == null) return Collections.emptyList();

        List<ItemStack> reverted = new ArrayList<>();
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack stack = inventory[slot];
            if (!stack.isEmpty() && isProbablyInputSlot(inv, slot)) {
                reverted.add(stack.copy());
                inventory[slot] = ItemStack.EMPTY;
            }
        }
        markDirty(te);
        return reverted;
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return Collections.emptyList();

        IInventory inv = (IInventory) te;
        ItemStack[] inventory = getInventoryArray(te);
        if (inventory == null) return Collections.emptyList();

        List<ItemStack> cleared = new ArrayList<>();
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack stack = inventory[slot];
            if (!stack.isEmpty() && isProbablyOutputSlot(inv, slot)) {
                cleared.add(stack.copy());
                inventory[slot] = ItemStack.EMPTY;
            }
        }
        markDirty(te);
        return cleared;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return Collections.emptyList();

        IInventory inv = (IInventory) te;
        ItemStack[] inventory = getInventoryArray(te);
        if (inventory == null) return Collections.emptyList();
        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();

        List<ItemStack> collected = new ArrayList<>();

        // 阶段 1：优先收集匹配预期产物的物品
        if (expectedOutputs != null) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null) continue;
                ItemStack expectedStack = expected.createItemStack();
                for (int slot = 0; slot < inventory.length; slot++) {
                    ItemStack inSlot = inventory[slot];
                    if (inSlot.isEmpty()) continue;
                    if (isInputMaterial(inSlot, inputsSafe)) continue;
                    if (!matchesLoosely(inSlot, expectedStack)) continue;
                    collected.add(inSlot.copy());
                    inventory[slot] = ItemStack.EMPTY;
                }
            }
        }

        // 阶段 2：收集所有非输入材料物品(主要是输出槽)
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack stack = inventory[slot];
            if (stack.isEmpty()) continue;
            if (isInputMaterial(stack, inputsSafe)) continue;
            collected.add(stack.copy());
            inventory[slot] = ItemStack.EMPTY;
        }

        markDirty(te);
        return collected;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return true;

        ItemStack[] inventory = getInventoryArray(te);
        if (inventory == null) return true;
        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();

        boolean hasProducts = false;
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack stack = inventory[slot];
            if (stack.isEmpty()) continue;
            if (isInputMaterial(stack, inputsSafe)) {
                return false; // 还有输入材料未处理完
            }
            hasProducts = true;
        }
        return hasProducts;
    }

    // ---- Internal helpers ----

    private ItemStack[] getInventoryArray(TileEntity te) {
        try {
            return (ItemStack[]) INVENTORY_FIELD.get(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get ThermalExpansion inventory field", e);
            return null;
        }
    }

    private boolean pushItemToInventory(IInventory inv, ItemStack[] inventory, ItemStack stack) {
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack existing = inventory[slot];
            if (!existing.isEmpty()) {
                if (ItemStack.areItemsEqual(existing, stack)
                        && ItemStack.areItemStackTagsEqual(existing, stack)
                        && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()
                        && inv.isItemValidForSlot(slot, stack)) {
                    existing.grow(stack.getCount());
                    return true;
                }
                continue;
            }
            if (inv.isItemValidForSlot(slot, stack)) {
                inventory[slot] = stack.copy();
                return true;
            }
        }
        return false;
    }

    private boolean isProbablyInputSlot(IInventory inv, int slot) {
        // 简单启发式：前 2/3 槽位视为输入(大多数 TE 机器输入槽在前,输出槽在后)
        return slot < inv.getSizeInventory() * 2 / 3;
    }

    private boolean isProbablyOutputSlot(IInventory inv, int slot) {
        return !isProbablyInputSlot(inv, slot);
    }

    private void markDirty(TileEntity te) {
        try {
            te.markDirty();
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] markDirty failed", e);
        }
    }

    private boolean isInputMaterial(ItemStack stack, List<ItemStack> inputs) {
        for (ItemStack input : inputs) {
            if (ItemStack.areItemsEqual(stack, input) && ItemStack.areItemStackTagsEqual(stack, input)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesLoosely(ItemStack actual, ItemStack expected) {
        if (!ItemStack.areItemsEqual(actual, expected)) return false;
        if (!expected.hasTagCompound()) return true;
        return ItemStack.areItemStackTagsEqual(actual, expected);
    }
}

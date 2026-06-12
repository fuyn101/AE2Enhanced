package com.github.aeddddd.ae2enhanced.centralinterface.handler.nuclearcraft;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * NuclearCraft 2.19a (非重制版) 机器远程处理器.
 *
 * <p>通过反射直接访问 {@code ITileInventory#getInventoryStacks()},
 * 绕过 NC 机器的侧面配置(I/O mode),避免因为配置面导致中枢 ME 接口无法输入输出.</p>
 */
public class NuclearCraftLegacyMachineHandler implements IRemoteHandler {

    private static final boolean AVAILABLE;

    private static Class<?> TILE_ITEM_PROCESSOR_CLASS;
    private static Class<?> TILE_FLUID_PROCESSOR_CLASS;
    private static Class<?> TILE_ITEM_FLUID_PROCESSOR_CLASS;
    private static Class<?> ITILE_INVENTORY_CLASS;
    private static Class<?> IUPGRADABLE_CLASS;

    private static Method GET_INVENTORY_STACKS_METHOD;
    private static Method IS_ITEM_VALID_FOR_SLOT_METHOD;

    static {
        boolean available = false;
        try {
            if (Loader.isModLoaded("nuclearcraft")) {
                TILE_ITEM_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileItemProcessor");
                TILE_FLUID_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileFluidProcessor");
                TILE_ITEM_FLUID_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileItemFluidProcessor");
                ITILE_INVENTORY_CLASS = Class.forName("nc.tile.inventory.ITileInventory");
                IUPGRADABLE_CLASS = Class.forName("nc.tile.processor.IUpgradable");

                GET_INVENTORY_STACKS_METHOD = ITILE_INVENTORY_CLASS.getMethod("getInventoryStacks");
                IS_ITEM_VALID_FOR_SLOT_METHOD = ITILE_INVENTORY_CLASS.getMethod("isItemValidForSlot", int.class, ItemStack.class);

                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize NuclearCraft 2.19a handler", e);
        }
        AVAILABLE = available;
    }

    @Override
    public boolean canHandle(String blockId) {
        return blockId != null && blockId.startsWith("nuclearcraft:");
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return isProcessorTile(te);
    }

    private boolean isProcessorTile(TileEntity te) {
        if (te == null) return false;
        return TILE_ITEM_PROCESSOR_CLASS.isInstance(te)
                || TILE_FLUID_PROCESSOR_CLASS.isInstance(te)
                || TILE_ITEM_FLUID_PROCESSOR_CLASS.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        return isValidTarget(world, pos);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return false;

        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            int inputSize = getItemInputSize(te);

            List<ItemStack> toPush = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    toPush.add(stack.copy());
                }
            }

            for (ItemStack stack : toPush) {
                if (!pushItemToInventory(te, inventory, inputSize, stack)) {
                    return false;
                }
            }
            markDirty(te);
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft pushMaterials failed", e);
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return Collections.emptyList();

        List<ItemStack> reverted = new ArrayList<>();
        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            int inputSize = getItemInputSize(te);
            for (int slot = 0; slot < inputSize && slot < inventory.size(); slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty()) {
                    reverted.add(stack.copy());
                    inventory.set(slot, ItemStack.EMPTY);
                }
            }
            markDirty(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft revertMaterials failed", e);
        }
        return reverted;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return Collections.emptyList();

        List<ItemStack> cleared = new ArrayList<>();
        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            int inputSize = getItemInputSize(te);
            int outputSize = getItemOutputSize(te);
            int outputStart = inputSize;
            int outputEnd = Math.min(inputSize + outputSize, inventory.size());

            // 收集输出槽
            for (int slot = outputStart; slot < outputEnd; slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty()) {
                    cleared.add(stack.copy());
                    inventory.set(slot, ItemStack.EMPTY);
                }
            }
            markDirty(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft clearOutputs failed", e);
        }
        return cleared;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return Collections.emptyList();

        List<ItemStack> collected = new ArrayList<>();
        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            int inputSize = getItemInputSize(te);
            int outputSize = getItemOutputSize(te);
            int outputStart = inputSize;
            int outputEnd = Math.min(inputSize + outputSize, inventory.size());

            // 阶段 1：优先收集匹配预期产物的物品
            if (expectedOutputs != null) {
                for (IAEItemStack expected : expectedOutputs) {
                    if (expected == null) continue;
                    ItemStack expectedStack = expected.createItemStack();
                    for (int slot = outputStart; slot < outputEnd; slot++) {
                        ItemStack inSlot = inventory.get(slot);
                        if (inSlot.isEmpty()) continue;
                        if (matchesLoosely(inSlot, expectedStack)) {
                            collected.add(inSlot.copy());
                            inventory.set(slot, ItemStack.EMPTY);
                        }
                    }
                }
            }

            // 阶段 2：收集输出槽中所有剩余物品(副产物/容器等)
            for (int slot = outputStart; slot < outputEnd; slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty()) {
                    collected.add(stack.copy());
                    inventory.set(slot, ItemStack.EMPTY);
                }
            }

            // 阶段 3：收集输入槽中未被识别为输入材料的剩余物品(防止机器把产物吐回输入槽)
            List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();
            for (int slot = 0; slot < inputSize && slot < inventory.size(); slot++) {
                ItemStack stack = inventory.get(slot);
                if (stack.isEmpty()) continue;
                if (!isInputMaterial(stack, inputsSafe)) {
                    collected.add(stack.copy());
                    inventory.set(slot, ItemStack.EMPTY);
                }
            }
            markDirty(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft collectProducts failed", e);
        }
        return collected;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return true;

        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            int inputSize = getItemInputSize(te);
            int outputSize = getItemOutputSize(te);
            int outputStart = inputSize;
            int outputEnd = Math.min(inputSize + outputSize, inventory.size());
            List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();

            // 检查输入槽是否还有未处理完的输入材料
            for (int slot = 0; slot < inputSize && slot < inventory.size(); slot++) {
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty() && isInputMaterial(stack, inputsSafe)) {
                    return false;
                }
            }

            // 检查输出槽是否有产物
            for (int slot = outputStart; slot < outputEnd; slot++) {
                if (!inventory.get(slot).isEmpty()) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft isIdle failed", e);
            return true;
        }
    }

    // ---- Internal helpers ----

    private boolean pushItemToInventory(TileEntity te, NonNullList<ItemStack> inventory, int inputSize, ItemStack stack) {
        for (int slot = 0; slot < inputSize && slot < inventory.size(); slot++) {
            ItemStack existing = inventory.get(slot);
            try {
                if (!existing.isEmpty()) {
                    if (ItemStack.areItemsEqual(existing, stack)
                            && ItemStack.areItemStackTagsEqual(existing, stack)
                            && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                        if ((Boolean) IS_ITEM_VALID_FOR_SLOT_METHOD.invoke(te, slot, stack)) {
                            existing.grow(stack.getCount());
                            return true;
                        }
                    }
                    continue;
                }
                if ((Boolean) IS_ITEM_VALID_FOR_SLOT_METHOD.invoke(te, slot, stack)) {
                    inventory.set(slot, stack.copy());
                    return true;
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] isItemValidForSlot failed for slot {}", slot, e);
            }
        }
        return false;
    }

    private int getItemInputSize(TileEntity te) {
        try {
            return (Integer) te.getClass().getField("itemInputSize").get(te);
        } catch (Exception e) {
            return 0;
        }
    }

    private int getItemOutputSize(TileEntity te) {
        try {
            return (Integer) te.getClass().getField("itemOutputSize").get(te);
        } catch (Exception e) {
            return 0;
        }
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

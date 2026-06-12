package com.github.aeddddd.ae2enhanced.centralinterface.handler.enderio;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ender IO 机器远程处理器.
 *
 * <p>同时支持 legacy 机器(直接操作 {@code inventory[]} 字段)和新的 capability 机器
 * (通过 {@code getInventory()} 获取内部 {@link IItemHandler}).
 * 绕过 EIO 机器的 IoMode 侧面配置,避免因为配置面导致中枢 ME 接口无法输入输出.</p>
 */
public class EnderIOMachineHandler implements IRemoteHandler {

    private static final boolean AVAILABLE;

    private static Class<?> ABSTRACT_MACHINE_ENTITY_CLASS;
    private static Class<?> ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS;
    private static Class<?> ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS;

    private static Field LEGACY_INVENTORY_FIELD;
    private static Method GET_INVENTORY_METHOD;

    static {
        boolean available = false;
        try {
            if (Loader.isModLoaded("enderio")) {
                ABSTRACT_MACHINE_ENTITY_CLASS = Class.forName("crazypants.enderio.base.machine.base.te.AbstractMachineEntity");
                try {
                    ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS = Class.forName("crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity");
                    LEGACY_INVENTORY_FIELD = ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.getDeclaredField("inventory");
                    LEGACY_INVENTORY_FIELD.setAccessible(true);
                } catch (ClassNotFoundException | NoSuchFieldException e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] EIO legacy machine support not available");
                }
                try {
                    ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS = Class.forName("crazypants.enderio.base.machine.base.te.AbstractCapabilityMachineEntity");
                    GET_INVENTORY_METHOD = ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.getMethod("getInventory");
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] EIO capability machine support not available");
                }
                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Ender IO handler", e);
        }
        AVAILABLE = available;
    }

    @Override
    public boolean canHandle(String blockId) {
        return blockId != null && blockId.startsWith("enderio:");
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return AVAILABLE && te != null && ABSTRACT_MACHINE_ENTITY_CLASS.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        return isValidTarget(world, pos);
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return false;

        IItemHandler handler = getInternalItemHandler(te);
        if (handler == null) {
            // 内部 handler 不可用,回退到遍历所有 face 的 capability(legacy fallback)
            return pushToAnyFace(te, ingredients);
        }

        List<ItemStack> toPush = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                toPush.add(stack.copy());
            }
        }

        for (ItemStack stack : toPush) {
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        te.markDirty();
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

        IItemHandler handler = getInternalItemHandler(te);
        if (handler == null) {
            return revertFromAnyFace(te);
        }

        List<ItemStack> reverted = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (!inSlot.isEmpty()) {
                ItemStack extracted = handler.extractItem(slot, inSlot.getCount(), false);
                if (!extracted.isEmpty()) {
                    reverted.add(extracted);
                }
            }
        }
        te.markDirty();
        return reverted;
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source) {
        return revertMaterials(world, pos, source);
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return Collections.emptyList();

        IItemHandler handler = getInternalItemHandler(te);
        if (handler == null) {
            return collectFromAnyFace(te, expectedOutputs, inputs);
        }

        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();
        List<ItemStack> collected = new ArrayList<>();

        // 阶段 1：优先收集预期产物
        if (expectedOutputs != null) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null) continue;
                ItemStack expectedStack = expected.createItemStack();
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    ItemStack inSlot = handler.getStackInSlot(slot);
                    if (inSlot.isEmpty()) continue;
                    if (isInputMaterial(inSlot, inputsSafe)) continue;
                    if (!matchesLoosely(inSlot, expectedStack)) continue;
                    ItemStack extracted = handler.extractItem(slot, inSlot.getCount(), false);
                    if (!extracted.isEmpty()) {
                        collected.add(extracted);
                    }
                }
            }
        }

        // 阶段 2：收集所有非输入材料物品
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (inSlot.isEmpty()) continue;
            if (isInputMaterial(inSlot, inputsSafe)) continue;
            ItemStack extracted = handler.extractItem(slot, inSlot.getCount(), false);
            if (!extracted.isEmpty()) {
                collected.add(extracted);
            }
        }

        te.markDirty();
        return collected;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return true;

        IItemHandler handler = getInternalItemHandler(te);
        if (handler == null) {
            return isIdleFromAnyFace(te, inputs);
        }

        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();
        boolean hasProducts = false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (inSlot.isEmpty()) continue;
            if (isInputMaterial(inSlot, inputsSafe)) {
                return false;
            }
            hasProducts = true;
        }
        return hasProducts;
    }

    // ---- Internal helpers ----

    private IItemHandler getInternalItemHandler(TileEntity te) {
        if (ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.isInstance(te)
                && GET_INVENTORY_METHOD != null) {
            try {
                Object inventory = GET_INVENTORY_METHOD.invoke(te);
                if (inventory instanceof IItemHandler) {
                    return (IItemHandler) inventory;
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to get EIO capability inventory", e);
            }
        }
        if (ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.isInstance(te)
                && LEGACY_INVENTORY_FIELD != null) {
            try {
                final ItemStack[] inventory = (ItemStack[]) LEGACY_INVENTORY_FIELD.get(te);
                if (inventory != null) {
                    return new IItemHandler() {
                        @Override public int getSlots() { return inventory.length; }
                        @Override public ItemStack getStackInSlot(int slot) { return slot >= 0 && slot < inventory.length ? inventory[slot] : ItemStack.EMPTY; }
                        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                            if (slot < 0 || slot >= inventory.length || stack.isEmpty()) return stack;
                            ItemStack existing = inventory[slot];
                            if (existing.isEmpty()) {
                                if (!simulate) inventory[slot] = stack.copy();
                                return ItemStack.EMPTY;
                            }
                            if (ItemStack.areItemsEqual(existing, stack) && ItemStack.areItemStackTagsEqual(existing, stack)
                                    && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                                if (!simulate) existing.grow(stack.getCount());
                                return ItemStack.EMPTY;
                            }
                            return stack;
                        }
                        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                            if (slot < 0 || slot >= inventory.length || amount <= 0) return ItemStack.EMPTY;
                            ItemStack existing = inventory[slot];
                            if (existing.isEmpty()) return ItemStack.EMPTY;
                            int toExtract = Math.min(amount, existing.getCount());
                            ItemStack copy = existing.copy();
                            copy.setCount(toExtract);
                            if (!simulate) {
                                existing.shrink(toExtract);
                                if (existing.isEmpty()) inventory[slot] = ItemStack.EMPTY;
                            }
                            return copy;
                        }
                        @Override public int getSlotLimit(int slot) { return 64; }
                    };
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to get EIO legacy inventory", e);
            }
        }
        return null;
    }

    private boolean pushToAnyFace(TileEntity te, InventoryCrafting ingredients) {
        List<ItemStack> toPush = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) toPush.add(stack.copy());
        }
        for (EnumFacing face : EnumFacing.values()) {
            if (toPush.isEmpty()) break;
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int i = 0; i < toPush.size(); ) {
                ItemStack stack = toPush.get(i);
                ItemStack remaining = stack.copy();
                for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                    remaining = handler.insertItem(slot, remaining, false);
                }
                if (remaining.isEmpty()) {
                    toPush.remove(i);
                } else {
                    toPush.set(i, remaining);
                    i++;
                }
            }
        }
        return toPush.isEmpty();
    }

    private List<ItemStack> revertFromAnyFace(TileEntity te) {
        List<ItemStack> reverted = new ArrayList<>();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (!inSlot.isEmpty()) {
                    ItemStack extracted = handler.extractItem(slot, inSlot.getCount(), false);
                    if (!extracted.isEmpty()) reverted.add(extracted);
                }
            }
        }
        return reverted;
    }

    private List<ItemStack> collectFromAnyFace(TileEntity te, IAEItemStack[] expectedOutputs, List<ItemStack> inputs) {
        List<ItemStack> collected = new ArrayList<>();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                if (isInputMaterial(inSlot, inputs != null ? inputs : Collections.emptyList())) continue;
                ItemStack extracted = handler.extractItem(slot, inSlot.getCount(), false);
                if (!extracted.isEmpty()) collected.add(extracted);
            }
        }
        return collected;
    }

    private boolean isIdleFromAnyFace(TileEntity te, List<ItemStack> inputs) {
        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();
        boolean hasProducts = false;
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                if (isInputMaterial(inSlot, inputsSafe)) return false;
                hasProducts = true;
            }
        }
        return hasProducts;
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

package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 默认单批次处理器（通用 Fallback）。
 *
 * <p>当没有特定 {@link IRemoteHandler} 匹配目标方块类型时，使用此处理器。
 * 适用场景：熔炉、通用加工机器等具备 {@link IItemHandler} 能力、
 * 物品插入后自动开始处理、处理完成后产物留在输出槽的设备。</p>
 *
 * <p>核心修复（回收增强）：</p>
 * <ul>
 *   <li>{@code pushMaterials}：记录推料时间与输入材料快照</li>
 *   <li>{@code isIdle}：推送后立刻检查是否存在<b>非输入材料</b>的可抽取物品（即产物/副产物）。
 *       若无产物但已超时（600 ticks），视为 idle 以清理状态。</li>
 *   <li>{@code collectProducts}：优先收集匹配预期产物的物品（NBT 放宽），再收集所有
 *       其他非输入材料的可抽取物品（副产物、容器残余等）。</li>
 * </ul>
 */
public class DefaultSingleBatchHandler implements IRemoteHandler {

    /** 最大等待 tick 数，防止状态无限卡住 */
    private static final int MAX_WAIT_TICKS = 600;
    /** 推料状态过期时间，避免内存泄漏 */
    private static final int STATE_EXPIRY_TICKS = 1200;

    private static class PushState {
        long pushTick;
        List<ItemStack> inputs;
    }

    /** 按 world dim + BlockPos 索引的推料状态 */
    private final Map<String, PushState> pushStates = new HashMap<>();

    private static String key(World world, BlockPos pos) {
        return world.provider.getDimension() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private void cleanupExpiredStates(long now) {
        pushStates.entrySet().removeIf(e -> now - e.getValue().pushTick > STATE_EXPIRY_TICKS);
    }

    @Override
    public boolean canHandle(String blockId) {
        return false; // 不主动匹配任何 blockId，由 HandlerRegistry 作为兜底 fallback
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return false;
        }
        for (EnumFacing face : EnumFacing.values()) {
            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        // Fallback：只要目标有 IItemHandler 就允许尝试推送
        return isValidTarget(world, pos);
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return false;
        }

        cleanupExpiredStates(world.getTotalWorldTime());

        List<ItemStack> toPush = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                toPush.add(stack.copy());
            }
        }

        for (ItemStack stack : toPush) {
            ItemStack remaining = pushItemToTarget(te, stack);
            if (!remaining.isEmpty()) {
                return false;
            }
        }

        // 记录推料状态
        PushState state = new PushState();
        state.pushTick = world.getTotalWorldTime();
        state.inputs = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                state.inputs.add(stack.copy());
            }
        }
        pushStates.put(key(world, pos), state);

        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        // 通用机器无需显式启动
        return true;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return Collections.emptyList();
        }
        List<ItemStack> reverted = new ArrayList<>();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (!inSlot.isEmpty()) {
                    ItemStack extracted = handler.extractItem(slot, inSlot.getCount(), false);
                    if (!extracted.isEmpty()) {
                        reverted.add(extracted);
                    }
                }
            }
        }
        pushStates.remove(key(world, pos));
        return reverted;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return Collections.emptyList();
        }

        String k = key(world, pos);
        PushState state = pushStates.get(k);
        List<ItemStack> inputs = state != null ? state.inputs : Collections.emptyList();

        List<ItemStack> collected = new ArrayList<>();
        Set<String> visitedSlots = new HashSet<>();

        // 阶段 1：优先收集匹配预期产物的物品（NBT 放宽，兼容 Mekanism / Thermal Expansion 等）
        if (expectedOutputs != null && expectedOutputs.length > 0) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null) continue;
                ItemStack expectedStack = expected.createItemStack();
                ItemStack result = collectExpectedItem(te, expectedStack, inputs, visitedSlots);
                if (!result.isEmpty()) {
                    collected.add(result);
                }
            }
        }

        // 阶段 2：收集所有其他非输入材料的可抽取物品（副产物、残余、容器物品等）
        List<ItemStack> extras = collectAllNonInputItems(te, inputs, visitedSlots);
        collected.addAll(extras);

        // 若收集到了任何物品，清理该目标的推料状态
        if (!collected.isEmpty()) {
            pushStates.remove(k);
        }

        return collected;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        long now = world.getTotalWorldTime();
        cleanupExpiredStates(now);

        PushState state = pushStates.get(key(world, pos));
        if (state == null) {
            return true; // 无推料记录，视为 idle（允许收集已有产物或清理）
        }

        long elapsed = now - state.pushTick;

        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            pushStates.remove(key(world, pos));
            return true;
        }

        // 检查是否存在非输入材料的可抽取物品（即产物）
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                ItemStack simulated = handler.extractItem(slot, 1, true);
                if (simulated.isEmpty()) continue;
                if (!isInputMaterial(inSlot, state.inputs)) {
                    return true; // 发现产物，允许收集
                }
            }
        }

        // 无可抽取产物。若已超时，视为 idle 以清理状态
        if (elapsed > MAX_WAIT_TICKS) {
            pushStates.remove(key(world, pos));
            return true;
        }

        return false; // 仍在处理中，继续等待
    }

    // ---- Internal helpers ----

    private ItemStack pushItemToTarget(TileEntity target, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            if (remaining.isEmpty()) {
                break;
            }
        }
        return remaining;
    }

    /**
     * 从目标中收集与 expected 匹配的物品（严格匹配 item+meta，NBT 放宽）。
     *
     * @param inputs       输入材料快照，用于跳过未消耗的输入
     * @param visitedSlots 已访问的槽位 key 集合，避免重复收集
     */
    private ItemStack collectExpectedItem(TileEntity target, ItemStack expected,
                                          List<ItemStack> inputs, Set<String> visitedSlots) {
        ItemStack collected = ItemStack.EMPTY;
        int remainingAmount = expected.getCount();

        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;

            for (int slot = 0; slot < handler.getSlots() && remainingAmount > 0; slot++) {
                String slotKey = face.ordinal() + ":" + slot;
                if (visitedSlots.contains(slotKey)) continue;

                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                if (isInputMaterial(inSlot, inputs)) continue;

                if (!matchesLoosely(inSlot, expected)) continue;

                int toExtract = Math.min(remainingAmount, inSlot.getCount());
                ItemStack extracted = handler.extractItem(slot, toExtract, false);
                if (!extracted.isEmpty()) {
                    visitedSlots.add(slotKey);
                    if (collected.isEmpty()) {
                        collected = extracted.copy();
                    } else {
                        collected.grow(extracted.getCount());
                    }
                    remainingAmount -= extracted.getCount();
                }
            }
            if (remainingAmount <= 0) {
                break;
            }
        }
        return collected;
    }

    /**
     * 收集目标中所有未被访问过且不是输入材料的可抽取物品。
     */
    private List<ItemStack> collectAllNonInputItems(TileEntity target,
                                                    List<ItemStack> inputs, Set<String> visitedSlots) {
        List<ItemStack> collected = new ArrayList<>();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                String slotKey = face.ordinal() + ":" + slot;
                if (visitedSlots.contains(slotKey)) continue;

                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                if (isInputMaterial(inSlot, inputs)) continue;

                ItemStack extracted = handler.extractItem(slot, inSlot.getCount(), false);
                if (!extracted.isEmpty()) {
                    visitedSlots.add(slotKey);
                    collected.add(extracted);
                }
            }
        }
        return collected;
    }

    /**
     * 判断物品是否属于输入材料（严格匹配 item + meta + NBT）。
     * 用于在机器处理期间保护输入槽不被误回收。
     */
    private boolean isInputMaterial(ItemStack stack, List<ItemStack> inputs) {
        for (ItemStack input : inputs) {
            if (ItemStack.areItemsEqual(stack, input) && ItemStack.areItemStackTagsEqual(stack, input)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 宽松匹配：先比较 item + metadata，NBT 方面若 expected 没有 NBT 则不检查。
     * 可兼容 Mekanism / Thermal Expansion 等模组在产物上附加默认 NBT 的情况。
     */
    private boolean matchesLoosely(ItemStack actual, ItemStack expected) {
        if (!ItemStack.areItemsEqual(actual, expected)) {
            return false;
        }
        if (!expected.hasTagCompound()) {
            return true;
        }
        return ItemStack.areItemStackTagsEqual(actual, expected);
    }
}

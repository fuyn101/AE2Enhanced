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
import java.util.List;

/**
 * 默认单批次处理器（通用 Fallback）。
 *
 * <p>当没有特定 {@link IRemoteHandler} 匹配目标方块类型时，使用此处理器。
 * 适用场景：熔炉、通用加工机器等具备 {@link IItemHandler} 能力、
 * 物品插入后自动开始处理、处理完成后产物留在输出槽的设备。</p>
 *
 * <p>行为概述：</p>
 * <ul>
 *   <li>{@code pushMaterials}：遍历目标所有面，将材料插入首个可用的 IItemHandler</li>
 *   <li>{@code startProcess}：空操作（机器自动开始）</li>
 *   <li>{@code isIdle}：始终返回 true（让 tick 轮询尝试收集）</li>
 *   <li>{@code collectProducts}：从目标所有面的 IItemHandler 中抽取匹配预期产物的物品</li>
 * </ul>
 */
public class DefaultSingleBatchHandler implements IRemoteHandler {

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
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        // 通用机器无需显式启动
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return Collections.emptyList();
        }

        List<ItemStack> collected = new ArrayList<>();
        if (expectedOutputs == null || expectedOutputs.length == 0) {
            return collected;
        }

        // 收集所有匹配预期产物的物品
        for (IAEItemStack expected : expectedOutputs) {
            if (expected == null) {
                continue;
            }
            ItemStack expectedStack = expected.createItemStack();
            ItemStack result = collectItemFromTarget(te, expectedStack);
            if (!result.isEmpty()) {
                collected.add(result);
            }
        }

        return collected;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        // Fallback 策略：始终视为 idle，让 tickingRequest 尝试收集。
        // 若目标尚无产物，collectProducts 会返回空列表，状态保持 PROCESSING 等待下次 tick。
        return true;
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

    private ItemStack collectItemFromTarget(TileEntity target, ItemStack expected) {
        ItemStack collected = ItemStack.EMPTY;
        int remainingAmount = expected.getCount();

        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && remainingAmount > 0; slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) {
                    continue;
                }
                if (!ItemStack.areItemsEqual(inSlot, expected)) {
                    continue;
                }
                int toExtract = Math.min(remainingAmount, inSlot.getCount());
                ItemStack extracted = handler.extractItem(slot, toExtract, false);
                if (!extracted.isEmpty()) {
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
}

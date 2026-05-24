package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认单批次处理器。
 *
 * 当没有特定 IRemoteHandler 匹配目标方块时，使用此通用 fallback：
 * - pushMaterials：遍历目标所有面，将材料插入第一个可用的 IItemHandler
 * - collectProducts：遍历目标所有面，从 IItemHandler 中抽取匹配的预期产物
 */
public class DefaultSingleBatchHandler implements IRemoteHandler {

    @Override
    public boolean matches(TileEntity target) {
        return true; // 通用 fallback，始终匹配
    }

    @Override
    public boolean pushMaterials(TileEntity target, InventoryCrafting ingredients, IActionSource source) {
        List<ItemStack> toPush = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                toPush.add(stack.copy());
            }
        }

        for (ItemStack stack : toPush) {
            ItemStack remaining = pushItemToTarget(target, stack);
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack collectProducts(TileEntity target, IAEItemStack expected, IActionSource source) {
        if (expected == null) {
            return ItemStack.EMPTY;
        }
        return collectItemFromTarget(target, expected.createItemStack());
    }

    private ItemStack pushItemToTarget(TileEntity target, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            if (remaining.isEmpty()) break;
        }
        return remaining;
    }

    private ItemStack collectItemFromTarget(TileEntity target, ItemStack expected) {
        ItemStack collected = ItemStack.EMPTY;
        int remainingAmount = expected.getCount();

        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots() && remainingAmount > 0; slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
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
            if (remainingAmount <= 0) break;
        }
        return collected;
    }
}

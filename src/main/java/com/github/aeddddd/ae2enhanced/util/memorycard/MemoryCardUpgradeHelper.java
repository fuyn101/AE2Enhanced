package com.github.aeddddd.ae2enhanced.util.memorycard;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;

/**
 * 通用内存卡升级槽序列化与粘贴的公共辅助方法。
 */
public class MemoryCardUpgradeHelper {

    public static NBTTagList serializeUpgrades(IItemHandler upgrades) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Slot", i);
                stack.writeToNBT(tag);
                list.appendTag(tag);
            }
        }
        return list;
    }

    public static IMemoryCardHandler.PasteResult applyUpgrades(IItemHandler upgrades, NBTTagList list, EntityPlayer player) {
        // 先检查背包是否有足够的升级卡
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack needed = new ItemStack(tag);
            if (needed.isEmpty()) continue;

            int required = needed.getCount();
            int available = countInInventory(player, needed);
            if (available < required) {
                return IMemoryCardHandler.PasteResult.MISSING_UPGRADES;
            }
        }

        // 弹出目标现有升级卡
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack existing = upgrades.extractItem(i, Integer.MAX_VALUE, false);
            if (!existing.isEmpty()) {
                if (!player.addItemStackToInventory(existing)) {
                    player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, existing));
                }
            }
        }

        // 从背包扣除并放入新升级卡
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getInteger("Slot");
            ItemStack needed = new ItemStack(tag);
            if (needed.isEmpty()) continue;

            consumeFromInventory(player, needed);

            if (slot >= 0 && slot < upgrades.getSlots()) {
                upgrades.insertItem(slot, needed, false);
            }
        }

        return IMemoryCardHandler.PasteResult.SUCCESS;
    }

    public static int countInInventory(EntityPlayer player, ItemStack stack) {
        int count = 0;
        for (ItemStack invStack : player.inventory.mainInventory) {
            if (invStack.isEmpty()) continue;
            if (ItemStack.areItemsEqual(stack, invStack) && ItemStack.areItemStackTagsEqual(stack, invStack)) {
                count += invStack.getCount();
            }
        }
        for (ItemStack invStack : player.inventory.offHandInventory) {
            if (invStack.isEmpty()) continue;
            if (ItemStack.areItemsEqual(stack, invStack) && ItemStack.areItemStackTagsEqual(stack, invStack)) {
                count += invStack.getCount();
            }
        }
        return count;
    }

    public static void consumeFromInventory(EntityPlayer player, ItemStack stack) {
        int remaining = stack.getCount();
        for (int i = 0; i < player.inventory.mainInventory.size() && remaining > 0; i++) {
            ItemStack invStack = player.inventory.mainInventory.get(i);
            if (invStack.isEmpty()) continue;
            if (ItemStack.areItemsEqual(stack, invStack) && ItemStack.areItemStackTagsEqual(stack, invStack)) {
                int take = Math.min(remaining, invStack.getCount());
                invStack.shrink(take);
                if (invStack.isEmpty()) {
                    player.inventory.mainInventory.set(i, ItemStack.EMPTY);
                }
                remaining -= take;
            }
        }
        if (remaining > 0) {
            for (int i = 0; i < player.inventory.offHandInventory.size() && remaining > 0; i++) {
                ItemStack invStack = player.inventory.offHandInventory.get(i);
                if (invStack.isEmpty()) continue;
                if (ItemStack.areItemsEqual(stack, invStack) && ItemStack.areItemStackTagsEqual(stack, invStack)) {
                    int take = Math.min(remaining, invStack.getCount());
                    invStack.shrink(take);
                    if (invStack.isEmpty()) {
                        player.inventory.offHandInventory.set(i, ItemStack.EMPTY);
                    }
                    remaining -= take;
                }
            }
        }
    }
}

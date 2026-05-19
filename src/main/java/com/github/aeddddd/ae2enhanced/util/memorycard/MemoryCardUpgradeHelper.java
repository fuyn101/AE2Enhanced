package com.github.aeddddd.ae2enhanced.util.memorycard;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.PlayerSource;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

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
        // 1. 计算缺失数量
        List<ItemStack> missing = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack needed = new ItemStack(tag);
            if (needed.isEmpty()) continue;

            int required = needed.getCount();
            int available = countInInventory(player, needed);
            if (available < required) {
                ItemStack deficit = needed.copy();
                deficit.setCount(required - available);
                missing.add(deficit);
            }
        }

        // 2. 有缺失时，尝试从绑定的 ME 网络补充
        if (!missing.isEmpty()) {
            boolean pulled = tryPullFromNetwork(player, missing);
            if (!pulled) {
                return IMemoryCardHandler.PasteResult.MISSING_UPGRADES;
            }
            // 补充后再验证一次
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                ItemStack needed = new ItemStack(tag);
                if (needed.isEmpty()) continue;
                if (countInInventory(player, needed) < needed.getCount()) {
                    return IMemoryCardHandler.PasteResult.MISSING_UPGRADES;
                }
            }
        }

        // 3. 弹出目标现有升级卡
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack existing = upgrades.extractItem(i, Integer.MAX_VALUE, false);
            if (!existing.isEmpty()) {
                if (!player.addItemStackToInventory(existing)) {
                    player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, existing));
                }
            }
        }

        // 4. 从背包扣除并放入新升级卡
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

    private static boolean tryPullFromNetwork(EntityPlayer player, List<ItemStack> missing) {
        ItemStack handStack = player.getHeldItemMainhand();
        if (!(handStack.getItem() instanceof ItemUniversalMemoryCard)) return false;
        if (!ItemUniversalMemoryCard.hasBinding(handStack)) return false;

        NBTTagCompound binding = ItemUniversalMemoryCard.getBinding(handStack);
        BlockPos pos = BlockPos.fromLong(binding.getLong("pos"));
        int dim = binding.getInteger("dim");

        World world = player.getEntityWorld();
        if (world.provider.getDimension() != dim) return false;
        if (!world.isBlockLoaded(pos)) return false;

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileWirelessChannelTransmitter)) return false;
        TileWirelessChannelTransmitter transmitter = (TileWirelessChannelTransmitter) te;

        try {
            appeng.api.networking.IGrid grid = transmitter.getProxy().getGrid();
            appeng.api.networking.storage.IStorageGrid storageGrid = grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            if (storageGrid == null) return false;
            IMEMonitor<IAEItemStack> inv = storageGrid.getInventory(
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
            PlayerSource source = new PlayerSource(player, null);

            // 模拟提取，确保网络库存足够
            for (ItemStack deficit : missing) {
                AEItemStack want = AEItemStack.fromItemStack(deficit);
                IAEItemStack sim = inv.extractItems(want, Actionable.SIMULATE, source);
                if (sim == null || sim.getStackSize() < deficit.getCount()) {
                    return false;
                }
            }

            // 实际提取
            for (ItemStack deficit : missing) {
                AEItemStack want = AEItemStack.fromItemStack(deficit);
                IAEItemStack extracted = inv.extractItems(want, Actionable.MODULATE, source);
                if (extracted != null && extracted.getStackSize() > 0) {
                    ItemStack stack = extracted.createItemStack();
                    if (!player.addItemStackToInventory(stack)) {
                        player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, stack));
                    }
                }
            }
            return true;
        } catch (GridAccessException e) {
            AE2Enhanced.LOGGER.debug("[AE2E] UMC bound transmitter grid not accessible at {}", pos);
            return false;
        }
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

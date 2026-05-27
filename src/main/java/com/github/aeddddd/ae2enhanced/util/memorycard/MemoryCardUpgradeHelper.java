package com.github.aeddddd.ae2enhanced.util.memorycard;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.me.helpers.PlayerSource;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import com.google.common.collect.ImmutableSet;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    public static boolean tryPullFromNetwork(EntityPlayer player, List<ItemStack> missing) {
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
            ICraftingGrid craftingGrid = grid.getCache(ICraftingGrid.class);

            List<ItemStack> stillMissing = new ArrayList<>();
            List<ItemStack> craftable = new ArrayList<>();
            List<ItemStack> directExtract = new ArrayList<>();

            // 模拟提取，分类：可直接提取 vs 需要合成
            for (ItemStack deficit : missing) {
                AEItemStack want = AEItemStack.fromItemStack(deficit);
                IAEItemStack sim = inv.extractItems(want, Actionable.SIMULATE, source);
                if (sim != null && sim.getStackSize() >= deficit.getCount()) {
                    // 网络有足够库存，全部直接提取
                    directExtract.add(deficit.copy());
                    continue;
                }

                long available = sim != null ? sim.getStackSize() : 0;
                if (available > 0) {
                    // 部分可提取
                    ItemStack partial = deficit.copy();
                    partial.setCount((int) available);
                    directExtract.add(partial);
                }
                int needCount = deficit.getCount() - (int) available;
                if (needCount > 0) {
                    ItemStack need = deficit.copy();
                    need.setCount(needCount);

                    // 检查是否能合成
                    if (craftingGrid != null && craftingGrid.canEmitFor(AEItemStack.fromItemStack(need))) {
                        craftable.add(need);
                    } else {
                        stillMissing.add(need);
                    }
                }
            }

            // 如果有无法合成也无法提取的物品，直接失败
            if (!stillMissing.isEmpty()) {
                return false;
            }

            // 尝试为可合成的物品发起合成请求
            boolean craftingRequested = false;
            if (!craftable.isEmpty() && craftingGrid != null) {
                craftingRequested = requestCrafting(player, world, grid, source, craftingGrid, craftable, transmitter);
            }

            // 实际提取可直接获取的物品
            for (ItemStack toExtract : directExtract) {
                AEItemStack want = AEItemStack.fromItemStack(toExtract);
                IAEItemStack extracted = inv.extractItems(want, Actionable.MODULATE, source);
                if (extracted != null && extracted.getStackSize() > 0) {
                    ItemStack stack = extracted.createItemStack();
                    if (!player.addItemStackToInventory(stack)) {
                        player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, stack));
                    }
                }
            }

            if (craftingRequested) {
                // 发送消息提示玩家已请求合成
                player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.crafting_requested"));
            }

            // 如果还有需要合成的物品，返回 false（物品未实际到账）
            return !craftingRequested;
        } catch (GridAccessException e) {
            AE2Enhanced.LOGGER.debug("[AE2E] UMC bound transmitter grid not accessible at {}", pos);
            return false;
        }
    }

    private static boolean requestCrafting(EntityPlayer player, World world, appeng.api.networking.IGrid grid,
                                           PlayerSource source, ICraftingGrid craftingGrid,
                                           List<ItemStack> toCraft, TileWirelessChannelTransmitter transmitter) {
        boolean anyRequested = false;

        ICraftingRequester requester = new ICraftingRequester() {
            @Override
            public IGridNode getActionableNode() {
                return transmitter.getProxy().getNode();
            }

            @Override
            public ImmutableSet<ICraftingLink> getRequestedJobs() {
                return ImmutableSet.of();
            }

            @Override
            public IAEItemStack injectCraftedItems(ICraftingLink link, IAEItemStack items, Actionable mode) {
                return items;
            }

            @Override
            public void jobStateChange(ICraftingLink link) {
            }
        };

        for (ItemStack stack : toCraft) {
            try {
                IAEItemStack want = AEItemStack.fromItemStack(stack);
                Future<ICraftingJob> future = craftingGrid.beginCraftingJob(world, grid, source, want, new ICraftingCallback() {
                    @Override
                    public void calculationComplete(ICraftingJob job) {
                        // 同步回调中提交合成
                        try {
                            craftingGrid.submitJob(job, requester, null, false, source);
                        } catch (Exception e) {
                            AE2Enhanced.LOGGER.debug("[AE2E] Failed to submit crafting job", e);
                        }
                    }
                });

                // 短暂等待计算完成
                ICraftingJob job = future.get(200, TimeUnit.MILLISECONDS);
                if (job != null) {
                    ICraftingLink link = craftingGrid.submitJob(job, requester, null, false, source);
                    if (link != null) {
                        anyRequested = true;
                    }
                }
            } catch (java.util.concurrent.TimeoutException e) {
                // 计算超时，但异步回调可能仍会提交
                anyRequested = true;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Crafting request failed for {}", stack.getDisplayName(), e);
            }
        }

        return anyRequested;
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

    /**
     * 确保玩家背包（含 ME 网络回退）中有足够的物品。
     * 如果网络拉取了物品，它们会被放入玩家背包。
     * @return true 表示所有物品都已在背包中可用
     */
    public static boolean ensureAvailable(EntityPlayer player, List<ItemStack> needed) {
        List<ItemStack> missing = new ArrayList<>();
        for (ItemStack need : needed) {
            if (need.isEmpty()) continue;
            int available = countInInventory(player, need);
            if (available < need.getCount()) {
                ItemStack deficit = need.copy();
                deficit.setCount(need.getCount() - available);
                missing.add(deficit);
            }
        }
        if (missing.isEmpty()) return true;

        boolean pulled = tryPullFromNetwork(player, missing);
        if (!pulled) return false;

        // 网络回退后再次验证
        for (ItemStack need : needed) {
            if (need.isEmpty()) continue;
            if (countInInventory(player, need) < need.getCount()) {
                return false;
            }
        }
        return true;
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

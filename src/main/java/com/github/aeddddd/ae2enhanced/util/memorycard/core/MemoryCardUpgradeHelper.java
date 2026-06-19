package com.github.aeddddd.ae2enhanced.util.memorycard.core;

import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.IUpgradeProvider;
import com.github.aeddddd.ae2enhanced.util.memorycard.upgrade.ItemHandlerUpgradeAdapter;
import com.google.common.collect.ImmutableSet;

import ae2.api.config.Actionable;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.crafting.CalculationStrategy;
import ae2.api.networking.crafting.ICraftingLink;
import ae2.api.networking.crafting.ICraftingPlan;
import ae2.api.networking.crafting.ICraftingRequester;
import ae2.api.networking.crafting.ICraftingService;
import ae2.api.networking.crafting.ICraftingSimulationRequester;
import ae2.api.networking.crafting.ICraftingSubmitResult;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.storage.MEStorage;
import ae2.me.helpers.PlayerSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
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
 * 通用内存卡升级槽序列化与粘贴的公共辅助方法.
 *
 * 架构约定：
 * 1. 所有升级操作基于 IUpgradeProvider 抽象,不再直接依赖 IItemHandler.
 * 2. IItemHandler 的兼容通过 ItemHandlerUpgradeAdapter 桥接.
 * 3. tryPullFromNetwork 返回 NetworkPullResult 三元状态,区分直接提取 / 已请求合成 / 失败.
 */
public class MemoryCardUpgradeHelper {

    public enum NetworkPullResult {
        PULLED,              // 所有物品已直接提取到账
        CRAFTING_REQUESTED,  // 部分或全部物品已提交合成请求(尚未到账)
        FAILED               // 无法获取(既无库存也无法合成)
    }

    // ================== IUpgradeProvider API ==================

    public static NBTTagList serializeUpgrades(IUpgradeProvider provider) {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < provider.getSlotCount(); i++) {
            ItemStack stack = provider.getStackInSlot(i);
            if (!stack.isEmpty()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setInteger("Slot", i);
                stack.writeToNBT(tag);
                list.appendTag(tag);
            }
        }
        return list;
    }

    public static List<ItemStack> deserializeUpgrades(NBTTagList list) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(tag);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
        return result;
    }

    /**
     * 基于 IUpgradeProvider 的统一升级应用流程.
     * 1. 统一验证(含网络回退)
     * 2. 弹出旧升级(返还玩家背包)
     * 3. 消耗新升级
     * 4. 放入新升级
     */
    public static PasteResult applyUpgrades(IUpgradeProvider provider, List<ItemStack> needed, EntityPlayer player) {
        if (needed.isEmpty()) {
            provider.clearSlots();
            return PasteResult.SUCCESS;
        }

        // 1. 统一验证(含网络回退)
        if (!ensureAvailable(player, needed)) {
            return PasteResult.MISSING_UPGRADES;
        }

        // 2. 弹出旧升级
        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < provider.getSlotCount(); i++) {
            ItemStack old = provider.getStackInSlot(i);
            if (!old.isEmpty()) {
                removed.add(old.copy());
            }
        }
        provider.clearSlots();

        for (ItemStack old : removed) {
            if (!player.addItemStackToInventory(old)) {
                player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, old));
            }
        }

        // 3. 消耗新升级
        for (ItemStack need : needed) {
            consumeFromInventory(player, need);
        }

        // 4. 放入新升级
        for (int i = 0; i < needed.size() && i < provider.getSlotCount(); i++) {
            provider.setStackInSlot(i, needed.get(i).copy());
        }

        return PasteResult.SUCCESS;
    }

    // ================== IItemHandler 兼容层 ==================

    public static NBTTagList serializeUpgrades(IItemHandler upgrades) {
        return serializeUpgrades(new ItemHandlerUpgradeAdapter(upgrades));
    }

    public static PasteResult applyUpgrades(IItemHandler upgrades, NBTTagList list, EntityPlayer player) {
        return applyUpgrades(new ItemHandlerUpgradeAdapter(upgrades), deserializeUpgrades(list), player);
    }

    // ================== 网络回退 ==================

    public static NetworkPullResult tryPullFromNetwork(EntityPlayer player, List<ItemStack> missing) {
        ItemStack handStack = player.getHeldItemMainhand();
        if (!(handStack.getItem() instanceof ItemUniversalMemoryCard)) return NetworkPullResult.FAILED;
        if (!ItemUniversalMemoryCard.hasBinding(handStack)) return NetworkPullResult.FAILED;

        NBTTagCompound binding = ItemUniversalMemoryCard.getBinding(handStack);
        BlockPos pos = BlockPos.fromLong(binding.getLong("pos"));
        int dim = binding.getInteger("dim");

        World world = player.getEntityWorld();
        if (world.provider.getDimension() != dim) return NetworkPullResult.FAILED;
        if (!world.isBlockLoaded(pos)) return NetworkPullResult.FAILED;

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileWirelessChannelTransmitter)) return NetworkPullResult.FAILED;
        TileWirelessChannelTransmitter transmitter = (TileWirelessChannelTransmitter) te;

        if (!transmitter.getMainNode().isActive()) return NetworkPullResult.FAILED;

        IGrid grid = transmitter.getMainNode().getGrid();
        if (grid == null) return NetworkPullResult.FAILED;

        IStorageService storageService = grid.getService(IStorageService.class);
        if (storageService == null) return NetworkPullResult.FAILED;
        MEStorage inv = storageService.getInventory();
        if (inv == null) return NetworkPullResult.FAILED;

        PlayerSource source = new PlayerSource(player, transmitter);
        ICraftingService craftingGrid = grid.getService(ICraftingService.class);

        List<ItemStack> stillMissing = new ArrayList<>();
        List<ItemStack> craftable = new ArrayList<>();
        List<ItemStack> directExtract = new ArrayList<>();

        for (ItemStack deficit : missing) {
            AEItemKey want = AEItemKey.of(deficit);
            if (want == null) {
                stillMissing.add(deficit);
                continue;
            }

            long available = inv.extract(want, deficit.getCount(), Actionable.SIMULATE, source);
            if (available >= deficit.getCount()) {
                directExtract.add(deficit.copy());
                continue;
            }

            if (available > 0) {
                ItemStack partial = deficit.copy();
                partial.setCount((int) available);
                directExtract.add(partial);
            }
            int needCount = deficit.getCount() - (int) available;
            if (needCount > 0) {
                ItemStack need = deficit.copy();
                need.setCount(needCount);

                if (craftingGrid != null && craftingGrid.canEmitFor(AEItemKey.of(need))) {
                    craftable.add(need);
                } else {
                    stillMissing.add(need);
                }
            }
        }

        if (!stillMissing.isEmpty()) {
            return NetworkPullResult.FAILED;
        }

        boolean craftingRequested = false;
        if (!craftable.isEmpty() && craftingGrid != null) {
            craftingRequested = requestCrafting(player, world, grid, source, craftingGrid, craftable, transmitter);
        }

        for (ItemStack toExtract : directExtract) {
            AEItemKey want = AEItemKey.of(toExtract);
            if (want == null) continue;
            long extracted = inv.extract(want, toExtract.getCount(), Actionable.MODULATE, source);
            if (extracted > 0) {
                ItemStack stack = want.toStack((int) extracted);
                if (!player.addItemStackToInventory(stack)) {
                    player.world.spawnEntity(new EntityItem(player.world, player.posX, player.posY, player.posZ, stack));
                }
            }
        }

        if (craftingRequested) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.crafting_requested"));
            return NetworkPullResult.CRAFTING_REQUESTED;
        }

        return NetworkPullResult.PULLED;
    }

    private static boolean requestCrafting(EntityPlayer player, World world, IGrid grid,
                                           IActionSource source, ICraftingService craftingGrid,
                                           List<ItemStack> toCraft, TileWirelessChannelTransmitter transmitter) {
        boolean anyRequested = false;

        ICraftingRequester requester = new ICraftingRequester() {
            @Override
            public IGridNode getActionableNode() {
                return transmitter.getMainNode().getNode();
            }

            @Override
            public ImmutableSet<ICraftingLink> getRequestedJobs() {
                return ImmutableSet.of();
            }

            @Override
            public long insertCraftedItems(ICraftingLink link, AEKey items, long amount, Actionable mode) {
                return amount;
            }

            @Override
            public void jobStateChange(ICraftingLink link) {
            }
        };

        ICraftingSimulationRequester simulationRequester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return source;
            }

            @Override
            public IGridNode getGridNode() {
                return transmitter.getMainNode().getNode();
            }
        };

        for (ItemStack stack : toCraft) {
            try {
                AEItemKey want = AEItemKey.of(stack);
                if (want == null) continue;
                Future<ICraftingPlan> future = craftingGrid.beginCraftingCalculation(
                        world, simulationRequester, want, stack.getCount(), CalculationStrategy.CRAFT_LESS);

                ICraftingPlan plan = future.get(200, TimeUnit.MILLISECONDS);
                if (plan != null) {
                    ICraftingSubmitResult result = craftingGrid.submitJob(plan, requester, null, false, source);
                    if (result != null && result.successful()) {
                        anyRequested = true;
                    }
                }
            } catch (java.util.concurrent.TimeoutException e) {
                anyRequested = true;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Crafting request failed for {}", stack.getDisplayName(), e);
            }
        }

        return anyRequested;
    }

    // ================== 背包操作 ==================

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
     * 确保玩家背包(含 ME 网络回退)中有足够的物品.
     * 如果网络拉取了物品,它们会被放入玩家背包.
     * @return true 表示所有物品都已在背包中可用(CRAFTING_REQUESTED 也视为 true,因为合成已启动)
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

        NetworkPullResult result = tryPullFromNetwork(player, missing);
        if (result == NetworkPullResult.FAILED) return false;

        // PULLED 或 CRAFTING_REQUESTED：再次验证库存
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

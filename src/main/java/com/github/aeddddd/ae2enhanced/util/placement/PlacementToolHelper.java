package com.github.aeddddd.ae2enhanced.util.placement;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.util.AEPartLocation;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.facade.FacadePart;
import appeng.facade.IFacadeItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraftforge.common.util.BlockSnapshot;

/**
 * 放置工具核心辅助类。
 * 负责从 ME 网络提取物品并放置方块、AE2 Part、Facade，以及批量放置和撤销。
 */
public final class PlacementToolHelper {

    private PlacementToolHelper() {}

    // 每个玩家最近一次的批量放置记录，用于撤销
    private static final Map<UUID, UndoRecord> PLAYER_UNDO = new LinkedHashMap<UUID, UndoRecord>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, UndoRecord> eldest) {
            return size() > 100;
        }
    };

    /**
     * 单格放置。从配置读取当前选中物品，从网络提取并放置到指定位置。
     *
     * @return 是否成功放置
     */
    public static boolean placeSingle(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                      EnumHand hand, ItemStack toolStack, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;

        IGrid grid = SecurityTerminalBindingHelper.getLinkedGrid(toolStack, world, player);
        if (grid == null) return false;

        IMEMonitor<IAEItemStack> monitor = SecurityTerminalBindingHelper.getItemMonitor(grid);
        if (monitor == null) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.no_storage"));
            return false;
        }

        PlacementConfig config = new PlacementConfig(toolStack);
        ItemStack target = config.getStackInSlot(config.getSelectedSlot());
        if (target.isEmpty()) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.no_configured_item"));
            return false;
        }

        IAEItemStack request = findMatchingStack(monitor, target);
        if (request == null) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                    target.getDisplayName()));
            return false;
        }

        // 模拟提取
        IAEItemStack toExtract = request.copy();
        toExtract.setStackSize(1);
        IAEItemStack simulated = monitor.extractItems(toExtract, Actionable.SIMULATE, SecurityTerminalBindingHelper.createPlayerSource(player));
        if (simulated == null || simulated.getStackSize() < 1) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                    target.getDisplayName()));
            return false;
        }

        ItemStack placeStack = request.getDefinition().copy();
        placeStack.setCount(1);

        // 记录回滚信息
        BlockPos blockPlacePos = pos.offset(side);
        IBlockState prevBlockState = world.getBlockState(blockPlacePos);

        boolean placed = false;
        PlacementTarget targetType = PlacementTarget.OTHER;

        try {
            if (placeStack.getItem() instanceof ItemBlock) {
                targetType = PlacementTarget.BLOCK;
                placed = tryPlaceBlock(player, world, pos, side, hand, placeStack, hitX, hitY, hitZ);
            } else if (placeStack.getItem() instanceof IPartItem) {
                targetType = PlacementTarget.PART;
                placed = tryPlacePart(player, world, pos, side, hand, placeStack);
            } else if (placeStack.getItem() instanceof IFacadeItem) {
                targetType = PlacementTarget.FACADE;
                placed = tryPlaceFacade(player, world, pos, side, placeStack);
            }
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Exception during placement", e);
        }

        if (placed) {
            // 实际提取
            IAEItemStack extracted = monitor.extractItems(toExtract, Actionable.MODULATE, SecurityTerminalBindingHelper.createPlayerSource(player));
            if (extracted == null || extracted.getStackSize() < 1) {
                // 提取失败，回滚
                rollbackSingle(world, pos, side, prevBlockState, targetType);
                player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.network_missing",
                        target.getDisplayName()));
                return false;
            }

            BlockPos soundPos = targetType == PlacementTarget.BLOCK ? blockPlacePos : pos;
            world.playSound(null, soundPos, net.minecraft.init.SoundEvents.BLOCK_STONE_PLACE,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            player.swingArm(hand);
            return true;
        } else {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.cannot_place"));
            return false;
        }
    }

    /**
     * 批量放置（BFS）。Phase 3 完整实现，Phase 1 保留入口。
     */
    public static boolean placeBulk(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                    EnumHand hand, ItemStack toolStack, int count, float hitX, float hitY, float hitZ) {
        // Phase 3 实现
        player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.not_implemented"));
        return false;
    }

    /**
     * 撤销最后一次批量放置。Phase 3 完整实现。
     */
    public static boolean undoLast(EntityPlayer player, World world, ItemStack toolStack) {
        // Phase 3 实现
        player.sendMessage(new net.minecraft.util.text.TextComponentTranslation("message.ae2enhanced.placement.not_implemented"));
        return false;
    }

    // ==================== 内部放置逻辑 ====================

    private static boolean tryPlaceBlock(EntityPlayer player, World world, BlockPos clickedPos, EnumFacing side,
                                         EnumHand hand, ItemStack placeStack, float hitX, float hitY, float hitZ) {
        BlockPos placePos = clickedPos.offset(side);
        if (!world.isAirBlock(placePos) && !world.getBlockState(placePos).getBlock().isReplaceable(world, placePos)) {
            return false;
        }

        ItemStack originalMain = player.getHeldItemMainhand();
        ItemStack originalOff = player.getHeldItemOffhand();

        try {
            player.setHeldItem(EnumHand.MAIN_HAND, placeStack);
            player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
            EnumActionResult result = placeStack.onItemUse(player, world, clickedPos, hand, side, hitX, hitY, hitZ);
            return result == EnumActionResult.SUCCESS;
        } finally {
            player.setHeldItem(EnumHand.MAIN_HAND, originalMain);
            player.setHeldItem(EnumHand.OFF_HAND, originalOff);
        }
    }

    private static boolean tryPlacePart(EntityPlayer player, World world, BlockPos clickedPos, EnumFacing side,
                                                 EnumHand hand, ItemStack placeStack) {
        try {
            EnumActionResult result = AEApi.instance().partHelper().placeBus(placeStack, clickedPos, side, player, hand, world);
            return result == EnumActionResult.SUCCESS;
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to place part", e);
            return false;
        }
    }

    private static boolean tryPlaceFacade(EntityPlayer player, World world, BlockPos clickedPos, EnumFacing side,
                                          ItemStack placeStack) {
        try {
            IFacadeItem facadeItem = (IFacadeItem) placeStack.getItem();
            AEPartLocation loc = AEPartLocation.fromFacing(side);
            FacadePart facade = facadeItem.createPartFromItemStack(placeStack, loc);
            if (facade == null) return false;

            IPartHost host = AEApi.instance().partHelper().getPartHost(world, clickedPos);
            if (host == null) return false;

            if (host.getPart(AEPartLocation.INTERNAL) != null && host.getFacadeContainer().canAddFacade(facade)) {
                boolean added = host.getFacadeContainer().addFacade(facade);
                if (added) {
                    host.markForSave();
                    host.markForUpdate();
                    return true;
                }
            }
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to place facade", e);
        }
        return false;
    }

    // ==================== 网络匹配与回滚 ====================

    @Nullable
    private static IAEItemStack findMatchingStack(IMEMonitor<IAEItemStack> monitor, ItemStack target) {
        IAEItemStack request = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
                .createStack(target);
        if (request == null) return null;
        request.setStackSize(1);

        // 优先精确匹配
        for (IAEItemStack stack : monitor.getStorageList()) {
            if (stack.isSameType(target)) {
                return stack.copy();
            }
        }

        // 退而忽略 NBT 匹配 item + meta
        for (IAEItemStack stack : monitor.getStorageList()) {
            ItemStack netStack = stack.getDefinition().copy();
            netStack.setCount(1);
            if (netStack.getItem() == target.getItem()
                    && netStack.getMetadata() == target.getMetadata()) {
                return stack.copy();
            }
        }

        return null;
    }

    private static void rollbackSingle(World world, BlockPos clickedPos, EnumFacing side,
                                       IBlockState prevBlockState, PlacementTarget targetType) {
        try {
            if (targetType == PlacementTarget.BLOCK) {
                BlockPos placePos = clickedPos.offset(side);
                world.setBlockState(placePos, prevBlockState);
            } else if (targetType == PlacementTarget.PART) {
                IPartHost host = AEApi.instance().partHelper().getPartHost(world, clickedPos);
                if (host != null) {
                    host.removePart(AEPartLocation.fromFacing(side), true);
                    host.markForSave();
                    host.markForUpdate();
                }
            } else if (targetType == PlacementTarget.FACADE) {
                IPartHost host = AEApi.instance().partHelper().getPartHost(world, clickedPos);
                if (host != null) {
                    host.getFacadeContainer().removeFacade(host, AEPartLocation.fromFacing(side));
                    host.markForSave();
                    host.markForUpdate();
                }
            }
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to rollback placement at {}", clickedPos, e);
        }
    }

    private enum PlacementTarget {
        BLOCK, PART, FACADE, OTHER
    }

    // ==================== 撤销记录 ====================

    private static class UndoRecord {
        final List<BlockSnapshot> snapshots = new ArrayList<>();
        final Map<IAEItemStack, Long> consumed = new LinkedHashMap<>();
    }
}

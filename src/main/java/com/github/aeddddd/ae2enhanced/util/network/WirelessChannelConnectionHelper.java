package com.github.aeddddd.ae2enhanced.util.network;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.parts.automation.PartUpgradeable;
import appeng.tile.misc.TileInterface;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.items.IItemHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 无线频道接收卡的远程网格连接管理器。
 * 此类不是 Mixin，可被任意 Mixin 安全引用，避免类加载器隔离问题。
 */
public class WirelessChannelConnectionHelper {

    private static final Map<IGridNode, IGridConnection> AE2E_REMOTE_CONNECTIONS = new HashMap<>();

    public static void destroyConnection(IAEAppEngInventory parent) {
        IGridNode node = getNodeFromParent(parent);
        if (node == null) return;
        IGridConnection old = AE2E_REMOTE_CONNECTIONS.remove(node);
        if (old != null) {
            try {
                old.destroy();
                AE2Enhanced.LOGGER.warn("[AE2E] Destroyed wireless grid connection for {}",
                        parent.getClass().getSimpleName());
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to destroy wireless grid connection", e);
            }
        }
    }

    public static void tryConnect(IAEAppEngInventory parent) {
        if (!Platform.isServer()) return;

        IGridNode node = getNodeFromParent(parent);
        if (node == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] tryConnect: node is null for {}",
                    parent.getClass().getSimpleName());
            return;
        }

        // If already connected, skip
        if (AE2E_REMOTE_CONNECTIONS.containsKey(node)) {
            AE2Enhanced.LOGGER.debug("[AE2E] tryConnect: already connected for {}",
                    parent.getClass().getSimpleName());
            return;
        }

        IItemHandler upgrades = getUpgradesFromParent(parent);
        if (upgrades == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] tryConnect: upgrades inventory is null for {}",
                    parent.getClass().getSimpleName());
            return;
        }

        TileEntity tile = getTileFromParent(parent);
        if (tile == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] tryConnect: tile is null for {}",
                    parent.getClass().getSimpleName());
            return;
        }

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof ItemChannelReceiverCard)) continue;
            if (!ItemChannelReceiverCard.isBound(stack)) continue;

            BlockPos pos = ItemChannelReceiverCard.getTransmitterPos(stack);
            int dim = ItemChannelReceiverCard.getTransmitterDim(stack);
            if (pos == null) continue;

            if (!AE2EnhancedConfig.wirelessChannel.crossDimension) {
                try {
                    int localDim = tile.getWorld().provider.getDimension();
                    if (dim != localDim) continue;
                } catch (Exception ignored) {
                    continue;
                }
            }

            // Range check
            if (AE2EnhancedConfig.wirelessChannel.maxRange > 0) {
                try {
                    BlockPos localPos = tile.getPos();
                    if (localPos.getDistance(pos.getX(), pos.getY(), pos.getZ()) > AE2EnhancedConfig.wirelessChannel.maxRange) {
                        continue;
                    }
                } catch (Exception ignored) {
                }
            }

            IGridNode transmitterNode = findTransmitterNode(pos, dim);
            if (transmitterNode == null) continue;

            // Prevent self-connection
            if (transmitterNode == node) continue;

            try {
                IGridConnection conn = AEApi.instance().grid().createGridConnection(node, transmitterNode);
                AE2E_REMOTE_CONNECTIONS.put(node, conn);
                AE2Enhanced.LOGGER.warn("[AE2E] Created wireless grid connection for {} -> transmitter at {}",
                        parent.getClass().getSimpleName(), pos);

                // AE2 的 createGridConnection 在 addConnection 之前调用 repath()，
                // 导致路径系统看不到这条新连接，从而无法分配频道。
                // 手动再触发一次 repath() 以修正路径计算。
                IGrid grid = node.getGrid();
                if (grid != null) {
                    IPathingGrid pathing = grid.getCache(IPathingGrid.class);
                    if (pathing != null) {
                        pathing.repath();
                        AE2Enhanced.LOGGER.warn("[AE2E] Triggered manual repath() for wireless connection");
                    }
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to create wireless channel connection", e);
            }
            break;
        }
    }

    private static IGridNode getNodeFromParent(IAEAppEngInventory parent) {
        if (parent instanceof PartUpgradeable) {
            return ((PartUpgradeable) parent).getProxy().getNode();
        }
        // DualityInterface 以及其它持有 gridProxy 字段的内部类
        try {
            java.lang.reflect.Field f = parent.getClass().getDeclaredField("gridProxy");
            f.setAccessible(true);
            appeng.me.helpers.AENetworkProxy proxy = (appeng.me.helpers.AENetworkProxy) f.get(parent);
            return proxy != null ? proxy.getNode() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static IItemHandler getUpgradesFromParent(IAEAppEngInventory parent) {
        if (parent instanceof appeng.api.implementations.tiles.ISegmentedInventory) {
            return ((appeng.api.implementations.tiles.ISegmentedInventory) parent).getInventoryByName("upgrades");
        }
        return null;
    }

    private static TileEntity getTileFromParent(IAEAppEngInventory parent) {
        if (parent instanceof PartUpgradeable) {
            return ((PartUpgradeable) parent).getTile();
        }
        if (parent instanceof appeng.helpers.DualityInterface) {
            return ((appeng.helpers.DualityInterface) parent).getTile();
        }
        return null;
    }

    private static IGridNode findTransmitterNode(BlockPos pos, int dim) {
        World world = DimensionManager.getWorld(dim);
        if (world == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] findTransmitterNode: world is null for dim={}", dim);
            return null;
        }
        if (!world.isBlockLoaded(pos)) {
            AE2Enhanced.LOGGER.debug("[AE2E] findTransmitterNode: block at {} not loaded", pos);
            return null;
        }

        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] findTransmitterNode: no TileEntity at {}", pos);
            return null;
        }
        if (!(te instanceof TileWirelessChannelTransmitter)) {
            AE2Enhanced.LOGGER.debug("[AE2E] findTransmitterNode: TileEntity at {} is {}, expected TileWirelessChannelTransmitter",
                    pos, te.getClass().getName());
            // 向后兼容：旧版卡片可能绑定到 IPartHost（线缆）上的 Part
            if (te instanceof IPartHost) {
                IPartHost host = (IPartHost) te;
                for (appeng.api.util.AEPartLocation side : appeng.api.util.AEPartLocation.SIDE_LOCATIONS) {
                    IPart part = host.getPart(side);
                    if (part != null) {
                        try {
                            return part.getExternalFacingNode();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            return null;
        }

        TileWirelessChannelTransmitter tile = (TileWirelessChannelTransmitter) te;
        try {
            IGridNode node = tile.getGridNode(appeng.api.util.AEPartLocation.INTERNAL);
            if (node == null) {
                AE2Enhanced.LOGGER.debug("[AE2E] findTransmitterNode: tile.getGridNode() returned null at {}", pos);
            }
            return node;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] findTransmitterNode: getGridNode threw exception at {}", pos, e);
            return null;
        }
    }
}

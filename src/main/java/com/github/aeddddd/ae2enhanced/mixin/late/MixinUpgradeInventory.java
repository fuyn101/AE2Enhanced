package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.parts.automation.PartUpgradeable;
import appeng.parts.automation.UpgradeInventory;
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
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * F1b：在任意 {@link UpgradeInventory} 内容变化时，如果其 parent 是 {@link PartUpgradeable}，
 * 且其中包含绑定的频道接收卡，则自动创建/销毁到对应无线频道发生器的远程 {@link IGridConnection}。
 *
 * <p>直接注入 {@link UpgradeInventory#onChangeInventory} 的 TAIL，而不是
 * {@link PartUpgradeable#onChangeInventory}，因为后者在大量子类中被覆盖且不调用 super，
 * 导致注入 {@code PartUpgradeable.onChangeInventory} 的 Mixin 实际上永远不会执行。</p>
 */
@Mixin(value = UpgradeInventory.class, remap = false)
public class MixinUpgradeInventory {

    private static final Map<IGridNode, IGridConnection> AE2E_REMOTE_CONNECTIONS = new HashMap<>();

    @Shadow
    @Final
    private IAEAppEngInventory parent;

    @Inject(method = "onChangeInventory", at = @At("TAIL"), remap = false)
    private void ae2e$onUpgradeInventoryChanged(IItemHandler inv, int slot, appeng.util.inv.InvOperation mc,
                                                 ItemStack removedStack, ItemStack newStack, CallbackInfo ci) {
        // 只在服务端执行，避免客户端预测性更新产生干扰日志
        if (!appeng.util.Platform.isServer()) {
            return;
        }

        AE2Enhanced.LOGGER.warn("[AE2E] DEBUG MixinUpgradeInventory triggered, parent={}, parentClass={}",
                this.parent, this.parent != null ? this.parent.getClass().getSimpleName() : "null");

        if (!(this.parent instanceof PartUpgradeable)) {
            return;
        }

        PartUpgradeable self = (PartUpgradeable) this.parent;

        IGridNode node;
        try {
            node = self.getProxy().getNode();
        } catch (NullPointerException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] DEBUG getProxy().getNode() NPE");
            return;
        }
        if (node == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] DEBUG node is null");
            return;
        }

        AE2Enhanced.LOGGER.warn("[AE2E] DEBUG node={}, destroying old connection", node);

        // Destroy existing remote connection
        IGridConnection old = AE2E_REMOTE_CONNECTIONS.remove(node);
        if (old != null) {
            old.destroy();
            AE2Enhanced.LOGGER.warn("[AE2E] DEBUG destroyed old connection");
        }

        // Search upgrade inventory for channel receiver card
        IItemHandler upgrades = self.getInventoryByName("upgrades");
        if (upgrades == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] DEBUG upgrades inventory is null");
            return;
        }

        AE2Enhanced.LOGGER.warn("[AE2E] DEBUG searching {} upgrade slots", upgrades.getSlots());

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof ItemChannelReceiverCard)) continue;
            if (!ItemChannelReceiverCard.isBound(stack)) {
                AE2Enhanced.LOGGER.warn("[AE2E] DEBUG card in slot {} is not bound", i);
                continue;
            }

            BlockPos pos = ItemChannelReceiverCard.getTransmitterPos(stack);
            int dim = ItemChannelReceiverCard.getTransmitterDim(stack);
            if (pos == null) continue;

            AE2Enhanced.LOGGER.warn("[AE2E] DEBUG found bound card -> pos={}, dim={}", pos, dim);

            if (!AE2EnhancedConfig.wirelessChannel.crossDimension) {
                try {
                    int localDim = self.getTile().getWorld().provider.getDimension();
                    if (dim != localDim) {
                        AE2Enhanced.LOGGER.warn("[AE2E] DEBUG cross-dimension denied");
                        continue;
                    }
                } catch (Exception ignored) {
                    continue;
                }
            }

            // Range check
            if (AE2EnhancedConfig.wirelessChannel.maxRange > 0) {
                try {
                    BlockPos localPos = self.getTile().getPos();
                    if (localPos.getDistance(pos.getX(), pos.getY(), pos.getZ()) > AE2EnhancedConfig.wirelessChannel.maxRange) {
                        AE2Enhanced.LOGGER.warn("[AE2E] DEBUG out of range");
                        continue;
                    }
                } catch (Exception ignored) {
                }
            }

            IGridNode transmitterNode = findTransmitterNode(pos, dim);
            if (transmitterNode == null) {
                AE2Enhanced.LOGGER.warn("[AE2E] DEBUG transmitter node not found");
                continue;
            }

            // Prevent self-connection
            if (transmitterNode == node) {
                AE2Enhanced.LOGGER.warn("[AE2E] DEBUG self-connection prevented");
                continue;
            }

            try {
                IGridConnection conn = AEApi.instance().grid().createGridConnection(node, transmitterNode);
                AE2E_REMOTE_CONNECTIONS.put(node, conn);
                AE2Enhanced.LOGGER.warn("[AE2E] Created wireless grid connection for {} -> transmitter at {}",
                        self.getClass().getSimpleName(), pos);

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

    private static IGridNode findTransmitterNode(BlockPos pos, int dim) {
        World world = DimensionManager.getWorld(dim);
        if (world == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] DEBUG findTransmitterNode: world is null for dim={}", dim);
            return null;
        }
        if (!world.isBlockLoaded(pos)) {
            AE2Enhanced.LOGGER.warn("[AE2E] DEBUG findTransmitterNode: block at {} not loaded", pos);
            return null;
        }

        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] DEBUG findTransmitterNode: no TileEntity at {}", pos);
            return null;
        }
        if (!(te instanceof TileWirelessChannelTransmitter)) {
            AE2Enhanced.LOGGER.warn("[AE2E] DEBUG findTransmitterNode: TileEntity at {} is {}, expected TileWirelessChannelTransmitter",
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
                AE2Enhanced.LOGGER.warn("[AE2E] DEBUG findTransmitterNode: tile.getGridNode() returned null at {}", pos);
            }
            return node;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] DEBUG findTransmitterNode: getGridNode threw exception at {}", pos, e);
            return null;
        }
    }
}

package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.api.AEApi;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.me.GridAccessException;
import appeng.parts.automation.PartUpgradeable;
import appeng.parts.automation.UpgradeInventory;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * F1b：在任意 {@link PartUpgradeable} 的 Upgrade 槽中检测到频道接收卡时，
 * 自动创建/销毁到对应无线频道发生器的远程 {@link IGridConnection}。
 */
@Mixin(value = PartUpgradeable.class, remap = false)
public abstract class MixinPartUpgradeable {

    private static final Map<IGridNode, IGridConnection> AE2E_REMOTE_CONNECTIONS = new HashMap<>();

    @Shadow
    private UpgradeInventory upgrades;

    /**
     * 注入 {@link PartUpgradeable#onChangeInventory} 的 TAIL，而不是
     * {@code upgradesChanged()}。因为大量 AE2 Part 子类（ImportBus、ExportBus、
     * FormationPlane 等）覆盖了 {@code upgradesChanged()} 且不调用
     * {@code super.upgradesChanged()}，导致注入 {@code upgradesChanged} 的 Mixin
     * 实际上永远不会执行。
     */
    @Inject(method = "onChangeInventory", at = @At("TAIL"), remap = false)
    private void ae2e$onUpgradesChanged(IItemHandler inv, int slot, appeng.util.inv.InvOperation mc,
                                         ItemStack removedStack, ItemStack newStack, CallbackInfo ci) {
        // 只处理升级槽的变化
        if (inv != this.upgrades) {
            return;
        }

        PartUpgradeable self = (PartUpgradeable) (Object) this;

        IGridNode node;
        try {
            node = self.getProxy().getNode();
        } catch (NullPointerException e) {
            return;
        }
        if (node == null) return;

        // Destroy existing remote connection
        IGridConnection old = AE2E_REMOTE_CONNECTIONS.remove(node);
        if (old != null) {
            old.destroy();
        }

        // Search upgrade inventory for channel receiver card
        IItemHandler upgrades = self.getInventoryByName("upgrades");
        if (upgrades == null) return;

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
                    int localDim = self.getTile().getWorld().provider.getDimension();
                    if (dim != localDim) continue;
                } catch (Exception ignored) {
                    continue;
                }
            }

            // Range check
            if (AE2EnhancedConfig.wirelessChannel.maxRange > 0) {
                try {
                    BlockPos localPos = self.getTile().getPos();
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
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to create wireless channel connection", e);
            }
            break;
        }
    }

    private static IGridNode findTransmitterNode(BlockPos pos, int dim) {
        World world = DimensionManager.getWorld(dim);
        if (world == null) return null;
        if (!world.isBlockLoaded(pos)) return null;

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileWirelessChannelTransmitter)) {
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
            return tile.getGridNode(appeng.api.util.AEPartLocation.INTERNAL);
        } catch (Exception e) {
            return null;
        }
    }
}

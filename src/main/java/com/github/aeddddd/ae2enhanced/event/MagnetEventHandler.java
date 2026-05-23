package com.github.aeddddd.ae2enhanced.event;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.storage.MEMonitorIInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.IMEAdaptor;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemOmniUpgradeCard;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminal;
import com.github.aeddddd.ae2enhanced.storage.OmniTerminalData;
import com.github.aeddddd.ae2enhanced.storage.OmniTerminalInventory;
import com.github.aeddddd.ae2enhanced.storage.OmniTerminalStorage;
import com.github.aeddddd.ae2enhanced.util.OmniTerminalFinder;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;
import java.util.UUID;

/**
 * 磁引卡 tick 处理器：每 5 tick 扫描玩家周围掉落物并根据模式吸收。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public class MagnetEventHandler {

    private static final int SCAN_INTERVAL = 5;
    private static final double RANGE = 16.0;
    private static final double ATTRACT_SPEED = 0.12;
    private static final double PICKUP_DISTANCE = 1.5;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        if (event.player.ticksExisted % SCAN_INTERVAL != 0) {
            return;
        }

        EntityPlayer player = event.player;
        World world = player.world;

        ItemStack terminal = OmniTerminalFinder.findOmniTerminal(player);
        if (terminal.isEmpty()) {
            return;
        }

        UUID storageId = ItemOmniWirelessTerminal.getStorageId(terminal);
        OmniTerminalStorage storage = OmniTerminalData.get(world).getOrCreate(storageId);
        OmniTerminalInventory upgrades = storage.getUpgradeInventory();

        int magnetMode = -1;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack card = upgrades.getStackInSlot(i);
            if (card.getItem() instanceof ItemOmniUpgradeCard && card.getMetadata() == ItemOmniUpgradeCard.META_MAGNET) {
                magnetMode = ItemOmniUpgradeCard.getMagnetMode(card);
                break;
            }
        }

        if (magnetMode <= 0) {
            return;
        }

        // 模式 2 (网络) 需要检查无线链接和能量
        WirelessTerminalGuiObject host = null;
        IStorageGrid storageGrid = null;
        if (magnetMode == 2) {
            String key = ((ItemOmniWirelessTerminal) terminal.getItem()).getEncryptionKey(terminal);
            if (key == null || key.isEmpty()) {
                return;
            }
            if (!((ItemOmniWirelessTerminal) terminal.getItem()).hasPower(player, 0.5, terminal)) {
                return;
            }
            appeng.api.features.IWirelessTermHandler handler =
                    AEApi.instance().registries().wireless().getWirelessTerminalHandler(terminal);
            if (handler == null) {
                return;
            }
            host = new WirelessTerminalGuiObject(
                    handler, terminal, player, world,
                    (int) player.posX, (int) player.posY, (int) player.posZ
            );
            IGridNode node = host.getActionableNode();
            if (node == null || node.getGrid() == null) {
                return;
            }
            storageGrid = node.getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) {
                return;
            }
        }

        AxisAlignedBB area = new AxisAlignedBB(
                player.posX - RANGE, player.posY - RANGE, player.posZ - RANGE,
                player.posX + RANGE, player.posY + RANGE, player.posZ + RANGE
        );
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, area);

        for (EntityItem entityItem : items) {
            if (entityItem.isDead) {
                continue;
            }
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            double dx = player.posX - entityItem.posX;
            double dy = (player.posY + player.getEyeHeight()) - entityItem.posY;
            double dz = player.posZ - entityItem.posZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist > PICKUP_DISTANCE) {
                entityItem.motionX += dx / dist * ATTRACT_SPEED;
                entityItem.motionY += dy / dist * ATTRACT_SPEED;
                entityItem.motionZ += dz / dist * ATTRACT_SPEED;
                entityItem.velocityChanged = true;
            } else {
                // 吸收
                if (magnetMode == 1) {
                    // 吸入背包
                    InventoryAdaptor adp = InventoryAdaptor.getAdaptor(player);
                    ItemStack leftover = adp.addItems(stack);
                    if (leftover.isEmpty()) {
                        entityItem.setDead();
                    } else {
                        entityItem.setItem(leftover);
                    }
                } else if (magnetMode == 2 && host != null && storageGrid != null) {
                    // 吸入 AE 网络
                    IAEItemStack toInsert = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)
                            .createStack(stack);
                    if (toInsert != null) {
                        IAEItemStack leftover = Platform.poweredInsert(
                                host,
                                storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)),
                                toInsert,
                                new appeng.me.helpers.MachineSource(host)
                        );
                        if (leftover == null || leftover.getStackSize() == 0) {
                            entityItem.setDead();
                        } else {
                            entityItem.setItem(leftover.createItemStack());
                        }
                    }
                }
            }
        }
    }
}

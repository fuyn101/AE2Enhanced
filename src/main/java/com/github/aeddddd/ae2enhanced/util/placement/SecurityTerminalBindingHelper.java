package com.github.aeddddd.ae2enhanced.util.placement;

import ae2.api.features.Locatables;
import ae2.api.networking.IGrid;
import ae2.api.networking.IGridNode;
import ae2.api.networking.security.IActionHost;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.storage.MEStorage;
import ae2.core.localization.PlayerMessages;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * 安全终端绑定辅助类。
 * 为独立放置工具和先进 ME 工具提供统一的 AE2 安全终端绑定、网络获取逻辑。
 *
 * AE2S 中不存在独立的安全终端方块，因此保留加密钥 NBT 存储，并通过 Locatables
 * 存根化地解析绑定的 IActionHost。实际绑定需要外部机制写入 encryptionKey。
 */
public final class SecurityTerminalBindingHelper {

    private static final String NBT_ENCRYPTION_KEY = "encryptionKey";

    private SecurityTerminalBindingHelper() {}

    public static boolean isLinked(ItemStack stack) {
        return !getEncryptionKey(stack).isEmpty();
    }

    public static String getEncryptionKey(ItemStack stack) {
        if (!stack.hasTagCompound()) return "";
        return stack.getTagCompound().getString(NBT_ENCRYPTION_KEY);
    }

    public static void setEncryptionKey(ItemStack stack, String key) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        }
        stack.getTagCompound().setString(NBT_ENCRYPTION_KEY, key);
    }

    public static void clearEncryptionKey(ItemStack stack) {
        if (stack.hasTagCompound()) {
            stack.getTagCompound().removeTag(NBT_ENCRYPTION_KEY);
        }
    }

    /**
     * 通过加密密钥获取绑定的 ME 网络。
     *
     * @param stack  工具物品
     * @param world  当前世界
     * @param player 可选玩家，用于发送提示信息
     * @return 网格，未绑定或不可用时返回 null
     */
    @Nullable
    public static IGrid getLinkedGrid(ItemStack stack, World world, @Nullable EntityPlayer player) {
        String key = getEncryptionKey(stack);
        if (key.isEmpty()) {
            if (player != null) {
                player.sendMessage(PlayerMessages.DeviceNotLinked.text());
            }
            return null;
        }

        long encKey;
        try {
            encKey = Long.parseLong(key);
        } catch (NumberFormatException e) {
            if (player != null) {
                player.sendMessage(PlayerMessages.DeviceNotLinked.text());
            }
            return null;
        }

        if (!world.isRemote) {
            IActionHost host = Locatables.quantumNetworkBridges().get(world, encKey);
            if (host != null) {
                IGridNode node = host.getActionableNode();
                if (node != null && node.grid() != null) {
                    return node.grid();
                }
            }
        }

        if (player != null) {
            player.sendMessage(PlayerMessages.LinkedNetworkNotFound.text());
        }
        return null;
    }

    /**
     * 获取网络的物品存储监控器。
     */
    @Nullable
    public static MEStorage getItemMonitor(IGrid grid) {
        if (grid == null) return null;
        IStorageService storageService = grid.getStorageService();
        if (storageService == null) return null;
        return storageService.getInventory();
    }

    /**
     * 创建以玩家为来源的 IActionSource。
     */
    public static IActionSource createPlayerSource(final EntityPlayer player) {
        return IActionSource.ofPlayer(player);
    }

    /**
     * 尝试让物品实现旧版 INetworkEncodable 时委托使用的工具方法（AE2S 中该接口已移除）。
     */
    public static String getEncryptionKeyForEncodable(ItemStack item) {
        return getEncryptionKey(item);
    }

    public static void setEncryptionKeyForEncodable(ItemStack item, String encKey, String name) {
        setEncryptionKey(item, encKey);
    }
}

package com.github.aeddddd.ae2enhanced.network;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminal;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端按键打开 Omni Terminal 的请求包。
 * 服务端收到后，在玩家物品栏和 Baubles 饰品槽中查找 Omni Terminal 并打开 GUI。
 */
public class PacketOpenOmniTerminal implements IMessage {

    public PacketOpenOmniTerminal() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketOpenOmniTerminal, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenOmniTerminal message, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                ItemStack target = findOmniTerminal(player);
                if (target.isEmpty()) {
                    return;
                }
                int slot = findSlotIndex(player, target);
                boolean isBauble = isInBaublesSlot(player, target);
                player.openGui(AE2Enhanced.instance, GuiHandler.GUI_OMNI_TERMINAL, player.world, slot, isBauble ? 1 : 0, 0);
            });
            return null;
        }

        private static ItemStack findOmniTerminal(EntityPlayer player) {
            // 1. 检查主手
            ItemStack main = player.getHeldItemMainhand();
            if (main.getItem() instanceof ItemOmniWirelessTerminal) {
                return main;
            }
            // 2. 检查副手
            ItemStack off = player.getHeldItemOffhand();
            if (off.getItem() instanceof ItemOmniWirelessTerminal) {
                return off;
            }
            // 3. 检查物品栏
            for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (stack.getItem() instanceof ItemOmniWirelessTerminal) {
                    return stack;
                }
            }
            // 4. 检查 Baubles 饰品槽（如果 Baubles 存在）
            return findInBaubles(player);
        }

        private static int findSlotIndex(EntityPlayer player, ItemStack target) {
            // 主手
            if (player.getHeldItemMainhand() == target) {
                return player.inventory.currentItem;
            }
            // 副手
            if (player.getHeldItemOffhand() == target) {
                return 40;
            }
            // 物品栏
            for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                if (player.inventory.getStackInSlot(i) == target) {
                    return i;
                }
            }
            // Baubles 槽位
            return findBaubleSlot(player, target);
        }

        private static boolean isInBaublesSlot(EntityPlayer player, ItemStack target) {
            return findBaubleSlot(player, target) >= 0;
        }

        // === Baubles 反射查找（避免硬引用导致 NoClassDefFoundError）===

        private static ItemStack findInBaubles(EntityPlayer player) {
            if (!net.minecraftforge.fml.common.Loader.isModLoaded("baubles")) {
                return ItemStack.EMPTY;
            }
            try {
                Object handler = Class.forName("baubles.api.BaublesApi")
                        .getMethod("getBaublesHandler", EntityPlayer.class)
                        .invoke(null, player);
                int slots = (int) handler.getClass().getMethod("getSlots").invoke(handler);
                for (int i = 0; i < slots; i++) {
                    ItemStack stack = (ItemStack) handler.getClass()
                            .getMethod("getStackInSlot", int.class)
                            .invoke(handler, i);
                    if (!stack.isEmpty() && stack.getItem() instanceof ItemOmniWirelessTerminal) {
                        return stack;
                    }
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to search Baubles for OmniTerminal", e);
            }
            return ItemStack.EMPTY;
        }

        private static int findBaubleSlot(EntityPlayer player, ItemStack target) {
            if (!net.minecraftforge.fml.common.Loader.isModLoaded("baubles")) {
                return -1;
            }
            try {
                Object handler = Class.forName("baubles.api.BaublesApi")
                        .getMethod("getBaublesHandler", EntityPlayer.class)
                        .invoke(null, player);
                int slots = (int) handler.getClass().getMethod("getSlots").invoke(handler);
                for (int i = 0; i < slots; i++) {
                    ItemStack stack = (ItemStack) handler.getClass()
                            .getMethod("getStackInSlot", int.class)
                            .invoke(handler, i);
                    if (stack == target) {
                        return i;
                    }
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to find Bauble slot for OmniTerminal", e);
            }
            return -1;
        }
    }
}

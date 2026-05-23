package com.github.aeddddd.ae2enhanced.network;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 → 客户端：同步当前正在 Crafting CPU 中合成的物品列表
 */
public class PacketOmniCraftingUpdate implements IMessage {

    private List<IAEItemStack> activeCrafting = new ArrayList<>();

    public PacketOmniCraftingUpdate() {
    }

    public PacketOmniCraftingUpdate(List<IAEItemStack> activeCrafting) {
        this.activeCrafting = activeCrafting != null ? activeCrafting : new ArrayList<>();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        this.activeCrafting = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            try {
                IAEItemStack stack = AEItemStack.fromPacket(buf);
                if (stack != null) {
                    this.activeCrafting.add(stack);
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to read crafting update packet", e);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.activeCrafting.size());
        for (IAEItemStack stack : this.activeCrafting) {
            try {
                stack.writeToPacket(buf);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to write crafting update packet", e);
            }
        }
    }

    public static class Handler implements IMessageHandler<PacketOmniCraftingUpdate, IMessage> {
        @Override
        public IMessage onMessage(PacketOmniCraftingUpdate message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (Minecraft.getMinecraft().currentScreen instanceof GuiContainer) {
                    GuiContainer gui = (GuiContainer) Minecraft.getMinecraft().currentScreen;
                    if (gui.inventorySlots instanceof com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) {
                        // GuiOmniTerm 会在 drawScreen 中自行从 container 读取
                        // 这里直接更新 container 的字段，由 Gui 读取
                        com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm container =
                                (com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) gui.inventorySlots;
                        container.setClientActiveCrafting(message.activeCrafting);
                    }
                }
            });
            return null;
        }
    }
}

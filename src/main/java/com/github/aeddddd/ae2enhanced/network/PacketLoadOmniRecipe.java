package com.github.aeddddd.ae2enhanced.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Omni Terminal JEI 配方转移网络包
 */
public class PacketLoadOmniRecipe implements IMessage {

    private NBTTagCompound data;

    public PacketLoadOmniRecipe() {
    }

    public PacketLoadOmniRecipe(byte mode, boolean isCrafting, List<ItemStack> inputs, List<ItemStack> outputs) {
        this.data = new NBTTagCompound();
        this.data.setByte("mode", mode);
        this.data.setBoolean("isCrafting", isCrafting);
        NBTTagList inList = new NBTTagList();
        for (ItemStack stack : inputs) {
            if (stack != null && !stack.isEmpty()) {
                inList.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        this.data.setTag("inputs", inList);
        NBTTagList outList = new NBTTagList();
        for (ItemStack stack : outputs) {
            if (stack != null && !stack.isEmpty()) {
                outList.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        this.data.setTag("outputs", outList);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.data);
    }

    public NBTTagCompound getData() {
        return data;
    }

    public static class Handler implements IMessageHandler<PacketLoadOmniRecipe, IMessage> {

        @Override
        public IMessage onMessage(PacketLoadOmniRecipe message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                if (!(ctx.getServerHandler().player.openContainer instanceof com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm)) {
                    return;
                }
                com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm c =
                        (com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) ctx.getServerHandler().player.openContainer;
                net.minecraft.nbt.NBTTagCompound data = message.getData();
                if (data == null) return;
                byte mode = data.getByte("mode");
                boolean isCrafting = data.getBoolean("isCrafting");
                net.minecraft.nbt.NBTTagList inList = data.getTagList("inputs", 10);
                net.minecraft.nbt.NBTTagList outList = data.getTagList("outputs", 10);
                java.util.List<net.minecraft.item.ItemStack> inputs = new java.util.ArrayList<>();
                java.util.List<net.minecraft.item.ItemStack> outputs = new java.util.ArrayList<>();
                for (int i = 0; i < inList.tagCount(); i++) {
                    inputs.add(new net.minecraft.item.ItemStack(inList.getCompoundTagAt(i)));
                }
                for (int i = 0; i < outList.tagCount(); i++) {
                    outputs.add(new net.minecraft.item.ItemStack(outList.getCompoundTagAt(i)));
                }
                c.loadPattern(mode, isCrafting, inputs, outputs);
            });
            return null;
        }
    }
}

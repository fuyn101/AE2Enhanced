package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * E2a：直接容器提取/注入网络包。
 *
 * <p>AE2S 迁移期间：AE2-UEL 的 ContainerMEStorage / 存储通道 / IStorageService 泛型 API
 * 已不存在，且当前没有任何代码发送本包。因此服务端处理器暂时为空实现，
 * 保留包体与注册入口以避免网络注册处编译/运行时缺失。</p>
 */
public class PacketMEMonitorableAction implements IMessage {

    public static final byte FLUID_WORK = 0;
    public static final byte GAS_WORK = 1;
    public static final byte FLUID_OPERATE = 2;
    public static final byte GAS_OPERATE = 3;

    private byte type;
    private net.minecraft.nbt.NBTTagCompound nbt;

    public PacketMEMonitorableAction() {
    }

    public PacketMEMonitorableAction(byte type, net.minecraft.nbt.NBTTagCompound nbt) {
        this.type = type;
        this.nbt = nbt;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.type = buf.readByte();
        this.nbt = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.type);
        ByteBufUtils.writeTag(buf, this.nbt);
    }

    public byte getType() {
        return type;
    }

    public net.minecraft.nbt.NBTTagCompound getNbt() {
        return nbt;
    }

    public static class Handler implements IMessageHandler<PacketMEMonitorableAction, IMessage> {

        @Override
        public IMessage onMessage(PacketMEMonitorableAction message, MessageContext ctx) {
            // TODO: optional migration dependency — AE2S terminal already handles fluid/gas interactions.
            // Re-implement here only if a custom client-side handler starts sending this packet again.
            return null;
        }
    }
}

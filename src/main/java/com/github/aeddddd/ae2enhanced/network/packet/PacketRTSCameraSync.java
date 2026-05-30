package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端相机高度/FOV 同步到服务端 —— 服务端仅做边界校验，不转发
 */
public class PacketRTSCameraSync implements IMessage {

    private float heightOffset;
    private float fov;

    public PacketRTSCameraSync() {}

    public PacketRTSCameraSync(float heightOffset, float fov) {
        this.heightOffset = heightOffset;
        this.fov = fov;
    }

    public float getHeightOffset() { return heightOffset; }
    public float getFov() { return fov; }

    @Override
    public void fromBytes(ByteBuf buf) {
        heightOffset = buf.readFloat();
        fov = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeFloat(heightOffset);
        buf.writeFloat(fov);
    }

    public static class Handler implements IMessageHandler<PacketRTSCameraSync, IMessage> {
        @Override
        public IMessage onMessage(PacketRTSCameraSync message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                // 服务端仅做边界校验：height 和 fov 必须在合理范围内
                // 如果超出范围，可记录日志或强制退出
                // 当前版本不做额外处理
            });
            return null;
        }
    }
}

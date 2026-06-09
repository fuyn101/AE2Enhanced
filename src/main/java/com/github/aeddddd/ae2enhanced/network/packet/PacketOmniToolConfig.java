package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 客户端发送先进ME工具配置更新到服务端.
 */
public class PacketOmniToolConfig implements IMessage {

    private int mode;
    private int dropMode;
    private boolean silkTouch;
    private int fortune;
    private double blinkDistance;
    private int breakCooldown;
    private int paramEnabled;

    public PacketOmniToolConfig() {
    }

    public PacketOmniToolConfig(int mode, int dropMode, boolean silkTouch,
                                 int fortune, double blinkDistance, int breakCooldown,
                                 int paramEnabled) {
        this.mode = mode;
        this.dropMode = dropMode;
        this.silkTouch = silkTouch;
        this.fortune = fortune;
        this.blinkDistance = blinkDistance;
        this.breakCooldown = breakCooldown;
        this.paramEnabled = paramEnabled;
    }

    public int getMode() { return mode; }
    public int getDropMode() { return dropMode; }
    public boolean isSilkTouch() { return silkTouch; }
    public int getFortune() { return fortune; }
    public double getBlinkDistance() { return blinkDistance; }
    public int getBreakCooldown() { return breakCooldown; }
    public int getParamEnabled() { return paramEnabled; }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readByte();
        dropMode = buf.readByte();
        silkTouch = buf.readBoolean();
        fortune = buf.readByte();
        blinkDistance = buf.readDouble();
        breakCooldown = buf.readByte();
        paramEnabled = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);
        buf.writeByte(dropMode);
        buf.writeBoolean(silkTouch);
        buf.writeByte(fortune);
        buf.writeDouble(blinkDistance);
        buf.writeByte(breakCooldown);
        buf.writeByte(paramEnabled);
    }
}

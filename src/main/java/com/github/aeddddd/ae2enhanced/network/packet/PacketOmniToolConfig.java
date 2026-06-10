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
    private boolean chaosForceKill;
    private boolean conformalEnabled;
    private boolean advancedSilkTouch;
    private boolean wallPhase;

    public PacketOmniToolConfig() {
    }

    public PacketOmniToolConfig(int mode, int dropMode, boolean silkTouch,
                                 int fortune, double blinkDistance, int breakCooldown,
                                 int paramEnabled, boolean chaosForceKill, boolean conformalEnabled,
                                 boolean advancedSilkTouch, boolean wallPhase) {
        this.mode = mode;
        this.dropMode = dropMode;
        this.silkTouch = silkTouch;
        this.fortune = fortune;
        this.blinkDistance = blinkDistance;
        this.breakCooldown = breakCooldown;
        this.paramEnabled = paramEnabled;
        this.chaosForceKill = chaosForceKill;
        this.conformalEnabled = conformalEnabled;
        this.advancedSilkTouch = advancedSilkTouch;
        this.wallPhase = wallPhase;
    }

    public int getMode() { return mode; }
    public int getDropMode() { return dropMode; }
    public boolean isSilkTouch() { return silkTouch; }
    public int getFortune() { return fortune; }
    public double getBlinkDistance() { return blinkDistance; }
    public int getBreakCooldown() { return breakCooldown; }
    public int getParamEnabled() { return paramEnabled; }
    public boolean isChaosForceKill() { return chaosForceKill; }
    public boolean isConformalEnabled() { return conformalEnabled; }
    public boolean isAdvancedSilkTouch() { return advancedSilkTouch; }
    public boolean isWallPhase() { return wallPhase; }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readByte();
        dropMode = buf.readByte();
        silkTouch = buf.readBoolean();
        fortune = buf.readByte();
        blinkDistance = buf.readDouble();
        breakCooldown = buf.readByte();
        paramEnabled = buf.readShort();
        chaosForceKill = buf.readBoolean();
        conformalEnabled = buf.readBoolean();
        advancedSilkTouch = buf.readBoolean();
        wallPhase = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);
        buf.writeByte(dropMode);
        buf.writeBoolean(silkTouch);
        buf.writeByte(fortune);
        buf.writeDouble(blinkDistance);
        buf.writeByte(breakCooldown);
        buf.writeShort(paramEnabled);
        buf.writeBoolean(chaosForceKill);
        buf.writeBoolean(conformalEnabled);
        buf.writeBoolean(advancedSilkTouch);
        buf.writeBoolean(wallPhase);
    }
}

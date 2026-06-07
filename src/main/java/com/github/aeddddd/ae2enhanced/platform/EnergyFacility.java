package com.github.aeddddd.ae2enhanced.platform;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * 平台范围内检测到的能量设施封装.
 * 缓存 BlockPos 用于可视化、TOP 展示及故障定位；
 * 缓存 blockId 和 adapter 用于多模组能量注入优化.
 */
public class EnergyFacility {

    public enum Type {
        /** 默认模式：从 ME 网络接收能量 */
        RECEIVER,
        /** 特殊标记模式：向 ME 网络输出能量(暂不启用,预留扩展) */
        PROVIDER
    }

    public final BlockPos pos;
    public final IEnergyStorage cap;
    public final String blockId;
    public final IEnergyAdapter adapter;
    public Type type;

    public EnergyFacility(BlockPos pos, IEnergyStorage cap, String blockId, IEnergyAdapter adapter, Type type) {
        this.pos = pos;
        this.cap = cap;
        this.blockId = blockId;
        this.adapter = adapter;
        this.type = type;
    }

    public boolean isReceiver() {
        return type == Type.RECEIVER;
    }

    public boolean isProvider() {
        return type == Type.PROVIDER;
    }

    @Override
    public String toString() {
        return "EnergyFacility{pos=" + pos + ", type=" + type + ", blockId=" + blockId + ", adapter=" + adapter.getClass().getSimpleName() + "}";
    }
}

package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Draconic Evolution 专用能量适配器。
 *
 * <p>DE 的能量水晶/核心本身已使用高 maxTransfer 策略，
 * 通常不需要额外绕过。此适配器未来可用于特殊处理 DE 能量核心的
 * {@code modifyEnergyStored()} 直接注入（如突破 2.1B int 限制）。</p>
 */
public class DEEnergyAdapter extends ForgeEnergyAdapter {

    @Override
    public boolean canHandle(String blockId) {
        return blockId.startsWith("draconicevolution:");
    }
}

package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Thermal Expansion 专用能量适配器（占位，预留扩展）。
 *
 * <p>当前 Thermal Expansion 机器通过标准 Forge {@link IEnergyStorage} 的
 * {@link ForgeEnergyAdapter} 多调用策略通常已可高效供能。
 * 若后续发现 TE 机器存在类似 EIO 的 tick 级限流，可在此实现反射绕过。</p>
 */
public class TEEnergyAdapter extends ForgeEnergyAdapter {

    @Override
    public boolean canHandle(String blockId) {
        return blockId.startsWith("thermalexpansion:") || blockId.startsWith("thermaldynamics:");
    }
}

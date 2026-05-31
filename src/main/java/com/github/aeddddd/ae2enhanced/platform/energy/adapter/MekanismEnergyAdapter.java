package com.github.aeddddd.ae2enhanced.platform.energy.adapter;

import com.github.aeddddd.ae2enhanced.platform.energy.IEnergyAdapter;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * Mekanism 专用能量适配器（占位，预留扩展）。
 *
 * <p>Mekanism 使用自己的 Joules (J) 能量系统，虽然通过 Forge 兼容性
 * 暴露了 {@link IEnergyStorage}，但转换效率可能不是最优。
 * 此适配器未来可实现直接调用 Mekanism 内部能量 API（如 {@code IEnergyCube}）
 * 以减少能量单位转换损耗。</p>
 */
public class MekanismEnergyAdapter extends ForgeEnergyAdapter {

    @Override
    public boolean canHandle(String blockId) {
        return blockId.startsWith("mekanism:");
    }
}

package com.github.aeddddd.ae2enhanced.tile;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * 压缩区块供电节点的 TileEntity.
 *
 * <p>与区块供电节点一致,但供电范围扩展为 3×3 区块(以自身所在区块为中心).</p>
 */
public class TileCompressedChunkPowerNode extends TileChunkPowerNode {

    public TileCompressedChunkPowerNode() {
    }

    /**
     * 扫描 3×3 区块范围内所有可接收能量的 TileEntity,缓存其位置.
     */
    @Override
    protected void refreshTargetCache() {
        cachedTargets.clear();
        int centerChunkX = pos.getX() >> 4;
        int centerChunkZ = pos.getZ() >> 4;

        for (TileEntity te : world.loadedTileEntityList) {
            if (te == null || te.isInvalid()) continue;
            if (te == this) continue;

            BlockPos tp = te.getPos();
            int teChunkX = tp.getX() >> 4;
            int teChunkZ = tp.getZ() >> 4;

            // 检查是否在 3×3 区块范围内
            if (Math.abs(teChunkX - centerChunkX) > 1 || Math.abs(teChunkZ - centerChunkZ) > 1) continue;

            for (EnumFacing facing : EnumFacing.values()) {
                if (te.hasCapability(CapabilityEnergy.ENERGY, facing)) {
                    IEnergyStorage cap = te.getCapability(CapabilityEnergy.ENERGY, facing);
                    if (cap != null && cap.canReceive()) {
                        cachedTargets.add(tp.toImmutable());
                        break;
                    }
                }
            }
        }
    }
}

package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * 超因果计算核心成型状态 GUI 的 Container（纯展示面板，无物品槽）。
 * 用于避免服务端下发玩家背包槽位数据导致客户端物品栏错位。
 */
public class ContainerComputationFormed extends Container {

    private final TileComputationCore tile;

    public ContainerComputationFormed(TileComputationCore tile) {
        this.tile = tile;
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        if (tile == null || tile.isInvalid()) {
            return false;
        }
        return tile.getWorld().getTileEntity(tile.getPos()) == tile
                && playerIn.getDistanceSq(tile.getPos()) <= 64.0;
    }
}

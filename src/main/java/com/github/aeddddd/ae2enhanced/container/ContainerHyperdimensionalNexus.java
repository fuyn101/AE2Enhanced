package com.github.aeddddd.ae2enhanced.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * 超维度仓储中枢信息面板的空 Container.
 * 纯展示 GUI 不需要槽位,但需要服务器端 Container 才能让 Forge 正确发送 OpenGui 包.
 */
public class ContainerHyperdimensionalNexus extends Container {

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return true;
    }
}

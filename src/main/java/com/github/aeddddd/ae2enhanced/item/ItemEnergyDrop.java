package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.ItemStack;

/**
 * RF 能量假物品。
 * 用于在标准 AE2 物品终端中显示 RF 能量存储量（P1 阶段接入终端显示）。
 * P0 阶段仅注册物品和材质。
 */
public class ItemEnergyDrop extends AbstractNbtDrop {

    public ItemEnergyDrop() {
        super("energy_drop");
    }

    /**
     * 判断 ItemStack 是否是能量假物品。
     */
    public static boolean isEnergyDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemEnergyDrop;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return "RF Energy";
    }
}

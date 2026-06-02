package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

/**
 * RF 能量假物品。
 * 用于在标准 AE2 物品终端中显示 RF 能量存储量。
 *
 * 设计：RF 能量无子类型，ItemStack 的 count 固定为 1（作为模板），
 * 实际数量通过 IAEItemStack.stackSize 表示。
 */
public class ItemEnergyDrop extends AbstractNbtDrop {

    public ItemEnergyDrop() {
        super("energy_drop");
    }

    /**
     * 创建 RF 假物品模板（count = 1）。
     * 实际数量通过 AEItemStack.setStackSize() 设置。
     */
    public static ItemStack createStack() {
        return new ItemStack(ItemRegistry.ENERGY_DROP, 1);
    }

    /**
     * 判断 ItemStack 是否是能量假物品。
     */
    public static boolean isEnergyDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemEnergyDrop;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return I18n.format("item.ae2enhanced.energy_drop.name");
    }
}

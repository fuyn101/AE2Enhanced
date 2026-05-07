package com.github.aeddddd.ae2enhanced.util;

import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import net.minecraft.item.ItemStack;

/**
 * AE2Enhanced 假物品注册表。
 * 独立于 ae2fc 的 FakeItemRegister，只管理本模组注册的假物品类型。
 */
public class AE2EnhancedFakeItemRegister {

    /**
     * 判断 ItemStack 是否是 AE2Enhanced 注册的假物品。
     */
    public static boolean isOurFakeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return ItemEssentiaDrop.isEssentiaDrop(stack);
    }
}

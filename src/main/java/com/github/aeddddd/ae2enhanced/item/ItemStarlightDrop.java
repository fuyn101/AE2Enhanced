package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import net.minecraft.item.ItemStack;

/**
 * Astral Sorcery Starlight 假物品.
 * 用于在标准 AE2 物品终端中显示 Starlight 存储量.
 *
 * 设计：Starlight 无子类型,ItemStack 的 count 固定为 1(作为模板),
 * 实际数量通过 IAEItemStack.stackSize 表示.
 */
public class ItemStarlightDrop extends AbstractNbtDrop {

    public ItemStarlightDrop() {
        super("starlight_drop");
    }

    /**
     * 创建 Starlight 假物品模板(count = 1).
     * 实际数量通过 AEItemStack.setStackSize() 设置.
     */
    public static ItemStack createStack() {
        return new ItemStack(ItemRegistry.STARLIGHT_DROP, 1);
    }

    /**
     * 判断 ItemStack 是否是 Starlight 假物品.
     */
    public static boolean isStarlightDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemStarlightDrop;
    }
}

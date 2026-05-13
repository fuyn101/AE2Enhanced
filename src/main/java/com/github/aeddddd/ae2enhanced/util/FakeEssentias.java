package com.github.aeddddd.ae2enhanced.util;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import net.minecraft.item.ItemStack;

/**
 * 源质假物品的安全工具类。
 * 不包含任何对 thaumicenergistics 的直接类型引用，避免在 Part 类加载时触发
 * thaumicenergistics 类缺失导致的 NoClassDefFoundError。
 *
 * 涉及 thaumicenergistics 类型的 pack/unpack 逻辑已移至 EssentiaBusHelper。
 */
public class FakeEssentias {

    /**
     * 判断 ItemStack 是否是源质假物品。
     */
    public static boolean isEssentiaFakeItem(ItemStack stack) {
        return ItemEssentiaDrop.isEssentiaDrop(stack);
    }

    /**
     * 反射方法：从源质容器（IEssentiaContainerItem）转换为 ItemEssentiaDrop。
     * 供 Container / GhostIngredientTarget 调用，避免硬引用 Thaumcraft API。
     */
    public static ItemStack tryConvertContainerToFake(ItemStack held) {
        if (held == null || held.isEmpty()) return null;
        try {
            Class<?> containerItemClass = Class.forName("thaumcraft.api.aspects.IEssentiaContainerItem");
            if (!containerItemClass.isInstance(held.getItem())) return null;
            Object containerItem = held.getItem();
            Object aspectList = containerItemClass.getMethod("getAspects", ItemStack.class).invoke(containerItem, held);
            if (aspectList == null) return null;
            Object[] aspects = (Object[]) aspectList.getClass().getMethod("getAspects").invoke(aspectList);
            if (aspects == null || aspects.length == 0) return null;
            Object aspect = aspects[0];
            String aspectTag = (String) aspect.getClass().getMethod("getTag").invoke(aspect);
            return ItemEssentiaDrop.createStack(aspectTag, 1);
        } catch (Exception e) {
            return null;
        }
    }
}

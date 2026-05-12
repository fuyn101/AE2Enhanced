package com.github.aeddddd.ae2enhanced.util;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import net.minecraft.item.ItemStack;
import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

/**
 * 源质假物品的打包/解包工具类。
 * 将 Thaumic Energistics 的 IAEEssentiaStack 与标准 AE2 的 IAEItemStack 互相转换。
 */
public class FakeEssentias {

    /**
     * 将源质栈打包为假物品 IAEItemStack，用于在物品终端中显示。
     */
    public static IAEItemStack packEssentia(IAEEssentiaStack essentiaStack) {
        if (essentiaStack == null || essentiaStack.getAspect() == null) return null;
        String aspectTag = essentiaStack.getAspect().getTag();
        long amount = essentiaStack.getStackSize();
        ItemStack fakeItem = ItemEssentiaDrop.createStack(aspectTag, 1);
        AE2Enhanced.LOGGER.info("[AE2E-PACK] aspect={} fakeItem.damage={}", aspectTag, fakeItem.getItemDamage());
        IAEItemStack result = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(fakeItem);
        if (result != null) {
            result.setStackSize(amount);
            AE2Enhanced.LOGGER.info("[AE2E-PACK] result.damage={} result.stackSize={}", result.createItemStack().getItemDamage(), result.getStackSize());
        }
        return result;
    }

    /**
     * 将假物品 IAEItemStack 还原为源质栈，用于 extract/inject 操作。
     */
    public static IAEEssentiaStack unpackEssentia(IAEItemStack itemStack) {
        if (itemStack == null) return null;
        ItemStack mcStack = itemStack.createItemStack();
        String aspectTag = ItemEssentiaDrop.getAspectTag(mcStack);
        if (aspectTag == null) return null;
        EssentiaStack essStack = new EssentiaStack(aspectTag, 1);
        IAEEssentiaStack result = AEEssentiaStack.fromEssentiaStack(essStack);
        if (result != null) {
            result.setStackSize(itemStack.getStackSize());
        }
        return result;
    }

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

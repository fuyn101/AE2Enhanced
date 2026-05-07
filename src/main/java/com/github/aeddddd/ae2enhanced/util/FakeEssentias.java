package com.github.aeddddd.ae2enhanced.util;

import appeng.api.AEApi;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
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
        IAEItemStack result = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(fakeItem);
        if (result != null) {
            result.setStackSize(amount);
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
}

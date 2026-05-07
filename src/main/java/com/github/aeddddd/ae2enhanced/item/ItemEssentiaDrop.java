package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 源质假物品（Essentia Drop）。
 * 用于在标准 AE2 物品终端中显示源质存储。
 * 每种源质类型通过 NBT 中的 aspectTag 区分。
 */
public class ItemEssentiaDrop extends Item {

    public static final String NBT_ASPECT_TAG = "ae2enhanced:essentia_aspect";

    public ItemEssentiaDrop() {
        setRegistryName(AE2Enhanced.MOD_ID, "essentia_drop");
        setTranslationKey(AE2Enhanced.MOD_ID + ".essentia_drop");
        setCreativeTab(null); // 不在创造模式物品栏显示
    }

    /**
     * 创建指定源质类型的假物品堆叠。
     */
    public static ItemStack createStack(String aspectTag, int amount) {
        ItemStack stack = new ItemStack(ModItems.ESSENTIA_DROP, amount);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(NBT_ASPECT_TAG, aspectTag);
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * 从 ItemStack 中提取源质类型标签。
     */
    public static String getAspectTag(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.getString(NBT_ASPECT_TAG) : null;
    }

    /**
     * 判断 ItemStack 是否是源质假物品。
     */
    public static boolean isEssentiaDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemEssentiaDrop;
    }
}

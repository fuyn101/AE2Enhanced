package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.creativetab.CreativeTabs;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasRegistry;
import mekanism.api.gas.GasStack;

/**
 * 气体假物品（Gas Drop）。
 * 用于在标准 AE2 物品终端中显示 Mekanism 气体存储。
 *
 * 关键设计：使用 NBT 存储气体注册名，不直接依赖 metadata。
 */
public class ItemGasDrop extends Item {

    private static final String GAS_TAG = "GasName";

    public ItemGasDrop() {
        setRegistryName(AE2Enhanced.MOD_ID, "gas_drop");
        setTranslationKey(AE2Enhanced.MOD_ID + ".gas_drop");
        setCreativeTab(null);
    }

    /**
     * 创建指定气体类型的假物品堆叠。
     */
    public static ItemStack createStack(GasStack gas) {
        if (gas == null || gas.getGas() == null || gas.amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(ModItems.GAS_DROP, gas.amount);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(GAS_TAG, gas.getGas().getName());
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * 从 ItemStack 中提取 GasStack。
     */
    public static GasStack getGasStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemGasDrop)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(GAS_TAG, 8)) return null;
        Gas gas = GasRegistry.getGas(tag.getString(GAS_TAG));
        if (gas == null) return null;
        return new GasStack(gas, stack.getCount());
    }

    /**
     * 从 ItemStack 中提取气体名称。
     */
    public static String getGasName(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemGasDrop)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.getString(GAS_TAG) : null;
    }

    /**
     * 判断 ItemStack 是否是气体假物品。
     */
    public static boolean isGasDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemGasDrop;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        GasStack gas = getGasStack(stack);
        return gas != null ? gas.getGas().getLocalizedName() : super.getItemStackDisplayName(stack);
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        // 不返回任何子类型，避免 JEI 索引
    }
}

package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.client.render.GasItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 气体假物品（Gas Drop）。
 * 用于在标准 AE2 物品终端中显示 Mekanism 气体存储。
 *
 * 关键设计：使用 NBT 存储气体名称字符串（不直接引用 Mekanism 类，便于条件加载）。
 */
public class ItemGasDrop extends Item {

    public ItemGasDrop() {
        setRegistryName(AE2Enhanced.MOD_ID, "gas_drop");
        setTranslationKey(AE2Enhanced.MOD_ID + ".gas_drop");
        setCreativeTab(null);
    }

    /**
     * 创建指定气体类型的假物品堆叠。
     */
    public static ItemStack createStack(String gasName, int amount) {
        ItemStack stack = new ItemStack(ModItems.GAS_DROP, 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("GasName", gasName);
        tag.setInteger("Amt", amount);
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * 从 ItemStack 中提取气体名称。
     */
    public static String getGasName(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemGasDrop)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.getString("GasName") : null;
    }

    /**
     * 判断 ItemStack 是否是气体假物品。
     */
    public static boolean isGasDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemGasDrop;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String gasName = getGasName(stack);
        return gasName != null ? gasName : super.getItemStackDisplayName(stack);
    }

    @Override
    public void getSubItems(net.minecraft.creativetab.CreativeTabs tab, net.minecraft.util.NonNullList<ItemStack> items) {
        // 不返回任何子类型，避免 JEI 索引
    }

    /**
     * 客户端初始化：注册自定义 TileEntityItemStackRenderer。
     */
    @SideOnly(Side.CLIENT)
    public void initModel() {
        this.setTileEntityItemStackRenderer(GasItemRenderer.INSTANCE);
    }
}

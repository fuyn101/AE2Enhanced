package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.client.render.FluidItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 流体假物品（Fluid Drop）。
 * 用于在标准 AE2 物品终端中显示流体存储。
 *
 * 关键设计：使用 NBT 存储 FluidStack 信息（FluidName + Amount），
 * 数量由外层 IAEItemStack.getStackSize() 控制。
 */
public class ItemFluidDrop extends Item {

    public ItemFluidDrop() {
        setRegistryName(AE2Enhanced.MOD_ID, "fluid_drop");
        setTranslationKey(AE2Enhanced.MOD_ID + ".fluid_drop");
        setCreativeTab(null);
    }

    /**
     * 创建指定流体类型的假物品堆叠。
     */
    public static ItemStack createStack(FluidStack fluid) {
        ItemStack stack = new ItemStack(ModItems.FLUID_DROP, 1);
        if (fluid != null) {
            NBTTagCompound tag = new NBTTagCompound();
            fluid.writeToNBT(tag);
            stack.setTagCompound(tag);
        }
        return stack;
    }

    /**
     * 从 ItemStack 中提取 FluidStack（数量可能为 0，仅用于获取类型）。
     */
    public static FluidStack getFluidStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemFluidDrop)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? FluidStack.loadFluidStackFromNBT(tag) : null;
    }

    /**
     * 判断 ItemStack 是否是流体假物品。
     */
    public static boolean isFluidDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemFluidDrop;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        FluidStack fluid = getFluidStack(stack);
        return fluid != null ? fluid.getLocalizedName() : super.getItemStackDisplayName(stack);
    }

    /**
     * 为 JEI / 创造模式标签页提供所有子类型。
     * 假物品不需要在创造模式或 JEI 中显示，因此返回空。
     */
    @Override
    public void getSubItems(net.minecraft.creativetab.CreativeTabs tab, net.minecraft.util.NonNullList<ItemStack> items) {
        // 不返回任何子类型，避免 JEI 索引
    }

    /**
     * 客户端初始化：注册自定义 TileEntityItemStackRenderer。
     */
    @SideOnly(Side.CLIENT)
    public void initModel() {
        this.setTileEntityItemStackRenderer(FluidItemRenderer.INSTANCE);
    }
}

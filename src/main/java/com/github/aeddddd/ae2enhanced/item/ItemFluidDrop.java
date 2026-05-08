package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.client.render.FluidItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流体假物品（Fluid Drop）。
 * 用于在标准 AE2 物品终端中显示流体存储。
 *
 * 关键设计：使用 ItemStack 的 metadata（itemDamage）编码流体注册名哈希，
 * 确保 AEItemStackRegistry 的 hashCode 去重不会合并不同流体。
 * 数量由外层 IAEItemStack.getStackSize() 控制。
 */
public class ItemFluidDrop extends Item {

    private static final Map<String, Integer> FLUID_TO_META = new HashMap<>();
    private static final Map<Integer, String> META_TO_FLUID = new HashMap<>();
    private static boolean mapsInitialized = false;

    public ItemFluidDrop() {
        setRegistryName(AE2Enhanced.MOD_ID, "fluid_drop");
        setTranslationKey(AE2Enhanced.MOD_ID + ".fluid_drop");
        setCreativeTab(null);
        setHasSubtypes(true);
    }

    private static synchronized void initFluidMaps() {
        if (mapsInitialized) return;
        mapsInitialized = true;

        List<String> names = new ArrayList<>();
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            if (fluid != null && fluid.getName() != null) {
                names.add(fluid.getName());
            }
        }
        Collections.sort(names);

        int meta = 0;
        for (String name : names) {
            FLUID_TO_META.put(name, meta);
            META_TO_FLUID.put(meta, name);
            meta++;
        }
    }

    public static int getFluidMeta(String fluidName) {
        initFluidMaps();
        return FLUID_TO_META.getOrDefault(fluidName, 0);
    }

    public static String getFluidNameFromMeta(int meta) {
        initFluidMaps();
        return META_TO_FLUID.get(meta);
    }

    public static int getFluidCount() {
        initFluidMaps();
        return FLUID_TO_META.size();
    }

    /**
     * 创建指定流体类型的假物品堆叠。
     */
    public static ItemStack createStack(FluidStack fluid) {
        initFluidMaps();
        if (fluid == null || fluid.getFluid() == null) {
            return new ItemStack(ModItems.FLUID_DROP, 1, 0);
        }
        int meta = getFluidMeta(fluid.getFluid().getName());
        return new ItemStack(ModItems.FLUID_DROP, 1, meta);
    }

    /**
     * 从 ItemStack 中提取 FluidStack（数量可能为 0，仅用于获取类型）。
     */
    public static FluidStack getFluidStack(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemFluidDrop)) return null;
        String fluidName = getFluidNameFromMeta(stack.getItemDamage());
        if (fluidName == null) return null;
        Fluid fluid = FluidRegistry.getFluid(fluidName);
        if (fluid == null) return null;
        return new FluidStack(fluid, 1);
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

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
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

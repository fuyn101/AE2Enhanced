package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 虚拟并行卡：安装到 Central ME Interface 后，所有支持的远程目标切换为虚拟合成模式。
 *
 * <p>通过 NBT {@code Tier} 区分 8 个等级，每个等级对应固定的并行数。
 * 多张卡同时安装时取最高 tier。</p>
 */
public class ItemVirtualParallelCard extends Item {

    public static final String NBT_TIER = "Tier";

    /**
     * 8 个等级对应的并行数：8 / 32 / 128 / 512 / 32768 / 2097152 / 134217728 / Integer.MAX_VALUE
     */
    public static final int[] PARALLEL_VALUES = {
            8,
            32,
            128,
            512,
            32768,
            2097152,
            134217728,
            Integer.MAX_VALUE
    };

    public static final int MAX_TIER = PARALLEL_VALUES.length;

    public ItemVirtualParallelCard() {
        setRegistryName(AE2Enhanced.MOD_ID, "virtual_parallel_card");
        setTranslationKey(AE2Enhanced.MOD_ID + ".virtual_parallel_card");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(64);
    }

    public static int getTier(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) {
            return 0;
        }
        return stack.getTagCompound().getInteger(NBT_TIER);
    }

    public static void setTier(ItemStack stack, int tier) {
        if (stack.isEmpty()) {
            return;
        }
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setInteger(NBT_TIER, Math.max(0, Math.min(tier, MAX_TIER - 1)));
        stack.setTagCompound(tag);
    }

    /**
     * 获取每个 tier 的染色颜色（用于 ItemColors，不影响 NBT）。
     * 颜色从低 tier 的绿色渐变到高 tier 的紫红色。
     */
    public static int getTierColor(int tier) {
        switch (tier) {
            case 0:  return 0xFF55FF55; // 绿
            case 1:  return 0xFF55FFFF; // 青
            case 2:  return 0xFF5555FF; // 蓝
            case 3:  return 0xFFFF55FF; // 紫
            case 4:  return 0xFFFFAA00; // 橙
            case 5:  return 0xFFFF5555; // 红
            case 6:  return 0xFFFF0055; // 深红
            case 7:  return 0xFFAA00FF; // 紫红
            default: return 0xFFFFFFFF;
        }
    }

    public static int getParallel(ItemStack stack) {
        int tier = getTier(stack);
        if (tier < 0 || tier >= PARALLEL_VALUES.length) {
            return 1;
        }
        return PARALLEL_VALUES[tier];
    }

    public static ItemStack createStack(int tier) {
        ItemStack stack = new ItemStack(ItemRegistry.VIRTUAL_PARALLEL_CARD);
        setTier(stack, tier);
        return stack;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return getTranslationKey() + ".tier" + (getTier(stack) + 1);
    }

    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (!isInCreativeTab(tab)) {
            return;
        }
        for (int i = 0; i < MAX_TIER; i++) {
            items.add(createStack(i));
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        int tier = getTier(stack);
        int parallel = getParallel(stack);
        String parallelText = parallel == Integer.MAX_VALUE ? "∞" : String.valueOf(parallel);
        tooltip.add(I18n.format("item.ae2enhanced.virtual_parallel_card.tooltip", tier + 1, parallelText));
        tooltip.add(I18n.format("item.ae2enhanced.virtual_parallel_card.tooltip.detail",
                AE2EnhancedConfig.centralInterface.virtualParallelEnergyCost));
    }
}

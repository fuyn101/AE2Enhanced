package com.github.aeddddd.ae2enhanced.util.fakeitem;

import net.minecraft.item.ItemStack;

/**
 * 气体假物品的安全判断工具类。
 *
 * 本类不 import 任何第三方 mod 类（Mekanism / MekanismEnergistics），
 * 仅使用字符串比较和 NBT 读取，确保在缺少对应 mod 时不会触发 NoClassDefFoundError。
 *
 * 无条件配置中的 Mixin 和 Part 类应使用本类进行气体假物品判断，
 * 避免直接引用 {@link FakeGases}（其常量池包含 Mekanism 类引用）。
 */
public final class GasFakeItemChecks {

    private static final String GAS_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemGasDrop";

    private GasFakeItemChecks() {}

    /**
     * 判断 ItemStack 是否是本 mod 的气体假物品。
     * 使用字符串比较而非直接引用 ItemGasDrop 类。
     */
    public static boolean isGasFakeItemSafe(ItemStack stack) {
        return !stack.isEmpty() && GAS_DROP_CLASS.equals(stack.getItem().getClass().getName());
    }

    /**
     * 安全获取气体假物品的气体注册名。
     * 直接从 NBT 读取，不加载 ItemGasDrop 类。
     */
    public static String tryGetGasName(ItemStack stack) {
        if (!isGasFakeItemSafe(stack)) return null;
        net.minecraft.nbt.NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.getString("GasName") : null;
    }
}

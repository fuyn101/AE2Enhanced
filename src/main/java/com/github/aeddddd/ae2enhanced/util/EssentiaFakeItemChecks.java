package com.github.aeddddd.ae2enhanced.util;

import net.minecraft.item.ItemStack;

/**
 * 源质假物品的安全判断工具类。
 *
 * 本类不 import 任何第三方 mod 类（Thaumcraft / ThaumicEnergistics），
 * 仅使用字符串比较和反射，确保在缺少对应 mod 时不会触发 NoClassDefFoundError。
 *
 * 无条件配置中的 Mixin 和 Part 类应使用本类进行源质假物品判断，
 * 避免直接引用 {@link FakeEssentias}（其常量池包含 ThaumicEnergistics 类引用）。
 */
public final class EssentiaFakeItemChecks {

    private static final String ESSENTIA_DROP_CLASS = "com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop";

    private EssentiaFakeItemChecks() {}

    /**
     * 判断 ItemStack 是否是本 mod 的源质假物品。
     * 使用字符串比较而非直接引用 ItemEssentiaDrop 类。
     */
    public static boolean isEssentiaFakeItem(ItemStack stack) {
        return !stack.isEmpty() && ESSENTIA_DROP_CLASS.equals(stack.getItem().getClass().getName());
    }

    /**
     * 安全获取源质假物品的 aspect 标签。
     * 使用反射调用 ItemEssentiaDrop.getAspectTag，仅在确认是源质假物品后调用。
     */
    public static String tryGetAspectTag(ItemStack stack) {
        if (!isEssentiaFakeItem(stack)) return null;
        try {
            Class<?> clazz = Class.forName(ESSENTIA_DROP_CLASS);
            return (String) clazz.getMethod("getAspectTag", ItemStack.class).invoke(null, stack);
        } catch (Exception e) {
            return null;
        }
    }
}

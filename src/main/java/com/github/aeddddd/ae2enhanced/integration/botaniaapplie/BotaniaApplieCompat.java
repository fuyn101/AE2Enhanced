package com.github.aeddddd.ae2enhanced.integration.botaniaapplie;

import appeng.api.storage.IStorageChannel;

/**
 * Botania_Applie 兼容性检测类.
 * 仅通过反射探测 {@code nyonio.ae2.ManaStorageChannel},避免无条件加载类中出现外部类的硬引用.
 */
public final class BotaniaApplieCompat {

    private static final boolean LOADED;
    private static final boolean HAS_MANA_CHANNEL;
    private static Class<?> manaChannelClass;
    private static Object manaChannelInstance;

    static {
        boolean loaded = false;
        boolean hasChannel = false;
        try {
            manaChannelClass = Class.forName("nyonio.ae2.ManaStorageChannel");
            loaded = true;
            Object instance = manaChannelClass.getField("INSTANCE").get(null);
            if (instance instanceof IStorageChannel) {
                manaChannelInstance = instance;
                hasChannel = true;
            }
        } catch (Throwable ignored) {
            // Botania_Applie 未安装或类结构不符,静默回退
        }
        LOADED = loaded;
        HAS_MANA_CHANNEL = hasChannel;
    }

    private BotaniaApplieCompat() {
    }

    /**
     * 判断 Botania_Applie 相关类是否存在.
     */
    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * 判断 Botania_Applie 的 {@code ManaStorageChannel} 是否可用.
     */
    public static boolean isManaStorageChannelAvailable() {
        return HAS_MANA_CHANNEL;
    }

    /**
     * 获取 Botania_Applie 的 {@code ManaStorageChannel} 单例实例.
     *
     * @return 外部 Mana 存储通道实例,若不可用则返回 null
     */
    @SuppressWarnings("unchecked")
    public static IStorageChannel<?> getManaStorageChannelInstance() {
        return (IStorageChannel<?>) manaChannelInstance;
    }

    /**
     * 获取 Botania_Applie 的 {@code ManaStorageChannel} 类对象.
     *
     * @return 外部通道类,若不存在则返回 null
     */
    public static Class<?> getManaChannelClass() {
        return manaChannelClass;
    }
}

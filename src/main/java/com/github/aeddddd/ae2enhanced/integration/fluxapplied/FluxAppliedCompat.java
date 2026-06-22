package com.github.aeddddd.ae2enhanced.integration.fluxapplied;

import appeng.api.storage.IStorageChannel;

/**
 * Flux_Applied 兼容性检测类.
 * 仅通过反射探测 {@code com.flux_applied.ae2.FluxStorageChannel},避免无条件加载类中出现外部类的硬引用.
 */
public final class FluxAppliedCompat {

    private static final boolean LOADED;
    private static final boolean HAS_FLUX_CHANNEL;
    private static Class<?> fluxChannelClass;
    private static Object fluxChannelInstance;

    static {
        boolean loaded = false;
        boolean hasChannel = false;
        try {
            fluxChannelClass = Class.forName("com.flux_applied.ae2.FluxStorageChannel");
            loaded = true;
            Object instance = fluxChannelClass.getField("INSTANCE").get(null);
            if (instance instanceof IStorageChannel) {
                fluxChannelInstance = instance;
                hasChannel = true;
            }
        } catch (Throwable ignored) {
            // Flux_Applied 未安装或类结构不符,静默回退
        }
        LOADED = loaded;
        HAS_FLUX_CHANNEL = hasChannel;
    }

    private FluxAppliedCompat() {
    }

    /**
     * 判断 Flux_Applied 相关类是否存在.
     */
    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * 判断 Flux_Applied 的 {@code FluxStorageChannel} 是否可用.
     */
    public static boolean isFluxStorageChannelAvailable() {
        return HAS_FLUX_CHANNEL;
    }

    /**
     * 获取 Flux_Applied 的 {@code FluxStorageChannel} 单例实例.
     *
     * @return 外部能量存储通道实例,若不可用则返回 null
     */
    @SuppressWarnings("unchecked")
    public static IStorageChannel<?> getFluxStorageChannelInstance() {
        return (IStorageChannel<?>) fluxChannelInstance;
    }

    /**
     * 获取 Flux_Applied 的 {@code FluxStorageChannel} 类对象.
     *
     * @return 外部通道类,若不存在则返回 null
     */
    public static Class<?> getFluxChannelClass() {
        return fluxChannelClass;
    }
}

package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import net.minecraftforge.fml.common.Loader;

import java.math.BigInteger;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 可选存储管理器：条件加载 Mekanism 气体、Thaumcraft 源质等第三方存储适配器。
 * 同时提供扩展注册接口，允许其他 Mod 通过 API 注册自定义存储适配器。
 *
 * 设计原则：本类<strong>不</strong>直接引用可选 Mod 的类，所有适配器实例通过
 * {@link Class#forName} 延迟加载，字段类型为 {@link Object}，方法调用通过反射完成。
 */
public class OptionalStorageManager {

    /**
     * 使用 Object 类型保存适配器实例，避免在 OptionalStorageManager 类加载时
     * 连带触发 GasStorageAdapter / EssentiaStorageAdapter 的加载
     * （这两个类的常量池中包含 mekanism / thaumicenergistics 的 CONSTANT_Class 引用）。
     */
    private Object gasAdapter;
    private Object essentiaAdapter;

    /**
     * 外部扩展注册表。其他 Mod 可通过 {@link #registerExternalAdapter} 注册自定义 IMEMonitor。
     */
    private final List<Object> externalAdapters = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 反射方法缓存，避免每次 getHandlers() 都重复 getMethod()。
     */
    private final Map<Class<?>, Method[]> methodCache = new ConcurrentHashMap<>();

    private Method[] getCachedMethods(Class<?> clazz) {
        return methodCache.computeIfAbsent(clazz, k -> {
            try {
                return new Method[]{
                    k.getMethod("getChannel"),
                    k.getMethod("getHandler")
                };
            } catch (NoSuchMethodException e) {
                return null;
            }
        });
    }

    public void init(HyperdimensionalStorageFile file) {
        if (Loader.isModLoaded("mekeng")) {
            try {
                Class<?> gasAdapterClass = Class.forName("com.github.aeddddd.ae2enhanced.storage.GasStorageAdapter");
                gasAdapter = gasAdapterClass.getConstructor(HyperdimensionalStorageFile.class).newInstance(file);
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                    "[AE2E] Failed to initialize gas storage adapter", e);
            }
        }
        if (Loader.isModLoaded("thaumicenergistics")) {
            try {
                Class<?> essentiaAdapterClass = Class.forName("com.github.aeddddd.ae2enhanced.storage.EssentiaStorageAdapter");
                essentiaAdapter = essentiaAdapterClass.getConstructor(HyperdimensionalStorageFile.class).newInstance(file);
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn(
                    "[AE2E] Failed to initialize essentia storage adapter", e);
            }
        }
    }

    /**
     * 设置回调。通过反射调用适配器的 setOnChangeCallback / setPostChangeCallback，
     * 避免编译期依赖可选 Mod 的类型。
     */
    @SuppressWarnings("unchecked")
    public void setCallbacks(Runnable changeCallback,
                             BiConsumer<?, IActionSource> itemPostChange,
                             BiConsumer<?, IActionSource> fluidPostChange) {
        if (gasAdapter != null) {
            try {
                gasAdapter.getClass().getMethod("setOnChangeCallback", Runnable.class).invoke(gasAdapter, changeCallback);
                gasAdapter.getClass().getMethod("setPostChangeCallback", BiConsumer.class).invoke(gasAdapter, itemPostChange);
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to set gas adapter callbacks", e);
            }
        }
        if (essentiaAdapter != null) {
            try {
                essentiaAdapter.getClass().getMethod("setOnChangeCallback", Runnable.class).invoke(essentiaAdapter, changeCallback);
                essentiaAdapter.getClass().getMethod("setPostChangeCallback", BiConsumer.class).invoke(essentiaAdapter, itemPostChange);
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to set essentia adapter callbacks", e);
            }
        }
    }

    /**
     * 根据 IStorageChannel 返回对应的 IMEInventoryHandler。
     */
    @SuppressWarnings("unchecked")
    public List<IMEInventoryHandler> getHandlers(IStorageChannel<?> channel) {
        try {
            Class<?> gasChannelClass = Class.forName("com.mekeng.github.common.me.storage.IGasStorageChannel");
            if (gasAdapter != null && gasChannelClass.isInstance(channel)
                    && gasAdapter instanceof IMEInventoryHandler) {
                return Collections.singletonList((IMEInventoryHandler) gasAdapter);
            }
        } catch (ClassNotFoundException ignored) {}

        try {
            Class<?> essentiaChannelClass = Class.forName("thaumicenergistics.api.storage.IEssentiaStorageChannel");
            if (essentiaAdapter != null && essentiaChannelClass.isInstance(channel)
                    && essentiaAdapter instanceof IMEInventoryHandler) {
                return Collections.singletonList((IMEInventoryHandler) essentiaAdapter);
            }
        } catch (ClassNotFoundException ignored) {}

        // 外部扩展适配器
        for (Object ext : externalAdapters) {
            Method[] methods = getCachedMethods(ext.getClass());
            if (methods == null) continue;
            try {
                Object extChannel = methods[0].invoke(ext);
                if (extChannel == channel) {
                    Object handler = methods[1].invoke(ext);
                    if (handler instanceof IMEInventoryHandler) {
                        return Collections.singletonList((IMEInventoryHandler) handler);
                    }
                }
            } catch (Exception ignored) {}
        }
        return Collections.emptyList();
    }

    public int getTotalTypeCount() {
        int count = 0;
        if (gasAdapter != null) {
            try {
                Object map = gasAdapter.getClass().getMethod("getStorageMap").invoke(gasAdapter);
                if (map instanceof Map) count += ((Map<?, ?>) map).size();
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to get gas type count", e);
            }
        }
        if (essentiaAdapter != null) {
            try {
                Object map = essentiaAdapter.getClass().getMethod("getStorageMap").invoke(essentiaAdapter);
                if (map instanceof Map) count += ((Map<?, ?>) map).size();
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to get essentia type count", e);
            }
        }
        return count;
    }

    public BigInteger getTotalCount() {
        BigInteger sum = BigInteger.ZERO;
        if (gasAdapter != null) {
            try {
                Object total = gasAdapter.getClass().getMethod("getTotalCount").invoke(gasAdapter);
                if (total instanceof BigInteger) sum = sum.add((BigInteger) total);
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to get gas total count", e);
            }
        }
        if (essentiaAdapter != null) {
            try {
                Object total = essentiaAdapter.getClass().getMethod("getTotalCount").invoke(essentiaAdapter);
                if (total instanceof BigInteger) sum = sum.add((BigInteger) total);
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to get essentia total count", e);
            }
        }
        return sum;
    }

    public boolean isSafeMode() {
        boolean safe = false;
        if (gasAdapter != null) {
            try {
                Object result = gasAdapter.getClass().getMethod("isSafeMode").invoke(gasAdapter);
                if (result instanceof Boolean) safe |= (Boolean) result;
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to get gas safe mode", e);
            }
        }
        if (essentiaAdapter != null) {
            try {
                Object result = essentiaAdapter.getClass().getMethod("isSafeMode").invoke(essentiaAdapter);
                if (result instanceof Boolean) safe |= (Boolean) result;
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to get essentia safe mode", e);
            }
        }
        return safe;
    }

    /**
     * 刷新所有可选 monitor（反射强制 NetworkMonitor 更新）。
     */
    public void refreshMonitors(java.util.function.Consumer<Object> refresher) {
        if (gasAdapter != null) refresher.accept(gasAdapter);
        if (essentiaAdapter != null) refresher.accept(essentiaAdapter);
    }

    /**
     * 注册外部存储适配器。其他 Mod 可通过 API 调用此方法注册自定义存储通道。
     */
    public void registerExternalAdapter(Object adapter) {
        if (adapter != null) {
            externalAdapters.add(adapter);
        }
    }

    public Object getGasAdapter() {
        return gasAdapter;
    }

    public Object getEssentiaAdapter() {
        return essentiaAdapter;
    }

    public void close() {
        gasAdapter = null;
        essentiaAdapter = null;
        externalAdapters.clear();
        methodCache.clear();
    }
}

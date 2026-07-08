package com.github.aeddddd.ae2enhanced.hyperdimensional.storage;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel.StorageChannel;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * 可选存储管理器：条件加载 Mekanism 气体、Thaumcraft 源质、Botania mana、Astral Sorcery starlight 等
 * 第三方存储通道。
 *
 * <p>设计原则：本类<strong>不</strong>直接引用可选 Mod 的类，所有通道实例通过
 * {@link Class#forName} 延迟加载，方法调用通过反射完成。若对应 Mod 未安装或没有提供
 * 符合约定的工厂方法，则静默跳过。</p>
 *
 * <p>当前 1.20.1 分支下这些第三方 Mod 尚未移植，因此本类主要提供反射钩子与占位实现，
 * 便于后续随 Mod 生态更新而扩展。</p>
 */
public class OptionalStorageManager {

    private static final OptionalStorageManager INSTANCE = new OptionalStorageManager();

    /**
     * 需要尝试加载的第三方 Mod 主类列表。
     * 若类存在，则查找其返回 {@link StorageChannel} 的公共静态无参工厂方法并注册。
     */
    private static final List<String> OPTIONAL_CLASSES = List.of(
            "mekanism.common.Mekanism",
            "thaumcraft.Thaumcraft",
            "vazkii.botania.common.Botania",
            "hellfirepvp.astralsorcery.AstralSorcery"
    );

    private OptionalStorageManager() {
    }

    /**
     * 获取单例实例。
     *
     * @return OptionalStorageManager 单例
     */
    public static OptionalStorageManager getInstance() {
        return INSTANCE;
    }

    /**
     * 尝试通过反射加载并注册所有可选第三方通道。
     *
     * @param storage 目标超维度存储容器
     */
    public void registerOptionalChannels(HyperdimensionalStorage storage) {
        for (String className : OPTIONAL_CLASSES) {
            try {
                Class<?> clazz = Class.forName(className);
                registerChannelFromClass(clazz, storage);
            } catch (ClassNotFoundException e) {
                // 可选 Mod 未安装，静默跳过
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] 加载可选存储通道 {} 失败", className, e);
            }
        }
    }

    /**
     * 在指定类中查找返回 {@link StorageChannel} 的公共静态无参方法，并注册到 storage。
     *
     * @param clazz   第三方 Mod 主类
     * @param storage 目标超维度存储容器
     */
    @SuppressWarnings("unchecked")
    private void registerChannelFromClass(Class<?> clazz, HyperdimensionalStorage storage) {
        for (Method method : clazz.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getParameterCount() != 0) {
                continue;
            }
            Class<?> returnType = method.getReturnType();
            if (!StorageChannel.class.isAssignableFrom(returnType)) {
                continue;
            }
            try {
                Object result = method.invoke(null);
                if (result instanceof StorageChannel) {
                    storage.registerChannel((StorageChannel<?>) result);
                    AE2Enhanced.LOGGER.info("[AE2E] 已注册可选存储通道: {}.{}", clazz.getName(), method.getName());
                    return;
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] 调用 {}.{} 失败", clazz.getName(), method.getName(), e);
            }
        }
    }
}

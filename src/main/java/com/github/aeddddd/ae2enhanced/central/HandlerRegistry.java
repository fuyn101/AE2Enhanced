package com.github.aeddddd.ae2enhanced.central;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 远程处理器反射隔离注册表(适配 AE2S 后的最小骨架).
 *
 * <p>无条件加载,内部通过 {@link Class#forName(String)} 懒加载具体 handler 类.
 * 未安装的 mod 对应的 handler 类永远不会被触碰,避免 {@link NoClassDefFoundError}.</p>
 *
 * <p>当前仅注册通用 fallback;各 mod 专用 handler 将在后续集成阶段通过
 * {@link #tryLoad(String, String)} 反射加入.</p>
 */
public final class HandlerRegistry {

    private static final List<IRemoteHandler> HANDLERS = new ArrayList<>();
    private static boolean initialized = false;

    private HandlerRegistry() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        HANDLERS.add(new DefaultSingleBatchHandler());
        AE2Enhanced.LOGGER.info("[AE2E] HandlerRegistry initialized with default fallback.");
    }

    /**
     * 反射隔离加载 handler.若对应 mod 未加载或类不存在则忽略.
     */
    public static void tryLoad(String modId, String className) {
        if (!net.minecraftforge.fml.common.Loader.isModLoaded(modId)) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className);
            IRemoteHandler handler = (IRemoteHandler) clazz.newInstance();
            HANDLERS.add(handler);
            AE2Enhanced.LOGGER.info("[AE2E] HandlerRegistry loaded handler for mod: {}", modId);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] HandlerRegistry failed to load handler {} for mod: {}", className, modId, e);
        }
    }

    /**
     * 根据方块 ID 查找匹配的处理器,未匹配时返回默认 fallback.
     */
    @Nonnull
    public static IRemoteHandler findHandler(@Nonnull String blockId) {
        init();
        for (IRemoteHandler handler : HANDLERS) {
            if (handler.canHandle(blockId)) {
                return handler;
            }
        }
        return HANDLERS.get(0);
    }

    /**
     * 获取所有已注册处理器的只读副本.
     */
    @Nonnull
    public static List<IRemoteHandler> getHandlers() {
        init();
        return Collections.unmodifiableList(new ArrayList<>(HANDLERS));
    }
}

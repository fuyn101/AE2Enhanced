package com.github.aeddddd.ae2enhanced.util.channel;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraftforge.fml.common.Loader;

/**
 * 星光（Starlight）通道占位 stub。
 *
 * <p>AE2S 目前没有可用的星光桥接 addon。该类仅作为扩展点存在，检测到相关模组时输出 TODO 日志，
 * 避免在其他模块中硬引用第三方星光类导致类加载失败。</p>
 */
public final class StarlightChannelPlaceholder {

    private StarlightChannelPlaceholder() {}

    public static final String ASTRAL_SORCERY = "astralsorcery";

    public static boolean isAvailable() {
        return Loader.isModLoaded(ASTRAL_SORCERY);
    }

    public static void logTodoIfAvailable() {
        if (isAvailable()) {
            AE2Enhanced.LOGGER.info("[AE2E] Starlight channel integration is currently a TODO placeholder.");
        }
    }
}

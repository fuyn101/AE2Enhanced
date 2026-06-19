package com.github.aeddddd.ae2enhanced.mixin;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * 核心插件（空实现）.
 *
 * <p>AE2S 以 coremod 形式加载，其部分类（如 {@code ae2.tile.grid.AENetworkedTile}）
 * 对普通 mod classloader 不可见。通过把 AE2Enhanced 也声明为 coremod，
 * 使我们的类与 AE2S 类处于同一 classloader，避免 {@code NoClassDefFoundError}。</p>
 */
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("AE2Enhanced Core Plugin")
public class AE2EnhancedCorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

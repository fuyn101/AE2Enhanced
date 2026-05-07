package com.github.aeddddd.ae2enhanced.mixin;

import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.ILateMixinLoader;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
@IFMLLoadingPlugin.Name("AE2EnhancedMixinPlugin")
public class AssemblyMixinPlugin implements IFMLLoadingPlugin, ILateMixinLoader {

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
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

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<>();
        configs.add("mixins.ae2enhanced.late.json");
        // 如果 Thaumic Energistics 安装，加载源质相关 Mixin
        try {
            Class.forName("thaumicenergistics.api.storage.IEssentiaStorageChannel");
            configs.add("mixins.ae2enhanced.late.thaumic.json");
        } catch (ClassNotFoundException e) {
            // Thaumic Energistics 未安装，跳过源质 Mixin
        }
        return configs;
    }
}

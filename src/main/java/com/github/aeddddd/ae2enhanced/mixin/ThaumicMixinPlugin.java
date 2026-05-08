package com.github.aeddddd.ae2enhanced.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * mixins.ae2enhanced.late.thaumic.json 的 plugin。
 * 在 onLoad 时检测 Thaumic Energistics 是否存在，若不存在则跳过该配置文件下的所有 Mixin。
 */
public class ThaumicMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("AE2E-ThaumicMixinPlugin");
    private boolean thaumicEnergisticsLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class.forName("thaumicenergistics.api.storage.IEssentiaStorageChannel");
            thaumicEnergisticsLoaded = true;
            LOGGER.info("[AE2E] ThaumicMixinPlugin: IEssentiaStorageChannel found, thaumic mixins ENABLED");
        } catch (ClassNotFoundException e) {
            thaumicEnergisticsLoaded = false;
            LOGGER.info("[AE2E] ThaumicMixinPlugin: IEssentiaStorageChannel NOT found, thaumic mixins DISABLED");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return thaumicEnergisticsLoaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

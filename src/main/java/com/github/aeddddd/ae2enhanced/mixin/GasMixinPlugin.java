package com.github.aeddddd.ae2enhanced.mixin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * mixins.ae2enhanced.late.gas.json 的 plugin。
 * 在 onLoad 时检测 MekanismEnergistics (mekeng) 的 IGasStorageChannel 是否存在，
 * 若不存在则跳过该配置文件下的所有 Mixin。
 */
public class GasMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogManager.getLogger("AE2E-GasMixinPlugin");
    private boolean gasChannelLoaded = false;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class.forName("com.mekeng.github.common.me.storage.IGasStorageChannel");
            gasChannelLoaded = true;
            LOGGER.info("[AE2E] GasMixinPlugin: IGasStorageChannel found, gas mixins ENABLED");
        } catch (ClassNotFoundException e) {
            gasChannelLoaded = false;
            LOGGER.info("[AE2E] GasMixinPlugin: IGasStorageChannel NOT found, gas mixins DISABLED");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return gasChannelLoaded;
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

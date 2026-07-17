package com.github.aeddddd.ae2enhanced.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mekanism CE Unofficial v10 machine output redirect mixin config plugin.
 */
public class MekanismV10MixinPlugin implements IMixinConfigPlugin {

    private boolean mekanismV10Loaded;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            mekanismV10Loaded = loader != null
                    && loader.getResource("mekanism/common/tile/component/TileComponentEjector.class") != null
                    && loader.getResource("mekanism/api/fluid/IExtendedFluidTank.class") != null;
        } catch (Exception ignored) {
            mekanismV10Loaded = false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return mekanismV10Loaded;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}

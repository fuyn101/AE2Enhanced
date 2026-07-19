package com.github.aeddddd.ae2enhanced.mekceuv10patch;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

public final class MekCeuV10PatchLateMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        if (!Loader.isModLoaded("ae2enhanced") || !Loader.isModLoaded("mekanism")
                || hasBuiltInV10Support() || !isMekCeuV10()) {
            return Collections.emptyList();
        }
        return Collections.singletonList("mixins.ae2enhancedmekceuv10patch.json");
    }

    private static boolean hasBuiltInV10Support() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null && loader.getResource(
                "com/github/aeddddd/ae2enhanced/mixin/MekanismV10MixinPlugin.class") != null;
    }

    private static boolean isMekCeuV10() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null && loader.getResource("mekanism/api/fluid/IExtendedFluidTank.class") != null;
    }
}

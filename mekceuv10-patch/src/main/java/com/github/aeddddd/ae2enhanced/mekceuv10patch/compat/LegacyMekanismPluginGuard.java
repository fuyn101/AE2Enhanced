package com.github.aeddddd.ae2enhanced.mekceuv10patch.compat;

public final class LegacyMekanismPluginGuard {

    private LegacyMekanismPluginGuard() {
    }

    public static boolean isLegacyMekanism() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null
                && loader.getResource("mekanism/common/tile/component/TileComponentEjector.class") != null
                && loader.getResource("mekanism/api/fluid/IExtendedFluidTank.class") == null;
    }
}

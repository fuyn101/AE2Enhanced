package com.github.aeddddd.ae2enhanced.mixin;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Late Mixin 加载器.
 * 仅保留第三方 mod 的 Mixin 配置；AE2/自定义 type 相关配置已移除.
 */
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<>(Arrays.asList(
                "mixins.ae2enhanced.late.json"
        ));
        if (Loader.isModLoaded("thermalexpansion")) {
            configs.add("mixins.ae2enhanced.late.thermal.json");
        }
        if (Loader.isModLoaded("mekanism")) {
            configs.add("mixins.ae2enhanced.late.mekanism.json");
        }
        if (Loader.isModLoaded("techreborn")) {
            configs.add("mixins.ae2enhanced.late.techreborn.json");
        }
        if (Loader.isModLoaded("enderio")) {
            configs.add("mixins.ae2enhanced.late.eio.json");
        }
        if (Loader.isModLoaded("industrialforegoing")) {
            configs.add("mixins.ae2enhanced.late.industrialforegoing.json");
        }
        if (Loader.isModLoaded("nuclearcraft")) {
            configs.add("mixins.ae2enhanced.late.nuclearcraft.json");
        }
        return configs;
    }
}

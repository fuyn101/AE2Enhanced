package com.github.aeddddd.ae2enhanced.mixin;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Arrays;
import java.util.List;

/**
 * Late Mixin 加载器。
 * 从 AssemblyMixinPlugin 中拆分出来，避免 IFMLLoadingPlugin（coremod）在过早阶段
 * 触发 JEI 内部类的加载，导致 CleanroomMC ActualClassLoader 标记 invalid。
 *
 * ae2fc 采用相同的架构：LateMixinLoader 仅实现 ILateMixinLoader，不作为 coremod。
 */
public class LateMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList(
                "mixins.ae2enhanced.late.json",
                "mixins.ae2enhanced.late.thaumic.json",
                "mixins.ae2enhanced.late.gas.json"
        );
    }
}

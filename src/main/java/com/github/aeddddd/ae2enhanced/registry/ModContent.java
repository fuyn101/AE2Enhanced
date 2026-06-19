package com.github.aeddddd.ae2enhanced.registry;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/**
 * 内容注册中心：升级卡、物质炮弹药、各类初始化注册表
 */
public final class ModContent {

    private ModContent() {}

    public static void preInit() {
        // 气体/魔力通道由可选依赖 mekeng / appbot 提供，不在这里初始化自定义 AEKeyType。
        // 源质/星光暂为 TODO，待对应 AE2S addon 出现后再接入。

        com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardHandlerRegistry.init();
    }

    public static void init() {
        // TODO: 物质炮弹药注册需适配 AE2S 新的 matter cannon API
        // ae2.api.AEApi.instance().registries().matterCannon().registerAmmo(...)

        // 智能样板垃圾回收器
        com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternGarbageCollector.init();
    }
}

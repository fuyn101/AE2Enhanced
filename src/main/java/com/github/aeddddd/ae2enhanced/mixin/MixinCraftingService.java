package com.github.aeddddd.ae2enhanced.mixin;

import org.spongepowered.asm.mixin.Mixin;

import appeng.me.service.CraftingService;

/**
 * Phase 0 占位 Mixin，仅用于验证 Mixin AP 能生成 refmap。
 * Phase 4 将注入虚拟 CPU。
 */
@Mixin(CraftingService.class)
public class MixinCraftingService {
}

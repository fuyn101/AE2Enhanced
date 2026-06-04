package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import com.github.aeddddd.ae2enhanced.crafting.scheduler.PatternDepthRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects depth recording into CraftingJob.addTask().
 * Depth is used by the optimized scheduler for critical-path-first allocation.
 */
@Mixin(value = CraftingJob.class, remap = false)
public class MixinCraftingJob {

    @Inject(method = "addTask", at = @At("TAIL"), remap = false)
    private void ae2e$recordDepth(IAEItemStack what, long crafts,
                                   ICraftingPatternDetails details, int depth,
                                   CallbackInfo ci) {
        PatternDepthRegistry.record(details, depth);
    }
}

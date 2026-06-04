package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.crafting.scheduler.AmplificationGenerator;
import com.github.aeddddd.ae2enhanced.crafting.scheduler.PlanAmplifiedPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Injects amplified pattern variants into CraftingTreeNode.addNode().
 * This happens at TAIL so native logic (notRecursive checks, etc.) runs first.
 */
@Mixin(value = CraftingTreeNode.class, remap = false)
public class MixinCraftingTreeNode {

    @Shadow(remap = false)
    private ArrayList<CraftingTreeProcess> nodes;

    @Shadow(remap = false)
    private appeng.api.networking.crafting.ICraftingGrid cc;

    @Shadow(remap = false)
    private CraftingJob job;

    @Shadow(remap = false)
    private int depth;

    @Unique
    private static Constructor<CraftingTreeProcess> ae2e$processCtor;

    @Unique
    private static Field ae2e$processDetailsField;

    @Unique
    private static Field ae2e$processDepthField;

    @Unique
    private static boolean ae2e$reflectionReady = false;

    @Unique
    private static boolean ae2e$reflectionFailed = false;

    private static synchronized void ae2e$initReflection() {
        if (ae2e$reflectionReady || ae2e$reflectionFailed) return;
        try {
            ae2e$processCtor = CraftingTreeProcess.class.getDeclaredConstructor(
                appeng.api.networking.crafting.ICraftingGrid.class,
                CraftingJob.class,
                ICraftingPatternDetails.class,
                CraftingTreeNode.class,
                int.class
            );
            ae2e$processCtor.setAccessible(true);

            ae2e$processDetailsField = CraftingTreeProcess.class.getDeclaredField("details");
            ae2e$processDetailsField.setAccessible(true);

            ae2e$processDepthField = CraftingTreeProcess.class.getDeclaredField("depth");
            ae2e$processDepthField.setAccessible(true);

            ae2e$reflectionReady = true;
        } catch (Exception e) {
            ae2e$reflectionFailed = true;
            AE2Enhanced.LOGGER.error("[AE2E] CraftingTreeNode reflection init failed: {}", e.toString());
        }
    }

    @Inject(method = "addNode", at = @At("TAIL"), remap = false)
    private void ae2e$injectAmplifiedVariants(CallbackInfo ci) {
        if (!AE2EnhancedConfig.crafting.autoAmplification) return;
        if (nodes == null || nodes.isEmpty()) return;
        if (ae2e$reflectionFailed) return;

        ae2e$initReflection();
        if (!ae2e$reflectionReady) return;

        try {
            List<CraftingTreeProcess> originals = new ArrayList<>(nodes);

            for (CraftingTreeProcess pro : originals) {
                ICraftingPatternDetails details = (ICraftingPatternDetails) ae2e$processDetailsField.get(pro);
                if (details instanceof PlanAmplifiedPattern) continue;

                List<PlanAmplifiedPattern> amplified = AmplificationGenerator.generate(details);
                if (amplified.size() <= 1) continue;

                int proDepth = ae2e$processDepthField.getInt(pro);

                for (PlanAmplifiedPattern amp : amplified) {
                    if (amp.getAmplification() <= 1) continue;

                    CraftingTreeProcess newPro = ae2e$processCtor.newInstance(cc, job, amp, (CraftingTreeNode)(Object)this, proDepth);
                    if (newPro != null) {
                        // Insert at front so larger amplification is tried first by request()
                        nodes.add(0, newPro);
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] injectAmplifiedVariants error: {}", e.toString());
        }
    }
}

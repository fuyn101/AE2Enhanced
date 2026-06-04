package com.github.aeddddd.ae2enhanced.crafting.scheduler;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Generates amplified pattern variants for planning-time optimization.
 *
 * <p>Only processing patterns ({@code isCraftable=false}) are amplified,
 * because workbench patterns rely on shape matching which ignores stack size.</p>
 */
public class AmplificationGenerator {

    private static final WeakHashMap<ICraftingPatternDetails, List<PlanAmplifiedPattern>> cache = new WeakHashMap<>();

    public static List<PlanAmplifiedPattern> generate(ICraftingPatternDetails original) {
        return cache.computeIfAbsent(original, k -> {
            List<PlanAmplifiedPattern> list = new ArrayList<>();
            list.add(new PlanAmplifiedPattern(k, 1)); // Always include 1x fallback

            if (!isAmplifiable(k)) {
                return Collections.unmodifiableList(list);
            }

            int maxAmp = calculateMaxAmplification(k);
            int amp = 2;
            while (amp <= maxAmp) {
                list.add(new PlanAmplifiedPattern(k, amp));
                amp <<= 1;
            }

            // If maxAmp is not a power of two, add the exact max
            if (maxAmp > 1 && (maxAmp & (maxAmp - 1)) != 0) {
                list.add(new PlanAmplifiedPattern(k, maxAmp));
            }

            return Collections.unmodifiableList(list);
        });
    }

    public static boolean isAmplifiable(ICraftingPatternDetails details) {
        // Workbench patterns cannot be amplified via InventoryCrafting stack size
        if (details.isCraftable()) return false;
        if (details.canSubstitute()) return false;

        for (IAEItemStack input : details.getCondensedInputs()) {
            if (input == null) continue;
            ItemStack def = input.getDefinition();
            if (input.getItem().hasContainerItem(def)) return false;
            if (input.getItem().getItemStackLimit(def) <= 1) return false;
        }

        for (IAEItemStack output : details.getCondensedOutputs()) {
            if (output == null) continue;
            ItemStack def = output.getDefinition();
            if (output.getItem().getItemStackLimit(def) <= 1) return false;
        }

        return true;
    }

    public static int calculateMaxAmplification(ICraftingPatternDetails details) {
        int max = Integer.MAX_VALUE;

        for (IAEItemStack input : details.getCondensedInputs()) {
            if (input == null) continue;
            int itemMax = input.getItem().getItemStackLimit(input.getDefinition());
            int amount = (int) input.getStackSize();
            if (amount > 0) {
                max = Math.min(max, itemMax / amount);
            }
        }

        for (IAEItemStack output : details.getCondensedOutputs()) {
            if (output == null) continue;
            int itemMax = output.getItem().getItemStackLimit(output.getDefinition());
            int amount = (int) output.getStackSize();
            if (amount > 0) {
                max = Math.min(max, itemMax / amount);
            }
        }

        return Math.max(1, max);
    }

    public static void clearCache() {
        cache.clear();
    }
}

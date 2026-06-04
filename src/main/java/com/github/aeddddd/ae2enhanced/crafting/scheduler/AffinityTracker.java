package com.github.aeddddd.ae2enhanced.crafting.scheduler;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import java.util.Arrays;
import java.util.WeakHashMap;

/**
 * Tracks which pattern a crafting medium was last pushing.
 * Used to implement affinity-aware allocation for Smart Blocking Mode.
 *
 * <p>Does NOT rely on injectItems to detect completion; instead uses
 * {@code isBusy() + timeout} which is more reliable since injectItems
 * does not carry machine identity.</p>
 */
public class AffinityTracker {

    public static class AffinityState {
        ICraftingPatternDetails pattern;
        long lastPushTime;
    }

    private static final WeakHashMap<ICraftingMedium, AffinityState> map = new WeakHashMap<>();

    public static boolean isAffinity(ICraftingMedium medium, ICraftingPatternDetails details, long timeoutMs) {
        AffinityState state = map.get(medium);
        if (state == null || state.pattern == null) return false;
        if (!patternEquals(state.pattern, details)) return false;

        long now = System.currentTimeMillis();
        if (now - state.lastPushTime > timeoutMs && !medium.isBusy()) {
            // Expired and idle: clear silently
            state.pattern = null;
            return false;
        }
        return true;
    }

    public static void updatePush(ICraftingMedium medium, ICraftingPatternDetails details) {
        AffinityState state = map.computeIfAbsent(medium, k -> new AffinityState());
        state.pattern = details;
        state.lastPushTime = System.currentTimeMillis();
    }

    public static void cleanup(long timeoutMs) {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> {
            ICraftingMedium m = e.getKey();
            AffinityState s = e.getValue();
            if (m == null) return true;
            if (s.pattern == null) return true;
            if (now - s.lastPushTime > timeoutMs && !m.isBusy()) {
                return true;
            }
            return false;
        });
    }

    /**
     * Content-level pattern equality: compares condensed inputs/outputs.
     * This avoids false negatives when multiple identical patterns exist.
     */
    public static boolean patternEquals(ICraftingPatternDetails a, ICraftingPatternDetails b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Arrays.equals(a.getCondensedInputs(), b.getCondensedInputs())
            && Arrays.equals(a.getCondensedOutputs(), b.getCondensedOutputs());
    }
}

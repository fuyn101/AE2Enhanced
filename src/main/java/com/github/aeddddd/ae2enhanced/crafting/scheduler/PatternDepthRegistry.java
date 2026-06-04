package com.github.aeddddd.ae2enhanced.crafting.scheduler;

import appeng.api.networking.crafting.ICraftingPatternDetails;

import java.util.HashMap;
import java.util.Map;

/**
 * Records the minimum depth of each pattern in the crafting tree.
 * Depth is injected via a Mixin on CraftingJob.addTask().
 * The registry is cleared when a crafting job completes.
 */
public class PatternDepthRegistry {

    private static final Map<ICraftingPatternDetails, Integer> depthMap = new HashMap<>();

    public static synchronized void record(ICraftingPatternDetails details, int depth) {
        if (details == null) return;
        depthMap.merge(details, depth, Math::min);
    }

    public static synchronized int getDepth(ICraftingPatternDetails details) {
        return depthMap.getOrDefault(details, 10);
    }

    public static synchronized void clear() {
        depthMap.clear();
    }

    public static synchronized boolean isEmpty() {
        return depthMap.isEmpty();
    }
}

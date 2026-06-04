package com.github.aeddddd.ae2enhanced.crafting.scheduler;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Key-path-first, affinity-aware crafting scheduler.
 *
 * <p>This scheduler replaces the native HashMap traversal in
 * CraftingCPUCluster.executeCrafting() with a deterministic scoring system:</p>
 *
 * <ul>
 *   <li>Depth-first: root-node tasks (smaller depth) are prioritized</li>
 *   <li>Batch affinity: tasks prefer machines already running the same pattern</li>
 *   <li>Anti-starvation: long-waiting tasks get linear priority boost</li>
 * </ul>
 */
public class OptimizedScheduler {

    private int consecutiveErrors = 0;
    private boolean fallbackMode = false;

    /**
     * Main entry point called each tick from the Mixin.
     *
     * @return number of pushPattern calls actually executed this tick
     */
    @SuppressWarnings("unchecked")
    public int executeTick(
            Map<ICraftingPatternDetails, Object> tasks,
            int remainingOps,
            CraftingGridCache cc,
            CraftingCPUCluster cpu,
            IEnergyGrid eg) {

        if (fallbackMode) return 0;
        if (!TaskProgressAccessor.isReady()) return 0;
        if (tasks == null || tasks.isEmpty() || remainingOps <= 0) return 0;

        long timeLimitNs = AE2EnhancedConfig.crafting.schedulerTimeLimitMs * 1_000_000L;
        long startNs = System.nanoTime();

        try {
            AE2EnhancedConfig.Crafting cfg = AE2EnhancedConfig.crafting;
            double wDepth = cfg.schedulerDepthWeight;
            double wBatch = cfg.schedulerBatchWeight;
            double wWait = cfg.schedulerWaitWeight;
            long timeoutMs = cfg.affinityTimeoutMs;
            boolean debug = cfg.schedulerDebugLog;

            // 1. Build scored task list
            List<TaskEntry> entries = new ArrayList<>();
            for (Map.Entry<ICraftingPatternDetails, Object> e : tasks.entrySet()) {
                ICraftingPatternDetails details = e.getKey();
                Object progress = e.getValue();
                long remaining = TaskProgressAccessor.getValue(progress);
                if (remaining <= 0) continue;

                int depth = PatternDepthRegistry.getDepth(details);
                int wait = getWaitTicks(details);

                double score = wDepth / (depth + 1.0)
                             + wBatch * remaining
                             + wWait * wait;

                entries.add(new TaskEntry(details, progress, remaining, depth, wait, score));
            }

            if (entries.isEmpty()) return 0;

            // 2. Sort by score descending
            entries.sort((a, b) -> Double.compare(b.score, a.score));

            // 3. Affinity cleanup
            AffinityTracker.cleanup(timeoutMs);

            // 4. Greedy allocation
            int executed = 0;
            Set<ICraftingMedium> usedThisTick = new HashSet<>();

            for (TaskEntry task : entries) {
                if (remainingOps <= 0) break;
                if (task.remaining <= 0) continue;

                // Time guard
                if (System.nanoTime() - startNs > timeLimitNs) {
                    if (debug) AE2Enhanced.LOGGER.info("[AE2E] Scheduler time limit exceeded, yielding");
                    break;
                }

                List<ICraftingMedium> all = cc.getMediums(task.details);
                if (all == null || all.isEmpty()) continue;

                List<ICraftingMedium> affinity = new ArrayList<>();
                List<ICraftingMedium> idle = new ArrayList<>();

                for (ICraftingMedium m : all) {
                    if (m == null || usedThisTick.contains(m)) continue;
                    if (m.isBusy()) continue;

                    if (AffinityTracker.isAffinity(m, task.details, timeoutMs)) {
                        affinity.add(m);
                    } else {
                        idle.add(m);
                    }
                }

                boolean pushed = false;

                // 4a. Affinity machines first
                for (ICraftingMedium m : affinity) {
                    if (remainingOps <= 0 || task.remaining <= 0) break;
                    if (tryPush(task, m, cpu)) {
                        remainingOps--;
                        task.remaining--;
                        executed++;
                        usedThisTick.add(m);
                        AffinityTracker.updatePush(m, task.details);
                        pushed = true;
                    }
                }

                // 4b. Idle machines
                for (ICraftingMedium m : idle) {
                    if (remainingOps <= 0 || task.remaining <= 0) break;
                    if (tryPush(task, m, cpu)) {
                        remainingOps--;
                        task.remaining--;
                        executed++;
                        usedThisTick.add(m);
                        AffinityTracker.updatePush(m, task.details);
                        pushed = true;
                    }
                }

                // 4c. Update wait counter and write back progress
                if (pushed) {
                    setWaitTicks(task.details, 0);
                } else {
                    setWaitTicks(task.details, task.wait + 1);
                }
                TaskProgressAccessor.setValue(task.progress, task.remaining);
            }

            if (debug && executed > 0) {
                AE2Enhanced.LOGGER.info("[AE2E] Scheduler executed {} pushes this tick", executed);
            }

            consecutiveErrors = 0;
            return executed;

        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] OptimizedScheduler exception", e);
            consecutiveErrors++;
            if (consecutiveErrors >= 3) {
                AE2Enhanced.LOGGER.error("[AE2E] Scheduler entering fallback mode");
                fallbackMode = true;
            }
            return 0;
        }
    }

    private boolean tryPush(TaskEntry task, ICraftingMedium medium, CraftingCPUCluster cpu) {
        try {
            InventoryCrafting ic = buildInventory(task.details);
            return medium.pushPattern(task.details, ic);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] pushPattern exception: {}", e.toString());
            return false;
        }
    }

    private InventoryCrafting buildInventory(ICraftingPatternDetails details) {
        Container dummy = new Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) {
                return false;
            }
        };

        int width = details.isCraftable() ? 3 : 10;  // Processing width already expanded to 10 via mixin
        int height = details.isCraftable() ? 3 : 10;
        InventoryCrafting ic = new InventoryCrafting(dummy, width, height);

        appeng.api.storage.data.IAEItemStack[] inputs = details.getInputs();
        if (inputs != null) {
            for (int i = 0; i < inputs.length && i < width * height; i++) {
                if (inputs[i] != null) {
                    ItemStack stack = inputs[i].createItemStack();
                    ic.setInventorySlotContents(i, stack);
                }
            }
        }
        return ic;
    }

    // ---- wait-ticks map (kept in memory only, per crafting job) ----

    private final java.util.WeakHashMap<ICraftingPatternDetails, Integer> waitMap = new java.util.WeakHashMap<>();

    private int getWaitTicks(ICraftingPatternDetails details) {
        return waitMap.getOrDefault(details, 0);
    }

    private void setWaitTicks(ICraftingPatternDetails details, int ticks) {
        waitMap.put(details, ticks);
    }

    public void clearWaitMap() {
        waitMap.clear();
    }

    public boolean isFallbackMode() {
        return fallbackMode;
    }

    public void resetFallback() {
        fallbackMode = false;
        consecutiveErrors = 0;
    }

    // ---- Internal ----

    private static class TaskEntry {
        final ICraftingPatternDetails details;
        final Object progress;
        long remaining;
        final int depth;
        final int wait;
        final double score;

        TaskEntry(ICraftingPatternDetails details, Object progress, long remaining,
                  int depth, int wait, double score) {
            this.details = details;
            this.progress = progress;
            this.remaining = remaining;
            this.depth = depth;
            this.wait = wait;
            this.score = score;
        }
    }
}

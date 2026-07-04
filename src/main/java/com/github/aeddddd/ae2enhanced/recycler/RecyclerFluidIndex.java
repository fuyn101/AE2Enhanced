package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.storage.data.IAEFluidStack;
import com.github.aeddddd.ae2enhanced.storage.FluidDescriptor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流体类型 → 目标集合 的倒排索引.
 */
public class RecyclerFluidIndex {

    private final Object2ObjectOpenHashMap<FluidDescriptor, ObjectOpenHashSet<TargetManager.TargetRef>> index =
            new Object2ObjectOpenHashMap<>();

    public void add(@Nonnull FluidDescriptor desc, @Nonnull TargetManager.TargetRef target) {
        ObjectOpenHashSet<TargetManager.TargetRef> set = index.get(desc);
        if (set == null) {
            set = new ObjectOpenHashSet<>();
            index.put(desc, set);
        }
        set.add(target);
    }

    public void remove(@Nonnull FluidDescriptor desc, @Nonnull TargetManager.TargetRef target) {
        ObjectOpenHashSet<TargetManager.TargetRef> set = index.get(desc);
        if (set != null) {
            set.remove(target);
            if (set.isEmpty()) {
                index.remove(desc);
            }
        }
    }

    public void clear() {
        index.clear();
    }

    @Nonnull
    public Set<TargetManager.TargetRef> getTargets(@Nonnull FluidDescriptor desc) {
        ObjectOpenHashSet<TargetManager.TargetRef> set = index.get(desc);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    @Nonnull
    public Set<FluidDescriptor> getAllTypes() {
        return Collections.unmodifiableSet(index.keySet());
    }

    public boolean contains(@Nonnull FluidDescriptor desc) {
        return index.containsKey(desc);
    }

    public void rebuild(@Nonnull Map<TargetManager.TargetRef, TargetAdapterSnapshot> snapshots) {
        clear();
        for (Map.Entry<TargetManager.TargetRef, TargetAdapterSnapshot> entry : snapshots.entrySet()) {
            TargetManager.TargetRef target = entry.getKey();
            for (FluidStack stack : entry.getValue().contents) {
                if (stack == null || stack.amount <= 0) continue;
                add(new FluidDescriptor(stack), target);
            }
        }
    }

    /**
     * 目标快照.
     */
    public static final class TargetAdapterSnapshot {
        public final long lastChangeTick;
        public final List<FluidStack> contents;

        public TargetAdapterSnapshot(long lastChangeTick, List<FluidStack> contents) {
            this.lastChangeTick = lastChangeTick;
            this.contents = contents;
        }
    }
}

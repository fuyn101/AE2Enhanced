package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量收集待注入的流体.
 */
public class FluidBulkCollector {

    private final List<IAEFluidStack> buffer = new ArrayList<>();
    private long totalCount = 0;

    public void add(@Nonnull FluidStack stack) {
        if (stack == null || stack.amount <= 0) return;
        IAEFluidStack ae = AEFluidStack.fromFluidStack(stack);
        if (ae == null) return;
        add(ae);
    }

    public void add(@Nonnull IAEFluidStack stack) {
        if (stack.getStackSize() <= 0) return;
        for (IAEFluidStack existing : buffer) {
            if (existing.getFluidStack().isFluidEqual(stack.getFluidStack())) {
                existing.add(stack);
                totalCount += stack.getStackSize();
                return;
            }
        }
        buffer.add(stack.copy());
        totalCount += stack.getStackSize();
    }

    @Nonnull
    public List<IAEFluidStack> drain() {
        if (buffer.isEmpty()) return Collections.emptyList();
        List<IAEFluidStack> result = new ArrayList<>(buffer);
        buffer.clear();
        totalCount = 0;
        return result;
    }

    public boolean isEmpty() {
        return buffer.isEmpty();
    }

    public int getTypeCount() {
        return buffer.size();
    }

    public long getTotalCount() {
        return totalCount;
    }
}

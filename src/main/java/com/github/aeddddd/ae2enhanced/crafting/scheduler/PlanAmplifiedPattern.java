package com.github.aeddddd.ae2enhanced.crafting.scheduler;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.Arrays;

/**
 * A plan-time-only wrapper around {@link ICraftingPatternDetails} that scales
 * input/output stack sizes by an amplification factor.
 *
 * <p>This class is <b>never</b> persisted in CraftingCPUCluster.tasks. It exists
 * solely during the CraftingTree recursion to reduce the number of recursive
 * node visits for large crafting requests. Before NBT serialization, it is
 * mapped back to the original pattern and its value is multiplied.</p>
 */
public class PlanAmplifiedPattern implements ICraftingPatternDetails {

    private final ICraftingPatternDetails original;
    private final int amplification;

    public PlanAmplifiedPattern(ICraftingPatternDetails original, int amplification) {
        this.original = original;
        this.amplification = amplification;
    }

    public ICraftingPatternDetails getOriginal() {
        return original;
    }

    public int getAmplification() {
        return amplification;
    }

    @Override
    public IAEItemStack[] getInputs() {
        return amplifyStacks(original.getInputs());
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return amplifyStacks(original.getCondensedInputs());
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return amplifyStacks(original.getCondensedOutputs());
    }

    @Override
    public IAEItemStack getPrimaryOutput() {
        IAEItemStack out = original.getPrimaryOutput().copy();
        out.setStackSize(out.getStackSize() * amplification);
        return out;
    }

    private IAEItemStack[] amplifyStacks(IAEItemStack[] src) {
        if (src == null) return null;
        IAEItemStack[] dst = new IAEItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            if (src[i] != null) {
                dst[i] = src[i].copy();
                dst[i].setStackSize(src[i].getStackSize() * amplification);
            }
        }
        return dst;
    }

    // ---- Delegates ----
    @Override
    public boolean isCraftable() {
        return original.isCraftable();
    }

    @Override
    public boolean canSubstitute() {
        return false; // Amplified patterns do not support substitution
    }

    @Override
    public ItemStack getPattern() {
        return original.getPattern();
    }

    @Override
    public int getPriority() {
        return original.getPriority();
    }

    @Override
    public void setPriority(int p) {
        original.setPriority(p);
    }

    @Override
    public ItemStack getOutput(InventoryCrafting ic, World world) {
        return original.getOutput(ic, world);
    }

    @Override
    public boolean isValidItemForSlot(int slot, ItemStack stack, World world) {
        return original.isValidItemForSlot(slot, stack, world);
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return amplifyStacks(original.getOutputs());
    }

    @Override
    public java.util.List<IAEItemStack> getSubstituteInputs(int slot) {
        return original.getSubstituteInputs(slot);
    }

    // ---- Identity: based on original + amplification ----
    @Override
    public int hashCode() {
        return original.hashCode() * 31 + amplification;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PlanAmplifiedPattern)) return false;
        PlanAmplifiedPattern o = (PlanAmplifiedPattern) obj;
        return this.amplification == o.amplification && this.original.equals(o.original);
    }

    @Override
    public String toString() {
        return "PlanAmplifiedPattern{original=" + original + ", amp=" + amplification + "}";
    }
}

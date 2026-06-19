package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import ae2.api.crafting.IPatternDetails;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.AEKey;
import ae2.api.stacks.GenericStack;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 智能样板展开后的单个配方详情。
 * 实现 {@link IPatternDetails}，使 AE2 网络将其视为独立的普通样板。
 *
 * <p>关键设计点：</p>
 * <ul>
 *   <li>{@link #getDefinition()} 返回 parent 智能样板 ItemStack（用于 identity 验证）</li>
 *   <li>{@link #equals(Object)} / {@link #hashCode()} 基于配方内容，确保 Set 中不重复</li>
 *   <li>processing 配方：不提供合成网格验证</li>
 * </ul>
 */
public class SmartPatternSubDetails implements IPatternDetails {

    private final ItemStack parentPattern;
    private final SmartRecipe recipe;
    private final IInput[] inputs;

    public SmartPatternSubDetails(@Nonnull ItemStack parentPattern, @Nonnull SmartRecipe recipe) {
        this.parentPattern = parentPattern;
        this.recipe = recipe;
        this.inputs = buildInputs(recipe.getInputs());
    }

    @Override
    @Nonnull
    public AEItemKey getDefinition() {
        return AEItemKey.of(parentPattern);
    }

    @Override
    @Nonnull
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    @Nonnull
    public List<GenericStack> getOutputs() {
        List<GenericStack> result = new ArrayList<>();
        for (GenericStack output : recipe.getOutputs()) {
            if (output != null && output.amount() > 0) {
                result.add(output);
            }
        }
        return result;
    }

    @Nonnull
    public SmartRecipe getRecipe() {
        return recipe;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SmartPatternSubDetails)) return false;
        SmartPatternSubDetails other = (SmartPatternSubDetails) obj;
        return Arrays.equals(recipe.getInputs(), other.recipe.getInputs())
            && Arrays.equals(recipe.getOutputs(), other.recipe.getOutputs());
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(recipe.getInputs());
        result = 31 * result + Arrays.hashCode(recipe.getOutputs());
        return result;
    }

    @Nonnull
    private static IInput[] buildInputs(@Nonnull GenericStack[] stacks) {
        List<IInput> result = new ArrayList<>();
        for (GenericStack stack : stacks) {
            if (stack != null && stack.amount() > 0) {
                result.add(new SmartPatternInput(stack));
            }
        }
        return result.toArray(new IInput[0]);
    }

    /**
     * 单个输入槽位的 IPatternDetails.IInput 实现。
     */
    private static final class SmartPatternInput implements IInput {

        private final GenericStack template;

        SmartPatternInput(@Nonnull GenericStack template) {
            this.template = template;
        }

        @Override
        @Nonnull
        public GenericStack[] possibleInputs() {
            return new GenericStack[] { template };
        }

        @Override
        public long getMultiplier() {
            return template.amount();
        }

        @Override
        public boolean isValid(@Nonnull AEKey input, @Nonnull World world) {
            return template.what().equals(input);
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(@Nonnull AEKey input) {
            return null;
        }
    }
}

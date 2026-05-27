package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 单个配方的数据结构，可被序列化为 NBT。
 * 用于智能样板接口的配方聚合与虚拟展开。
 */
public class SmartRecipe {

    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private final boolean isCrafting;

    public SmartRecipe(@Nonnull IAEItemStack[] inputs, @Nonnull IAEItemStack[] outputs, boolean isCrafting) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.isCrafting = isCrafting;
    }

    @Nonnull
    public IAEItemStack[] getInputs() {
        return inputs;
    }

    @Nonnull
    public IAEItemStack[] getOutputs() {
        return outputs;
    }

    public boolean isCrafting() {
        return isCrafting;
    }

    /**
     * 获取主要输出（第一个非空输出）。
     */
    @Nullable
    public IAEItemStack getPrimaryOutput() {
        for (IAEItemStack output : outputs) {
            if (output != null && output.getStackSize() > 0) {
                return output;
            }
        }
        return null;
    }

    public void setInput(int index, @Nullable IAEItemStack stack) {
        if (index >= 0 && index < inputs.length) {
            inputs[index] = stack;
        }
    }

    public void setOutput(int index, @Nullable IAEItemStack stack) {
        if (index >= 0 && index < outputs.length) {
            outputs[index] = stack;
        }
    }

    /**
     * 只保留主输出，清空其他输出。
     */
    public void keepPrimary() {
        IAEItemStack primary = getPrimaryOutput();
        for (int i = 0; i < outputs.length; i++) {
            outputs[i] = null;
        }
        if (primary != null && outputs.length > 0) {
            outputs[0] = primary.copy();
            outputs[0].setStackSize(primary.getStackSize());
        }
    }

    /**
     * 所有输入输出数量翻倍，上限 Integer.MAX_VALUE。
     */
    public void doubleAmounts() {
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != null) {
                long newSize = inputs[i].getStackSize() * 2L;
                inputs[i].setStackSize(newSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : newSize);
            }
        }
        for (int i = 0; i < outputs.length; i++) {
            if (outputs[i] != null) {
                long newSize = outputs[i].getStackSize() * 2L;
                outputs[i].setStackSize(newSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : newSize);
            }
        }
    }

    /**
     * 序列化为 NBT。
     */
    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("inputs", writeStackArray(inputs));
        tag.setTag("outputs", writeStackArray(outputs));
        tag.setBoolean("crafting", isCrafting);
        return tag;
    }

    /**
     * 从 NBT 反序列化。
     */
    public static SmartRecipe fromNBT(NBTTagCompound tag) {
        IAEItemStack[] inputs = readStackArray(tag.getTagList("inputs", 10));
        IAEItemStack[] outputs = readStackArray(tag.getTagList("outputs", 10));
        boolean isCrafting = tag.getBoolean("crafting");
        return new SmartRecipe(inputs, outputs, isCrafting);
    }

    private static NBTTagList writeStackArray(IAEItemStack[] stacks) {
        NBTTagList list = new NBTTagList();
        for (IAEItemStack stack : stacks) {
            if (stack != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                ((AEItemStack) stack).writeToNBT(itemTag);
                list.appendTag(itemTag);
            } else {
                list.appendTag(new NBTTagCompound());
            }
        }
        return list;
    }

    private static IAEItemStack[] readStackArray(NBTTagList list) {
        IAEItemStack[] stacks = new IAEItemStack[list.tagCount()];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(i);
            if (itemTag.getKeySet().isEmpty()) {
                stacks[i] = null;
            } else {
                stacks[i] = AEItemStack.fromNBT(itemTag);
            }
        }
        return stacks;
    }
}

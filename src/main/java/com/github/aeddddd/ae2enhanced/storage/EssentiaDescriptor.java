package com.github.aeddddd.ae2enhanced.storage;

import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

/**
 * 源质描述符，作为 Map 的 Key。基于 Thaumcraft Aspect tag。
 */
public class EssentiaDescriptor implements Descriptor {
    private final String aspectTag;
    private final int hash;
    private transient volatile IAEEssentiaStack aeTemplate = null;

    public EssentiaDescriptor(IAEEssentiaStack stack) {
        this.aspectTag = stack.getAspect() != null ? stack.getAspect().getTag() : "unknown";
        this.hash = aspectTag.hashCode();
    }

    public EssentiaDescriptor(String aspectTag) {
        this.aspectTag = aspectTag;
        this.hash = aspectTag.hashCode();
    }

    /**
     * 根据存储的 aspectTag 重建 IAEEssentiaStack 模板（stackSize=1）。
     */
    public IAEEssentiaStack getAETemplate() {
        IAEEssentiaStack result = aeTemplate;
        if (result == null) {
            synchronized (this) {
                result = aeTemplate;
                if (result == null) {
                    EssentiaStack stack = new EssentiaStack(aspectTag, 1);
                    result = aeTemplate = AEEssentiaStack.fromEssentiaStack(stack);
                }
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EssentiaDescriptor)) return false;
        return Objects.equals(aspectTag, ((EssentiaDescriptor) obj).aspectTag);
    }

    public static EssentiaDescriptor fromNBT(NBTTagCompound tag) {
        String aspectTag = tag.getString("aspectTag");
        if (aspectTag.isEmpty()) return null;
        return new EssentiaDescriptor(aspectTag);
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("aspectTag", aspectTag);
        return tag;
    }

    public String getAspectTag() {
        return aspectTag;
    }
}

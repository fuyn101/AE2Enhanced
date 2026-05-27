package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;

/**
 * 智能样板的聚合配方数据。
 * 包含全部配方列表、冲突掩码、禁用掩码等元数据。
 * 实际持久化由 {@link SmartPatternStorageFile} 管理。
 */
public class SmartPatternData {

    private final UUID patternDataId;
    private final String targetBlockId;
    private final List<SmartRecipe> recipes;
    private final BitSet conflictMask;
    private BitSet disabledMask;
    private final long createdAt;

    public SmartPatternData(@Nonnull UUID patternDataId, @Nonnull String targetBlockId,
                            @Nonnull List<SmartRecipe> recipes) {
        this.patternDataId = patternDataId;
        this.targetBlockId = targetBlockId;
        this.recipes = new ArrayList<>(recipes);
        this.conflictMask = new BitSet(recipes.size());
        this.disabledMask = new BitSet(recipes.size());
        this.createdAt = System.currentTimeMillis();
    }

    private SmartPatternData(@Nonnull UUID patternDataId, @Nonnull String targetBlockId,
                             @Nonnull List<SmartRecipe> recipes,
                             @Nonnull BitSet conflictMask, @Nonnull BitSet disabledMask, long createdAt) {
        this.patternDataId = patternDataId;
        this.targetBlockId = targetBlockId;
        this.recipes = recipes;
        this.conflictMask = conflictMask;
        this.disabledMask = disabledMask;
        this.createdAt = createdAt;
    }

    @Nonnull
    public UUID getPatternDataId() {
        return patternDataId;
    }

    @Nonnull
    public String getTargetBlockId() {
        return targetBlockId;
    }

    @Nonnull
    public List<SmartRecipe> getRecipes() {
        return recipes;
    }

    public int getRecipeCount() {
        return recipes.size();
    }

    @Nonnull
    public BitSet getConflictMask() {
        return conflictMask;
    }

    @Nonnull
    public BitSet getDisabledMask() {
        return disabledMask;
    }

    public void setDisabledMask(@Nonnull BitSet disabledMask) {
        this.disabledMask = disabledMask;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 检测冲突：若两个配方的主要输出相同，则标记为冲突。
     * 冲突配方在 GUI 中置顶显示，且禁止编码。
     */
    public void detectConflicts() {
        conflictMask.clear();
        for (int i = 0; i < recipes.size(); i++) {
            IAEItemStack primaryA = recipes.get(i).getPrimaryOutput();
            if (primaryA == null) continue;
            for (int j = i + 1; j < recipes.size(); j++) {
                IAEItemStack primaryB = recipes.get(j).getPrimaryOutput();
                if (primaryB != null && primaryA.equals(primaryB)) {
                    conflictMask.set(i);
                    conflictMask.set(j);
                }
            }
        }
    }

    /**
     * 检查是否存在冲突。
     */
    public boolean hasConflicts() {
        return !conflictMask.isEmpty();
    }

    /**
     * 获取指定索引的配方是否被禁用。
     */
    public boolean isDisabled(int index) {
        return index >= 0 && index < recipes.size() && disabledMask.get(index);
    }

    /**
     * 获取启用的配方数量（用于显示）。
     */
    public int getEnabledCount() {
        return recipes.size() - disabledMask.cardinality();
    }

    /**
     * 将 BitSet 序列化为 Base64 字符串（用于压缩 NBT）。
     */
    @Nonnull
    public static String bitSetToBase64(@Nonnull BitSet bitSet) {
        byte[] bytes = bitSet.toByteArray();
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 从 Base64 字符串反序列化 BitSet。
     */
    @Nonnull
    public static BitSet bitSetFromBase64(@Nonnull String base64) {
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            return BitSet.valueOf(bytes);
        } catch (IllegalArgumentException e) {
            return new BitSet();
        }
    }

    /**
     * 序列化为 NBT。
     */
    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("patternDataId", patternDataId);
        tag.setString("targetBlockId", targetBlockId);
        tag.setLong("createdAt", createdAt);

        NBTTagList recipeList = new NBTTagList();
        for (SmartRecipe recipe : recipes) {
            recipeList.appendTag(recipe.toNBT());
        }
        tag.setTag("recipes", recipeList);
        tag.setString("conflictMask", bitSetToBase64(conflictMask));
        tag.setString("disabledMask", bitSetToBase64(disabledMask));

        return tag;
    }

    /**
     * 从 NBT 反序列化。
     */
    @Nullable
    public static SmartPatternData fromNBT(NBTTagCompound tag) {
        try {
            UUID patternDataId = tag.getUniqueId("patternDataId");
            String targetBlockId = tag.getString("targetBlockId");
            long createdAt = tag.getLong("createdAt");

            NBTTagList recipeList = tag.getTagList("recipes", 10);
            List<SmartRecipe> recipes = new ArrayList<>();
            for (int i = 0; i < recipeList.tagCount(); i++) {
                recipes.add(SmartRecipe.fromNBT(recipeList.getCompoundTagAt(i)));
            }

            BitSet conflictMask = bitSetFromBase64(tag.getString("conflictMask"));
            BitSet disabledMask = bitSetFromBase64(tag.getString("disabledMask"));

            return new SmartPatternData(patternDataId, targetBlockId, recipes, conflictMask, disabledMask, createdAt);
        } catch (Exception e) {
            return null;
        }
    }
}

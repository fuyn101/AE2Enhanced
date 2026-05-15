package com.github.aeddddd.ae2enhanced.crafting;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 黑洞合成配方注册表。
 */
public class BlackHoleRecipeRegistry {

    private static final List<BlackHoleRecipe> RECIPES = new ArrayList<>();

    /** 延迟移除队列：CraftTweaker 脚本可能在配方注册前执行 */
    private static final Set<String> PENDING_REMOVALS = new HashSet<>();

    public static void register(BlackHoleRecipe recipe) {
        RECIPES.add(recipe);
    }

    /**
     * 在 found 中寻找第一个匹配的配方。
     */
    public static BlackHoleRecipe findMatching(Map<String, Integer> found) {
        for (BlackHoleRecipe recipe : RECIPES) {
            if (recipe.matches(found)) {
                return recipe;
            }
        }
        return null;
    }

    public static boolean removeById(String id) {
        return RECIPES.removeIf(r -> r.getId().equals(id));
    }

    public static List<BlackHoleRecipe> getRecipes() {
        return new ArrayList<>(RECIPES);
    }

    public static void queueRemoval(String id) {
        PENDING_REMOVALS.add(id);
    }

    public static void applyPendingRemovals() {
        if (PENDING_REMOVALS.isEmpty()) return;
        for (String id : new HashSet<>(PENDING_REMOVALS)) {
            removeById(id);
        }
        PENDING_REMOVALS.clear();
    }
}

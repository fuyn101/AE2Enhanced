package com.github.aeddddd.ae2enhanced.crafting;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 黑洞合成配方注册表.
 */
public class SingularityRecipeRegistry {

    private static final List<SingularityRecipe> RECIPES = new CopyOnWriteArrayList<>();

    public static void register(SingularityRecipe recipe) {
        RECIPES.add(recipe);
    }

    /**
     * 在以 center 为中心的 5×5×5 区域内寻找第一个匹配的配方.
     */
    public static SingularityRecipe findMatching(World world, BlockPos center) {
        for (SingularityRecipe recipe : RECIPES) {
            if (recipe.matches(world, center)) {
                return recipe;
            }
        }
        return null;
    }

    public static List<SingularityRecipe> getRecipes() {
        return new java.util.ArrayList<>(RECIPES);
    }
}

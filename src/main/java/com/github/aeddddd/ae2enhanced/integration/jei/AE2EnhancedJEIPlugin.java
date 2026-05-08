package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientBlacklist;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 插件：注册黑洞合成配方类别与配方显示。
 */
@JEIPlugin
public class AE2EnhancedJEIPlugin implements IModPlugin {

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        registry.addRecipeCategories(new BlackHoleRecipeCategory(registry.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void register(IModRegistry registry) {
        IIngredientRegistry ingredientRegistry = registry.getIngredientRegistry();
        IIngredientBlacklist blacklist = registry.getJeiHelpers().getIngredientBlacklist();

        // E2a：将假物品加入 JEI 黑名单，避免在物品列表中显示
        if (ModItems.ESSENTIA_DROP != null) {
            int aspectCount = ItemEssentiaDrop.getAspectCount();
            for (int meta = 0; meta < aspectCount; meta++) {
                blacklist.addIngredientToBlacklist(new ItemStack(ModItems.ESSENTIA_DROP, 1, meta));
            }
            AE2Enhanced.LOGGER.info("[AE2E-JEI] Hidden {} essentia drop variants from JEI", aspectCount);
        }
        if (ModItems.FLUID_DROP != null) {
            // 隐藏基础流体假物品（getSubItems 已返回空，黑名单确保基础物品也不显示）
            blacklist.addIngredientToBlacklist(new ItemStack(ModItems.FLUID_DROP));
            AE2Enhanced.LOGGER.info("[AE2E-JEI] Hidden fluid drop from JEI");
        }
        if (ModItems.GAS_DROP != null) {
            blacklist.addIngredientToBlacklist(new ItemStack(ModItems.GAS_DROP));
            AE2Enhanced.LOGGER.info("[AE2E-JEI] Hidden gas drop from JEI");
        }

        // 必须将 BlackHoleRecipe 包装为 BlackHoleRecipeWrapper，与 IRecipeCategory 的泛型匹配
        List<BlackHoleRecipeWrapper> wrappers = new ArrayList<>();
        for (BlackHoleRecipe recipe : BlackHoleRecipeRegistry.getRecipes()) {
            wrappers.add(new BlackHoleRecipeWrapper(recipe));
        }
        registry.addRecipes(wrappers, BlackHoleRecipeCategory.UID);
        AE2Enhanced.LOGGER.info("JEI 集成已注册，黑洞合成配方数量: {}", wrappers.size());
    }
}

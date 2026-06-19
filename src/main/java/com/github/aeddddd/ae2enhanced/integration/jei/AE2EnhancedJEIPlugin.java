package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 插件：注册黑洞合成配方类别与配方显示。
 */
@JEIPlugin
public class AE2EnhancedJEIPlugin implements IModPlugin {

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JEISearchKeyHandler.setJeiRuntime(jeiRuntime);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        registry.addRecipeCategories(new BlackHoleRecipeCategory(registry.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void register(IModRegistry registry) {
        // 必须将 BlackHoleRecipe 包装为 BlackHoleRecipeWrapper,与 IRecipeCategory 的泛型匹配
        List<BlackHoleRecipeWrapper> wrappers = new ArrayList<>();
        for (BlackHoleRecipe recipe : BlackHoleRecipeRegistry.getRecipes()) {
            wrappers.add(new BlackHoleRecipeWrapper(recipe));
        }
        registry.addRecipes(wrappers, BlackHoleRecipeCategory.UID);

        // Smart Pattern Interface MiniGUI ghost ingredient drag support
        registry.addGhostIngredientHandler(
                com.github.aeddddd.ae2enhanced.client.gui.GuiSmartPatternInterface.class,
                new com.github.aeddddd.ae2enhanced.integration.jei.SmartPatternInterfaceGhostHandler());

    }
}

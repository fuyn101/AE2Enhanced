package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JEI 黑洞合成配方包装器.
 */
public class BlackHoleRecipeWrapper implements IRecipeWrapper {

    private final BlackHoleRecipe recipe;

    public BlackHoleRecipeWrapper(BlackHoleRecipe recipe) {
        this.recipe = recipe;
    }

    public BlackHoleRecipe getRecipe() {
        return recipe;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : recipe.getInputs().entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue();
            // 解析 "registryName:meta" 格式
            int lastColon = key.lastIndexOf(':');
            if (lastColon <= 0) continue;
            String registryName = key.substring(0, lastColon);
            int meta;
            try {
                meta = Integer.parseInt(key.substring(lastColon + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            net.minecraft.item.Item item = net.minecraft.item.Item.REGISTRY.getObject(new ResourceLocation(registryName));
            if (item == null) continue;
            List<ItemStack> subList = new ArrayList<>();
            subList.add(new ItemStack(item, count, meta));
            inputs.add(subList);
        }
        ingredients.setInputLists(ItemStack.class, inputs);
        ingredients.setOutput(ItemStack.class, recipe.getOutput());
    }
}

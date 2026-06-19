package com.github.aeddddd.ae2enhanced.integration.jei;

import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.JEISearchKeyHandler;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartRecipe;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.ingredients.Ingredients;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JEI/HEI 配方查询助手。
 *
 * <p>通过 JEI 的 RecipeRegistry 查询目标方块对应的所有配方。</p>
 * <p>查询策略：</p>
 * <ol>
 *   <li><b>催化剂匹配</b>：遍历所有 RecipeCategory，检查其催化剂列表中是否包含目标方块</li>
 *   <li><b>IFocus 回退</b>：创建 OUTPUT 焦点，查询与目标方块作为输出相关的类别</li>
 * </ol>
 *
 * <p>AE2S 迁移：移除对不存在 ae2fc helper 的引用；输入输出改用 {@link GenericStack}。</p>
 *
 * <p>注意：JEI 是纯客户端模组，此类必须在客户端调用。</p>
 */
@SideOnly(Side.CLIENT)
public class JEIRecipeHelper {

    /**
     * 检查 JEI/HEI 是否可用。
     */
    public static boolean isJeiAvailable() {
        return JEISearchKeyHandler.getJeiRuntime() != null;
    }

    /**
     * 判断目标方块是否在黑名单中。
     */
    public static boolean isBlacklisted(@Nonnull String blockId) {
        String clean = blockId.contains("@") ? blockId.substring(0, blockId.indexOf('@')) : blockId;
        for (String entry : AE2EnhancedConfig.smartPattern.blacklist) {
            if (clean.equals(entry) || blockId.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查询与目标方块相关的所有配方，并转换为 SmartRecipe 列表。
     *
     * @param blockRegistryName 目标方块的注册名（可能带有 @meta 后缀）
     * @return 转换后的 SmartRecipe 列表，若 JEI 不可用或找不到配方则返回空列表
     */
    @Nonnull
    public static List<SmartRecipe> getRecipesForBlock(@Nonnull String blockRegistryName) {
        if (!isJeiAvailable()) {
            return Collections.emptyList();
        }

        IJeiRuntime runtime = JEISearchKeyHandler.getJeiRuntime();
        if (runtime == null) {
            return Collections.emptyList();
        }
        IRecipeRegistry registry = runtime.getRecipeRegistry();
        if (registry == null) {
            return Collections.emptyList();
        }

        // 去掉 @meta 后缀，获取纯净 registryName
        String cleanBlockId = blockRegistryName.contains("@")
                ? blockRegistryName.substring(0, blockRegistryName.indexOf('@'))
                : blockRegistryName;
        ResourceLocation blockRl = new ResourceLocation(cleanBlockId);

        // 尝试从注册表获取目标方块对应的 ItemStack（用于催化剂匹配）
        ItemStack targetStack = ItemStack.EMPTY;
        if (ForgeRegistries.BLOCKS.containsKey(blockRl)) {
            net.minecraft.block.Block block = ForgeRegistries.BLOCKS.getValue(blockRl);
            if (block != null) {
                targetStack = new ItemStack(block, 1);
            }
        }

        List<SmartRecipe> result = new ArrayList<>();

        // 1. 催化剂匹配：遍历所有 RecipeCategory
        try {
            for (IRecipeCategory<?> category : registry.getRecipeCategories()) {
                if (category == null) continue;
                List<Object> catalysts = registry.getRecipeCatalysts(category);
                if (catalysts == null) continue;
                boolean matched = false;
                for (Object catalyst : catalysts) {
                    if (catalyst instanceof ItemStack) {
                        ItemStack cs = (ItemStack) catalyst;
                        if (!cs.isEmpty() && cs.getItem() == targetStack.getItem()) {
                            matched = true;
                            break;
                        }
                    }
                }
                if (matched) {
                    addRecipesFromCategory(registry, category, result);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] SmartPattern catalyst scan failed for {}.", blockRegistryName, e);
        }

        // 2. IFocus 回退：以目标方块作为 OUTPUT 焦点
        if (result.isEmpty() && !targetStack.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                IFocus<ItemStack> focus = registry.createFocus(IFocus.Mode.OUTPUT, targetStack);
                for (IRecipeCategory<?> category : registry.getRecipeCategories(focus)) {
                    addRecipesFromCategory(registry, category, result);
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] SmartPattern focus scan failed for {}.", blockRegistryName, e);
            }
        }

        return result;
    }

    private static <T extends IRecipeWrapper> void addRecipesFromCategory(
            @Nonnull IRecipeRegistry registry,
            @Nonnull IRecipeCategory<T> category,
            @Nonnull List<SmartRecipe> result) {
        try {
            List<T> wrappers = registry.getRecipeWrappers(category);
            if (wrappers == null) return;
            for (T wrapper : wrappers) {
                SmartRecipe recipe = convertWrapper(wrapper, category);
                if (recipe != null) {
                    result.add(recipe);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to collect recipes from category: {}",
                    category.getUid(), e);
        }
    }

    /**
     * 将 JEI 的 IRecipeWrapper 转换为 SmartRecipe。
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static SmartRecipe convertWrapper(@Nonnull IRecipeWrapper wrapper, @Nonnull IRecipeCategory category) {
        try {
            Ingredients ingredients = new Ingredients();
            wrapper.getIngredients(ingredients);

            List<List<ItemStack>> inputLists = ingredients.getInputs(VanillaTypes.ITEM);
            List<List<ItemStack>> outputLists = ingredients.getOutputs(VanillaTypes.ITEM);

            // 收集所有输入（仅物品）
            List<GenericStack> inputList = new ArrayList<>();
            for (List<ItemStack> slotInputs : inputLists) {
                if (!slotInputs.isEmpty()) {
                    ItemStack is = slotInputs.get(0);
                    if (!is.isEmpty()) {
                        inputList.add(GenericStack.fromItemStack(is));
                    }
                }
            }

            // 收集所有输出（仅物品），支持多输出
            List<GenericStack> outputList = new ArrayList<>();
            for (List<ItemStack> slotOutputs : outputLists) {
                if (!slotOutputs.isEmpty()) {
                    ItemStack is = slotOutputs.get(0);
                    if (!is.isEmpty()) {
                        outputList.add(GenericStack.fromItemStack(is));
                    }
                }
            }

            GenericStack[] inputs = inputList.toArray(new GenericStack[0]);
            GenericStack[] outputs = outputList.toArray(new GenericStack[0]);

            // 智能样板统一作为 processing 配方处理，不区分 crafting/processing
            return new SmartRecipe(inputs, outputs, false);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to convert JEI recipe wrapper for category: {}",
                    category.getUid(), e);
            return null;
        }
    }
}

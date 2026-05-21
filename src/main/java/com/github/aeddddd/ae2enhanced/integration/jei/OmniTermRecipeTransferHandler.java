package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.network.PacketLoadOmniRecipe;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Omni Terminal JEI 配方快速转移 Handler
 *
 * 行为：
 * - 默认（无 modifier）：crafting 配方同时填充左 3×3 crafting grid 和右编码区；处理配方只填充编码区
 * - Shift（maxTransfer=true）：只填充右编码区
 * - Alt（Keyboard.KEY_LMENU）：只填充左 3×3 crafting grid（仅 crafting 配方有效）
 */
public class OmniTermRecipeTransferHandler implements IRecipeTransferHandler<ContainerOmniTerm> {

    @Override
    public Class<ContainerOmniTerm> getContainerClass() {
        return ContainerOmniTerm.class;
    }

    @Override
    public IRecipeTransferError transferRecipe(ContainerOmniTerm container, IRecipeLayout recipeLayout, EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) {
            return null;
        }

        boolean isCrafting = recipeLayout.getRecipeCategory().getUid().equals("minecraft.crafting");

        List<ItemStack> inputs = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();

        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients = recipeLayout.getItemStacks().getGuiIngredients();
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : ingredients.entrySet()) {
            IGuiIngredient<ItemStack> ing = entry.getValue();
            ItemStack displayed = ing.getDisplayedIngredient();
            if (displayed == null || displayed.isEmpty()) {
                continue;
            }
            if (ing.isInput()) {
                inputs.add(displayed.copy());
            } else {
                outputs.add(displayed.copy());
            }
        }

        if (inputs.isEmpty()) {
            return null;
        }

        byte mode;
        if (maxTransfer) {
            mode = 1; // Shift: 只到编码区
        } else if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            mode = 2; // Alt: 只到合成区
        } else {
            mode = 0; // 默认: 两边
        }

        // 处理配方没有合成区，Alt 模式回退到默认
        if (!isCrafting && mode == 2) {
            mode = 0;
        }

        AE2Enhanced.network.sendToServer(new PacketLoadOmniRecipe(mode, isCrafting, inputs, outputs));
        return null;
    }
}

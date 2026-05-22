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

import java.util.HashMap;
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
        boolean isCrafting = recipeLayout.getRecipeCategory().getUid().equals("minecraft.crafting");

        // 使用 Map 保留 JEI slot index，确保空位不被忽略
        Map<Integer, ItemStack> inputs = new HashMap<>();
        Map<Integer, ItemStack> outputs = new HashMap<>();

        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients = recipeLayout.getItemStacks().getGuiIngredients();
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : ingredients.entrySet()) {
            int slotIndex = entry.getKey();
            IGuiIngredient<ItemStack> ing = entry.getValue();
            ItemStack displayed = ing.getDisplayedIngredient();
            if (displayed == null || displayed.isEmpty()) {
                continue;
            }
            if (ing.isInput()) {
                inputs.put(slotIndex, displayed.copy());
            } else {
                outputs.put(slotIndex, displayed.copy());
            }
        }

        if (inputs.isEmpty()) {
            return null;
        }

        if (!doTransfer) {
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

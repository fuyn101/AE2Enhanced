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
import java.util.Comparator;
import java.util.HashMap;
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
        String recipeUid = recipeLayout.getRecipeCategory().getUid();
        boolean isCrafting = recipeUid.equals("minecraft.crafting");
        int gridSize = 3; // default 3x3

        // 识别 Extended Crafting 工作台配方
        if (recipeUid.startsWith("jei.ec.table_crafting_")) {
            isCrafting = true;
            try {
                String suffix = recipeUid.substring("jei.ec.table_crafting_".length());
                gridSize = Integer.parseInt(suffix.substring(0, suffix.indexOf('x')));
            } catch (Exception ignored) {}
        }

        // 收集 JEI ingredients，按 key 排序以保留顺序
        List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> sortedIngredients = new ArrayList<>();
        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients = recipeLayout.getItemStacks().getGuiIngredients();
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : ingredients.entrySet()) {
            sortedIngredients.add(entry);
        }
        sortedIngredients.sort(Comparator.comparingInt(Map.Entry::getKey));

        // 使用 Map 保留 JEI slot index
        Map<Integer, ItemStack> inputs = new HashMap<>();
        Map<Integer, ItemStack> outputs = new HashMap<>();

        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : sortedIngredients) {
            int slotIndex = entry.getKey();
            IGuiIngredient<ItemStack> ing = entry.getValue();

            // 优先取 displayed，fallback 到 allIngredients 的第一个非空物品
            ItemStack toUse = getFirstNonEmpty(ing);
            if (toUse == null || toUse.isEmpty()) {
                continue;
            }

            if (ing.isInput()) {
                inputs.put(slotIndex, toUse.copy());
            } else {
                outputs.put(slotIndex, toUse.copy());
            }
        }

        if (inputs.isEmpty()) {
            return null;
        }

        if (!doTransfer) {
            return null;
        }

        // Crafting recipe: 检测并修正 key 偏移（某些 recipe category 的 output 占用了 key 0，inputs 从 1 开始）
        // 只有当 output 明确占用了 key 0 时才偏移，避免将 "第一个 input slot 为空" 误判为偏移
        if (isCrafting && outputs.containsKey(0)) {
            Map<Integer, ItemStack> shifted = new HashMap<>();
            for (Map.Entry<Integer, ItemStack> entry : inputs.entrySet()) {
                shifted.put(entry.getKey() - 1, entry.getValue());
            }
            inputs = shifted;
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

        AE2Enhanced.network.sendToServer(new PacketLoadOmniRecipe(mode, isCrafting, gridSize, inputs, outputs));
        return null;
    }

    /**
     * 从 IGuiIngredient 中获取第一个非空 ItemStack。
     * 优先使用 getDisplayedIngredient()，如果为空则尝试 getAllIngredients()。
     */
    private static ItemStack getFirstNonEmpty(IGuiIngredient<ItemStack> ing) {
        ItemStack displayed = ing.getDisplayedIngredient();
        if (displayed != null && !displayed.isEmpty()) {
            return displayed;
        }
        List<ItemStack> all = ing.getAllIngredients();
        if (all != null) {
            for (ItemStack stack : all) {
                if (stack != null && !stack.isEmpty()) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}

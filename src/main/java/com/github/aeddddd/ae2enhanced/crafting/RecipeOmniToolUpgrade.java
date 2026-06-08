package com.github.aeddddd.ae2enhanced.crafting;

import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapelessOreRecipe;

public class RecipeOmniToolUpgrade extends ShapelessOreRecipe {

    private final String upgradeType;

    public RecipeOmniToolUpgrade(ResourceLocation group, ItemStack result, String upgradeType, Object... input) {
        super(group, result, input);
        this.upgradeType = upgradeType;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        ItemStack omniTool = ItemStack.EMPTY;
        ItemStack upgradeItem = ItemStack.EMPTY;
        int nonEmptyCount = 0;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            nonEmptyCount++;

            if (stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                if (!omniTool.isEmpty()) return false;
                omniTool = stack;
            } else if (matchesUpgradeItem(stack)) {
                if (!upgradeItem.isEmpty()) return false;
                upgradeItem = stack;
            } else {
                return false;
            }
        }

        if (nonEmptyCount != 2 || omniTool.isEmpty() || upgradeItem.isEmpty()) {
            return false;
        }

        // 防止重复升级
        if ("chaos".equals(upgradeType) && ItemAdvancedMEOmniTool.hasChaosCore(omniTool)) {
            return false;
        }
        if ("fortune".equals(upgradeType) && ItemAdvancedMEOmniTool.getFortuneLevel(omniTool) > 0) {
            return false;
        }

        return true;
    }

    private boolean matchesUpgradeItem(ItemStack stack) {
        if ("chaos".equals(upgradeType)) {
            return stack.getItem().getRegistryName() != null
                    && "draconicevolution:chaotic_core".equals(stack.getItem().getRegistryName().toString());
        } else if ("fortune".equals(upgradeType)) {
            if (stack.getItem() != Items.ENCHANTED_BOOK) return false;
            return getFortuneLevelFromBook(stack) > 0;
        }
        return false;
    }

    private static int getFortuneLevelFromBook(ItemStack stack) {
        if (!stack.hasTagCompound()) return 0;
        NBTTagList list = stack.getTagCompound().getTagList("StoredEnchantments", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Enchantment ench = Enchantment.getEnchantmentByID(tag.getShort("id"));
            if (ench != null && ench.getRegistryName() != null
                    && "minecraft".equals(ench.getRegistryName().getNamespace())
                    && "fortune".equals(ench.getRegistryName().getPath())) {
                return tag.getShort("lvl");
            }
        }
        return 0;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack omniTool = ItemStack.EMPTY;
        ItemStack book = ItemStack.EMPTY;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.getItem() instanceof ItemAdvancedMEOmniTool) {
                omniTool = stack;
            } else if (stack.getItem() == Items.ENCHANTED_BOOK) {
                book = stack;
            }
        }
        if (omniTool.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = omniTool.copy();
        result.setCount(1);
        if ("chaos".equals(upgradeType)) {
            ItemAdvancedMEOmniTool.setChaosCore(result, true);
        } else if ("fortune".equals(upgradeType)) {
            int level = getFortuneLevelFromBook(book);
            ItemAdvancedMEOmniTool.setFortuneLevel(result, level > 0 ? level : 3);
        }
        return result;
    }
}

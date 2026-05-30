package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import net.minecraft.inventory.InventoryCrafting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = InventoryCrafting.class, remap = true)
public class MixinInventoryCrafting {

    @ModifyVariable(
        method = "<init>",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private int modifyWidth(int width) {
        if (width == 4 && MixinCraftingCPUCluster.EXPAND_CRAFTING_BUFFER.get()) {
            return 10;
        }
        return width;
    }

    @ModifyVariable(
        method = "<init>",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1
    )
    private int modifyHeight(int height) {
        if (height == 4 && MixinCraftingCPUCluster.EXPAND_CRAFTING_BUFFER.get()) {
            return 10;
        }
        return height;
    }
}

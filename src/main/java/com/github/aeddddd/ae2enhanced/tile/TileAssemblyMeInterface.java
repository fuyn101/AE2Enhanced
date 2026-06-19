package com.github.aeddddd.ae2enhanced.tile;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.crafting.ICraftingProvider;
import ae2.api.stacks.KeyCounter;
import net.minecraft.inventory.InventoryCrafting;

import java.util.Collections;
import java.util.List;

public class TileAssemblyMeInterface extends TileDelegatedProxyBase<TileAssemblyController> implements ICraftingProvider {

    @Override
    protected Class<TileAssemblyController> getControllerClass() {
        return TileAssemblyController.class;
    }

    @Override
    protected boolean isControllerFormed(TileAssemblyController controller) {
        return controller.isFormed();
    }

    // ICraftingProvider
    @Override
    public List<? extends IPatternDetails> getAvailablePatterns() {
        TileAssemblyController controller = getController();
        if (controller != null && controller.isMeInterfaceActive(pos)) {
            return controller.getAvailablePatterns();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, int multiplier) {
        TileAssemblyController controller = getController();
        if (controller != null) {
            return controller.pushPattern(patternDetails, inputs, multiplier);
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        TileAssemblyController controller = getController();
        if (controller != null) {
            return controller.isBusy();
        }
        return false;
    }

    @Override
    public boolean canMergePatternPush(IPatternDetails patternDetails) {
        TileAssemblyController controller = getController();
        if (controller != null) {
            return controller.canMergePatternPush(patternDetails);
        }
        return true;
    }

    @Override
    public int getMaxPatternPushMultiplier(IPatternDetails patternDetails, int multiplier) {
        TileAssemblyController controller = getController();
        if (controller != null) {
            return controller.getMaxPatternPushMultiplier(patternDetails, multiplier);
        }
        return multiplier;
    }
}

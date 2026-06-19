package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.client.gui.GuiSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.inventory.Slot;

import javax.annotation.Nonnull;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JEI Ghost Ingredient Handler for Smart Pattern Interface MiniGUI。
 *
 * <p>AE2S 迁移：原 {@code ae2.core.sync.packets.PacketInventoryAction} 与
 * {@code InventoryAction.PLACE_JEI_GHOST_ITEM} 已不存在。当前保留目标区域高亮，
 * 实际放置逻辑暂存根，避免引入大量自定义网络基础设施。</p>
 */
public class SmartPatternInterfaceGhostHandler implements IGhostIngredientHandler<GuiSmartPatternInterface> {

    @Nonnull
    @Override
    public <I> List<Target<I>> getTargets(@Nonnull GuiSmartPatternInterface gui, @Nonnull I ingredient, boolean doStart) {
        if (gui.getTile().getLockedRecipeIndex() < 0) {
            return Collections.emptyList();
        }

        if (!(ingredient instanceof net.minecraft.item.ItemStack)) {
            return Collections.emptyList();
        }

        ArrayList<Target<I>> targets = new ArrayList<>();
        int scrollOffset = gui.getTile().getMiniGuiScrollOffset();
        int slotStart = ContainerSmartPatternInterface.SLOT_MINIGUI_INPUT_START + scrollOffset * 9;
        int slotEnd = slotStart + 9;

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            int slotNumber = slot.slotNumber;
            if (slotNumber >= slotStart && slotNumber < slotEnd) {
                if (slot instanceof ae2.container.slot.FakeSlot) {
                    Target<I> target = new GhostIngredientTargetWrapper<>(gui.getGuiLeft(), gui.getGuiTop(), slot);
                    targets.add(target);
                }
            }
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

    /**
     * Wrapper around a fake slot that implements JEI's generic Target interface.
     */
    private static class GhostIngredientTargetWrapper<I> implements Target<I> {
        private final int guiLeft;
        private final int guiTop;
        private final Slot slot;

        GhostIngredientTargetWrapper(int guiLeft, int guiTop, Slot slot) {
            this.guiLeft = guiLeft;
            this.guiTop = guiTop;
            this.slot = slot;
        }

        @Nonnull
        @Override
        public Rectangle getArea() {
            return new Rectangle(this.guiLeft + this.slot.xPos, this.guiTop + this.slot.yPos, 16, 16);
        }

        @Override
        public void accept(@Nonnull I ingredient) {
            // AE2S 暂无等效 ghost item 网络包，暂存根。
            // 用户仍可通过手动点击 MiniGUI 槽位配置配方。
        }
    }
}

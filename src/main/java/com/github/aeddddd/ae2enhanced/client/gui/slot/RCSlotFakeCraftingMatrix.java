package com.github.aeddddd.ae2enhanced.client.gui.slot;

import appeng.container.slot.SlotFakeCraftingMatrix;
import net.minecraftforge.items.IItemHandler;

/**
 * 支持 visible 切换的假合成矩阵槽位（81槽位编码终端用）
 */
public class RCSlotFakeCraftingMatrix extends SlotFakeCraftingMatrix {

    public boolean visible = true;
    private final int defX;
    private final int defY;

    public RCSlotFakeCraftingMatrix(IItemHandler inv, int idx, int x, int y) {
        super(inv, idx, x, y);
        this.defX = x;
        this.defY = y;
    }

    @Override
    public boolean shouldDisplay() {
        return this.visible;
    }

    public int getDefX() {
        return this.defX;
    }

    public int getDefY() {
        return this.defY;
    }
}

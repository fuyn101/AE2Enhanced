package com.github.aeddddd.ae2enhanced.client.gui.slot;

import appeng.container.slot.AppEngSlot;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import com.github.aeddddd.ae2enhanced.item.ItemOmniUpgradeCard;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/**
 * Omni Terminal 右侧升级卡槽位 —— 只能放入频道接收卡和 Omni 专用升级卡。
 */
public class SlotOmniUpgrade extends AppEngSlot {

    public SlotOmniUpgrade(IItemHandler inv, int idx, int x, int y) {
        super(inv, idx, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack.getItem() instanceof ItemChannelReceiverCard
                || stack.getItem() instanceof ItemOmniUpgradeCard;
    }
}

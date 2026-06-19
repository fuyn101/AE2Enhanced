package com.github.aeddddd.ae2enhanced.item;

import ae2.container.GuiIds;
import ae2.helpers.WirelessCraftingTerminalGuiHost;
import ae2.items.tools.powered.WirelessTerminalItem;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.ItemStack;

/**
 * 全能无线终端 —— 基于 AE2S 标准无线合成终端实现。
 *
 * <p>移除了自定义物品同步、分页、搜索、81 槽编码区、右侧存储等优化层，
 * 直接复用 AE2S 的 {@link ae2.container.me.items.ContainerWirelessCraftingTerm}
 * 与 {@link ae2.client.gui.me.items.GuiCraftingTerm}。</p>
 */
public class ItemOmniWirelessTerminal extends WirelessTerminalItem {

    public ItemOmniWirelessTerminal() {
        super(
                ae2.core.AEConfig.instance().getWirelessTerminalBattery(),
                "omni_wireless_terminal",
                GuiIds.GuiKey.WIRELESS_CRAFTING_TERMINAL,
                item -> new ItemStack(item),
                WirelessCraftingTerminalGuiHost::new,
                "openOmniTerminal",
                2
        );
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setTranslationKey("ae2enhanced.omni_wireless_terminal");
        setRegistryName("omni_wireless_terminal");
    }
}

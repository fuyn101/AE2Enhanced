package com.github.aeddddd.ae2enhanced.registry.content;

import com.github.aeddddd.ae2enhanced.item.*;
import net.minecraft.item.Item;

/**
 * 物品注册表 —— 仅声明普通 Item 实例字段(不含 Part).
 */
public final class ItemRegistry {

    private ItemRegistry() {}

    public static ItemUpgradeCard UPGRADE_CARD;
    public static ItemConformalCharge CONFORMAL_CHARGE;
    public static ItemDifferentialFormStabilizer DIFFERENTIAL_FORM_STABILIZER;
    public static ItemStableSpacetimeManifold STABLE_SPACETIME_MANIFOLD;
    public static ItemChannelReceiverCard CHANNEL_RECEIVER_CARD;
    public static ItemUniversalMemoryCard UNIVERSAL_MEMORY_CARD;
    public static ItemOmniWirelessTerminal OMNI_WIRELESS_TERMINAL;
    public static ItemOmniUpgradeCard OMNI_UPGRADE_CARD;
    public static ItemSmartBlankPattern SMART_BLANK_PATTERN;
    public static ItemSmartPattern SMART_PATTERN;

    // 先进ME工具
    public static ItemAdvancedMEOmniTool ME_OMNI_TOOL;

    // ME 放置工具
    public static ItemMEPlacementTool ME_PLACEMENT_TOOL;
}

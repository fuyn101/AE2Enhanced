package com.github.aeddddd.ae2enhanced.registry;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketCraftRequestLong;
import com.github.aeddddd.ae2enhanced.network.packet.PacketMEMonitorableAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPatternPage;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRequestAssembly;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSetSlotAmount;
import com.github.aeddddd.ae2enhanced.network.packet.PacketStockingBusConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUniversalBusConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniTermAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketLoadOmniRecipe;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniCraftingUpdate;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniTerminal;
import com.github.aeddddd.ae2enhanced.network.packet.PacketToggleMagnet;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPickerAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternToggle;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternScroll;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternEncode;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternBind;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternModify;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternReplace;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlatformEnergySync;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlatformGenerateRequest;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlatformGenerateResult;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternMiniGuiScroll;
import com.github.aeddddd.ae2enhanced.network.packet.platform.PacketPlatformInit;
import com.github.aeddddd.ae2enhanced.network.packet.platform.PacketPlatformStatus;
import com.github.aeddddd.ae2enhanced.network.packet.platform.PacketSubnetAction;
import com.github.aeddddd.ae2enhanced.network.packet.platform.PacketZoneAction;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 网络包注册中心
 */
public final class ModNetwork {

    private ModNetwork() {}

    public static void init() {
        AE2Enhanced.network = new SimpleNetworkWrapper(AE2Enhanced.MOD_ID);
        SimpleNetworkWrapper nw = AE2Enhanced.network;

        nw.registerMessage(PacketRequestAssembly.Handler.class, PacketRequestAssembly.class, 0, Side.SERVER);
        nw.registerMessage(PacketPatternPage.Handler.class, PacketPatternPage.class, 1, Side.SERVER);
        nw.registerMessage(PacketCraftRequestLong.Handler.class, PacketCraftRequestLong.class, 2, Side.SERVER);
        nw.registerMessage(PacketMEMonitorableAction.Handler.class, PacketMEMonitorableAction.class, 3, Side.SERVER);
        nw.registerMessage(PacketUniversalBusConfig.Handler.class, PacketUniversalBusConfig.class, 4, Side.SERVER);
        nw.registerMessage(PacketStockingBusConfig.Handler.class, PacketStockingBusConfig.class, 5, Side.SERVER);
        nw.registerMessage(PacketUMCAction.Handler.class, PacketUMCAction.class, 6, Side.SERVER);
        nw.registerMessage(PacketOmniTermAction.Handler.class, PacketOmniTermAction.class, 7, Side.SERVER);
        nw.registerMessage(PacketLoadOmniRecipe.Handler.class, PacketLoadOmniRecipe.class, 8, Side.SERVER);
        nw.registerMessage(PacketSetSlotAmount.Handler.class, PacketSetSlotAmount.class, 9, Side.SERVER);
        nw.registerMessage(PacketOmniCraftingUpdate.Handler.class, PacketOmniCraftingUpdate.class, 10, Side.CLIENT);
        nw.registerMessage(PacketOpenOmniTerminal.Handler.class, PacketOpenOmniTerminal.class, 11, Side.SERVER);
        nw.registerMessage(PacketToggleMagnet.Handler.class, PacketToggleMagnet.class, 12, Side.SERVER);
        nw.registerMessage(PacketPickerAction.Handler.class, PacketPickerAction.class, 13, Side.SERVER);
        nw.registerMessage(PacketSmartPatternToggle.Handler.class, PacketSmartPatternToggle.class, 14, Side.SERVER);
        nw.registerMessage(PacketSmartPatternScroll.Handler.class, PacketSmartPatternScroll.class, 15, Side.SERVER);
        nw.registerMessage(PacketSmartPatternEncode.Handler.class, PacketSmartPatternEncode.class, 16, Side.SERVER);
        nw.registerMessage(PacketSmartPatternBind.Handler.class, PacketSmartPatternBind.class, 17, Side.SERVER);
        nw.registerMessage(PacketSmartPatternModify.Handler.class, PacketSmartPatternModify.class, 18, Side.SERVER);
        nw.registerMessage(PacketSmartPatternReplace.Handler.class, PacketSmartPatternReplace.class, 19, Side.SERVER);
        nw.registerMessage(PacketSmartPatternMiniGuiScroll.Handler.class, PacketSmartPatternMiniGuiScroll.class, 20, Side.SERVER);

        nw.registerMessage(PacketPlatformGenerateRequest.Handler.class, PacketPlatformGenerateRequest.class, 21, Side.SERVER);
        nw.registerMessage(PacketPlatformGenerateResult.Handler.class, PacketPlatformGenerateResult.class, 22, Side.CLIENT);
        nw.registerMessage(PacketPlatformEnergySync.Handler.class, PacketPlatformEnergySync.class, 23, Side.CLIENT);

        nw.registerMessage(PacketSubnetAction.Handler.class, PacketSubnetAction.class, 24, Side.SERVER);
        nw.registerMessage(PacketZoneAction.Handler.class, PacketZoneAction.class, 25, Side.SERVER);
        nw.registerMessage(PacketPlatformInit.Handler.class, PacketPlatformInit.class, 26, Side.CLIENT);
        nw.registerMessage(PacketPlatformStatus.Handler.class, PacketPlatformStatus.class, 27, Side.CLIENT);
    }
}

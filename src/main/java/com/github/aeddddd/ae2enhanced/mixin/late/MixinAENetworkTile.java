package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.tile.grid.AENetworkTile;
import com.github.aeddddd.ae2enhanced.util.WirelessChannelConnectionHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * F1b-fix：在 TileEntity（如 ME 接口）加载到世界时（onReady），自动检查升级槽中的频道接收卡，
 * 并尝试建立无线网格连接。解决退出重进游戏后连接丢失的问题。
 */
@Mixin(value = AENetworkTile.class, remap = false)
public class MixinAENetworkTile {

    @Inject(method = "onReady", at = @At("TAIL"), remap = false)
    private void ae2e$onTileReady(CallbackInfo ci) {
        if (!appeng.util.Platform.isServer()) return;
        if ((Object) this instanceof IInterfaceHost) {
            DualityInterface duality = ((IInterfaceHost) (Object) this).getInterfaceDuality();
            if (duality != null) {
                WirelessChannelConnectionHelper.tryConnect(duality);
            }
        }
    }
}

package com.github.aeddddd.ae2enhanced.mixin.late;

import appeng.client.gui.MathExpressionParser;
import appeng.client.gui.implementations.GuiCraftAmount;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftRequest;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.PacketCraftRequestLong;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

/**
 * E2b: 终端合成下单量从 int 提升到 long。
 * AE2-UEL 后端已完全支持 long，瓶颈仅在前端 GUI 的局部变量类型和 PacketCraftRequest 的 int 构造函数。
 * 此 Mixin 修改 addQty 和 actionPerformed 方法，使用 long 计算；当数量超过 int 范围时，
 * 使用本 mod 的 PacketCraftRequestLong 替代原生的 PacketCraftRequest。
 */
@Mixin(value = GuiCraftAmount.class, remap = false)
public class MixinGuiCraftAmount {

    @Shadow
    private GuiTextField amountToCraft;

    @Shadow
    private GuiButton next;

    @Shadow
    private GuiTabButton originalGuiBtn;

    /**
     * 拦截 next 按钮点击，使用 long 解析数量并发送网络包。
     * 当数量在 int 范围内时，仍使用原生 PacketCraftRequest 保持兼容性；
     * 当数量超过 int 范围时，使用 PacketCraftRequestLong。
     */
    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onActionPerformed(GuiButton btn, CallbackInfo ci) throws IOException {
        if (btn == this.next) {
            try {
                double resultD = MathExpressionParser.parse(this.amountToCraft.getText());
                long result = resultD <= 0.0 || Double.isNaN(resultD) ? 1L : (long) MathExpressionParser.round(resultD, 0);
                boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
                if (result <= Integer.MAX_VALUE) {
                    NetworkHandler.instance().sendToServer(new PacketCraftRequest((int) result, shift));
                } else {
                    AE2Enhanced.network.sendToServer(new PacketCraftRequestLong(result, shift));
                }
                ci.cancel();
            } catch (NumberFormatException e) {
                this.amountToCraft.setText("1");
                ci.cancel();
            }
        }
    }

    /**
     * 拦截 addQty，将内部计算从 int 改为 long，支持超过 2^31-1 的数量累加。
     */
    @Inject(method = "addQty", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$onAddQty(int i, CallbackInfo ci) {
        try {
            String out = this.amountToCraft.getText();
            double resultD = MathExpressionParser.parse(out);
            long result = resultD <= 0.0 || Double.isNaN(resultD) ? 0L : (long) MathExpressionParser.round(resultD, 0);
            if (result == 1 && i > 1) {
                result = 0;
            }
            result += i;
            if (result < 1) {
                result = 1;
            }
            this.amountToCraft.setText(Long.toString(result));
            ci.cancel();
        } catch (NumberFormatException e) {
            ci.cancel();
        }
    }
}

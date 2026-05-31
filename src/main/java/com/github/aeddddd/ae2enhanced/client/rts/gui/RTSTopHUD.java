package com.github.aeddddd.ae2enhanced.client.rts.gui;

import com.github.aeddddd.ae2enhanced.client.rts.RTSInputHandler;
import com.github.aeddddd.ae2enhanced.client.rts.RTSSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * RTS 模式上方 HUD 信息展示。
 *
 * <p>显示内容：</p>
 * <ul>
 *   <li>光标指向的世界坐标（X, Y, Z）</li>
 *   <li>当前选中的方块总数</li>
 *   <li>当前选取模式（单点 / 范围 / 连锁 / 无）</li>
 * </ul>
 */
public class RTSTopHUD {

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!com.github.aeddddd.ae2enhanced.client.rts.RTSCamera.isActive()) {
            return;
        }
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = event.getResolution();
        int screenW = sr.getScaledWidth();

        StringBuilder sb = new StringBuilder();

        // 坐标
        BlockPos hit = RTSInputHandler.getLastHitPos();
        if (hit != null && RTSInputHandler.isLastHitValid()) {
            sb.append(String.format("\u00a77[\u00a7f%d \u00a77| \u00a7f%d \u00a77| \u00a7f%d\u00a77]  ",
                    hit.getX(), hit.getY(), hit.getZ()));
        } else {
            sb.append("\u00a77[\u00a78--- \u00a77| \u00a78--- \u00a77| \u00a78---\u00a77]  ");
        }

        // 选区数量
        int selCount = RTSSelection.getSelectedBlocks().size();
        sb.append(String.format("\u00a77\u9009\u533a: \u00a7f%d  ", selCount));

        // 模式
        RTSSelection.Mode mode = RTSSelection.getMode();
        String modeStr;
        switch (mode) {
            case ANCHOR_A_SET:
                modeStr = "\u00a7e\u8303\u56f4(\u5f85\u786e\u8ba4)";
                break;
            case BOX_SELECTED:
                modeStr = "\u00a7e\u8303\u56f4(\u540c\u6b65\u4e2d)";
                break;
            default:
                modeStr = selCount > 0 ? "\u00a7a\u5df2\u9009" : "\u00a78\u65e0";
                break;
        }
        sb.append("\u00a77|  ").append(modeStr);

        String text = sb.toString();
        int textW = mc.fontRenderer.getStringWidth(text);
        int x = (screenW - textW) / 2;
        int y = 4;

        mc.fontRenderer.drawStringWithShadow(text, x, y, 0xFFFFFFFF);
    }
}

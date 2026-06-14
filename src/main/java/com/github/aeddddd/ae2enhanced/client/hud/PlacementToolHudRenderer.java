package com.github.aeddddd.ae2enhanced.client.hud;

import com.github.aeddddd.ae2enhanced.item.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.SecurityTerminalBindingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * ME 放置工具 HUD —— 显示当前选中物品、数量、绑定状态。
 */
public class PlacementToolHudRenderer {

    @SubscribeEvent
    public void onRenderHud(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) return;

        ItemStack held = player.getHeldItemMainhand();
        boolean show = held.getItem() instanceof ItemMEPlacementTool
                || (held.getItem() instanceof com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool
                    && com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool.getMode(held)
                        == com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool.MODE_PLACEMENT);
        if (!show) return;

        ScaledResolution sr = event.getResolution();
        int x = sr.getScaledWidth() / 2 + 16;
        int y = sr.getScaledHeight() / 2 + 8;

        PlacementConfig config = new PlacementConfig(held);
        ItemStack selected = config.getStackInSlot(config.getSelectedSlot());

        // 绑定状态
        String status = SecurityTerminalBindingHelper.isLinked(held)
                ? I18n.format("item.ae2enhanced.me_placement_tool.linked")
                : I18n.format("item.ae2enhanced.me_placement_tool.unlinked");
        mc.fontRenderer.drawString(status, x, y - 12, 0xFFFFFF);

        // 当前选中物品
        if (!selected.isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(selected, x, y);
            mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRenderer, selected, x, y, null);
            RenderHelper.disableStandardItemLighting();

            String name = selected.getDisplayName();
            mc.fontRenderer.drawString(name, x + 20, y + 4, 0xFFFFFF);
            String count = "x" + config.getPlacementCount();
            mc.fontRenderer.drawString(count, x + 20, y + 14, 0xFFFFFF);
        } else {
            mc.fontRenderer.drawString(I18n.format("item.ae2enhanced.me_placement_tool.no_selection"), x + 20, y + 4, 0xFFFFFF);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}

package com.github.aeddddd.ae2enhanced.client.rts;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange;
import com.github.aeddddd.ae2enhanced.platform.PlatformQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/**
 * RTS 输入处理 —— 鼠标事件 + 按键事件 + 射线检测
 */
public class RTSInputHandler {

    // 上一帧的鼠标命中结果（由 RenderWorldLastEvent 中的射线检测更新）
    private static BlockPos lastHitPos = null;
    private static boolean lastHitValid = false;

    public static void setLastHit(BlockPos pos, boolean valid) {
        lastHitPos = pos;
        lastHitValid = valid;
    }

    public static BlockPos getLastHitPos() { return lastHitPos; }
    public static boolean isLastHitValid() { return lastHitValid; }

    // ==================== 鼠标事件 ====================

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (!RTSCamera.isActive()) return;
        if (Minecraft.getMinecraft().currentScreen != null) return;

        int button = event.getButton();
        boolean state = event.isButtonstate();

        // 鼠标移动事件（button == -1）：累积到位移缓冲区，由 RenderTickEvent 统一应用
        if (button == -1) {
            RTSTickController.accumulateMouseDelta(event.getDx(), event.getDy());
        }

        if (button == 0) {  // 左键
            event.setCanceled(true);
            if (state) {
                boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                RTSSelection.onLeftClick(lastHitValid ? lastHitPos : null, shift, ctrl);
            }
        } else if (button == 1) {  // 右键
            event.setCanceled(true);
            if (state) {
                net.minecraft.item.ItemStack placement = com.github.aeddddd.ae2enhanced.client.rts.gui.RTSBottomPanel.getCurrentPlacementItem();
                if (!placement.isEmpty()) {
                    if (!RTSSelection.getSelectedBlocks().isEmpty()) {
                        AE2Enhanced.network.sendToServer(new com.github.aeddddd.ae2enhanced.network.packet.PacketRTSPlace(
                                com.github.aeddddd.ae2enhanced.network.packet.PacketRTSPlace.MODE_SELECTION, placement));
                    } else if (lastHitValid && lastHitPos != null) {
                        AE2Enhanced.network.sendToServer(new com.github.aeddddd.ae2enhanced.network.packet.PacketRTSPlace(
                                com.github.aeddddd.ae2enhanced.network.packet.PacketRTSPlace.MODE_SINGLE, lastHitPos, placement));
                    }
                }
            }
        } else if (button == 2) {  // 中键
            event.setCanceled(true);
            RTSCamera.setRotating(state);
        }

        // 滚轮缩放
        if (event.getDwheel() != 0) {
            event.setCanceled(true);
            RTSCamera.adjustHeight(event.getDwheel());
        }
    }

    // ==================== 按键事件 ====================

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (!RTSCamera.isActive()) return;

        // Esc 或 I 键退出
        if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE) || Keyboard.isKeyDown(Keyboard.KEY_I)) {
            RTSCamera.deactivate();
            RTSSelection.clear();
            AE2Enhanced.network.sendToServer(new PacketRTSStateChange(PacketRTSStateChange.ACTION_EXIT));
            return;
        }

        // 数字键 1-9 选择底部面板 ME 存储物品
        for (int i = 0; i < 9; i++) {
            if (Keyboard.isKeyDown(Keyboard.KEY_1 + i)) {
                com.github.aeddddd.ae2enhanced.client.rts.gui.RTSBottomPanel.selectSlot(i);
                break;
            }
        }
    }

    // ==================== 准星与光标渲染 ====================

    @SubscribeEvent
    public void onPreRenderCrosshair(RenderGameOverlayEvent.Pre event) {
        if (!RTSCamera.isActive()) return;
        // 移除原版准星
        if (event.getType() == RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            event.setCanceled(true);
        }
        // 移除主手/物品栏显示
        if (event.getType() == RenderGameOverlayEvent.ElementType.HOTBAR) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPostRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (!RTSCamera.isActive()) return;
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.gui.ScaledResolution sr = event.getResolution();

        float cursorX = RTSTickController.getCursorX();
        float cursorY = RTSTickController.getCursorY();

        int sx = (int) (cursorX * sr.getScaledWidth() / mc.displayWidth);
        int sy = (int) (cursorY * sr.getScaledHeight() / mc.displayHeight);

        // 绘制白色十字光标
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // 水平线
        net.minecraft.client.gui.Gui.drawRect(sx - 6, sy, sx + 7, sy + 1, 0xFFFFFFFF);
        // 垂直线
        net.minecraft.client.gui.Gui.drawRect(sx, sy - 6, sx + 1, sy + 7, 0xFFFFFFFF);

        // 如果命中有效方块，画一个绿色小点
        if (lastHitValid) {
            net.minecraft.client.renderer.GlStateManager.color(0.3f, 1.0f, 0.3f, 1.0f);
            net.minecraft.client.gui.Gui.drawRect(sx - 1, sy - 1, sx + 2, sy + 2, 0xFF4DFF4D);
        }

        // 绘制当前放置物的 2D 缩略图（光标左下侧）
        net.minecraft.item.ItemStack placement = com.github.aeddddd.ae2enhanced.client.rts.gui.RTSBottomPanel.getCurrentPlacementItem();
        if (!placement.isEmpty()) {
            net.minecraft.client.renderer.RenderItem renderItem = mc.getRenderItem();
            net.minecraft.client.renderer.GlStateManager.enableTexture2D();
            net.minecraft.client.renderer.GlStateManager.enableRescaleNormal();
            net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
            int thumbX = sx + 8;
            int thumbY = sy + 8;
            renderItem.renderItemAndEffectIntoGUI(placement, thumbX, thumbY);
            renderItem.renderItemOverlayIntoGUI(mc.fontRenderer, placement, thumbX, thumbY, null);
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            net.minecraft.client.renderer.GlStateManager.disableRescaleNormal();
        }

        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    // 缓存反射方法，避免每次射线检测都查找
    private static java.lang.reflect.Method getFOVMethod = null;

    private static float getActualFov(Minecraft mc) {
        try {
            if (getFOVMethod == null) {
                getFOVMethod = net.minecraft.client.renderer.EntityRenderer.class.getDeclaredMethod("func_78481_a", float.class, boolean.class);
                getFOVMethod.setAccessible(true);
            }
            return (float) getFOVMethod.invoke(mc.entityRenderer, mc.getRenderPartialTicks(), true);
        } catch (Exception e) {
            return RTSCamera.getFov();
        }
    }

    // ==================== 射线检测（由 RenderWorldLastEvent 调用） ====================

    public static void updateRaycast() {
        if (!RTSCamera.isActive()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        float cursorX = RTSTickController.getCursorX();
        float cursorY = RTSTickController.getCursorY();

        // 相机位置（使用插值位置，与渲染一致）
        double camX = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * mc.getRenderPartialTicks();
        double camY = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * mc.getRenderPartialTicks() + RTSCamera.getHeight();
        double camZ = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * mc.getRenderPartialTicks();

        // 相机朝向
        float yawRad = (float) Math.toRadians(RTSCamera.getYaw());
        float pitchRad = (float) Math.toRadians(RTSCamera.getPitch());

        Vec3d forward = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
            Math.cos(yawRad) * Math.cos(pitchRad)
        );

        // 构建相机坐标系的 right 和 up
        Vec3d worldUp = new Vec3d(0, 1, 0);
        Vec3d right = forward.crossProduct(worldUp);
        if (right.lengthSquared() < 0.001) {
            right = new Vec3d(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3d up = right.crossProduct(forward).normalize();

        // NDC 坐标（鼠标坐标 Y 轴向下，OpenGL NDC Y 轴向上，需要翻转）
        float ndcX = (2.0f * cursorX / mc.displayWidth) - 1.0f;
        float ndcY = 1.0f - (2.0f * cursorY / mc.displayHeight);

        // FOV：直接读取 Minecraft 实际渲染使用的 FOV，确保与投影矩阵完全一致
        float actualFov = getActualFov(mc);
        float fovRad = (float) Math.toRadians(actualFov);
        float aspect = (float) mc.displayWidth / mc.displayHeight;
        float tanHalfFov = (float) Math.tan(fovRad / 2.0f);

        float camDirX = ndcX * tanHalfFov * aspect;
        float camDirY = ndcY * tanHalfFov;

        // 世界空间射线方向
        Vec3d dir = forward.add(right.scale(camDirX)).add(up.scale(camDirY)).normalize();

        // 使用 Minecraft 原生射线检测沿世界空间遍历，返回碰到的首个方块
        Vec3d start = new Vec3d(camX, camY, camZ);
        Vec3d end = start.add(dir.scale(256)); // 256 格足够覆盖任何平台尺寸
        net.minecraft.util.math.RayTraceResult result = mc.world.rayTraceBlocks(start, end, false, false, false);

        if (result != null && result.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            // rayTraceBlocks 返回的 blockPos 在俯视场景中会低一格，取上方方块修正
            BlockPos hitPos = result.getBlockPos().up();
            setLastHit(hitPos, true);
        } else {
            setLastHit(null, false);
        }
    }
}

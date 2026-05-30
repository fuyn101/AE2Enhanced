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
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

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

        // 鼠标移动事件（button == -1）
        if (button == -1 && (event.getDx() != 0 || event.getDy() != 0)) {
            if (RTSCamera.isRotating()) {
                // 中键按住：旋转相机 yaw，同时同步玩家本体视角
                RTSCamera.addYaw(event.getDx() * RTSCamera.ROTATION_SENSITIVITY);
                Minecraft.getMinecraft().player.rotationYaw = RTSCamera.getYaw();
            } else {
                // 正常：移动虚拟光标
                RTSTickController.moveCursor(
                    event.getDx() * RTSCamera.CURSOR_SENSITIVITY,
                    event.getDy() * RTSCamera.CURSOR_SENSITIVITY
                );
            }
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
            // P2：右键放置等操作
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
        int sy = sr.getScaledHeight() - (int) (cursorY * sr.getScaledHeight() / mc.displayHeight);

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

        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    // ==================== 射线检测（由 RenderWorldLastEvent 调用） ====================

    public static void updateRaycast() {
        if (!RTSCamera.isActive()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        float cursorX = RTSTickController.getCursorX();
        float cursorY = RTSTickController.getCursorY();

        try {
            // 通过反射获取 MODELVIEW / PROJECTION / VIEWPORT
            java.lang.reflect.Field mvField = net.minecraft.client.renderer.ActiveRenderInfo.class.getDeclaredField("MODELVIEW");
            java.lang.reflect.Field projField = net.minecraft.client.renderer.ActiveRenderInfo.class.getDeclaredField("PROJECTION");
            java.lang.reflect.Field vpField = net.minecraft.client.renderer.ActiveRenderInfo.class.getDeclaredField("VIEWPORT");
            mvField.setAccessible(true);
            projField.setAccessible(true);
            vpField.setAccessible(true);

            FloatBuffer modelView = (FloatBuffer) mvField.get(null);
            FloatBuffer projection = (FloatBuffer) projField.get(null);
            IntBuffer viewport = (IntBuffer) vpField.get(null);

            if (modelView == null || projection == null || viewport == null) return;

            FloatBuffer nearPos = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).asFloatBuffer();
            FloatBuffer farPos = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).asFloatBuffer();

            GLU.gluUnProject(cursorX, cursorY, 0.0f, modelView, projection, viewport, nearPos);
            GLU.gluUnProject(cursorX, cursorY, 1.0f, modelView, projection, viewport, farPos);

            double nx = nearPos.get(0), ny = nearPos.get(1), nz = nearPos.get(2);
            double fx = farPos.get(0), fy = farPos.get(1), fz = farPos.get(2);

            Vec3d origin = new Vec3d(nx, ny, nz);
            Vec3d dir = new Vec3d(fx - nx, fy - ny, fz - nz).normalize();

            int surfaceY = RTSCamera.getPlatformSurfaceY();
            if (Math.abs(dir.y) < 0.0001) {
                setLastHit(null, false);
                return;
            }

            double t = (surfaceY + 1 - origin.y) / dir.y;
            if (t > 0) {
                Vec3d hit = origin.add(dir.scale(t));
                BlockPos hitPos = new BlockPos(hit.x, surfaceY + 1, hit.z);
                if (PlatformQuery.isInside(hitPos)) {
                    setLastHit(hitPos, true);
                    return;
                }
            }
            setLastHit(null, false);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] RTS raycast failed", e);
            setLastHit(null, false);
        }
    }
}

package com.github.aeddddd.ae2enhanced.client.rts;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange;
import com.github.aeddddd.ae2enhanced.proxy.ClientProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * RTS 输入处理器 —— 拦截原游戏输入（移动/交互/潜行），映射为 RTS 操作。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class RTSInputHandler {

    private static final double PAN_SPEED = 2.0;
    private static final double ZOOM_SPEED = 4.0;
    private static final double MIN_HEIGHT = 8.0;
    private static final double MAX_HEIGHT = 256.0;

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        if (ClientProxy.RTS_TOGGLE_KEY.isPressed()) {
            if (ClientRTSState.isInRTS) {
                // 退出 RTS
                AE2Enhanced.network.sendToServer(new PacketRTSStateChange(false));
                RTSCameraController.exit();
                ClientRTSState.exit();
            } else {
                // 请求进入 RTS
                AE2Enhanced.network.sendToServer(new PacketRTSStateChange(true));
            }
        }

        // Esc 退出 RTS
        if (ClientRTSState.isInRTS && Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
            AE2Enhanced.network.sendToServer(new PacketRTSStateChange(false));
            RTSCameraController.exit();
            ClientRTSState.exit();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ClientRTSState.isInRTS || event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // ===== 移动输入接管：WASD → 相机平移 =====
        double dx = 0;
        double dz = 0;

        if (mc.gameSettings.keyBindForward.isKeyDown()) dz -= 1;
        if (mc.gameSettings.keyBindBack.isKeyDown()) dz += 1;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) dx -= 1;
        if (mc.gameSettings.keyBindRight.isKeyDown()) dx += 1;

        // 根据相机 yaw 将屏幕方向映射到世界方向
        // W=屏幕上方(相机朝向), S=屏幕下方, A=屏幕左方, D=屏幕右方
        double yawRad = Math.toRadians(ClientRTSState.cameraYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double moveX = dz * sin - dx * cos;
        double moveZ = -dz * cos - dx * sin;

        ClientRTSState.targetCameraX += moveX * PAN_SPEED;
        ClientRTSState.targetCameraZ += moveZ * PAN_SPEED;

        // ===== 滚轮缩放 =====
        int dw = Mouse.getDWheel();
        if (dw != 0) {
            ClientRTSState.targetCameraY -= Math.signum(dw) * ZOOM_SPEED;
        }

        // 高度限制
        if (ClientRTSState.controllerPos != null) {
            int surfaceY = ClientRTSState.controllerPos.getY();
            ClientRTSState.targetCameraY = Math.max(surfaceY + MIN_HEIGHT,
                    Math.min(surfaceY + MAX_HEIGHT, ClientRTSState.targetCameraY));
        }

        // ===== 边界锁定 =====
        // 客户端仅做软锁定，服务端做硬校验
        // 这里读取 controllerPos 对应的平台边界... 
        // 由于客户端不知道平台精确边界，暂时不做客户端硬锁定
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInputUpdate(InputUpdateEvent event) {
        if (!ClientRTSState.isInRTS) return;
        // 禁用原游戏移动输入
        event.getMovementInput().moveForward = 0;
        event.getMovementInput().moveStrafe = 0;
        event.getMovementInput().jump = false;
        event.getMovementInput().sneak = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseEvent(MouseEvent event) {
        if (!ClientRTSState.isInRTS) return;

        int button = event.getButton();
        boolean down = event.isButtonstate();

        // 左键：选取
        if (button == 0) {
            event.setCanceled(true);
            if (down) {
                handleLeftClickDown();
            } else {
                handleLeftClickUp();
            }
        }

        // 右键：P1 不实现放置，仅拦截
        if (button == 1) {
            event.setCanceled(true);
        }

        // 中键：拦截
        if (button == 2) {
            event.setCanceled(true);
        }
    }

    private static void handleLeftClickDown() {
        Minecraft mc = Minecraft.getMinecraft();
        RayTraceResult result = mc.objectMouseOver;

        if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
            BlockPos pos = result.getBlockPos();

            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                // Shift+左键：开始框选
                ClientRTSState.isDragging = true;
                ClientRTSState.dragStartPos = pos;
            } else if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                // Ctrl+左键：连锁选取（客户端只做视觉反馈，实际逻辑在服务端）
                // P1 简化：连锁选取在客户端直接计算
                // TODO: 发送连锁选取请求到服务端
            } else {
                // 单点选取
                ClientRTSState.currentSelection.addSingle(pos);
                // TODO: 发送选取到服务端
            }
        } else {
            // 空点击：清空选区
            ClientRTSState.currentSelection.clear();
        }
    }

    private static void handleLeftClickUp() {
        if (ClientRTSState.isDragging && ClientRTSState.dragStartPos != null) {
            Minecraft mc = Minecraft.getMinecraft();
            RayTraceResult result = mc.objectMouseOver;
            if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK) {
                BlockPos endPos = result.getBlockPos();
                ClientRTSState.currentSelection.setBox(ClientRTSState.dragStartPos, endPos);
                // TODO: 发送框选到服务端
            }
            ClientRTSState.isDragging = false;
            ClientRTSState.dragStartPos = null;
        }
    }
}

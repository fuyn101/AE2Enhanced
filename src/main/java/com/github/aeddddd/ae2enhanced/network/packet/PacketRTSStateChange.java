package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.rts.RTSCamera;
import com.github.aeddddd.ae2enhanced.client.rts.RTSSelection;
import com.github.aeddddd.ae2enhanced.platform.PlatformQuery;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RTS 视角状态变更同步
 */
public class PacketRTSStateChange implements IMessage {

    public static final byte ACTION_ENTER = 0;
    public static final byte ACTION_EXIT = 1;
    public static final byte ACTION_ENTER_CONFIRM = 2;
    public static final byte ACTION_FORCE_EXIT = 3;

    private byte action;
    private int centerX, centerY, centerZ;
    private int sizeInChunks;
    private int surfaceY;

    public PacketRTSStateChange() {}

    public PacketRTSStateChange(byte action) {
        this.action = action;
    }

    public PacketRTSStateChange(byte action, BlockPos center, int sizeInChunks, int surfaceY) {
        this.action = action;
        this.centerX = center.getX();
        this.centerY = center.getY();
        this.centerZ = center.getZ();
        this.sizeInChunks = sizeInChunks;
        this.surfaceY = surfaceY;
    }

    public byte getAction() { return action; }
    public BlockPos getCenter() { return new BlockPos(centerX, centerY, centerZ); }
    public int getSizeInChunks() { return sizeInChunks; }
    public int getSurfaceY() { return surfaceY; }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = buf.readByte();
        if (action == ACTION_ENTER_CONFIRM) {
            centerX = buf.readInt();
            centerY = buf.readInt();
            centerZ = buf.readInt();
            sizeInChunks = buf.readInt();
            surfaceY = buf.readInt();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        if (action == ACTION_ENTER_CONFIRM) {
            buf.writeInt(centerX);
            buf.writeInt(centerY);
            buf.writeInt(centerZ);
            buf.writeInt(sizeInChunks);
            buf.writeInt(surfaceY);
        }
    }

    // ==================== C→S ====================
    public static class C2SHandler implements IMessageHandler<PacketRTSStateChange, IMessage> {

        // 服务端 RTS 状态存储
        public static final Map<UUID, ServerRTSState> STATES = new HashMap<>();

        @Override
        public IMessage onMessage(PacketRTSStateChange message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                UUID uuid = player.getUniqueID();
                if (message.action == ACTION_ENTER) {
                    handleEnter(player, uuid);
                } else if (message.action == ACTION_EXIT) {
                    handleExit(uuid);
                }
            });
            return null;
        }

        private void handleEnter(EntityPlayerMP player, UUID uuid) {
            // 已在 RTS 中则忽略
            if (STATES.containsKey(uuid)) return;

            // 查找玩家附近的平台控制器
            com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController controller = findController(player);
            if (controller == null || !controller.isPlatformActive()) return;

            BlockPos center = controller.getPos();
            int size = controller.getPlatformSizeInChunks();
            int surfaceY = controller.getPlatformSurfaceY();

            // 校验玩家是否在平台范围内
            BlockPos min = controller.getPlatformMin();
            BlockPos max = controller.getPlatformMax();
            if (player.posX < min.getX() || player.posX > max.getX() + 1 ||
                player.posZ < min.getZ() || player.posZ > max.getZ() + 1) {
                return;
            }

            ServerRTSState state = new ServerRTSState();
            state.platformCenter = center;
            state.platformSize = size;
            state.surfaceY = surfaceY;
            state.platformMin = min;
            state.platformMax = max;
            STATES.put(uuid, state);

            // 发送确认包
            AE2Enhanced.network.sendTo(
                new PacketRTSStateChange(ACTION_ENTER_CONFIRM, center, size, surfaceY),
                player
            );
        }

        private void handleExit(UUID uuid) {
            STATES.remove(uuid);
        }

        private com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController findController(EntityPlayerMP player) {
            // 在玩家周围 128 格范围内查找平台控制器
            for (net.minecraft.tileentity.TileEntity te : player.world.loadedTileEntityList) {
                if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController) {
                    com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController ctrl =
                        (com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController) te;
                    if (ctrl.getPos().distanceSq(player.posX, player.posY, player.posZ) < 128 * 128) {
                        return ctrl;
                    }
                }
            }
            return null;
        }
    }

    // ==================== S→C ====================
    public static class S2CHandler implements IMessageHandler<PacketRTSStateChange, IMessage> {
        @Override
        public IMessage onMessage(PacketRTSStateChange message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.action == ACTION_ENTER_CONFIRM) {
                    RTSCamera.activate(message.getCenter(), message.getSizeInChunks(), message.getSurfaceY());
                } else if (message.action == ACTION_FORCE_EXIT) {
                    RTSCamera.deactivate();
                    RTSSelection.clear();
                }
            });
            return null;
        }
    }

    // 服务端状态数据结构
    public static class ServerRTSState {
        public BlockPos platformCenter;
        public int platformSize;
        public int surfaceY;
        public BlockPos platformMin;
        public BlockPos platformMax;
        public java.util.BitSet selectionBitmap = new java.util.BitSet();
    }
}

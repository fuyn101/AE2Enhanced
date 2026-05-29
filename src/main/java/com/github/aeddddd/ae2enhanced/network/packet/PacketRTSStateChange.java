package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.rts.ClientRTSState;
import com.github.aeddddd.ae2enhanced.platform.selection.SelectionManager;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * RTS 状态变更包 —— 双向通信。
 * 客户端 → 服务端：请求进入/退出 RTS。
 * 服务端 → 客户端：确认进入，下发相机目标位置。
 */
public class PacketRTSStateChange implements IMessage {

    private boolean entering;
    private double cameraX, cameraY, cameraZ;

    public PacketRTSStateChange() {
    }

    /** 客户端构造：请求进入/退出 */
    public PacketRTSStateChange(boolean entering) {
        this.entering = entering;
    }

    /** 服务端构造：确认进入，下发相机位置 */
    public PacketRTSStateChange(boolean entering, double cameraX, double cameraY, double cameraZ) {
        this.entering = entering;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entering = buf.readBoolean();
        this.cameraX = buf.readDouble();
        this.cameraY = buf.readDouble();
        this.cameraZ = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(entering);
        buf.writeDouble(cameraX);
        buf.writeDouble(cameraY);
        buf.writeDouble(cameraZ);
    }

    // ==================== 客户端 → 服务端 Handler ====================

    public static class C2SHandler implements IMessageHandler<PacketRTSStateChange, IMessage> {

        @Override
        public IMessage onMessage(PacketRTSStateChange message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (message.entering) {
                    tryEnterRTS(player);
                } else {
                    SelectionManager.exitRTS(player);
                }
            });
            return null;
        }

        private void tryEnterRTS(EntityPlayerMP player) {
            World world = player.getServerWorld();
            int chunkX = (int) player.posX >> 4;
            int chunkZ = (int) player.posZ >> 4;

            // 搜索玩家所在区块及周围 2 格范围内的活跃平台控制器
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    net.minecraft.util.math.ChunkPos cp = new net.minecraft.util.math.ChunkPos(chunkX + dx, chunkZ + dz);
                    if (!world.isChunkGeneratedAt(cp.x, cp.z)) continue;
                    net.minecraft.world.chunk.Chunk chunk = world.getChunk(cp.x, cp.z);
                    for (net.minecraft.tileentity.TileEntity te : chunk.getTileEntityMap().values()) {
                        if (te instanceof TileAdvancedPlatformController) {
                            TileAdvancedPlatformController controller = (TileAdvancedPlatformController) te;
                            if (controller.isPlatformActive()) {
                                BlockPos min = controller.getPlatformMin();
                                BlockPos max = controller.getPlatformMax();
                                if (player.posX >= min.getX() && player.posX <= max.getX()
                                        && player.posZ >= min.getZ() && player.posZ <= max.getZ()) {
                                    // 玩家在平台范围内，允许进入
                                    SelectionManager.enterRTS(player, controller.getPos());
                                    double camX = (min.getX() + max.getX()) / 2.0 + 0.5;
                                    double camZ = (min.getZ() + max.getZ()) / 2.0 + 0.5;
                                    double camY = min.getY() + 64;
                                    AE2Enhanced.network.sendTo(
                                            new PacketRTSStateChange(true, camX, camY, camZ),
                                            player);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
            // 未找到可用平台，忽略请求
        }
    }

    // ==================== 服务端 → 客户端 Handler ====================

    public static class S2CHandler implements IMessageHandler<PacketRTSStateChange, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketRTSStateChange message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.entering) {
                    ClientRTSState.enter(null, message.cameraX, message.cameraY, message.cameraZ);
                } else {
                    ClientRTSState.exit();
                }
            });
            return null;
        }
    }
}

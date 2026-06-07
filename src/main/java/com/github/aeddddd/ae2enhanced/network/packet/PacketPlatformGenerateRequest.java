package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.platform.PlatformAsyncPlacer;
import com.github.aeddddd.ae2enhanced.platform.PlatformOverlapManager;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPlatformGenerateRequest implements IMessage {

    private int targetX, targetY, targetZ;

    public PacketPlatformGenerateRequest() {
    }

    public PacketPlatformGenerateRequest(BlockPos target) {
        this.targetX = target.getX();
        this.targetY = target.getY();
        this.targetZ = target.getZ();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.targetX = buf.readInt();
        this.targetY = buf.readInt();
        this.targetZ = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(targetX);
        buf.writeInt(targetY);
        buf.writeInt(targetZ);
    }

    public static class Handler implements IMessageHandler<PacketPlatformGenerateRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketPlatformGenerateRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                World world = player.getServerWorld();
                BlockPos target = new BlockPos(message.targetX, message.targetY, message.targetZ);

                if (player.getDistanceSq(target) > 64.0) return;
                if (!(player.getHeldItemMainhand().getItem() == ItemRegistry.PLATFORM_DEVELOPMENT_LICENSE)) return;

                int chunkX = target.getX() >> 4;
                int chunkZ = target.getZ() >> 4;
                BlockPos controllerPos = new BlockPos(chunkX * 16 + 7, target.getY(), chunkZ * 16 + 7);

                if (!PlatformOverlapManager.get(world).canClaim(controllerPos, 5)) {
                    player.sendMessage(new TextComponentString("§c该区域已存在平台,无法重叠生成."));
                    return;
                }

                player.getHeldItemMainhand().shrink(1);
                PlatformAsyncPlacer.startGeneration(player, controllerPos, target.getY(), 5);
            });
            return null;
        }
    }
}

package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * RTS 破坏选区方块请求（C→S）。
 *
 * <p>客户端发送空包，服务端根据当前玩家的 {@link PacketRTSStateChange.ServerRTSState}
 * 中的选区位图执行破坏。破坏后自动清空选区并同步回客户端。</p>
 */
public class PacketRTSBreak implements IMessage {

    public PacketRTSBreak() {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    public static class Handler implements IMessageHandler<PacketRTSBreak, IMessage> {
        @Override
        public IMessage onMessage(PacketRTSBreak message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                handleBreak(player);
            });
            return null;
        }

        private void handleBreak(EntityPlayerMP player) {
            PacketRTSStateChange.ServerRTSState state =
                    PacketRTSStateChange.C2SHandler.STATES.get(player.getUniqueID());
            if (state == null) return;

            TileAdvancedPlatformController controller = findController(player, state);
            if (controller == null) return;

            World world = player.world;
            int height = state.platformMax.getZ() - state.platformMin.getZ() + 1;
            int broken = 0;

            for (int i = state.selectionBitmap.nextSetBit(0); i >= 0; i = state.selectionBitmap.nextSetBit(i + 1)) {
                int relX = i / height;
                int relZ = i % height;
                BlockPos pos = new BlockPos(
                        state.platformMin.getX() + relX,
                        state.selectionY,
                        state.platformMin.getZ() + relZ);

                if (!isInsidePlatform(pos, state)) continue;

                IBlockState blockState = world.getBlockState(pos);
                if (blockState.getMaterial().isReplaceable() || world.isAirBlock(pos)) continue;

                world.destroyBlock(pos, true);
                broken++;
            }

            // 破坏后清空选区并同步
            if (broken > 0 || !state.selectionBitmap.isEmpty()) {
                state.selectionBitmap.clear();
                AE2Enhanced.network.sendTo(
                        new PacketRTSSelection(state.selectionBitmap, state.platformMin, state.platformMax, state.selectionY),
                        player
                );
            }
        }

        private boolean isInsidePlatform(BlockPos pos, PacketRTSStateChange.ServerRTSState state) {
            return pos.getX() >= state.platformMin.getX() && pos.getX() <= state.platformMax.getX()
                    && pos.getY() >= state.platformMin.getY() && pos.getY() <= state.platformMax.getY()
                    && pos.getZ() >= state.platformMin.getZ() && pos.getZ() <= state.platformMax.getZ();
        }

        private TileAdvancedPlatformController findController(EntityPlayerMP player, PacketRTSStateChange.ServerRTSState state) {
            net.minecraft.tileentity.TileEntity te = player.world.getTileEntity(state.platformCenter);
            if (te instanceof TileAdvancedPlatformController) {
                return (TileAdvancedPlatformController) te;
            }
            return null;
        }
    }
}

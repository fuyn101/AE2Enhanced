package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;

/**
 * RTS 破坏选区方块请求（C→S）。
 *
 * <p>客户端发送空包，服务端根据当前玩家的 {@link PacketRTSStateChange.ServerRTSState}
 * 中的选区位图执行破坏。破坏后掉落物自动吸入所连 ME 网络，并清空选区同步回客户端。</p>
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
                        state.surfaceY,
                        state.platformMin.getZ() + relZ);

                if (!isInsidePlatform(pos, state)) continue;

                IBlockState blockState = world.getBlockState(pos);
                if (blockState.getMaterial().isReplaceable() || world.isAirBlock(pos)) continue;

                // 破坏方块并生成掉落物实体
                world.destroyBlock(pos, true);
                broken++;

                // 立即吸收该位置新生成的物品实体到 ME 网络
                absorbDropsAround(world, pos, controller);
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

        private void absorbDropsAround(World world, BlockPos pos, TileAdvancedPlatformController controller) {
            List<EntityItem> entities = world.getEntitiesWithinAABB(
                    EntityItem.class,
                    new AxisAlignedBB(pos).grow(1.5));
            if (entities.isEmpty()) return;

            try {
                appeng.api.networking.storage.IStorageGrid storageGrid =
                        controller.getProxy().getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
                if (storageGrid == null) return;

                IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
                for (EntityItem entity : entities) {
                    ItemStack stack = entity.getItem();
                    if (stack.isEmpty()) continue;

                    IAEItemStack toInject = channel.createStack(stack);
                    if (toInject == null) continue;

                    IAEItemStack leftover = storageGrid.getInventory(channel).injectItems(
                            toInject, Actionable.MODULATE, controller.getMachineSource());

                    if (leftover == null || leftover.getStackSize() <= 0) {
                        entity.setDead(); // 全部吸入，移除实体
                    } else {
                        // 部分吸入，更新实体上的剩余数量
                        stack.setCount((int) Math.min(leftover.getStackSize(), Integer.MAX_VALUE));
                        if (stack.getCount() <= 0) {
                            entity.setDead();
                        }
                    }
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to absorb drops into ME network", e);
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

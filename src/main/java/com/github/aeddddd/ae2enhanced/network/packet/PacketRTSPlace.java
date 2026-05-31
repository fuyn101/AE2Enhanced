package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * RTS 右键放置请求（C→S）。
 */
public class PacketRTSPlace implements IMessage {

    public static final int MODE_SINGLE = 0;
    public static final int MODE_SELECTION = 1;

    private int mode;
    private BlockPos pos;
    private ItemStack stack;

    public PacketRTSPlace() {
    }

    public PacketRTSPlace(int mode, ItemStack stack) {
        this.mode = mode;
        this.stack = stack;
    }

    public PacketRTSPlace(int mode, BlockPos pos, ItemStack stack) {
        this.mode = mode;
        this.pos = pos;
        this.stack = stack;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(mode);
        ByteBufUtils.writeItemStack(buf, stack);
        if (mode == MODE_SINGLE) {
            buf.writeInt(pos.getX());
            buf.writeInt(pos.getY());
            buf.writeInt(pos.getZ());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readInt();
        stack = ByteBufUtils.readItemStack(buf);
        if (mode == MODE_SINGLE) {
            pos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    public static class Handler implements IMessageHandler<PacketRTSPlace, IMessage> {
        @Override
        public IMessage onMessage(PacketRTSPlace message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                handlePlace(player, message);
            });
            return null;
        }

        private void handlePlace(EntityPlayerMP player, PacketRTSPlace message) {
            if (message.stack.isEmpty()) return;
            if (!(message.stack.getItem() instanceof ItemBlock)) return;

            PacketRTSStateChange.ServerRTSState state =
                    PacketRTSStateChange.C2SHandler.STATES.get(player.getUniqueID());
            if (state == null) return;

            TileAdvancedPlatformController controller = findController(player, state);
            if (controller == null) return;

            if (message.mode == MODE_SINGLE) {
                if (message.pos == null) return;
                if (!isInsidePlatform(message.pos, state)) return;
                if (!tryPlace(player, message.pos, message.stack, controller)) {
                    // 播放错误音效（可选）
                }
            } else if (message.mode == MODE_SELECTION) {
                int width = state.platformMax.getX() - state.platformMin.getX() + 1;
                int height = state.platformMax.getZ() - state.platformMin.getZ() + 1;

                for (int i = state.selectionBitmap.nextSetBit(0); i >= 0; i = state.selectionBitmap.nextSetBit(i + 1)) {
                    int relX = i / height;
                    int relZ = i % height;
                    BlockPos pos = new BlockPos(
                            state.platformMin.getX() + relX,
                            state.selectionY,
                            state.platformMin.getZ() + relZ);

                    if (!tryPlace(player, pos, message.stack, controller)) {
                        break; // 物品不足，停止批量放置
                    }
                }
            }
        }

        private boolean tryPlace(EntityPlayerMP player, BlockPos pos, ItemStack template, TileAdvancedPlatformController controller) {
            World world = player.world;
            if (!world.getBlockState(pos).getMaterial().isReplaceable() && !world.isAirBlock(pos)) {
                return true; // 位置已被占据，跳过（不消耗物品）
            }

            // 消耗物品：背包优先，其次 ME 网络
            if (!consumeFromInventory(player, template)) {
                if (!consumeFromNetwork(controller, template)) {
                    return false; // 物品不足
                }
            }

            net.minecraft.block.Block block = ((ItemBlock) template.getItem()).getBlock();
            IBlockState blockState = block.getDefaultState();
            if (world.setBlockState(pos, blockState)) {
                world.playEvent(2001, pos, net.minecraft.block.Block.getIdFromBlock(block));
                return true;
            }
            return false;
        }

        private boolean consumeFromInventory(EntityPlayerMP player, ItemStack requested) {
            for (int i = 0; i < player.inventory.mainInventory.size(); i++) {
                ItemStack invStack = player.inventory.mainInventory.get(i);
                if (ItemStack.areItemsEqual(invStack, requested)
                        && ItemStack.areItemStackTagsEqual(invStack, requested)
                        && invStack.getCount() > 0) {
                    invStack.shrink(1);
                    if (invStack.isEmpty()) {
                        player.inventory.mainInventory.set(i, ItemStack.EMPTY);
                    }
                    player.inventory.markDirty();
                    return true;
                }
            }
            return false;
        }

        private boolean consumeFromNetwork(TileAdvancedPlatformController controller, ItemStack requested) {
            try {
                appeng.api.networking.storage.IStorageGrid storageGrid =
                        controller.getProxy().getGrid().getCache(appeng.api.networking.storage.IStorageGrid.class);
                if (storageGrid == null) return false;

                IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
                IAEItemStack toExtract = channel.createStack(requested);
                if (toExtract == null) return false;
                toExtract.setStackSize(1);

                IAEItemStack extracted = storageGrid.getInventory(channel).extractItems(
                        toExtract, Actionable.MODULATE, controller.getMachineSource());
                return extracted != null && extracted.getStackSize() > 0;
            } catch (Exception e) {
                return false;
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

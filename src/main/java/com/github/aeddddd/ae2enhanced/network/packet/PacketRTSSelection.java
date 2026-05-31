package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.rts.RTSSelection;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.BitSet;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 选区操作请求与全量同步 —— 使用位图 + Deflater 压缩
 */
public class PacketRTSSelection implements IMessage {

    public static final byte MODE_SINGLE = 0;
    public static final byte MODE_BOX = 1;
    public static final byte MODE_FLOOD = 2;
    public static final byte MODE_CLEAR = 3;
    public static final byte MODE_FULL_SYNC = 4;

    private byte mode;
    private int x1, y1, z1;
    private int x2, y2, z2;
    private byte[] compressedData;

    public PacketRTSSelection() {}

    // C→S 构造器
    public PacketRTSSelection(byte mode, BlockPos pos) {
        this.mode = mode;
        this.x1 = pos.getX();
        this.y1 = pos.getY();
        this.z1 = pos.getZ();
    }

    public PacketRTSSelection(byte mode, BlockPos a, BlockPos b) {
        this.mode = mode;
        this.x1 = a.getX(); this.y1 = a.getY(); this.z1 = a.getZ();
        this.x2 = b.getX(); this.y2 = b.getY(); this.z2 = b.getZ();
    }

    // S→C 构造器：从 Set<BlockPos>
    public PacketRTSSelection(Set<BlockPos> selection, BlockPos platformMin, BlockPos platformMax, int y) {
        this.mode = MODE_FULL_SYNC;
        this.compressedData = compressSelection(selection, platformMin, platformMax, y);
    }

    // S→C 构造器：从 BitSet（服务端直接压缩，避免解压再压缩）
    public PacketRTSSelection(java.util.BitSet bitmap, BlockPos platformMin, BlockPos platformMax, int y) {
        this.mode = MODE_FULL_SYNC;
        this.compressedData = compressBitmap(bitmap, y);
    }

    public byte getMode() { return mode; }
    public BlockPos getPos1() { return new BlockPos(x1, y1, z1); }
    public BlockPos getPos2() { return new BlockPos(x2, y2, z2); }
    public byte[] getCompressedData() { return compressedData; }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = buf.readByte();
        if (mode == MODE_FULL_SYNC) {
            int len = buf.readInt();
            if (len > 0) {
                compressedData = new byte[len];
                buf.readBytes(compressedData);
            }
        } else {
            x1 = buf.readInt(); y1 = buf.readInt(); z1 = buf.readInt();
            if (mode == MODE_BOX) {
                x2 = buf.readInt(); y2 = buf.readInt(); z2 = buf.readInt();
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(mode);
        if (mode == MODE_FULL_SYNC) {
            buf.writeInt(compressedData != null ? compressedData.length : 0);
            if (compressedData != null) {
                buf.writeBytes(compressedData);
            }
        } else {
            buf.writeInt(x1); buf.writeInt(y1); buf.writeInt(z1);
            if (mode == MODE_BOX) {
                buf.writeInt(x2); buf.writeInt(y2); buf.writeInt(z2);
            }
        }
    }

    // ==================== C→S ====================
    public static class C2SHandler implements IMessageHandler<PacketRTSSelection, IMessage> {
        @Override
        public IMessage onMessage(PacketRTSSelection message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                PacketRTSStateChange.ServerRTSState state =
                    PacketRTSStateChange.C2SHandler.STATES.get(player.getUniqueID());
                if (state == null) return;

                switch (message.mode) {
                    case MODE_SINGLE:
                        state.selectionBitmap.clear();
                        state.selectionY = message.getPos1().getY();
                        addToBitmap(state, message.getPos1());
                        break;
                    case MODE_BOX: {
                        state.selectionBitmap.clear();
                        BlockPos a = message.getPos1();
                        BlockPos b = message.getPos2();
                        state.selectionY = a.getY();
                        int minX = Math.min(a.getX(), b.getX());
                        int maxX = Math.max(a.getX(), b.getX());
                        int minZ = Math.min(a.getZ(), b.getZ());
                        int maxZ = Math.max(a.getZ(), b.getZ());
                        for (int x = minX; x <= maxX; x++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                setBitmapBit(state, x, z, true);
                            }
                        }
                        break;
                    }
                    case MODE_FLOOD:
                        state.selectionBitmap.clear();
                        state.selectionY = message.getPos1().getY();
                        floodFill(state, player, message.getPos1());
                        break;
                    case MODE_CLEAR:
                        state.selectionBitmap.clear();
                        break;
                }

                // 回传全量同步：直接从 bitmap 压缩，避免解压再压缩
                AE2Enhanced.network.sendTo(
                    new PacketRTSSelection(state.selectionBitmap, state.platformMin, state.platformMax, state.selectionY),
                    player
                );
            });
            return null;
        }

        private void addToBitmap(PacketRTSStateChange.ServerRTSState state, BlockPos pos) {
            setBitmapBit(state, pos.getX(), pos.getZ(), true);
        }

        private void setBitmapBit(PacketRTSStateChange.ServerRTSState state, int x, int z, boolean value) {
            int width = state.platformMax.getX() - state.platformMin.getX() + 1;
            int relX = x - state.platformMin.getX();
            int relZ = z - state.platformMin.getZ();
            if (relX < 0 || relZ < 0) return;
            if (relX >= width) return;
            int height = state.platformMax.getZ() - state.platformMin.getZ() + 1;
            if (relZ >= height) return;
            state.selectionBitmap.set(relX * height + relZ, value);
        }

        private void floodFill(PacketRTSStateChange.ServerRTSState state, EntityPlayerMP player, BlockPos origin) {
            int width = state.platformMax.getX() - state.platformMin.getX() + 1;
            int height = state.platformMax.getZ() - state.platformMin.getZ() + 1;

            net.minecraft.block.state.IBlockState originState = player.world.getBlockState(origin);
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
            java.util.HashSet<BlockPos> visited = new java.util.HashSet<>();
            queue.add(origin);
            visited.add(origin);

            int count = 0;
            while (!queue.isEmpty() && count < 1000) {
                BlockPos pos = queue.poll();
                setBitmapBit(state, pos.getX(), pos.getZ(), true);
                count++;

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        BlockPos next = pos.add(dx, 0, dz);
                        if (visited.contains(next)) continue;
                        if (next.getX() < state.platformMin.getX() || next.getX() > state.platformMax.getX() ||
                            next.getZ() < state.platformMin.getZ() || next.getZ() > state.platformMax.getZ()) continue;
                        if (player.world.getBlockState(next) != originState) continue;
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
    }

    // ==================== S→C ====================
    public static class S2CHandler implements IMessageHandler<PacketRTSSelection, IMessage> {
        @Override
        public IMessage onMessage(PacketRTSSelection message, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.mode == MODE_FULL_SYNC && message.compressedData != null) {
                    RTSSelection.syncFromBitmap(message.compressedData);
                }
            });
            return null;
        }
    }

    // ==================== 压缩 / 解压工具 ====================

    public static byte[] compressBitmap(java.util.BitSet bitmap, int y) {
        byte[] raw = bitmap.toByteArray();
        byte[] combined = new byte[raw.length + 4];
        combined[0] = (byte)(y >>> 24);
        combined[1] = (byte)(y >>> 16);
        combined[2] = (byte)(y >>> 8);
        combined[3] = (byte)y;
        System.arraycopy(raw, 0, combined, 4, raw.length);
        Deflater deflater = new Deflater();
        deflater.setInput(combined);
        deflater.finish();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(combined.length);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    public static byte[] compressSelection(Set<BlockPos> selection, BlockPos platformMin, BlockPos platformMax, int y) {
        int width = platformMax.getX() - platformMin.getX() + 1;
        int height = platformMax.getZ() - platformMin.getZ() + 1;
        BitSet bits = new BitSet(width * height);
        for (BlockPos pos : selection) {
            int relX = pos.getX() - platformMin.getX();
            int relZ = pos.getZ() - platformMin.getZ();
            if (relX >= 0 && relX < width && relZ >= 0 && relZ < height) {
                bits.set(relX * height + relZ);
            }
        }
        return compressBitmap(bits, y);
    }

    public static java.util.Map.Entry<Integer, java.util.Set<BlockPos>> decompressBitmap(byte[] compressed, BlockPos platformMin, BlockPos platformMax) {
        java.util.HashSet<BlockPos> result = new java.util.HashSet<>();
        int width = platformMax.getX() - platformMin.getX() + 1;
        int height = platformMax.getZ() - platformMin.getZ() + 1;
        int expectedBits = width * height;
        int expectedBytes = (expectedBits + 7) / 8;
        int y = platformMin.getY(); // fallback for legacy data

        try {
            Inflater inflater = new Inflater();
            inflater.setInput(compressed);
            byte[] raw = new byte[expectedBytes + 4];
            int len = inflater.inflate(raw);
            inflater.end();

            if (len >= 4) {
                y = ((raw[0] & 0xFF) << 24) | ((raw[1] & 0xFF) << 16) | ((raw[2] & 0xFF) << 8) | (raw[3] & 0xFF);
                int bitDataLen = len - 4;
                if (bitDataLen > 0) {
                    byte[] bitData = new byte[bitDataLen];
                    System.arraycopy(raw, 4, bitData, 0, bitDataLen);
                    BitSet bits = BitSet.valueOf(java.nio.ByteBuffer.wrap(bitData));
                    for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
                        int relX = i / height;
                        int relZ = i % height;
                        result.add(new BlockPos(platformMin.getX() + relX, y, platformMin.getZ() + relZ));
                    }
                }
            } else if (len > 0) {
                // Legacy format without Y prefix: treat entire data as bitset
                BitSet bits = BitSet.valueOf(java.nio.ByteBuffer.wrap(raw, 0, len));
                for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
                    int relX = i / height;
                    int relZ = i % height;
                    result.add(new BlockPos(platformMin.getX() + relX, platformMin.getY(), platformMin.getZ() + relZ));
                }
            }
        } catch (DataFormatException e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to decompress selection bitmap", e);
        }
        return new java.util.AbstractMap.SimpleEntry<>(y, result);
    }

    // 供客户端内部使用：直接解压位图数据到 RTSSelection
    public static void decompressToSelection(byte[] compressed, BlockPos platformMin, BlockPos platformMax,
                                             java.util.function.Consumer<BlockPos> addCallback) {
        java.util.Map.Entry<Integer, java.util.Set<BlockPos>> entry = decompressBitmap(compressed, platformMin, platformMax);
        for (BlockPos pos : entry.getValue()) {
            addCallback.accept(pos);
        }
    }
}

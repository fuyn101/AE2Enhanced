package com.github.aeddddd.ae2enhanced.platform;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemPlatformDevelopmentLicense;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPlatformGenerateResult;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 平台异步放置器 —— 全局 tick 调度器.
 * 将 5×5 区块的平台生成拆分为多 tick 渐进执行,避免单 tick 卡顿.
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public class PlatformAsyncPlacer {

    private static final List<PlacerTask> activeTasks = new ArrayList<>();

    public static void startGeneration(EntityPlayerMP player, BlockPos center, int surfaceY, int sizeInChunks) {
        activeTasks.add(new PlacerTask(player, center, surfaceY, sizeInChunks));
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.side != Side.SERVER || event.phase != TickEvent.Phase.END) return;

        Iterator<PlacerTask> it = activeTasks.iterator();
        while (it.hasNext()) {
            PlacerTask task = it.next();
            if (task.isCancelled()) {
                it.remove();
                continue;
            }
            boolean done = task.tick();
            if (done) {
                it.remove();
            }
        }
    }

    public static class PlacerTask {

        enum Phase { SCANNING, SCAN_FAILED, PLACING, PLACING_CONTROLLER, COMPLETED, CANCELLED }

        private final World world;
        private final UUID playerId;
        private final BlockPos centerPos;
        private final int surfaceY;
        private final IBlockState surfaceState;
        private final IBlockState edgeState;
        private final int platformMinX;
        private final int platformMinZ;
        private final int platformSizeInChunks;
        private final int platformTotalWidth;

        private Phase phase = Phase.SCANNING;
        private int scanIndex = 0;
        private int placeIndex = 0;
        private final List<BlockPos> conflictList = new ArrayList<>();
        private final List<BlockPos> placeQueue = new ArrayList<>();

        public PlacerTask(EntityPlayerMP player, BlockPos center, int surfaceY, int sizeInChunks) {
            this.world = player.getServerWorld();
            this.playerId = player.getUniqueID();
            this.centerPos = center;
            this.surfaceY = surfaceY;
            this.platformSizeInChunks = sizeInChunks;
            this.platformTotalWidth = sizeInChunks * 16;
            this.surfaceState = parseBlockState(AE2EnhancedConfig.advancedPlatform.platformSurfaceBlock,
                    AE2EnhancedConfig.advancedPlatform.platformSurfaceMeta);
            this.edgeState = parseBlockState(AE2EnhancedConfig.advancedPlatform.platformEdgeBlock,
                    AE2EnhancedConfig.advancedPlatform.platformEdgeMeta);
            int centerChunkStartX = (centerPos.getX() >> 4) << 4;
            int centerChunkStartZ = (centerPos.getZ() >> 4) << 4;
            int halfChunks = (sizeInChunks - 1) / 2;
            this.platformMinX = centerChunkStartX - halfChunks * 16;
            this.platformMinZ = centerChunkStartZ - halfChunks * 16;
        }

        public boolean tick() {
            switch (phase) {
                case SCANNING:
                    return tickScanning();
                case PLACING:
                    return tickPlacing();
                case PLACING_CONTROLLER:
                    return tickPlaceController();
                case SCAN_FAILED:
                    return tickScanFailed();
                case COMPLETED:
                case CANCELLED:
                    return true;
            }
            return true;
        }

        private boolean tickScanning() {
            int total = platformTotalWidth * platformTotalWidth * 3;
            int scanPerTick = AE2EnhancedConfig.advancedPlatform.scanBlocksPerTick;

            int done = 0;
            while (scanIndex < total && done < scanPerTick) {
                int localIdx = scanIndex++;
                int x = platformMinX + (localIdx % platformTotalWidth);
                int z = platformMinZ + ((localIdx / platformTotalWidth) % platformTotalWidth);
                int y = surfaceY - 1 + (localIdx / (platformTotalWidth * platformTotalWidth));
                done++;

                BlockPos pos = new BlockPos(x, y, z);
                if (world.getTileEntity(pos) != null) {
                    conflictList.add(pos);
                    continue;
                }
                IBlockState state = world.getBlockState(pos);
                Block block = state.getBlock();
                if (block.getBlockHardness(state, world, pos) < 0) {
                    conflictList.add(pos);
                    continue;
                }
            }

            if (scanIndex >= total) {
                if (conflictList.isEmpty()) {
                    buildPlaceQueue();
                    phase = Phase.PLACING;
                } else {
                    phase = Phase.SCAN_FAILED;
                }
            }
            return false;
        }

        private void buildPlaceQueue() {
            for (int x = platformMinX; x <= platformMinX + platformTotalWidth - 1; x++) {
                for (int z = platformMinZ; z <= platformMinZ + platformTotalWidth - 1; z++) {
                    if (x == centerPos.getX() && z == centerPos.getZ()) continue;
                    placeQueue.add(new BlockPos(x, surfaceY, z));
                }
            }
        }

        private boolean tickPlacing() {
            int placed = 0;
            int placePerTick = AE2EnhancedConfig.advancedPlatform.placementBlocksPerTick;
            while (placeIndex < placeQueue.size() && placed < placePerTick) {
                BlockPos pos = placeQueue.get(placeIndex++);
                IBlockState state = determineState(pos);
                world.setBlockState(pos, state, 2);
                placed++;
            }

            if (placeIndex >= placeQueue.size()) {
                phase = Phase.PLACING_CONTROLLER;
            }
            return false;
        }

        private boolean tickPlaceController() {
            world.setBlockState(centerPos, BlockRegistry.ADVANCED_PLATFORM_CONTROLLER.getDefaultState(), 2);
            TileEntity te = world.getTileEntity(centerPos);
            if (te instanceof TileAdvancedPlatformController) {
                ((TileAdvancedPlatformController) te).activatePlatform(platformSizeInChunks, 0);
            }
            PlatformOverlapManager.get(world).registerPlatform(centerPos, platformSizeInChunks);
            phase = Phase.COMPLETED;
            return false;
        }

        private boolean tickScanFailed() {
            EntityPlayerMP player = world.getMinecraftServer().getPlayerList().getPlayerByUUID(playerId);
            if (player != null) {
                player.inventory.addItemStackToInventory(new ItemStack(ItemRegistry.PLATFORM_DEVELOPMENT_LICENSE));
                AE2Enhanced.network.sendTo(new PacketPlatformGenerateResult(false, conflictList), player);
            }
            return true;
        }

        public void cancel() {
            phase = Phase.CANCELLED;
        }

        public boolean isCancelled() {
            return phase == Phase.CANCELLED
                    || world.getMinecraftServer().getPlayerList().getPlayerByUUID(playerId) == null;
        }

        private IBlockState determineState(BlockPos pos) {
            // 每个 16×16 区块独立判断：西北角 15×15 为白色,其余为黑色
            int chunkStartX = (pos.getX() >> 4) << 4;
            int chunkStartZ = (pos.getZ() >> 4) << 4;
            int localX = pos.getX() - chunkStartX;
            int localZ = pos.getZ() - chunkStartZ;
            // 每个区块的 15×15 中心放黑色标记,最中心区块的中心是控制器(已在 placeQueue 中排除)
            if (localX == 7 && localZ == 7) {
                return edgeState;
            }
            boolean isWhite = localX < 15 && localZ < 15;
            return isWhite ? surfaceState : edgeState;
        }

        private static IBlockState parseBlockState(String registryName, int meta) {
            Block block = Block.getBlockFromName(registryName);
            if (block == null) block = net.minecraft.init.Blocks.CONCRETE;
            return block.getStateFromMeta(meta);
        }
    }
}

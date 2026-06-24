package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.WorldEvent;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 个人维度管理器：维度类型注册、ID 分配、传送、规则应用。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class PersonalDimensionManager {

    private PersonalDimensionManager() {}

    private static DimensionType PERSONAL_DIM_TYPE;
    private static boolean typeRegistered = false;

    /**
     * 在 preInit 阶段注册维度类型。
     */
    public static void registerDimensionType() {
        if (typeRegistered) return;
        // 从 5290 开始找一个未使用的 DimensionType id，避免与常见模组冲突
        int typeId = 5290;
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (DimensionType dt : DimensionType.values()) {
            used.add(dt.getId());
        }
        while (used.contains(typeId)) {
            typeId++;
        }
        PERSONAL_DIM_TYPE = DimensionType.register(
                AE2Enhanced.MOD_ID + ":personal_dim",
                "_pdim",
                typeId,
                WorldProviderPersonalDim.class,
                false
        );
        typeRegistered = true;
        AE2Enhanced.LOGGER.info("[AE2E] Registered personal dimension type with id {}", typeId);
    }

    @Nullable
    public static DimensionType getDimensionType() {
        return PERSONAL_DIM_TYPE;
    }

    public static boolean isPersonalDimension(int dimId) {
        if (PERSONAL_DIM_TYPE == null) return false;
        return DimensionManager.getProviderType(dimId) == PERSONAL_DIM_TYPE;
    }

    /**
     * 获取或创建玩家个人维度，返回维度 ID。
     */
    public static int getOrCreateDimension(EntityPlayerMP player) {
        if (PERSONAL_DIM_TYPE == null) return Integer.MIN_VALUE;
        World world = player.getServerWorld();
        PersonalDimensionData data = PersonalDimensionData.get(world);
        PlayerDimEntry entry = data.getEntry(player.getUniqueID());
        if (entry.dimensionId == Integer.MIN_VALUE) {
            int dimId = DimensionManager.getNextFreeDimId();
            DimensionManager.registerDimension(dimId, PERSONAL_DIM_TYPE);
            data.updateDimensionMapping(player.getUniqueID(), dimId);
            AE2Enhanced.LOGGER.info("[AE2E] Created personal dimension {} for player {}", dimId, player.getName());
        }
        return entry.dimensionId;
    }

    public static int getDimensionId(UUID playerId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return Integer.MIN_VALUE;
        return PersonalDimensionData.get(overworld).getEntry(playerId).dimensionId;
    }

    @Nullable
    public static PlayerDimEntry getEntry(UUID playerId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return null;
        return PersonalDimensionData.get(overworld).getEntry(playerId);
    }

    @Nullable
    public static PlayerDimEntry getEntryByDimension(int dimId) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return null;
        return PersonalDimensionData.get(overworld).getEntryByDimensionId(dimId);
    }

    public static void setEntryPoint(EntityPlayer player, BlockPos pos) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData.get(overworld).setEntryPoint(player.getUniqueID(), pos);
    }

    public static void setReturnPoint(EntityPlayerMP player) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData.get(overworld).setReturnPoint(
                player.getUniqueID(),
                player.dimension,
                player.posX, player.posY, player.posZ,
                player.rotationYaw, player.rotationPitch
        );
    }

    public static void teleportToReturnPoint(EntityPlayerMP player) {
        PlayerDimEntry entry = getEntry(player.getUniqueID());
        if (entry == null || !entry.hasReturnPoint) {
            // 没有记录则返回主世界出生点
            MinecraftServer server = player.getServerWorld().getMinecraftServer();
            WorldServer target = server != null ? server.getWorld(0) : null;
            if (target == null) return;
            BlockPos spawn = target.getSpawnPoint();
            teleportTo(player, 0, spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
            resetAbilities(player);
            return;
        }
        teleportTo(player, entry.returnDim, entry.returnX, entry.returnY, entry.returnZ, entry.returnYaw, entry.returnPitch);
        resetAbilities(player);
    }

    public static void teleportToDimension(EntityPlayerMP player, int dimId) {
        PlayerDimEntry entry = getEntry(player.getUniqueID());
        BlockPos entryPos = entry != null ? entry.entryPoint : new BlockPos(0, AE2EnhancedConfig.personalDimension.entryY, 0);
        double tx = entryPos.getX() + 0.5;
        double ty = entryPos.getY() + 0.1;
        double tz = entryPos.getZ() + 0.5;
        teleportTo(player, dimId, tx, ty, tz, player.rotationYaw, player.rotationPitch);
        scheduleRelight(player.getServerWorld().getMinecraftServer(), dimId, new BlockPos(tx, ty, tz));
    }

    private static void scheduleRelight(@Nullable MinecraftServer server, int dimId, BlockPos center) {
        if (server == null) return;
        server.addScheduledTask(() -> relightDimensionChunks(dimId, center));
        server.addScheduledTask(() -> server.addScheduledTask(() -> {
            // 延迟一tick后再做一次，确保区块已完全加载并同步到客户端
            relightDimensionChunks(dimId, center);
            WorldServer target = server.getWorld(dimId);
            if (target != null) {
                refreshSkyLight(target, center);
            }
        }));
    }

    private static void relightDimensionChunks(int dimId, BlockPos center) {
        WorldServer world = DimensionManager.getWorld(dimId);
        if (world == null) return;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(cx + dx, cz + dz);
                if (chunk == null) continue;
                chunk.checkLight();
                chunk.setLightPopulated(true);
                chunk.markDirty();
            }
        }
    }

    private static void refreshSkyLight(WorldServer world, BlockPos center) {
        int floorY = AE2EnhancedConfig.personalDimension.floorY;
        int startY = Math.min(center.getY(), floorY + 2);
        for (int dx = -32; dx <= 32; dx += 4) {
            for (int dz = -32; dz <= 32; dz += 4) {
                BlockPos pos = new BlockPos(center.getX() + dx, startY, center.getZ() + dz);
                world.checkLightFor(EnumSkyBlock.SKY, pos);
            }
        }
    }

    public static void teleportTo(EntityPlayerMP player, int dimId, double x, double y, double z, float yaw, float pitch) {
        if (player.dimension == dimId) {
            player.setPositionAndUpdate(x, y, z);
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;
        // 与 SimpleVoidWorld 一致：直接走 PlayerList.transferPlayerToDimension，
        // 由 Forge 负责跨维度实体迁移。先 init 目标世界避免未加载时为空。
        if (DimensionManager.isDimensionRegistered(dimId)) {
            DimensionManager.initDimension(dimId);
        }
        WorldServer targetWorld = server.getWorld(dimId);
        if (targetWorld == null) return;

        // 个人维度 WorldServer 重建后 entityId 计数器会从 0 开始，而玩家固定 ID 可能很低，
        // 后续目标世界生成新实体时容易与之冲突并触发 "Entity is already tracked!"。
        // 在传送前把计数器抬升到当前所有已加载实体（包括玩家）最大 ID 之后，避免冲突。
        com.github.aeddddd.ae2enhanced.mixin.late.world.MixinWorldServerLoadEntities.bumpEntityIdCounter(targetWorld, player.getEntityId());

        // 多次进出后，源/目标世界可能残留同 ID 的实体或旧的 tracker 记录，
        // 导致 transferPlayerToDimension 在 spawnEntity 阶段触发 "Entity is already tracked!"。
        // 在 transfer 前强制清理：untrack 当前玩家 + 移除同 ID 残留实体。
        WorldServer sourceWorld = server.getWorld(player.dimension);
        cleanupEntityTracker(sourceWorld, player);
        cleanupEntityTracker(targetWorld, player);

        server.getPlayerList().transferPlayerToDimension(player, dimId,
                new PersonalTeleporter(targetWorld, x, y, z, yaw, pitch));
    }

    private static void cleanupEntityTracker(@Nullable WorldServer world, EntityPlayerMP player) {
        if (world == null) return;
        try {
            world.getEntityTracker().untrack(player);
        } catch (Exception ignored) {
        }
        try {
            net.minecraft.entity.Entity existing = world.getEntityByID(player.getEntityId());
            if (existing != null && existing != player) {
                world.removeEntityDangerously(existing);
            }
        } catch (Exception ignored) {
        }
    }

    public static void setRules(UUID playerId, PersonalDimensionRules rules) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        data.setRules(playerId, rules);
        // 规则变更后立即同步给玩家，确保客户端 GUI 与维度状态一致
        sendRulesToPlayer(playerId);
    }

    private static void sendRulesToPlayer(UUID playerId) {
        if (AE2Enhanced.network == null) return;
        PlayerDimEntry entry = getEntry(playerId);
        if (entry == null) return;
        MinecraftServer server = net.minecraftforge.common.DimensionManager.getWorld(0) != null
                ? net.minecraftforge.common.DimensionManager.getWorld(0).getMinecraftServer()
                : null;
        if (server == null) return;
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player != null) {
            AE2Enhanced.network.sendTo(new com.github.aeddddd.ae2enhanced.network.packet.PacketPersonalDimensionRulesSync(entry.rules), player);
        }
    }

    @Nullable
    private static WorldServer getOverworld() {
        return net.minecraftforge.common.DimensionManager.getWorld(0);
    }

    /**
     * 服务端启动时重新注册已保存的个人维度。
     * 注意：FML 生命周期事件不在 MinecraftForge.EVENT_BUS 上，需要由 @Mod 主类调用。
     */
    public static void onServerStarted(FMLServerStartedEvent event) {
        if (PERSONAL_DIM_TYPE == null) return;
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        for (PlayerDimEntry entry : data.getAllEntries()) {
            if (entry.dimensionId == Integer.MIN_VALUE) continue;
            if (!DimensionManager.isDimensionRegistered(entry.dimensionId)) {
                DimensionManager.registerDimension(entry.dimensionId, PERSONAL_DIM_TYPE);
            }
        }
    }

    /**
     * 阻止个人维度内的生物自然生成。
     */
    @SubscribeEvent
    public static void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (event.getWorld().isRemote) return;
        int dim = event.getWorld().provider.getDimension();
        if (!isPersonalDimension(dim)) return;
        PlayerDimEntry entry = getEntryByDimension(dim);
        if (entry != null && entry.rules.disableMobSpawning) {
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    /**
     * 每 tick 应用天气/时间规则。
     */
    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        int dim = event.world.provider.getDimension();
        if (!isPersonalDimension(dim)) return;
        PlayerDimEntry entry = getEntryByDimension(dim);
        if (entry == null) return;

        World world = event.world;
        PersonalDimensionRules rules = entry.rules;
        if (rules.lockWeather) {
            if (world.isRaining()) world.getWorldInfo().setRaining(false);
            if (world.isThundering()) world.getWorldInfo().setThundering(false);
        }
        if (rules.lockTime || !rules.daylightCycle) {
            world.setWorldTime(rules.timeValue);
        }
        for (net.minecraft.entity.player.EntityPlayer player : world.playerEntities) {
            if (player instanceof EntityPlayerMP) {
                applyFlightRules((EntityPlayerMP) player, rules);
            }
        }
    }

    private static void applyFlightRules(EntityPlayerMP player, PersonalDimensionRules rules) {
        net.minecraft.entity.player.PlayerCapabilities cap = player.capabilities;
        boolean shouldFly = player.isCreative() || rules.flightEnabled;
        if (cap.allowFlying != shouldFly) {
            cap.allowFlying = shouldFly;
            if (!shouldFly) {
                cap.isFlying = false;
            }
            player.sendPlayerAbilities();
        }
        float speed = rules.movementSpeed;
        if (Math.abs(cap.getFlySpeed() - speed) > 1e-4f || Math.abs(cap.getWalkSpeed() - speed) > 1e-4f) {
            cap.setFlySpeed(speed);
            cap.setPlayerWalkSpeed(speed);
            player.sendPlayerAbilities();
        }
        if (rules.noFlightInertia && cap.isFlying) {
            if (player.moveForward == 0.0f && player.moveStrafing == 0.0f) {
                player.motionX = 0.0;
                player.motionZ = 0.0;
            }
        }
    }

    private static void resetAbilities(EntityPlayerMP player) {
        if (player.isCreative()) return;
        net.minecraft.entity.player.PlayerCapabilities cap = player.capabilities;
        cap.allowFlying = false;
        cap.isFlying = false;
        cap.setPlayerWalkSpeed(0.1f);
        cap.setFlySpeed(0.05f);
        player.sendPlayerAbilities();
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) return;
        if (!isPersonalDimension(event.getWorld().provider.getDimension())) return;
        // 世界卸载前 flush 已加载的脏 chunk，防止世界重建后读取到未保存的修改。
        WorldServer world = (WorldServer) event.getWorld();
        try {
            world.saveAllChunks(false, null);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to save personal dimension chunks on unload", e);
        }
    }

    /**
     * 个人维度 WorldServer 创建后不再强制 keep-loaded。
     * 需要常驻运行的区块请使用 FTB Utilities、ChickenChunks 等外部 chunk loader。
     */
    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        // 强制 keep-loaded 已被移除，原因：
        // 1) 玩家离开时同步 saveAllChunks(true) 会导致 MSPT 飙升、明显卡顿；
        // 2) 强制 keep-loaded 与 FTB Utilities 等外部 chunk loader 冲突，导致 claim 后仍不工作。
        // 玩家离开后世界自然卸载，Minecraft 会正常保存 chunk；需要机器继续运行的区块请外部 claim。
    }

    /**
     * 玩家在个人维度死亡并重生后恢复默认能力。
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player.world.isRemote) return;
        if (event.player instanceof EntityPlayerMP) {
            resetAbilities((EntityPlayerMP) event.player);
        }
    }

    /**
     * 玩家进入个人维度时预加载区块，并同步规则到客户端。
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (!isPersonalDimension(player.dimension)) return;
        WorldServer dimWorld = DimensionManager.getWorld(player.dimension);
        if (dimWorld == null) return;
        try {
            dimWorld.saveAllChunks(false, null);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to save personal dimension chunks on logout", e);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (isPersonalDimension(player.dimension)) {
            WorldServer dimWorld = DimensionManager.getWorld(player.dimension);
            if (dimWorld != null) {
                BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
                relightDimensionChunks(player.dimension, pos);
                refreshSkyLight(dimWorld, pos);
            }
            sendRulesToPlayer(player.getUniqueID());
        }
    }

    /**
     * 玩家切换维度时应用/重置个人维度能力，并同步规则。
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (isPersonalDimension(event.toDim)) {
            PlayerDimEntry entry = getEntry(player.getUniqueID());
            if (entry != null) {
                applyFlightRules(player, entry.rules);
                sendRulesToPlayer(player.getUniqueID());
            }
            // 通过指令或其他 mod 进入个人维度时，校正光照
            WorldServer dimWorld = player.getServerWorld();
            if (dimWorld != null) {
                BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
                relightDimensionChunks(event.toDim, pos);
                refreshSkyLight(dimWorld, pos);
            }
        } else if (isPersonalDimension(event.fromDim)) {
            resetAbilities(player);
            // 个人维度不再强制 keep-loaded，玩家离开后世界会自然卸载。
            // 为避免玩家高频进出时部分 chunk 尚未写入磁盘，先异步 flush 已加载的脏 chunk
            //（all=false 仅保存 dirty 的已加载 chunk，远低于 saveAllChunks(true) 的卡顿）。
            WorldServer dimWorld = DimensionManager.getWorld(event.fromDim);
            if (dimWorld != null) {
                try {
                    dimWorld.saveAllChunks(false, null);
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.error("[AE2E] Failed to save personal dimension chunks on leave", e);
                }
            }
        }
    }

    /**
     * 玩家 tick 中持续应用个人维度能力，防止进出维度时状态不同步。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        if (!isPersonalDimension(event.player.dimension)) return;
        PlayerDimEntry entry = getEntry(event.player.getUniqueID());
        if (entry != null) {
            applyFlightRules((EntityPlayerMP) event.player, entry.rules);
        }
    }

    /**
     * 简单的定点传送器，避免生成传送门。
     * 继承原版 Teleporter 以获得 Forge 最稳定的跨维度实体放置支持。
     */
    private static class PersonalTeleporter extends net.minecraft.world.Teleporter {
        private final double x, y, z;
        private final float yaw, pitch;

        PersonalTeleporter(WorldServer world, double x, double y, double z, float yaw, float pitch) {
            super(world);
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public void placeInPortal(net.minecraft.entity.Entity entity, float rotationYaw) {
            entity.setLocationAndAngles(x, y, z, this.yaw, this.pitch);
            entity.motionX = 0;
            entity.motionY = 0;
            entity.motionZ = 0;
            // 不再调用 setPositionAndUpdate，避免额外触发 chunk 加载/同步；
            // PlayerList.transferPlayerToDimension 后续会调用 setPlayerLocation 完成最终定位。
        }
    }
}

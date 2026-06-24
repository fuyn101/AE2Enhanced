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
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 个人维度管理器：维度类型注册、ID 分配、传送、规则应用。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class PersonalDimensionManager {

    private PersonalDimensionManager() {}

    private static DimensionType PERSONAL_DIM_TYPE;
    private static boolean typeRegistered = false;

    private static final Set<Integer> ISOLATED_WORLD_INFOS = new HashSet<>();
    private static final Field WORLD_INFO_FIELD = ReflectionHelper.findField(
            net.minecraft.world.World.class, "worldInfo", "field_72986_A");

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

    private static void preloadChunks(WorldServer world, BlockPos center) {
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getChunkProvider().provideChunk(cx + dx, cz + dz);
            }
        }
    }

    public static void teleportTo(EntityPlayerMP player, int dimId, double x, double y, double z, float yaw, float pitch) {
        if (player.dimension == dimId) {
            player.setPositionAndUpdate(x, y, z);
            return;
        }
        DimensionManager.initDimension(dimId);
        if (isPersonalDimension(dimId)) {
            // 通过核心或其他 mod 调用本方法进入个人维度时，立即保持加载并预生成目标区块
            DimensionManager.keepDimensionLoaded(dimId, true);
            WorldServer target = DimensionManager.getWorld(dimId);
            if (target != null) {
                preloadChunks(target, new BlockPos(x, y, z));
            }
        }
        player.changeDimension(dimId, new PersonalTeleporter(x, y, z, yaw, pitch));
    }

    public static void setRules(UUID playerId, PersonalDimensionRules rules) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData data = PersonalDimensionData.get(overworld);
        data.setRules(playerId, rules);
        // 规则变更后立即同步给玩家，确保客户端 GUI 与维度状态一致
        sendRulesToPlayer(playerId);

        // 如果玩家当前在其个人维度内，立即重新隔离 worldInfo（规则如 lockWeather 可能变化）
        PlayerDimEntry entry = data.getEntry(playerId);
        if (entry != null && entry.dimensionId != Integer.MIN_VALUE) {
            WorldServer dimWorld = DimensionManager.getWorld(entry.dimensionId);
            if (dimWorld != null) {
                ISOLATED_WORLD_INFOS.remove(entry.dimensionId);
                ensureIsolatedWorldInfo(dimWorld, entry);
            }
        }
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
            // 服务端启动时即把已存在的个人维度标记为常驻加载，避免登录/重载时世界对象被回收
            // 导致无线连接器等跨 tick 状态丢失
            try {
                DimensionManager.initDimension(entry.dimensionId);
                DimensionManager.keepDimensionLoaded(entry.dimensionId, true);
                AE2Enhanced.LOGGER.info("[AE2E] Kept personal dimension {} loaded on server start", entry.dimensionId);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to keep personal dimension {} loaded on server start", entry.dimensionId, e);
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

        // 将个人维度的 worldInfo 替换为隔离版本，防止时间/天气委托给主世界
        if (event.world instanceof WorldServer) {
            ensureIsolatedWorldInfo((WorldServer) event.world, entry);
        }

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

    private static void ensureIsolatedWorldInfo(WorldServer world, PlayerDimEntry entry) {
        int dim = world.provider.getDimension();
        if (ISOLATED_WORLD_INFOS.contains(dim)) return;
        if (world.getWorldInfo() instanceof PersonalDimensionWorldInfo) {
            ISOLATED_WORLD_INFOS.add(dim);
            return;
        }
        try {
            WorldInfo parent = world.getWorldInfo();
            PersonalDimensionWorldInfo isolated = new PersonalDimensionWorldInfo(
                    parent,
                    entry.rules,
                    () -> PersonalDimensionData.get(world).markDirty()
            );
            WORLD_INFO_FIELD.set(world, isolated);
            ISOLATED_WORLD_INFOS.add(dim);
            AE2Enhanced.LOGGER.info("[AE2E] Isolated world info for personal dimension {}", dim);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to isolate world info for personal dimension {}", dim, e);
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

    /**
     * 世界卸载时清理 worldInfo 隔离标记，确保下次加载（例如外部区块加载器重新加载）
     * 能够重新替换为隔离 WorldInfo。
     */
    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) return;
        int dim = event.getWorld().provider.getDimension();
        if (isPersonalDimension(dim)) {
            ISOLATED_WORLD_INFOS.remove(dim);
        }
    }

    /**
     * 个人维度 WorldServer 创建后立即替换 WorldInfo，避免玩家进入/登录后才隔离导致客户端不同步。
     */
    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load event) {
        if (event.getWorld().isRemote || !(event.getWorld() instanceof WorldServer)) return;
        int dim = event.getWorld().provider.getDimension();
        if (!isPersonalDimension(dim)) return;
        // 个人维度 WorldServer 一旦创建（无论是核心、指令还是其他 mod 传送触发的）就保持常驻，
        // 避免出去后世界对象被回收导致无线连接器等状态丢失
        try {
            DimensionManager.keepDimensionLoaded(dim, true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to keep personal dimension {} loaded on world load", dim, e);
        }
        PlayerDimEntry entry = getEntryByDimension(dim);
        if (entry != null) {
            ensureIsolatedWorldInfo((WorldServer) event.getWorld(), entry);
        }
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
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player.world.isRemote) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PlayerDimEntry entry = getEntry(player.getUniqueID());
        if (entry.dimensionId != Integer.MIN_VALUE) {
            // 玩家个人维度一旦加载就保持常驻，避免登录/重载时世界对象被回收
            // 导致区块、无线连接器等跨 tick 状态丢失
            try {
                DimensionManager.initDimension(entry.dimensionId);
                DimensionManager.keepDimensionLoaded(entry.dimensionId, true);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to keep personal dimension {} loaded for player {}", entry.dimensionId, player.getName(), e);
            }
        }
        if (isPersonalDimension(player.dimension)) {
            WorldServer dimWorld = DimensionManager.getWorld(player.dimension);
            if (dimWorld != null) {
                ensureIsolatedWorldInfo(dimWorld, getEntry(player.getUniqueID()));
                BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
                preloadChunks(dimWorld, pos);
                relightDimensionChunks(player.dimension, pos);
                refreshSkyLight(dimWorld, pos);
                AE2Enhanced.LOGGER.info("[AE2E] Preloaded personal dimension {} chunks around {}", player.dimension, pos);
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
            // 通过指令或其他 mod 进入个人维度时，确保目标区块已加载，防止落地虚空/无法移动
            WorldServer dimWorld = player.getServerWorld();
            if (dimWorld != null) {
                ensureIsolatedWorldInfo(dimWorld, getEntry(player.getUniqueID()));
                BlockPos pos = new BlockPos(player.posX, player.posY, player.posZ);
                preloadChunks(dimWorld, pos);
                relightDimensionChunks(event.toDim, pos);
                refreshSkyLight(dimWorld, pos);
                AE2Enhanced.LOGGER.info("[AE2E] Preloaded personal dimension {} chunks around {} after dimension change", event.toDim, pos);
            }
        } else if (isPersonalDimension(event.fromDim)) {
            resetAbilities(player);
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
     */
    private static class PersonalTeleporter implements ITeleporter {
        private final double x, y, z;
        private final float yaw, pitch;

        PersonalTeleporter(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public void placeEntity(World world, net.minecraft.entity.Entity entity, float yaw) {
            entity.setLocationAndAngles(x, y, z, this.yaw, this.pitch);
            entity.motionX = 0;
            entity.motionY = 0;
            entity.motionZ = 0;
            if (entity instanceof EntityPlayerMP) {
                ((EntityPlayerMP) entity).setPositionAndUpdate(x, y, z);
            }
        }

        @Override
        public boolean isVanilla() {
            return false;
        }
    }
}

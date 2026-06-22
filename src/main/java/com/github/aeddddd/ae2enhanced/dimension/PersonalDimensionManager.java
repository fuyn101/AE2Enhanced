package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

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
            return;
        }
        teleportTo(player, entry.returnDim, entry.returnX, entry.returnY, entry.returnZ, entry.returnYaw, entry.returnPitch);
    }

    public static void teleportToDimension(EntityPlayerMP player, int dimId) {
        PlayerDimEntry entry = getEntry(player.getUniqueID());
        BlockPos entryPos = entry != null ? entry.entryPoint : new BlockPos(0, AE2EnhancedConfig.personalDimension.entryY, 0);
        teleportTo(player, dimId, entryPos.getX() + 0.5, entryPos.getY() + 0.1, entryPos.getZ() + 0.5, player.rotationYaw, player.rotationPitch);
    }

    public static void teleportTo(EntityPlayerMP player, int dimId, double x, double y, double z, float yaw, float pitch) {
        if (player.dimension == dimId) {
            player.setPositionAndUpdate(x, y, z);
            return;
        }
        DimensionManager.initDimension(dimId);
        player.changeDimension(dimId, new PersonalTeleporter(x, y, z, yaw, pitch));
    }

    public static void setRules(UUID playerId, PersonalDimensionRules rules) {
        WorldServer overworld = getOverworld();
        if (overworld == null) return;
        PersonalDimensionData.get(overworld).setRules(playerId, rules);
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
            if (entry.dimensionId != Integer.MIN_VALUE && !DimensionManager.isDimensionRegistered(entry.dimensionId)) {
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
    }

    /**
     * 玩家进入个人维度时预加载区块。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player.world.isRemote) return;
        if (isPersonalDimension(event.player.dimension)) {
            DimensionManager.initDimension(event.player.dimension);
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

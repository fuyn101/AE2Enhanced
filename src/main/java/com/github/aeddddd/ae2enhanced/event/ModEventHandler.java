package com.github.aeddddd.ae2enhanced.event;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * AE2Enhanced 全局事件处理器
 */
public final class ModEventHandler {

    private ModEventHandler() {}

    private static final java.lang.reflect.Method DAMAGE_ENTITY_METHOD;
    static {
        java.lang.reflect.Method m = null;
        try {
            m = EntityLivingBase.class.getDeclaredMethod("func_70665_d", DamageSource.class, float.class);
            m.setAccessible(true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to cache damageEntity method", e);
        }
        DAMAGE_ENTITY_METHOD = m;
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new ModEventHandler());
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!"matter_cannon".equals(event.getSource().getDamageType())) return;
        if (event.getAmount() <= 1_000_000.0f) return;

        EntityLivingBase entity = event.getEntityLiving();
        World world = entity.world;
        double x = entity.posX;
        double y = entity.posY + entity.height / 2.0;
        double z = entity.posZ;

        // ① 粒子爆发
        if (!world.isRemote) {
            for (int i = 0; i < 10; i++) {
                world.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE,
                        x + (world.rand.nextDouble() - 0.5) * 2.0,
                        y + (world.rand.nextDouble() - 0.5) * 2.0,
                        z + (world.rand.nextDouble() - 0.5) * 2.0,
                        0.0, 0.0, 0.0);
            }
            for (int i = 0; i < 20; i++) {
                world.spawnParticle(EnumParticleTypes.PORTAL,
                        x + (world.rand.nextDouble() - 0.5) * 3.0,
                        y + (world.rand.nextDouble() - 0.5) * 3.0,
                        z + (world.rand.nextDouble() - 0.5) * 3.0,
                        world.rand.nextGaussian() * 0.5,
                        world.rand.nextGaussian() * 0.5,
                        world.rand.nextGaussian() * 0.5);
            }
            for (int i = 0; i < 20; i++) {
                world.spawnParticle(EnumParticleTypes.END_ROD,
                        x + (world.rand.nextDouble() - 0.5) * 2.0,
                        y + (world.rand.nextDouble() - 0.5) * 2.0,
                        z + (world.rand.nextDouble() - 0.5) * 2.0,
                        world.rand.nextGaussian() * 0.3,
                        world.rand.nextGaussian() * 0.3,
                        world.rand.nextGaussian() * 0.3);
            }
        }

        // ② 处决伤害：反射调用 damageEntity 绕过 Forge 事件系统
        if (DAMAGE_ENTITY_METHOD != null) {
            try {
                DamageSource exec = new DamageSource("ae2enhanced_conformal");
                exec.setDamageIsAbsolute();
                DAMAGE_ENTITY_METHOD.invoke(entity, exec, Float.MAX_VALUE);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Conformal damage reflection failed, falling back", e);
                entity.setHealth(0.0f);
            }
        } else {
            entity.setHealth(0.0f);
        }

        // ③ 虚空伤害
        if (DAMAGE_ENTITY_METHOD != null) {
            try {
                DAMAGE_ENTITY_METHOD.invoke(entity, DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Void damage reflection failed", e);
            }
        }

        // ④ 击退
        if (event.getSource().getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) event.getSource().getTrueSource();
            double dx = attacker.posX - entity.posX;
            double dz = attacker.posZ - entity.posZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.001) {
                dx /= dist;
                dz /= dist;
                entity.knockBack(attacker, 4.0f, -dx, -dz);
            }
        }

        // ⑤ 燃烧
        entity.setFire(10);
    }

    // ==================== RTS 强制退出 ====================

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player.world.isRemote) return;
        java.util.UUID uuid = event.player.getUniqueID();
        com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.C2SHandler.STATES.remove(uuid);
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().world.isRemote) return;
        if (!(event.getEntity() instanceof net.minecraft.entity.player.EntityPlayerMP)) return;
        net.minecraft.entity.player.EntityPlayerMP player = (net.minecraft.entity.player.EntityPlayerMP) event.getEntity();
        java.util.UUID uuid = player.getUniqueID();
        if (com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.C2SHandler.STATES.containsKey(uuid)) {
            com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.C2SHandler.STATES.remove(uuid);
            AE2Enhanced.network.sendTo(
                new com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange(
                    com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.ACTION_FORCE_EXIT),
                player
            );
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player.world.isRemote) return;
        java.util.UUID uuid = event.player.getUniqueID();
        if (com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.C2SHandler.STATES.containsKey(uuid)) {
            com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.C2SHandler.STATES.remove(uuid);
            AE2Enhanced.network.sendTo(
                new com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange(
                    com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.ACTION_FORCE_EXIT),
                (net.minecraft.entity.player.EntityPlayerMP) event.player
            );
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        net.minecraft.server.MinecraftServer server =
            net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) return;
        net.minecraft.server.management.PlayerList playerList = server.getPlayerList();
        if (playerList == null) return;

        java.util.Iterator<java.util.Map.Entry<java.util.UUID, com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.ServerRTSState>> it =
            com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.C2SHandler.STATES.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<java.util.UUID, com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.ServerRTSState> entry = it.next();
            net.minecraft.entity.player.EntityPlayerMP player = playerList.getPlayerByUUID(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.ServerRTSState state = entry.getValue();
            double margin = 8.0;
            if (player.posX < state.platformMin.getX() - margin ||
                player.posX > state.platformMax.getX() + 1 + margin ||
                player.posZ < state.platformMin.getZ() - margin ||
                player.posZ > state.platformMax.getZ() + 1 + margin) {
                it.remove();
                AE2Enhanced.network.sendTo(
                    new com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange(
                        com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange.ACTION_FORCE_EXIT),
                    player
                );
            }
        }
    }

}

package com.github.aeddddd.ae2enhanced.event;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import com.github.aeddddd.ae2enhanced.util.ForceKillHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        EntityPlayer player = event.getHarvester();
        if (player == null) return;
        ItemStack mainHand = player.getHeldItemMainhand();
        if (!(mainHand.getItem() instanceof ItemAdvancedMEOmniTool)) return;

        int dropMode = ItemAdvancedMEOmniTool.getDropMode(mainHand);
        if (dropMode == ItemAdvancedMEOmniTool.DROP_NORMAL) return;

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        event.setDropChance(0.0f);

        if (dropMode == ItemAdvancedMEOmniTool.DROP_INVENTORY) {
            for (ItemStack drop : drops) {
                if (!player.inventory.addItemStackToInventory(drop)) {
                    EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, drop);
                    player.world.spawnEntity(entityItem);
                }
            }
        } else if (dropMode == ItemAdvancedMEOmniTool.DROP_AE) {
            if (!ItemAdvancedMEOmniTool.isAEBound(mainHand)) {
                // 未绑定 AE，回退到正常掉落
                for (ItemStack drop : drops) {
                    EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, drop);
                    player.world.spawnEntity(entityItem);
                }
                return;
            }
            BlockPos txPos = ItemAdvancedMEOmniTool.getAETransmitterPos(mainHand);
            int txDim = ItemAdvancedMEOmniTool.getAETransmitterDim(mainHand);
            if (txPos == null || player.world.provider.getDimension() != txDim) {
                for (ItemStack drop : drops) {
                    EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, drop);
                    player.world.spawnEntity(entityItem);
                }
                return;
            }
            TileEntity te = player.world.getTileEntity(txPos);
            if (!(te instanceof TileWirelessChannelTransmitter)) {
                for (ItemStack drop : drops) {
                    EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, drop);
                    player.world.spawnEntity(entityItem);
                }
                return;
            }
            TileWirelessChannelTransmitter transmitter = (TileWirelessChannelTransmitter) te;
            IGridNode node = transmitter.getGridNode(AEPartLocation.INTERNAL);
            if (node == null || node.getGrid() == null) {
                for (ItemStack drop : drops) {
                    EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, drop);
                    player.world.spawnEntity(entityItem);
                }
                return;
            }
            IGrid grid = node.getGrid();
            appeng.api.networking.storage.IStorageGrid storage = (appeng.api.networking.storage.IStorageGrid) grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
            IMEMonitor<IAEItemStack> monitor = storage.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

            IActionSource source = new IActionSource() {
                @Override
                public Optional<EntityPlayer> player() {
                    return Optional.of(player);
                }
                @Override
                public Optional<appeng.api.networking.security.IActionHost> machine() {
                    return Optional.empty();
                }
                @Override
                public <T> Optional<T> context(Class<T> key) {
                    return Optional.empty();
                }
            };

            for (ItemStack drop : drops) {
                IAEItemStack aeStack = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(drop);
                if (aeStack != null) {
                    IAEItemStack leftover = monitor.injectItems(aeStack, appeng.api.config.Actionable.MODULATE, source);
                    if (leftover != null && leftover.getStackSize() > 0) {
                        ItemStack overflow = leftover.createItemStack();
                        EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, overflow);
                        player.world.spawnEntity(entityItem);
                    }
                } else {
                    EntityItem entityItem = new EntityItem(player.world, player.posX, player.posY, player.posZ, drop);
                    player.world.spawnEntity(entityItem);
                }
            }
        }
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

    // ==================== Anti-Heal ====================

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (ItemAdvancedMEOmniTool.hasAntiHeal(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    /**
     * 兜底：每 tick 末强制把带 anti-heal 且未死亡的实体标记为 isDead。
     * 防止某些具有自定义存活逻辑的实体通过覆盖 setDead / onLivingUpdate 等手段
     * 阻止自身被 World.updateEntities() 移除。
     */
    @SubscribeEvent
    public void onWorldTick(net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || event.world.isRemote) return;
        for (net.minecraft.entity.Entity entity : event.world.loadedEntityList) {
            if (entity instanceof EntityLivingBase && ItemAdvancedMEOmniTool.hasAntiHeal((EntityLivingBase) entity)) {
                if (!entity.isDead) {
                    ForceKillHelper.forceSetIsDead(entity, true);
                }
            }
        }
    }
}

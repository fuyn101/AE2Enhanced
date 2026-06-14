package com.github.aeddddd.ae2enhanced.item;

import appeng.api.features.INetworkEncodable;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.block.BlockWirelessChannelTransmitter;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.util.BossDropHelper;
import com.github.aeddddd.ae2enhanced.util.ForceKillHelper;
import com.github.aeddddd.ae2enhanced.util.TravelAnchorHelper;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementMode;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementTargetResolver;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementToolHelper;
import com.github.aeddddd.ae2enhanced.util.placement.SecurityTerminalBindingHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

import java.util.UUID;

public class ItemAdvancedMEOmniTool extends Item implements INetworkEncodable {

    // ---- NBT Keys ----
    public static final String NBT_MODE = "Mode";
    public static final String NBT_SILK = "SilkTouch";
    public static final String NBT_CHAOS = "ChaosCore";
    public static final String NBT_FORTUNE = "Fortune";
    public static final String NBT_TRAVEL = "TravelStaff";
    public static final String NBT_BLINK_DIST = "BlinkDist";
    public static final String NBT_LAST_BLINK = "LastBlink";
    public static final String NBT_BREAK_COOLDOWN = "BreakCooldown";
    public static final String NBT_LAST_BREAK = "LastBreak";
    public static final String NBT_DROP_MODE = "DropMode";
    public static final String NBT_AE_BOUND = "AEBound";
    public static final String NBT_AE_X = "AEX";
    public static final String NBT_AE_Y = "AEY";
    public static final String NBT_AE_Z = "AEZ";
    public static final String NBT_AE_DIM = "AEDim";
    public static final String NBT_TRAVEL_ANCHOR_BOUND = "TravelAnchorBound";
    public static final String NBT_TRAVEL_ANCHOR_X = "TravelAnchorX";
    public static final String NBT_TRAVEL_ANCHOR_Y = "TravelAnchorY";
    public static final String NBT_TRAVEL_ANCHOR_Z = "TravelAnchorZ";
    public static final String NBT_TRAVEL_ANCHOR_DIM = "TravelAnchorDim";
    public static final String NBT_ANTI_HEAL = "AE2E_AntiHeal";
    public static final String NBT_CONFORMAL = "ConformalCharge";
    public static final String NBT_PARAM_ENABLED = "ParamEnabled";
    public static final String NBT_CHAOS_FORCE_KILL = "ChaosForceKill";
    public static final String NBT_ADVANCED_SILK = "AdvancedSilkTouch";
    public static final String NBT_WALL_PHASE = "WallPhase";
    public static final String NBT_ENCHANTMENTS = "AE2E_Enchantments";

    // ---- Drop Modes ----
    public static final int DROP_NORMAL = 0;
    public static final int DROP_INVENTORY = 1;
    public static final int DROP_AE = 2;
    private static final String[] DROP_MODE_NAMES = {"normal", "inventory", "ae"};

    // ---- Modes ----
    public static final int MODE_COUNT = 4;
    public static final int MODE_UNIVERSAL = 0;
    public static final int MODE_PLACEMENT = 1;
    public static final int MODE_ROTATE = 2;
    public static final int MODE_TRAVEL = 3;

    private static final String[] MODE_NAMES = {
        "mode.universal", "mode.placement", "mode.rotate", "mode.travel"
    };

    // ---- Damage Sources ----
    public static final DamageSource OMNITOOL_DAMAGE =
        new DamageSource("ae2enhanced.omnitool").setDamageBypassesArmor();
    public static final DamageSource CHAOS_DAMAGE =
        new DamageSource("ae2enhanced.omnitool.chaos").setDamageBypassesArmor();

    // ---- DE Chaos Crystal class name (for reflection-free string comparison) ----
    private static final String DE_CHAOS_CRYSTAL_CLASS = "com.brandon3055.draconicevolution.blocks.ChaosCrystal";

    // ---- Blacklist cache ----
    private static java.util.Set<net.minecraft.util.ResourceLocation> blacklistCache = null;
    private static long blacklistCacheTime = -1L;

    // ---- Constants ----
    private static final float DESTROY_SPEED = 1_000_000.0f;
    private static final float CHAOS_DAMAGE_VALUE = 1000.0f;
    private static final int BLINK_COOLDOWN_TICKS = 1;
    private static final UUID REACH_MODIFIER_UUID = UUID.fromString("ae2e0000-0000-0000-0000-000000000001");

    public ItemAdvancedMEOmniTool() {
        setRegistryName(AE2Enhanced.MOD_ID, "me_omni_tool");
        setTranslationKey(AE2Enhanced.MOD_ID + ".me_omni_tool");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(1);
        // 扳手集成已移除
    }

    // ==================== Mining ====================

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        int mode = getMode(stack);
        if (mode != MODE_UNIVERSAL && mode != MODE_TRAVEL) return 1.0f;
        if (isBlacklisted(state.getBlock())) return 0.0f;
        return DESTROY_SPEED;
    }

    @Override
    public boolean canHarvestBlock(IBlockState state, ItemStack stack) {
        int mode = getMode(stack);
        if (mode != MODE_UNIVERSAL && mode != MODE_TRAVEL) return false;
        return !isBlacklisted(state.getBlock());
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass, EntityPlayer player, @Nullable IBlockState blockState) {
        int mode = getMode(stack);
        if (mode != MODE_UNIVERSAL && mode != MODE_TRAVEL) return -1;
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, EntityPlayer player) {
        int mode = getMode(stack);
        if (mode != MODE_UNIVERSAL && mode != MODE_TRAVEL) {
            return super.onBlockStartBreak(stack, pos, player);
        }

        // 混沌核心：允许破坏 DE 混沌水晶（即使 hardness = -1）
        if (hasChaosCore(stack) && isChaosCrystal(player.world, pos)) {
            IBlockState state = player.world.getBlockState(pos);
            Block block = state.getBlock();
            if (!player.world.isRemote) {
                block.dropBlockAsItem(player.world, pos, state, getFortuneLevel(stack));
                player.world.setBlockToAir(pos);
                block.breakBlock(player.world, pos, state);
            }
            return true;
        }

        int cooldown = getBreakCooldown(stack);
        if (cooldown > 0) {
            long now = player.world.getTotalWorldTime();
            long last = getLastBreakTick(stack);
            if (now - last < cooldown) {
                // 同步方块状态到客户端，防止幽灵方块
                if (!player.world.isRemote && player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                    ((net.minecraft.entity.player.EntityPlayerMP) player).connection.sendPacket(
                            new net.minecraft.network.play.server.SPacketBlockChange(player.world, pos));
                }
                return true;
            }
            setLastBreakTick(stack, now);
        }

        // 高级精准采集：保留方块 NBT 掉落
        if (isSilkTouchEnabled(stack) && isAdvancedSilkTouchEnabled(stack)) {
            return breakBlockWithNBT(stack, player.world, pos, player);
        }

        return super.onBlockStartBreak(stack, pos, player);
    }

    private static boolean breakBlockWithNBT(ItemStack stack, World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote) return true;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (isBlacklisted(block)) {
            return false;
        }

        // 1. 保存 TileEntity NBT
        TileEntity te = world.getTileEntity(pos);
        NBTTagCompound teNbt = null;
        if (te != null) {
            teNbt = new NBTTagCompound();
            te.writeToNBT(teNbt);
            teNbt.removeTag("x");
            teNbt.removeTag("y");
            teNbt.removeTag("z");
            teNbt.removeTag("id");
        }

        // 2. 移除 TileEntity，防止 breakBlock 额外掉落内容物
        world.removeTileEntity(pos);

        // 3. 获取掉落物
        List<ItemStack> drops = block.getDrops(world, pos, state, getFortuneLevel(stack));
        if (drops.isEmpty()) {
            Item item = Item.getItemFromBlock(block);
            if (item != null && item != net.minecraft.init.Items.AIR) {
                drops.add(new ItemStack(item, 1, block.damageDropped(state)));
            }
        }

        // 4. 给第一个掉落物附加 NBT
        if (teNbt != null && !drops.isEmpty()) {
            ItemStack mainDrop = drops.get(0);
            NBTTagCompound tag = mainDrop.hasTagCompound() ? mainDrop.getTagCompound() : new NBTTagCompound();
            tag.setTag("BlockEntityTag", teNbt);
            mainDrop.setTagCompound(tag);
        }

        // 5. 生成掉落物
        for (ItemStack drop : drops) {
            EntityItem entityItem = new EntityItem(world,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            entityItem.setPickupDelay(10);
            world.spawnEntity(entityItem);
        }

        // 6. 破坏方块并移除
        block.breakBlock(world, pos, state);
        world.setBlockToAir(pos);
        return true;
    }

    // ==================== Attack (Bypass Cooldown) ====================

    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
        // 通用守护水晶实体检测：类名包含 GuardianCrystal 或 CrystalEntity（覆盖 DE / dechaosislandlegacy 等）
        String className = entity.getClass().getName();
        if ((className.contains("GuardianCrystal") || className.endsWith("CrystalEntity"))
                && hasChaosCore(stack) && !entity.world.isRemote) {
            entity.setDead();
            return true;
        }

        // 处理多碰撞箱生物（如末影龙、混沌守卫）：点击的是 part，实际伤害 parent
        Entity targetEntity = entity;
        if (targetEntity instanceof net.minecraft.entity.MultiPartEntityPart) {
            targetEntity = (Entity) ((net.minecraft.entity.MultiPartEntityPart) targetEntity).parent;
        }

        if (targetEntity instanceof EntityLivingBase) {
            EntityLivingBase target = (EntityLivingBase) targetEntity;

            // Shift+左键：范围攻击
            if (player.isSneaking()) {
                performAreaAttack(stack, player, target, getFortuneLevel(stack));
                return true;
            }

            if (hasChaosCore(stack) && isChaosForceKillEnabled(stack)) {
                applyChaosDamage(target, player, getFortuneLevel(stack));
            } else {
                float baseDamage = (float) com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.omniTool.baseAttackDamage;
                applyTrueDamage(target, player, baseDamage, OMNITOOL_DAMAGE, getFortuneLevel(stack));
            }
            return true; // 阻止默认攻击逻辑（绕过攻击冷却衰减）
        }
        return super.onLeftClickEntity(stack, player, entity);
    }

    private static final double AOE_RADIUS = 4.0;

    private void performAreaAttack(ItemStack stack, EntityPlayer player, EntityLivingBase primaryTarget, int fortune) {
        if (player.world.isRemote) return;

        boolean chaosKill = hasChaosCore(stack) && isChaosForceKillEnabled(stack);
        float baseDamage = (float) AE2EnhancedConfig.omniTool.baseAttackDamage;

        AxisAlignedBB aoe = new AxisAlignedBB(
                primaryTarget.posX - AOE_RADIUS, primaryTarget.posY - AOE_RADIUS, primaryTarget.posZ - AOE_RADIUS,
                primaryTarget.posX + AOE_RADIUS, primaryTarget.posY + AOE_RADIUS, primaryTarget.posZ + AOE_RADIUS);

        List<EntityLivingBase> hits = player.world.getEntitiesWithinAABB(EntityLivingBase.class, aoe,
                e -> e != null && e.isEntityAlive() && e != player);

        // 确保主目标被包含且只处理一次
        if (!hits.contains(primaryTarget) && primaryTarget.isEntityAlive()) {
            hits.add(primaryTarget);
        }

        for (EntityLivingBase target : hits) {
            if (target == null || !target.isEntityAlive()) continue;
            if (chaosKill) {
                applyChaosDamage(target, player, fortune);
            } else {
                applyTrueDamage(target, player, baseDamage, OMNITOOL_DAMAGE, fortune);
            }
        }
    }

    /**
     * 应用混沌伤害：扣除配置指定的混沌伤害值，越过 LivingHurtEvent、护甲、药水、难度缩放、护盾等一切保护。
     * 视觉效果（受击动画、击退）保留在本方法中；核心强制击杀逻辑委托给 {@link ForceKillHelper}。
     */
    private void applyChaosDamage(EntityLivingBase target, EntityPlayer player, int fortune) {
        if (target.world.isRemote) return;
        if (target.getHealth() <= 0.0f) return;

        // 玩家特殊检查（唤醒睡眠）
        if (target instanceof EntityPlayer) {
            EntityPlayer targetPlayer = (EntityPlayer) target;
            if (targetPlayer.isPlayerSleeping() && !targetPlayer.world.isRemote) {
                targetPlayer.wakeUpPlayer(true, true, false);
            }
        }

        target.limbSwingAmount = 1.5f;
        target.setRevengeTarget(player);
        target.hurtResistantTime = target.maxHurtResistantTime;
        target.hurtTime = target.maxHurtTime;
        target.world.setEntityState(target, (byte) 2);
        double dx = player.posX - target.posX;
        double dz = player.posZ - target.posZ;
        while (dx * dx + dz * dz < 1.0E-4) {
            dx = (Math.random() - Math.random()) * 0.01;
            dz = (Math.random() - Math.random()) * 0.01;
        }
        target.attackedAtYaw = (float)(MathHelper.atan2(dz, dx) * 57.29577951308232 - (double)target.rotationYaw);
        target.knockBack(player, 0.4f, dx, dz);

        // 施加禁疗效果（必须在 onDeath 之前，因为 Mixin 注入会检查此标志）
        applyAntiHeal(target);

        // 设置玩家击杀标记，帮助自定义 Boss 掉落逻辑识别击杀来源
        markAsPlayerKill(target, player);

        // 核心强制击杀逻辑
        ForceKillHelper.applyForceKill(target, player, CHAOS_DAMAGE_VALUE, CHAOS_DAMAGE);

        // 尝试生成特殊 Boss 掉落物（如额外植物学盖亚 III 等自定义掉落实体）
        if (!target.world.isRemote && !target.isEntityAlive()) {
            BossDropHelper.trySpawnBossDrops(target, player, CHAOS_DAMAGE, fortune);
        }

        // 最后保险：如果实体仍然没有被移除，在下一 tick 开头强制从 world 剔除
        if (!target.world.isRemote && target.world.getMinecraftServer() != null) {
            final EntityLivingBase toRemove = target;
            target.world.getMinecraftServer().addScheduledTask(() -> {
                if (!toRemove.isDead && toRemove.world != null) {
                    try {
                        toRemove.world.removeEntityDangerously(toRemove);
                    } catch (Exception e) {
                        AE2Enhanced.LOGGER.error("[AE2E] removeEntityDangerously failed", e);
                    }
                }
            });
        }
    }

    // ==================== Anti-Heal ====================

    public static void applyAntiHeal(EntityLivingBase entity) {
        entity.getEntityData().setBoolean(NBT_ANTI_HEAL, true);
    }

    public static boolean hasAntiHeal(EntityLivingBase entity) {
        return entity.getEntityData().getBoolean(NBT_ANTI_HEAL);
    }

    public static void clearAntiHeal(EntityLivingBase entity) {
        entity.getEntityData().removeTag(NBT_ANTI_HEAL);
    }

    public static boolean hasConformalCharge(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_CONFORMAL);
    }

    public static void setConformalCharge(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_CONFORMAL, has);
    }

    private static boolean isChaosCrystal(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return DE_CHAOS_CRYSTAL_CLASS.equals(block.getClass().getName());
    }

    /**
     * 应用完全锁定的真实伤害：直接修改血量，绕过 LivingHurtEvent / LivingDamageEvent / 护甲 / 药水 / 难度缩放。
     */
    private void applyTrueDamage(EntityLivingBase target, EntityPlayer player, float damage, DamageSource source, int fortune) {
        if (target.world.isRemote) return;
        if (target.getHealth() <= 0.0f) return;

        // 玩家特殊检查（唤醒睡眠）
        if (target instanceof EntityPlayer) {
            EntityPlayer targetPlayer = (EntityPlayer) target;
            if (targetPlayer.isPlayerSleeping() && !targetPlayer.world.isRemote) {
                targetPlayer.wakeUpPlayer(true, true, false);
            }
        }

        target.limbSwingAmount = 1.5f;

        float newHealth = target.getHealth() - damage;

        // 复仇目标
        target.setRevengeTarget(player);

        // 受伤动画与无敌帧
        target.hurtResistantTime = target.maxHurtResistantTime;
        target.hurtTime = target.maxHurtTime;
        target.world.setEntityState(target, (byte) 2);

        // 击退
        double dx = player.posX - target.posX;
        double dz = player.posZ - target.posZ;
        while (dx * dx + dz * dz < 1.0E-4) {
            dx = (Math.random() - Math.random()) * 0.01;
            dz = (Math.random() - Math.random()) * 0.01;
        }
        target.attackedAtYaw = (float)(MathHelper.atan2(dz, dx) * 57.29577951308232 - (double)target.rotationYaw);
        target.knockBack(player, 0.4f, dx, dz);

        // 直接血量修改（绕过所有伤害计算事件和修饰）
        if (newHealth <= 0.0f) {
            // 设置玩家击杀标记，帮助自定义 Boss 掉落逻辑识别击杀来源
            markAsPlayerKill(target, player);
            target.setHealth(0.0f);
            target.onDeath(source);
            // 尝试生成特殊 Boss 掉落物
            if (!target.world.isRemote && !target.isEntityAlive()) {
                BossDropHelper.trySpawnBossDrops(target, player, source, fortune);
            }
        } else {
            target.setHealth(newHealth);
        }
    }

    /**
     * 反射设置 EntityLivingBase 的玩家击杀标记，帮助依赖该标记的 Boss 掉落逻辑正常触发。
     */
    private static void markAsPlayerKill(EntityLivingBase target, EntityPlayer player) {
        try {
            java.lang.reflect.Field attackingPlayer = EntityLivingBase.class.getDeclaredField("attackingPlayer");
            attackingPlayer.setAccessible(true);
            attackingPlayer.set(target, player);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to set attackingPlayer", e);
        }
        try {
            java.lang.reflect.Field recentlyHit = EntityLivingBase.class.getDeclaredField("recentlyHit");
            recentlyHit.setAccessible(true);
            recentlyHit.setInt(target, 100);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to set recentlyHit", e);
        }
    }

    // ==================== Item Use First (Universal Rotate) ====================

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, IBlockAccess world, BlockPos pos, EntityPlayer player) {
        int mode = getMode(stack);
        return mode == MODE_PLACEMENT || mode == MODE_UNIVERSAL;
    }

    // ==================== Right-Click on Block ====================

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) return EnumActionResult.SUCCESS;

        // 蹲下右键无线频道发生器：绑定 AE
        Block targetBlock = world.getBlockState(pos).getBlock();
        if (targetBlock instanceof BlockWirelessChannelTransmitter && player.isSneaking()) {
            setAEBound(stack, pos, world.provider.getDimension());
            player.sendStatusMessage(new TextComponentTranslation("message.ae2enhanced.omnitool.ae_bound", pos.getX(), pos.getY(), pos.getZ()), true);
            player.setHeldItem(hand, stack);
            return EnumActionResult.SUCCESS;
        }

        int mode = getMode(stack);
        switch (mode) {
            case MODE_UNIVERSAL:
                // 通用模式不再右键破坏方块，仅保留 Shift 右键绑定 AE（已在上方处理）
                return EnumActionResult.PASS;
            case MODE_PLACEMENT:
                return doPlacement(player, world, pos, facing, hand, stack, hitX, hitY, hitZ);
            case MODE_ROTATE:
                return doRotate(player, world, pos, facing);
            case MODE_TRAVEL:
                return doTravel(player, world, pos, hand, stack);
            default:
                return EnumActionResult.PASS;
        }
    }

    public static void forceBreakBlock(EntityPlayer player, World world, BlockPos pos, ItemStack stack) {
        if (world.isRemote) return;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (isBlacklisted(block)) return;

        int cooldown = getBreakCooldown(stack);
        if (cooldown > 0) {
            long now = world.getTotalWorldTime();
            long last = getLastBreakTick(stack);
            if (now - last < cooldown) {
                if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                    ((net.minecraft.entity.player.EntityPlayerMP) player).connection.sendPacket(
                            new net.minecraft.network.play.server.SPacketBlockChange(world, pos));
                }
                return;
            }
            setLastBreakTick(stack, now);
        }

        // 高级精准采集：保留方块 NBT 掉落
        if (isSilkTouchEnabled(stack) && isAdvancedSilkTouchEnabled(stack)) {
            breakBlockWithNBT(stack, world, pos, player);
            return;
        }

        // 普通破坏
        TileEntity te = world.getTileEntity(pos);
        if (player.capabilities.isCreativeMode) {
            world.setBlockToAir(pos);
        } else {
            block.harvestBlock(world, player, pos, state, te, stack);
        }
        world.playEvent(2001, pos, net.minecraft.block.Block.getStateId(state));
    }

    // ==================== Right-Click in Air ====================

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, stack);

        int mode = getMode(stack);
        if (mode == MODE_TRAVEL) {
            player.fallDistance = 0.0f; // 每次尝试位移都重置摔落伤害

            // 安装旅行手杖且已绑定锚点时，右键空气传送到绑定锚点
            if (hasTravelStaff(stack) && isTravelAnchorBound(stack)) {
                BlockPos target = getBoundTravelAnchorPos(stack);
                int targetDim = getBoundTravelAnchorDim(stack);
                if (target != null && world.provider.getDimension() == targetDim
                        && TravelAnchorHelper.teleportToAnchor(player, world, target)) {
                    player.swingArm(hand);
                    return new ActionResult<>(EnumActionResult.SUCCESS, stack);
                }
                player.sendStatusMessage(new TextComponentTranslation("message.ae2enhanced.omnitool.travel_anchor_unavailable"), true);
            }

            doBlink(player, world, stack);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        } else if (mode == MODE_PLACEMENT) {
            // 重做后：右键空气无动作；潜行右键清除线缆起点
            if (player.isSneaking()) {
                PlacementConfig config = new PlacementConfig(stack);
                if (config.getCableStart() != null) {
                    config.setCableStart(null);
                    return new ActionResult<>(EnumActionResult.SUCCESS, stack);
                }
            }
            return new ActionResult<>(EnumActionResult.PASS, stack);
        }
        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    // ==================== Placement Mode ====================

    private EnumActionResult doPlacement(EntityPlayer player, World world, BlockPos pos, EnumFacing facing, EnumHand hand,
                                         ItemStack stack, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return EnumActionResult.SUCCESS;

        PlacementConfig config = new PlacementConfig(stack);
        PlacementMode subMode = config.getPlacementMode();
        ItemStack target = PlacementTargetResolver.resolveSingleOrCable(player, config, world, pos);

        boolean ok;
        if (PlacementTargetResolver.isCable(target)) {
            // 线缆模式：右键设置起点；若已有起点则设终点并放置
            BlockPos start = config.getCableStart();
            if (start == null) {
                config.setCableStart(pos.offset(facing));
                return EnumActionResult.SUCCESS;
            } else {
                BlockPos end = pos.offset(facing);
                ok = PlacementToolHelper.placeCableBetween(player, world, start, end, hand, stack);
                config.setCableStart(null);
                return ok ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
            }
        } else if (subMode == PlacementMode.BULK) {
            ok = PlacementToolHelper.placeBulk(player, world, pos, facing, hand, stack, hitX, hitY, hitZ);
        } else {
            ok = PlacementToolHelper.placeSingle(player, world, pos, facing, hand, stack, hitX, hitY, hitZ);
        }
        return ok ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
    }

    // ==================== Rotate Mode ====================

    private EnumActionResult doRotate(EntityPlayer player, World world, BlockPos pos, EnumFacing facing) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        boolean rotated = block.rotateBlock(world, pos, facing);
        if (rotated) {
            player.swingArm(player.getActiveHand());
            return EnumActionResult.SUCCESS;
        }

        // PropertyDirection 手动循环回退
        for (IProperty<?> prop : state.getPropertyKeys()) {
            if (prop instanceof PropertyDirection) {
                PropertyDirection dirProp = (PropertyDirection) prop;
                EnumFacing current = state.getValue(dirProp);
                EnumFacing next = getNextFacing(current, facing, dirProp);
                if (next != null && next != current && dirProp.getAllowedValues().contains(next)) {
                    world.setBlockState(pos, state.withProperty(dirProp, next));
                    player.swingArm(player.getActiveHand());
                    return EnumActionResult.SUCCESS;
                }
            }
        }
        return EnumActionResult.PASS;
    }

    private EnumFacing getNextFacing(EnumFacing current, EnumFacing clickFace, PropertyDirection dirProp) {
        if (clickFace.getAxis() == EnumFacing.Axis.Y) {
            // 点击顶面/底面：先尝试绕 X 轴旋转，再绕 Z 轴，再取反
            EnumFacing next = current.rotateAround(EnumFacing.Axis.X);
            if (dirProp.getAllowedValues().contains(next)) return next;
            next = current.rotateAround(EnumFacing.Axis.Z);
            if (dirProp.getAllowedValues().contains(next)) return next;
            next = current.getOpposite();
            if (dirProp.getAllowedValues().contains(next)) return next;
        } else {
            // 点击侧面：绕 Y 轴旋转
            EnumFacing next = current.rotateY();
            if (dirProp.getAllowedValues().contains(next)) return next;
            next = current.rotateYCCW();
            if (dirProp.getAllowedValues().contains(next)) return next;
        }
        return null;
    }

    // ==================== Travel Mode ====================

    private EnumActionResult doTravel(EntityPlayer player, World world, BlockPos pos, EnumHand hand, ItemStack stack) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        player.fallDistance = 0.0f;

        // 安装旅行手杖且 Shift+右键 Travel Anchor 时将其绑定为传送目标
        if (hasTravelStaff(stack) && TravelAnchorHelper.isTravelAnchor(world, pos)) {
            if (player.isSneaking()) {
                setBoundTravelAnchor(stack, pos, world.provider.getDimension());
                player.sendStatusMessage(new TextComponentTranslation("message.ae2enhanced.omnitool.travel_anchor_bound", pos.getX(), pos.getY(), pos.getZ()), true);
                player.swingArm(hand);
                return EnumActionResult.SUCCESS;
            }
            // 非 Shift 右键不直接传送，允许锚点方块自身交互
            return EnumActionResult.PASS;
        }

        return doBlink(player, world, stack);
    }

    private EnumActionResult doBlink(EntityPlayer player, World world, ItemStack stack) {
        long now = world.getTotalWorldTime();
        long lastBlink = getLastBlink(stack);
        if (now - lastBlink < BLINK_COOLDOWN_TICKS) return EnumActionResult.PASS;

        double distance = getBlinkDistance(stack);
        Vec3d look = player.getLookVec();
        Vec3d start = player.getPositionEyes(1.0f);
        Vec3d end = start.add(look.x * distance, look.y * distance, look.z * distance);

        RayTraceResult ray = world.rayTraceBlocks(start, end, false, true, false);
        Vec3d target;
        if (ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK) {
            if (isWallPhaseEnabled(stack)) {
                // 尝试穿墙：穿过阻挡方块后继续搜索安全落点
                Vec3d through = ray.hitVec.add(look.scale(0.5));
                Vec3d safe = findSafePos(world, through, end, look, player);
                if (safe != null) {
                    target = safe;
                } else {
                    target = ray.hitVec.subtract(look.scale(0.5));
                }
            } else {
                // 不穿墙：在阻挡点前留出更大安全距离，减少卡在方块内
                target = ray.hitVec.subtract(look.scale(0.5));
            }
        } else {
            target = end;
        }

        // 防卡墙：根据视线方向增加偏移，并确保落点安全
        target = adjustLandingPosition(world, target, look, player);

        player.setPositionAndUpdate(target.x, target.y - player.getEyeHeight(), target.z);
        player.fallDistance = 0.0f;
        setLastBlink(stack, now);
        return EnumActionResult.SUCCESS;
    }

    /**
     * 调整落点以避免卡墙。向下看时额外抬高，并在不安全时向上搜索，
     * 同时尝试在落点周围小范围寻找可站立位置。
     */
    private Vec3d adjustLandingPosition(World world, Vec3d target, Vec3d look, EntityPlayer player) {
        // 向下看时额外抬高落点，避免卡在台阶/斜面/不完整方块内
        if (look.y < -0.1) {
            target = target.add(0, 0.25, 0);
        }

        // 优先尝试原落点，再尝试向上搜索，最后尝试水平微调
        double feetY = target.y - player.getEyeHeight();
        BlockPos basePos = new BlockPos(target.x, feetY, target.z);

        // 垂直搜索
        for (int dy = 0; dy <= 5; dy++) {
            BlockPos feetPos = basePos.up(dy);
            if (isSafeStandingPos(world, feetPos, player)) {
                return new Vec3d(target.x, feetPos.getY() + player.getEyeHeight(), target.z);
            }
        }

        // 水平微调（用于落点紧贴方块边缘的情况）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos feetPos = basePos.add(dx, 0, dz);
                if (isSafeStandingPos(world, feetPos, player)) {
                    return new Vec3d(feetPos.getX() + 0.5, feetPos.getY() + player.getEyeHeight(), feetPos.getZ() + 0.5);
                }
            }
        }

        return target;
    }

    /**
     * 在穿过阻挡点后向前搜索第一个安全的站立位置。
     */
    private Vec3d findSafePos(World world, Vec3d through, Vec3d maxEnd, Vec3d look, EntityPlayer player) {
        double remainingDist = through.distanceTo(maxEnd);
        double step = 0.5;
        int steps = (int) Math.ceil(remainingDist / step);

        for (int i = 0; i <= steps; i++) {
            Vec3d check = through.add(look.scale(i * step));
            if (check.distanceTo(through) > remainingDist + 0.01) break;

            BlockPos feetPos = new BlockPos(check);
            if (isSafeStandingPos(world, feetPos, player)) {
                return new Vec3d(check.x, feetPos.getY(), check.z);
            }
        }
        return null;
    }

    /**
     * 检查指定坐标是否为安全位置（使用实体碰撞箱检测，不要求脚下有地面）。
     */
    private boolean isSafeStandingPos(World world, BlockPos pos, EntityPlayer player) {
        IBlockState feet = world.getBlockState(pos);
        IBlockState head = world.getBlockState(pos.up());
        // 完整方块碰撞箱直接判定为不安全
        if (feet.getBlock().getCollisionBoundingBox(feet, world, pos) != null) return false;
        if (head.getBlock().getCollisionBoundingBox(head, world, pos.up()) != null) return false;
        // 使用玩家碰撞箱进一步确认
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        AxisAlignedBB box = player.getEntityBoundingBox()
                .offset(x - player.posX, y - player.posY, z - player.posZ);
        return world.getCollisionBoxes(player, box).isEmpty();
    }

    // ==================== Mode ====================

    public static int getMode(ItemStack stack) {
        if (!stack.hasTagCompound()) return MODE_UNIVERSAL;
        return stack.getTagCompound().getInteger(NBT_MODE);
    }

    public static void setMode(ItemStack stack, int mode) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(NBT_MODE, mode % MODE_COUNT);
    }

    public static void cycleMode(ItemStack stack) {
        setMode(stack, getMode(stack) + 1);
    }

    public static String getModeNameKey(int mode) {
        return "item.ae2enhanced.me_omni_tool." + MODE_NAMES[mode % MODE_COUNT];
    }

    // ==================== Drop Mode ====================

    public static int getDropMode(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getInteger(NBT_DROP_MODE) : DROP_NORMAL;
    }

    public static void setDropMode(ItemStack stack, int mode) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(NBT_DROP_MODE, mode % 3);
    }

    public static void cycleDropMode(ItemStack stack) {
        setDropMode(stack, getDropMode(stack) + 1);
    }

    public static String getDropModeNameKey(int mode) {
        return "item.ae2enhanced.me_omni_tool.drop_mode." + DROP_MODE_NAMES[mode % 3];
    }

    // ==================== AE Binding ====================

    // INetworkEncodable —— 用于放置模式的安全终端绑定（不影响原有的无线频道发射器绑定）
    @Override
    public String getEncryptionKey(ItemStack item) {
        return SecurityTerminalBindingHelper.getEncryptionKey(item);
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        SecurityTerminalBindingHelper.setEncryptionKey(item, encKey);
    }

    public static boolean isAEBound(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_AE_BOUND);
    }

    public static void setAEBound(ItemStack stack, BlockPos pos, int dim) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        tag.setBoolean(NBT_AE_BOUND, true);
        tag.setInteger(NBT_AE_X, pos.getX());
        tag.setInteger(NBT_AE_Y, pos.getY());
        tag.setInteger(NBT_AE_Z, pos.getZ());
        tag.setInteger(NBT_AE_DIM, dim);
    }

    public static BlockPos getAETransmitterPos(ItemStack stack) {
        if (!isAEBound(stack)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return new BlockPos(tag.getInteger(NBT_AE_X), tag.getInteger(NBT_AE_Y), tag.getInteger(NBT_AE_Z));
    }

    public static int getAETransmitterDim(ItemStack stack) {
        if (!isAEBound(stack)) return Integer.MIN_VALUE;
        return stack.getTagCompound().getInteger(NBT_AE_DIM);
    }

    public static void clearAEBinding(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            tag.removeTag(NBT_AE_BOUND);
            tag.removeTag(NBT_AE_X);
            tag.removeTag(NBT_AE_Y);
            tag.removeTag(NBT_AE_Z);
            tag.removeTag(NBT_AE_DIM);
            if (tag.getSize() == 0) {
                stack.setTagCompound(null);
            }
        }
    }

    // ==================== Travel Anchor Binding ====================

    public static boolean isTravelAnchorBound(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_TRAVEL_ANCHOR_BOUND);
    }

    public static void setBoundTravelAnchor(ItemStack stack, BlockPos pos, int dim) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound tag = stack.getTagCompound();
        tag.setBoolean(NBT_TRAVEL_ANCHOR_BOUND, true);
        tag.setInteger(NBT_TRAVEL_ANCHOR_X, pos.getX());
        tag.setInteger(NBT_TRAVEL_ANCHOR_Y, pos.getY());
        tag.setInteger(NBT_TRAVEL_ANCHOR_Z, pos.getZ());
        tag.setInteger(NBT_TRAVEL_ANCHOR_DIM, dim);
    }

    public static BlockPos getBoundTravelAnchorPos(ItemStack stack) {
        if (!isTravelAnchorBound(stack)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return new BlockPos(tag.getInteger(NBT_TRAVEL_ANCHOR_X), tag.getInteger(NBT_TRAVEL_ANCHOR_Y), tag.getInteger(NBT_TRAVEL_ANCHOR_Z));
    }

    public static int getBoundTravelAnchorDim(ItemStack stack) {
        if (!isTravelAnchorBound(stack)) return Integer.MIN_VALUE;
        return stack.getTagCompound().getInteger(NBT_TRAVEL_ANCHOR_DIM);
    }

    public static void clearBoundTravelAnchor(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTTagCompound tag = stack.getTagCompound();
            tag.removeTag(NBT_TRAVEL_ANCHOR_BOUND);
            tag.removeTag(NBT_TRAVEL_ANCHOR_X);
            tag.removeTag(NBT_TRAVEL_ANCHOR_Y);
            tag.removeTag(NBT_TRAVEL_ANCHOR_Z);
            tag.removeTag(NBT_TRAVEL_ANCHOR_DIM);
            if (tag.getSize() == 0) {
                stack.setTagCompound(null);
            }
        }
    }

    // ==================== Silk Touch ====================

    public static boolean isSilkTouchEnabled(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_SILK);
    }

    public static void setSilkTouchEnabled(ItemStack stack, boolean enabled) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_SILK, enabled);
        updateEnchantments(stack);
    }

    public static void toggleSilkTouch(ItemStack stack) {
        setSilkTouchEnabled(stack, !isSilkTouchEnabled(stack));
    }

    public static boolean isAdvancedSilkTouchEnabled(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_ADVANCED_SILK);
    }

    public static void setAdvancedSilkTouchEnabled(ItemStack stack, boolean enabled) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_ADVANCED_SILK, enabled);
    }

    // ==================== Upgrades ====================

    public static boolean hasChaosCore(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_CHAOS);
    }

    public static void setChaosCore(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_CHAOS, has);
    }

    public static boolean isChaosForceKillEnabled(ItemStack stack) {
        if (!stack.hasTagCompound()) return true;
        if (!stack.getTagCompound().hasKey(NBT_CHAOS_FORCE_KILL)) return true;
        return stack.getTagCompound().getBoolean(NBT_CHAOS_FORCE_KILL);
    }

    public static void setChaosForceKillEnabled(ItemStack stack, boolean enabled) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_CHAOS_FORCE_KILL, enabled);
    }

    public static boolean hasFortuneUpgrade(ItemStack stack) {
        return getFortuneLevel(stack) > 0;
    }

    public static int getFortuneLevel(ItemStack stack) {
        return getStoredEnchantmentLevel(stack, (short) Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.FORTUNE));
    }

    public static void setFortuneLevel(ItemStack stack, int level) {
        setStoredEnchantmentLevel(stack, (short) Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.FORTUNE), level);
    }

    // ==================== Stored Enchantments (from Enchanted Book) ====================

    public static boolean hasStoredEnchantments(ItemStack stack) {
        return getStoredEnchantments(stack).tagCount() > 0;
    }

    public static NBTTagList getStoredEnchantments(ItemStack stack) {
        if (!stack.hasTagCompound()) return new NBTTagList();
        NBTTagCompound tag = stack.getTagCompound();

        // 从旧版 NBT_FORTUNE 迁移
        if (tag.hasKey(NBT_FORTUNE, net.minecraftforge.common.util.Constants.NBT.TAG_INT) && !tag.hasKey(NBT_ENCHANTMENTS)) {
            int fortune = tag.getInteger(NBT_FORTUNE);
            if (fortune > 0) {
                NBTTagList list = new NBTTagList();
                NBTTagCompound ench = new NBTTagCompound();
                ench.setShort("id", (short) Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.FORTUNE));
                ench.setShort("lvl", (short) fortune);
                ench.setShort("max", (short) fortune);
                list.appendTag(ench);
                tag.setTag(NBT_ENCHANTMENTS, list);
            }
            tag.removeTag(NBT_FORTUNE);
        }

        return tag.getTagList(NBT_ENCHANTMENTS, net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
    }

    public static int getStoredEnchantmentLevel(ItemStack stack, short enchantmentId) {
        NBTTagList list = getStoredEnchantments(stack);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getShort("id") == enchantmentId) {
                return tag.getShort("lvl");
            }
        }
        return 0;
    }

    public static int getEnchantmentSourceLevel(ItemStack stack, short enchantmentId) {
        NBTTagList list = getStoredEnchantments(stack);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getShort("id") == enchantmentId) {
                return tag.hasKey("max", net.minecraftforge.common.util.Constants.NBT.TAG_SHORT)
                        ? tag.getShort("max") : tag.getShort("lvl");
            }
        }
        return 0;
    }

    public static void setStoredEnchantmentLevel(ItemStack stack, short enchantmentId, int level) {
        NBTTagList list = getStoredEnchantments(stack);
        boolean found = false;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if (tag.getShort("id") == enchantmentId) {
                int max = tag.hasKey("max", net.minecraftforge.common.util.Constants.NBT.TAG_SHORT)
                        ? tag.getShort("max") : tag.getShort("lvl");
                if (level <= 0) {
                    list.removeTag(i);
                } else {
                    tag.setShort("lvl", (short) Math.min(level, max));
                }
                found = true;
                break;
            }
        }
        if (!found && level > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", enchantmentId);
            tag.setShort("lvl", (short) level);
            tag.setShort("max", (short) level);
            list.appendTag(tag);
        }
        setStoredEnchantments(stack, list);
    }

    public static void setStoredEnchantments(ItemStack stack, NBTTagList list) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        if (list == null || list.tagCount() == 0) {
            stack.getTagCompound().removeTag(NBT_ENCHANTMENTS);
        } else {
            stack.getTagCompound().setTag(NBT_ENCHANTMENTS, list);
        }
        updateEnchantments(stack);
    }

    public static NBTTagList copyEnchantmentsFromBook(ItemStack book) {
        NBTTagList result = new NBTTagList();
        if (!book.hasTagCompound()) return result;
        NBTTagList stored = book.getTagCompound().getTagList("StoredEnchantments", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < stored.tagCount(); i++) {
            NBTTagCompound src = stored.getCompoundTagAt(i);
            short lvl = src.getShort("lvl");
            short max = AE2EnhancedConfig.omniTool.maxEnchantmentLevel > 0
                    ? (short) Math.min(lvl, AE2EnhancedConfig.omniTool.maxEnchantmentLevel)
                    : lvl;
            NBTTagCompound dst = new NBTTagCompound();
            dst.setShort("id", src.getShort("id"));
            dst.setShort("lvl", max);
            dst.setShort("max", max);
            result.appendTag(dst);
        }
        return result;
    }

    public static boolean hasTravelStaff(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_TRAVEL);
    }

    public static void setTravelStaff(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_TRAVEL, has);
    }

    public static boolean isWallPhaseEnabled(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            return com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.omniTool.enableWallPhase;
        }
        if (!stack.getTagCompound().hasKey(NBT_WALL_PHASE)) {
            return com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.omniTool.enableWallPhase;
        }
        return stack.getTagCompound().getBoolean(NBT_WALL_PHASE);
    }

    public static void setWallPhaseEnabled(ItemStack stack, boolean enabled) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_WALL_PHASE, enabled);
    }

    // ==================== Param Enabled ====================

    public static boolean isParamEnabled(ItemStack stack, int paramIdx) {
        if (paramIdx < 0 || paramIdx > 31) return true;
        if (!stack.hasTagCompound()) return true;
        int mask = stack.getTagCompound().getInteger(NBT_PARAM_ENABLED);
        if (mask == 0 && !stack.getTagCompound().hasKey(NBT_PARAM_ENABLED)) return true;
        return (mask & (1 << paramIdx)) != 0;
    }

    public static void setParamEnabled(ItemStack stack, int paramIdx, boolean enabled) {
        if (paramIdx < 0 || paramIdx > 31) return;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        int mask = stack.getTagCompound().getInteger(NBT_PARAM_ENABLED);
        if (enabled) mask |= (1 << paramIdx);
        else mask &= ~(1 << paramIdx);
        stack.getTagCompound().setInteger(NBT_PARAM_ENABLED, mask);
    }

    // ==================== Blink Distance / Cooldown ====================

    public static double getBlinkDistance(ItemStack stack) {
        double max = com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.omniTool.maxBlinkDistance;
        if (!stack.hasTagCompound()) return max;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_BLINK_DIST, net.minecraftforge.common.util.Constants.NBT.TAG_DOUBLE)) {
            tag.setDouble(NBT_BLINK_DIST, max);
        }
        double dist = tag.getDouble(NBT_BLINK_DIST);
        return Math.min(dist, max);
    }

    public static void setBlinkDistance(ItemStack stack, double dist) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setDouble(NBT_BLINK_DIST, dist);
    }

    private static long getLastBlink(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getLong(NBT_LAST_BLINK) : 0;
    }

    private static void setLastBlink(ItemStack stack, long tick) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setLong(NBT_LAST_BLINK, tick);
    }

    // ==================== Break Cooldown ====================

    public static int getBreakCooldown(ItemStack stack) {
        int max = com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.omniTool.maxBreakCooldown;
        int cooldown = stack.hasTagCompound() ? stack.getTagCompound().getInteger(NBT_BREAK_COOLDOWN) : max;
        return Math.min(cooldown, max);
    }

    public static void setBreakCooldown(ItemStack stack, int ticks) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(NBT_BREAK_COOLDOWN, ticks);
    }

    private static long getLastBreakTick(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getLong(NBT_LAST_BREAK) : 0;
    }

    private static void setLastBreakTick(ItemStack stack, long tick) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setLong(NBT_LAST_BREAK, tick);
    }

    // ==================== Enchantment Sync ====================

    private static void updateEnchantments(ItemStack stack) {
        NBTTagList enchList = new NBTTagList();

        // 从书中导入的附魔（以存储区为准）
        NBTTagList stored = getStoredEnchantments(stack);
        for (int i = 0; i < stored.tagCount(); i++) {
            NBTTagCompound src = stored.getCompoundTagAt(i);
            short id = src.getShort("id");
            short lvl = src.getShort("lvl");
            if (lvl <= 0) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", id);
            tag.setShort("lvl", lvl);
            enchList.appendTag(tag);
        }

        // 工具自带的精准采集开关（若书中已有时运/精准采集，以书中的为准，避免冲突时重复生成）
        boolean hasSilkTouch = false;
        boolean hasFortune = false;
        for (int i = 0; i < enchList.tagCount(); i++) {
            short id = enchList.getCompoundTagAt(i).getShort("id");
            if (id == Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.SILK_TOUCH)) hasSilkTouch = true;
            if (id == Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.FORTUNE)) hasFortune = true;
        }

        if (isSilkTouchEnabled(stack) && !hasSilkTouch) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", (short) Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.SILK_TOUCH));
            tag.setShort("lvl", (short) 1);
            enchList.appendTag(tag);
        }

        // 清理过时的 NBT_FORTUNE（迁移逻辑已在 getStoredEnchantments 中处理）
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(NBT_FORTUNE)) {
            stack.getTagCompound().removeTag(NBT_FORTUNE);
        }

        if (enchList.tagCount() > 0) {
            if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound().setTag("ench", enchList);
        } else if (stack.hasTagCompound()) {
            stack.getTagCompound().removeTag("ench");
        }
    }

    // ==================== Tooltip ====================

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        int mode = getMode(stack);
        String modeName = I18n.format(getModeNameKey(mode));

        tooltip.add(TextFormatting.AQUA + "━━━━━━━━━━━━━━━━━━━━");
        tooltip.add(TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.mode", TextFormatting.YELLOW + modeName));

        if (isSilkTouchEnabled(stack)) {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.silk_touch.on"));
        } else {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.silk_touch.off"));
        }

        if (mode == MODE_TRAVEL) {
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                + I18n.format("item.ae2enhanced.me_omni_tool.blink_dist", TextFormatting.YELLOW + String.format("%.1f", getBlinkDistance(stack))));
            if (isWallPhaseEnabled(stack)) {
                tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.wall_phase.on"));
            } else {
                tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.wall_phase.off"));
            }
        }

        if (mode == MODE_UNIVERSAL) {
            int cooldown = getBreakCooldown(stack);
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                + I18n.format("item.ae2enhanced.me_omni_tool.break_cooldown", TextFormatting.YELLOW + String.valueOf(cooldown)));

            int dropMode = getDropMode(stack);
            String dropModeName = I18n.format(getDropModeNameKey(dropMode));
            tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                + I18n.format("item.ae2enhanced.me_omni_tool.drop_mode", TextFormatting.YELLOW + dropModeName));
        }

        if (mode == MODE_PLACEMENT) {
            PlacementConfig config = new PlacementConfig(stack);
            ItemStack selected = config.getSelectedStack();
            if (!selected.isEmpty()) {
                tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                    + I18n.format("item.ae2enhanced.me_omni_tool.placement.selected",
                            TextFormatting.YELLOW + selected.getDisplayName(),
                            I18n.format("gui.ae2enhanced.placement.mode." + config.getPlacementMode().name().toLowerCase())));
            } else {
                tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                    + I18n.format("item.ae2enhanced.me_omni_tool.placement.no_selection"));
            }
            if (SecurityTerminalBindingHelper.isLinked(stack)) {
                tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                    + I18n.format("item.ae2enhanced.me_omni_tool.placement.linked"));
            } else {
                tooltip.add(TextFormatting.GRAY + "▸ " + TextFormatting.WHITE
                    + I18n.format("item.ae2enhanced.me_omni_tool.placement.unlinked"));
            }
        }

        tooltip.add(TextFormatting.AQUA + "━━━━━━━━━━━━━━━━━━━━");

        boolean hasUpgrades = false;
        if (hasChaosCore(stack)) {
            tooltip.add(TextFormatting.GOLD + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.chaos"));
            hasUpgrades = true;
        }
        NBTTagList storedEnch = getStoredEnchantments(stack);
        for (int i = 0; i < storedEnch.tagCount(); i++) {
            NBTTagCompound tag = storedEnch.getCompoundTagAt(i);
            short id = tag.getShort("id");
            short lvl = tag.getShort("lvl");
            Enchantment ench = Enchantment.getEnchantmentByID(id);
            String name = ench != null ? ench.getTranslatedName(lvl) : I18n.format("item.ae2enhanced.me_omni_tool.unknown_enchant", id, lvl);
            tooltip.add(TextFormatting.GREEN + "● " + TextFormatting.WHITE + name);
            hasUpgrades = true;
        }
        if (hasTravelStaff(stack)) {
            tooltip.add(TextFormatting.LIGHT_PURPLE + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.travel"));
            hasUpgrades = true;
        }
        if (hasConformalCharge(stack)) {
            tooltip.add(TextFormatting.AQUA + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.conformal"));
            hasUpgrades = true;
        }
        if (!hasUpgrades) {
            tooltip.add(TextFormatting.GRAY + I18n.format("item.ae2enhanced.me_omni_tool.no_upgrades"));
        }

        if (isTravelAnchorBound(stack)) {
            BlockPos anchorPos = getBoundTravelAnchorPos(stack);
            int anchorDim = getBoundTravelAnchorDim(stack);
            tooltip.add(TextFormatting.LIGHT_PURPLE + "● " + TextFormatting.WHITE
                + I18n.format("item.ae2enhanced.me_omni_tool.travel_anchor_bound", anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(), anchorDim));
        }

        if (isAEBound(stack)) {
            BlockPos aePos = getAETransmitterPos(stack);
            int aeDim = getAETransmitterDim(stack);
            tooltip.add(TextFormatting.DARK_AQUA + "● " + TextFormatting.WHITE
                + I18n.format("item.ae2enhanced.me_omni_tool.ae_bound", aePos.getX(), aePos.getY(), aePos.getZ(), aeDim));
        }
    }

    // ==================== Item Entity Protection (Conformal Charge) ====================

    private static final java.lang.reflect.Field ENTITY_IMMUNE_TO_FIRE;
    static {
        java.lang.reflect.Field f = null;
        try {
            f = Entity.class.getDeclaredField("isImmuneToFire");
            f.setAccessible(true);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to cache isImmuneToFire field", e);
        }
        ENTITY_IMMUNE_TO_FIRE = f;
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem entityItem) {
        ItemStack stack = entityItem.getItem();
        if (hasConformalCharge(stack)) {
            if (!entityItem.getEntityData().getBoolean("AE2E_ConformalInit")) {
                entityItem.getEntityData().setBoolean("AE2E_ConformalInit", true);
                if (ENTITY_IMMUNE_TO_FIRE != null) {
                    try {
                        ENTITY_IMMUNE_TO_FIRE.setBoolean(entityItem, true);
                    } catch (Exception ignored) {}
                }
                entityItem.setEntityInvulnerable(true);
                entityItem.setNoDespawn();
            }
            entityItem.setNoPickupDelay();
        }
        return false;
    }

    // ==================== Attribute Modifiers ====================

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = HashMultimap.create();
        multimap.putAll(super.getAttributeModifiers(slot, stack));
        if (slot == EntityEquipmentSlot.MAINHAND) {
            PlacementConfig config = new PlacementConfig(stack);
            float reach = config.getReachDistance();
            // 玩家基础触及距离为 5.0，因此 modifier = reach - 5.0
            double modifier = Math.max(0.0, reach - 5.0);
            multimap.put(EntityPlayer.REACH_DISTANCE.getName(),
                new AttributeModifier(REACH_MODIFIER_UUID, "AE2Enhanced OmniTool reach", modifier, 0));
        }
        return multimap;
    }

    // ==================== Helpers ====================

    public static boolean isBlacklisted(Block block) {
        long now = System.currentTimeMillis();
        if (blacklistCache == null || now - blacklistCacheTime > 5000L) {
            blacklistCache = new java.util.HashSet<>();
            for (String raw : AE2EnhancedConfig.omniTool.breakableBlacklist) {
                if (raw == null || raw.trim().isEmpty()) continue;
                try {
                    blacklistCache.add(new net.minecraft.util.ResourceLocation(raw.trim()));
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Invalid breakable blacklist entry: {}", raw);
                }
            }
            blacklistCacheTime = now;
        }
        net.minecraft.util.ResourceLocation reg = block.getRegistryName();
        return reg != null && blacklistCache.contains(reg);
    }
}

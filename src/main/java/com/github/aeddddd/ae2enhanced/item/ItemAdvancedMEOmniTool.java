package com.github.aeddddd.ae2enhanced.item;

import appeng.api.implementations.items.IAEWrench;
import cofh.api.item.IToolHammer;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.block.BlockWirelessChannelTransmitter;
import com.github.aeddddd.ae2enhanced.util.ForceKillHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import crazypants.enderio.api.tool.IConduitControl;
import crazypants.enderio.api.tool.IHideFacades;
import crazypants.enderio.api.tool.ITool;
import mekanism.api.IMekWrench;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Optional.InterfaceList({
    @Optional.Interface(iface = "cofh.api.item.IToolHammer", modid = "cofhcore"),
    @Optional.Interface(iface = "mekanism.api.IMekWrench", modid = "mekanism"),
    @Optional.Interface(iface = "crazypants.enderio.api.tool.ITool", modid = "enderio"),
    @Optional.Interface(iface = "crazypants.enderio.api.tool.IConduitControl", modid = "enderio")
})
public class ItemAdvancedMEOmniTool extends Item implements IAEWrench, IToolHammer, IMekWrench, ITool, IConduitControl {

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
    public static final String NBT_ANTI_HEAL = "AE2E_AntiHeal";

    // ---- Drop Modes ----
    public static final int DROP_NORMAL = 0;
    public static final int DROP_INVENTORY = 1;
    public static final int DROP_AE = 2;
    private static final String[] DROP_MODE_NAMES = {"normal", "inventory", "ae"};

    // ---- Modes ----
    public static final int MODE_COUNT = 4;
    public static final int MODE_UNIVERSAL = 0;
    public static final int MODE_WRENCH = 1;
    public static final int MODE_ROTATE = 2;
    public static final int MODE_TRAVEL = 3;

    private static final String[] MODE_NAMES = {
        "mode.universal", "mode.wrench", "mode.rotate", "mode.travel"
    };

    // ---- Damage Sources ----
    public static final DamageSource OMNITOOL_DAMAGE =
        new DamageSource("ae2enhanced.omnitool").setDamageBypassesArmor();
    public static final DamageSource CHAOS_DAMAGE =
        new DamageSource("ae2enhanced.omnitool.chaos").setDamageBypassesArmor();

    // ---- DE Chaos Crystal class name (for reflection-free string comparison) ----
    private static final String DE_CHAOS_CRYSTAL_CLASS = "com.brandon3055.draconicevolution.blocks.ChaosCrystal";

    // ---- Blacklist ----
    private static final Set<Block> BLACKLIST = ImmutableSet.of(
        Blocks.BEDROCK,
        Blocks.COMMAND_BLOCK,
        Blocks.CHAIN_COMMAND_BLOCK,
        Blocks.REPEATING_COMMAND_BLOCK,
        Blocks.BARRIER,
        Blocks.STRUCTURE_BLOCK,
        Blocks.STRUCTURE_VOID,
        Blocks.END_PORTAL_FRAME,
        Blocks.PORTAL,
        Blocks.END_PORTAL
    );

    // ---- Constants ----
    private static final float DESTROY_SPEED = 1_000_000.0f;
    private static final float ATTACK_DAMAGE = 6.0f;
    private static final float CHAOS_DAMAGE_VALUE = 1000.0f;
    private static final double DEFAULT_BLINK_DIST = 32.0;
    private static final int BLINK_COOLDOWN_TICKS = 1;
    private static final int DEFAULT_BREAK_COOLDOWN = 6;
    private static final UUID REACH_MODIFIER_UUID = UUID.fromString("ae2e0000-0000-0000-0000-000000000001");

    public ItemAdvancedMEOmniTool() {
        setRegistryName(AE2Enhanced.MOD_ID, "me_omni_tool");
        setTranslationKey(AE2Enhanced.MOD_ID + ".me_omni_tool");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(1);
        setHarvestLevel("wrench", 0);
    }

    // ==================== Mining ====================

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        if (getMode(stack) != MODE_UNIVERSAL) return 1.0f;
        if (isBlacklisted(state.getBlock())) return 0.0f;
        return DESTROY_SPEED;
    }

    @Override
    public boolean canHarvestBlock(IBlockState state, ItemStack stack) {
        if (getMode(stack) != MODE_UNIVERSAL) return false;
        return !isBlacklisted(state.getBlock());
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass, EntityPlayer player, @Nullable IBlockState blockState) {
        if (getMode(stack) != MODE_UNIVERSAL) return -1;
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, EntityPlayer player) {
        if (getMode(stack) != MODE_UNIVERSAL) {
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
                return true;
            }
            setLastBreakTick(stack, now);
        }
        return super.onBlockStartBreak(stack, pos, player);
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
            if (hasChaosCore(stack)) {
                applyChaosDamage(target, player);
            } else {
                applyTrueDamage(target, player, ATTACK_DAMAGE, OMNITOOL_DAMAGE);
            }
            return true; // 阻止默认攻击逻辑（绕过攻击冷却衰减）
        }
        return super.onLeftClickEntity(stack, player, entity);
    }

    /**
     * 应用混沌伤害：固定扣除 1000 血量，越过 LivingHurtEvent、护甲、药水、难度缩放、护盾等一切保护。
     * 视觉效果（受击动画、击退）保留在本方法中；核心强制击杀逻辑委托给 {@link ForceKillHelper}。
     */
    private void applyChaosDamage(EntityLivingBase target, EntityPlayer player) {
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

        // 核心强制击杀逻辑
        ForceKillHelper.applyForceKill(target, player, CHAOS_DAMAGE_VALUE, CHAOS_DAMAGE);

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

    private static boolean isChaosCrystal(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return DE_CHAOS_CRYSTAL_CLASS.equals(block.getClass().getName());
    }

    /**
     * 应用完全锁定的真实伤害：直接修改血量，绕过 LivingHurtEvent / LivingDamageEvent / 护甲 / 药水 / 难度缩放。
     */
    private void applyTrueDamage(EntityLivingBase target, EntityPlayer player, float damage, DamageSource source) {
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
            target.setHealth(0.0f);
            target.onDeath(source);
        } else {
            target.setHealth(newHealth);
        }
    }

    // ==================== Item Use First (Wrench Rotate) ====================

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) return EnumActionResult.PASS;
        ItemStack stack = player.getHeldItem(hand);
        int mode = getMode(stack);
        if (mode != MODE_WRENCH && mode != MODE_UNIVERSAL) return EnumActionResult.PASS;
        if (player.isSneaking()) return EnumActionResult.PASS;

        Block block = world.getBlockState(pos).getBlock();
        if (block != null && block.rotateBlock(world, pos, side)) {
            player.swingArm(hand);
            return EnumActionResult.SUCCESS;
        }
        return EnumActionResult.PASS;
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, IBlockAccess world, BlockPos pos, EntityPlayer player) {
        int mode = getMode(stack);
        return mode == MODE_WRENCH || mode == MODE_UNIVERSAL;
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
            case MODE_WRENCH:
                return doWrench(player, world, pos, facing, hand);
            case MODE_ROTATE:
                return doRotate(player, world, pos, facing);
            case MODE_TRAVEL:
                return doTravel(player, world, pos, hand, stack);
            default:
                return EnumActionResult.PASS;
        }
    }

    // ==================== Right-Click in Air ====================

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, stack);

        int mode = getMode(stack);
        if (mode == MODE_TRAVEL) {
            if (hasTravelStaff(stack)) {
                // TODO: EIO Travel Anchor 反射传送（右键空气时不触发Anchor）
            }
            player.fallDistance = 0.0f; // 每次尝试位移都重置摔落伤害
            doBlink(player, world, stack);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(EnumActionResult.PASS, stack);
    }

    // ==================== Wrench Mode ====================

    private EnumActionResult doWrench(EntityPlayer player, World world, BlockPos pos, EnumFacing facing, EnumHand hand) {
        if (world.isRemote) return EnumActionResult.SUCCESS;

        // 首先尝试通用旋转
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block.rotateBlock(world, pos, facing)) {
            player.swingArm(hand);
            return EnumActionResult.SUCCESS;
        }

        // PropertyDirection 手动循环回退（用于扳手模式旋转）
        for (IProperty<?> prop : state.getPropertyKeys()) {
            if (prop instanceof PropertyDirection) {
                PropertyDirection dirProp = (PropertyDirection) prop;
                EnumFacing current = state.getValue(dirProp);
                EnumFacing next = getNextFacing(current, facing, dirProp);
                if (next != null && next != current && dirProp.getAllowedValues().contains(next)) {
                    world.setBlockState(pos, state.withProperty(dirProp, next));
                    player.swingArm(hand);
                    return EnumActionResult.SUCCESS;
                }
            }
        }

        return EnumActionResult.PASS;
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
        if (hasTravelStaff(stack)) {
            // TODO: EIO Travel Anchor 反射传送
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
            // 尝试穿墙：穿过阻挡方块后继续搜索安全落点
            Vec3d through = ray.hitVec.add(look.scale(0.5));
            Vec3d safe = findSafePos(world, through, end, look);
            if (safe != null) {
                target = safe;
            } else {
                target = ray.hitVec.subtract(look.scale(0.15));
            }
        } else {
            target = end;
        }

        player.setPositionAndUpdate(target.x, target.y - player.getEyeHeight(), target.z);
        player.fallDistance = 0.0f;
        setLastBlink(stack, now);
        return EnumActionResult.SUCCESS;
    }

    /**
     * 在穿过阻挡点后向前搜索第一个安全的站立位置。
     */
    private Vec3d findSafePos(World world, Vec3d through, Vec3d maxEnd, Vec3d look) {
        double remainingDist = through.distanceTo(maxEnd);
        double step = 0.5;
        int steps = (int) Math.ceil(remainingDist / step);

        for (int i = 0; i <= steps; i++) {
            Vec3d check = through.add(look.scale(i * step));
            if (check.distanceTo(through) > remainingDist + 0.01) break;

            BlockPos pos = new BlockPos(check);
            if (isSafeStandingPos(world, pos)) {
                return new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            }
        }
        return null;
    }

    /**
     * 检查指定坐标是否为安全位置（身体/头部无碰撞箱即可，不要求脚下有地面）。
     */
    private boolean isSafeStandingPos(World world, BlockPos pos) {
        IBlockState feet = world.getBlockState(pos);
        IBlockState head = world.getBlockState(pos.up());
        return feet.getBlock().getCollisionBoundingBox(feet, world, pos) == null
            && head.getBlock().getCollisionBoundingBox(head, world, pos.up()) == null;
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

    // ==================== Upgrades ====================

    public static boolean hasChaosCore(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_CHAOS);
    }

    public static void setChaosCore(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_CHAOS, has);
    }

    public static int getFortuneLevel(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getInteger(NBT_FORTUNE) : 0;
    }

    public static void setFortuneLevel(ItemStack stack, int level) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(NBT_FORTUNE, level);
        updateEnchantments(stack);
    }

    public static boolean hasTravelStaff(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_TRAVEL);
    }

    public static void setTravelStaff(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_TRAVEL, has);
    }

    // ==================== Blink Distance / Cooldown ====================

    public static double getBlinkDistance(ItemStack stack) {
        if (!stack.hasTagCompound()) return DEFAULT_BLINK_DIST;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_BLINK_DIST, net.minecraftforge.common.util.Constants.NBT.TAG_DOUBLE)) {
            tag.setDouble(NBT_BLINK_DIST, DEFAULT_BLINK_DIST);
        }
        return tag.getDouble(NBT_BLINK_DIST);
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
        return stack.hasTagCompound() ? stack.getTagCompound().getInteger(NBT_BREAK_COOLDOWN) : DEFAULT_BREAK_COOLDOWN;
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
        if (stack.isItemEnchanted()) {
            NBTTagList existing = stack.getEnchantmentTagList();
            for (int i = 0; i < existing.tagCount(); i++) {
                NBTTagCompound tag = existing.getCompoundTagAt(i);
                short id = tag.getShort("id");
                if (id != Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.SILK_TOUCH)
                        && id != Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.FORTUNE)) {
                    enchList.appendTag(tag.copy());
                }
            }
        }
        if (isSilkTouchEnabled(stack)) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", (short) Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.SILK_TOUCH));
            tag.setShort("lvl", (short) 1);
            enchList.appendTag(tag);
        }
        int fortune = getFortuneLevel(stack);
        if (fortune > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", (short) Enchantment.getEnchantmentID(net.minecraft.init.Enchantments.FORTUNE));
            tag.setShort("lvl", (short) fortune);
            enchList.appendTag(tag);
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

        tooltip.add(TextFormatting.AQUA + "━━━━━━━━━━━━━━━━━━━━");

        boolean hasUpgrades = false;
        if (hasChaosCore(stack)) {
            tooltip.add(TextFormatting.GOLD + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.chaos"));
            hasUpgrades = true;
        }
        int fortune = getFortuneLevel(stack);
        if (fortune > 0) {
            tooltip.add(TextFormatting.GREEN + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.fortune", fortune));
            hasUpgrades = true;
        }
        if (hasTravelStaff(stack)) {
            tooltip.add(TextFormatting.LIGHT_PURPLE + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.travel"));
            hasUpgrades = true;
        }
        if (!hasUpgrades) {
            tooltip.add(TextFormatting.GRAY + I18n.format("item.ae2enhanced.me_omni_tool.no_upgrades"));
        }

        if (isAEBound(stack)) {
            BlockPos aePos = getAETransmitterPos(stack);
            int aeDim = getAETransmitterDim(stack);
            tooltip.add(TextFormatting.DARK_AQUA + "● " + TextFormatting.WHITE
                + I18n.format("item.ae2enhanced.me_omni_tool.ae_bound", aePos.getX(), aePos.getY(), aePos.getZ(), aeDim));
        }
    }

    // ==================== Wrench Interface Implementations ====================

    // -- IAEWrench --
    @Override
    public boolean canWrench(ItemStack wrench, EntityPlayer player, BlockPos pos) {
        int mode = getMode(wrench);
        return mode == MODE_WRENCH || mode == MODE_UNIVERSAL;
    }

    // -- IToolHammer (CoFH) --
    @Override
    public boolean isUsable(ItemStack item, EntityLivingBase user, BlockPos pos) {
        return item.getItem() instanceof ItemAdvancedMEOmniTool;
    }

    @Override
    public boolean isUsable(ItemStack item, EntityLivingBase user, Entity entity) {
        return item.getItem() instanceof ItemAdvancedMEOmniTool;
    }

    @Override
    public void toolUsed(ItemStack item, EntityLivingBase user, BlockPos pos) {
    }

    @Override
    public void toolUsed(ItemStack item, EntityLivingBase user, Entity entity) {
    }

    // -- IMekWrench (Mekanism) --
    @Override
    public boolean canUseWrench(ItemStack stack, EntityPlayer player, BlockPos pos) {
        int mode = getMode(stack);
        return mode == MODE_WRENCH || mode == MODE_UNIVERSAL;
    }

    @Override
    public boolean canUseWrench(EntityPlayer player, EnumHand hand, ItemStack stack, RayTraceResult rayTrace) {
        int mode = getMode(stack);
        return mode == MODE_WRENCH || mode == MODE_UNIVERSAL;
    }

    @Override
    public void wrenchUsed(EntityPlayer player, EnumHand hand, ItemStack stack, RayTraceResult rayTrace) {
    }

    // -- ITool / IHideFacades / IConduitControl (EnderIO) --
    @Override
    public boolean canUse(EnumHand hand, EntityPlayer player, BlockPos pos) {
        ItemStack stack = player.getHeldItem(hand);
        int mode = getMode(stack);
        return (mode == MODE_WRENCH || mode == MODE_UNIVERSAL) && stack.getItem() instanceof ItemAdvancedMEOmniTool;
    }

    @Override
    public void used(EnumHand hand, EntityPlayer player, BlockPos pos) {
    }

    @Override
    public boolean shouldHideFacades(ItemStack stack, EntityPlayer player) {
        int mode = getMode(stack);
        return mode == MODE_WRENCH || mode == MODE_UNIVERSAL;
    }

    @Override
    public boolean showOverlay(ItemStack stack, EntityPlayer player) {
        int mode = getMode(stack);
        return mode == MODE_WRENCH || mode == MODE_UNIVERSAL;
    }

    // ==================== Attribute Modifiers ====================

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> multimap = HashMultimap.create();
        multimap.putAll(super.getAttributeModifiers(slot, stack));
        if (slot == EntityEquipmentSlot.MAINHAND) {
            multimap.put(EntityPlayer.REACH_DISTANCE.getName(),
                new AttributeModifier(REACH_MODIFIER_UUID, "AE2Enhanced OmniTool reach", 5.0, 0));
        }
        return multimap;
    }

    // ==================== Helpers ====================

    public static boolean isBlacklisted(Block block) {
        return BLACKLIST.contains(block);
    }
}

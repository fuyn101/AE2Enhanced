package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.google.common.collect.ImmutableSet;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * 先进ME工具 — 通用创造级挖掘 + 扳手 + 旋转 + 旅行 + 升级系统.
 */
public class ItemAdvancedMEOmniTool extends Item {

    // ---- NBT Keys ----
    public static final String NBT_MODE = "Mode";
    public static final String NBT_SILK = "SilkTouch";
    public static final String NBT_CHAOS = "ChaosCore";
    public static final String NBT_FORTUNE = "Fortune";
    public static final String NBT_TRAVEL = "TravelStaff";
    public static final String NBT_BLINK_DIST = "BlinkDist";
    public static final String NBT_LAST_BLINK = "LastBlink";

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
    private static final double DEFAULT_BLINK_DIST = 5.0;
    private static final int BLINK_COOLDOWN_TICKS = 20; // 1 second

    public ItemAdvancedMEOmniTool() {
        setRegistryName(AE2Enhanced.MOD_ID, "me_omni_tool");
        setTranslationKey(AE2Enhanced.MOD_ID + ".me_omni_tool");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    // ==================== Mining ====================

    @Override
    public float getDestroySpeed(ItemStack stack, IBlockState state) {
        if (isBlacklisted(state.getBlock())) return 0.0f;
        return DESTROY_SPEED;
    }

    @Override
    public boolean canHarvestBlock(IBlockState state, ItemStack stack) {
        return !isBlacklisted(state.getBlock());
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass, EntityPlayer player, @Nullable IBlockState blockState) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, World world, IBlockState state, BlockPos pos, EntityLivingBase entityLiving) {
        // 触发附魔效果（Silk Touch / Fortune 通过 NBT 附魔自动生效）
        return true;
    }

    // ==================== Combat ====================

    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        target.attackEntityFrom(OMNITOOL_DAMAGE, ATTACK_DAMAGE);
        if (hasChaosCore(stack) && attacker instanceof EntityPlayer) {
            target.attackEntityFrom(CHAOS_DAMAGE, CHAOS_DAMAGE_VALUE);
        }
        return true;
    }

    // ==================== Right-Click (Mode Switching) ====================

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) return EnumActionResult.SUCCESS;

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

    // ==================== Wrench Mode ====================

    private EnumActionResult doWrench(EntityPlayer player, World world, BlockPos pos, EnumFacing facing, EnumHand hand) {
        if (world.isRemote) return EnumActionResult.SUCCESS;

        // TODO: 实现多模组扳手兼容
        // 1. AE2 IAEWrench
        // 2. Ender IO ITool
        // 3. Mekanism IMekWrench / 管道面调整
        // 4. Thermal Expansion IToolHammer
        // 5. BuildCraft IToolWrench
        // 6. IC2 IWrenchable
        // 7. Immersive Engineering ITool
        // 全部通过反射隔离访问

        // 暂时返回 PASS，等 WrenchCompatHelper 实现后接入
        return EnumActionResult.PASS;
    }

    // ==================== Rotate Mode ====================

    private EnumActionResult doRotate(EntityPlayer player, World world, BlockPos pos, EnumFacing facing) {
        if (world.isRemote) return EnumActionResult.SUCCESS;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // 尝试 Block.rotateBlock
        boolean rotated = block.rotateBlock(world, pos, facing);
        if (rotated) {
            return EnumActionResult.SUCCESS;
        }

        // 回退：尝试手动翻转 FACING 属性
        // TODO: 完善 PropertyDirection 循环逻辑

        return EnumActionResult.PASS;
    }

    // ==================== Travel Mode ====================

    private EnumActionResult doTravel(EntityPlayer player, World world, BlockPos pos, EnumHand hand, ItemStack stack) {
        if (world.isRemote) return EnumActionResult.SUCCESS;

        // 检查是否指向 Travel Anchor（若安装了 EIO 旅行手杖升级）
        if (hasTravelStaff(stack)) {
            // TODO: 反射检测 EIO Travel Anchor 并执行传送
        }

        // 默认：向前 Blink 位移
        return doBlink(player, world, stack);
    }

    private EnumActionResult doBlink(EntityPlayer player, World world, ItemStack stack) {
        long now = world.getTotalWorldTime();
        long lastBlink = getLastBlink(stack);
        if (now - lastBlink < BLINK_COOLDOWN_TICKS) {
            // 冷却中，不执行
            return EnumActionResult.PASS;
        }

        double distance = getBlinkDistance(stack);
        Vec3d look = player.getLookVec();
        Vec3d start = player.getPositionVector().add(0, player.getEyeHeight(), 0);
        Vec3d end = start.add(look.x * distance, look.y * distance, look.z * distance);

        RayTraceResult ray = world.rayTraceBlocks(start, end, false, true, false);
        Vec3d target;
        if (ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK) {
            target = ray.hitVec.subtract(look.scale(0.15));
        } else {
            target = end;
        }

        player.setPositionAndUpdate(target.x, target.y - player.getEyeHeight(), target.z);
        player.fallDistance = 0.0f;
        setLastBlink(stack, now);

        return EnumActionResult.SUCCESS;
    }

    // ==================== Mode Management ====================

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

    // ==================== Silk Touch ====================

    public static boolean isSilkTouchEnabled(ItemStack stack) {
        if (!stack.hasTagCompound()) return false;
        return stack.getTagCompound().getBoolean(NBT_SILK);
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
        if (!stack.hasTagCompound()) return 0;
        return stack.getTagCompound().getInteger(NBT_FORTUNE);
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
        return stack.getTagCompound().getDouble(NBT_BLINK_DIST);
    }

    public static void setBlinkDistance(ItemStack stack, double dist) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setDouble(NBT_BLINK_DIST, dist);
    }

    private static long getLastBlink(ItemStack stack) {
        if (!stack.hasTagCompound()) return 0;
        return stack.getTagCompound().getLong(NBT_LAST_BLINK);
    }

    private static void setLastBlink(ItemStack stack, long tick) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setLong(NBT_LAST_BLINK, tick);
    }

    // ==================== Enchantment Sync ====================

    private static void updateEnchantments(ItemStack stack) {
        NBTTagList enchList = new NBTTagList();

        // 保留已有非丝绸/时运附魔
        if (stack.isItemEnchanted()) {
            NBTTagList existing = stack.getEnchantmentTagList();
            for (int i = 0; i < existing.tagCount(); i++) {
                NBTTagCompound tag = existing.getCompoundTagAt(i);
                short id = tag.getShort("id");
                if (id != Enchantment.getEnchantmentID(Enchantments.SILK_TOUCH)
                        && id != Enchantment.getEnchantmentID(Enchantments.FORTUNE)) {
                    enchList.appendTag(tag.copy());
                }
            }
        }

        if (isSilkTouchEnabled(stack)) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", (short) Enchantment.getEnchantmentID(Enchantments.SILK_TOUCH));
            tag.setShort("lvl", (short) 1);
            enchList.appendTag(tag);
        }

        int fortune = getFortuneLevel(stack);
        if (fortune > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setShort("id", (short) Enchantment.getEnchantmentID(Enchantments.FORTUNE));
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
        tooltip.add(TextFormatting.AQUA + I18n.format("item.ae2enhanced.me_omni_tool.mode",
            I18n.format("item.ae2enhanced.me_omni_tool." + MODE_NAMES[mode])));

        if (isSilkTouchEnabled(stack)) {
            tooltip.add(TextFormatting.YELLOW + I18n.format("item.ae2enhanced.me_omni_tool.silk_touch.on"));
        }

        // 已安装升级
        boolean hasUpgrades = false;
        if (hasChaosCore(stack)) {
            tooltip.add(TextFormatting.DARK_RED + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.chaos"));
            hasUpgrades = true;
        }
        int fortune = getFortuneLevel(stack);
        if (fortune > 0) {
            tooltip.add(TextFormatting.GREEN + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.fortune", fortune));
            hasUpgrades = true;
        }
        if (hasTravelStaff(stack)) {
            tooltip.add(TextFormatting.LIGHT_PURPLE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.travel"));
            hasUpgrades = true;
        }
        if (!hasUpgrades) {
            tooltip.add(TextFormatting.GRAY + I18n.format("item.ae2enhanced.me_omni_tool.no_upgrades"));
        }

        // Blink 距离
        double blinkDist = getBlinkDistance(stack);
        tooltip.add(TextFormatting.GRAY + I18n.format("item.ae2enhanced.me_omni_tool.blink_dist", blinkDist));
    }

    // ==================== Helpers ====================

    public static boolean isBlacklisted(Block block) {
        return BLACKLIST.contains(block);
    }
}

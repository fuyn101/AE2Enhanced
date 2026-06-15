package com.github.aeddddd.ae2enhanced.item;

import appeng.api.features.INetworkEncodable;
import appeng.api.networking.IGrid;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.omnitool.network.SecurityTerminalNetworkLink;
import com.github.aeddddd.ae2enhanced.omnitool.network.WirelessTransmitterNetworkLink;
import com.github.aeddddd.ae2enhanced.block.BlockWirelessChannelTransmitter;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.omnitool.module.CombatModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.OmniToolModules;
import com.github.aeddddd.ae2enhanced.util.placement.SecurityTerminalBindingHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.Block;

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

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
    // AE-bound 坐标绑定已迁移到 WirelessTransmitterNetworkLink，此处保留别名以便兼容旧调用方
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
    public static final String NBT_BEDROCK_BREAKER = "BedrockBreaker";
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
        return OmniToolModules.getForMode(MODE_UNIVERSAL).getDestroySpeed(stack, state);
    }

    @Override
    public boolean canHarvestBlock(IBlockState state, ItemStack stack) {
        return OmniToolModules.getForMode(MODE_UNIVERSAL).canHarvestBlock(state, stack);
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass, EntityPlayer player, @Nullable IBlockState blockState) {
        return OmniToolModules.getForMode(MODE_UNIVERSAL).getHarvestLevel(stack, toolClass, player, blockState);
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, EntityPlayer player) {
        boolean handled = OmniToolModules.getForMode(MODE_UNIVERSAL).onBlockStartBreak(stack, pos, player);
        if (handled) return true;
        return super.onBlockStartBreak(stack, pos, player);
    }

    // ==================== Attack (Bypass Cooldown) ====================

    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
        // 战斗逻辑独立于模式，始终由 CombatModule 处理
        boolean handled = new CombatModule()
                .onLeftClickEntity(stack, player, entity);
        if (handled) return true;
        return super.onLeftClickEntity(stack, player, entity);
    }

    // 禁疗标记保持对 Mixin 的可访问性
    public static boolean hasAntiHeal(EntityLivingBase entity) {
        return CombatModule.hasAntiHeal(entity);
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
            WirelessTransmitterNetworkLink.setBound(stack, pos, world.provider.getDimension());
            player.sendStatusMessage(new TextComponentTranslation("message.ae2enhanced.omnitool.ae_bound", pos.getX(), pos.getY(), pos.getZ()), true);
            player.setHeldItem(hand, stack);
            return EnumActionResult.SUCCESS;
        }

        return OmniToolModules.getForMode(getMode(stack))
                .onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
    }

    // ==================== Right-Click in Air ====================

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) return new ActionResult<>(EnumActionResult.SUCCESS, stack);

        return OmniToolModules.getForMode(getMode(stack))
                .onItemRightClick(world, player, hand);
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
        return SecurityTerminalNetworkLink.INSTANCE.isLinked(item)
                ? SecurityTerminalBindingHelper.getEncryptionKey(item)
                : "";
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        SecurityTerminalNetworkLink.INSTANCE.clear(item);
        if (encKey != null && !encKey.isEmpty()) {
            SecurityTerminalBindingHelper.setEncryptionKey(item, encKey);
        }
    }

    // AE 坐标绑定已迁移到 WirelessTransmitterNetworkLink；以下方法保留为兼容包装
    public static boolean isAEBound(ItemStack stack) {
        return WirelessTransmitterNetworkLink.INSTANCE.isLinked(stack);
    }

    @Deprecated
    public static void setAEBound(ItemStack stack, BlockPos pos, int dim) {
        WirelessTransmitterNetworkLink.setBound(stack, pos, dim);
    }

    @Deprecated
    public static BlockPos getAETransmitterPos(ItemStack stack) {
        return WirelessTransmitterNetworkLink.getTransmitterPos(stack);
    }

    @Deprecated
    public static int getAETransmitterDim(ItemStack stack) {
        return WirelessTransmitterNetworkLink.getTransmitterDim(stack);
    }

    @Deprecated
    public static void clearAEBinding(ItemStack stack) {
        WirelessTransmitterNetworkLink.INSTANCE.clear(stack);
    }

    @Nullable
    public static IGrid getAELinkedGrid(ItemStack stack, World world) {
        return WirelessTransmitterNetworkLink.INSTANCE.getLinkedGrid(stack, world, null);
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

    public static boolean hasBedrockBreaker(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_BEDROCK_BREAKER);
    }

    public static void setBedrockBreaker(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_BEDROCK_BREAKER, has);
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

    public static boolean hasConformalCharge(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(NBT_CONFORMAL);
    }

    public static void setConformalCharge(ItemStack stack, boolean has) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setBoolean(NBT_CONFORMAL, has);
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

        OmniToolModules.getForMode(mode).addTooltip(stack, worldIn, tooltip, flagIn);

        tooltip.add(TextFormatting.AQUA + "━━━━━━━━━━━━━━━━━━━━");

        boolean hasUpgrades = false;
        if (hasChaosCore(stack)) {
            tooltip.add(TextFormatting.GOLD + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.chaos"));
            hasUpgrades = true;
        }
        if (hasBedrockBreaker(stack)) {
            tooltip.add(TextFormatting.DARK_RED + "● " + TextFormatting.WHITE + I18n.format("item.ae2enhanced.me_omni_tool.upgrade.bedrock"));
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

        if (com.github.aeddddd.ae2enhanced.omnitool.module.TravelModule.isTravelAnchorBound(stack)) {
            BlockPos anchorPos = com.github.aeddddd.ae2enhanced.omnitool.module.TravelModule.getBoundTravelAnchorPos(stack);
            int anchorDim = com.github.aeddddd.ae2enhanced.omnitool.module.TravelModule.getBoundTravelAnchorDim(stack);
            tooltip.add(TextFormatting.LIGHT_PURPLE + "● " + TextFormatting.WHITE
                + I18n.format("item.ae2enhanced.me_omni_tool.travel_anchor_bound", anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(), anchorDim));
        }

        if (WirelessTransmitterNetworkLink.INSTANCE.isLinked(stack)) {
            BlockPos aePos = WirelessTransmitterNetworkLink.getTransmitterPos(stack);
            int aeDim = WirelessTransmitterNetworkLink.getTransmitterDim(stack);
            if (aePos != null) {
                tooltip.add(TextFormatting.DARK_AQUA + "● " + TextFormatting.WHITE
                    + I18n.format("item.ae2enhanced.me_omni_tool.ae_bound", aePos.getX(), aePos.getY(), aePos.getZ(), aeDim));
            }
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
        multimap.putAll(OmniToolModules.getForMode(getMode(stack)).getAttributeModifiers(slot, stack));
        return multimap;
    }

}

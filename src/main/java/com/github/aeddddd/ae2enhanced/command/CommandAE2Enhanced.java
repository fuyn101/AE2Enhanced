package com.github.aeddddd.ae2enhanced.command;

import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternGarbageCollector;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/**
 * AE2Enhanced 主指令。
 *
 * <p>子指令：</p>
 * <ul>
 *   <li>{@code /ae2e spgc} — 手动触发智能样板垃圾回收</li>
 *   <li>{@code /ae2e channels enable|disable|status} — 控制 AE2 频道检查</li>
 *   <li>{@code /ae2e recoverhd list} — 列出所有超维度仓储中枢 UUID</li>
 *   <li>{@code /ae2e recoverhd <uuid>} — 获取携带指定 UUID 的主方块</li>
 * </ul>
 */
public class CommandAE2Enhanced extends CommandBase {

    @Override
    @Nonnull
    public String getName() {
        return "ae2enhanced";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/ae2e <spgc|channels|recoverhd>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "用法: " + getUsage(sender)));
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "spgc":
                executeSpgc(sender);
                break;
            case "channels":
                executeChannels(sender, args);
                break;
            case "recoverhd":
                executeRecoverHd(server, sender, args);
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "未知子指令: " + sub));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + getUsage(sender)));
        }
    }

    // ---- spgc ----

    private void executeSpgc(@Nonnull ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] 正在扫描 ME 接口并清理无效的智能样板文件..."));
        int deleted = SmartPatternGarbageCollector.runManualGC();
        if (deleted < 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] 清理过程中发生错误，请查看服务端日志。"));
        } else if (deleted == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] 没有找到需要清理的无效文件。"));
        } else {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] 已清理 " + deleted + " 个无效的智能样板文件。"));
        }
    }

    // ---- channels ----

    private void executeChannels(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "用法: /ae2e channels <enable|disable|status>"));
            return;
        }
        String action = args[1].toLowerCase();
        AEConfig config = AEConfig.instance();
        switch (action) {
            case "enable":
                setChannelsEnabled(true);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] AE2 频道检查已启用。"));
                break;
            case "disable":
                setChannelsEnabled(false);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] AE2 频道检查已禁用（无限频道）。"));
                break;
            case "status":
                boolean enabled = config.isFeatureEnabled(AEFeature.CHANNELS);
                String status = enabled ? TextFormatting.GREEN + "已启用" : TextFormatting.YELLOW + "已禁用（无限频道）";
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] AE2 频道检查状态: " + status));
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "用法: /ae2e channels <enable|disable|status>"));
        }
    }

    private void setChannelsEnabled(boolean enabled) {
        try {
            AEConfig config = AEConfig.instance();
            Field field = AEConfig.class.getDeclaredField("featureFlags");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            EnumSet<AEFeature> flags = (EnumSet<AEFeature>) field.get(config);
            if (enabled) {
                flags.add(AEFeature.CHANNELS);
            } else {
                flags.remove(AEFeature.CHANNELS);
            }
            // 同步修改配置文件持久化
            net.minecraftforge.common.config.Property prop = config.get("Features.NetworkFeatures", "Channels", true);
            prop.set(enabled);
            config.save();
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to toggle AE2 channel feature.", e);
        }
    }

    // ---- recoverhd ----

    private void executeRecoverHd(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "用法: /ae2e recoverhd list 或 /ae2e recoverhd <uuid>"));
            return;
        }
        String arg = args[1].toLowerCase();
        if ("list".equals(arg)) {
            listHdUuids(sender);
        } else {
            giveHdController(server, sender, arg);
        }
    }

    private void listHdUuids(@Nonnull ICommandSender sender) {
        WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
        if (world == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] 无法获取主世界。"));
            return;
        }
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File storageDir = new File(worldDir, "ae2enhanced/storage");
        if (!storageDir.exists()) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] 没有找到任何超维度仓储中枢数据。"));
            return;
        }
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".dat") && !name.startsWith("smartpattern_"));
        if (files == null || files.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] 没有找到任何超维度仓储中枢数据。"));
            return;
        }
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] 已找到 " + files.length + " 个超维度仓储中枢数据文件："));
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : files) {
            String name = file.getName();
            String uuidStr = name.substring(0, name.length() - 4);
            sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  - " + uuidStr + "  ("
                    + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(file.lastModified())) + ")"));
        }
    }

    private void giveHdController(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String uuidStr) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] 无效的 UUID: " + uuidStr));
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] 该指令只能由玩家执行。"));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        ItemStack stack = new ItemStack(ModBlocks.HYPERDIMENSIONAL_CONTROLLER);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("nexusId", uuid);
        stack.setTagCompound(tag);
        boolean added = player.inventory.addItemStackToInventory(stack);
        if (!added) {
            player.dropItem(stack, false);
        }
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] 已给予携带 UUID " + uuidStr + " 的超维度仓储中枢主方块。"));
    }
}

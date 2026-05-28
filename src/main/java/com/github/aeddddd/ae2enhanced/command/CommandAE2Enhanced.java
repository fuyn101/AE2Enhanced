package com.github.aeddddd.ae2enhanced.command;

import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternGarbageCollector;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;

/**
 * AE2Enhanced 主指令。
 *
 * <p>子指令：</p>
 * <ul>
 *   <li>{@code /ae2e spgc} — 手动触发智能样板垃圾回收</li>
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
        return "/ae2e spgc  — 手动清理无引用的智能样板文件";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // OP level 2
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "用法: " + getUsage(sender)));
            return;
        }
        String sub = args[0].toLowerCase();
        if ("spgc".equals(sub)) {
            executeSpgc(sender);
        } else {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "未知子指令: " + sub));
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + getUsage(sender)));
        }
    }

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
}

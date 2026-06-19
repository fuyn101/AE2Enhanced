package com.github.aeddddd.ae2enhanced.command;

import ae2.api.config.Actionable;
import ae2.api.networking.IGrid;
import ae2.api.networking.IManagedGridNode;
import ae2.api.networking.pathing.ChannelMode;
import ae2.api.networking.security.IActionSource;
import ae2.api.networking.storage.IStorageService;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.KeyCounter;
import ae2.core.AEConfig;
import ae2.me.helpers.PlayerSource;
import ae2.api.storage.MEStorage;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternGarbageCollector;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Config;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * AE2Enhanced main command.
 *
 * <p>Subcommands:</p>
 * <ul>
 *   <li>{@code /ae2e spgc} — Manual Smart Pattern garbage collection</li>
 *   <li>{@code /ae2e channels enable|disable|status} — Toggle AE2 channel checking</li>
 *   <li>{@code /ae2e fastpathing enable|disable|status} — Toggle experimental O(N) channel pathing</li>
 *   <li>{@code /ae2e recoverhd list} — List all hyperdimensional storage UUIDs</li>
 *   <li>{@code /ae2e recoverhd <uuid>} — Get controller block with specified UUID</li>
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
        return "/ae2e <spgc|channels|fastpathing|recoverhd|testhd|migratefluids|help>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: " + getUsage(sender)));
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
            case "fastpathing":
                executeFastPathing(sender, args);
                break;
            case "recoverhd":
                executeRecoverHd(server, sender, args);
                break;
            case "testhd":
                executeTestHd(server, sender, args);
                break;
            case "migratefluids":
                executeMigrateFluids(sender);
                break;
            case "help":
                executeHelp(sender);
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Unknown subcommand: " + sub));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + getUsage(sender)));
        }
    }

    // ---- help ----

    private void executeHelp(@Nonnull ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "========== AE2Enhanced Command Help =========="));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e spgc"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Manually trigger Smart Pattern garbage collection."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e channels <enable|disable|status>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  enable:  Enable AE2 channel checking (normal mode)."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  disable: Disable AE2 channel checking (infinite channels)."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  status:  Show current channel checking status."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e fastpathing <enable|disable|status>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  enable:  Use experimental O(N) channel pathing (PR #8285 port)."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  disable: Use vanilla AE2-UEL PathSegment pathing."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  status:  Show current fast pathing status."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e recoverhd list"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  List all hyperdimensional storage UUIDs (sorted by mtime)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e recoverhd <uuid>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Give the player a Hyperdimensional Controller block carrying the specified UUID."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e testhd <uuid> <count>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Inject <count> random enchanted gear types into the controller with the specified UUID."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e migratefluids"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Convert AE2E ItemFluidDrop in all ME networks to ae2fc format."));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Requires ae2fc to be loaded and OP permission."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e help"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Display this help message."));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "=============================================="));
    }

    // ---- migrate fluids ----

    private void executeMigrateFluids(@Nonnull ICommandSender sender) {
        // TODO: optional mod dependency — ae2fc / ItemFluidDrop unavailable in AE2S migration.
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] migratefluids is disabled during the AE2S migration (ae2fc not available)."));
    }

    // ---- spgc ----

    private void executeSpgc(@Nonnull ICommandSender sender) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Scanning ME interfaces and cleaning orphaned Smart Pattern files..."));
        int deleted = SmartPatternGarbageCollector.runManualGC();
        if (deleted < 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] An error occurred during cleanup. Check the server log."));
        } else if (deleted == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] No orphaned files found."));
        } else {
            sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Cleaned up " + deleted + " orphaned Smart Pattern file(s)."));
        }
    }

    // ---- channels ----

    private void executeChannels(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e channels <enable|disable|status>"));
            return;
        }
        String action = args[1].toLowerCase();
        AEConfig config = AEConfig.instance();
        switch (action) {
            case "enable":
                config.setChannelMode(ChannelMode.DEFAULT);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] AE2 channel checking enabled."));
                break;
            case "disable":
                config.setChannelMode(ChannelMode.INFINITE);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] AE2 channel checking disabled (infinite channels)."));
                break;
            case "status":
                boolean enabled = config.getRequireChannel();
                String status = enabled ? TextFormatting.GREEN + "Enabled" : TextFormatting.YELLOW + "Disabled (infinite channels)";
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] AE2 channel checking status: " + status));
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e channels <enable|disable|status>"));
        }
    }

    // ---- fastpathing ----

    private void executeFastPathing(@Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e fastpathing <enable|disable|status>"));
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "enable":
                setFastPathing(true);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Experimental fast channel pathing enabled."));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Existing networks will switch on next repath."));
                break;
            case "disable":
                setFastPathing(false);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Experimental fast channel pathing disabled."));
                sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Networks will fall back to vanilla AE2-UEL pathing on next repath."));
                break;
            case "status":
                boolean enabled = AE2EnhancedConfig.channelPathing.fastPathing;
                String status = enabled
                        ? TextFormatting.GREEN + "Enabled (O(N) hierarchical BFS + iterative DFS)"
                        : TextFormatting.YELLOW + "Disabled (vanilla PathSegment)";
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Fast channel pathing status: " + status));
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e fastpathing <enable|disable|status>"));
        }
    }

    private void setFastPathing(boolean enabled) {
        AE2EnhancedConfig.channelPathing.fastPathing = enabled;
        ConfigManager.sync(AE2Enhanced.MOD_ID, Config.Type.INSTANCE);
    }

    // ---- recoverhd ----

    private void executeRecoverHd(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e recoverhd list  or  /ae2e recoverhd <uuid>"));
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
        WorldServer world = net.minecraftforge.common.DimensionManager.getWorld(0);
        if (world == null) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Cannot access the overworld."));
            return;
        }
        File worldDir = world.getSaveHandler().getWorldDirectory();
        File storageDir = new File(worldDir, "ae2enhanced/storage");
        if (!storageDir.exists()) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] No hyperdimensional storage data found."));
            return;
        }
        File[] entries = storageDir.listFiles((dir, name) -> {
            if (name.startsWith("smartpattern_")) return false;
            File f = new File(dir, name);
            return f.isDirectory() || name.endsWith(".dat");
        });
        if (entries == null || entries.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] No hyperdimensional storage data found."));
            return;
        }
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Found " + entries.length + " hyperdimensional storage entry(s) (click UUID to copy):"));
        Arrays.sort(entries, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File entry : entries) {
            String name = entry.getName();
            String uuidStr = entry.isDirectory() ? name : name.substring(0, name.length() - 4);

            TextComponentString uuidText = new TextComponentString(uuidStr);
            uuidText.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, uuidStr));
            uuidText.getStyle().setColor(TextFormatting.AQUA);
            uuidText.getStyle().setUnderlined(true);

            TextComponentString suffix = new TextComponentString("  ("
                    + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(entry.lastModified())) + ")");
            suffix.getStyle().setColor(TextFormatting.GRAY);

            TextComponentString line = new TextComponentString("  - ");
            line.getStyle().setColor(TextFormatting.GRAY);
            line.appendSibling(uuidText);
            line.appendSibling(suffix);
            sender.sendMessage(line);
        }
    }

    private void giveHdController(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String uuidStr) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Invalid UUID: " + uuidStr));
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] This command can only be executed by a player."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        // TODO: optional migration dependency — Hyperdimensional Controller block removed in AE2S migration.
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Hyperdimensional Controller block is not available during the AE2S migration."));
    }

    // ---- testhd ----

    private void executeTestHd(MinecraftServer server, ICommandSender sender, String[] args) {
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] Hyperdimensional Controller has been removed in the AE2S migration; testhd is disabled."));
        return;
    }
}

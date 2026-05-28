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
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/**
 * AE2Enhanced main command.
 *
 * <p>Subcommands:</p>
 * <ul>
 *   <li>{@code /ae2e spgc} — Manual Smart Pattern garbage collection</li>
 *   <li>{@code /ae2e channels enable|disable|status} — Toggle AE2 channel checking</li>
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
        return "/ae2e <spgc|channels|recoverhd|help>";
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
            case "recoverhd":
                executeRecoverHd(server, sender, args);
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
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e recoverhd list"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  List all hyperdimensional storage UUIDs (sorted by mtime)."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e recoverhd <uuid>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Give the player a Hyperdimensional Controller block carrying the specified UUID."));
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e help"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Display this help message."));
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "=============================================="));
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
                setChannelsEnabled(true);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] AE2 channel checking enabled."));
                break;
            case "disable":
                setChannelsEnabled(false);
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] AE2 channel checking disabled (infinite channels)."));
                break;
            case "status":
                boolean enabled = config.isFeatureEnabled(AEFeature.CHANNELS);
                String status = enabled ? TextFormatting.GREEN + "Enabled" : TextFormatting.YELLOW + "Disabled (infinite channels)";
                sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] AE2 channel checking status: " + status));
                break;
            default:
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e channels <enable|disable|status>"));
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
        WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(0);
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
        File[] files = storageDir.listFiles((dir, name) -> name.endsWith(".dat") && !name.startsWith("smartpattern_"));
        if (files == null || files.length == 0) {
            sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "[AE2E] No hyperdimensional storage data found."));
            return;
        }
        sender.sendMessage(new TextComponentString(TextFormatting.AQUA + "[AE2E] Found " + files.length + " hyperdimensional storage data file(s):"));
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
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Invalid UUID: " + uuidStr));
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] This command can only be executed by a player."));
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
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Given Hyperdimensional Controller block carrying UUID " + uuidStr + "."));
    }
}

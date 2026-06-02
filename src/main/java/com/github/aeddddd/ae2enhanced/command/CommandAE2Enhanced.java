package com.github.aeddddd.ae2enhanced.command;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternGarbageCollector;
import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.io.File;
import java.lang.reflect.Field;
import java.math.BigInteger;
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
        return "/ae2e <spgc|channels|recoverhd|testhd|help>";
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
            case "testhd":
                executeTestHd(server, sender, args);
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
        sender.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/ae2e testhd <count>"));
        sender.sendMessage(new TextComponentString(TextFormatting.GRAY + "  Inject <count> random enchanted gear types into each formed hyperdimensional controller."));
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
        ItemStack stack = new ItemStack(BlockRegistry.HYPERDIMENSIONAL_CONTROLLER);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("nexusId", uuid);
        stack.setTagCompound(tag);
        boolean added = player.inventory.addItemStackToInventory(stack);
        if (!added) {
            player.dropItem(stack, false);
        }
        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Given Hyperdimensional Controller block carrying UUID " + uuidStr + "."));
    }

    // ---- testhd ----

    private static final List<Item> GEAR_CACHE = new ArrayList<>();

    private void executeTestHd(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Usage: /ae2e testhd <count>"));
            return;
        }
        int count;
        try {
            count = Integer.parseInt(args[1]);
            if (count <= 0 || count > 100_000) {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "Count must be between 1 and 100000."));
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Invalid count: " + args[1]));
            return;
        }

        // Cache gear items on first run
        synchronized (GEAR_CACHE) {
            if (GEAR_CACHE.isEmpty()) {
                for (Item item : Item.REGISTRY) {
                    if (item instanceof ItemSword || item instanceof ItemTool
                            || item instanceof ItemArmor || item instanceof ItemBow) {
                        GEAR_CACHE.add(item);
                    }
                }
            }
        }
        if (GEAR_CACHE.isEmpty()) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] No gear items found in registry."));
            return;
        }

        IActionSource fallbackSource = null;
        if (sender instanceof EntityPlayerMP) {
            fallbackSource = new PlayerSource((EntityPlayerMP) sender, null);
        }

        int totalControllers = 0;
        long totalTypes = 0;
        Random random = new Random();

        for (WorldServer world : server.worlds) {
            if (world == null) continue;
            for (net.minecraft.tileentity.TileEntity te : world.loadedTileEntityList) {
                if (te instanceof TileHyperdimensionalController) {
                    TileHyperdimensionalController controller = (TileHyperdimensionalController) te;
                    if (!controller.isFormed()) continue;
                    totalControllers++;

                    ItemStorageAdapter adapter = controller.getItemAdapter();
                    if (adapter == null) continue;

                    IActionSource source = fallbackSource != null ? fallbackSource : new MachineSource(controller);

                    for (int i = 0; i < count; i++) {
                        ItemStack stack = generateRandomGear(random);
                        if (stack.isEmpty()) continue;

                        IAEItemStack aeStack = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createStack(stack);
                        if (aeStack == null) continue;

                        // Random BigInteger amount: 1 .. ~2^100 (approx 10^30)
                        BigInteger amount = new BigInteger(100, random).abs().add(BigInteger.ONE);
                        injectBigInteger(adapter, aeStack, amount, source);
                        totalTypes++;
                    }
                }
            }
        }

        sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "[AE2E] Injected " + totalTypes + " random enchanted gear type(s) across " + totalControllers + " controller(s)."));
    }

    private static ItemStack generateRandomGear(Random random) {
        Item item = GEAR_CACHE.get(random.nextInt(GEAR_CACHE.size()));
        ItemStack stack = new ItemStack(item);

        int enchantCount = random.nextInt(6); // 0..5 enchantments
        if (enchantCount > 0) {
            List<Enchantment> possible = new ArrayList<>();
            for (Enchantment ench : Enchantment.REGISTRY) {
                if (ench != null && ench.canApply(stack)) {
                    possible.add(ench);
                }
            }
            if (!possible.isEmpty()) {
                Collections.shuffle(possible, random);
                for (int i = 0; i < Math.min(enchantCount, possible.size()); i++) {
                    Enchantment ench = possible.get(i);
                    int level = 1 + random.nextInt(ench.getMaxLevel());
                    stack.addEnchantment(ench, level);
                }
            }
        }
        return stack;
    }

    private static void injectBigInteger(ItemStorageAdapter adapter, IAEItemStack template, BigInteger amount, IActionSource source) {
        BigInteger remaining = amount;
        BigInteger maxChunk = BigInteger.valueOf(Long.MAX_VALUE);
        while (remaining.compareTo(BigInteger.ZERO) > 0) {
            BigInteger chunk = remaining.min(maxChunk);
            IAEItemStack toInject = template.copy();
            toInject.setStackSize(chunk.longValueExact());
            adapter.injectItems(toInject, Actionable.MODULATE, source);
            remaining = remaining.subtract(chunk);
        }
    }
}

package com.github.aeddddd.ae2enhanced.config;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Central configuration for AE2Enhanced.
 * File location: config/ae2enhanced.cfg
 * Changes via in-game Mod Options GUI are applied immediately (F3+T not required).
 */
@Config(modid = AE2Enhanced.MOD_ID)
@Config.LangKey("config.ae2enhanced.title")
public class AE2EnhancedConfig {

    @Config.Name("Storage")
    @Config.Comment({
        "Persistent storage settings for the Hyperdimensional Storage Nexus.",
        "These values affect server-side I/O behavior and data safety."
    })
    public static Storage storage = new Storage();

    @Config.Name("Render")
    @Config.Comment({
        "Client-side visual settings for the Hyperdimensional Controller TESR.",
        "Only processed on the client; changing them on a dedicated server has no effect."
    })
    public static Render render = new Render();

    @Config.Name("BlackHole")
    @Config.Comment({
        "Event-horizon behavior for Micro Singularity black holes.",
        "Controls whether entities inside the 3x3x3 horizon take damage."
    })
    public static BlackHole blackHole = new BlackHole();

    @Config.Name("Crafting")
    @Config.Comment({
        "Supercausal Computation Core crafting engine settings.",
        "Affects parallel limit, order scheduling, and batch behavior."
    })
    public static Crafting crafting = new Crafting();

    @Config.Name("WirelessChannel")
    @Config.Comment({
        "Wireless Channel System settings.",
        "Controls cross-dimension behavior, range, and transmitter power draw."
    })
    public static WirelessChannel wirelessChannel = new WirelessChannel();

    public static class Storage {
        @Config.Comment({
            "Auto-flush interval for the external .dat storage file (seconds).",
            "Lower values reduce data loss risk on crash but increase disk I/O.",
            "Higher values improve performance but may lose up to this many seconds of changes.",
            "WARNING: Extremely high values (e.g. > 3600) may result in significant",
            "data loss if the server crashes before the next flush.",
            "Range: 1 ~ 86400, Default: 5"
        })
        @Config.RangeInt(min = 1, max = 86400)
        public int flushIntervalSeconds = 5;
    }

    public static class Render {
        @Config.Comment({
            "Enable the holographic tesseract renderer above the Hyperdimensional Controller.",
            "If false, the spinning wireframe cube, rings, and core are completely skipped.",
            "Useful for low-end GPUs or when many controllers are visible.",
            "Default: true"
        })
        public boolean enableHyperdimensionalRenderer = true;

        @Config.Comment({
            "Maximum camera-to-structure distance (in blocks) at which the hologram is drawn.",
            "Beyond this distance the TESR returns early, saving FPS.",
            "WARNING: Very high values may cause FPS drops when many controllers",
            "are loaded, as the TESR will render at extreme distances.",
            "Range: 8 ~ 2147483647, Default: 64"
        })
        @Config.RangeInt(min = 8, max = Integer.MAX_VALUE)
        public int renderDistance = 64;
    }

    public enum DamageMode {
        ALL,
        NON_CREATIVE,
        NONE
    }

    private static DamageMode parseDamageMode(String raw) {
        if (raw == null) return DamageMode.ALL;
        try {
            return DamageMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DamageMode.ALL;
        }
    }

    public static class Crafting {
        @Config.Comment({
            "Maximum parallel crafting limit for the Computation Core.",
            "The actual limit is the smaller of this value and the structure-derived limit.",
            "WARNING: Extremely high values may cause lag, memory pressure, or",
            "integer overflow in internal calculations. Use with caution.",
            "Range: 1 ~ 2147483647, Default: 16384"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int maxParallel = 16384;

        @Config.Comment({
            "Maximum number of concurrently active crafting orders.",
            "Each order consumes parallel from the pool; excess orders queue.",
            "WARNING: Very high values increase memory usage and scheduling overhead.",
            "Range: 1 ~ 2147483647, Default: 8"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int maxActiveOrders = 8;
    }

    public static class WirelessChannel {
        @Config.Comment({
            "Allow wireless channel transmitters to connect across dimensions.",
            "If false, transmitters and receivers must be in the same dimension.",
            "Default: true"
        })
        public boolean crossDimension = true;

        @Config.Comment({
            "Maximum range (in blocks) between a transmitter and receiver.",
            "Set to 0 for infinite range (same dimension only).",
            "WARNING: Extremely large ranges may cause chunk loading issues",
            "or unexpected behavior with cross-dimension connections.",
            "Range: 0 ~ 2147483647, Default: 0"
        })
        @Config.RangeInt(min = 0, max = Integer.MAX_VALUE)
        public int maxRange = 0;

        @Config.Comment({
            "Power draw (AE/t) for an active wireless channel transmitter.",
            "WARNING: Very high power draw may exceed your network generation",
            "capacity and cause devices to shut down.",
            "Range: 1 ~ 2147483647, Default: 512"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int transmitterPower = 512;

        @Config.Comment({
            "Extra upgrade slots added to all AE2 devices (Parts and Tiles).",
            "This compensates for the channel receiver card occupying a slot.",
            "WARNING: Excessive values may cause GUI overflow or rendering",
            "issues since upgrade slot positions are fixed in AE2 GUIs.",
            "Range: 0 ~ 2147483647, Default: 2"
        })
        @Config.RangeInt(min = 0, max = Integer.MAX_VALUE)
        public int extraUpgradeSlots = 2;
    }

    public static class BlackHole {
        @Config.Comment({
            "Damage dealt by the Micro Singularity event horizon.",
            "  ALL          - All living entities are instantly killed, including creative-mode players.",
            "  NON_CREATIVE - Only non-creative entities are killed; creative players are immune.",
            "  NONE         - No damage is dealt; the black hole only decays after its lifetime expires.",
            "Default: ALL"
        })
        @Config.Name("damageMode")
        public String damageMode = "ALL";

        public DamageMode getDamageMode() {
            return parseDamageMode(damageMode);
        }
    }

    @Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
    public static class SyncHandler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (AE2Enhanced.MOD_ID.equals(event.getModID())) {
                ConfigManager.sync(AE2Enhanced.MOD_ID, Config.Type.INSTANCE);
            }
        }
    }
}
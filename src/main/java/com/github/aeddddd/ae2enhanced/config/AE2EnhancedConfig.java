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

    @Config.Name("Terminal")
    @Config.Comment({
        "Omni Terminal settings."
    })
    public static Terminal terminal = new Terminal();

    @Config.Name("SmartPattern")
    @Config.Comment({
        "Smart Pattern Interface settings.",
        "Controls recipe query limits, blacklist, and overload protection."
    })
    public static SmartPattern smartPattern = new SmartPattern();

    @Config.Name("Thaumcraft")
    @Config.Comment("Thaumcraft automation settings for Central ME Interface")
    public static Thaumcraft thaumcraft = new Thaumcraft();

    @Config.Name("Energy")
    @Config.Comment({
        "RF energy bridge settings.",
        "Controls the behavior of RF Access Node when adjacent to creative energy sources."
    })
    public static Energy energy = new Energy();

    @Config.Name("AdvancedPlatform")
    @Config.Comment({
        "Advanced Central Platform settings.",
        "Controls RF energy storage, platform generation, and chunk loading."
    })
    public static AdvancedPlatform advancedPlatform = new AdvancedPlatform();

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

        @Config.Comment({
            "Full-scan interval for the AE2 NetworkMonitor cache reconciliation (ticks).",
            "The Hyperdimensional Storage Nexus uses postAlterationOfStoredItems() for",
            "real-time incremental updates. This full scan is ONLY a safety net to correct",
            "any cache drift that may occur under extreme edge cases (e.g. chunk load/unload",
            "races, network splits, or AE2-UEL internal inconsistencies).",
            "",
            "Set to 0 to disable the safety-net scan entirely. If you observe ME terminals",
            "showing stale or incorrect item counts, try lowering this value. If performance",
            "is critical and terminals remain accurate, raise it or set to 0.",
            "",
            "WARNING: Values below 100 (5 seconds) defeat the purpose and may reintroduce",
            "the lag caused by excessive forceUpdate() calls. The default of 200 ticks",
            "(10 seconds) provides a reasonable balance between correctness and performance.",
            "Range: 0 ~ 72000, Default: 200 (10 seconds)"
        })
        @Config.RangeInt(min = 0, max = 72000)
        public int monitorFullScanIntervalTicks = 200;
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

    public static class Terminal {
        @Config.Comment({
            "Max stack size for right-side pattern storage and upgrade slots in the Omni Terminal.",
            "This allows storing more than 64 items per slot in the terminal's internal buffers.",
            "Default: 4096"
        })
        @Config.RangeInt(min = 1, max = Integer.MAX_VALUE)
        public int rightStorageMaxStackSize = 4096;
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

        @Config.Comment({
            "Interval (in ticks) for automatic wireless channel connection validation.",
            "The system scans all cached wireless connections and destroys stale ones.",
            "Stale connections are then rebuilt on the next valid event (inventory change,",
            "chunk load, or world reload). Lower values improve recovery speed but add tick overhead.",
            "Set to 0 to disable automatic validation entirely (connections are only checked",
            "during inventory changes and world reloads).",
            "Range: 0 ~ 72000, Default: 100 (5 seconds)"
        })
        @Config.RangeInt(min = 0, max = 72000)
        public int reconnectIntervalTicks = 100;
    }

    public static class SmartPattern {
        @Config.Comment({
            "Maximum number of recipes a Smart Pattern can hold.",
            "If the target machine has more recipes than this limit,",
            "only the first N recipes will be included (truncation).",
            "Range: 1 ~ 4096, Default: 256"
        })
        @Config.RangeInt(min = 1, max = 4096)
        public int maxRecipes = 256;

        @Config.Comment({
            "Blacklist of block registry names that cannot be bound to the Smart Pattern Interface.",
            "Format: modid:blockname (e.g. minecraft:furnace)",
            "Default: [minecraft:furnace, extendedcrafting:crafting_table_base]"
        })
        public String[] blacklist = {
            "minecraft:furnace",
            "extendedcrafting:crafting_table_base"
        };

        @Config.Comment({
            "Garbage collection interval for orphaned Smart Pattern files (minutes).",
            "The collector scans ME interfaces for referenced patterns and deletes",
            "files that are both unreferenced and older than the newest file by gcMaxAgeDays.",
            "Set to 0 to disable automatic garbage collection entirely.",
            "Range: 0 ~ 10080, Default: 1440 (24 hours)"
        })
        @Config.RangeInt(min = 0, max = 10080)
        public int gcIntervalMinutes = 1440;

        @Config.Comment({
            "Maximum age of an unreferenced Smart Pattern file before deletion (days).",
            "Files referenced by any loaded ME interface are exempt from age checks.",
            "Range: 1 ~ 365, Default: 7"
        })
        @Config.RangeInt(min = 1, max = 365)
        public int gcMaxAgeDays = 7;
    }

    public static class BlackHole {
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

    public static class Energy {
        @Config.Comment({
            "When enabled, RF Access Node adjacent to a Draconic Evolution Creative RF Source",
            "will directly inject energy into the ME network as long, bypassing the int limit",
            "of Forge Energy API (2.147B per tick).",
            "Default: true"
        })
        public boolean creativeRfSourceBoostEnabled = true;

        @Config.Comment({
            "Amount of RF to inject per operation when adjacent to a creative RF source.",
            "Supports scientific notation (e.g. '1.0E12' for 1 trillion, '2.5E9' for 2.5 billion).",
            "Default: 1.0E12"
        })
        public String creativeRfSourceBoostAmount = "1.0E12";

        public long getParsedBoostAmount() {
            try {
                double val = Double.parseDouble(creativeRfSourceBoostAmount.trim());
                if (val <= 0) return 0;
                if (val >= Long.MAX_VALUE) return Long.MAX_VALUE;
                return (long) val;
            } catch (NumberFormatException e) {
                return 1_000_000_000_000L;
            }
        }
    }

    public static class AdvancedPlatform {
        @Config.Comment({
            "Platform surface block registry name.",
            "Default: minecraft:concrete"
        })
        public String platformSurfaceBlock = "minecraft:concrete";

        @Config.Comment({
            "Platform surface block metadata (color).",
            "Default: 0 (white)"
        })
        @Config.RangeInt(min = 0, max = 15)
        public int platformSurfaceMeta = 0;

        @Config.Comment({
            "Platform edge block registry name.",
            "Default: minecraft:concrete"
        })
        public String platformEdgeBlock = "minecraft:concrete";

        @Config.Comment({
            "Platform edge block metadata (color).",
            "Default: 15 (black)"
        })
        @Config.RangeInt(min = 0, max = 15)
        public int platformEdgeMeta = 15;

        @Config.Comment({
            "Platform center block registry name.",
            "Default: minecraft:concrete"
        })
        public String platformCenterBlock = "minecraft:concrete";

        @Config.Comment({
            "Platform center block metadata (color).",
            "Default: 0 (white)"
        })
        @Config.RangeInt(min = 0, max = 15)
        public int platformCenterMeta = 0;

        @Config.Comment({
            "Vertical range above platform surface for facility scanning.",
            "Range: 0 ~ 256, Default: 32"
        })
        @Config.RangeInt(min = 0, max = 256)
        public int facilityScanVerticalRange = 32;

        @Config.Comment({
            "Enable forced chunk loading for platform chunks.",
            "Default: true"
        })
        public boolean platformChunkLoading = true;

        @Config.Comment({
            "Controller internal RF buffer capacity.",
            "Range: 100000 ~ 2147483647, Default: 10000000"
        })
        @Config.RangeInt(min = 100000, max = 2147483647)
        public int controllerRfBufferCapacity = 10000000;

        @Config.Comment({
            "Controller RF extraction limit per tick from ME network.",
            "Range: 1 ~ 1000000, Default: 10000"
        })
        @Config.RangeInt(min = 1, max = 1000000)
        public int controllerRfExtractPerTick = 10000;

        @Config.Comment({
            "Blocks placed per tick during platform generation.",
            "Range: 1 ~ 1000, Default: 100"
        })
        @Config.RangeInt(min = 1, max = 1000)
        public int placementBlocksPerTick = 100;

        @Config.Comment({
            "Blocks scanned per tick during conflict detection.",
            "Range: 50 ~ 1000, Default: 400"
        })
        @Config.RangeInt(min = 50, max = 1000)
        public int scanBlocksPerTick = 400;
    }

    public static class Thaumcraft {
        @Config.Comment({
            "Automatically fill the crucible with water from the ME network if empty or low.",
            "If false, the pattern must specify water in its fluid slots.",
            "Default: true"
        })
        public boolean autoFillWater = true;

        @Config.Comment({
            "Clear crucible aspects and remaining water after each craft to prevent",
            "cross-contamination between different recipes.",
            "Default: true"
        })
        public boolean clearAfterCraft = true;
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
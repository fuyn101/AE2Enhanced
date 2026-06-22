package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 加载个人维度地板预设。
 */
public final class PresetLoader {

    private PresetLoader() {}

    private static FloorPreset cached;

    public static FloorPreset getPreset() {
        if (cached == null) {
            cached = load(AE2EnhancedConfig.personalDimension.presetPath);
        }
        return cached;
    }

    public static void reload() {
        cached = null;
    }

    public static FloorPreset load(String path) {
        if (path == null || path.isEmpty()) {
            return fallback();
        }
        FloorPreset fromFile = loadFromFile(path);
        if (fromFile != null) return fromFile;
        FloorPreset fromAsset = loadFromAsset(path);
        if (fromAsset != null) return fromAsset;
        AE2Enhanced.LOGGER.warn("[AE2E] Failed to load personal dimension preset from {}, using fallback.", path);
        return fallback();
    }

    private static FloorPreset loadFromFile(String path) {
        try {
            File file = new File(path);
            if (!file.isFile()) {
                file = new File(net.minecraftforge.fml.common.Loader.instance().getConfigDir(), path);
            }
            if (!file.isFile()) return null;
            try (InputStream in = new FileInputStream(file)) {
                return parse(in);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to load preset file {}", path, e);
            return null;
        }
    }

    private static FloorPreset loadFromAsset(String path) {
        String asset = path;
        if (!asset.startsWith("/")) {
            asset = "/" + asset;
        }
        try (InputStream in = AE2Enhanced.class.getResourceAsStream(asset)) {
            if (in == null) return null;
            return parse(in);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to load preset asset {}", asset, e);
            return null;
        }
    }

    private static FloorPreset parse(InputStream in) {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();

            JsonObject start = root.getAsJsonObject("startpos");
            JsonObject end = root.getAsJsonObject("endpos");
            int startX = start.get("X").getAsInt();
            int startZ = start.get("Z").getAsInt();
            int endX = end.get("X").getAsInt();
            int endZ = end.get("Z").getAsInt();
            int width = endX - startX + 1;
            int depth = endZ - startZ + 1;

            JsonArray map = root.getAsJsonArray("blockstatemap");
            IBlockState[] palette = new IBlockState[map.size()];
            for (int i = 0; i < map.size(); i++) {
                JsonObject entry = map.get(i).getAsJsonObject();
                String name = entry.get("Name").getAsString();
                palette[i] = resolveState(name);
            }

            JsonArray list = root.getAsJsonArray("statelist");
            int[] states = new int[list.size()];
            for (int i = 0; i < list.size(); i++) {
                states[i] = list.get(i).getAsInt();
            }

            return new FloorPreset(width, depth, palette, states);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to parse preset", e);
            return null;
        }
    }

    private static IBlockState resolveState(String name) {
        // GTCEu 未加载时映射到本 Mod 的替代方块
        if ("gtceu:yellow_stripes_block_b".equals(name)) {
            if (BlockRegistry.YELLOW_STRIPES_BLOCK_B != null) {
                return BlockRegistry.YELLOW_STRIPES_BLOCK_B.getDefaultState();
            }
        }
        ResourceLocation rl = new ResourceLocation(name);
        Block block = Block.REGISTRY.getObject(rl);
        if (block != null) {
            return block.getDefaultState();
        }
        AE2Enhanced.LOGGER.warn("[AE2E] Preset block {} not found, falling back to bedrock.", name);
        return Blocks.BEDROCK.getDefaultState();
    }

    private static FloorPreset fallback() {
        IBlockState[] palette = new IBlockState[] {
                BlockRegistry.YELLOW_STRIPES_BLOCK_B != null
                        ? BlockRegistry.YELLOW_STRIPES_BLOCK_B.getDefaultState()
                        : Blocks.BEDROCK.getDefaultState()
        };
        int[] states = new int[96 * 96];
        return new FloorPreset(96, 96, palette, states);
    }
}

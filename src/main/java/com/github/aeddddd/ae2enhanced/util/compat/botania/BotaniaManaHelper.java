package com.github.aeddddd.ae2enhanced.util.compat.botania;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Botania 魔力交互反射助手.
 *
 * <p>所有 Botania 类均通过字符串类名反射访问,避免在 Botania 未安装时触发
 * {@link NoClassDefFoundError}.本类仅由 {@code TileChunkManaNode} 在 Botania
 * 已加载的条件下使用.</p>
 */
public final class BotaniaManaHelper {

    private static final String MOD_ID = "botania";

    private static final String IMANA_RECEIVER = "vazkii.botania.api.mana.IManaReceiver";
    private static final String TILE_SPECIAL_FLOWER = "vazkii.botania.common.block.tile.TileSpecialFlower";
    private static final String SUB_TILE_GENERATING = "vazkii.botania.api.subtile.SubTileGenerating";
    private static final String BLOCK_MANA_VOID = "vazkii.botania.common.block.mana.BlockManaVoid";

    private static Class<?> imanaReceiverClass;
    private static Class<?> tileSpecialFlowerClass;
    private static Class<?> subTileGeneratingClass;
    private static Class<?> blockManaVoidClass;

    private static Method isFullMethod;
    private static Method receiveManaMethod;
    private static Field subTileField;

    private static boolean initialized = false;
    private static boolean available = false;

    private BotaniaManaHelper() {}

    public static boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    private static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        if (!net.minecraftforge.fml.common.Loader.isModLoaded(MOD_ID)) {
            return;
        }

        try {
            imanaReceiverClass = Class.forName(IMANA_RECEIVER);
            tileSpecialFlowerClass = Class.forName(TILE_SPECIAL_FLOWER);
            subTileGeneratingClass = Class.forName(SUB_TILE_GENERATING);
            blockManaVoidClass = Class.forName(BLOCK_MANA_VOID);

            isFullMethod = imanaReceiverClass.getMethod("isFull");
            receiveManaMethod = imanaReceiverClass.getMethod("recieveMana", int.class);

            subTileField = tileSpecialFlowerClass.getField("subTile");

            available = true;
        } catch (Throwable t) {
            imanaReceiverClass = null;
            tileSpecialFlowerClass = null;
            subTileGeneratingClass = null;
            blockManaVoidClass = null;
            isFullMethod = null;
            receiveManaMethod = null;
            subTileField = null;
            available = false;
        }
    }

    /**
     * 判断指定方块是否是 Mana Void.
     */
    public static boolean isManaVoid(World world, BlockPos pos) {
        if (!available || blockManaVoidClass == null) return false;
        try {
            return blockManaVoidClass.isInstance(world.getBlockState(pos).getBlock());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 判断指定 TileEntity 是否是 Botania 产能花(generating flower).
     */
    public static boolean isGeneratingFlower(TileEntity te) {
        if (!available || tileSpecialFlowerClass == null || subTileGeneratingClass == null || subTileField == null) {
            return false;
        }
        try {
            if (!tileSpecialFlowerClass.isInstance(te)) return false;
            Object subTile = subTileField.get(te);
            return subTile != null && subTileGeneratingClass.isInstance(subTile);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 判断指定 TileEntity 是否是可接收魔力的 IManaReceiver.
     */
    public static boolean isManaReceiver(TileEntity te) {
        if (!available || imanaReceiverClass == null) return false;
        return imanaReceiverClass.isInstance(te);
    }

    /**
     * 调用 IManaReceiver#isFull().
     */
    public static boolean isFull(TileEntity te) {
        if (!available || isFullMethod == null) return true;
        try {
            Object result = isFullMethod.invoke(te);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 调用 IManaReceiver#recieveMana(int).
     *
     * @param te     IManaReceiver 实例
     * @param amount 注入魔力量,Botania 内部会自动 clamp 到容量上限
     */
    public static void receiveMana(TileEntity te, int amount) {
        if (!available || receiveManaMethod == null || amount <= 0) return;
        try {
            receiveManaMethod.invoke(te, amount);
        } catch (Throwable ignored) {
        }
    }
}

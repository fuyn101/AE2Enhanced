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
    private static Method getCurrentManaMethod;
    private static Method getMaxManaMethod;

    private static Field subTileField;
    private static Field manaField;
    private static Field manaCapField;
    private static Field manaToGetField;

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

            // TilePool#getCurrentMana() 等常见查询方法
            try {
                getCurrentManaMethod = imanaReceiverClass.getMethod("getCurrentMana");
            } catch (NoSuchMethodException ignored) {
                getCurrentManaMethod = null;
            }

            subTileField = tileSpecialFlowerClass.getField("subTile");

            // 通用 mana / manaCap 字段(魔力池等)
            try {
                manaField = imanaReceiverClass.getField("mana");
            } catch (NoSuchFieldException ignored) {
                manaField = null;
            }
            try {
                manaCapField = imanaReceiverClass.getField("manaCap");
            } catch (NoSuchFieldException ignored) {
                manaCapField = null;
            }

            // 符文祭坛的配方目标魔力
            try {
                manaToGetField = Class.forName("vazkii.botania.common.block.tile.mana.TileRuneAltar").getField("manaToGet");
            } catch (Throwable ignored) {
                manaToGetField = null;
            }

            // 花/功能子 tile 的 getMaxMana()
            try {
                getMaxManaMethod = Class.forName("vazkii.botania.api.subtile.SubTileEntity").getMethod("getMaxMana");
            } catch (Throwable ignored) {
                getMaxManaMethod = null;
            }

            available = true;
        } catch (Throwable t) {
            imanaReceiverClass = null;
            tileSpecialFlowerClass = null;
            subTileGeneratingClass = null;
            blockManaVoidClass = null;
            isFullMethod = null;
            receiveManaMethod = null;
            getCurrentManaMethod = null;
            getMaxManaMethod = null;
            subTileField = null;
            manaField = null;
            manaCapField = null;
            manaToGetField = null;
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

    /**
     * 获取目标当前 Mana.
     *
     * <p>按以下优先级尝试：</p>
     * <ol>
     *   <li>IManaReceiver#getCurrentMana()</li>
     *   <li>public int mana 字段</li>
     *   <li>TileSpecialFlower 子 tile 的 mana 字段</li>
     * </ol>
     *
     * @return 当前 mana,无法获取时返回 0
     */
    public static int getCurrentMana(TileEntity te) {
        if (!available || !isManaReceiver(te)) return 0;
        try {
            if (getCurrentManaMethod != null) {
                Object result = getCurrentManaMethod.invoke(te);
                if (result instanceof Number) return ((Number) result).intValue();
            }
            if (manaField != null) {
                Object result = manaField.get(te);
                if (result instanceof Number) return ((Number) result).intValue();
            }
            if (tileSpecialFlowerClass != null && tileSpecialFlowerClass.isInstance(te) && subTileField != null) {
                Object subTile = subTileField.get(te);
                if (subTile != null) {
                    try {
                        Field subMana = subTile.getClass().getField("mana");
                        Object result = subMana.get(subTile);
                        if (result instanceof Number) return ((Number) result).intValue();
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /**
     * 获取目标 Mana 容量.
     *
     * <p>按以下优先级尝试：</p>
     * <ol>
     *   <li>public int manaCap 字段(魔力池等)</li>
     *   <li>TileSpecialFlower 子 tile 的 getMaxMana()</li>
     *   <li>符文祭坛的 manaToGet 字段(目标配方 mana)</li>
     * </ol>
     *
     * @return 容量,无法获取时返回 0
     */
    public static int getManaCapacity(TileEntity te) {
        if (!available || !isManaReceiver(te)) return 0;
        try {
            if (manaCapField != null) {
                Object result = manaCapField.get(te);
                if (result instanceof Number) return ((Number) result).intValue();
            }
            if (tileSpecialFlowerClass != null && tileSpecialFlowerClass.isInstance(te) && subTileField != null && getMaxManaMethod != null) {
                Object subTile = subTileField.get(te);
                if (subTile != null) {
                    Object result = getMaxManaMethod.invoke(subTile);
                    if (result instanceof Number) return ((Number) result).intValue();
                }
            }
            if (manaToGetField != null && manaToGetField.getDeclaringClass().isInstance(te)) {
                Object result = manaToGetField.get(te);
                if (result instanceof Number) return ((Number) result).intValue();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }
}

package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.lang.reflect.Field;

/**
 * 实体 ID 计数器辅助工具。
 *
 * <p>用于修复动态维度（个人维度）反复卸载/加载后 {@link World#entityId} 计数器归零，
 * 导致新实体与已加载实体 ID 冲突的问题。逻辑放在独立的非 mixin 类中，避免业务代码
 * 直接引用 mixin 类而触发 {@link NoClassDefFoundError}。</p>
 */
public final class EntityIdHelper {

    private static final Field ENTITY_ID_FIELD;

    static {
        ENTITY_ID_FIELD = resolveField(World.class, "entityId", "field_72993_J");
    }

    private EntityIdHelper() {}

    private static Field resolveField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * 将当前世界的 entityId 计数器提升到所有已加载实体最大 ID 之后。
     *
     * @param world 要调整的世界
     * @param minEntityId 计数器必须不低于该值
     */
    public static void bumpEntityIdCounter(WorldServer world, int minEntityId) {
        if (ENTITY_ID_FIELD == null) return;
        if (world.isRemote) return;

        int maxId = minEntityId;
        for (Entity e : world.loadedEntityList) {
            int id = e.getEntityId();
            if (id > maxId) maxId = id;
        }

        try {
            int current = ENTITY_ID_FIELD.getInt(world);
            int target = Math.max(maxId + 1, current);
            if (target > current) {
                ENTITY_ID_FIELD.setInt(world, target);
            }
        } catch (Exception ignored) {
        }
    }
}

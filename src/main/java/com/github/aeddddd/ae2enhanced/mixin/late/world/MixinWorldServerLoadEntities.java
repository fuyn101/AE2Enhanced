package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * 修复动态维度（个人维度）反复卸载/加载后 entityId 计数器归零导致的实体 ID 冲突。
 *
 * <p>WorldServer 实例重建后 {@link World#entityId} 会从 0 开始计数，但磁盘 chunk 中的实体
 * 仍保留之前的 ID。后续新实体生成时会分配到与已加载实体相同的 ID，触发
 * {@code World.addEntityId} 对旧实体重新分配 ID，但 EntityTracker 中的 key 不会同步更新，
 * 最终抛出 {@code "Entity is already tracked!"} 并导致实体追踪异常。</p>
 *
 * <p>此 mixin 在 chunk 从 NBT 加载完实体后（{@link WorldServer#loadEntities}）以及
 * WorldServer 常规实体生成路径中，将 entityId 计数器提升到所有已加载实体最大 ID 之后，
 * 从根本上避免 ID 冲突。</p>
 */
@Mixin(value = WorldServer.class, remap = true)
public class MixinWorldServerLoadEntities {

    private static final Field ENTITY_ID_FIELD;

    static {
        Field f = resolveField(World.class, "entityId", "field_72993_J");
        ENTITY_ID_FIELD = f;
    }

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

    @Inject(method = "loadEntities", at = @At("RETURN"))
    private void ae2e$afterLoadEntities(Collection<? extends Entity> entities, CallbackInfo ci) {
        if (entities == null || entities.isEmpty()) return;
        WorldServer world = (WorldServer) (Object) this;
        if (world.isRemote) return;
        if (!PersonalDimensionManager.isPersonalDimension(world.provider.getDimension())) return;

        bumpEntityIdCounter(world, 0);
    }

    /**
     * 将当前世界的 entityId 计数器提升到所有已加载实体最大 ID 之后。
     * 公开给 PersonalDimensionManager 在玩家进入个人维度前调用。
     *
     * @param minEntityId 计数器必须不低于该值，用于确保高于玩家自身的固定 entityId
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

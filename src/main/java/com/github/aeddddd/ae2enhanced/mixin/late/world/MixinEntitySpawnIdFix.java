package com.github.aeddddd.ae2enhanced.mixin.late.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * 修复玩家跨维度传送时目标世界已存在相同 entityId 的实体导致的
 * "Entity is already tracked!" 错误。
 *
 * <p>个人维度等动态维度与世界间频繁传送后，目标世界的 entityId 计数器可能递增到与
 * 玩家固定 entityId 相同的值；同时 EntityTracker.trackedEntities 里也可能残留旧记录。
 * World.spawnEntity 对已有非零 ID 的实体不会重新分配 ID，从而触发 IllegalStateException。
 * 此 mixin 在玩家实体加入世界前：先清理 EntityTracker 中可能残留的记录，再检查
 * World.entitiesById 是否冲突，若冲突则通过反射递增 World.entityId 字段分配新的唯一 ID。</p>
 */
@Mixin(value = World.class, remap = true)
public class MixinEntitySpawnIdFix {

    private static final Field ENTITY_ID_FIELD;

    static {
        Field f;
        try {
            f = World.class.getDeclaredField("entityId");
            f.setAccessible(true);
        } catch (Exception e) {
            f = null;
        }
        ENTITY_ID_FIELD = f;
    }

    @Inject(method = "spawnEntity", at = @At("HEAD"))
    private void ae2e$fixPlayerEntityIdConflict(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null || entity.world.isRemote) return;
        if (!(entity instanceof EntityPlayerMP)) return;
        if (ENTITY_ID_FIELD == null) return;

        World world = (World) (Object) this;

        // 清理 EntityTracker 中可能残留的同名记录
        if (world instanceof WorldServer) {
            try {
                EntityTracker tracker = ((WorldServer) world).getEntityTracker();
                if (tracker != null) {
                    tracker.untrack(entity);
                }
            } catch (Exception ignored) {
            }
        }

        // 检查 World.entitiesById 是否冲突；冲突则重新分配唯一 ID
        for (int i = 0; i < 10000; i++) {
            Entity existing = world.getEntityByID(entity.getEntityId());
            if (existing == null || existing == entity) {
                break;
            }
            try {
                int newId = ENTITY_ID_FIELD.getInt(world);
                ENTITY_ID_FIELD.setInt(world, newId + 1);
                entity.setEntityId(newId);
            } catch (Exception ignored) {
                break;
            }
        }
    }
}

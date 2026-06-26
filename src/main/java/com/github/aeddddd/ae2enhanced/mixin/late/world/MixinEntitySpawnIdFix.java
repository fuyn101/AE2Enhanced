package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.EntityIdHelper;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
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
 * 修复玩家进入个人维度时目标世界已存在相同 entityId 的实体导致的
 * "Entity is already tracked!" 错误。
 *
 * <p>作为 {@link MixinWorldServerLoadEntities} 的兜底：如果由于某些边界情况，
 * 玩家进入个人维度时 entityId 仍与已存在实体冲突，则在 {@link World#spawnEntity}
 * 正式加入世界前为玩家重新分配一个大于当前计数器的唯一 ID，避免触发
 * EntityTracker 的重复 key 异常。</p>
 */
@Mixin(value = World.class, remap = true)
public class MixinEntitySpawnIdFix {

    private static final Field ENTITY_ID_FIELD = EntityIdHelper.getEntityIdField();

    @Inject(method = "spawnEntity", at = @At("HEAD"))
    private void ae2e$fixPlayerEntityIdConflict(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null || entity.world.isRemote) return;
        if (!(entity instanceof EntityPlayerMP)) return;
        if (ENTITY_ID_FIELD == null) return;

        World world = (World) (Object) this;
        if (!PersonalDimensionManager.isPersonalDimension(world.provider.getDimension())) return;

        // 清理 EntityTracker 中可能残留的玩家旧记录
        if (world instanceof WorldServer) {
            try {
                EntityTracker tracker = ((WorldServer) world).getEntityTracker();
                if (tracker != null) {
                    tracker.untrack(entity);
                }
            } catch (Exception ignored) {
            }
        }

        // 若仍有实体占用玩家 ID，给玩家分配一个高于计数器的全新 ID
        for (int i = 0; i < 10000; i++) {
            Entity existing = world.getEntityByID(entity.getEntityId());
            if (existing == null || existing == entity) {
                break;
            }
            try {
                int newId = ENTITY_ID_FIELD.getInt(world);
                ENTITY_ID_FIELD.setInt(world, newId + 1);
                entity.setEntityId(newId);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to bump entity id for player {}", entity.getName(), e);
                break;
            }
        }
    }
}

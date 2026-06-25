package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.EntityIdHelper;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 修复动态维度（个人维度）反复卸载/加载后，实体 ID 计数器归零导致跨维度传送或
 * chunk 加载时出现 entityId 冲突，进而触发 {@code Entity is already tracked!} 崩溃。
 *
 * <p>在 {@link World#onEntityAdded} 的 HEAD 注入：此时 chunk 已同步加载、其他实体
 * 已加入 tracker，可以检测到真正的冲突来源（包括玩家与 chunk 中刚加载的实体）。</p>
 *
 * <ul>
 *   <li><b>非玩家实体</b>：直接重新分配一个大于当前计数器的唯一 ID，避免破坏 tracker；
 *       客户端会在随后收到带有新 ID 的生成/同步包。</li>
 *   <li><b>玩家实体</b>：客户端在维度切换时会保留旧玩家对象的 entityId
 *       ({@code Minecraft#setDimensionAndSpawnPlayer})，因此不宜修改玩家 ID。
 *       仅在涉及个人维度（来源或目标）的传送中，若发生冲突则移除占用该 ID 的冲突实体，
 *       作为从损坏状态中恢复的兜底。</li>
 * </ul>
 */
@Mixin(value = World.class, remap = true)
public class MixinEntitySpawnIdFix {

    private static final Field ENTITY_ID_FIELD = EntityIdHelper.getEntityIdField();

    @Inject(method = "onEntityAdded", at = @At("HEAD"))
    private void ae2e$fixEntityIdConflict(Entity entity, CallbackInfo ci) {
        if (entity == null) return;
        World world = (World) (Object) this;
        if (world.isRemote) return;

        Entity existing = world.getEntityByID(entity.getEntityId());
        if (existing == null || existing == entity) return;

        if (entity instanceof EntityPlayerMP) {
            handlePlayerConflict((EntityPlayerMP) entity, world, existing);
        } else {
            reassignNonPlayerId(entity, world);
        }
    }

    private static void handlePlayerConflict(EntityPlayerMP player, World world, Entity existing) {
        boolean toPersonal = PersonalDimensionManager.isPersonalDimension(player.dimension);
        boolean fromPersonal = player.world instanceof WorldServer
                && PersonalDimensionManager.isPersonalDimension(player.world.provider.getDimension());
        if (!toPersonal && !fromPersonal) return;

        AE2Enhanced.LOGGER.warn(
                "[AE2E] Player {} entity id {} conflicts with {} in dim {} (source dim {}). Removing conflicting entity to recover.",
                player.getName(), player.getEntityId(), existing.getClass().getSimpleName(),
                player.dimension, player.world.provider.getDimension());

        try {
            world.removeEntityDangerously(existing);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to remove conflicting entity", e);
        }
        if (world instanceof WorldServer) {
            try {
                ((WorldServer) world).getEntityTracker().untrack(existing);
            } catch (Exception ignored) {
            }
        }
    }

    private static void reassignNonPlayerId(Entity entity, World world) {
        if (ENTITY_ID_FIELD == null) return;
        if (!(world instanceof WorldServer)) return;

        EntityIdHelper.bumpEntityIdCounter((WorldServer) world, entity.getEntityId());
        try {
            int newId = ENTITY_ID_FIELD.getInt(world);
            ENTITY_ID_FIELD.setInt(world, newId + 1);
            entity.setEntityId(newId);
            AE2Enhanced.LOGGER.warn(
                    "[AE2E] Reassigned {} entity id to {} to avoid conflict in dim {}",
                    entity.getClass().getSimpleName(), newId, world.provider.getDimension());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to reassign entity id", e);
        }
    }
}

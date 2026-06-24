package com.github.aeddddd.ae2enhanced.dimension.rules;

import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;

/**
 * 统一应用/重置个人维度的玩家能力（飞行、移动速度、飞行惯性）。
 *
 * <p>将原本散落在 {@code PersonalDimensionManager} 中的能力逻辑抽离，
 * 避免 WorldTick 与 PlayerTick 重复刷新，并提供一致的 reset 行为。</p>
 */
public final class PlayerAbilityApplier {

    private PlayerAbilityApplier() {}

    /**
     * 根据个人维度规则应用飞行与移动速度。
     *
     * @param player 目标玩家
     * @param rules  维度规则
     * @return 若能力发生变化返回 true
     */
    public static boolean applyFlightRules(EntityPlayerMP player, PersonalDimensionRules rules) {
        PlayerCapabilities cap = player.capabilities;
        boolean changed = false;

        boolean shouldFly = player.isCreative() || rules.flightEnabled;
        if (cap.allowFlying != shouldFly) {
            cap.allowFlying = shouldFly;
            if (!shouldFly) {
                cap.isFlying = false;
            }
            changed = true;
        }

        float speed = clampMovementSpeed(rules.movementSpeed);
        if (Math.abs(cap.getFlySpeed() - speed) > 1e-4f || Math.abs(cap.getWalkSpeed() - speed) > 1e-4f) {
            cap.setFlySpeed(speed);
            cap.setPlayerWalkSpeed(speed);
            changed = true;
        }

        if (changed) {
            player.sendPlayerAbilities();
        }

        if (rules.noFlightInertia && cap.isFlying) {
            if (player.moveForward == 0.0f && player.moveStrafing == 0.0f) {
                player.motionX = 0.0;
                player.motionZ = 0.0;
            }
        }

        return changed;
    }

    /**
     * 将玩家能力恢复为默认值。
     *
     * <p>仅在玩家离开个人维度或重生时调用。创造模式玩家的飞行能力不会被清除。</p>
     *
     * @param player 目标玩家
     */
    public static void resetAbilities(EntityPlayerMP player) {
        if (player.isCreative()) return;
        PlayerCapabilities cap = player.capabilities;
        boolean changed = false;
        if (cap.allowFlying) {
            cap.allowFlying = false;
            changed = true;
        }
        if (cap.isFlying) {
            cap.isFlying = false;
            changed = true;
        }
        if (Math.abs(cap.getWalkSpeed() - 0.1f) > 1e-4f) {
            cap.setPlayerWalkSpeed(0.1f);
            changed = true;
        }
        if (Math.abs(cap.getFlySpeed() - 0.05f) > 1e-4f) {
            cap.setFlySpeed(0.05f);
            changed = true;
        }
        if (changed) {
            player.sendPlayerAbilities();
        }
    }

    /**
     * 校验并限制移动速度在合理范围内，防止客户端伪造极大/极小值。
     */
    public static float clampMovementSpeed(float speed) {
        if (Float.isNaN(speed) || speed < 0.05f) return 0.05f;
        if (speed > 2.0f) return 2.0f;
        return speed;
    }
}

package com.github.aeddddd.ae2enhanced.platform.selection;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRTSStateChange;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSelectionUpdate;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端选区管理器 —— 管理所有玩家的 RTS 状态与选区数据。
 * 玩家下线/死亡/跨维度时自动清空。
 */
public class SelectionManager {

    private static final Map<UUID, Selection> selections = new HashMap<>();
    private static final Map<UUID, BlockPos> controllerBindings = new HashMap<>();
    private static final Map<UUID, Boolean> rtsStates = new HashMap<>();

    public static void enterRTS(EntityPlayerMP player, BlockPos controllerPos) {
        UUID id = player.getUniqueID();
        rtsStates.put(id, true);
        controllerBindings.put(id, controllerPos);
        selections.put(id, new Selection());
    }

    public static void exitRTS(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        rtsStates.remove(id);
        controllerBindings.remove(id);
        selections.remove(id);
        AE2Enhanced.network.sendTo(new PacketRTSStateChange(false), player);
    }

    public static void forceExit(UUID playerId) {
        rtsStates.remove(playerId);
        controllerBindings.remove(playerId);
        selections.remove(playerId);
    }

    public static boolean isInRTS(UUID playerId) {
        return rtsStates.getOrDefault(playerId, false);
    }

    public static Selection getSelection(UUID playerId) {
        return selections.get(playerId);
    }

    public static BlockPos getControllerPos(UUID playerId) {
        return controllerBindings.get(playerId);
    }

    public static void syncSelectionToPlayer(EntityPlayerMP player) {
        Selection sel = getSelection(player.getUniqueID());
        if (sel != null) {
            AE2Enhanced.network.sendTo(new PacketSelectionUpdate(sel), player);
        }
    }
}

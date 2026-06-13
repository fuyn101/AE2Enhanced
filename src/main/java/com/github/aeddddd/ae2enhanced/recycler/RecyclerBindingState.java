package com.github.aeddddd.ae2enhanced.recycler;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 回收节点的绑定状态.
 */
public class RecyclerBindingState {

    @Nullable
    public UUID playerId;
    public long expireTick;

    public boolean isBinding(UUID playerId, long currentTick) {
        return this.playerId != null && this.playerId.equals(playerId) && currentTick < expireTick;
    }

    public boolean isBindingActive(long currentTick) {
        return this.playerId != null && currentTick < expireTick;
    }

    public void start(UUID playerId, long currentTick, int durationTicks) {
        this.playerId = playerId;
        this.expireTick = currentTick + durationTicks;
    }

    public void clear() {
        this.playerId = null;
        this.expireTick = 0;
    }
}

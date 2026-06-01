package com.github.aeddddd.ae2enhanced.platform.io;

public class ZoneActivityTracker {
    private ActivityLevel level = ActivityLevel.ACTIVE;
    private int ticksSinceLastIo = 0;
    private int consecutiveFailures = 0;

    public void onSuccessfulIo() {
        this.ticksSinceLastIo = 0;
        this.consecutiveFailures = 0;
    }

    public void onTick() {
        this.ticksSinceLastIo++;
    }

    public void onFailure() {
        this.consecutiveFailures++;
        if (this.consecutiveFailures >= this.level.downgradeThreshold) {
            ActivityLevel next = this.level.nextLower();
            if (next != this.level) {
                this.level = next;
                this.consecutiveFailures = 0;
            }
        }
    }

    public boolean shouldScanThisTick(long tickCounter) {
        if (this.level.scanInterval <= 1) {
            return true;
        }
        return tickCounter % this.level.scanInterval == 0;
    }

    public ActivityLevel getLevel() {
        return this.level;
    }

    public int getTicksSinceLastIo() {
        return this.ticksSinceLastIo;
    }

    public int getConsecutiveFailures() {
        return this.consecutiveFailures;
    }
}

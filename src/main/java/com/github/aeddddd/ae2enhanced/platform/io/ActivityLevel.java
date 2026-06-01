package com.github.aeddddd.ae2enhanced.platform.io;

public enum ActivityLevel {
    ACTIVE(1, 100),
    WARM(5, 500),
    COLD(20, 2000),
    FROZEN(100, Integer.MAX_VALUE);

    public final int scanInterval;
    public final int downgradeThreshold;

    ActivityLevel(int scanInterval, int downgradeThreshold) {
        this.scanInterval = scanInterval;
        this.downgradeThreshold = downgradeThreshold;
    }

    public ActivityLevel nextLower() {
        switch (this) {
            case ACTIVE:
                return WARM;
            case WARM:
                return COLD;
            case COLD:
                return FROZEN;
            case FROZEN:
                return FROZEN;
            default:
                return FROZEN;
        }
    }
}

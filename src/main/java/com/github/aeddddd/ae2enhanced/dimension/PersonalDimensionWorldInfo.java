package com.github.aeddddd.ae2enhanced.dimension;

import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraft.world.storage.WorldInfo;

/**
 * 个人维度专用的 DerivedWorldInfo。
 *
 * 原版非主世界维度使用 {@link DerivedWorldInfo}，将时间/天气等全部委托给主世界，
 * 导致个人维度的时间、天气与主世界同步。此类将时间/天气独立出来，按玩家规则持久化，
 * 同时继续委托其他世界信息（如游戏类型、种子等）给主世界。
 */
public class PersonalDimensionWorldInfo extends DerivedWorldInfo {

    private final WorldInfo parent;
    private final PersonalDimensionRules rules;
    private final Runnable markDirty;

    private long worldTime;
    private boolean raining;
    private boolean thundering;
    private int rainTime;
    private int thunderTime;
    private int cleanWeatherTime;

    public PersonalDimensionWorldInfo(WorldInfo parent, PersonalDimensionRules rules, Runnable markDirty) {
        super(parent);
        this.parent = parent;
        this.rules = rules;
        this.markDirty = markDirty;

        this.worldTime = rules.timeValue;
        if (rules.lockWeather) {
            this.raining = false;
            this.thundering = false;
            this.rainTime = 0;
            this.thunderTime = 0;
            this.cleanWeatherTime = Integer.MAX_VALUE;
        } else {
            this.raining = parent.isRaining();
            this.thundering = parent.isThundering();
            this.rainTime = parent.getRainTime();
            this.thunderTime = parent.getThunderTime();
            this.cleanWeatherTime = parent.getCleanWeatherTime();
        }
    }

    @Override
    public long getWorldTime() {
        return worldTime;
    }

    @Override
    public void setWorldTime(long time) {
        if (!rules.daylightCycle || rules.lockTime) {
            time = rules.timeValue;
        }
        if (this.worldTime != time) {
            this.worldTime = time;
            this.rules.timeValue = time;
            if (markDirty != null) markDirty.run();
        }
    }

    @Override
    public boolean isRaining() {
        return rules.lockWeather ? false : raining;
    }

    @Override
    public void setRaining(boolean raining) {
        if (rules.lockWeather) return;
        if (this.raining != raining) {
            this.raining = raining;
            if (markDirty != null) markDirty.run();
        }
    }

    @Override
    public boolean isThundering() {
        return rules.lockWeather ? false : thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        if (rules.lockWeather) return;
        if (this.thundering != thundering) {
            this.thundering = thundering;
            if (markDirty != null) markDirty.run();
        }
    }

    @Override
    public int getRainTime() {
        return rainTime;
    }

    @Override
    public void setRainTime(int time) {
        if (rules.lockWeather) return;
        if (this.rainTime != time) {
            this.rainTime = time;
            if (markDirty != null) markDirty.run();
        }
    }

    @Override
    public int getThunderTime() {
        return thunderTime;
    }

    @Override
    public void setThunderTime(int time) {
        if (rules.lockWeather) return;
        if (this.thunderTime != time) {
            this.thunderTime = time;
            if (markDirty != null) markDirty.run();
        }
    }

    @Override
    public int getCleanWeatherTime() {
        return cleanWeatherTime;
    }

    @Override
    public void setCleanWeatherTime(int time) {
        if (rules.lockWeather) {
            this.cleanWeatherTime = Integer.MAX_VALUE;
            return;
        }
        if (this.cleanWeatherTime != time) {
            this.cleanWeatherTime = time;
            if (markDirty != null) markDirty.run();
        }
    }
}

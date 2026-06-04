package com.github.aeddddd.ae2enhanced.crafting.scheduler;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

import java.lang.reflect.Field;

/**
 * Reflection helper for accessing the private {@code value} field inside
 * CraftingCPUCluster$TaskProgress.
 *
 * <p>AE2-UEL keeps obfuscated names, so we look up the field by type and name
 * at runtime. If reflection fails, the scheduler falls back to native mode.</p>
 */
public class TaskProgressAccessor {

    private static final String TASK_PROGRESS_CLASS = "appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress";
    private static Field valueField;
    private static boolean ready = false;
    private static boolean failed = false;

    public static boolean isReady() {
        if (ready) return true;
        if (failed) return false;
        init();
        return ready;
    }

    private static synchronized void init() {
        if (ready || failed) return;
        try {
            Class<?> clazz = Class.forName(TASK_PROGRESS_CLASS);
            valueField = clazz.getDeclaredField("value");
            valueField.setAccessible(true);
            ready = true;
        } catch (Exception e) {
            failed = true;
            AE2Enhanced.LOGGER.error("[AE2E] TaskProgress reflection init failed. Scheduler disabled. {}", e.toString());
        }
    }

    public static long getValue(Object progress) {
        if (!ready || valueField == null) return 0;
        try {
            return valueField.getLong(progress);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    public static void setValue(Object progress, long v) {
        if (!ready || valueField == null) return;
        try {
            valueField.setLong(progress, v);
        } catch (IllegalAccessException e) {
            // ignored
        }
    }
}

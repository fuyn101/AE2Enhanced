package com.github.aeddddd.ae2enhanced.integration.projecte;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.math.BigInteger;

/**
 * 通过反射访问被 Mixin 注入到 ProjectE KnowledgeImpl.DefaultImpl 中的 BigInteger EMC 方法。
 */
public final class ProjectEBigEmcHelper {

    private static final String METHOD_GET_EMC_BIG = "ae2e$getEmcBig";
    private static final String METHOD_ADD_EMC = "ae2e$addEmc";
    private static final String METHOD_SUBTRACT_EMC = "ae2e$subtractEmc";

    private static Class<?> cachedProviderClass;
    private static Method getEmcBigMethod;
    private static Method addEmcMethod;
    private static Method subtractEmcMethod;

    private ProjectEBigEmcHelper() {}

    private static void ensureMethods(Class<?> clazz) {
        if (getEmcBigMethod != null && cachedProviderClass != null && cachedProviderClass.isAssignableFrom(clazz)) {
            return;
        }
        try {
            getEmcBigMethod = clazz.getMethod(METHOD_GET_EMC_BIG);
            addEmcMethod = clazz.getMethod(METHOD_ADD_EMC, long.class);
            subtractEmcMethod = clazz.getMethod(METHOD_SUBTRACT_EMC, long.class);
            cachedProviderClass = clazz;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to locate ProjectE BigInteger EMC methods on {}", clazz, e);
            getEmcBigMethod = null;
            addEmcMethod = null;
            subtractEmcMethod = null;
            cachedProviderClass = null;
        }
    }

    public static boolean isBigEmcProvider(Object provider) {
        if (provider == null) return false;
        ensureMethods(provider.getClass());
        return getEmcBigMethod != null;
    }

    @Nonnull
    public static BigInteger getEmcBig(@Nonnull Object provider) {
        ensureMethods(provider.getClass());
        if (getEmcBigMethod == null) return BigInteger.ZERO;
        try {
            Object result = getEmcBigMethod.invoke(provider);
            return result instanceof BigInteger ? (BigInteger) result : BigInteger.ZERO;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to get BigInteger EMC", e);
            return BigInteger.ZERO;
        }
    }

    public static void addEmc(@Nonnull Object provider, long value) {
        if (value <= 0) return;
        ensureMethods(provider.getClass());
        if (addEmcMethod == null) return;
        try {
            addEmcMethod.invoke(provider, value);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to add BigInteger EMC {}", value, e);
        }
    }

    public static void subtractEmc(@Nonnull Object provider, long value) {
        if (value <= 0) return;
        ensureMethods(provider.getClass());
        if (subtractEmcMethod == null) return;
        try {
            subtractEmcMethod.invoke(provider, value);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to subtract BigInteger EMC {}", value, e);
        }
    }
}

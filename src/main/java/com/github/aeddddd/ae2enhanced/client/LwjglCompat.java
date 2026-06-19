package com.github.aeddddd.ae2enhanced.client;

import java.lang.reflect.Method;

/**
 * LWJGL 反射兼容层，避免编译期直接依赖 org.lwjgl.input.*。
 */
public final class LwjglCompat {

    private LwjglCompat() {}

    private static Class<?> keyboardClass;
    private static Method isKeyDownMethod;
    private static Class<?> mouseClass;
    private static Method getDWheelMethod;

    static {
        try {
            keyboardClass = Class.forName("org.lwjgl.input.Keyboard");
            isKeyDownMethod = keyboardClass.getMethod("isKeyDown", int.class);
        } catch (Exception ignored) {
        }
        try {
            mouseClass = Class.forName("org.lwjgl.input.Mouse");
            getDWheelMethod = mouseClass.getMethod("getDWheel");
        } catch (Exception ignored) {
        }
    }

    public static final int KEY_LSHIFT = 42;
    public static final int KEY_RSHIFT = 54;
    public static final int KEY_LCONTROL = 29;
    public static final int KEY_RCONTROL = 157;
    public static final int KEY_LMENU = 56;
    public static final int KEY_RMENU = 184;

    public static boolean isKeyDown(int keyCode) {
        try {
            if (isKeyDownMethod != null) {
                return (boolean) isKeyDownMethod.invoke(null, keyCode);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static int getMouseDWheel() {
        try {
            if (getDWheelMethod != null) {
                return (int) getDWheelMethod.invoke(null);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}

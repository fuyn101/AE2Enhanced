package com.github.aeddddd.ae2enhanced.util.memorycard;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用内存卡 Handler 注册表。
 * 按注册顺序遍历，第一个返回 true 的 handler 被使用。
 */
public class MemoryCardHandlerRegistry {

    private static final List<IMemoryCardHandler> HANDLERS = new ArrayList<>();

    public static void register(IMemoryCardHandler handler) {
        HANDLERS.add(handler);
    }

    public static IMemoryCardHandler findHandler(Object target) {
        for (IMemoryCardHandler handler : HANDLERS) {
            if (handler.canHandle(target)) {
                return handler;
            }
        }
        return null;
    }

    public static void init() {
        register(new AE2PartHandler());
        register(new AE2TileHandler());
        register(new MekanismMachineHandler());
        register(new EnderIOMachineHandler());
        register(new EnderIOConduitHandler());
        register(new ThermalExpansionMachineHandler());
        register(new AE2EnhancedHandler());
    }
}

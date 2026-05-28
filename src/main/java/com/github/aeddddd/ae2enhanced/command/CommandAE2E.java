package com.github.aeddddd.ae2enhanced.command;

import javax.annotation.Nonnull;

/**
 * AE2Enhanced 缩写指令 {@code /ae2e}。
 * 逻辑与全名指令 {@link CommandAE2Enhanced} 完全一致。
 */
public class CommandAE2E extends CommandAE2Enhanced {

    @Override
    @Nonnull
    public String getName() {
        return "ae2e";
    }
}

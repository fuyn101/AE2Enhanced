package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 方块注册中心。
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> DR = DeferredRegister.create(Registries.BLOCK, AE2Enhanced.MOD_ID);

    private ModBlocks() {
    }
}

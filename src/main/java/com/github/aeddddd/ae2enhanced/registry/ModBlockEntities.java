package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 方块实体类型注册中心。
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> DR = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,
            AE2Enhanced.MOD_ID);

    private ModBlockEntities() {
    }
}

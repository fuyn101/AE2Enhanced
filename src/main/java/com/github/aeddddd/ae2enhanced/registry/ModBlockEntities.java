package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.blockentity.HyperdimensionalMeInterfaceBlockEntity;

/**
 * 方块实体类型注册中心。
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> DR = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,
            AE2Enhanced.MOD_ID);

    public static final RegistryObject<BlockEntityType<HyperdimensionalControllerBlockEntity>> HYPERDIMENSIONAL_CONTROLLER = DR
            .register("hyperdimensional_controller",
                    () -> BlockEntityType.Builder.of(HyperdimensionalControllerBlockEntity::new,
                            ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get()).build(null));

    public static final RegistryObject<BlockEntityType<HyperdimensionalMeInterfaceBlockEntity>> HYPERDIMENSIONAL_ME_INTERFACE = DR
            .register("hyperdimensional_me_interface",
                    () -> BlockEntityType.Builder.of(HyperdimensionalMeInterfaceBlockEntity::new,
                            ModBlocks.HYPERDIMENSIONAL_ME_INTERFACE.get()).build(null));

    private ModBlockEntities() {
    }
}

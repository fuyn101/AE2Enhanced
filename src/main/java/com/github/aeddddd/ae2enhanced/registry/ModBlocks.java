package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.block.HyperdimensionalCasingBlock;
import com.github.aeddddd.ae2enhanced.block.HyperdimensionalControllerBlock;
import com.github.aeddddd.ae2enhanced.block.HyperdimensionalSingularityCoreBlock;
import com.github.aeddddd.ae2enhanced.block.MultiblockInterfaceBlock;

/**
 * 方块注册中心。
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> DR = DeferredRegister.create(Registries.BLOCK, AE2Enhanced.MOD_ID);

    public static final RegistryObject<Block> HYPERDIMENSIONAL_CONTROLLER = DR.register("hyperdimensional_controller",
            () -> new HyperdimensionalControllerBlock(metalProperties()));

    public static final RegistryObject<Block> HYPERDIMENSIONAL_CASING = DR.register("hyperdimensional_casing",
            () -> new HyperdimensionalCasingBlock(metalProperties()));

    public static final RegistryObject<Block> HYPERDIMENSIONAL_SINGULARITY_CORE = DR.register(
            "hyperdimensional_singularity_core",
            () -> new HyperdimensionalSingularityCoreBlock(metalProperties().lightLevel(state -> 8)));

    public static final RegistryObject<Block> MULTIBLOCK_INTERFACE = DR.register("multiblock_interface",
            () -> new MultiblockInterfaceBlock(metalProperties()));

    private static BlockBehaviour.Properties metalProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(5.0F, 10.0F)
                .requiresCorrectToolForDrops();
    }

    private ModBlocks() {
    }
}

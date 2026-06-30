package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 物品注册中心。
 */
public final class ModItems {
    public static final DeferredRegister<Item> DR = DeferredRegister.create(Registries.ITEM, AE2Enhanced.MOD_ID);

    public static final RegistryObject<Item> HYPERDIMENSIONAL_CONTROLLER = DR.register("hyperdimensional_controller",
            () -> new BlockItem(ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> HYPERDIMENSIONAL_CASING = DR.register("hyperdimensional_casing",
            () -> new BlockItem(ModBlocks.HYPERDIMENSIONAL_CASING.get(), new Item.Properties()));

    public static final RegistryObject<Item> HYPERDIMENSIONAL_SINGULARITY_CORE = DR.register(
            "hyperdimensional_singularity_core",
            () -> new BlockItem(ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE.get(), new Item.Properties()));

    public static final RegistryObject<Item> MULTIBLOCK_ME_INTERFACE = DR.register("multiblock_me_interface",
            () -> new BlockItem(ModBlocks.MULTIBLOCK_ME_INTERFACE.get(), new Item.Properties()));

    private ModItems() {
    }
}

package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 创造模式物品栏注册中心。
 */
public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> DR = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            AE2Enhanced.MOD_ID);

    public static final RegistryObject<CreativeModeTab> AE2E_TAB = DR.register("ae2enhanced",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.ae2enhanced"))
                    .icon(() -> new ItemStack(Items.AIR))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get());
                        output.accept(ModBlocks.MULTIBLOCK_INTERFACE.get());
                        output.accept(ModBlocks.HYPERDIMENSIONAL_CASING.get());
                        output.accept(ModBlocks.HYPERDIMENSIONAL_SINGULARITY_CORE.get());
                    })
                    .build());

    private ModCreativeTab() {
    }
}

package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * 物品注册中心。
 */
public final class ModItems {
    public static final DeferredRegister<Item> DR = DeferredRegister.create(Registries.ITEM, AE2Enhanced.MOD_ID);

    private ModItems() {
    }
}

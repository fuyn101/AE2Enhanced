package com.github.aeddddd.ae2enhanced;

import com.github.aeddddd.ae2enhanced.item.ItemConformalCharge;
import com.github.aeddddd.ae2enhanced.item.ItemDifferentialFormStabilizer;
import com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.item.ItemStableSpacetimeManifold;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public class ModItems {

    public static ItemUpgradeCard UPGRADE_CARD;
    public static ItemConformalCharge CONFORMAL_CHARGE;
    public static ItemDifferentialFormStabilizer DIFFERENTIAL_FORM_STABILIZER;
    public static ItemStableSpacetimeManifold STABLE_SPACETIME_MANIFOLD;
    public static ItemEssentiaDrop ESSENTIA_DROP;
    public static ItemFluidDrop FLUID_DROP;
    public static ItemGasDrop GAS_DROP;

    public static void init() {
        UPGRADE_CARD = new ItemUpgradeCard();
        CONFORMAL_CHARGE = new ItemConformalCharge();
        DIFFERENTIAL_FORM_STABILIZER = new ItemDifferentialFormStabilizer();
        STABLE_SPACETIME_MANIFOLD = new ItemStableSpacetimeManifold();
        if (net.minecraftforge.fml.common.Loader.isModLoaded("thaumcraft")) {
            ESSENTIA_DROP = new ItemEssentiaDrop();
        }
        FLUID_DROP = new ItemFluidDrop();
        if (net.minecraftforge.fml.common.Loader.isModLoaded("mekanism") && net.minecraftforge.fml.common.Loader.isModLoaded("mekeng")) {
            GAS_DROP = new ItemGasDrop();
        }
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(UPGRADE_CARD);
        event.getRegistry().register(CONFORMAL_CHARGE);
        event.getRegistry().register(DIFFERENTIAL_FORM_STABILIZER);
        event.getRegistry().register(STABLE_SPACETIME_MANIFOLD);
        if (ESSENTIA_DROP != null) {
            event.getRegistry().register(ESSENTIA_DROP);
        }
        event.getRegistry().register(FLUID_DROP);
        if (GAS_DROP != null) {
            event.getRegistry().register(GAS_DROP);
        }
    }
}

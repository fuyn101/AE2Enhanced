package com.github.aeddddd.ae2enhanced;

import com.github.aeddddd.ae2enhanced.item.ItemConformalCharge;
import com.github.aeddddd.ae2enhanced.item.ItemDifferentialFormStabilizer;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import com.github.aeddddd.ae2enhanced.item.ItemPartStockingBus;
import com.github.aeddddd.ae2enhanced.item.ItemPartUniversalExportBus;
import com.github.aeddddd.ae2enhanced.item.ItemPartUniversalImportBus;
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
    public static Item ESSENTIA_DROP;
    public static ItemFluidDrop FLUID_DROP;
    public static Item GAS_DROP;
    public static ItemPartUniversalImportBus PART_UNIVERSAL_IMPORT_BUS;
    public static ItemPartUniversalExportBus PART_UNIVERSAL_EXPORT_BUS;
    public static ItemPartStockingBus PART_STOCKING_BUS;
    public static ItemChannelReceiverCard CHANNEL_RECEIVER_CARD;

    public static void init() {
        UPGRADE_CARD = new ItemUpgradeCard();
        CONFORMAL_CHARGE = new ItemConformalCharge();
        DIFFERENTIAL_FORM_STABILIZER = new ItemDifferentialFormStabilizer();
        STABLE_SPACETIME_MANIFOLD = new ItemStableSpacetimeManifold();
        try {
            if (net.minecraftforge.fml.common.Loader.isModLoaded("thaumcraft")) {
                ESSENTIA_DROP = (Item) Class.forName("com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop").newInstance();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to instantiate ItemEssentiaDrop", e);
        }
        FLUID_DROP = new ItemFluidDrop();
        try {
            if (net.minecraftforge.fml.common.Loader.isModLoaded("mekanism") && net.minecraftforge.fml.common.Loader.isModLoaded("mekeng")) {
                GAS_DROP = (Item) Class.forName("com.github.aeddddd.ae2enhanced.item.ItemGasDrop").newInstance();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to instantiate ItemGasDrop", e);
        }
        PART_UNIVERSAL_IMPORT_BUS = new ItemPartUniversalImportBus();
        PART_UNIVERSAL_EXPORT_BUS = new ItemPartUniversalExportBus();
        PART_STOCKING_BUS = new ItemPartStockingBus();
        CHANNEL_RECEIVER_CARD = new ItemChannelReceiverCard();
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
        event.getRegistry().register(PART_UNIVERSAL_IMPORT_BUS);
        event.getRegistry().register(PART_UNIVERSAL_EXPORT_BUS);
        event.getRegistry().register(PART_STOCKING_BUS);
        event.getRegistry().register(CHANNEL_RECEIVER_CARD);
    }
}

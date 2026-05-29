package com.github.aeddddd.ae2enhanced.registry;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipe;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipeRegistry;
import appeng.api.AEApi;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方注册中心：工作台配方、黑洞合成配方、奇点仪式配方
 */
public final class ModRecipes {

    private ModRecipes() {}

    public static void init() {
        registerCentralInterfaceRecipe();
        registerSmartPatternInterfaceRecipe();
        registerSingularityRecipes();
    }

    private static void registerCentralInterfaceRecipe() {
        ItemStack centralInterface = new ItemStack(ModBlocks.CENTRAL_ME_INTERFACE, 4);
        ItemStack stableManifold = new ItemStack(ModItems.STABLE_SPACETIME_MANIFOLD);
        ItemStack ae2Interface = AEApi.instance().definitions().blocks().iface()
                .maybeStack(1).orElse(ItemStack.EMPTY);

        if (!ae2Interface.isEmpty() && !stableManifold.isEmpty()) {
            GameRegistry.addShapedRecipe(
                    new ResourceLocation(AE2Enhanced.MOD_ID, "central_me_interface"),
                    null,
                    centralInterface,
                    " I ", "IMI", " I ",
                    'I', ae2Interface,
                    'M', stableManifold
            );
        } else {
            AE2Enhanced.LOGGER.warn("[AE2E] 无法注册中枢 ME 接口合成配方：AE2 ME 接口或稳态时空流形不可用");
        }
    }

    private static void registerSmartPatternInterfaceRecipe() {
        ItemStack smartPatternInterface = new ItemStack(ModBlocks.SMART_PATTERN_INTERFACE, 1);
        ItemStack ae2Interface = AEApi.instance().definitions().blocks().iface()
                .maybeStack(1).orElse(ItemStack.EMPTY);
        ItemStack netherStar = new ItemStack(Items.NETHER_STAR);

        if (!ae2Interface.isEmpty() && !netherStar.isEmpty()) {
            Ingredient ingInterface = Ingredient.fromStacks(ae2Interface);
            Ingredient ingStar = Ingredient.fromStacks(netherStar);
            GameRegistry.addShapelessRecipe(
                    new ResourceLocation(AE2Enhanced.MOD_ID, "smart_pattern_interface"),
                    null,
                    smartPatternInterface,
                    ingInterface, ingInterface, ingInterface, ingInterface,
                    ingInterface, ingInterface, ingInterface, ingInterface,
                    ingStar
            );
        } else {
            AE2Enhanced.LOGGER.warn("[AE2E] 无法注册智能样板接口合成配方：AE2 ME 接口或下界之星不可用");
        }
    }

    private static void registerSingularityRecipes() {
        // 系统 A：黑洞生成仪式
        Item ae2Material = Item.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "material"));
        if (ae2Material != null) {
            List<ItemStack> ritualInputs = new ArrayList<>();
            ritualInputs.add(new ItemStack(ae2Material, 64, 47)); // AE2 奇点
            ritualInputs.add(new ItemStack(Items.NETHER_STAR, 4));
            SingularityRecipeRegistry.register(new SingularityRecipe("micro_singularity_ritual", ritualInputs));
        } else {
            AE2Enhanced.LOGGER.warn("无法获取 AE2 材料物品，黑洞生成仪式配方未注册");
        }

        // 系统 B：黑洞合成配方
        registerBlackHoleRecipes();

        // 执行 CraftTweaker 延迟移除
        BlackHoleRecipeRegistry.applyPendingRemovals();
    }

    private static void registerBlackHoleRecipes() {
        // 测试配方：8 石头 + 1 钻石 → 1 黑曜石
        Map<String, Integer> bhInputs = new HashMap<>();
        bhInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Blocks.STONE)), 8);
        bhInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Items.DIAMOND)), 1);
        BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                "test_obsidian", bhInputs, new ItemStack(Blocks.OBSIDIAN, 1)));

        Item ae2Material = Item.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "material"));
        if (ae2Material != null) {
            // 稳态时空流形：16 空间组件 + 64 奇点
            Map<String, Integer> manifoldInputs = new HashMap<>();
            manifoldInputs.put("appliedenergistics2:material:34", 16);
            manifoldInputs.put("appliedenergistics2:material:47", 64);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "stable_spacetime_manifold", manifoldInputs,
                    new ItemStack(ModItems.STABLE_SPACETIME_MANIFOLD, 1)));

            // 微分形式稳定单元：128 奇点 + 16 下界之星
            Map<String, Integer> stabilizerInputs = new HashMap<>();
            stabilizerInputs.put("appliedenergistics2:material:47", 128);
            stabilizerInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Items.NETHER_STAR)), 16);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "differential_form_stabilizer", stabilizerInputs,
                    new ItemStack(ModItems.DIFFERENTIAL_FORM_STABILIZER, 1)));
        }

        // 共形不变荷：16 稳态时空流形 + 16 微分形式稳定单元
        Map<String, Integer> chargeInputs = new HashMap<>();
        chargeInputs.put(BlackHoleRecipe.keyOf(new ItemStack(ModItems.STABLE_SPACETIME_MANIFOLD)), 16);
        chargeInputs.put(BlackHoleRecipe.keyOf(new ItemStack(ModItems.DIFFERENTIAL_FORM_STABILIZER)), 16);
        BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                "conformal_invariant_charge", chargeInputs,
                new ItemStack(ModItems.CONFORMAL_CHARGE, 1)));

        // 智能空白样板：64 空白样板
        if (ae2Material != null) {
            Map<String, Integer> smartPatternInputs = new HashMap<>();
            smartPatternInputs.put("appliedenergistics2:material:52", 64);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "smart_blank_pattern", smartPatternInputs,
                    new ItemStack(ModItems.SMART_BLANK_PATTERN, 1)));
        }
    }
}

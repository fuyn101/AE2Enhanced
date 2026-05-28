package com.github.aeddddd.ae2enhanced;

import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipe;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipeRegistry;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeEssentias;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeGases;
import appeng.api.config.Upgrades;
import com.github.aeddddd.ae2enhanced.network.packet.PacketCraftRequestLong;
import com.github.aeddddd.ae2enhanced.network.packet.PacketMEMonitorableAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketPatternPage;
import com.github.aeddddd.ae2enhanced.network.packet.PacketRequestAssembly;
import com.github.aeddddd.ae2enhanced.network.packet.PacketStockingBusConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSetSlotAmount;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUniversalBusConfig;
import com.github.aeddddd.ae2enhanced.proxy.CommonProxy;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = AE2Enhanced.MOD_ID,
    name = AE2Enhanced.MOD_NAME,
    version = AE2Enhanced.VERSION,
    dependencies = "required-after:appliedenergistics2"
)
public class AE2Enhanced {

    public static final String MOD_ID = "ae2enhanced";
    public static final String MOD_NAME = "AE2Enhanced";
    public static final String VERSION = "1.5.0";

    public static final String CLIENT_PROXY = "com.github.aeddddd.ae2enhanced.proxy.ClientProxy";
    public static final String SERVER_PROXY = "com.github.aeddddd.ae2enhanced.proxy.CommonProxy";

    @Mod.Instance(MOD_ID)
    public static AE2Enhanced instance;

    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = SERVER_PROXY)
    public static CommonProxy proxy;

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static SimpleNetworkWrapper network;

    private static final java.lang.reflect.Method DAMAGE_ENTITY_METHOD;
    static {
        java.lang.reflect.Method m = null;
        try {
            m = EntityLivingBase.class.getDeclaredMethod("func_70665_d", DamageSource.class, float.class);
            m.setAccessible(true);
        } catch (Exception e) {
            LOGGER.error("[AE2E] Failed to cache damageEntity method", e);
        }
        DAMAGE_ENTITY_METHOD = m;
    }

    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(MOD_ID) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(ModItems.CONFORMAL_CHARGE);
        }
    };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigManager.sync(MOD_ID, net.minecraftforge.common.config.Config.Type.INSTANCE);
        ModItems.init();
        FakeFluids.init();
        if (net.minecraftforge.fml.common.Loader.isModLoaded("mekanism") && net.minecraftforge.fml.common.Loader.isModLoaded("mekeng")) {
            FakeGases.init();
        }
        if (net.minecraftforge.fml.common.Loader.isModLoaded("thaumcraft")) {
            FakeEssentias.init();
        }
        network = new SimpleNetworkWrapper(MOD_ID);
        network.registerMessage(PacketRequestAssembly.Handler.class, PacketRequestAssembly.class, 0, Side.SERVER);
        network.registerMessage(PacketPatternPage.Handler.class, PacketPatternPage.class, 1, Side.SERVER);
        network.registerMessage(PacketCraftRequestLong.Handler.class, PacketCraftRequestLong.class, 2, Side.SERVER);
        network.registerMessage(PacketMEMonitorableAction.Handler.class, PacketMEMonitorableAction.class, 3, Side.SERVER);
        network.registerMessage(PacketUniversalBusConfig.Handler.class, PacketUniversalBusConfig.class, 4, Side.SERVER);
        network.registerMessage(PacketStockingBusConfig.Handler.class, PacketStockingBusConfig.class, 5, Side.SERVER);
        network.registerMessage(PacketUMCAction.Handler.class, PacketUMCAction.class, 6, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketOmniTermAction.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketOmniTermAction.class, 7, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketLoadOmniRecipe.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketLoadOmniRecipe.class, 8, Side.SERVER);
        network.registerMessage(PacketSetSlotAmount.Handler.class, PacketSetSlotAmount.class, 9, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketOmniCraftingUpdate.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketOmniCraftingUpdate.class, 10, Side.CLIENT);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniTerminal.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniTerminal.class, 11, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketToggleMagnet.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketToggleMagnet.class, 12, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketPickerAction.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketPickerAction.class, 13, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternToggle.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternToggle.class, 14, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternScroll.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternScroll.class, 15, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternEncode.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternEncode.class, 16, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternBind.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternBind.class, 17, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternModify.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternModify.class, 18, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternReplace.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternReplace.class, 19, Side.SERVER);
        network.registerMessage(com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternMiniGuiScroll.Handler.class, com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternMiniGuiScroll.class, 20, Side.SERVER);
        com.github.aeddddd.ae2enhanced.util.memorycard.core.MemoryCardHandlerRegistry.init();
        com.github.aeddddd.ae2enhanced.centralinterface.HandlerRegistry.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new GuiHandler());
        proxy.init(event);

        // MixinBooter / CleanroomMC 环境检测
        boolean hasMixinBooter = net.minecraftforge.fml.common.Loader.isModLoaded("mixinbooter");
        boolean hasCleanroom = false;
        try {
            Class.forName("com.cleanroommc.common.launch.ActualClassLoader");
            hasCleanroom = true;
        } catch (ClassNotFoundException ignored) {
            try {
                Class.forName("com.cleanroommc.loader.ActualClassLoader");
                hasCleanroom = true;
            } catch (ClassNotFoundException ignored2) {}
        }
        if (!hasMixinBooter && !hasCleanroom) {
            LOGGER.error("[AE2E] ============================================================");
            LOGGER.error("[AE2E] CRITICAL: MixinBooter not detected!");
            LOGGER.error("[AE2E] AE2Enhanced requires MixinBooter on standard Forge environments.");
            LOGGER.error("[AE2E] Without it, all Mixin-based features will be silently disabled:");
            LOGGER.error("[AE2E]   - Fluid/Gas/Essentia terminal display & interaction");
            LOGGER.error("[AE2E]   - Batch crafting (long order support)");
            LOGGER.error("[AE2E]   - Creative cell capacity override");
            LOGGER.error("[AE2E]   - Stocking Bus / Universal Bus enhancements");
            LOGGER.error("[AE2E] Please install MixinBooter:");
            LOGGER.error("[AE2E]   https://www.curseforge.com/minecraft/mc-mods/mixinbooter");
            LOGGER.error("[AE2E] ============================================================");
        }

        // E1a：注册通用输入总线支持的升级卡类型
        if (ModItems.PART_UNIVERSAL_IMPORT_BUS != null) {
            net.minecraft.item.ItemStack busStack = new net.minecraft.item.ItemStack(ModItems.PART_UNIVERSAL_IMPORT_BUS);
            Upgrades.SPEED.registerItem(busStack, 4);
            Upgrades.CAPACITY.registerItem(busStack, 5);
            Upgrades.REDSTONE.registerItem(busStack, 1);
            Upgrades.FUZZY.registerItem(busStack, 1);
            Upgrades.CRAFTING.registerItem(busStack, 1);
        }
        // E1b：注册通用输出总线支持的升级卡类型
        if (ModItems.PART_UNIVERSAL_EXPORT_BUS != null) {
            net.minecraft.item.ItemStack busStack = new net.minecraft.item.ItemStack(ModItems.PART_UNIVERSAL_EXPORT_BUS);
            Upgrades.SPEED.registerItem(busStack, 4);
            Upgrades.CAPACITY.registerItem(busStack, 5);
            Upgrades.REDSTONE.registerItem(busStack, 1);
            Upgrades.FUZZY.registerItem(busStack, 1);
            Upgrades.CRAFTING.registerItem(busStack, 1);
        }
        // Stocking 总线升级卡
        if (ModItems.PART_STOCKING_BUS != null) {
            net.minecraft.item.ItemStack busStack = new net.minecraft.item.ItemStack(ModItems.PART_STOCKING_BUS);
            Upgrades.SPEED.registerItem(busStack, 4);
            Upgrades.CAPACITY.registerItem(busStack, 4);
            Upgrades.REDSTONE.registerItem(busStack, 1);
            Upgrades.FUZZY.registerItem(busStack, 1);
            Upgrades.CRAFTING.registerItem(busStack, 1);
        }

        // 中枢 ME 接口升级卡支持
        if (ModBlocks.CENTRAL_ME_INTERFACE != null) {
            net.minecraft.item.ItemStack centralInterface = new net.minecraft.item.ItemStack(ModBlocks.CENTRAL_ME_INTERFACE);
            Upgrades.PATTERN_EXPANSION.registerItem(centralInterface, 3);
        }

        // 中枢 ME 接口合成配方：4 ME 接口围绕稳态时空流形，产出 4 个
        registerCentralInterfaceRecipe();

        registerSingularityRecipes();
        // 执行 CraftTweaker 延迟移除（CT 脚本可能在 init() 之前执行）
        BlackHoleRecipeRegistry.applyPendingRemovals();
        // 注册共形不变荷为物质炮弹药（weight 1E8 → 伤害 5,000,000）
        appeng.api.AEApi.instance().registries().matterCannon().registerAmmo(
                new ItemStack(ModItems.CONFORMAL_CHARGE), 100_000_000.0);
        // 注册智能样板垃圾回收器（扫描 ME 接口 + 过期文件删除）
        com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternGarbageCollector.init();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new com.github.aeddddd.ae2enhanced.command.CommandAE2Enhanced());
        event.registerServerCommand(new com.github.aeddddd.ae2enhanced.command.CommandAE2E());
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!"matter_cannon".equals(event.getSource().getDamageType())) return;
        if (event.getAmount() <= 1_000_000.0f) return;

        EntityLivingBase entity = event.getEntityLiving();
        World world = entity.world;
        double x = entity.posX;
        double y = entity.posY + entity.height / 2.0;
        double z = entity.posZ;

        // ① 粒子爆发
        if (!world.isRemote) {
            for (int i = 0; i < 10; i++) {
                world.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE,
                        x + (world.rand.nextDouble() - 0.5) * 2.0,
                        y + (world.rand.nextDouble() - 0.5) * 2.0,
                        z + (world.rand.nextDouble() - 0.5) * 2.0,
                        0.0, 0.0, 0.0);
            }
            for (int i = 0; i < 20; i++) {
                world.spawnParticle(EnumParticleTypes.PORTAL,
                        x + (world.rand.nextDouble() - 0.5) * 3.0,
                        y + (world.rand.nextDouble() - 0.5) * 3.0,
                        z + (world.rand.nextDouble() - 0.5) * 3.0,
                        world.rand.nextGaussian() * 0.5,
                        world.rand.nextGaussian() * 0.5,
                        world.rand.nextGaussian() * 0.5);
            }
            for (int i = 0; i < 20; i++) {
                world.spawnParticle(EnumParticleTypes.END_ROD,
                        x + (world.rand.nextDouble() - 0.5) * 2.0,
                        y + (world.rand.nextDouble() - 0.5) * 2.0,
                        z + (world.rand.nextDouble() - 0.5) * 2.0,
                        world.rand.nextGaussian() * 0.3,
                        world.rand.nextGaussian() * 0.3,
                        world.rand.nextGaussian() * 0.3);
            }
        }

        // ② 处决伤害：反射调用 damageEntity 绕过 Forge 事件系统（限伤/无敌帧）
        if (DAMAGE_ENTITY_METHOD != null) {
            try {
                DamageSource exec = new DamageSource("ae2enhanced_conformal");
                exec.setDamageIsAbsolute();
                DAMAGE_ENTITY_METHOD.invoke(entity, exec, Float.MAX_VALUE);
            } catch (Exception e) {
                LOGGER.warn("[AE2E] Conformal damage reflection failed, falling back", e);
                entity.setHealth(0.0f);
            }
        } else {
            entity.setHealth(0.0f);
        }

        // ③ 虚空伤害（同样反射绕过限伤）
        if (DAMAGE_ENTITY_METHOD != null) {
            try {
                DAMAGE_ENTITY_METHOD.invoke(entity, DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
            } catch (Exception e) {
                LOGGER.warn("[AE2E] Void damage reflection failed", e);
            }
        }

        // ④ 击退（由伤害源实体施加）
        if (event.getSource().getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) event.getSource().getTrueSource();
            double dx = attacker.posX - entity.posX;
            double dz = attacker.posZ - entity.posZ;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.001) {
                dx /= dist;
                dz /= dist;
                entity.knockBack(attacker, 4.0f, -dx, -dz);
            }
        }

        // ⑤ 燃烧
        entity.setFire(10);
    }

    private void registerCentralInterfaceRecipe() {
        ItemStack centralInterface = new ItemStack(ModBlocks.CENTRAL_ME_INTERFACE, 4);
        ItemStack stableManifold = new ItemStack(ModItems.STABLE_SPACETIME_MANIFOLD);

        // 获取 AE2 ME 接口方块物品
        ItemStack ae2Interface = appeng.api.AEApi.instance().definitions().blocks().iface()
                .maybeStack(1).orElse(ItemStack.EMPTY);

        if (!ae2Interface.isEmpty() && !stableManifold.isEmpty()) {
            net.minecraftforge.fml.common.registry.GameRegistry.addShapedRecipe(
                    new net.minecraft.util.ResourceLocation(MOD_ID, "central_me_interface"),
                    null,
                    centralInterface,
                    " I ", "IMI", " I ",
                    'I', ae2Interface,
                    'M', stableManifold
            );
        } else {
            LOGGER.warn("[AE2E] 无法注册中枢 ME 接口合成配方：AE2 ME 接口或稳态时空流形不可用");
        }
    }

    private void registerSingularityRecipes() {
        // 系统 A：黑洞生成仪式 —— 64 AE2 奇点 + 4 下界之星 + 1 ME 控制器方块
        // 手持下界之星右键 ME 控制器触发，生成微型奇点
        Item ae2Material = Item.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "material"));
        if (ae2Material != null) {
            java.util.List<ItemStack> ritualInputs = new java.util.ArrayList<>();
            // AE2 奇点 metadata = 47
            ritualInputs.add(new ItemStack(ae2Material, 64, 47));
            ritualInputs.add(new ItemStack(Items.NETHER_STAR, 4));
            SingularityRecipeRegistry.register(new SingularityRecipe("micro_singularity_ritual", ritualInputs));

        } else {
            AE2Enhanced.LOGGER.warn("无法获取 AE2 材料物品，黑洞生成仪式配方未注册");
        }

        // 系统 B：黑洞合成 —— 把物品投入黑洞事件视界后转化为产物
        registerBlackHoleRecipes();
    }

    private void registerBlackHoleRecipes() {
        // 保留测试配方：8 石头 + 1 钻石 → 1 黑曜石（验证黑洞合成系统）
        java.util.Map<String, Integer> bhInputs = new java.util.HashMap<>();
        bhInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Blocks.STONE)), 8);
        bhInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Items.DIAMOND)), 1);
        BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                "test_obsidian",
                bhInputs,
                new ItemStack(Blocks.OBSIDIAN, 1)
        ));

        // 新材料黑洞合成配方（key 格式："registryName:meta"）
        Item ae2Material = Item.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "material"));
        if (ae2Material != null) {
            // 稳态时空流形：16 空间组件(material:34) + 64 奇点(material:47)
            java.util.Map<String, Integer> manifoldInputs = new java.util.HashMap<>();
            manifoldInputs.put("appliedenergistics2:material:34", 16);
            manifoldInputs.put("appliedenergistics2:material:47", 64);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "stable_spacetime_manifold",
                    manifoldInputs,
                    new ItemStack(ModItems.STABLE_SPACETIME_MANIFOLD, 1)
            ));

            // 微分形式稳定单元：128 奇点(material:47) + 16 下界之星
            java.util.Map<String, Integer> stabilizerInputs = new java.util.HashMap<>();
            stabilizerInputs.put("appliedenergistics2:material:47", 128);
            stabilizerInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Items.NETHER_STAR)), 16);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "differential_form_stabilizer",
                    stabilizerInputs,
                    new ItemStack(ModItems.DIFFERENTIAL_FORM_STABILIZER, 1)
            ));
        }

        // 共形不变荷：16 稳态时空流形 + 16 微分形式稳定单元
        java.util.Map<String, Integer> chargeInputs = new java.util.HashMap<>();
        chargeInputs.put(BlackHoleRecipe.keyOf(new ItemStack(ModItems.STABLE_SPACETIME_MANIFOLD)), 16);
        chargeInputs.put(BlackHoleRecipe.keyOf(new ItemStack(ModItems.DIFFERENTIAL_FORM_STABILIZER)), 16);
        BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                "conformal_invariant_charge",
                chargeInputs,
                new ItemStack(ModItems.CONFORMAL_CHARGE, 1)
        ));


    }


}

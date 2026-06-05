package com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.fluids.util.AEFluidStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IThaumcraftRecipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Thaumcraft 6 坩埚远程处理器。
 *
 * <p>支持中枢 ME 接口对坩埚的自动化：
 * <ul>
 *   <li>自动补水：水量不足时自动从 ME 网络抽取水注入坩埚（可配置开关）</li>
 *   <li>热量等待：热量不足时不硬失败，返回 false 让 CPU 下次 tick 重试</li>
 *   <li>研究绕过：使用 FakePlayer 临时授予所有坩埚配方研究</li>
 *   <li>源质残留管理：合成后自动调用 spillRemnants 清空（可配置开关）</li>
 * </ul>
 *
 * <p><b>重要</b>：坩埚合成是瞬时的，且对投入顺序敏感。
 * 玩家制作样板时必须按正确顺序放置物品：先源质来源（被分解成 aspects），
 * 后催化剂（触发合成）。如果顺序颠倒，催化剂会在 aspects 不足时被分解，导致合成失败。</p>
 */
public class ThaumcraftCrucibleHandler implements IRemoteHandler {

    private static final String BLOCK_ID = "thaumcraft:crucible";
    private static final GameProfile CRUCIBLE_PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("ae2e-crucible".getBytes()), "[AE2E]");
    private static final Set<String> GRANTED_RESEARCH = new HashSet<>();

    private static Class<?> CLASS_TILE_CRUCIBLE;
    private static Class<?> CLASS_ENTITY_SPECIAL_ITEM;
    private static Method METHOD_ATTEMPT_SMELT;
    private static Method METHOD_SPILL_REMNANTS;
    private static Method METHOD_GET_ITEM;
    private static Field FIELD_HEAT;
    private static Field FIELD_TANK;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_TILE_CRUCIBLE = Class.forName("thaumcraft.common.tiles.crafting.TileCrucible");
            CLASS_ENTITY_SPECIAL_ITEM = Class.forName("thaumcraft.common.entities.EntitySpecialItem");

            METHOD_ATTEMPT_SMELT = CLASS_TILE_CRUCIBLE.getMethod("attemptSmelt", ItemStack.class, String.class);
            METHOD_SPILL_REMNANTS = CLASS_TILE_CRUCIBLE.getMethod("spillRemnants");
            // EntityItem.getItem() 运行时 SRG 名为 func_92059_d
            METHOD_GET_ITEM = CLASS_ENTITY_SPECIAL_ITEM.getMethod("func_92059_d");

            FIELD_HEAT = CLASS_TILE_CRUCIBLE.getField("heat");
            FIELD_TANK = CLASS_TILE_CRUCIBLE.getField("tank");

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ThaumcraftCrucibleHandler reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return BLOCK_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_TILE_CRUCIBLE.isInstance(te);
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_CRUCIBLE.isInstance(te)) return false;

        boolean hasItems = false;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) {
                hasItems = true;
                break;
            }
        }
        return hasItems;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_CRUCIBLE.isInstance(te)) return false;

        try {
            // 热量检查（不足时返回 false，CPU 会在下次 tick 重试，实现热量等待）
            short heat = (short) FIELD_HEAT.get(te);
            if (heat <= 150) {
                return false;
            }

            // 水量检查与自动补水
            FluidTank tank = (FluidTank) FIELD_TANK.get(te);
            if (tank.getFluidAmount() < 50) {
                if (AE2EnhancedConfig.thaumcraft.autoFillWater) {
                    if (!autoFillWater(world, pos, te, source)) {
                        return false;
                    }
                } else {
                    return false;
                }
            }

            // 收集物品
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    items.add(stack.copy());
                }
            }
            if (items.isEmpty()) return false;

            // FakePlayer 研究绕过
            String username = ensureResearch(world);

            // 处理物品
            for (ItemStack stack : items) {
                ItemStack remaining = (ItemStack) METHOD_ATTEMPT_SMELT.invoke(te, stack, username);
                if (remaining == null) {
                    remaining = ItemStack.EMPTY;
                }
            }
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] ThaumcraftCrucibleHandler.pushMaterials failed at {}", pos, e);
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs) {
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();

        AxisAlignedBB aabb = new AxisAlignedBB(
                pos.getX() - 0.5, pos.getY() + 0.5, pos.getZ() - 0.5,
                pos.getX() + 1.5, pos.getY() + 3.0, pos.getZ() + 1.5
        );

        try {
            @SuppressWarnings("unchecked")
            Class<? extends Entity> clazz = (Class<? extends Entity>) CLASS_ENTITY_SPECIAL_ITEM;
            for (Entity entity : world.getEntitiesWithinAABB(clazz, aabb)) {
                if (!CLASS_ENTITY_SPECIAL_ITEM.isInstance(entity)) continue;

                ItemStack item = (ItemStack) METHOD_GET_ITEM.invoke(entity);
                if (item != null && !item.isEmpty()) {
                    result.add(item.copy());
                    entity.setDead();
                }
            }

            // 源质残留管理
            if (AE2EnhancedConfig.thaumcraft.clearAfterCraft) {
                TileEntity te = world.getTileEntity(pos);
                if (CLASS_TILE_CRUCIBLE.isInstance(te)) {
                    METHOD_SPILL_REMNANTS.invoke(te);
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] ThaumcraftCrucibleHandler.collectProducts failed at {}", pos, e);
        }

        return result;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        return collectProducts(world, pos, null, null, source);
    }

    // ---- 增强功能 ----

    private boolean autoFillWater(World world, BlockPos pos, TileEntity te, IActionSource source) {
        try {
            FluidTank tank = (FluidTank) FIELD_TANK.get(te);
            int current = tank.getFluidAmount();
            int capacity = tank.getCapacity();
            int needed = capacity - current;
            if (needed <= 0) return true;
            if (needed < 50) needed = 50;

            Optional<IActionHost> hostOpt = source.machine();
            if (!hostOpt.isPresent()) return false;

            IGridNode node = hostOpt.get().getActionableNode();
            if (node == null) return false;

            IGrid grid = node.getGrid();
            IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
            if (storageGrid == null) return false;

            IFluidStorageChannel fluidChannel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            IMEMonitor<IAEFluidStack> fluidInv = storageGrid.getInventory(fluidChannel);

            FluidStack water = new FluidStack(FluidRegistry.WATER, needed);
            IAEFluidStack aeWater = AEFluidStack.fromFluidStack(water);
            IAEFluidStack extracted = fluidInv.extractItems(aeWater, Actionable.SIMULATE, source);
            if (extracted == null || extracted.getStackSize() <= 0) return false;

            int actualAmount = (int) Math.min(extracted.getStackSize(), needed);
            water.amount = actualAmount;
            aeWater = AEFluidStack.fromFluidStack(water);

            IAEFluidStack actual = fluidInv.extractItems(aeWater, Actionable.MODULATE, source);
            if (actual == null || actual.getStackSize() <= 0) return false;

            tank.fill(new FluidStack(FluidRegistry.WATER, (int) actual.getStackSize()), true);
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Crucible auto-fill failed at {}", pos, e);
            return false;
        }
    }

    private static String ensureResearch(World world) {
        if (!(world instanceof WorldServer)) return "[AE2E]";

        FakePlayer fakePlayer = FakePlayerFactory.get((WorldServer) world, CRUCIBLE_PROFILE);
        IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(fakePlayer);
        if (knowledge != null && GRANTED_RESEARCH.isEmpty()) {
            for (IThaumcraftRecipe recipe : ThaumcraftApi.getCraftingRecipes().values()) {
                if (recipe instanceof CrucibleRecipe) {
                    String research = ((CrucibleRecipe) recipe).getResearch();
                    if (research != null && !research.isEmpty() && !knowledge.isResearchKnown(research)) {
                        knowledge.addResearch(research);
                        GRANTED_RESEARCH.add(research);
                    }
                }
            }
        }
        return fakePlayer.getName();
    }
}

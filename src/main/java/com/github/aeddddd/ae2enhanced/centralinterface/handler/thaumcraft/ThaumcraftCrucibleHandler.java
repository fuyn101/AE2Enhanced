package com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTank;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Thaumcraft 6 坩埚远程处理器。
 *
 * <p>支持中枢 ME 接口对坩埚的自动化：
 * <ul>
 *   <li>水由 {@code DualityCentralInterface.pushFluidInputs} 自动推入（玩家需在样板流体槽指定水）</li>
 *   <li>物品按 {@code InventoryCrafting} 槽位顺序逐个调用 {@code attemptSmelt} 投入</li>
 *   <li>产物通过扫描坩埚上方 {@code EntitySpecialItem} 收集</li>
 * </ul>
 *
 * <p><b>重要</b>：坩埚合成是瞬时的，且对投入顺序敏感。
 * 玩家制作样板时必须按正确顺序放置物品：先源质来源（被分解成 aspects），
 * 后催化剂（触发合成）。如果顺序颠倒，催化剂会在 aspects 不足时被分解，导致合成失败。</p>
 */
public class ThaumcraftCrucibleHandler implements IRemoteHandler {

    private static final String BLOCK_ID = "thaumcraft:crucible";

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

            // EntitySpecialItem 继承 EntityItem，getItem() 在开发环境为 getItem，
            // 运行时 obfuscated 为 func_92059_d（SRG 名）
            try {
                METHOD_GET_ITEM = CLASS_ENTITY_SPECIAL_ITEM.getMethod("getItem");
            } catch (NoSuchMethodException e) {
                METHOD_GET_ITEM = CLASS_ENTITY_SPECIAL_ITEM.getMethod("func_92059_d");
            }

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

        try {
            short heat = (short) FIELD_HEAT.get(te);
            if (heat <= 150) return false;

            FluidTank tank = (FluidTank) FIELD_TANK.get(te);
            if (tank.getFluidAmount() < 50) return false;

            boolean hasItems = false;
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                if (!ingredients.getStackInSlot(i).isEmpty()) {
                    hasItems = true;
                    break;
                }
            }
            return hasItems;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] ThaumcraftCrucibleHandler.canStart failed at {}", pos, e);
            return false;
        }
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_TILE_CRUCIBLE.isInstance(te)) return false;

        try {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    items.add(stack.copy());
                }
            }
            if (items.isEmpty()) return false;

            for (ItemStack stack : items) {
                ItemStack remaining = (ItemStack) METHOD_ATTEMPT_SMELT.invoke(te, stack, "AE2Enhanced");
                if (remaining == null) {
                    remaining = ItemStack.EMPTY;
                }
                // attemptSmelt 会逐个处理 stack 中的物品。
                // 如果 remaining 非空，说明热量/水量不足导致后续物品无法处理。
                // 已分解的 aspects 无法回退，因此继续尝试剩余物品（可能它们也不需要处理）。
            }
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] ThaumcraftCrucibleHandler.pushMaterials failed at {}", pos, e);
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        // 坩埚合成在 pushMaterials 的 attemptSmelt 中已经瞬时完成
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs) {
        // 瞬时完成，总是空闲
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                                           List<ItemStack> inputs, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();

        // ejectItem 在坩埚上方 (pos.y + 0.71) 生成 EntitySpecialItem，给一个向上的微小速度
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
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] ThaumcraftCrucibleHandler.collectProducts failed at {}", pos, e);
        }

        return result;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        // 已分解为 aspects 的物品无法回退，只能收集已经弹出的产物
        return collectProducts(world, pos, null, null, source);
    }
}

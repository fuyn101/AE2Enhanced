package com.github.aeddddd.ae2enhanced.centralinterface.handler.bewitchment;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bewitchment 处理器：SpinningWheel + Distillery + WitchesCauldron。
 *
 * <p><b>SpinningWheel / Distillery</b>：通过 {@link EnumFacing#UP} 的 IItemHandler 推入材料，
 * 通过 {@link EnumFacing#DOWN} 的 IItemHandler 收集产物。Bewitchment 的 getCapability 映射为：
 * <ul>
 *   <li>DOWN → inventory_down（输出槽，isItemValid 返回 false）</li>
 *   <li>UP / 其他面 → inventory_up（输入槽，isItemValid 返回 true）</li>
 * </ul>
 * 若从底部（DOWN）输入，物品会被插入输出槽且无法被配方识别，因此 handler 必须显式使用 UP 面。</p>
 *
 * <p><b>WitchesCauldron</b>：不暴露 IItemHandler capability，通过生成 EntityItem 让其
 * {@code insertNextItem} 自然收集。handler 不负责热源/流体准备，需玩家预先 setup。</p>
 */
public class BewitchmentHandler implements IRemoteHandler {

    private static final String SPINNING_WHEEL_ID = "bewitchment:spinning_wheel";
    private static final String DISTILLERY_ID = "bewitchment:distillery";
    private static final String CAULDRON_ID = "bewitchment:witches_cauldron";

    // 反射缓存 — SpinningWheel
    private static Class<?> CLASS_SPINNING_WHEEL;
    private static Field FIELD_SPINNING_PROGRESS;

    // 反射缓存 — Distillery
    private static Class<?> CLASS_DISTILLERY;
    private static Field FIELD_DISTILLERY_PROGRESS;

    // 反射缓存 — Cauldron
    private static Class<?> CLASS_CAULDRON;
    private static Field FIELD_CAULDRON_INVENTORY;
    private static Field FIELD_CAULDRON_MODE;
    private static Field FIELD_CAULDRON_CRAFTING_TIMER;
    private static Field FIELD_CAULDRON_HAS_POWER;
    private static Field FIELD_CAULDRON_HEAT_TIMER;
    private static Field FIELD_CAULDRON_TANK;

    // 坩埚超时：记录 push 时间（key = dim:pos）
    private static final Map<String, Long> CAULDRON_PUSH_TIME = new HashMap<>();
    private static final long CAULDRON_TIMEOUT_MS = 30000;

    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_SPINNING_WHEEL = Class.forName("com.bewitchment.common.block.tile.entity.TileEntitySpinningWheel");
            FIELD_SPINNING_PROGRESS = CLASS_SPINNING_WHEEL.getField("progress");

            CLASS_DISTILLERY = Class.forName("com.bewitchment.common.block.tile.entity.TileEntityDistillery");
            FIELD_DISTILLERY_PROGRESS = CLASS_DISTILLERY.getField("progress");

            CLASS_CAULDRON = Class.forName("com.bewitchment.common.block.tile.entity.TileEntityWitchesCauldron");
            FIELD_CAULDRON_INVENTORY = CLASS_CAULDRON.getDeclaredField("inventory");
            FIELD_CAULDRON_INVENTORY.setAccessible(true);
            FIELD_CAULDRON_MODE = CLASS_CAULDRON.getField("mode");
            FIELD_CAULDRON_CRAFTING_TIMER = CLASS_CAULDRON.getField("craftingTimer");
            FIELD_CAULDRON_HAS_POWER = CLASS_CAULDRON.getField("hasPower");
            FIELD_CAULDRON_HEAT_TIMER = CLASS_CAULDRON.getDeclaredField("heatTimer");
            FIELD_CAULDRON_HEAT_TIMER.setAccessible(true);
            FIELD_CAULDRON_TANK = CLASS_CAULDRON.getField("tank");

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] Bewitchment reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return SPINNING_WHEEL_ID.equals(blockId)
            || DISTILLERY_ID.equals(blockId)
            || CAULDRON_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_SPINNING_WHEEL.isInstance(te)
            || CLASS_DISTILLERY.isInstance(te)
            || CLASS_CAULDRON.isInstance(te);
    }

    // ===================== canStart =====================

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_SPINNING_WHEEL.isInstance(te)) {
            return canStartSpinningWheel(te, ingredients);
        }
        if (CLASS_DISTILLERY.isInstance(te)) {
            return canStartDistillery(te, ingredients);
        }
        if (CLASS_CAULDRON.isInstance(te)) {
            return canStartCauldron(te, ingredients);
        }
        return false;
    }

    private boolean canStartSpinningWheel(TileEntity te, InventoryCrafting ingredients) {
        IItemHandler up = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (up == null) return false;
        // 要求输入槽完全为空，避免残留物品干扰配方匹配
        for (int i = 0; i < up.getSlots(); i++) {
            if (!up.getStackInSlot(i).isEmpty()) return false;
        }
        int needed = countNonEmpty(ingredients);
        return up.getSlots() >= needed;
    }

    private boolean canStartDistillery(TileEntity te, InventoryCrafting ingredients) {
        IItemHandler up = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (up == null) return false;
        for (int i = 0; i < up.getSlots(); i++) {
            if (!up.getStackInSlot(i).isEmpty()) return false;
        }
        int needed = countNonEmpty(ingredients);
        return up.getSlots() >= needed;
    }

    private static int countNonEmpty(InventoryCrafting inv) {
        int count = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) count++;
        }
        return count;
    }

    // ===================== pushMaterials =====================

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_SPINNING_WHEEL.isInstance(te)) {
            return pushSpinningWheel(te, ingredients);
        }
        if (CLASS_DISTILLERY.isInstance(te)) {
            return pushDistillery(te, ingredients);
        }
        if (CLASS_CAULDRON.isInstance(te)) {
            return pushCauldron(world, pos, te, ingredients);
        }
        return false;
    }

    private boolean pushSpinningWheel(TileEntity te, InventoryCrafting ingredients) {
        IItemHandler up = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (up == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] Bewitchment SpinningWheel has no UP capability");
            return false;
        }
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack single = stack.copy();
            single.setCount(1);
            ItemStack remaining = ItemHandlerHelper.insertItem(up, single, false);
            if (!remaining.isEmpty()) {
                AE2Enhanced.LOGGER.warn("[AE2E] Bewitchment SpinningWheel failed to insert {}", stack);
                return false;
            }
        }
        AE2Enhanced.LOGGER.info("[AE2E] Pushed materials to SpinningWheel");
        return true;
    }

    private boolean pushDistillery(TileEntity te, InventoryCrafting ingredients) {
        IItemHandler up = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
        if (up == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] Bewitchment Distillery has no UP capability");
            return false;
        }
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack single = stack.copy();
            single.setCount(1);
            ItemStack remaining = ItemHandlerHelper.insertItem(up, single, false);
            if (!remaining.isEmpty()) {
                AE2Enhanced.LOGGER.warn("[AE2E] Bewitchment Distillery failed to insert {}", stack);
                return false;
            }
        }
        AE2Enhanced.LOGGER.info("[AE2E] Pushed materials to Distillery");
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        // SpinningWheel / Distillery 配方自动匹配，Cauldron 由原生逻辑处理
        return true;
    }

    // ===================== isIdle =====================

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_SPINNING_WHEEL.isInstance(te)) {
            return isIdleSpinningWheel(te);
        }
        if (CLASS_DISTILLERY.isInstance(te)) {
            return isIdleDistillery(te);
        }
        if (CLASS_CAULDRON.isInstance(te)) {
            return isIdleCauldron(world, pos, te);
        }
        return false;
    }

    private boolean isIdleSpinningWheel(TileEntity te) {
        try {
            int progress = (int) FIELD_SPINNING_PROGRESS.get(te);
            if (progress != 0) return false;
            IItemHandler down = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
            if (down != null) {
                for (int i = 0; i < down.getSlots(); i++) {
                    if (!down.getStackInSlot(i).isEmpty()) return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isIdleDistillery(TileEntity te) {
        try {
            int progress = (int) FIELD_DISTILLERY_PROGRESS.get(te);
            if (progress != 0) return false;
            IItemHandler down = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
            if (down != null) {
                for (int i = 0; i < down.getSlots(); i++) {
                    if (!down.getStackInSlot(i).isEmpty()) return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ===================== collectProducts =====================

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (CLASS_SPINNING_WHEEL.isInstance(te)) {
            collectSpinningWheel(te, result);
        } else if (CLASS_DISTILLERY.isInstance(te)) {
            collectDistillery(te, result);
        } else if (CLASS_CAULDRON.isInstance(te)) {
            collectCauldron(world, pos, te, result);
        }
        return result;
    }

    private void collectSpinningWheel(TileEntity te, List<ItemStack> result) {
        IItemHandler down = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
        if (down == null) return;
        for (int i = 0; i < down.getSlots(); i++) {
            ItemStack stack = down.extractItem(i, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
    }

    private void collectDistillery(TileEntity te, List<ItemStack> result) {
        IItemHandler down = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
        if (down == null) return;
        for (int i = 0; i < down.getSlots(); i++) {
            ItemStack stack = down.extractItem(i, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        }
    }

    // ===================== WitchesCauldron =====================

    private boolean canStartCauldron(TileEntity te, InventoryCrafting ingredients) {
        try {
            boolean hasPower = (boolean) FIELD_CAULDRON_HAS_POWER.get(te);
            if (!hasPower) return false;

            int heatTimer = (int) FIELD_CAULDRON_HEAT_TIMER.get(te);
            if (heatTimer < 5) return false;

            Object tank = FIELD_CAULDRON_TANK.get(te);
            if (tank == null) return false;
            int fluidAmount = (int) tank.getClass().getMethod("getFluidAmount").invoke(tank);
            if (fluidAmount <= 0) return false;

            ItemStackHandler inv = (ItemStackHandler) FIELD_CAULDRON_INVENTORY.get(te);
            if (inv == null) return false;
            // 要求 inventory 完全为空，避免残留干扰
            for (int i = 0; i < inv.getSlots(); i++) {
                if (!inv.getStackInSlot(i).isEmpty()) return false;
            }
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Bewitchment cauldron canStart failed", e);
            return false;
        }
    }

    private boolean pushCauldron(World world, BlockPos pos, TileEntity te, InventoryCrafting ingredients) {
        try {
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                ItemStack single = stack.copy();
                single.setCount(1);

                // collectionZone AABB 为 [pos, pos+(1,0.65,1)]，EntityItem 必须落在该范围内
                EntityItem entity = new EntityItem(
                    world,
                    pos.getX() + 0.5,
                    pos.getY() + 0.3,
                    pos.getZ() + 0.5,
                    single
                );
                entity.setPickupDelay(40);
                entity.motionX = 0;
                entity.motionY = 0;
                entity.motionZ = 0;
                // 防止重力导致 EntityItem 掉出 collectionZone
                entity.setNoGravity(true);
                world.spawnEntity(entity);
            }

            String key = world.provider.getDimension() + ":" + pos.toLong();
            CAULDRON_PUSH_TIME.put(key, System.currentTimeMillis());
            AE2Enhanced.LOGGER.info("[AE2E] Spawned {} cauldron ingredients as EntityItem", countNonEmpty(ingredients));
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Bewitchment cauldron push failed", e);
            return false;
        }
    }

    private boolean isIdleCauldron(World world, BlockPos pos, TileEntity te) {
        try {
            int mode = (int) FIELD_CAULDRON_MODE.get(te);
            int craftingTimer = (int) FIELD_CAULDRON_CRAFTING_TIMER.get(te);

            // mode 5 是合成阶段，timer > 0 表示合成进行中
            if (mode == 5 && craftingTimer > 0) return false;

            // mode 2 是排空流体，视为处理中
            if (mode == 2) return false;

            ItemStackHandler inv = (ItemStackHandler) FIELD_CAULDRON_INVENTORY.get(te);
            if (inv == null) return true;

            boolean inventoryEmpty = true;
            for (int i = 0; i < inv.getSlots(); i++) {
                if (!inv.getStackInSlot(i).isEmpty()) {
                    inventoryEmpty = false;
                    break;
                }
            }

            // 检查是否有未收集的 EntityItem（我们 push 的物品或产物）
            AxisAlignedBB aabb = new AxisAlignedBB(pos).grow(1.0);
            List<EntityItem> nearbyItems = world.getEntitiesWithinAABB(EntityItem.class, aabb);
            boolean hasNearbyItems = !nearbyItems.isEmpty();

            // 空闲：inventory 为空且周围无 EntityItem
            if (inventoryEmpty && !hasNearbyItems) return true;

            // 超时检查
            String key = world.provider.getDimension() + ":" + pos.toLong();
            Long pushTime = CAULDRON_PUSH_TIME.get(key);
            if (pushTime != null && System.currentTimeMillis() - pushTime > CAULDRON_TIMEOUT_MS) {
                CAULDRON_PUSH_TIME.remove(key);
                AE2Enhanced.LOGGER.warn("[AE2E] Cauldron at {} timed out, forcing idle", pos);
                return true;
            }

            // inventory 有物品或周围有 EntityItem：检查是否有能量和沸腾
            boolean hasPower = (boolean) FIELD_CAULDRON_HAS_POWER.get(te);
            if (!hasPower) return true;

            int heatTimer = (int) FIELD_CAULDRON_HEAT_TIMER.get(te);
            if (heatTimer < 5) return true;

            Object tank = FIELD_CAULDRON_TANK.get(te);
            if (tank != null) {
                int fluidAmount = (int) tank.getClass().getMethod("getFluidAmount").invoke(tank);
                if (fluidAmount <= 0) return true;
            }

            // mode 5 且 timer == 0：合成已完成，允许收集
            if (mode == 5 && craftingTimer == 0) return true;

            // 仍在处理中
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private void collectCauldron(World world, BlockPos pos, TileEntity te, List<ItemStack> result) {
        try {
            // 1. 扫描周围 EntityItem（坩埚产物或未收集的输入物品）
            AxisAlignedBB aabb = new AxisAlignedBB(pos).grow(1.0);
            List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, aabb);
            for (EntityItem entity : items) {
                if (entity.isDead) continue;
                ItemStack stack = entity.getItem();
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    entity.setDead();
                }
            }

            // 2. 回收 inventory 中的残余物品
            ItemStackHandler inv = (ItemStackHandler) FIELD_CAULDRON_INVENTORY.get(te);
            if (inv != null) {
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        result.add(stack.copy());
                        inv.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }

            // 3. 清理超时记录
            String key = world.provider.getDimension() + ":" + pos.toLong();
            CAULDRON_PUSH_TIME.remove(key);
        } catch (Exception ignored) {}
    }
}

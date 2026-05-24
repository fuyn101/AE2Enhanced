package com.github.aeddddd.ae2enhanced.centralinterface.handler.bewitchment;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
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
import net.minecraftforge.items.ItemStackHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bewitchment 处理器：SpinningWheel + WitchesCauldron。
 *
 * <p><b>SpinningWheel</b>：4槽输入(inventory_up) + 2槽输出(inventory_down)。
 * 通过非 DOWN face 推入材料，配方自动匹配，需要外部祭坛供能。</p>
 *
 * <p><b>WitchesCauldron</b>：127槽 inventory + 流体 tank。不暴露 IItemHandler capability，
 * 通过反射直接操作 inventory。产物以 EntityItem 掉落。<br>
 * <b>限制</b>：handler 不负责热源/流体准备，需玩家预先 setup。超时 30 秒未合成则回收物品。</p>
 */
public class BewitchmentHandler implements IRemoteHandler {

    private static final String SPINNING_WHEEL_ID = "bewitchment:spinning_wheel";
    private static final String DISTILLERY_ID = "bewitchment:distillery";
    private static final String CAULDRON_ID = "bewitchment:witches_cauldron";

    // 反射缓存 — SpinningWheel
    private static Class<?> CLASS_SPINNING_WHEEL;
    private static Field FIELD_SPINNING_PROGRESS;
    private static Field FIELD_SPINNING_INVENTORY_UP;
    private static Field FIELD_SPINNING_INVENTORY_DOWN;

    // 反射缓存 — Distillery
    private static Class<?> CLASS_DISTILLERY;
    private static Field FIELD_DISTILLERY_PROGRESS;
    private static Field FIELD_DISTILLERY_INVENTORY_UP;
    private static Field FIELD_DISTILLERY_INVENTORY_DOWN;

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
            FIELD_SPINNING_INVENTORY_UP = CLASS_SPINNING_WHEEL.getDeclaredField("inventory_up");
            FIELD_SPINNING_INVENTORY_UP.setAccessible(true);
            FIELD_SPINNING_INVENTORY_DOWN = CLASS_SPINNING_WHEEL.getDeclaredField("inventory_down");
            FIELD_SPINNING_INVENTORY_DOWN.setAccessible(true);

            CLASS_DISTILLERY = Class.forName("com.bewitchment.common.block.tile.entity.TileEntityDistillery");
            FIELD_DISTILLERY_PROGRESS = CLASS_DISTILLERY.getField("progress");
            FIELD_DISTILLERY_INVENTORY_UP = CLASS_DISTILLERY.getDeclaredField("inventory_up");
            FIELD_DISTILLERY_INVENTORY_UP.setAccessible(true);
            FIELD_DISTILLERY_INVENTORY_DOWN = CLASS_DISTILLERY.getDeclaredField("inventory_down");
            FIELD_DISTILLERY_INVENTORY_DOWN.setAccessible(true);

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

    // ===================== SpinningWheel =====================

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

    private boolean canStartDistillery(TileEntity te, InventoryCrafting ingredients) {
        try {
            ItemStackHandler up = (ItemStackHandler) FIELD_DISTILLERY_INVENTORY_UP.get(te);
            if (up == null) return false;
            int needed = 0;
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                if (!ingredients.getStackInSlot(i).isEmpty()) needed++;
            }
            int empty = 0;
            for (int i = 0; i < up.getSlots(); i++) {
                if (up.getStackInSlot(i).isEmpty()) empty++;
            }
            return empty >= needed;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canStartSpinningWheel(TileEntity te, InventoryCrafting ingredients) {
        try {
            ItemStackHandler up = (ItemStackHandler) FIELD_SPINNING_INVENTORY_UP.get(te);
            if (up == null) return false;
            int needed = 0;
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                if (!ingredients.getStackInSlot(i).isEmpty()) needed++;
            }
            int empty = 0;
            for (int i = 0; i < up.getSlots(); i++) {
                if (up.getStackInSlot(i).isEmpty()) empty++;
            }
            return empty >= needed;
        } catch (Exception e) {
            return false;
        }
    }

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

    private boolean pushDistillery(TileEntity te, InventoryCrafting ingredients) {
        try {
            ItemStackHandler up = (ItemStackHandler) FIELD_DISTILLERY_INVENTORY_UP.get(te);
            if (up == null) return false;
            int slotIdx = 0;
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                ItemStack single = stack.copy();
                single.setCount(1);
                while (slotIdx < up.getSlots() && !up.getStackInSlot(slotIdx).isEmpty()) {
                    slotIdx++;
                }
                if (slotIdx >= up.getSlots()) return false;
                up.setStackInSlot(slotIdx, single);
                slotIdx++;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean pushSpinningWheel(TileEntity te, InventoryCrafting ingredients) {
        try {
            ItemStackHandler up = (ItemStackHandler) FIELD_SPINNING_INVENTORY_UP.get(te);
            if (up == null) return false;
            int slotIdx = 0;
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                ItemStack single = stack.copy();
                single.setCount(1);
                while (slotIdx < up.getSlots() && !up.getStackInSlot(slotIdx).isEmpty()) {
                    slotIdx++;
                }
                if (slotIdx >= up.getSlots()) return false;
                up.setStackInSlot(slotIdx, single);
                slotIdx++;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        // SpinningWheel 配方自动匹配，Cauldron 需玩家手动启动或环境条件
        return true;
    }

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

    private boolean isIdleDistillery(TileEntity te) {
        try {
            int progress = (int) FIELD_DISTILLERY_PROGRESS.get(te);
            if (progress != 0) return false;
            ItemStackHandler down = (ItemStackHandler) FIELD_DISTILLERY_INVENTORY_DOWN.get(te);
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

    private boolean isIdleSpinningWheel(TileEntity te) {
        try {
            int progress = (int) FIELD_SPINNING_PROGRESS.get(te);
            if (progress != 0) return false;
            ItemStackHandler down = (ItemStackHandler) FIELD_SPINNING_INVENTORY_DOWN.get(te);
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

    private void collectDistillery(TileEntity te, List<ItemStack> result) {
        try {
            ItemStackHandler down = (ItemStackHandler) FIELD_DISTILLERY_INVENTORY_DOWN.get(te);
            if (down == null) return;
            for (int i = 0; i < down.getSlots(); i++) {
                ItemStack stack = down.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    down.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        } catch (Exception ignored) {}
    }

    private void collectSpinningWheel(TileEntity te, List<ItemStack> result) {
        try {
            ItemStackHandler down = (ItemStackHandler) FIELD_SPINNING_INVENTORY_DOWN.get(te);
            if (down == null) return;
            for (int i = 0; i < down.getSlots(); i++) {
                ItemStack stack = down.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    result.add(stack.copy());
                    down.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        } catch (Exception ignored) {}
    }

    // ===================== WitchesCauldron =====================

    private boolean canStartCauldron(TileEntity te, InventoryCrafting ingredients) {
        try {
            boolean hasPower = (boolean) FIELD_CAULDRON_HAS_POWER.get(te);
            if (!hasPower) return false; // 祭坛能量不足

            int heatTimer = (int) FIELD_CAULDRON_HEAT_TIMER.get(te);
            if (heatTimer < 5) return false; // 未沸腾

            Object tank = FIELD_CAULDRON_TANK.get(te);
            if (tank == null) return false;
            int fluidAmount = (int) tank.getClass().getMethod("getFluidAmount").invoke(tank);
            if (fluidAmount <= 0) return false; // 无流体

            ItemStackHandler inv = (ItemStackHandler) FIELD_CAULDRON_INVENTORY.get(te);
            if (inv == null) return false;
            // 统计空槽
            int empty = 0;
            for (int i = 0; i < inv.getSlots(); i++) {
                if (inv.getStackInSlot(i).isEmpty()) empty++;
            }
            int needed = 0;
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                if (!ingredients.getStackInSlot(i).isEmpty()) needed++;
            }
            return empty >= needed;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean pushCauldron(World world, BlockPos pos, TileEntity te, InventoryCrafting ingredients) {
        try {
            // 改为生成 EntityItem，让坩埚的 insertNextItem 自然收集
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
                entity.setPickupDelay(40); // 2秒延迟，防止玩家拾取
                entity.motionX = 0;
                entity.motionY = 0;
                entity.motionZ = 0;
                world.spawnEntity(entity);
            }

            String key = world.provider.getDimension() + ":" + pos.toLong();
            CAULDRON_PUSH_TIME.put(key, System.currentTimeMillis());
            return true;
        } catch (Exception e) {
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

            // 超时检查：如果 push 后超过 30 秒仍然未处理完毕，强制回收
            String key = world.provider.getDimension() + ":" + pos.toLong();
            Long pushTime = CAULDRON_PUSH_TIME.get(key);
            if (pushTime != null && System.currentTimeMillis() - pushTime > CAULDRON_TIMEOUT_MS) {
                CAULDRON_PUSH_TIME.remove(key);
                return true; // 超时，允许 collectProducts 回收
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

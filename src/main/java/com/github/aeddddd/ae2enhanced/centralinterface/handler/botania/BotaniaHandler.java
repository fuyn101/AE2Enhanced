package com.github.aeddddd.ae2enhanced.centralinterface.handler.botania;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.recipe.RecipeElvenTrade;
import vazkii.botania.api.recipe.RecipeManaInfusion;
import vazkii.botania.api.recipe.RecipePetals;
import vazkii.botania.api.recipe.RecipeRuneAltar;
import vazkii.botania.api.state.BotaniaStateProps;
import vazkii.botania.api.state.enums.AlfPortalState;
import vazkii.botania.common.block.ModBlocks;
import vazkii.botania.common.block.tile.TileAlfPortal;
import vazkii.botania.common.block.tile.TileAltar;
import vazkii.botania.common.block.tile.TileRuneAltar;
import vazkii.botania.common.block.tile.TileTerraPlate;
import vazkii.botania.common.block.tile.mana.TilePool;
import vazkii.botania.common.item.ModItems;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Botania 远程处理器。
 *
 * 支持设备：
 * <ul>
 *   <li>魔力池 (botania:pool) — 通过 collideEntityItem 注入物品进行魔力转换</li>
 *   <li>精灵门 (botania:alfportal) — 丢入 EntityItem 进行精灵交易</li>
 *   <li>泰拉凝聚板 (botania:terraplate) — 丢入 3 个 manaResource 合成泰拉钢锭</li>
 *   <li>符文祭坛 (botania:runealtar) — addItem 放入物品 + 活石催化 + 魔杖启动合成</li>
 *   <li>花药台 (botania:altar) — 放入材料后手动触发配方合成</li>
 * </ul>
 */
public class BotaniaHandler implements IRemoteHandler {

    private static final String TAG_PORTAL_FLAG = "_elvenPortal";
    /** 中枢推送的输入物品标记，用于 revertMaterials 区分未消耗输入与产物 */
    private static final String TAG_INPUT_FLAG = "_ae2eInput";
    /** 推料状态过期时间 */
    private static final int STATE_EXPIRY_TICKS = 1200;

    private static class PushState {
        long pushTick;
    }

    private final java.util.Map<String, PushState> pushStates = new java.util.HashMap<>();

    private static String key(World world, BlockPos pos) {
        return world.provider.getDimension() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    private void cleanupExpiredStates(long now) {
        pushStates.entrySet().removeIf(e -> now - e.getValue().pushTick > STATE_EXPIRY_TICKS);
    }

    @Override
    public boolean canHandle(String blockId) {
        return "botania:pool".equals(blockId)
                || "botania:alfheimportal".equals(blockId)
                || "botania:terraplate".equals(blockId)
                || "botania:runealtar".equals(blockId)
                || "botania:altar".equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TilePool
                || te instanceof TileAlfPortal
                || te instanceof TileTerraPlate
                || te instanceof TileRuneAltar
                || te instanceof TileAltar;
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TilePool) {
            return canStartPool(world, pos, (TilePool) te, ingredients);
        } else if (te instanceof TileAlfPortal) {
            return canStartAlfPortal(world, pos, (TileAlfPortal) te, ingredients);
        } else if (te instanceof TileTerraPlate) {
            return canStartTerraPlate(world, pos, (TileTerraPlate) te, ingredients);
        } else if (te instanceof TileRuneAltar) {
            return canStartRuneAltar(world, pos, (TileRuneAltar) te, ingredients);
        } else if (te instanceof TileAltar) {
            return canStartAltar(world, pos, (TileAltar) te, ingredients);
        }
        return false;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        cleanupExpiredStates(world.getTotalWorldTime());
        TileEntity te = world.getTileEntity(pos);
        boolean success = false;
        if (te instanceof TilePool) {
            success = pushMaterialsPool(world, pos, (TilePool) te, ingredients);
        } else if (te instanceof TileAlfPortal) {
            success = pushMaterialsAlfPortal(world, pos, (TileAlfPortal) te, ingredients);
        } else if (te instanceof TileTerraPlate) {
            success = pushMaterialsTerraPlate(world, pos, (TileTerraPlate) te, ingredients);
        } else if (te instanceof TileRuneAltar) {
            success = pushMaterialsRuneAltar(world, pos, (TileRuneAltar) te, ingredients);
        } else if (te instanceof TileAltar) {
            success = pushMaterialsAltar(world, pos, (TileAltar) te, ingredients);
        }
        if (success) {
            PushState state = new PushState();
            state.pushTick = world.getTotalWorldTime();
            pushStates.put(key(world, pos), state);
        }
        return success;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRuneAltar) {
            return startProcessRuneAltar(world, pos, (TileRuneAltar) te);
        } else if (te instanceof TileAltar) {
            return startProcessAltar(world, pos, (TileAltar) te);
        }
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        List<ItemStack> result;
        if (te instanceof TileRuneAltar) {
            result = collectProductsRuneAltar(world, pos, expectedOutputs);
        } else if (te instanceof TileAltar) {
            result = collectMatchingEntityItems(world, pos, expectedOutputs);
        } else {
            result = collectMatchingEntityItems(world, pos, expectedOutputs);
        }
        if (!result.isEmpty()) {
            pushStates.remove(key(world, pos));
        }
        return result;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        String k = key(world, pos);
        pushStates.remove(k);
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRuneAltar) {
            List<ItemStack> result = new ArrayList<>();
            IItemHandler handler = ((TileRuneAltar) te).getItemHandler();
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.extractItem(i, 64, false);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
            return result;
        } else if (te instanceof TileAltar) {
            List<ItemStack> result = new ArrayList<>();
            IItemHandler handler = ((TileAltar) te).getItemHandler();
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.extractItem(i, 64, false);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
            // 同时回收 AABB 内尚未被 collideEntityItem 消耗的输入 EntityItem
            for (EntityItem item : getEntityItemsInAABB(world, pos)) {
                if (!item.isDead && !item.getItem().isEmpty() && item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                    result.add(item.getItem().copy());
                    item.setDead();
                }
            }
            return result;
        }
        // 对于 Pool / AlfPortal / TerraPlate 等通过 EntityItem 推料的设备，
        // 只回收带 TAG_INPUT_FLAG 标记的未消耗输入，不回收产物
        List<ItemStack> fallback = new ArrayList<>();
        for (EntityItem item : getEntityItemsInAABB(world, pos)) {
            if (!item.isDead && !item.getItem().isEmpty() && item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                fallback.add(item.getItem().copy());
                item.setDead();
            }
        }
        return fallback;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs) {
        cleanupExpiredStates(world.getTotalWorldTime());
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TilePool) {
            return isIdlePool(world, pos);
        } else if (te instanceof TileAlfPortal) {
            return isIdleAlfPortal(world, pos);
        } else if (te instanceof TileTerraPlate) {
            return isIdleTerraPlate(world, pos);
        } else if (te instanceof TileRuneAltar) {
            return isIdleRuneAltar(world, pos, (TileRuneAltar) te);
        } else if (te instanceof TileAltar) {
            return isIdleAltar(world, pos, (TileAltar) te);
        }
        return true;
    }

    // ==================== Pool ====================

    private boolean canStartPool(World world, BlockPos pos, TilePool pool, InventoryCrafting ingredients) {
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            RecipeManaInfusion recipe = TilePool.getMatchingRecipe(stack, world.getBlockState(pos.down()));
            if (recipe == null) return false;
        }
        return true;
    }

    private boolean pushMaterialsPool(World world, BlockPos pos, TilePool pool, InventoryCrafting ingredients) {
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 每次只推送 1 个，collideEntityItem 每次消耗 1 个
            ItemStack remaining = stack.copy();
            while (!remaining.isEmpty()) {
                ItemStack single = remaining.splitStack(1);
                EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, single);
                // 标记为中枢输入，使 revertMaterials 能区分未消耗输入与产物
                entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);
                // pickupDelay 在 EntityItem 中为 private，需反射设置
                // 100 < pickupDelay < 130 才能被 collideEntityItem 接受
                try {
                    java.lang.reflect.Field f = EntityItem.class.getDeclaredField("pickupDelay");
                    f.setAccessible(true);
                    f.setInt(entityItem, 110);
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Failed to set EntityItem pickupDelay", e);
                }
                entityItem.motionX = 0;
                entityItem.motionY = 0;
                entityItem.motionZ = 0;

                boolean consumed = pool.collideEntityItem(entityItem);
                if (!consumed) {
                    // mana 不足或其他原因未消耗：保留 EntityItem 在地上，
                    // 由 revertMaterials 按 TAG_INPUT_FLAG 回收
                    return false;
                }
                // collideEntityItem 成功时如果 stack 被消耗完，entityItem.getItem() 为空
                if (!entityItem.getItem().isEmpty()) {
                    // 异常情况，未完全消耗：保留在地上待回收
                    return false;
                }
                entityItem.setDead();
            }
        }
        return true;
    }

    private boolean isIdlePool(World world, BlockPos pos) {
        // 只把不带 TAG_INPUT_FLAG 的 EntityItem 视为产物；
        // 带标记的是未消耗的输入，不应视为产物
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            if (!item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                return true;
            }
        }
        return false;
    }

    // ==================== Alf Portal ====================

    private boolean canStartAlfPortal(World world, BlockPos pos, TileAlfPortal portal, InventoryCrafting ingredients) {
        AlfPortalState state = world.getBlockState(pos).getValue(BotaniaStateProps.ALFPORTAL_STATE);
        if (state == AlfPortalState.OFF) return false;

        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!isValidElvenTradeInput(stack)) return false;
        }
        return true;
    }

    private boolean pushMaterialsAlfPortal(World world, BlockPos pos, TileAlfPortal portal, InventoryCrafting ingredients) {
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, stack.copy());
            entityItem.setNoPickupDelay();
            entityItem.motionX = 0;
            entityItem.motionY = 0;
            entityItem.motionZ = 0;
            entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);
            world.spawnEntity(entityItem);
        }
        return true;
    }

    private boolean isIdleAlfPortal(World world, BlockPos pos) {
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        boolean hasProducts = false;
        for (EntityItem item : items) {
            if (item.isDead) continue;
            // 未带 _elvenPortal 标记的物品是未处理的输入
            if (!item.getEntityData().getBoolean(TAG_PORTAL_FLAG)) {
                return false;
            }
            hasProducts = true;
        }
        // 只有 AABB 内确实存在产物时才返回 true
        // AABB 为空时返回 false，避免在输入被吞噬后、产物生成前过早触发
        return hasProducts;
    }

    // ==================== Terra Plate ====================

    private boolean canStartTerraPlate(World world, BlockPos pos, TileTerraPlate plate, InventoryCrafting ingredients) {
        // 检查平台结构
        if (BotaniaReflectionHelper.METHOD_HAS_VALID_PLATFORM != null) {
            try {
                Boolean valid = (Boolean) BotaniaReflectionHelper.METHOD_HAS_VALID_PLATFORM.invoke(plate);
                if (!valid) return false;
            } catch (Exception e) {
                return false;
            }
        }
        // 检查板上是否已有物品
        if (BotaniaReflectionHelper.METHOD_GET_ITEMS != null) {
            try {
                List<?> items = (List<?>) BotaniaReflectionHelper.METHOD_GET_ITEMS.invoke(plate);
                if (!items.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private boolean pushMaterialsTerraPlate(World world, BlockPos pos, TileTerraPlate plate, InventoryCrafting ingredients) {
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack.copy());
            entityItem.setNoPickupDelay();
            entityItem.motionX = 0;
            entityItem.motionY = 0;
            entityItem.motionZ = 0;
            entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);
            world.spawnEntity(entityItem);
        }
        return true;
    }

    private boolean isIdleTerraPlate(World world, BlockPos pos) {
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            ItemStack stack = item.getItem();
            if (stack.getItem() == ModItems.manaResource && stack.getMetadata() == 4) {
                return true; // 发现泰拉钢锭
            }
        }
        return false;
    }

    // ==================== Rune Altar ====================

    private boolean canStartRuneAltar(World world, BlockPos pos, TileRuneAltar altar, InventoryCrafting ingredients) {
        int cooldown = BotaniaReflectionHelper.getRuneAltarCooldown(altar);
        if (cooldown > 0) return false;
        if (!altar.isEmpty()) return false;
        return true;
    }

    private boolean pushMaterialsRuneAltar(World world, BlockPos pos, TileRuneAltar altar, InventoryCrafting ingredients) {
        ItemStack livingrock = ItemStack.EMPTY;

        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // 活石作为催化剂，不放入祭坛物品栏，而是丢在旁边
            if (stack.getItem() == Item.getItemFromBlock(ModBlocks.livingrock) && stack.getMetadata() == 0) {
                if (livingrock.isEmpty()) {
                    livingrock = stack.copy();
                } else {
                    livingrock.grow(stack.getCount());
                }
                continue;
            }

            // 其他物品逐个放入祭坛
            int count = stack.getCount();
            for (int c = 0; c < count; c++) {
                ItemStack single = stack.copy();
                single.setCount(1);
                boolean added = altar.addItem(null, single, null);
                if (!added) return false;
            }
        }

        // 丢活石在旁边
        if (!livingrock.isEmpty()) {
            EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, livingrock);
            entityItem.setNoPickupDelay();
            entityItem.motionX = 0;
            entityItem.motionY = 0;
            entityItem.motionZ = 0;
            world.spawnEntity(entityItem);
        }

        return true;
    }

    private boolean startProcessRuneAltar(World world, BlockPos pos, TileRuneAltar altar) {
        int cooldown = BotaniaReflectionHelper.getRuneAltarCooldown(altar);
        if (cooldown > 0) return true;

        int mana = BotaniaReflectionHelper.getRuneAltarMana(altar);
        int manaToGet = altar.manaToGet;

        if (manaToGet <= 0 || mana < manaToGet) {
            return true; // 魔力不足，等待充能
        }

        // 查找匹配的配方
        RecipeRuneAltar recipe = null;
        Object currentRecipe = BotaniaReflectionHelper.getRuneAltarCurrentRecipe(altar);
        if (currentRecipe instanceof RecipeRuneAltar) {
            recipe = (RecipeRuneAltar) currentRecipe;
        } else {
            for (RecipeRuneAltar r : BotaniaAPI.runeAltarRecipes) {
                if (r.matches(altar.getItemHandler())) {
                    recipe = r;
                    break;
                }
            }
        }

        if (recipe == null) return false;

        // 扫描活石
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        EntityItem livingrockItem = null;
        for (EntityItem item : items) {
            if (item.isDead) continue;
            ItemStack stack = item.getItem();
            if (stack.getItem() == Item.getItemFromBlock(ModBlocks.livingrock) && stack.getMetadata() == 0) {
                livingrockItem = item;
                break;
            }
        }

        if (livingrockItem == null) {
            return true; // 活石还没到，等待
        }

        // 执行合成核心逻辑（反射绕过 player 检查）
        try {
            int manaCost = recipe.getManaUsage();
            altar.recieveMana(-manaCost);

            ItemStack output = recipe.getOutput().copy();
            EntityItem outputItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, output);
            world.spawnEntity(outputItem);

            BotaniaReflectionHelper.setRuneAltarCurrentRecipe(altar, null);
            world.addBlockEvent(pos, ModBlocks.runeAltar, 1, 60);
            world.addBlockEvent(pos, ModBlocks.runeAltar, 2, 0);
            altar.saveLastRecipe();

            // 清空物品栏
            IItemHandlerModifiable handler = altar.getItemHandler();
            for (int i = 0; i < altar.getSizeInventory(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                // 符文回收：非创造模式时符文会掉落
                if (stack.getItem() == ModItems.rune) {
                    EntityItem runeItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, stack.copy());
                    world.spawnEntity(runeItem);
                }
                handler.setStackInSlot(i, ItemStack.EMPTY);
            }

            // 消耗活石
            livingrockItem.getItem().shrink(1);
            if (livingrockItem.getItem().isEmpty()) {
                livingrockItem.setDead();
            }

            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] RuneAltar crafting failed", e);
            return false;
        }
    }

    private boolean isIdleRuneAltar(World world, BlockPos pos, TileRuneAltar altar) {
        int cooldown = BotaniaReflectionHelper.getRuneAltarCooldown(altar);
        // cooldown > 0 表示合成刚完成，正在冷却
        if (cooldown > 0) return true;

        // 如果祭坛内有物品且 manaToGet > 0，说明正在充能，不 idle
        if (!altar.isEmpty() && altar.manaToGet > 0) {
            return false;
        }

        // 不通过扫描 AABB 判断 idle，防止无关物品导致误判
        // 产物是否存在由 collectProducts 在 tick 中按 expectedOutputs 匹配收集
        return false;
    }

    // ==================== Petal Apothecary (Altar) ====================

    private boolean canStartAltar(World world, BlockPos pos, TileAltar altar, InventoryCrafting ingredients) {
        // 不预置水/空状态；水、种子、花瓣等统一作为样板输入通过 EntityItem 推入，
        // 由 altar 自身的 collideEntityItem 处理。
        return findMatchingRecipe(ingredients) != null;
    }

    private boolean pushMaterialsAltar(World world, BlockPos pos, TileAltar altar, InventoryCrafting ingredients) {
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, stack.copy());
            entityItem.setNoPickupDelay();
            entityItem.motionX = 0;
            entityItem.motionY = 0;
            entityItem.motionZ = 0;
            entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);
            world.spawnEntity(entityItem);
        }
        return true;
    }

    private boolean startProcessAltar(World world, BlockPos pos, TileAltar altar) {
        // 花药台通过 update() 自动扫描 AABB 内 EntityItem 并调用 collideEntityItem。
        // collideEntityItem 会：
        //   - 消耗水瓶/水桶并设置 hasWater()
        //   - 把花瓣/种子放入 itemHandler
        // 随后 update() 匹配 RecipePetals，成功后自动生成产物 EntityItem。
        // 因此无需外部手动触发合成。
        return true;
    }

    private boolean isIdleAltar(World world, BlockPos pos, TileAltar altar) {
        // 只把不带 _ae2eInput 的 EntityItem 视为产物；
        // 带标记的是尚未被 collideEntityItem 消耗的输入。
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            if (!item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                return true;
            }
        }
        return false;
    }

    private RecipePetals findMatchingRecipe(InventoryCrafting ingredients) {
        // 提取非空物品到临时 IItemHandler
        ItemStackHandler temp = new ItemStackHandler(16);
        int slot = 0;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                temp.setStackInSlot(slot++, stack.copy());
            }
        }
        if (slot == 0) return null;
        return findMatchingRecipe(temp);
    }

    private RecipePetals findMatchingRecipe(net.minecraftforge.items.IItemHandler handler) {
        for (RecipePetals recipe : BotaniaAPI.petalRecipes) {
            if (recipe.matches(handler)) {
                return recipe;
            }
        }
        return null;
    }

    // ==================== Common Helpers ====================

    private List<EntityItem> getEntityItemsInAABB(World world, BlockPos pos) {
        return world.getEntitiesWithinAABB(EntityItem.class,
                new AxisAlignedBB(pos, pos.add(1, 2, 1)));
    }

    private List<ItemStack> collectMatchingEntityItems(World world, BlockPos pos, IAEItemStack[] expectedOutputs) {
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        List<ItemStack> collected = new ArrayList<>();
        if (expectedOutputs == null || expectedOutputs.length == 0) {
            return collected;
        }

        for (EntityItem entityItem : new ArrayList<>(items)) {
            if (entityItem.isDead) continue;
            // 跳过未消耗的输入，只收集产物
            if (entityItem.getEntityData().getBoolean(TAG_INPUT_FLAG)) continue;
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;

            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null) continue;
                ItemStack expectedStack = expected.createItemStack();
                if (ItemStack.areItemsEqual(stack, expectedStack)) {
                    int toCollect = Math.min(expectedStack.getCount(), stack.getCount());
                    if (toCollect >= stack.getCount()) {
                        collected.add(stack.copy());
                        entityItem.setDead();
                    } else {
                        ItemStack taken = stack.splitStack(toCollect);
                        collected.add(taken);
                    }
                    break;
                }
            }
        }
        return collected;
    }

    private List<ItemStack> collectProductsRuneAltar(World world, BlockPos pos, IAEItemStack[] expectedOutputs) {
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        List<ItemStack> collected = new ArrayList<>();

        for (EntityItem entityItem : new ArrayList<>(items)) {
            if (entityItem.isDead) continue;
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;

            boolean collectedThis = false;

            // 1. 收集预期产物
            if (expectedOutputs != null) {
                for (IAEItemStack expected : expectedOutputs) {
                    if (expected == null) continue;
                    ItemStack expectedStack = expected.createItemStack();
                    if (ItemStack.areItemsEqual(stack, expectedStack)) {
                        collected.add(stack.copy());
                        entityItem.setDead();
                        collectedThis = true;
                        break;
                    }
                }
            }

            // 2. 收集返还物品（整合包可能有非符文返还；活石是催化剂不应收集）
            if (!collectedThis && !(stack.getItem() == Item.getItemFromBlock(ModBlocks.livingrock) && stack.getMetadata() == 0)) {
                collected.add(stack.copy());
                entityItem.setDead();
            }
        }
        return collected;
    }

    private boolean isValidElvenTradeInput(ItemStack stack) {
        if (stack.getItem() == ModItems.lexicon) return true;
        for (RecipeElvenTrade recipe : BotaniaAPI.elvenTradeRecipes) {
            for (Object o : recipe.getInputs()) {
                if (o instanceof String) {
                    for (ItemStack target : OreDictionary.getOres((String) o)) {
                        if (OreDictionary.itemMatches(target, stack, false)) return true;
                    }
                } else if (o instanceof ItemStack) {
                    ItemStack target = (ItemStack) o;
                    if (stack.getItem() == target.getItem() && stack.getMetadata() == target.getMetadata()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

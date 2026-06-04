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
            result = collectProductsAltar(world, pos);
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
        AE2Enhanced.LOGGER.debug("[AE2E-Botania] revertMaterials at {} te={}", pos, te != null ? te.getClass().getSimpleName() : "null");
        if (te instanceof TileRuneAltar) {
            List<ItemStack> result = new ArrayList<>();
            IItemHandler handler = ((TileRuneAltar) te).getItemHandler();
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack stack = handler.extractItem(i, 64, false);
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] revertMaterials RuneAltar: {} items at {}", result.size(), pos);
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
            // 兜底：回收 AABB 内可能残留的输入 EntityItem（正常不应存在）
            for (EntityItem item : getEntityItemsInAABB(world, pos)) {
                if (!item.isDead && !item.getItem().isEmpty() && item.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                    result.add(item.getItem().copy());
                    item.setDead();
                }
            }
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] revertMaterials Altar: {} items at {}", result.size(), pos);
            return result;
        }
        // 对于 Pool / AlfPortal / TerraPlate 等通过 EntityItem 推料的设备，
        // 只回收带 TAG_INPUT_FLAG 标记的未消耗输入，不回收产物
        List<ItemStack> fallback = new ArrayList<>();
        for (EntityItem item : getEntityItemsInAABB(world, pos)) {
            boolean isInput = item.getEntityData().getBoolean(TAG_INPUT_FLAG);
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] revertMaterials scan at {}: {} inputFlag={} dead={}",
                    pos, item.getItem(), isInput, item.isDead);
            if (!item.isDead && !item.getItem().isEmpty() && isInput) {
                fallback.add(item.getItem().copy());
                item.setDead();
            }
        }
        AE2Enhanced.LOGGER.debug("[AE2E-Botania] revertMaterials Pool/Portal/Terra: {} items at {}", fallback.size(), pos);
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
        int totalManaNeeded = 0;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            RecipeManaInfusion recipe = TilePool.getMatchingRecipe(stack, world.getBlockState(pos.down()));
            if (recipe == null) {
                AE2Enhanced.LOGGER.debug("[AE2E-Botania] canStartPool failed: no recipe for {} at {}", stack, pos);
                return false;
            }
            totalManaNeeded += recipe.getManaToConsume() * stack.getCount();
        }
        boolean manaOk = pool.getCurrentMana() >= totalManaNeeded;
        if (!manaOk) {
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] canStartPool failed: need {} mana, pool has {} at {}",
                    totalManaNeeded, pool.getCurrentMana(), pos);
        } else {
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] canStartPool ok: need {} mana, pool has {} at {}",
                    totalManaNeeded, pool.getCurrentMana(), pos);
        }
        return manaOk;
    }

    private boolean pushMaterialsPool(World world, BlockPos pos, TilePool pool, InventoryCrafting ingredients) {
        AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool start at {}", pos);

        // 清理 AABB 中已有的中枢输入标记实体，防止实体堆叠干扰 collideEntityItem
        for (EntityItem existing : getEntityItemsInAABB(world, pos)) {
            if (!existing.isDead && existing.getEntityData().getBoolean(TAG_INPUT_FLAG)) {
                existing.setDead();
                AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool cleaned leftover input entity: {} at {}",
                        existing.getItem(), pos);
            }
        }

        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool slot {}: stack=[count={}, item={}, class={}, hash={}, empty={}]",
                    i, stack.getCount(), stack.getItem(), stack.getClass().getName(), System.identityHashCode(stack), stack.isEmpty());
            if (stack.isEmpty()) continue;

            // 每次只推送 1 个，collideEntityItem 每次消耗 1 个
            ItemStack remaining = stack.copy();
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool slot {}: remaining copy=[count={}, item={}, hash={}, empty={}]",
                    i, remaining.getCount(), remaining.getItem(), System.identityHashCode(remaining), remaining.isEmpty());
            while (!remaining.isEmpty()) {
                int countBefore = remaining.getCount();
                ItemStack single = remaining.splitStack(1);
                AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool slot {} split: beforeCount={}, afterCount={}, single=[count={}, item={}, hash={}, empty={}]",
                        i, countBefore, remaining.getCount(), single.getCount(), single.getItem(), System.identityHashCode(single), single.isEmpty());
                if (single.isEmpty()) {
                    AE2Enhanced.LOGGER.warn("[AE2E-Botania] pushMaterialsPool: splitStack returned empty! Aborting slot. remainingCount={}, remainingItem={}",
                            remaining.getCount(), remaining.getItem());
                    break;
                }
                EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, single);
                // 标记为中枢输入，使 revertMaterials 能区分未消耗输入与产物
                entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);
                // 注意：Botania TilePool.collideEntityItem 会拒绝 100 < pickupDelay < 130 的物品
                // （视为玩家刚投掷的物品）。EntityItem 默认 pickupDelay=10，不在拒绝区间内。
                entityItem.motionX = 0;
                entityItem.motionY = 0;
                entityItem.motionZ = 0;
                world.spawnEntity(entityItem);  // 必须加入世界，否则产物不会出现在世界中
                AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool spawned entityItem: item={} hash={}",
                        entityItem.getItem(), System.identityHashCode(entityItem));

                boolean consumed = pool.collideEntityItem(entityItem);
                if (!consumed) {
                    int actualPickupDelay = 10;
                    try {
                        actualPickupDelay = (Integer) net.minecraftforge.fml.relauncher.ReflectionHelper.getPrivateValue(
                                EntityItem.class, entityItem, "field_145804_b", "pickupDelay");
                    } catch (Exception ignored) {}
                    AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool failed: collideEntityItem refused {} (item={} dead={} pickupDelay={}) at {} (mana={})",
                            single, entityItem.getItem(), entityItem.isDead, actualPickupDelay, pos, pool.getCurrentMana());
                    // 清理已 spawn 的实体，避免空实体堆积
                    if (!entityItem.isDead) {
                        entityItem.setDead();
                    }
                    return false;
                }
                // collideEntityItem 成功后：
                // - 无产物配方：entityItem.getItem() 为空，entityItem 已被 Botania setDead
                // - 有产物配方：entityItem.getItem() 变为产物（Botania setItem），需移除 TAG_INPUT_FLAG
                //   让 isIdle / collectProducts 正常识别为产物
                if (!entityItem.getItem().isEmpty()) {
                    ItemStack product = entityItem.getItem();
                    AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool product spawned: {} -> {} at {}",
                            single, product, pos);
                    entityItem.getEntityData().removeTag(TAG_INPUT_FLAG);
                } else {
                    entityItem.setDead();
                }
            }
        }
        AE2Enhanced.LOGGER.debug("[AE2E-Botania] pushMaterialsPool success at {}", pos);
        return true;
    }

    private boolean isIdlePool(World world, BlockPos pos) {
        // 只把不带 TAG_INPUT_FLAG 的 EntityItem 视为产物；
        // 带标记的是未消耗的输入，不应视为产物
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        boolean hasProduct = false;
        for (EntityItem item : items) {
            if (item.isDead) continue;
            boolean isInput = item.getEntityData().getBoolean(TAG_INPUT_FLAG);
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] isIdlePool scan at {}: {} inputFlag={} dead={}",
                    pos, item.getItem(), isInput, item.isDead);
            if (!isInput) {
                hasProduct = true;
            }
        }
        if (hasProduct) {
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] isIdlePool -> true (product found) at {}", pos);
        } else {
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] isIdlePool -> false (no product) at {}", pos);
        }
        return hasProduct;
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

    /** Botania 内部用于识别种子的正则，与 TileAltar.SEED_PATTERN 一致 */
    private static final java.util.regex.Pattern SEED_PATTERN = java.util.regex.Pattern.compile(
            "(?:(?:(?:[A-Z-_.:]|^)seed)|(?:(?:[a-z-_.:]|^)Seed))(?:[sA-Z-_.:]|$)"
    );

    private boolean isSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // 与 TileAltar.collideEntityItem 一致，使用 ItemStack.getTranslationKey() (func_77977_a)
        String unlocalizedName = stack.getTranslationKey();
        return SEED_PATTERN.matcher(unlocalizedName).find();
    }

    private boolean canStartAltar(World world, BlockPos pos, TileAltar altar, InventoryCrafting ingredients) {
        // 类似符文祭坛的严格前置检查
        if (!altar.isEmpty()) return false;
        if (altar.hasLava()) return false;
        return findMatchingRecipe(ingredients) != null;
    }

    private boolean pushMaterialsAltar(World world, BlockPos pos, TileAltar altar, InventoryCrafting ingredients) {
        // 自动填水：花药台合成必须有水，handler 自动保证
        if (!altar.hasWater()) {
            altar.setWater(true);
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }

        // 收集并分类材料：种子 vs 非种子
        List<ItemStack> nonSeeds = new ArrayList<>();
        ItemStack seedStack = ItemStack.EMPTY;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (isSeed(stack)) {
                if (seedStack.isEmpty()) {
                    seedStack = stack.copy();
                } else {
                    seedStack.grow(stack.getCount());
                }
            } else {
                nonSeeds.add(stack.copy());
            }
        }

        // 先推非种子材料（花瓣等），放入 itemHandler
        for (ItemStack stack : nonSeeds) {
            if (!pushItemToAltar(world, pos, altar, stack)) {
                return false;
            }
        }

        // 最后推种子，触发 collideEntityItem 中的配方匹配与合成
        // Botania 只需要 1 个种子作为触发剂
        if (!seedStack.isEmpty()) {
            ItemStack singleSeed = seedStack.copy();
            singleSeed.setCount(1);
            if (!pushItemToAltar(world, pos, altar, singleSeed)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 将物品作为 EntityItem 直接送入花药台的 collideEntityItem。
     * 这样可精确控制处理顺序（非种子先、种子后），避免 AABB 扫描的不确定性。
     */
    private boolean pushItemToAltar(World world, BlockPos pos, TileAltar altar, ItemStack stack) {
        while (!stack.isEmpty()) {
            ItemStack single = stack.splitStack(1);
            EntityItem entityItem = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, single);
            entityItem.setNoPickupDelay();
            entityItem.motionX = 0;
            entityItem.motionY = 0;
            entityItem.motionZ = 0;
            entityItem.getEntityData().setBoolean(TAG_INPUT_FLAG, true);

            boolean consumed = altar.collideEntityItem(entityItem);
            if (!consumed) {
                entityItem.setDead();
                return false;
            }
            // collideEntityItem 成功：非种子已放入 itemHandler，或种子已触发合成
            // 若 stack 仍有残留（理论上不应发生），也视为异常
            if (!entityItem.getItem().isEmpty()) {
                entityItem.setDead();
                return false;
            }
            entityItem.setDead();
        }
        return true;
    }

    private boolean startProcessAltar(World world, BlockPos pos, TileAltar altar) {
        // 花药台通过 collideEntityItem 自动触发合成，无需外部手动激活。
        // pushMaterialsAltar 中已按顺序调用 collideEntityItem，合成已即时完成。
        return true;
    }

    private boolean isIdleAltar(World world, BlockPos pos, TileAltar altar) {
        // pushMaterialsAltar 已直接调用 collideEntityItem 并将输入 EntityItem setDead，
        // 因此 AABB 中只会留下 Botania 生成的产物 EntityItem（标记为 ApothecarySpawned）。
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            // 跳过尚未处理的输入（兜底，理论上不应存在）
            if (item.getEntityData().getBoolean(TAG_INPUT_FLAG)) continue;
            return true;
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

    /**
     * 收集花药台 AABB 内的所有产物 EntityItem。
     * pushMaterialsAltar 已直接调用 collideEntityItem 并清理输入 EntityItem，
     * 因此 AABB 中遗留的 EntityItem 均可视为产物。
     */
    private List<ItemStack> collectProductsAltar(World world, BlockPos pos) {
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        List<ItemStack> collected = new ArrayList<>();
        for (EntityItem entityItem : new ArrayList<>(items)) {
            if (entityItem.isDead) continue;
            if (entityItem.getEntityData().getBoolean(TAG_INPUT_FLAG)) continue;
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;
            collected.add(stack.copy());
            entityItem.setDead();
        }
        return collected;
    }

    private List<ItemStack> collectMatchingEntityItems(World world, BlockPos pos, IAEItemStack[] expectedOutputs) {
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        List<ItemStack> collected = new ArrayList<>();
        if (expectedOutputs == null || expectedOutputs.length == 0) {
            AE2Enhanced.LOGGER.debug("[AE2E-Botania] collectMatchingEntityItems: no expectedOutputs at {}", pos);
            return collected;
        }

        for (EntityItem entityItem : new ArrayList<>(items)) {
            if (entityItem.isDead) continue;
            // 跳过未消耗的输入，只收集产物
            if (entityItem.getEntityData().getBoolean(TAG_INPUT_FLAG)) continue;
            ItemStack stack = entityItem.getItem();
            if (stack.isEmpty()) continue;

            boolean matched = false;
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
                    matched = true;
                    AE2Enhanced.LOGGER.debug("[AE2E-Botania] collectMatchingEntityItems matched: {} -> collect {} at {}",
                            stack, toCollect, pos);
                    break;
                }
            }
            if (!matched) {
                AE2Enhanced.LOGGER.debug("[AE2E-Botania] collectMatchingEntityItems unmatched: {} at {}", stack, pos);
            }
        }
        AE2Enhanced.LOGGER.debug("[AE2E-Botania] collectMatchingEntityItems result: {} items at {}", collected.size(), pos);
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

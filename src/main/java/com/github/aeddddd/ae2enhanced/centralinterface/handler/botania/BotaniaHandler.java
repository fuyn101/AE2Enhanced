package com.github.aeddddd.ae2enhanced.centralinterface.handler.botania;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.oredict.OreDictionary;
import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.recipe.RecipeElvenTrade;
import vazkii.botania.api.recipe.RecipeManaInfusion;
import vazkii.botania.api.recipe.RecipeRuneAltar;
import vazkii.botania.api.state.BotaniaStateProps;
import vazkii.botania.api.state.enums.AlfPortalState;
import vazkii.botania.common.block.ModBlocks;
import vazkii.botania.common.block.tile.TileAlfPortal;
import vazkii.botania.common.block.tile.TileRuneAltar;
import vazkii.botania.common.block.tile.TileTerraPlate;
import vazkii.botania.common.block.tile.mana.TilePool;
import vazkii.botania.common.item.ModItems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Botania 远程处理器。
 *
 * 支持设备：
 * <ul>
 *   <li>魔力池 (botania:pool) — 通过 collideEntityItem 注入物品进行魔力转换</li>
 *   <li>精灵门 (botania:alfportal) — 丢入 EntityItem 进行精灵交易</li>
 *   <li>泰拉凝聚板 (botania:terraplate) — 丢入 3 个 manaResource 合成泰拉钢锭</li>
 *   <li>符文祭坛 (botania:runealtar) — addItem 放入物品 + 活石催化 + 魔杖启动合成</li>
 * </ul>
 */
public class BotaniaHandler implements IRemoteHandler {

    private static final String TAG_PORTAL_FLAG = "_elvenPortal";

    @Override
    public boolean canHandle(String blockId) {
        return "botania:pool".equals(blockId)
                || "botania:alfportal".equals(blockId)
                || "botania:terraplate".equals(blockId)
                || "botania:runealtar".equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof TilePool
                || te instanceof TileAlfPortal
                || te instanceof TileTerraPlate
                || te instanceof TileRuneAltar;
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
        }
        return false;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TilePool) {
            return pushMaterialsPool(world, pos, (TilePool) te, ingredients);
        } else if (te instanceof TileAlfPortal) {
            return pushMaterialsAlfPortal(world, pos, (TileAlfPortal) te, ingredients);
        } else if (te instanceof TileTerraPlate) {
            return pushMaterialsTerraPlate(world, pos, (TileTerraPlate) te, ingredients);
        } else if (te instanceof TileRuneAltar) {
            return pushMaterialsRuneAltar(world, pos, (TileRuneAltar) te, ingredients);
        }
        return false;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileRuneAltar) {
            return startProcessRuneAltar(world, pos, (TileRuneAltar) te);
        }
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        return collectMatchingEntityItems(world, pos, expectedOutputs);
    }

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TilePool) {
            return isIdlePool(world, pos);
        } else if (te instanceof TileAlfPortal) {
            return isIdleAlfPortal(world, pos);
        } else if (te instanceof TileTerraPlate) {
            return isIdleTerraPlate(world, pos);
        } else if (te instanceof TileRuneAltar) {
            return isIdleRuneAltar(world, pos, (TileRuneAltar) te);
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
            if (pool.getCurrentMana() < recipe.getManaToConsume()) return false;
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
                    entityItem.setDead();
                    return false;
                }
                // collideEntityItem 成功时如果 stack 被消耗完，entityItem.getItem() 为空
                if (!entityItem.getItem().isEmpty()) {
                    // 异常情况，未完全消耗
                    entityItem.setDead();
                    return false;
                }
                entityItem.setDead();
            }
        }
        return true;
    }

    private boolean isIdlePool(World world, BlockPos pos) {
        // 输入已被 collideEntityItem 消耗，AABB 内任何 EntityItem 都是产物
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        return !items.isEmpty();
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
            world.spawnEntity(entityItem);
        }
        return true;
    }

    private boolean isIdleAlfPortal(World world, BlockPos pos) {
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        for (EntityItem item : items) {
            if (item.isDead) continue;
            // 未带 _elvenPortal 标记的物品是未处理的输入
            if (!item.getEntityData().getBoolean(TAG_PORTAL_FLAG)) {
                return false;
            }
        }
        return true; // 全是产物或为空
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
        if (cooldown > 0) return true;

        // 如果祭坛内有物品且 manaToGet > 0，说明正在充能，不 idle
        if (!altar.isEmpty() && altar.manaToGet > 0) {
            return false;
        }

        // 扫描产物
        List<EntityItem> items = getEntityItemsInAABB(world, pos);
        return !items.isEmpty();
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

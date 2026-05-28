package com.github.aeddddd.ae2enhanced.centralinterface.handler.bloodmagic;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import WayofTime.bloodmagic.tile.TileAlchemyTable;
import WayofTime.bloodmagic.tile.TileAltar;
import WayofTime.bloodmagic.tile.TileSoulForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Blood Magic 远程处理器。
 *
 * 支持设备：
 * <ul>
 *   <li>炼金术桌 (bloodmagic:alchemy_table) — 6 输入槽均分，每槽 1 个，收集输出槽产物 + 输入槽残余</li>
 *   <li>狱火锻炉 (bloodmagic:soul_forge) — 4 输入槽均分，每槽 1 个，收集输出槽产物 + 输入槽残余</li>
 *   <li>祭坛 (bloodmagic:altar) — 单槽 push + startCycle 启动，回收槽位 0 产物</li>
 * </ul>
 */
public class BloodMagicHandler implements IRemoteHandler {

    @Override
    public boolean canHandle(String blockId) {
        return "bloodmagic:alchemy_table".equals(blockId)
                || "bloodmagic:soul_forge".equals(blockId)
                || "bloodmagic:altar".equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return !((TileAlchemyTable) te).isSlave();
        }
        return te instanceof TileSoulForge || te instanceof TileAltar;
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return canStartAlchemyTable((TileAlchemyTable) te, ingredients);
        } else if (te instanceof TileSoulForge) {
            return canStartSoulForge((TileSoulForge) te, ingredients);
        } else if (te instanceof TileAltar) {
            return canStartAltar((TileAltar) te, ingredients);
        }
        return false;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return pushMaterialsAlchemyTable((TileAlchemyTable) te, ingredients);
        } else if (te instanceof TileSoulForge) {
            return pushMaterialsSoulForge((TileSoulForge) te, ingredients);
        } else if (te instanceof TileAltar) {
            return pushMaterialsAltar((TileAltar) te, ingredients);
        }
        return false;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAltar) {
            ((TileAltar) te).startCycle();
            return true;
        }
        // 炼金术桌和狱火锻炉 tick 自动处理，无需显式启动
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return collectProductsAlchemyTable((TileAlchemyTable) te);
        } else if (te instanceof TileSoulForge) {
            return collectProductsSoulForge((TileSoulForge) te);
        } else if (te instanceof TileAltar) {
            return collectProductsAltar((TileAltar) te);
        }
        return new ArrayList<>();
    }

    @Override
    public boolean isIdle(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return ((TileAlchemyTable) te).getBurnTime() == 0;
        } else if (te instanceof TileSoulForge) {
            return ((TileSoulForge) te).burnTime == 0;
        } else if (te instanceof TileAltar) {
            TileAltar altar = (TileAltar) te;
            return !altar.isActive() && altar.getProgress() == 0;
        }
        return true;
    }

    // ==================== Helpers ====================

    /**
     * 从 InventoryCrafting 中收集所有非空物品，每个只保留 1 个（均分策略）。
     */
    private List<ItemStack> collectSingles(InventoryCrafting ingredients) {
        List<ItemStack> materials = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStack single = stack.copy();
                single.setCount(1);
                materials.add(single);
            }
        }
        return materials;
    }

    // ==================== Alchemy Table ====================

    private boolean canStartAlchemyTable(TileAlchemyTable table, InventoryCrafting ingredients) {
        if (table.isSlave()) return false;

        List<ItemStack> materials = collectSingles(ingredients);
        if (materials.isEmpty()) return false;
        if (materials.size() > 6) return false;

        IItemHandler inputHandler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
        if (inputHandler == null) return false;

        // 检查是否有足够空输入槽
        int emptySlots = 0;
        for (int i = 0; i < inputHandler.getSlots(); i++) {
            if (table.isInputSlotAccessible(i) && inputHandler.getStackInSlot(i).isEmpty()) {
                emptySlots++;
            }
        }
        if (emptySlots < materials.size()) return false;

        // 检查输出槽
        IItemHandler outputHandler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
        if (outputHandler == null) return false;
        return outputHandler.getStackInSlot(0).isEmpty();
    }

    private boolean pushMaterialsAlchemyTable(TileAlchemyTable table, InventoryCrafting ingredients) {
        List<ItemStack> materials = collectSingles(ingredients);
        IItemHandler handler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
        if (handler == null) return false;

        int slot = 0;
        for (ItemStack material : materials) {
            boolean placed = false;
            while (slot < handler.getSlots()) {
                if (table.isInputSlotAccessible(slot) && handler.getStackInSlot(slot).isEmpty()) {
                    ItemStack remainder = handler.insertItem(slot, material, false);
                    if (remainder.isEmpty()) {
                        placed = true;
                        slot++;
                        break;
                    }
                }
                slot++;
            }
            if (!placed) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> collectProductsAlchemyTable(TileAlchemyTable table) {
        List<ItemStack> collected = new ArrayList<>();

        // 1. 收集输出槽产物（DOWN 面，槽位 0 对应绝对槽位 8）
        IItemHandler outputHandler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
        if (outputHandler != null) {
            ItemStack output = outputHandler.extractItem(0, 64, false);
            if (!output.isEmpty()) {
                collected.add(output);
            }
        }

        // 2. 收集输入槽残余（NORTH 面，槽位 0-5 对应绝对槽位 0-5）
        IItemHandler inputHandler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
        if (inputHandler != null) {
            for (int i = 0; i < inputHandler.getSlots(); i++) {
                ItemStack stack = inputHandler.extractItem(i, 64, false);
                if (!stack.isEmpty()) {
                    collected.add(stack);
                }
            }
        }

        return collected;
    }

    // ==================== Soul Forge ====================

    private boolean canStartSoulForge(TileSoulForge forge, InventoryCrafting ingredients) {
        List<ItemStack> materials = collectSingles(ingredients);
        if (materials.isEmpty()) return false;
        if (materials.size() > 4) return false;

        IItemHandler handler = forge.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return false;

        int emptySlots = 0;
        for (int i = 0; i < 4; i++) {
            if (handler.getStackInSlot(i).isEmpty()) {
                emptySlots++;
            }
        }
        if (emptySlots < materials.size()) return false;

        return handler.getStackInSlot(5).isEmpty();
    }

    private boolean pushMaterialsSoulForge(TileSoulForge forge, InventoryCrafting ingredients) {
        List<ItemStack> materials = collectSingles(ingredients);
        IItemHandler handler = forge.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return false;

        int slot = 0;
        for (ItemStack material : materials) {
            boolean placed = false;
            while (slot < 4) {
                if (handler.getStackInSlot(slot).isEmpty()) {
                    ItemStack remainder = handler.insertItem(slot, material, false);
                    if (remainder.isEmpty()) {
                        placed = true;
                        slot++;
                        break;
                    }
                }
                slot++;
            }
            if (!placed) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> collectProductsSoulForge(TileSoulForge forge) {
        List<ItemStack> collected = new ArrayList<>();
        IItemHandler handler = forge.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return collected;

        ItemStack output = handler.extractItem(5, 64, false);
        if (!output.isEmpty()) {
            collected.add(output);
        }

        for (int i = 0; i < 4; i++) {
            ItemStack stack = handler.extractItem(i, 64, false);
            if (!stack.isEmpty()) {
                collected.add(stack);
            }
        }

        return collected;
    }

    // ==================== Altar ====================

    private boolean canStartAltar(TileAltar altar, InventoryCrafting ingredients) {
        List<ItemStack> materials = collectSingles(ingredients);
        if (materials.size() != 1) return false;

        IItemHandler handler = altar.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return false;
        return handler.getStackInSlot(0).isEmpty();
    }

    private boolean pushMaterialsAltar(TileAltar altar, InventoryCrafting ingredients) {
        List<ItemStack> materials = collectSingles(ingredients);
        if (materials.isEmpty()) return false;

        IItemHandler handler = altar.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return false;

        ItemStack remainder = handler.insertItem(0, materials.get(0), false);
        return remainder.isEmpty();
    }

    private List<ItemStack> collectProductsAltar(TileAltar altar) {
        List<ItemStack> collected = new ArrayList<>();
        IItemHandler handler = altar.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return collected;

        ItemStack stack = handler.extractItem(0, 64, false);
        if (!stack.isEmpty()) {
            collected.add(stack);
        }
        return collected;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return revertMaterialsAlchemyTable((TileAlchemyTable) te);
        } else if (te instanceof TileSoulForge) {
            return revertMaterialsSoulForge((TileSoulForge) te);
        } else if (te instanceof TileAltar) {
            return revertMaterialsAltar((TileAltar) te);
        }
        return java.util.Collections.emptyList();
    }

    private List<ItemStack> revertMaterialsAlchemyTable(TileAlchemyTable table) {
        List<ItemStack> reverted = new ArrayList<>();
        IItemHandler handler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.extractItem(i, 64, false);
                if (!stack.isEmpty()) reverted.add(stack);
            }
        }
        return reverted;
    }

    private List<ItemStack> revertMaterialsSoulForge(TileSoulForge forge) {
        List<ItemStack> reverted = new ArrayList<>();
        IItemHandler handler = forge.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.extractItem(i, 64, false);
                if (!stack.isEmpty()) reverted.add(stack);
            }
        }
        return reverted;
    }

    private List<ItemStack> revertMaterialsAltar(TileAltar altar) {
        List<ItemStack> reverted = new ArrayList<>();
        IItemHandler handler = altar.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler != null) {
            ItemStack stack = handler.extractItem(0, 64, false);
            if (!stack.isEmpty()) reverted.add(stack);
        }
        return reverted;
    }
}

package com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extended Crafting 工作台虚拟批量合成处理器.
 *
 * <p>设计定位：<strong>只处理处理样板</strong>。对于绑定到 Extended Crafting 工作台的处理样板，
 * 直接按样板编码的输入/输出进行虚拟批量合成，不再查询 EC 工作台配方。</p>
 *
 * <p>原因：
 * <ul>
 *   <li>处理样板的输入/输出由玩家明确指定，本身就是“配方”；</li>
 *   <li>EC 工作台配方的槽位/顺序/等级约束只适用于真实的合成台场景，虚拟合成不应受其限制；</li>
 *   <li>AE2 CPU 对 craftable 样板只会构造 3×3 InventoryCrafting，导致超过 9 个输入的处理样板
 *       被错误截断。我们在 Mixin 层把截断的物品捕获并重组为 10×10 table，handler 层只需要
 *       信任 table 里的全部物品即可。</li>
 * </ul></p>
 */
public class ExtendedCraftingTableHandler implements IVirtualBatchCraftingHandler {

    private static final String[] TABLE_IDS = {
            "extendedcrafting:table_basic",
            "extendedcrafting:table_advanced",
            "extendedcrafting:table_elite",
            "extendedcrafting:table_ultimate"
    };

    private static Class<?> CLASS_ABSTRACT_TABLE;
    private static java.lang.reflect.Method METHOD_GET_LINE_SIZE;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_ABSTRACT_TABLE = Class.forName("com.blakebr0.extendedcrafting.tile.AbstractExtendedTable");
            METHOD_GET_LINE_SIZE = CLASS_ABSTRACT_TABLE.getMethod("getLineSize");
            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ExtendedCraftingTable reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        for (String id : TABLE_IDS) {
            if (id.equals(blockId)) return true;
        }
        return false;
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_ABSTRACT_TABLE.isInstance(te);
    }

    @Override
    public EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.virtualOnly();
    }

    @Override
    public long getDefaultParallel() {
        return 8;
    }

    // ---- IRemoteHandler 物理模式（纯虚拟设备，空实现） ----

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        return true;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        return Collections.emptyList();
    }

    // ---- IVirtualBatchCraftingHandler ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_ABSTRACT_TABLE.isInstance(te)) return false;

        if (outputs == null || outputs.length == 0) return false;
        boolean hasOutput = false;
        for (IAEItemStack output : outputs) {
            if (output != null && output.getStackSize() > 0) {
                hasOutput = true;
                break;
            }
        }
        if (!hasOutput) return false;

        boolean hasInput = false;
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) {
                hasInput = true;
                break;
            }
        }
        return hasInput;
    }

    @Override
    public List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return java.util.Arrays.asList(
                EnumParticleTypes.PORTAL,
                EnumParticleTypes.ENCHANTMENT_TABLE,
                EnumParticleTypes.SPELL_WITCH,
                EnumParticleTypes.END_ROD
        );
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        Map<ItemCostKey, Long> merged = new HashMap<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemCostKey key = new ItemCostKey(stack);
                merged.merge(key, (long) stack.getCount(), Long::sum);
            }
        }
        List<IAEStack> costs = new ArrayList<>();
        for (Map.Entry<ItemCostKey, Long> entry : merged.entrySet()) {
            IAEItemStack cost = AEItemStack.fromItemStack(entry.getKey().stack.copy());
            if (cost != null) {
                cost.setStackSize(entry.getValue() * count);
                costs.add(cost);
            }
        }
        return costs;
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count, IActionSource source) {
        if (!canCraftVirtually(world, pos, ingredients, outputs)) {
            return Collections.emptyList();
        }
        return scaleOutputsByCount(outputs, count);
    }

    // ---- 辅助 ----

    private static final class ItemCostKey {
        private final ItemStack stack;

        ItemCostKey(ItemStack stack) {
            this.stack = stack.copy();
            this.stack.setCount(1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemCostKey)) return false;
            ItemStack other = ((ItemCostKey) o).stack;
            return ItemStack.areItemsEqual(this.stack, other)
                    && ItemStack.areItemStackTagsEqual(this.stack, other);
        }

        @Override
        public int hashCode() {
            ResourceLocation regName = this.stack.getItem().getRegistryName();
            int result = regName != null ? regName.hashCode() : System.identityHashCode(this.stack.getItem());
            result = 31 * result + this.stack.getMetadata();
            if (this.stack.hasTagCompound()) {
                result = 31 * result + this.stack.getTagCompound().hashCode();
            }
            return result;
        }
    }
}

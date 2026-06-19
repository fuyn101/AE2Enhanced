package com.github.aeddddd.ae2enhanced.central;

import ae2.api.crafting.IPatternDetails;
import ae2.api.networking.security.IActionSource;
import ae2.api.stacks.AEItemKey;
import ae2.api.stacks.GenericStack;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 通用远程目标 fallback 处理器.
 *
 * <p>对任意目标机器使用 {@link IItemHandler} 推送输入物品,并在处理完成后
 * 收集输出.仅支持物品类型的输入/输出;流体/气体等需要 mod 专用 handler.</p>
 */
public class DefaultSingleBatchHandler implements IRemoteHandler {

    @Override
    public boolean canHandle(@Nonnull String blockId) {
        return true; // fallback
    }

    @Override
    public boolean isValidTarget(@Nonnull World world, @Nonnull BlockPos pos) {
        return world.getTileEntity(pos) != null;
    }

    @Override
    public boolean canStart(@Nonnull World world, @Nonnull BlockPos pos,
                            @Nonnull IPatternDetails pattern, @Nonnull List<GenericStack> inputs) {
        return true;
    }

    @Override
    public boolean pushMaterials(@Nonnull World world, @Nonnull BlockPos pos,
                                 @Nonnull IPatternDetails pattern, @Nonnull List<GenericStack> inputs,
                                 @Nonnull IActionSource source) {
        IItemHandler handler = getItemHandler(world, pos);
        if (handler == null) {
            return false;
        }

        List<ItemStack> inserted = new ArrayList<>();
        for (GenericStack input : inputs) {
            if (input == null || input.amount() <= 0) continue;
            if (!(input.what() instanceof AEItemKey)) {
                return false; // fallback 不支持非物品
            }
            ItemStack stack = input.what().wrapForDisplayOrFilter();
            stack.setCount((int) Math.min(input.amount(), Integer.MAX_VALUE));

            ItemStack remaining = ItemHandlerHelper.insertItem(handler, stack, true);
            if (!remaining.isEmpty()) {
                // 模拟插入失败,回滚已模拟插入的物品
                revertInserted(handler, inserted);
                return false;
            }
            remaining = ItemHandlerHelper.insertItem(handler, stack, false);
            if (!remaining.isEmpty()) {
                revertInserted(handler, inserted);
                return false;
            }
            inserted.add(stack);
        }
        return true;
    }

    @Override
    public boolean startProcess(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IActionSource source) {
        return true; // 普通机器插入物品后自动开始
    }

    @Override
    @Nonnull
    public List<GenericStack> collectProducts(@Nonnull World world, @Nonnull BlockPos pos,
                                              @Nonnull IPatternDetails pattern, @Nonnull List<GenericStack> inputs,
                                              @Nonnull IActionSource source) {
        IItemHandler handler = getItemHandler(world, pos);
        if (handler == null) {
            return Collections.emptyList();
        }

        List<GenericStack> products = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || isInputRemainder(stack, inputs)) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, stack.getCount(), false);
            if (!extracted.isEmpty()) {
                GenericStack generic = GenericStack.fromItemStack(extracted);
                if (generic != null) {
                    products.add(generic);
                }
            }
        }
        return products;
    }

    @Override
    public boolean isIdle(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull List<GenericStack> inputs) {
        IItemHandler handler = getItemHandler(world, pos);
        if (handler == null) {
            return true;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && !isInputRemainder(stack, inputs)) {
                return true; // 有非输入残留,认为可以收集
            }
        }
        return true; // fallback 默认允许结束
    }

    @Override
    @Nonnull
    public List<GenericStack> clearOutputs(@Nonnull World world, @Nonnull BlockPos pos,
                                           @Nonnull IActionSource source) {
        return collectProducts(world, pos, null, Collections.emptyList(), source);
    }

    @Nullable
    private IItemHandler getItemHandler(@Nonnull World world, @Nonnull BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return null;
        }
        return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP);
    }

    private boolean isInputRemainder(@Nonnull ItemStack stack, @Nonnull List<GenericStack> inputs) {
        for (GenericStack input : inputs) {
            if (input == null) continue;
            if (!(input.what() instanceof AEItemKey)) continue;
            ItemStack inputStack = input.what().wrapForDisplayOrFilter();
            if (ItemHandlerHelper.canItemStacksStack(inputStack, stack)) {
                return true;
            }
        }
        return false;
    }

    private void revertInserted(@Nonnull IItemHandler handler, @Nonnull List<ItemStack> inserted) {
        for (ItemStack stack : inserted) {
            for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (!inSlot.isEmpty() && ItemHandlerHelper.canItemStacksStack(inSlot, stack)) {
                    ItemStack extracted = handler.extractItem(slot, stack.getCount(), false);
                    stack.shrink(extracted.getCount());
                }
            }
        }
    }
}

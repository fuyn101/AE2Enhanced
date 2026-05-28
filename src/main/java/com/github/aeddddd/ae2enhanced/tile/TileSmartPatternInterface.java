package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternStorageFile;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartRecipe;
import com.github.aeddddd.ae2enhanced.item.ItemSmartBlankPattern;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.UUID;

/**
 * 智能样板接口的 TileEntity。
 *
 * <p>功能：</p>
 * <ul>
 *   <li>存储绑定目标方块信息（boundPos / boundDim / boundBlockId）</li>
 *   <li>管理 SmartPatternData（配方列表、冲突/禁用掩码）</li>
 *   <li>提供样板输入槽和输出槽（通过 ItemStackHandler）</li>
 *   <li>处理编码逻辑（空白样板 → 编码后样板）</li>
 *   <li>MiniGUI 编辑：锁定配方后可修改输入输出</li>
 * </ul>
 *
 * <p>注意：不接入 ME 网络，纯手动配置终端。</p>
 */
public class TileSmartPatternInterface extends TileEntity {

    private static final String NBT_BOUND_POS = "boundPos";
    private static final String NBT_BOUND_DIM = "boundDim";
    private static final String NBT_BOUND_BLOCK_ID = "boundBlockId";
    private static final String NBT_PATTERN_DATA_ID = "patternDataId";

    // 绑定目标信息
    @Nullable
    private BlockPos boundPos;
    private int boundDim = Integer.MIN_VALUE;
    @Nonnull
    private String boundBlockId = "";

    // 当前的 SmartPatternData（编码前/编码后都可能存在）
    @Nullable
    private SmartPatternData patternData;

    // 配方显示页码（0 = 第1页，每页45个配方）
    private int scrollOffset = 0;

    // 锁定配方的排序索引（-1 = 未锁定）
    private int lockedRecipeIndex = -1;

    // MiniGUI 滚动偏移
    private int miniGuiInputScroll = 0;
    private int miniGuiOutputScroll = 0;

    // 防止 onContentsChanged 递归
    private boolean isUpdatingMiniGui = false;

    // MiniGUI 物品栏：前9=输入，后9=输出
    private final ItemStackHandler miniGuiInventory = new ItemStackHandler(18) {
        @Override
        protected void onContentsChanged(int slot) {
            if (!isUpdatingMiniGui && world != null && !world.isRemote
                    && lockedRecipeIndex >= 0 && patternData != null) {
                syncMiniGuiSlotToRecipe(slot);
            }
            markDirty();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return lockedRecipeIndex >= 0;
        }
    };

    // 配方显示槽位：45槽 (9列 x 5行)，用于GUI展示配方输出
    private final ItemStackHandler recipeDisplayInventory = new ItemStackHandler(45) {
        @Override
        protected void onContentsChanged(int slot) {
            // 配方显示槽位变化不需要 markDirty（内容由patternData动态计算）
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return false; // 不允许手动放入
        }
    };

    // 物品槽位：0=空白样板输入, 1=编码样板输出
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot == 0) {
                return stack.getItem() instanceof ItemSmartBlankPattern;
            }
            return false; // 输出槽不允许手动放入
        }
    };

    public TileSmartPatternInterface() {
    }

    // ---- 绑定管理 ----

    public boolean isBound() {
        return boundPos != null && boundDim != Integer.MIN_VALUE && !boundBlockId.isEmpty();
    }

    @Nullable
    public BlockPos getBoundPos() {
        return boundPos;
    }

    public int getBoundDim() {
        return boundDim;
    }

    @Nonnull
    public String getBoundBlockId() {
        return boundBlockId;
    }

    public void setBinding(@Nullable BlockPos pos, int dim, @Nonnull String blockId) {
        this.boundPos = pos;
        this.boundDim = dim;
        this.boundBlockId = blockId;
        this.patternData = null; // 重新绑定后清除旧数据
        this.lockedRecipeIndex = -1;
        clearMiniGuiInventory();
        markDirty();
        syncToClient();
    }

    public void clearBinding() {
        this.boundPos = null;
        this.boundDim = Integer.MIN_VALUE;
        this.boundBlockId = "";
        this.patternData = null;
        this.lockedRecipeIndex = -1;
        clearMiniGuiInventory();
        markDirty();
        syncToClient();
    }

    // ---- SmartPatternData 管理 ----

    @Nullable
    public SmartPatternData getPatternData() {
        return patternData;
    }

    public void setPatternData(@Nullable SmartPatternData data) {
        this.patternData = data;
        this.lockedRecipeIndex = -1;
        clearMiniGuiInventory();
        markDirty();
    }

    /**
     * 编码：将输入槽的空白样板转换为编码后的智能样板，放入输出槽。
     *
     * @return 是否编码成功
     */
    public boolean encodePattern(@Nonnull EntityPlayer player) {
        if (patternData == null || patternData.hasConflicts()) {
            return false;
        }
        ItemStack input = inventory.getStackInSlot(0);
        if (input.isEmpty() || !(input.getItem() instanceof ItemSmartBlankPattern)) {
            return false;
        }
        if (!inventory.getStackInSlot(1).isEmpty()) {
            return false; // 输出槽已满
        }

        // 保存配方数据到文件
        boolean saved = SmartPatternStorageFile.save(world, patternData);
        if (!saved) {
            return false;
        }

        // 创建编码后的样板
        ItemStack encoded = ItemSmartPattern.createPattern(
                patternData.getPatternDataId(),
                patternData.getDisabledMask(),
                patternData.getRecipeCount(),
                patternData.getTargetBlockId()
        );

        // 消耗输入，输出编码样板
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        inventory.setStackInSlot(1, encoded);
        markDirty();
        return true;
    }

    // ---- 物品槽位 ----

    @Nonnull
    public ItemStackHandler getRecipeDisplayInventory() {
        return recipeDisplayInventory;
    }

    @Nonnull
    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Nonnull
    public ItemStackHandler getMiniGuiInventory() {
        return miniGuiInventory;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int scrollOffset) {
        int maxPage = getMaxPage();
        int newOffset = Math.max(0, Math.min(maxPage, scrollOffset));
        if (this.scrollOffset != newOffset) {
            this.scrollOffset = newOffset;
            updateRecipeDisplay();
            markDirty();
        }
    }

    private int getMaxPage() {
        if (patternData == null) return 0;
        return Math.max(0, (patternData.getRecipeCount() - 1) / 45);
    }

    // ---- 锁定与 MiniGUI ----

    public int getLockedRecipeIndex() {
        return lockedRecipeIndex;
    }

    public boolean isRecipeLocked(int sortedIndex) {
        return lockedRecipeIndex == sortedIndex;
    }

    public void lockRecipe(int sortedIndex) {
        if (patternData == null) return;
        this.lockedRecipeIndex = sortedIndex;
        updateMiniGuiFromRecipe();
        markDirty();
        syncToClient();
    }

    public void unlockRecipe() {
        this.lockedRecipeIndex = -1;
        clearMiniGuiInventory();
        markDirty();
        syncToClient();
    }

    public void modifyLockedRecipe(@Nonnull String action) {
        if (lockedRecipeIndex < 0 || patternData == null) return;
        int original = patternData.getDisplayIndex(lockedRecipeIndex);
        if (original < 0) return;
        patternData.modifyRecipe(lockedRecipeIndex, action);
        updateMiniGuiFromRecipe();
        updateRecipeDisplay();
        markDirty();
        syncToClient();
    }

    /**
     * 删除所有已禁用的配方。
     */
    public void deleteDisabledRecipes() {
        if (patternData == null) return;
        patternData.deleteDisabledRecipes();
        if (lockedRecipeIndex >= 0) {
            lockedRecipeIndex = -1;
            clearMiniGuiInventory();
        }
        setScrollOffset(scrollOffset); // 重新限制页码
        updateRecipeDisplay();
        markDirty();
        syncToClient();
    }

    private void clearMiniGuiInventory() {
        isUpdatingMiniGui = true;
        try {
            for (int i = 0; i < miniGuiInventory.getSlots(); i++) {
                miniGuiInventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        } finally {
            isUpdatingMiniGui = false;
        }
    }

    private void updateMiniGuiFromRecipe() {
        if (lockedRecipeIndex < 0 || patternData == null) {
            clearMiniGuiInventory();
            return;
        }
        int original = patternData.getDisplayIndex(lockedRecipeIndex);
        if (original < 0) {
            clearMiniGuiInventory();
            return;
        }
        SmartRecipe recipe = patternData.getRecipes().get(original);
        IAEItemStack[] inputs = recipe.getInputs();
        IAEItemStack[] outputs = recipe.getOutputs();

        isUpdatingMiniGui = true;
        try {
            for (int i = 0; i < 9; i++) {
                if (i < inputs.length && inputs[i] != null) {
                    miniGuiInventory.setStackInSlot(i, inputs[i].createItemStack());
                } else {
                    miniGuiInventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
            for (int i = 0; i < 9; i++) {
                if (i < outputs.length && outputs[i] != null) {
                    miniGuiInventory.setStackInSlot(i + 9, outputs[i].createItemStack());
                } else {
                    miniGuiInventory.setStackInSlot(i + 9, ItemStack.EMPTY);
                }
            }
        } finally {
            isUpdatingMiniGui = false;
        }
    }

    private void syncMiniGuiSlotToRecipe(int slot) {
        int original = patternData.getDisplayIndex(lockedRecipeIndex);
        if (original < 0) return;
        SmartRecipe recipe = patternData.getRecipes().get(original);
        ItemStack stack = miniGuiInventory.getStackInSlot(slot);
        IAEItemStack aeStack = stack.isEmpty() ? null : AEItemStack.fromItemStack(stack);
        if (slot < 9) {
            recipe.setInput(slot, aeStack);
        } else {
            recipe.setOutput(slot - 9, aeStack);
        }
        patternData.detectConflicts();
        updateRecipeDisplay();
        syncToClient();
    }

    public int getMiniGuiInputScroll() {
        return miniGuiInputScroll;
    }

    public void setMiniGuiInputScroll(int scroll) {
        this.miniGuiInputScroll = Math.max(0, scroll);
        markDirty();
    }

    public int getMiniGuiOutputScroll() {
        return miniGuiOutputScroll;
    }

    public void setMiniGuiOutputScroll(int scroll) {
        this.miniGuiOutputScroll = Math.max(0, scroll);
        markDirty();
    }

    // ---- 配方显示 ----

    /**
     * 根据当前 scrollOffset（页码）和 patternData 更新配方显示槽位。
     */
    public void updateRecipeDisplay() {
        if (patternData == null) {
            for (int i = 0; i < recipeDisplayInventory.getSlots(); i++) {
                recipeDisplayInventory.setStackInSlot(i, ItemStack.EMPTY);
            }
            return;
        }
        int recipeCount = patternData.getRecipeCount();
        int pageStart = this.scrollOffset * 45;
        for (int i = 0; i < recipeDisplayInventory.getSlots(); i++) {
            int sortedIndex = pageStart + i;
            if (sortedIndex < recipeCount) {
                SmartRecipe recipe = patternData.getRecipe(sortedIndex);
                if (recipe != null) {
                    IAEItemStack primary = recipe.getPrimaryOutput();
                    recipeDisplayInventory.setStackInSlot(i, primary != null ? primary.createItemStack() : ItemStack.EMPTY);
                } else {
                    recipeDisplayInventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            } else {
                recipeDisplayInventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * 切换指定配方索引的禁用/启用状态。
     */
    public void toggleRecipe(int sortedRecipeIndex) {
        if (patternData == null) return;
        int originalIndex = patternData.getDisplayIndex(sortedRecipeIndex);
        if (originalIndex < 0 || originalIndex >= patternData.getRecipeCount()) return;
        patternData.getDisabledMask().flip(originalIndex);
        updateRecipeDisplay();
        markDirty();
        syncToClient();
    }

    /**
     * 破坏时掉落所有内容物。
     */
    public void dropAllContents() {
        if (world == null || world.isRemote) return;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, java.util.Collections.singletonList(stack));
            }
        }
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey(NBT_BOUND_POS)) {
            int[] arr = compound.getIntArray(NBT_BOUND_POS);
            if (arr.length == 3) {
                boundPos = new BlockPos(arr[0], arr[1], arr[2]);
            }
        }
        boundDim = compound.getInteger(NBT_BOUND_DIM);
        boundBlockId = compound.getString(NBT_BOUND_BLOCK_ID);
        if (compound.hasKey("inventory")) {
            inventory.deserializeNBT(compound.getCompoundTag("inventory"));
        }
        scrollOffset = compound.getInteger("scrollOffset");
        lockedRecipeIndex = compound.getInteger("lockedRecipeIndex");
        miniGuiInputScroll = compound.getInteger("miniGuiInputScroll");
        miniGuiOutputScroll = compound.getInteger("miniGuiOutputScroll");
        if (compound.hasKey("recipeDisplay")) {
            recipeDisplayInventory.deserializeNBT(compound.getCompoundTag("recipeDisplay"));
        }
        if (compound.hasKey("miniGuiInventory")) {
            miniGuiInventory.deserializeNBT(compound.getCompoundTag("miniGuiInventory"));
        }
        // patternData 优先直接反序列化（支持客户端同步），回退到文件加载
        if (compound.hasKey("patternData")) {
            patternData = SmartPatternData.fromNBT(compound.getCompoundTag("patternData"));
        } else if (compound.hasKey(NBT_PATTERN_DATA_ID + "Most")) {
            UUID dataId = compound.getUniqueId(NBT_PATTERN_DATA_ID);
            patternData = SmartPatternStorageFile.load(world, dataId);
        }
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (boundPos != null) {
            compound.setIntArray(NBT_BOUND_POS, new int[]{boundPos.getX(), boundPos.getY(), boundPos.getZ()});
        }
        compound.setInteger(NBT_BOUND_DIM, boundDim);
        compound.setString(NBT_BOUND_BLOCK_ID, boundBlockId);
        compound.setTag("inventory", inventory.serializeNBT());
        compound.setInteger("scrollOffset", scrollOffset);
        compound.setInteger("lockedRecipeIndex", lockedRecipeIndex);
        compound.setInteger("miniGuiInputScroll", miniGuiInputScroll);
        compound.setInteger("miniGuiOutputScroll", miniGuiOutputScroll);
        compound.setTag("recipeDisplay", recipeDisplayInventory.serializeNBT());
        compound.setTag("miniGuiInventory", miniGuiInventory.serializeNBT());
        if (patternData != null) {
            compound.setUniqueId(NBT_PATTERN_DATA_ID, patternData.getPatternDataId());
            compound.setTag("patternData", patternData.toNBT());
        }
        return compound;
    }

    // ---- 客户端同步 ----

    @Override
    @Nonnull
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(super.getUpdateTag());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readFromNBT(tag);
    }

    @Override
    @Nullable
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, -1, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    private void syncToClient() {
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    // ---- Capability ----

    @Override
    public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                || super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (facing == null) {
                return net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
            }
        }
        return super.getCapability(capability, facing);
    }
}

package com.github.aeddddd.ae2enhanced.tile;

import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternStorageFile;
import com.github.aeddddd.ae2enhanced.item.ItemSmartBlankPattern;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
        markDirty();
        syncToClient();
    }

    public void clearBinding() {
        this.boundPos = null;
        this.boundDim = Integer.MIN_VALUE;
        this.boundBlockId = "";
        this.patternData = null;
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
    public ItemStackHandler getInventory() {
        return inventory;
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
        // patternData 不直接存储在 NBT 中，而是通过 patternDataId 从文件加载
        if (compound.hasKey(NBT_PATTERN_DATA_ID + "Most")) {
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
        if (patternData != null) {
            compound.setUniqueId(NBT_PATTERN_DATA_ID, patternData.getPatternDataId());
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
            return net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
        }
        return super.getCapability(capability, facing);
    }
}

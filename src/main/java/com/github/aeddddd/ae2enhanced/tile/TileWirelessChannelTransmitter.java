package com.github.aeddddd.ae2enhanced.tile;

import ae2.api.networking.GridFlags;
import ae2.api.networking.IGridNode;
import ae2.api.networking.IManagedGridNode;
import ae2.api.stacks.AEItemKey;
import ae2.api.util.AECableType;
import ae2.util.Platform;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import java.util.EnumSet;

/**
 * F1a：无线频道发生器的 TileEntity.
 *
 * <p>具有 AE 网络节点、2 槽物品栏(输入/输出频道卡)、
 * 周期性绑定处理与客户端状态同步.</p>
 */
public class TileWirelessChannelTransmitter extends TileAENetworkBase implements ITickable {

    public static final int SLOT_CARD = 0;

    private EnumFacing forward = EnumFacing.NORTH;
    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            TileWirelessChannelTransmitter.this.markDirty();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof ItemChannelReceiverCard
                || stack.getItem() instanceof ItemUniversalMemoryCard;
        }
    };

    // 客户端同步标志
    private int clientFlags = 0;
    private boolean lastPowered = false;
    private boolean lastActive = false;

    public TileWirelessChannelTransmitter() {
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setFlags(GridFlags.DENSE_CAPACITY, GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(AE2EnhancedConfig.wirelessChannel.transmitterPower)
                .setExposedOnSides(getExposedSides())
                .setVisualRepresentation(AEItemKey.of(new ItemStack(BlockRegistry.WIRELESS_CHANNEL_TRANSMITTER)));
    }

    private java.util.Set<EnumFacing> getExposedSides() {
        return EnumSet.of(this.forward.getOpposite());
    }

    private void updateExposedSides() {
        getMainNode().setExposedOnSides(getExposedSides());
    }

    // ---------- 朝向与代理 ----------

    public void setForward(EnumFacing facing) {
        this.forward = facing != null ? facing : EnumFacing.NORTH;
        updateExposedSides();
        markDirty();
    }

    public EnumFacing getForward() {
        return this.forward;
    }

    @Override
    public void disassemble() {
        if (world != null && !world.isRemote) {
            dropInventory(world, pos);
        }
    }

    public void securityBreak() {
        if (world != null && !world.isRemote) {
            dropInventory(world, pos);
            world.destroyBlock(pos, true);
        }
    }

    @Override
    public AECableType getCableConnectionType(@Nonnull EnumFacing dir) {
        if (this.forward != null && dir == this.forward.getOpposite()) {
            return AECableType.DENSE_SMART;
        }
        return AECableType.NONE;
    }

    @Override
    public IGridNode getGridNode(@Nonnull EnumFacing dir) {
        // null/INTERNAL 用于远程查询(如频道接收卡查找发射器节点)
        if (dir == null) {
            return getMainNode().getNode();
        }
        // 仅背面暴露节点(用于线缆连接)
        if (this.forward != null && dir == this.forward.getOpposite()) {
            return getMainNode().getNode();
        }
        return null;
    }

    // ---------- 初始化 ----------

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        processBinding();
        syncClientState();
    }

    private void syncClientState() {
        boolean powered = isPowered();
        boolean active = isActive();
        if (powered != lastPowered || active != lastActive) {
            lastPowered = powered;
            lastActive = active;
            int flags = 0;
            if (powered) flags |= 1;
            if (active) flags |= 2;
            this.clientFlags = flags;
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    // ---------- 状态访问 ----------

    public boolean isPowered() {
        if (world != null && world.isRemote) {
            return (this.clientFlags & 1) == 1;
        }
        return getMainNode().isPowered();
    }

    public boolean isActive() {
        if (world != null && world.isRemote) {
            return isPowered() && (this.clientFlags & 2) == 2;
        }
        return getMainNode().isActive();
    }

    // ---------- 物品栏 ----------

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    public void processBinding() {
        ItemStack stack = this.inventory.getStackInSlot(SLOT_CARD);
        if (stack.isEmpty()) return;

        if (stack.getItem() instanceof ItemChannelReceiverCard) {
            if (ItemChannelReceiverCard.isBound(stack)) return;
            BlockPos tp = this.pos;
            int dim = this.world.provider.getDimension();
            ItemChannelReceiverCard.bindToTransmitter(stack, tp, dim, this.forward);
            this.markDirty();
        } else if (stack.getItem() instanceof ItemUniversalMemoryCard) {
            if (ItemUniversalMemoryCard.hasBinding(stack)) {
                NBTTagCompound binding = ItemUniversalMemoryCard.getBinding(stack);
                BlockPos boundPos = BlockPos.fromLong(binding.getLong("pos"));
                int boundDim = binding.getInteger("dim");
                if (boundPos.equals(this.pos) && boundDim == this.world.provider.getDimension()) {
                    return; // 已绑定到本发射器
                }
            }
            ItemUniversalMemoryCard.setBinding(stack, this.pos, this.world.provider.getDimension());
            this.markDirty();
        }
    }

    public void dropInventory(World world, BlockPos pos) {
        for (int i = 0; i < this.inventory.getSlots(); i++) {
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, java.util.Collections.singletonList(stack));
            }
        }
    }

    // ---------- NBT ----------

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.forward = EnumFacing.byIndex(compound.getInteger("forward"));
        this.inventory.deserializeNBT(compound.getCompoundTag("inv"));
        this.clientFlags = compound.getInteger("clientFlags");
        updateExposedSides();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("forward", this.forward.getIndex());
        compound.setTag("inv", this.inventory.serializeNBT());
        compound.setInteger("clientFlags", this.clientFlags);
        return compound;
    }

    // ---------- 网络同步 ----------

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        tag.setInteger("forward", this.forward.getIndex());
        tag.setInteger("clientFlags", this.clientFlags);
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        this.forward = EnumFacing.byIndex(tag.getInteger("forward"));
        this.clientFlags = tag.getInteger("clientFlags");
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }
}

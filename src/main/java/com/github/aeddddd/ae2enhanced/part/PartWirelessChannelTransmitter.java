package com.github.aeddddd.ae2enhanced.part;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.parts.PartModel;
import appeng.core.settings.TickRates;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.parts.PartBasicState;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import java.util.EnumSet;
import java.util.List;

/**
 * F1a：无线频道发生器 Part。
 *
 * <p>安装在线缆上，具有 {@code DENSE_CAPACITY}（32 频道）。
 * 右键打开 GUI，可将空白频道卡写入自身坐标并输出已绑定卡片。
 * 持续消耗配置文件中设定的 AE 能量。</p>
 */
public class PartWirelessChannelTransmitter extends PartBasicState implements IGridTickable {

    private static final ResourceLocation MODEL_OFF = new ResourceLocation(AE2Enhanced.MOD_ID, "part/wireless_channel_transmitter_off");
    private static final ResourceLocation MODEL_ON = new ResourceLocation(AE2Enhanced.MOD_ID, "part/wireless_channel_transmitter_on");
    private static final ResourceLocation MODEL_HAS_CHANNEL = new ResourceLocation(AE2Enhanced.MOD_ID, "part/wireless_channel_transmitter_has_channel");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(new ResourceLocation[]{MODEL_ON, MODEL_HAS_CHANNEL});

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            PartWirelessChannelTransmitter.this.saveData();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot == SLOT_INPUT) {
                return stack.getItem() instanceof ItemChannelReceiverCard && !ItemChannelReceiverCard.isBound(stack);
            }
            return false;
        }
    };

    public PartWirelessChannelTransmitter(ItemStack is) {
        super(is);
        this.getProxy().setFlags(GridFlags.DENSE_CAPACITY, GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(AE2EnhancedConfig.wirelessChannel.transmitterPower);
        this.getProxy().setValidSides(EnumSet.noneOf(net.minecraft.util.EnumFacing.class));
    }

    @Override
    public void readFromNBT(NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.inventory.deserializeNBT(extra.getCompoundTag("inv"));
    }

    @Override
    public void writeToNBT(NBTTagCompound extra) {
        super.writeToNBT(extra);
        extra.setTag("inv", this.inventory.serializeNBT());
    }

    @Override
    public AECableType getCableConnectionType(AEPartLocation dir) {
        return AECableType.DENSE_SMART;
    }

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (!player.world.isRemote) {
            if (Platform.hasPermissions(player.world, this.getTile().getPos(), player)) {
                int guiId = GuiHandler.GUI_WIRELESS_CHANNEL_TRANSMITTER | (this.getSide().ordinal() << 8);
                net.minecraft.tileentity.TileEntity te = this.getHost().getTile();
                player.openGui(AE2Enhanced.instance, guiId, te.getWorld(), te.getPos().getX(), te.getPos().getY(), te.getPos().getZ());
            }
        }
        return true;
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    /**
     * 尝试将输入槽的空白卡转换为输出槽的已绑定卡。
     */
    public void processBinding() {
        ItemStack input = this.inventory.getStackInSlot(SLOT_INPUT);
        ItemStack output = this.inventory.getStackInSlot(SLOT_OUTPUT);
        if (input.isEmpty() || !(input.getItem() instanceof ItemChannelReceiverCard)) return;
        if (ItemChannelReceiverCard.isBound(input)) return;
        if (!output.isEmpty()) return;

        net.minecraft.tileentity.TileEntity te = this.getTile();
        BlockPos tp = te.getPos();
        int dim = te.getWorld().provider.getDimension();

        ItemStack bound = input.copy();
        bound.setCount(1);
        ItemChannelReceiverCard.bindToTransmitter(bound, tp, dim, this.getSide().getFacing());

        input.shrink(1);
        this.inventory.setStackInSlot(SLOT_INPUT, input);
        this.inventory.setStackInSlot(SLOT_OUTPUT, bound);
        this.saveData();
    }

    public void saveData() {
        NBTTagCompound data = new NBTTagCompound();
        this.writeToNBT(data);
        this.getHost().markForSave();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TickRates.ImportBus.getMin(), TickRates.ImportBus.getMax(), false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        this.processBinding();
        return TickRateModulation.SLEEP;
    }

    @PartModels
    public static List<IPartModel> getModelsStatic() {
        return java.util.Arrays.asList(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        }
        if (this.isPowered()) {
            return MODELS_ON;
        }
        return MODELS_OFF;
    }
}

package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.starlight.AEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IAEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

/**
 * Starlight 访问节点 TileEntity.
 * 将外部 Astral Sorcery Starlight 接入 AE2 ME 网络的 Starlight 存储通道.
 * 输入有严格上限,防止玩家大批量囤积星能.
 */
public class TileStarlightAccessNode extends TileAENetworkBase
        implements IActionHost, ITickable {

    public static final int MODE_INPUT = 0;
    public static final int MODE_OUTPUT = 1;
    private static final String NBT_MODE = "StarlightMode";

    private static final String ALTAR_CLASS = "hellfirepvp.astralsorcery.common.tile.TileAltar";
    private static Method altarGetStarlightStored;
    private static Method altarGetMaxStarlightStorage;
    private static Method altarReceiveStarlight;
    private static boolean altarReflectionReady = false;

    private MachineSource machineSource;
    private int mode = MODE_INPUT;

    public TileStarlightAccessNode() {
        initAltarReflection();
    }

    private static synchronized void initAltarReflection() {
        if (altarReflectionReady) return;
        try {
            Class<?> altarClass = Class.forName(ALTAR_CLASS);
            altarGetStarlightStored = altarClass.getMethod("getStarlightStored");
            altarGetMaxStarlightStorage = altarClass.getMethod("getMaxStarlightStorage");
            altarReceiveStarlight = altarClass.getMethod("receiveStarlight", Class.forName("hellfirepvp.astralsorcery.common.constellation.IWeakConstellation"), double.class);
            altarReflectionReady = true;
        } catch (Throwable t) {
            altarGetStarlightStored = null;
            altarGetMaxStarlightStorage = null;
            altarReceiveStarlight = null;
            altarReflectionReady = false;
        }
    }

    // === TileAENetworkBase ===

    @Override
    protected String getProxyName() {
        return "starlight_access_node";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.STARLIGHT_ACCESS_NODE);
    }

    @Override
    public AECableType getCableConnectionType(@Nonnull AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public void disassemble() {
    }

    @Override
    public void securityBreak() {
        if (this.world != null) {
            this.world.destroyBlock(this.pos, true);
        }
    }

    // === IActionHost ===

    @Override
    public IGridNode getActionableNode() {
        return this.getProxy().getNode();
    }

    // === ITickable ===

    @Override
    public void update() {
        if (!this.world.isRemote && this.needsReady()) {
            this.clearNeedsReady();
            this.getProxy().onReady();
        }
        if (!this.world.isRemote) {
            if (mode == MODE_INPUT) {
                doStarlightCollection();
            } else {
                doStarlightOutput();
            }
        }
    }

    /**
     * 输入模式：从天空收集星能,严格受配置上限限制.
     */
    private void doStarlightCollection() {
        if (!canCollectStarlight()) return;

        int maxInput = AE2EnhancedConfig.starlight.starlightAccessNodeMaxInput;
        if (maxInput <= 0) return;

        IMEMonitor<IAEStarlightStack> monitor = getStarlightMonitor();
        if (monitor == null) return;

        // 模拟注入,确保网络还能接受
        IAEStarlightStack rejected = monitor.injectItems(AEStarlightStack.create(maxInput), Actionable.SIMULATE, getMachineSource());
        long canAccept = maxInput - (rejected != null ? rejected.getStackSize() : 0);
        if (canAccept <= 0) return;

        monitor.injectItems(AEStarlightStack.create(canAccept), Actionable.MODULATE, getMachineSource());
    }

    /**
     * 判断当前是否可以收集星能.
     */
    private boolean canCollectStarlight() {
        if (world == null) return false;
        // 必须能直接看到天空
        if (!world.canBlockSeeSky(pos.up())) return false;
        // 简单判断夜晚: 客户端时间 13000 ~ 23000 为夜晚
        long time = world.getWorldTime() % 24000;
        if (time < 13000 || time > 23000) return false;
        return true;
    }

    /**
     * 输出模式：向相邻星辉祭坛输出 Starlight.
     */
    private void doStarlightOutput() {
        TileEntity altar = findAdjacentAltar();
        if (altar == null) return;

        Integer stored = getAltarStarlight(altar);
        Integer max = getAltarMaxStarlight(altar);
        if (stored == null || max == null || stored >= max) return;

        int space = max - stored;
        int maxOutput = AE2EnhancedConfig.starlight.starlightAccessNodeMaxOutput;
        int toTransfer = Math.min(space, maxOutput);
        if (toTransfer <= 0) return;

        IMEMonitor<IAEStarlightStack> monitor = getStarlightMonitor();
        if (monitor == null) return;

        IAEStarlightStack extracted = monitor.extractItems(AEStarlightStack.create(toTransfer), Actionable.MODULATE, getMachineSource());
        if (extracted == null || extracted.getStackSize() <= 0) return;

        int actual = (int) Math.min(extracted.getStackSize(), space);
        if (!giveAltarStarlight(altar, actual)) {
            // 输出失败,回退
            monitor.injectItems(AEStarlightStack.create(extracted.getStackSize()), Actionable.MODULATE, getMachineSource());
        }
    }

    private TileEntity findAdjacentAltar() {
        if (!altarReflectionReady) return null;
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos neighborPos = this.pos.offset(facing);
            if (!this.world.isBlockLoaded(neighborPos)) continue;
            TileEntity te = this.world.getTileEntity(neighborPos);
            if (te != null && !te.isInvalid() && ALTAR_CLASS.equals(te.getClass().getName())) {
                return te;
            }
        }
        return null;
    }

    private Integer getAltarStarlight(TileEntity altar) {
        try {
            return (Integer) altarGetStarlightStored.invoke(altar);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getAltarMaxStarlight(TileEntity altar) {
        try {
            return (Integer) altarGetMaxStarlightStorage.invoke(altar);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean giveAltarStarlight(TileEntity altar, int amount) {
        try {
            // receiveStarlight 内部 amount * 200,因此传入较小的原始值
            altarReceiveStarlight.invoke(altar, null, (double) amount / 200.0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // === Mode ===

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode == MODE_OUTPUT ? MODE_OUTPUT : MODE_INPUT;
        if (this.world != null) {
            this.world.notifyBlockUpdate(this.pos, this.world.getBlockState(this.pos), this.world.getBlockState(this.pos), 2);
        }
    }

    public void cycleMode() {
        setMode(mode == MODE_INPUT ? MODE_OUTPUT : MODE_INPUT);
    }

    public boolean isInputMode() {
        return mode == MODE_INPUT;
    }

    public boolean isOutputMode() {
        return mode == MODE_OUTPUT;
    }

    // === NBT ===

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.mode = compound.getInteger(NBT_MODE);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound = super.writeToNBT(compound);
        compound.setInteger(NBT_MODE, this.mode);
        return compound;
    }

    // === 辅助 ===

    private IMEMonitor<IAEStarlightStack> getStarlightMonitor() {
        try {
            IStorageGrid storageGrid = getProxy().getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return null;
            return storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IStarlightStorageChannel.class));
        } catch (GridAccessException e) {
            return null;
        }
    }

    private MachineSource getMachineSource() {
        if (this.machineSource == null) {
            this.machineSource = new MachineSource(this);
        }
        return this.machineSource;
    }

    public void onBreak() {
    }
}

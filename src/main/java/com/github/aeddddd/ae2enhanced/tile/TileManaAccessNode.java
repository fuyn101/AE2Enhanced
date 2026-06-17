package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.storage.mana.AEManaStack;
import com.github.aeddddd.ae2enhanced.util.compat.botania.BotaniaManaHelper;
import com.github.aeddddd.ae2enhanced.storage.mana.IAEManaStack;
import com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Optional;

import javax.annotation.Nonnull;

import java.lang.reflect.Method;

/**
 * Mana 访问节点 TileEntity.
 * 将外部 Botania Mana 网络接入 AE2 ME 网络的 Mana 存储通道.
 * 紧贴魔力池时可直接无上限 IO.
 */
@Optional.Interface(iface = "vazkii.botania.api.mana.IManaReceiver", modid = "botania")
public class TileManaAccessNode extends TileAENetworkBase
        implements IActionHost, vazkii.botania.api.mana.IManaReceiver, ITickable {

    public static final int MODE_INPUT = 0;
    public static final int MODE_OUTPUT = 1;
    private static final String NBT_MODE = "ManaMode";

    private static final String POOL_CLASS = "vazkii.botania.common.block.tile.mana.TilePool";
    private static Method poolGetCurrentMana;
    private static Method poolReceiveMana;
    private static boolean poolReflectionReady = false;

    private MachineSource machineSource;
    private int mode = MODE_INPUT;

    public TileManaAccessNode() {
        initPoolReflection();
    }

    private static synchronized void initPoolReflection() {
        if (poolReflectionReady) return;
        try {
            Class<?> poolClass = Class.forName(POOL_CLASS);
            poolGetCurrentMana = poolClass.getMethod("getCurrentMana");
            poolReceiveMana = poolClass.getMethod("recieveMana", int.class);
            poolReflectionReady = true;
        } catch (Throwable t) {
            poolGetCurrentMana = null;
            poolReceiveMana = null;
            poolReflectionReady = false;
        }
    }

    // === TileAENetworkBase ===

    @Override
    protected String getProxyName() {
        return "mana_access_node";
    }

    @Override
    protected ItemStack getProxyRepresentation() {
        return new ItemStack(BlockRegistry.MANA_ACCESS_NODE);
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
            doManaTick();
        }
    }

    private void doManaTick() {
        if (mode == MODE_INPUT) {
            doAdjacentPoolDrain();
        } else {
            doAdjacentPoolFill();
        }
    }

    /**
     * 输入模式：从相邻魔力池无上限抽取 Mana 注入 ME 网络.
     */
    private void doAdjacentPoolDrain() {
        TileEntity pool = findAdjacentManaPool();
        if (pool == null) return;

        BlockPos poolPos = pool.getPos();

        // 小彩蛋：若相邻的是永恒魔力池且网络中存在已成型的超维度仓储,
        // 则向网络注入 Long.MAX 的 Mana,并把永恒魔力池变为神话魔力池.
        if (BotaniaManaHelper.isEverlastingManaPool(world, poolPos)) {
            if (hasFormedHyperdimensionalController()) {
                IMEMonitor<IAEManaStack> monitor = getManaMonitor();
                if (monitor != null) {
                    monitor.injectItems(AEManaStack.create(Long.MAX_VALUE), Actionable.MODULATE, getMachineSource());
                    BotaniaManaHelper.convertEverlastingToFabulous(world, poolPos);
                    return;
                }
            }
        }

        Integer current = getPoolMana(pool);
        if (current == null || current <= 0) return;

        IMEMonitor<IAEManaStack> monitor = getManaMonitor();
        if (monitor == null) return;

        // 先模拟注入,看网络能接受多少
        IAEManaStack rejected = monitor.injectItems(AEManaStack.create(current), Actionable.SIMULATE, getMachineSource());
        long canAccept = current - (rejected != null ? rejected.getStackSize() : 0);
        if (canAccept <= 0) return;

        // 从池中扣除
        int drained = (int) Math.min(canAccept, current);
        if (!changePoolMana(pool, -drained)) return;

        // 实际注入网络
        monitor.injectItems(AEManaStack.create(drained), Actionable.MODULATE, getMachineSource());
    }

    /**
     * 输出模式：从 ME 网络抽取 Mana 并无上限填充相邻魔力池.
     */
    private void doAdjacentPoolFill() {
        TileEntity pool = findAdjacentManaPool();
        if (pool == null) return;

        Integer current = getPoolMana(pool);
        Integer cap = getPoolManaCap(pool);
        if (current == null || cap == null || current >= cap) return;

        int space = cap - current;
        if (space <= 0) return;

        IMEMonitor<IAEManaStack> monitor = getManaMonitor();
        if (monitor == null) return;

        IAEManaStack extracted = monitor.extractItems(AEManaStack.create(space), Actionable.MODULATE, getMachineSource());
        if (extracted == null || extracted.getStackSize() <= 0) return;

        int toFill = (int) Math.min(extracted.getStackSize(), space);
        if (!changePoolMana(pool, toFill)) {
            // 填充失败,回退
            monitor.injectItems(AEManaStack.create(extracted.getStackSize()), Actionable.MODULATE, getMachineSource());
        }
    }

    private TileEntity findAdjacentManaPool() {
        if (!poolReflectionReady) return null;
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos neighborPos = this.pos.offset(facing);
            if (!this.world.isBlockLoaded(neighborPos)) continue;
            TileEntity te = this.world.getTileEntity(neighborPos);
            if (te != null && !te.isInvalid() && isManaPool(te)) {
                return te;
            }
        }
        return null;
    }

    private boolean isManaPool(TileEntity te) {
        return POOL_CLASS.equals(te.getClass().getName());
    }

    private Integer getPoolMana(TileEntity pool) {
        try {
            return (Integer) poolGetCurrentMana.invoke(pool);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getPoolManaCap(TileEntity pool) {
        try {
            return (Integer) pool.getClass().getField("manaCap").get(pool);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean changePoolMana(TileEntity pool, int delta) {
        try {
            poolReceiveMana.invoke(pool, delta);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // === IManaReceiver (Botania, Optional) ===

    @Override
    public boolean isFull() {
        if (mode != MODE_INPUT) return true;
        IMEMonitor<IAEManaStack> monitor = getManaMonitor();
        if (monitor == null) return true;
        IAEManaStack rejected = monitor.injectItems(AEManaStack.create(1), Actionable.SIMULATE, getMachineSource());
        return rejected != null && rejected.getStackSize() > 0;
    }

    @Override
    public void recieveMana(int mana) {
        if (mode != MODE_INPUT || mana <= 0) return;
        IMEMonitor<IAEManaStack> monitor = getManaMonitor();
        if (monitor == null) return;
        int limit = AE2EnhancedConfig.mana.manaAccessNodeMaxTransfer;
        if (mana > limit) mana = limit;
        monitor.injectItems(AEManaStack.create(mana), Actionable.MODULATE, getMachineSource());
    }

    @Override
    public boolean canRecieveManaFromBursts() {
        return mode == MODE_INPUT;
    }

    @Override
    public int getCurrentMana() {
        IMEMonitor<IAEManaStack> monitor = getManaMonitor();
        if (monitor == null) return 0;
        IItemList<IAEManaStack> list = monitor.getStorageList();
        long total = 0;
        for (IAEManaStack stack : list) {
            if (stack != null) total += stack.getStackSize();
        }
        return (int) Math.min(total, Integer.MAX_VALUE);
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

    private IMEMonitor<IAEManaStack> getManaMonitor() {
        try {
            IStorageGrid storageGrid = getProxy().getGrid().getCache(IStorageGrid.class);
            if (storageGrid == null) return null;
            return storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IManaStorageChannel.class));
        } catch (GridAccessException e) {
            return null;
        }
    }

    /**
     * 检查当前 ME 网络中是否存在已成型的超维度仓储中枢.
     */
    private boolean hasFormedHyperdimensionalController() {
        try {
            appeng.api.networking.IGrid grid = getProxy().getGrid();
            if (grid == null) return false;
            for (appeng.api.networking.IGridNode node : grid.getNodes()) {
                if (node == null) continue;
                Object machine = node.getMachine();
                if (machine instanceof TileHyperdimensionalController) {
                    if (((TileHyperdimensionalController) machine).isFormed()) {
                        return true;
                    }
                }
            }
        } catch (GridAccessException e) {
            return false;
        }
        return false;
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

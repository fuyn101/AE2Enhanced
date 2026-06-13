package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.AEApi;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.IConfigManager;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternSubDetails;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidCompat;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 中枢 ME 接口的核心逻辑类,复刻 AE2 {@link appeng.helpers.DualityInterface} 的结构.
 *
 * P1 阶段实现：
 * - 样板存储与 CraftingProvider 注册
 * - 远程目标绑定管理
 * - 单份材料发配(默认 fallback)：推送材料到目标 IItemHandler,tick 时收集产物
 */
public class DualityCentralInterface implements appeng.util.inv.IAEAppEngInventory {

    public static final int NUMBER_OF_PATTERN_SLOTS = 36;
    public static final int NUMBER_OF_CONFIG_SLOTS = 9;
    public static final int NUMBER_OF_STORAGE_SLOTS = 9;
    public static final int NUMBER_OF_UPGRADE_SLOTS = 4;

    private final ICentralInterfaceHost host;
    private final ConfigManager cm;

    private final AppEngInternalAEInventory config;
    private final AppEngInternalInventory patterns;
    private final AppEngInternalInventory storage;

    private Set<ICraftingPatternDetails> craftingList = null;
    private int priority = 0;

    // 远程绑定
    private final List<TargetBinding> bindings = new ArrayList<>();
    private String boundBlockId = null;
    // 每个绑定目标对应一个 TargetSession,集中管理 PUSHING/PROCESSING/COLLECTING/IDLE/UNAVAILABLE
    private final Map<TargetBinding, TargetSession> sessions = new HashMap<>();
    // 虚拟合成产物暂存队列(等待 waitingFor 注册后再注入网络)
    private final List<ItemStack> pendingVirtualProducts = new ArrayList<>();

    // 流体 IO 尝试顺序：null（内部 tank）优先，然后按 UP/DOWN/NORTH/SOUTH/WEST/EAST 逐个尝试
    private static final List<EnumFacing> FLUID_FACE_ORDER;
    static {
        List<EnumFacing> list = new ArrayList<>();
        list.add(null);
        list.add(EnumFacing.UP);
        list.add(EnumFacing.DOWN);
        list.add(EnumFacing.NORTH);
        list.add(EnumFacing.SOUTH);
        list.add(EnumFacing.WEST);
        list.add(EnumFacing.EAST);
        FLUID_FACE_ORDER = Collections.unmodifiableList(list);
    }

    public DualityCentralInterface(ICentralInterfaceHost host) {
        this.host = host;
        this.cm = new ConfigManager(new IConfigManagerHost() {
            @Override
            public void updateSetting(IConfigManager manager, Enum settingName, Enum newValue) {
                DualityCentralInterface.this.host.saveChanges();
            }
        });
        this.cm.registerSetting(Settings.BLOCK, YesNo.NO);
        this.cm.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);
        this.cm.registerSetting(Settings.UNLOCK, LockCraftingMode.NONE);

        this.bindings.clear();
        this.sessions.clear();

        this.config = new AppEngInternalAEInventory(this, NUMBER_OF_CONFIG_SLOTS, 512);
        this.patterns = new AppEngInternalInventory(this, NUMBER_OF_PATTERN_SLOTS, 1) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem;
            }
        };
        this.storage = new AppEngInternalInventory(this, NUMBER_OF_STORAGE_SLOTS, 512) {
            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return true;
            }
        };
    }

    // ---- Inventory Access ----

    public AppEngInternalAEInventory getConfig() { return this.config; }
    public AppEngInternalInventory getPatterns() { return this.patterns; }
    public AppEngInternalInventory getStorage() { return this.storage; }

    public IItemHandler getInventoryByName(String name) {
        if ("config".equals(name)) return this.config;
        if ("patterns".equals(name)) return this.patterns;
        if ("storage".equals(name)) return this.storage;
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.util.EnumFacing facing) {
        if (capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) this.storage;
        }
        return null;
    }

    public void dropExcessPatterns() {
        IItemHandler patterns = this.getPatterns();
        java.util.ArrayList<net.minecraft.item.ItemStack> dropList = new java.util.ArrayList<>();
        int allowedSlots = 9 + getInstalledUpgrades(Upgrades.PATTERN_EXPANSION) * 9;
        for (int invSlot = allowedSlots; invSlot < patterns.getSlots(); ++invSlot) {
            net.minecraft.item.ItemStack is = patterns.getStackInSlot(invSlot);
            if (!is.isEmpty()) {
                dropList.add(patterns.extractItem(invSlot, Integer.MAX_VALUE, false));
            }
        }
        if (dropList.size() > 0) {
            net.minecraft.world.World world = this.host.getTileEntity().getWorld();
            net.minecraft.util.math.BlockPos pos = this.host.getTileEntity().getPos();
            for (net.minecraft.item.ItemStack stack : dropList) {
                net.minecraft.entity.item.EntityItem entityItem = new net.minecraft.entity.item.EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                world.spawnEntity(entityItem);
            }
        }
    }

    public int getInstalledUpgrades(Upgrades upgrade) {
        return ((appeng.api.implementations.IUpgradeableHost) this.host).getInstalledUpgrades(upgrade);
    }

    public LockCraftingMode getCraftingLockedReason() {
        // 当前未实现红石状态检测和 unlockEvent 追踪,默认返回 NONE
        return LockCraftingMode.NONE;
    }

    // ---- Crafting Provider ----

    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        AENetworkProxy proxy = this.host.getProxy();
        if (proxy.isActive() && this.craftingList != null && !this.bindings.isEmpty()) {
            for (ICraftingPatternDetails details : this.craftingList) {
                details.setPriority(this.priority);
                // 每个 binding 注册一次,使 AE2 网络能并行调度多个相同配方
                for (int i = 0; i < this.bindings.size(); i++) {
                    craftingTracker.addCraftingOption(this.host, details);
                }
            }
        }
    }

    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        AENetworkProxy proxy = this.host.getProxy();
        if (!proxy.isActive() || this.craftingList == null
                || !this.craftingList.contains(patternDetails)) {
            return false;
        }

        // 收集所有 IDLE 候选 target；逐个尝试，确保一个机器的失败不影响其他机器
        List<TargetBinding> candidates = findIdleTargets();
        if (candidates.isEmpty()) {
            return false;
        }

        for (TargetBinding target : candidates) {
            if (tryPushPatternToTarget(proxy, patternDetails, table, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回所有当前处于 IDLE 状态的绑定目标.
     */
    private List<TargetBinding> findIdleTargets() {
        List<TargetBinding> result = new ArrayList<>();
        for (TargetBinding binding : this.bindings) {
            TargetSession session = this.sessions.get(binding);
            if (session == null || session.isIdle()) {
                result.add(binding);
            }
        }
        return result;
    }

    private TargetSession getOrCreateSession(TargetBinding binding) {
        return this.sessions.computeIfAbsent(binding, b -> new TargetSession(b, this));
    }

    /**
     * 尝试把单个样板推送到指定目标.
     *
     * <p>本方法内部会复制 {@code originalTable}，因此无论成功或失败都不会污染
     * AE2 传入的原始 {@link InventoryCrafting}，也不会影响其他 target 的推送尝试.</p>
     *
     * @return true 表示推送成功；false 表示失败，调用方应尝试下一个 target
     */
    private boolean tryPushPatternToTarget(AENetworkProxy proxy,
                                           ICraftingPatternDetails patternDetails,
                                           InventoryCrafting originalTable,
                                           TargetBinding target) {
        World world = this.host.getTileEntity().getWorld();
        if (world.provider.getDimension() != target.dimension) {
            return false;
        }

        IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
        if (handler == null) {
            return false;
        }

        TargetSession session = getOrCreateSession(target);

        // 1A：每个 target 同一时刻只能有一份材料在途
        if (!session.isIdle()) {
            return false;
        }

        if (!handler.isValidTarget(world, target.pos)) {
            session.setUnavailable();
            return false;
        }

        // 虚拟合成路径(Extended Crafting 工作台等)
        if (handler instanceof IVirtualCraftingHandler) {
            IVirtualCraftingHandler vh = (IVirtualCraftingHandler) handler;
            // 复制 table，避免污染 AE2 传入的原始 InventoryCrafting
            InventoryCrafting virtualTable = copyInventoryCrafting(originalTable);
            IAEItemStack[] outputs = patternDetails.getOutputs();
            if (vh.canCraftVirtually(world, target.pos, virtualTable, outputs)) {
                List<ItemStack> products = vh.virtualCraft(world, target.pos, virtualTable, outputs,
                        new appeng.me.helpers.MachineSource(this.host));
                if (!products.isEmpty()) {
                    // 使用 handler 实际返回的产物,避免与 pattern 定义输出数量/物品不一致
                    this.pendingVirtualProducts.addAll(products);
                }
                // 虚拟合成不占用物理设备,target 保持 IDLE,可立即复用
                tryWakeTickDevice();
                return true;
            }
            return false;
        }

        // 物理模式路径：复制 table，避免一个 target 的失败影响其他 target
        InventoryCrafting table = copyInventoryCrafting(originalTable);

        // 获取全局坐标所有权，进入 PUSHING 状态
        List<FluidStack> pushedFluids = new ArrayList<>();
        if (!session.beginPush(pushedFluids)) {
            return false;
        }

        boolean success = false;
        try {
            // 推送流体输入(如果配方包含流体),并从 table 中移除已推送的流体假物品
            if (!pushFluidInputs(world, target.pos, table, pushedFluids)) {
                revertPushedFluids(world, target.pos, pushedFluids);
                return false;
            }

            // 发配前回收目标全部输出槽残留内容,防止残留产物干扰新材料推送
            List<ItemStack> clearedOutputs = handler.clearOutputs(world, target.pos,
                    new appeng.me.helpers.MachineSource(this.host));
            if (!clearedOutputs.isEmpty()) {
                if (!injectItemsToNetwork(proxy, world, clearedOutputs)) {
                    stashItemsToStorage(world, clearedOutputs);
                }
            }

            // 物理模式路径
            if (!handler.canStart(world, target.pos, table)) {
                revertPushedFluids(world, target.pos, pushedFluids);
                return false;
            }

            boolean pushed = handler.pushMaterials(world, target.pos, table,
                    new appeng.me.helpers.MachineSource(this.host));
            if (!pushed) {
                // 回退已推入的材料(handler 可能已部分推入)
                List<ItemStack> reverted = handler.revertMaterials(world, target.pos,
                        new appeng.me.helpers.MachineSource(this.host));
                if (!reverted.isEmpty()) {
                    injectItemsToNetwork(proxy, world, reverted);
                }
                revertPushedFluids(world, target.pos, pushedFluids);
                return false;
            }

            boolean started = handler.startProcess(world, target.pos,
                    new appeng.me.helpers.MachineSource(this.host));
            if (!started) {
                // 回退已推送的材料
                List<ItemStack> reverted = handler.revertMaterials(world, target.pos,
                        new appeng.me.helpers.MachineSource(this.host));
                if (!reverted.isEmpty()) {
                    injectItemsToNetwork(proxy, world, reverted);
                }
                // 回退已推送的流体
                revertPushedFluids(world, target.pos, pushedFluids);
                return false;
            }

            IAEItemStack[] outputs = patternDetails.getOutputs();

            // 保存输入材料快照(在 pushFluidInputs 后 table 中已移除流体,剩余为物品材料)
            List<ItemStack> inputSnapshot = new ArrayList<>();
            for (int i = 0; i < table.getSizeInventory(); i++) {
                ItemStack stack = table.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    inputSnapshot.add(stack.copy());
                }
            }

            session.commitPush(outputs, inputSnapshot, world.getTotalWorldTime());
            success = true;
            tryWakeTickDevice();
            return true;
        } finally {
            if (!success) {
                session.reset();
            }
        }
    }

    /**
     * 将物品列表注入 AE 网络,溢出部分先进入 storage slots,再溢出则掉落.
     * 流体假物品走物品通道,由 ae2fc 的 FakeMonitor 体系接管(若 ae2fc 未安装,
     * 则由本 mod 的 MixinNetworkMonitorFluid 转注入流体通道).
     */
    private boolean injectItemsToNetwork(AENetworkProxy proxy, World world, List<ItemStack> items) {
        try {
            IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            for (ItemStack product : items) {
                if (product.isEmpty()) continue;
                IAEItemStack toInsert = AEItemStack.fromItemStack(product);
                IAEItemStack remaining = Platform.poweredInsert(
                        proxy.getEnergy(),
                        proxy.getStorage().getInventory(channel),
                        toInsert,
                        new appeng.me.helpers.MachineSource(this.host));
                if (remaining != null && remaining.getStackSize() > 0) {
                    ItemStack leftover = remaining.createItemStack();
                    stashItemToStorage(world, leftover);
                }
            }
            return true;
        } catch (appeng.me.GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] CentralInterface failed to inject items to network", e);
            return false;
        }
    }

    /**
     * 将单个物品暂存到 storage slots,溢出则掉落.
     */
    private void stashItemToStorage(World world, ItemStack item) {
        if (item.isEmpty()) return;
        ItemStack leftover = item.copy();
        for (int s = 0; s < this.storage.getSlots() && !leftover.isEmpty(); s++) {
            leftover = this.storage.insertItem(s, leftover, false);
        }
        if (!leftover.isEmpty()) {
            BlockPos pos = this.host.getTileEntity().getPos();
            net.minecraft.entity.item.EntityItem entityItem = new net.minecraft.entity.item.EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, leftover);
            world.spawnEntity(entityItem);
        }
    }

    /**
     * 将物品暂存到 storage slots,溢出则掉落.
     */
    private void stashItemsToStorage(World world, List<ItemStack> items) {
        for (ItemStack item : items) {
            stashItemToStorage(world, item);
        }
    }

    public boolean isBusy() {
        if (this.bindings.isEmpty()) {
            return false;
        }
        for (TargetBinding binding : this.bindings) {
            TargetSession session = this.sessions.get(binding);
            if (session == null || session.isIdle()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 对 UNAVAILABLE 目标定期进行有效性检查，若重新有效则恢复为 IDLE.
     * 防止目标方块短暂卸载/替换后该并行槽位永久冻结。
     */
    private void recoverUnavailableTargets() {
        World world = this.host.getTileEntity().getWorld();
        for (TargetBinding binding : this.bindings) {
            TargetSession session = this.sessions.get(binding);
            if (session == null || !session.isUnavailable()) {
                continue;
            }
            if (world.provider.getDimension() != binding.dimension) continue;
            if (!world.isBlockLoaded(binding.pos)) continue;

            IRemoteHandler handler = HandlerRegistry.findHandler(binding.blockId);
            if (handler == null) continue;

            if (handler.isValidTarget(world, binding.pos)) {
                session.recoverFromUnavailable();
            }
        }
    }

    // ---- Ticking ----

    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 5, !hasWorkToDo(), true);
    }

    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        AENetworkProxy proxy = this.host.getProxy();
        if (!proxy.isActive()) {
            return TickRateModulation.SLEEP;
        }

        // 先尝试恢复 UNAVAILABLE 目标：如果目标重新有效，则恢复为 IDLE
        recoverUnavailableTargets();

        boolean didWork = false;
        World world = this.host.getTileEntity().getWorld();
        int timeoutTicks = com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.centralInterface.processingTimeoutTicks;

        // 阶段 1：处理所有 PROCESSING 目标
        // 如果机器已经 idle，则进入 COLLECTING；否则尝试条件启动或检查超时
        for (TargetSession session : new ArrayList<>(this.sessions.values())) {
            if (!session.isProcessing()) continue;
            if (checkSessionTimeout(session, world, timeoutTicks)) continue;

            TargetBinding target = session.getBinding();
            if (world.provider.getDimension() != target.dimension) continue;
            if (!world.isBlockLoaded(target.pos)) continue;

            IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
            if (handler == null) {
                session.reset();
                continue;
            }

            List<ItemStack> inputs = session.getInputs();
            boolean idle = handler.isIdle(world, target.pos, inputs);
            if (!idle) {
                // 对于需要条件启动的设备(如符文祭坛),在 tick 中尝试启动
                handler.startProcess(world, target.pos, new appeng.me.helpers.MachineSource(this.host));
                continue;
            }

            session.beginCollect();
        }

        // 阶段 2：收集所有 COLLECTING 目标的产物
        for (TargetSession session : new ArrayList<>(this.sessions.values())) {
            if (!session.isCollecting()) continue;
            if (checkSessionTimeout(session, world, timeoutTicks)) continue;

            TargetBinding target = session.getBinding();
            if (world.provider.getDimension() != target.dimension) continue;
            if (!world.isBlockLoaded(target.pos)) continue;

            IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
            if (handler == null) {
                session.reset();
                continue;
            }

            IAEItemStack[] expected = session.getExpectedOutputs();
            List<ItemStack> inputs = session.getInputs();
            List<ItemStack> products = handler.collectProducts(world, target.pos, expected, inputs,
                    new appeng.me.helpers.MachineSource(this.host));

            // 收集流体产物
            List<ItemStack> fluidProducts = collectFluidProducts(world, target.pos, expected);
            if (!fluidProducts.isEmpty()) {
                products.addAll(fluidProducts);
            }

            if (!products.isEmpty()) {
                if (injectItemsToNetwork(proxy, world, products)) {
                    didWork = true;
                } else {
                    // 注入失败(网络断开等),产物暂存到 storage slots,避免丢失
                    stashItemsToStorage(world, products);
                }
            }

            // 流水线模式：只有 handler 报告输入已耗尽且无后续产物时,才结束本次发配
            boolean finished = handler.hasFinished(world, target.pos, inputs);
            session.finishCollect(finished);
            if (!products.isEmpty()) {
                didWork = true;
            }
        }

        // 注入待处理的虚拟合成产物(waitingFor 已注册,此时注入可被 CPU cluster 识别)
        if (!this.pendingVirtualProducts.isEmpty()) {
            List<ItemStack> toInject = new ArrayList<>(this.pendingVirtualProducts);
            this.pendingVirtualProducts.clear();
            if (injectItemsToNetwork(proxy, this.host.getTileEntity().getWorld(), toInject)) {
                didWork = true;
            } else {
                // 注入失败,暂存到 storage slots
                stashItemsToStorage(this.host.getTileEntity().getWorld(), toInject);
            }
        }

        // 将 storage slots 中的物品推入网络(如果有空间)
        pushStorageToNetwork(proxy);

        return hasWorkToDo()
                ? (didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER)
                : TickRateModulation.SLEEP;
    }

    /**
     * 检查指定 session 是否已超时。超时则紧急收集并强制回到 IDLE。
     *
     * @return true 表示已处理超时，调用方无需继续处理该 session
     */
    private boolean checkSessionTimeout(TargetSession session, World world, int timeoutTicks) {
        if (!session.isTimedOut(world.getTotalWorldTime(), timeoutTicks)) {
            return false;
        }

        TargetBinding target = session.getBinding();
        if (world.provider.getDimension() == target.dimension && world.isBlockLoaded(target.pos)) {
            IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
            if (handler != null) {
                IAEItemStack[] expected = session.getExpectedOutputs();
                List<ItemStack> inputs = session.getInputs();
                List<ItemStack> leftover = handler.collectProducts(world, target.pos, expected, inputs,
                        new appeng.me.helpers.MachineSource(this.host));
                List<ItemStack> leftoverFluids = collectFluidProducts(world, target.pos, expected);
                if (!leftoverFluids.isEmpty()) {
                    leftover.addAll(leftoverFluids);
                }
                if (!leftover.isEmpty()) {
                    stashItemsToStorage(world, leftover);
                }
            }
        }
        session.reset();
        return true;
    }

    private void pushStorageToNetwork(AENetworkProxy proxy) {
        try {
            IItemStorageChannel channel =
                    AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            for (int s = 0; s < this.storage.getSlots(); s++) {
                ItemStack stack = this.storage.getStackInSlot(s);
                if (stack.isEmpty()) continue;
                IAEItemStack notInserted = Platform.poweredInsert(
                        proxy.getEnergy(),
                        proxy.getStorage().getInventory(channel),
                        AEItemStack.fromItemStack(stack),
                        new appeng.me.helpers.MachineSource(this.host));
                if (notInserted == null || notInserted.getStackSize() == 0) {
                    this.storage.extractItem(s, stack.getCount(), false);
                } else {
                    int inserted = (int) (stack.getCount() - notInserted.getStackSize());
                    if (inserted > 0) {
                        this.storage.extractItem(s, inserted, false);
                    }
                }
            }
        } catch (appeng.me.GridAccessException e) {
            // 网络未连接,保持 storage 中
        }
    }

    // ---- 流体支持 ----

    /**
     * 将 table 中的流体假物品推送到目标的 IFluidHandler.
     * CPU 已事先将物品提取到 table 中,此处只做转换与推送,不再从网络提取.
     * 推送成功后,将对应槽位从 table 中清空,防止后续 handler.pushMaterials 再次处理.
     *
     * <p>按 {@link #FLUID_FACE_ORDER} 依次尝试各方向面,第一个能完整接受流体的面即被使用,
     * 避免向多个 face 重复推送或污染输出槽.</p>
     */
    private boolean pushFluidInputs(World world, BlockPos pos, InventoryCrafting table, List<FluidStack> pushedFluids) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return true;

        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            FluidStack fluid = Ae2fcFluidCompat.getFluidStack(stack);
            if (fluid == null) {
                // 兼容 ae2fc 的 ItemFluidPacket(旧版 fallback)
                String itemClass = stack.getItem().getClass().getName();
                if ("com.glodblock.github.common.item.ItemFluidPacket".equals(itemClass)) {
                    fluid = com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.unpackAe2fcFluid(stack);
                }
            }
            if (fluid == null) continue;

            boolean pushed = false;
            for (EnumFacing face : FLUID_FACE_ORDER) {
                if (tryFillFluidHandler(te, face, fluid, pushedFluids)) {
                    pushed = true;
                    break;
                }
            }

            if (!pushed) {
                return false;
            }

            // 从 table 中移除已推送的流体,避免 handler.pushMaterials 再次尝试插入 ItemFluidDrop
            table.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        return true;
    }

    private boolean tryFillFluidHandler(TileEntity te, EnumFacing face, FluidStack fluid, List<FluidStack> pushedFluids) {
        if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) return false;
        IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        if (fh == null) return false;
        int filled = fh.fill(fluid, false);
        if (filled >= fluid.amount) {
            fh.fill(fluid, true);
            pushedFluids.add(fluid.copy());
            return true;
        }
        return false;
    }

    /**
     * 回退已推送到目标 IFluidHandler 的流体.
     * 按 {@link #FLUID_FACE_ORDER} 依次尝试抽取,按精确 FluidStack 匹配回退.
     */
    private void revertPushedFluids(World world, BlockPos pos, List<FluidStack> fluids) {
        if (fluids.isEmpty()) return;
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return;
        for (FluidStack fluid : fluids) {
            for (EnumFacing face : FLUID_FACE_ORDER) {
                if (tryDrainFluidHandler(te, face, fluid)) break;
            }
        }
    }

    private boolean tryDrainFluidHandler(TileEntity te, EnumFacing face, FluidStack fluid) {
        if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) return false;
        IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        if (fh == null) return false;
        FluidStack drained = fh.drain(fluid, false);
        if (drained != null && drained.amount > 0) {
            // 只回退实际存在的量,避免过度抽取
            FluidStack toDrain = fluid.copy();
            toDrain.amount = Math.min(fluid.amount, drained.amount);
            fh.drain(toDrain, true);
            return true;
        }
        return false;
    }

    /**
     * 从目标的 IFluidHandler 收集流体产物.
     *
     * <p>按 {@link #FLUID_FACE_ORDER} 依次尝试各方向面，只收集预期产物，
     * 第一个含有足够量预期流体的 face 即被使用。不再无差别抽干所有 face，
     * 避免把输入槽/其他 target 共享槽中的材料抽回。</p>
     */
    private List<ItemStack> collectFluidProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs) {
        List<ItemStack> fluids = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return fluids;

        if (expectedOutputs != null) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null || expected.getStackSize() <= 0) continue;
                ItemStack stack = expected.createItemStack();
                FluidStack expectedFluid = extractFluidFromItemStack(stack);
                if (expectedFluid == null) continue;

                for (EnumFacing face : FLUID_FACE_ORDER) {
                    if (tryCollectExpectedFluid(te, face, expectedFluid, fluids)) {
                        break;
                    }
                }
            }
        }

        return fluids;
    }

    private FluidStack extractFluidFromItemStack(ItemStack stack) {
        FluidStack fluid = Ae2fcFluidCompat.getFluidStack(stack);
        if (fluid != null) {
            return fluid;
        }
        String itemClass = stack.getItem().getClass().getName();
        if ("com.glodblock.github.common.item.ItemFluidPacket".equals(itemClass)) {
            return com.github.aeddddd.ae2enhanced.util.fakeitem.FakeFluids.unpackAe2fcFluid(stack);
        }
        return null;
    }

    private boolean tryCollectExpectedFluid(TileEntity te, EnumFacing face, FluidStack expectedFluid, List<ItemStack> fluids) {
        if (!te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)) return false;
        IFluidHandler fh = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        if (fh == null) return false;
        FluidStack drained = fh.drain(expectedFluid, false);
        if (drained != null && drained.amount >= expectedFluid.amount) {
            fh.drain(expectedFluid, true);
            fluids.add(Ae2fcFluidCompat.createFluidDrop(expectedFluid));
            return true;
        }
        return false;
    }

    private boolean hasWorkToDo() {
        if (!this.pendingVirtualProducts.isEmpty()) {
            return true;
        }
        for (TargetSession session : this.sessions.values()) {
            if (!session.isIdle() && !session.isUnavailable()) {
                return true;
            }
        }
        return false;
    }

    // ---- Inventory Callbacks ----

    @Override
    public void saveChanges() {
        this.host.saveChanges();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.patterns) {
            updateCraftingList();
            this.host.saveChanges();
        } else if (inv == this.config) {
            this.host.saveChanges();
        } else if (inv == this.storage) {
            this.host.saveChanges();
        }
    }

    public boolean canInsert(ItemStack stack) {
        return true;
    }

    // ---- Crafting List Management ----

    public void initialize() {
        updateCraftingList();
        // 加载完成后,若存在持久化的处理状态,唤醒 tick 以继续收集
        if (hasWorkToDo()) {
            tryWakeTickDevice();
        }
    }

    private void tryWakeTickDevice() {
        try {
            AENetworkProxy proxy = this.host.getProxy();
            if (proxy.isActive()) {
                appeng.api.networking.ticking.ITickManager tm = proxy.getTick();
                if (tm != null) {
                    tm.wakeDevice(this.host.getProxy().getNode());
                }
            }
        } catch (appeng.me.GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to wake tick device for CentralInterface", e);
        }
    }

    private void updateCraftingList() {
        AENetworkProxy proxy = this.host.getProxy();

        boolean removed = false;
        boolean newPattern = false;

        if (this.craftingList == null) {
            this.craftingList = new HashSet<>();
        }

        boolean[] accountedFor = new boolean[this.patterns.getSlots()];

        Iterator<ICraftingPatternDetails> i = this.craftingList.iterator();
        while (i.hasNext()) {
            ICraftingPatternDetails details = i.next();
            boolean found = false;
            for (int x = 0; x < accountedFor.length; x++) {
                ItemStack stackInSlot = this.patterns.getStackInSlot(x);
                if (details.getPattern() == stackInSlot) {
                    found = true;
                    accountedFor[x] = true;
                    break;
                }
                // SmartPatternSubDetails: match by parent ItemSmartPattern
                if (details instanceof SmartPatternSubDetails) {
                    ItemStack parent = ((SmartPatternSubDetails) details).getPattern();
                    if (parent == stackInSlot) {
                        found = true;
                        accountedFor[x] = true;
                        break;
                    }
                }
            }
            if (!found) {
                removed = true;
                i.remove();
            }
        }

        for (int x = 0; x < accountedFor.length; x++) {
            if (!accountedFor[x]) {
                newPattern = true;
                addToCraftingList(this.patterns.getStackInSlot(x));
            }
        }

        // 网络就绪后才发送事件；若未就绪，craftingList 仍已完成重建，
        // 等网络恢复后 CraftingGridCache 的 provideCrafting 扫描即可发现配方。
        if (proxy.isReady() && (newPattern || removed)) {
            try {
                proxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this.host, proxy.getNode()));
            } catch (GridAccessException e) {
                // ignore
            }
        }
    }

    private void addToCraftingList(ItemStack stack) {
        if (stack.isEmpty()) return;
        // 智能样板展开
        if (stack.getItem() instanceof ItemSmartPattern) {
            World world = this.host.getTileEntity().getWorld();
            List<SmartPatternSubDetails> subs = ItemSmartPattern.expandPatterns(stack, world);
            if (subs != null) {
                this.craftingList.addAll(subs);
            }
            return;
        }
        if (!(stack.getItem() instanceof appeng.api.implementations.ICraftingPatternItem)) return;
        appeng.api.implementations.ICraftingPatternItem patternItem =
                (appeng.api.implementations.ICraftingPatternItem) stack.getItem();
        ICraftingPatternDetails details = patternItem.getPatternForItem(stack, this.host.getTileEntity().getWorld());
        if (details != null) {
            this.craftingList.add(details);
        }
    }

    // ---- Binding Management ----

    public List<TargetBinding> getBindings() {
        return Collections.unmodifiableList(this.bindings);
    }

    public String getBoundBlockId() {
        return this.boundBlockId;
    }

    public void addBinding(TargetBinding binding) {
        if (this.boundBlockId == null) {
            this.boundBlockId = binding.blockId;
        } else if (!this.boundBlockId.equals(binding.blockId)) {
            // 只允许绑定同种方块实体
            return;
        }
        if (!this.bindings.contains(binding)) {
            // 安全重置：若该目标曾经处于处理状态(来自持久化数据),先清理其运行时状态
            TargetSession session = this.sessions.get(binding);
            if (session != null) {
                session.reset();
            }

            this.bindings.add(binding);
            getOrCreateSession(binding); // 确保存在 IDLE session
            postPatternChangeEvent();
        }
    }

    public void removeBinding(TargetBinding binding) {
        // 若目标正在处理,先尝试紧急收集产物,避免移除绑定后产物无人接管
        TargetSession session = this.sessions.get(binding);
        if (session != null && !session.isIdle() && !session.isUnavailable()) {
            tryEmergencyCollect(binding);
            session.reset();
        }
        this.bindings.remove(binding);
        this.sessions.remove(binding);
        if (this.bindings.isEmpty()) {
            this.boundBlockId = null;
        }
        postPatternChangeEvent();
    }

    public void clearBindings() {
        // 批量移除前,先紧急收集所有正在处理目标的产物
        for (TargetSession session : new ArrayList<>(this.sessions.values())) {
            if (!session.isIdle() && !session.isUnavailable()) {
                tryEmergencyCollect(session.getBinding());
                session.reset();
            }
        }
        this.bindings.clear();
        this.sessions.clear();
        this.boundBlockId = null;
        TargetOwnershipTracker.instance().releaseAll(this);
        postPatternChangeEvent();
    }

    /**
     * 接口销毁时调用，释放所有持有的目标所有权。
     */
    public void destroy() {
        for (TargetSession session : new ArrayList<>(this.sessions.values())) {
            session.reset();
        }
        TargetOwnershipTracker.instance().releaseAll(this);
    }

    /** 紧急收集指定目标的产物(用于移除绑定前清理),收集失败则暂存到 storage slots */
    private void tryEmergencyCollect(TargetBinding binding) {
        TargetSession session = this.sessions.get(binding);
        if (session == null) return;

        try {
            World world = this.host.getTileEntity().getWorld();
            if (world == null || world.provider.getDimension() != binding.dimension) return;
            if (!world.isBlockLoaded(binding.pos)) return;

            IRemoteHandler handler = HandlerRegistry.findHandler(binding.blockId);
            if (handler == null) return;

            List<ItemStack> inputs = session.getInputs();
            IAEItemStack[] expected = session.getExpectedOutputs();

            List<ItemStack> products = handler.collectProducts(world, binding.pos, expected, inputs,
                    new appeng.me.helpers.MachineSource(this.host));
            List<ItemStack> fluidProducts = collectFluidProducts(world, binding.pos, expected);
            if (!fluidProducts.isEmpty()) {
                products.addAll(fluidProducts);
            }
            if (!products.isEmpty()) {
                stashItemsToStorage(world, products);
            }

            List<ItemStack> reverted = handler.revertMaterials(world, binding.pos,
                    new appeng.me.helpers.MachineSource(this.host));
            if (!reverted.isEmpty()) {
                stashItemsToStorage(world, reverted);
            }
            revertPushedFluids(world, binding.pos, Collections.emptyList());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Emergency collect failed for binding {}: {}", binding.pos, e.toString());
        }
    }

    private void postPatternChangeEvent() {
        try {
            AENetworkProxy proxy = this.host.getProxy();
            if (proxy.isReady()) {
                proxy.getGrid().postEvent(new appeng.api.networking.events.MENetworkCraftingPatternChange(this.host, proxy.getNode()));
            }
        } catch (appeng.me.GridAccessException e) {
            // ignore
        }
    }

    // ---- 默认单份材料发配工具方法 ----

    private InventoryCrafting copyInventoryCrafting(InventoryCrafting original) {
        InventoryCrafting copy = new InventoryCrafting(new net.minecraft.inventory.Container() {
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) { return false; }
        }, 3, 3);
        for (int i = 0; i < original.getSizeInventory(); i++) {
            ItemStack stack = original.getStackInSlot(i);
            if (!stack.isEmpty()) {
                copy.setInventorySlotContents(i, stack.copy());
            }
        }
        return copy;
    }

    private TileEntity getTargetTile(TargetBinding target) {
        World world = this.host.getTileEntity().getWorld();
        if (world.provider.getDimension() != target.dimension) return null;
        if (!world.isBlockLoaded(target.pos)) return null;
        TileEntity te = world.getTileEntity(target.pos);
        if (te == null) return null;
        return te;
    }

    private IRemoteHandler resolveHandler(String blockId) {
        return HandlerRegistry.findHandler(blockId);
    }

    // ---- NBT ----

    public void readFromNBT(NBTTagCompound data) {
        this.config.readFromNBT(data, "config");
        this.patterns.readFromNBT(data, "patterns");
        this.storage.readFromNBT(data, "storage");
        this.cm.readFromNBT(data);
        this.priority = data.getInteger("priority");

        // 绑定
        this.bindings.clear();
        this.sessions.clear();
        this.boundBlockId = data.hasKey("boundBlockId") ? data.getString("boundBlockId") : null;
        if (data.hasKey("bindings")) {
            NBTTagList list = data.getTagList("bindings", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                TargetBinding binding = TargetBinding.readFromNBT(list.getCompoundTagAt(i));
                this.bindings.add(binding);
                getOrCreateSession(binding); // 初始为 IDLE
            }
        }

        // 恢复运行时处理状态(区块卸载/重载后可以继续收集产物)
        readProcessingStateFromNBT(data);

        // 注意：不在 readFromNBT 时调用 updateCraftingList(),
        // 因为 SmartPattern 展开需要 world 对象,而 NBT 读取阶段 world 可能为 null.
        // craftingList 将在 TileCentralMEInterface.update() -> initialize() 中重建.
    }

    public void writeToNBT(NBTTagCompound data) {
        this.config.writeToNBT(data, "config");
        this.patterns.writeToNBT(data, "patterns");
        this.storage.writeToNBT(data, "storage");
        this.cm.writeToNBT(data);
        data.setInteger("priority", this.priority);

        // 绑定
        if (this.boundBlockId != null) {
            data.setString("boundBlockId", this.boundBlockId);
        }
        NBTTagList list = new NBTTagList();
        for (TargetBinding binding : this.bindings) {
            list.appendTag(binding.writeToNBT());
        }
        data.setTag("bindings", list);

        // 持久化运行时处理状态
        writeProcessingStateToNBT(data);
    }

    /**
     * 将正在运行的合成状态写入 NBT,以便区块卸载/服务器重启后恢复.
     */
    private void writeProcessingStateToNBT(NBTTagCompound data) {
        NBTTagList list = new NBTTagList();
        for (TargetSession session : this.sessions.values()) {
            // 只持久化 PROCESSING；COLLECTING 也按 PROCESSING 恢复（用户已同意）
            if (!session.isProcessing() && !session.isCollecting()) {
                continue;
            }
            list.appendTag(session.serializeProcessing());
        }
        if (list.tagCount() > 0) {
            data.setTag("processingState", list);
        } else {
            data.removeTag("processingState");
        }
    }

    /**
     * 从 NBT 恢复正在运行的合成状态.
     */
    private void readProcessingStateFromNBT(NBTTagCompound data) {
        if (!data.hasKey("processingState")) {
            return;
        }
        NBTTagList list = data.getTagList("processingState", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            TargetSession session = TargetSession.deserializeProcessing(entry, this);
            TargetBinding binding = session.getBinding();

            // 只恢复仍然存在于 bindings 列表中的目标
            if (!this.bindings.contains(binding)) {
                continue;
            }

            this.sessions.put(binding, session);
        }
    }

    // ---- Cleanup ----

    public void clearContents() {
        // 掉落物品并清空库存
        World world = this.host.getTileEntity().getWorld();
        BlockPos pos = this.host.getTileEntity().getPos();
        for (int i = 0; i < this.patterns.getSlots(); i++) {
            ItemStack stack = this.patterns.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, Collections.singletonList(stack));
                this.patterns.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < this.storage.getSlots(); i++) {
            ItemStack stack = this.storage.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, Collections.singletonList(stack));
                this.storage.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    public void onStackReturnedToNetwork(IAEItemStack stack) {
        // 空实现,备用
    }

    public IConfigManager getConfigManager() {
        return this.cm;
    }

    public AENetworkProxy getProxy() {
        return this.host.getProxy();
    }
}

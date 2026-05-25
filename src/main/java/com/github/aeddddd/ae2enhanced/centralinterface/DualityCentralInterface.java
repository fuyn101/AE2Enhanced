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
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * 中枢 ME 接口的核心逻辑类，复刻 AE2 {@link appeng.helpers.DualityInterface} 的结构。
 *
 * P1 阶段实现：
 * - 样板存储与 CraftingProvider 注册
 * - 远程目标绑定管理
 * - 单份材料发配（默认 fallback）：推送材料到目标 IItemHandler，tick 时收集产物
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
    private final Map<TargetBinding, TargetState> targetStates = new HashMap<>();
    // 记录每个目标当前正在合成的产物列表，用于 tick 收集时匹配
    private final Map<TargetBinding, IAEItemStack[]> pendingOutputs = new HashMap<>();

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
        this.targetStates.clear();
        this.pendingOutputs.clear();

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
        // 当前未实现红石状态检测和 unlockEvent 追踪，默认返回 NONE
        return LockCraftingMode.NONE;
    }

    // ---- Crafting Provider ----

    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        AENetworkProxy proxy = this.host.getProxy();
        if (proxy.isActive() && this.craftingList != null && !this.bindings.isEmpty()) {
            for (ICraftingPatternDetails details : this.craftingList) {
                details.setPriority(this.priority);
                // 每个 binding 注册一次，使 AE2 网络能并行调度多个相同配方
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

        TargetBinding target = findIdleTarget();
        if (target == null) {
            return false;
        }

        World world = this.host.getTileEntity().getWorld();
        if (world.provider.getDimension() != target.dimension) {
            return false;
        }

        IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
        if (handler == null) {
            return false;
        }
        if (!handler.isValidTarget(world, target.pos)) {
            this.targetStates.put(target, TargetState.UNAVAILABLE);
            return false;
        }

        // P7: 虚拟合成路径（Extended Crafting 工作台等）
        if (handler instanceof IVirtualCraftingHandler) {
            IVirtualCraftingHandler vh = (IVirtualCraftingHandler) handler;
            IAEItemStack[] outputs = patternDetails.getOutputs();
            if (vh.canCraftVirtually(world, target.pos, table, outputs)) {
                List<ItemStack> products = vh.virtualCraft(world, target.pos, table, outputs, new appeng.me.helpers.MachineSource(this.host));
                if (!products.isEmpty()) {
                    injectItemsToNetwork(proxy, world, products);
                }
                // 虚拟合成不占用物理设备，target 保持 IDLE，可立即复用
                try {
                    appeng.api.networking.ticking.ITickManager tm = proxy.getTick();
                    if (tm != null) {
                        tm.wakeDevice(this.host.getProxy().getNode());
                    }
                } catch (appeng.me.GridAccessException e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Failed to wake tick device for CentralInterface", e);
                }
                return true;
            }
            return false;
        }

        // 物理模式路径
        if (!handler.canStart(world, target.pos, table)) {
            return false;
        }

        boolean pushed = handler.pushMaterials(world, target.pos, table, new appeng.me.helpers.MachineSource(this.host));
        if (!pushed) {
            return false;
        }

        boolean started = handler.startProcess(world, target.pos, new appeng.me.helpers.MachineSource(this.host));
        if (!started) {
            return false;
        }

        IAEItemStack[] outputs = patternDetails.getOutputs();
        if (outputs != null && outputs.length > 0) {
            this.pendingOutputs.put(target, outputs);
        }

        this.targetStates.put(target, TargetState.PROCESSING);

        try {
            appeng.api.networking.ticking.ITickManager tm = proxy.getTick();
            if (tm != null) {
                tm.wakeDevice(this.host.getProxy().getNode());
            }
        } catch (appeng.me.GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to wake tick device for CentralInterface", e);
        }

        return true;
    }

    /**
     * 将物品列表注入 AE 网络，溢出部分先进入 storage slots，再溢出则掉落。
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
                    for (int s = 0; s < this.storage.getSlots() && !leftover.isEmpty(); s++) {
                        leftover = this.storage.insertItem(s, leftover, false);
                    }
                    if (!leftover.isEmpty()) {
                        BlockPos pos = this.host.getTileEntity().getPos();
                        net.minecraft.entity.item.EntityItem entityItem = new net.minecraft.entity.item.EntityItem(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, leftover);
                        world.spawnEntity(entityItem);
                    }
                }
            }
            return true;
        } catch (appeng.me.GridAccessException e) {
            AE2Enhanced.LOGGER.warn("[AE2E] CentralInterface failed to inject items to network", e);
            return false;
        }
    }

    public boolean isBusy() {
        if (this.bindings.isEmpty()) {
            return false;
        }
        for (TargetBinding binding : this.bindings) {
            TargetState state = this.targetStates.getOrDefault(binding, TargetState.IDLE);
            if (state == TargetState.IDLE) {
                return false;
            }
        }
        return true;
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

        // 检查 PROCESSING 目标，收集产物
        boolean didWork = false;
        Iterator<Map.Entry<TargetBinding, TargetState>> it = this.targetStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TargetBinding, TargetState> entry = it.next();
            if (entry.getValue() != TargetState.PROCESSING) continue;

            TargetBinding target = entry.getKey();
            World world = this.host.getTileEntity().getWorld();
            if (world.provider.getDimension() != target.dimension) continue;
            if (!world.isBlockLoaded(target.pos)) continue;

            IRemoteHandler handler = HandlerRegistry.findHandler(target.blockId);
            if (handler == null) {
                entry.setValue(TargetState.IDLE);
                this.pendingOutputs.remove(target);
                continue;
            }
            if (!handler.isIdle(world, target.pos)) {
                // 对于需要条件启动的设备（如符文祭坛），在 tick 中尝试启动
                handler.startProcess(world, target.pos, new appeng.me.helpers.MachineSource(this.host));
                continue;
            }

            IAEItemStack[] expected = this.pendingOutputs.get(target);
            List<ItemStack> products = handler.collectProducts(world, target.pos, expected, new appeng.me.helpers.MachineSource(this.host));

            if (!products.isEmpty()) {
                if (injectItemsToNetwork(proxy, world, products)) {
                    entry.setValue(TargetState.IDLE);
                    this.pendingOutputs.remove(target);
                    didWork = true;
                }
            } else {
                // 设备已 idle 但没有产物，重置状态允许重试
                entry.setValue(TargetState.IDLE);
                this.pendingOutputs.remove(target);
            }
        }

        // 将 storage slots 中的物品推入网络（如果有空间）
        pushStorageToNetwork(proxy);

        return hasWorkToDo()
                ? (didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER)
                : TickRateModulation.SLEEP;
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
            // 网络未连接，保持 storage 中
        }
    }

    private boolean hasWorkToDo() {
        for (TargetState state : this.targetStates.values()) {
            if (state == TargetState.PROCESSING) {
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
    }

    private void updateCraftingList() {
        AENetworkProxy proxy = this.host.getProxy();
        if (!proxy.isReady()) return;

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
                if (details.getPattern() == this.patterns.getStackInSlot(x)) {
                    found = true;
                    accountedFor[x] = true;
                    break;
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

        if (newPattern || removed) {
            try {
                proxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this.host, proxy.getNode()));
            } catch (GridAccessException e) {
                // ignore
            }
        }
    }

    private void addToCraftingList(ItemStack stack) {
        if (stack.isEmpty()) return;
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
            this.bindings.add(binding);
            this.targetStates.put(binding, TargetState.IDLE);
            postPatternChangeEvent();
        }
    }

    public void removeBinding(TargetBinding binding) {
        this.bindings.remove(binding);
        this.targetStates.remove(binding);
        this.pendingOutputs.remove(binding);
        if (this.bindings.isEmpty()) {
            this.boundBlockId = null;
        }
        postPatternChangeEvent();
    }

    public void clearBindings() {
        this.bindings.clear();
        this.targetStates.clear();
        this.pendingOutputs.clear();
        this.boundBlockId = null;
        postPatternChangeEvent();
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

    private TargetBinding findIdleTarget() {
        for (TargetBinding binding : this.bindings) {
            TargetState state = this.targetStates.getOrDefault(binding, TargetState.IDLE);
            if (state == TargetState.IDLE) {
                return binding;
            }
        }
        return null;
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
        this.targetStates.clear();
        this.pendingOutputs.clear();
        this.boundBlockId = data.hasKey("boundBlockId") ? data.getString("boundBlockId") : null;
        if (data.hasKey("bindings")) {
            NBTTagList list = data.getTagList("bindings", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                TargetBinding binding = TargetBinding.readFromNBT(list.getCompoundTagAt(i));
                this.bindings.add(binding);
                this.targetStates.put(binding, TargetState.IDLE);
            }
        }

        this.updateCraftingList();
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
        // 空实现，备用
    }

    public IConfigManager getConfigManager() {
        return this.cm;
    }

    public AENetworkProxy getProxy() {
        return this.host.getProxy();
    }
}

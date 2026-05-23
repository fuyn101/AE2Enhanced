package com.github.aeddddd.ae2enhanced.centralinterface;

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
    // 记录每个目标当前正在合成的产物，用于 tick 收集时匹配
    private final Map<TargetBinding, IAEItemStack> pendingOutputs = new HashMap<>();

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

    // ---- Crafting Provider ----

    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        AENetworkProxy proxy = this.host.getProxy();
        if (proxy.isActive() && this.craftingList != null) {
            for (ICraftingPatternDetails details : this.craftingList) {
                details.setPriority(this.priority);
                craftingTracker.addCraftingOption(this.host, details);
            }
        }
    }

    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        AENetworkProxy proxy = this.host.getProxy();
        if (!proxy.isActive() || this.craftingList == null
                || !this.craftingList.contains(patternDetails)) {
            return false;
        }

        // 找到第一个 IDLE 的目标
        TargetBinding target = findIdleTarget();
        if (target == null) {
            return false;
        }

        // P1：单份材料发配（默认 fallback）
        // 将 InventoryCrafting 中的物品推送到目标的 IItemHandler
        TileEntity te = getTargetTile(target);
        if (te == null) {
            this.targetStates.put(target, TargetState.UNAVAILABLE);
            return false;
        }

        // 收集所有非空物品
        List<ItemStack> toPush = new ArrayList<>();
        for (int i = 0; i < table.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i);
            if (!stack.isEmpty()) {
                toPush.add(stack.copy());
            }
        }

        // 推送到目标（遍历所有面，找到可以接收的 IItemHandler）
        boolean allPushed = true;
        for (ItemStack stack : toPush) {
            ItemStack remaining = pushItemToTarget(te, stack);
            if (!remaining.isEmpty()) {
                allPushed = false;
                break;
            }
        }

        if (!allPushed) {
            return false;
        }

        // 记录期望产物
        IAEItemStack[] outputs = patternDetails.getOutputs();
        if (outputs != null && outputs.length > 0 && outputs[0] != null) {
            this.pendingOutputs.put(target, outputs[0].copy());
        }

        this.targetStates.put(target, TargetState.PROCESSING);
        return true;
    }

    public boolean isBusy() {
        for (TargetState state : this.targetStates.values()) {
            if (state == TargetState.PROCESSING || state == TargetState.PUSHING || state == TargetState.COLLECTING) {
                return true;
            }
        }
        return false;
    }

    // ---- Ticking ----

    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(10, 60, !hasWorkToDo(), true);
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
            TileEntity te = getTargetTile(target);
            if (te == null) {
                entry.setValue(TargetState.UNAVAILABLE);
                continue;
            }

            IAEItemStack pending = this.pendingOutputs.get(target);
            if (pending == null) {
                entry.setValue(TargetState.IDLE);
                this.pendingOutputs.remove(target);
                continue;
            }

            // 尝试从目标收集产物
            ItemStack expected = pending.createItemStack();
            ItemStack collected = collectItemFromTarget(te, expected);
            if (!collected.isEmpty()) {
                // 注入网络
                try {
                    appeng.api.storage.channels.IItemStorageChannel channel =
                            appeng.api.AEApi.instance().storage().getStorageChannel(appeng.api.storage.channels.IItemStorageChannel.class);
                    Platform.poweredInsert(
                            proxy.getEnergy(),
                            proxy.getStorage().getInventory(channel),
                            appeng.util.item.AEItemStack.fromItemStack(collected),
                            new appeng.me.helpers.MachineSource(this.host));
                } catch (GridAccessException e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] CentralInterface failed to inject collected items", e);
                }
                entry.setValue(TargetState.IDLE);
                this.pendingOutputs.remove(target);
                didWork = true;
            }
        }

        return hasWorkToDo()
                ? (didWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER)
                : TickRateModulation.SLEEP;
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
            return; // 只允许同一种方块
        }
        if (!this.bindings.contains(binding)) {
            this.bindings.add(binding);
            this.targetStates.put(binding, TargetState.IDLE);
        }
    }

    public void removeBinding(TargetBinding binding) {
        this.bindings.remove(binding);
        this.targetStates.remove(binding);
        this.pendingOutputs.remove(binding);
        if (this.bindings.isEmpty()) {
            this.boundBlockId = null;
        }
    }

    public void clearBindings() {
        this.bindings.clear();
        this.targetStates.clear();
        this.pendingOutputs.clear();
        this.boundBlockId = null;
    }

    // ---- 默认单份材料发配工具方法 ----

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

    /**
     * 将单个物品推送到目标的 IItemHandler（遍历所有面）。
     * 返回未能推送的剩余物品。
     */
    private ItemStack pushItemToTarget(TileEntity target, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            if (remaining.isEmpty()) break;
        }
        return remaining;
    }

    /**
     * 从目标的 IItemHandler 中收集指定物品（遍历所有面）。
     * 返回实际收集到的物品。
     */
    private ItemStack collectItemFromTarget(TileEntity target, ItemStack expected) {
        ItemStack collected = ItemStack.EMPTY;
        int remainingAmount = expected.getCount();

        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots() && remainingAmount > 0; slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                if (!ItemStack.areItemsEqual(inSlot, expected)
                        || !ItemStack.areItemStackTagsEqual(inSlot, expected)) {
                    continue;
                }
                int toExtract = Math.min(remainingAmount, inSlot.getCount());
                ItemStack extracted = handler.extractItem(slot, toExtract, false);
                if (!extracted.isEmpty()) {
                    if (collected.isEmpty()) {
                        collected = extracted.copy();
                    } else {
                        collected.grow(extracted.getCount());
                    }
                    remainingAmount -= extracted.getCount();
                }
            }
            if (remainingAmount <= 0) break;
        }
        return collected;
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
        // 掉落物品
        World world = this.host.getTileEntity().getWorld();
        BlockPos pos = this.host.getTileEntity().getPos();
        for (int i = 0; i < this.patterns.getSlots(); i++) {
            ItemStack stack = this.patterns.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, Collections.singletonList(stack));
            }
        }
        for (int i = 0; i < this.storage.getSlots(); i++) {
            ItemStack stack = this.storage.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, Collections.singletonList(stack));
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

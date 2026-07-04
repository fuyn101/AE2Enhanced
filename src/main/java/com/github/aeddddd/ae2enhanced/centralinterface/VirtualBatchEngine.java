package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketVirtualCraftingParticles;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 虚拟批量合成引擎.
 *
 * <p>安装虚拟并行卡后，对支持 {@link HandlerCapabilities#VIRTUAL_BATCH} 的目标
 * 直接从网络扣除资源并返回产物，不占用物理设备。</p>
 */
public class VirtualBatchEngine {

    private final DualityCentralInterface owner;

    public VirtualBatchEngine(DualityCentralInterface owner) {
        this.owner = owner;
    }

    /**
     * 尝试对指定目标执行一次虚拟批量合成。
     *
     * @param proxy       网络代理
     * @param patternDetails 配方详情
     * @param originalTable 原始合成台（已包含 CPU 提取的第一份物品）
     * @param target      目标绑定
     * @param handler     批量虚拟合成 handler
     * @param maxParallel 卡片 tier 提供的最大并行数（Long.MAX_VALUE 表示无上限）
     * @return 实际完成的并行数，0 表示失败
     */
    public long execute(AENetworkProxy proxy,
                        ICraftingPatternDetails patternDetails,
                        InventoryCrafting originalTable,
                        TargetBinding target,
                        IVirtualBatchCraftingHandler handler,
                        long maxParallel) {
        World world = owner.host.getTileEntity().getWorld();
        if (world.provider.getDimension() != target.dimension) {
            logFail(world, target, "dimension mismatch");
            return 0;
        }

        if (owner.isOnGlobalVirtualCooldown()) {
            logFail(world, target, "on global virtual cooldown");
            return 0;
        }
        if (owner.isOnVirtualCooldown(target)) {
            logFail(world, target, "on target virtual cooldown");
            return 0;
        }
        TargetSession session = owner.getOrCreateSession(target);
        if (!session.isIdle()) {
            logFail(world, target, "session not idle (" + session.getState() + ")");
            return 0;
        }

        if (!handler.isValidTarget(world, target.pos)) {
            session.setUnavailable();
            logFail(world, target, "invalid target");
            return 0;
        }

        if (!TargetOwnershipTracker.instance().tryAcquire(target, owner)) {
            logFail(world, target, "ownership already held");
            return 0;
        }

        boolean ownershipAcquired = true;
        try {
            InventoryCrafting virtualTable = owner.copyInventoryCrafting(originalTable);
            IAEItemStack[] outputs = patternDetails.getOutputs();

            RecipeRemainInfo remainInfo = computeRemainInfo(virtualTable, world);
            if (remainInfo.hasAny()) {
                maxParallel = 1;
            }

            if (!handler.canCraftVirtually(world, target.pos, virtualTable, outputs, patternDetails)) {
                logFail(world, target, "canCraftVirtually returned false");
                return 0;
            }

            IStorageGrid storage;
            IEnergySource energy;
            try {
                storage = proxy.getStorage();
                energy = proxy.getEnergy();
            } catch (GridAccessException e) {
                logFail(world, target, "grid access exception: " + e.getMessage());
                return 0;
            }

            IMEInventory<IAEItemStack> itemSource = owner.getVirtualItemSource();
            long actualParallel = computeActualParallel(storage, energy, handler, world, target,
                    virtualTable, outputs, maxParallel, itemSource, patternDetails);
            if (actualParallel <= 0) {
                logFail(world, target, "computeActualParallel returned 0");
                return 0;
            }

            IActionSource source = new MachineSource(owner.host);
            List<IAEStack> netCosts = getNetCosts(handler, world, target, virtualTable,
                    outputs, actualParallel, patternDetails, remainInfo);
            if (netCosts == null) {
                logFail(world, target, "getNetCosts returned null for parallel=" + actualParallel);
                return 0;
            }

            if (!VirtualCostExtractor.simulateExtract(storage, netCosts, source, itemSource)) {
                logFail(world, target, "simulateExtract failed for parallel=" + actualParallel);
                return 0;
            }

            double energyCost = AE2EnhancedConfig.centralInterface.virtualParallelEnergyCost * (double) actualParallel;
            if (!VirtualCostExtractor.simulateExtractEnergy(energy, energyCost)) {
                logFail(world, target, "simulateExtractEnergy failed (need " + energyCost + " AE)");
                return 0;
            }

            List<IAEStack> extracted = VirtualCostExtractor.extractAll(storage, netCosts, source, itemSource);
            if (extracted == null) {
                logFail(world, target, "extractAll failed");
                return 0;
            }

            if (!VirtualCostExtractor.extractEnergy(energy, energyCost, source)) {
                VirtualCostExtractor.rollbackExtracted(storage, extracted, source, itemSource);
                logFail(world, target, "extractEnergy failed after resources extracted");
                return 0;
            }

            List<ItemStack> products = handler.virtualCraftBatch(world, target.pos, virtualTable,
                    outputs, actualParallel, source, patternDetails);
            if (products == null || products.isEmpty()) {
                logFail(world, target, "virtualCraftBatch returned no products");
                return 0;
            }

            products = mergeProducts(products);
            // 将催化剂、耐久物品等 remaining 产物返回到待注入队列
            for (ItemStack remaining : remainInfo.remaining) {
                if (!remaining.isEmpty()) {
                    products.add(remaining.copy());
                }
            }
            owner.pendingVirtualProducts.addAll(products);

            List<EnumParticleTypes> particleTypes = handler.getVirtualCraftingParticles(world, target.pos);
            int particleType = particleTypes.isEmpty()
                    ? EnumParticleTypes.PORTAL.getParticleID()
                    : particleTypes.get(world.rand.nextInt(particleTypes.size())).getParticleID();
            addParticleTarget(target.pos, particleType);

            owner.virtualCooldowns.put(target, AE2EnhancedConfig.centralInterface.virtualCooldownTargetTicks);
            owner.tryWakeTickDevice();
            return actualParallel;
        } finally {
            if (ownershipAcquired) {
                TargetOwnershipTracker.instance().release(target, owner);
            }
        }
    }

    /**
     * 处理虚拟产物的网络注入、粒子包发送、冷却递减。
     *
     * @return 是否在本 tick 中实际做了工作
     */
    public boolean tick(AENetworkProxy proxy) {
        boolean didWork = false;
        World world = owner.host.getTileEntity().getWorld();

        if (!owner.pendingVirtualProducts.isEmpty()) {
            List<ItemStack> toInject = new ArrayList<>(owner.pendingVirtualProducts);
            owner.pendingVirtualProducts.clear();
            if (owner.injectItemsToNetwork(proxy, world, toInject)) {
                didWork = true;
            } else {
                owner.stashItemsToStorage(world, toInject);
            }
        }

        if (sendParticlePackets(world)) {
            didWork = true;
        }

        return didWork;
    }

    /**
     * 计算当前网络资源可支撑的最大虚拟并行数。
     *
     * <p>直接按“单份成本 × 可用量”计算，不再使用 binary search，
     * 因此对 Long.MAX_VALUE 级别的并行卡也没有性能问题。</p>
     */
    private long computeActualParallel(IStorageGrid storage,
                                       IEnergySource energy,
                                       IVirtualBatchCraftingHandler handler,
                                       World world,
                                       TargetBinding target,
                                       InventoryCrafting virtualTable,
                                       IAEItemStack[] outputs,
                                       long maxParallel,
                                       IMEInventory<IAEItemStack> itemSource,
                                       ICraftingPatternDetails details) {
        MachineSource source = new MachineSource(owner.host);
        List<IAEStack> perCopy = handler.getVirtualCost(world, target.pos, virtualTable, outputs, 1, details);
        if (perCopy == null || perCopy.isEmpty()) {
            long actual = maxParallel;
            double perOp = AE2EnhancedConfig.centralInterface.virtualParallelEnergyCost;
            if (perOp > 0) {
                double availableEnergy = VirtualCostExtractor.queryAvailableEnergy(energy);
                actual = Math.min(actual, (long) (availableEnergy / perOp));
            }
            return actual > 0 ? actual : 0;
        }

        List<IAEStack> mergedCosts = mergeItemCosts(perCopy);

        long actual = maxParallel;
        for (IAEStack cost : mergedCosts) {
            if (cost == null || cost.getStackSize() <= 0) continue;
            long perCopySize = cost.getStackSize();
            long available = VirtualCostExtractor.queryAvailable(storage, cost, source, itemSource);
            long q = available / perCopySize;
            long supported = (q >= Long.MAX_VALUE - 1) ? Long.MAX_VALUE : q + 1;
            actual = Math.min(actual, supported);
            if (actual <= 0) return 0;
        }

        double perOp = AE2EnhancedConfig.centralInterface.virtualParallelEnergyCost;
        if (perOp > 0) {
            double availableEnergy = VirtualCostExtractor.queryAvailableEnergy(energy);
            actual = Math.min(actual, (long) (availableEnergy / perOp));
        }
        return actual > 0 ? actual : 0;
    }

    /**
     * 获取需要从网络额外提取的资源清单。
     *
     * <p>策略：以 handler 返回的 {@code parallel} 份总成本为权威值，
     * 减去 AE2 CPU 已经在 pushPattern 前提取的第一份成本。
     * 物品按单份成本扣减（因 CPU 已提取 1 份物品到 table），
     * Secondary 资源不会进入 table，因此不扣减。</p>
     *
     * <p>不直接读取 {@code virtualTable} 做扣减，避免 EC 配方 ingredient
     * 与样板输入之间 NBT 不完全一致导致少扣 1 份。</p>
     */
    private List<IAEStack> getNetCosts(IVirtualBatchCraftingHandler handler,
                                       World world,
                                       TargetBinding target,
                                       InventoryCrafting virtualTable,
                                       IAEItemStack[] outputs,
                                       long parallel,
                                       ICraftingPatternDetails details,
                                       RecipeRemainInfo remainInfo) {
        if (parallel <= 0) {
            return Collections.emptyList();
        }

        List<IAEStack> perCopy = handler.getVirtualCost(world, target.pos, virtualTable, outputs, 1, details);
        List<IAEStack> fullCosts = handler.getVirtualCost(world, target.pos, virtualTable, outputs, parallel, details);
        if (fullCosts == null || fullCosts.isEmpty()) {
            return Collections.emptyList();
        }

        List<IAEStack> net = new ArrayList<>();
        for (int i = 0; i < fullCosts.size(); i++) {
            IAEStack full = fullCosts.get(i);
            if (full == null) continue;
            long perCopySize = (perCopy != null && i < perCopy.size() && perCopy.get(i) != null)
                    ? perCopy.get(i).getStackSize()
                    : full.getStackSize() / parallel;
            long extra = full.getStackSize() - perCopySize;
            if (extra > 0) {
                IAEStack extraStack = full.copy();
                extraStack.setStackSize(extra);
                // 排除催化剂/耐久槽位对应的净成本
                if (!isExcludedItemCost(extraStack, remainInfo)) {
                    net.add(extraStack);
                }
            }
        }
        return net;
    }

    /**
     * 判断指定成本是否为已被识别为催化剂或耐久转换的槽位对应的物品成本。
     */
    private static boolean isExcludedItemCost(IAEStack cost, RecipeRemainInfo remainInfo) {
        if (!(cost instanceof IAEItemStack) || remainInfo == null || !remainInfo.hasAny()) {
            return false;
        }
        ItemStack costStack = ((IAEItemStack) cost).createItemStack();
        for (int i = 0; i < remainInfo.inputs.size(); i++) {
            if (!remainInfo.catalystSlots.get(i) && !remainInfo.transformSlots.get(i)) {
                continue;
            }
            ItemStack input = remainInfo.inputs.get(i);
            if (!input.isEmpty()
                    && ItemStack.areItemsEqual(costStack, input)
                    && costStack.getMetadata() == input.getMetadata()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 合并产物列表中可堆叠的相同物品。
     */
    private List<ItemStack> mergeProducts(List<ItemStack> products) {
        if (products == null || products.size() <= 1) {
            return products;
        }
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack incoming : products) {
            if (incoming.isEmpty()) continue;
            boolean found = false;
            for (ItemStack existing : merged) {
                if (ItemStack.areItemsEqual(existing, incoming) && ItemStack.areItemStackTagsEqual(existing, incoming)) {
                    int canAdd = Math.min(incoming.getCount(), existing.getMaxStackSize() - existing.getCount());
                    existing.grow(canAdd);
                    if (canAdd < incoming.getCount()) {
                        ItemStack leftover = incoming.copy();
                        leftover.setCount(incoming.getCount() - canAdd);
                        merged.add(leftover);
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.add(incoming.copy());
            }
        }
        return merged;
    }

    /**
     * 记录虚拟合成失败原因（无操作占位，避免日志刷屏）。
     */
    private void logFail(World world, TargetBinding target, String reason) {
    }

    private void addParticleTarget(BlockPos pos, int particleType) {
        int color = 0xFFFFFFFF;
        if (particleType == EnumParticleTypes.PORTAL.getParticleID()) color = 0xFFAA55FF;
        else if (particleType == EnumParticleTypes.ENCHANTMENT_TABLE.getParticleID()) color = 0xFF55AAFF;
        else if (particleType == EnumParticleTypes.SPELL_WITCH.getParticleID()) color = 0xFFFF55FF;
        else if (particleType == EnumParticleTypes.END_ROD.getParticleID()) color = 0xFFFFFFFF;

        int maxTargets = AE2EnhancedConfig.centralInterface.virtualParticleMaxTargets;
        if (owner.activeParticleTargets.size() >= maxTargets) {
            owner.activeParticleTargets.remove(0);
        }
        owner.activeParticleTargets.add(new DualityCentralInterface.VirtualParticleTarget(
                pos, particleType, color, AE2EnhancedConfig.centralInterface.virtualParticleDurationTicks));
    }

    private boolean sendParticlePackets(World world) {
        if (owner.activeParticleTargets.isEmpty()) {
            return false;
        }
        if (world == null || world.isRemote) {
            return false;
        }

        int countPerTick = AE2EnhancedConfig.centralInterface.virtualParticleCountPerTick;
        int renderDistance = AE2EnhancedConfig.centralInterface.virtualParticleRenderDistance;
        int renderDistanceSq = renderDistance * renderDistance;

        List<PacketVirtualCraftingParticles.ParticleTarget> packetTargets = new ArrayList<>();
        Iterator<DualityCentralInterface.VirtualParticleTarget> it = owner.activeParticleTargets.iterator();
        while (it.hasNext()) {
            DualityCentralInterface.VirtualParticleTarget target = it.next();
            target.remainingTicks--;
            if (target.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            packetTargets.add(new PacketVirtualCraftingParticles.ParticleTarget(
                    target.pos, target.particleType, countPerTick, target.color));
        }

        if (packetTargets.isEmpty()) {
            return false;
        }

        PacketVirtualCraftingParticles packet = new PacketVirtualCraftingParticles(packetTargets);
        BlockPos interfacePos = owner.host.getTileEntity().getPos();
        for (net.minecraft.entity.player.EntityPlayerMP player : world.getPlayers(net.minecraft.entity.player.EntityPlayerMP.class,
                p -> p.getDistanceSq(interfacePos) <= renderDistanceSq)) {
            AE2Enhanced.network.sendTo(packet, player);
        }
        return true;
    }

    /**
     * 将 IAEStack 列表中的 IAEItemStack 按物品类型合并，
     * 避免同种物品分散在多个 crafting slot 时被重复计算并行数。
     */
    private static List<IAEStack> mergeItemCosts(List<IAEStack> costs) {
        if (costs == null || costs.isEmpty()) {
            return Collections.emptyList();
        }
        Map<ItemCostKey, Long> itemSums = new HashMap<>();
        List<IAEStack> others = new ArrayList<>();
        for (IAEStack cost : costs) {
            if (cost == null || cost.getStackSize() <= 0) continue;
            if (cost instanceof IAEItemStack) {
                ItemCostKey key = new ItemCostKey(((IAEItemStack) cost).createItemStack());
                itemSums.merge(key, cost.getStackSize(), Long::sum);
            } else {
                others.add(cost);
            }
        }
        List<IAEStack> merged = new ArrayList<>(others);
        for (Map.Entry<ItemCostKey, Long> entry : itemSums.entrySet()) {
            IAEItemStack stack = AEItemStack.fromItemStack(entry.getKey().stack.copy());
            if (stack != null) {
                stack.setStackSize(entry.getValue());
                merged.add(stack);
            }
        }
        return merged;
    }

    private static final class ItemCostKey {
        private final ItemStack stack;

        ItemCostKey(ItemStack stack) {
            this.stack = stack.copy();
            this.stack.setCount(1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ItemCostKey)) return false;
            ItemStack other = ((ItemCostKey) o).stack;
            return ItemStack.areItemsEqual(this.stack, other)
                    && ItemStack.areItemStackTagsEqual(this.stack, other);
        }

        @Override
        public int hashCode() {
            net.minecraft.util.ResourceLocation regName = this.stack.getItem().getRegistryName();
            int result = regName != null ? regName.hashCode() : System.identityHashCode(this.stack.getItem());
            result = 31 * result + this.stack.getMetadata();
            if (this.stack.hasTagCompound()) {
                result = 31 * result + this.stack.getTagCompound().hashCode();
            }
            return result;
        }
    }

    /**
     * 记录配方 getRemainingItems 分析结果：催化剂槽位、耐久/转换槽位、
     * 原始输入快照以及剩余物品快照。
     */
    public static final class RecipeRemainInfo {
        public final BitSet catalystSlots;
        public final BitSet transformSlots;
        public NonNullList<ItemStack> inputs;
        public NonNullList<ItemStack> remaining;

        RecipeRemainInfo(int slotCount) {
            this.catalystSlots = new BitSet(slotCount);
            this.transformSlots = new BitSet(slotCount);
            this.inputs = NonNullList.withSize(slotCount, ItemStack.EMPTY);
            this.remaining = NonNullList.withSize(slotCount, ItemStack.EMPTY);
        }

        public boolean hasAny() {
            return catalystSlots.cardinality() > 0 || transformSlots.cardinality() > 0;
        }
    }

    /**
     * 通过 {@link IRecipe#getRemainingItems(InventoryCrafting)} 识别催化剂和耐久/转换槽位。
     */
    public static RecipeRemainInfo computeRemainInfo(InventoryCrafting table, World world) {
        int size = table.getSizeInventory();
        RecipeRemainInfo info = new RecipeRemainInfo(size);
        for (int i = 0; i < size; i++) {
            info.inputs.set(i, table.getStackInSlot(i).copy());
        }

        IRecipe recipe = CraftingManager.findMatchingRecipe(table, world);
        if (recipe == null) {
            return info;
        }

        NonNullList<ItemStack> remaining = recipe.getRemainingItems(table);
        for (int i = 0; i < size && i < remaining.size(); i++) {
            info.remaining.set(i, remaining.get(i));
        }

        for (int i = 0; i < size && i < remaining.size(); i++) {
            ItemStack input = table.getStackInSlot(i);
            ItemStack rem = remaining.get(i);
            if (rem.isEmpty()) continue;
            if (ItemStack.areItemsEqual(input, rem) && input.getMetadata() == rem.getMetadata()) {
                if (areNbtEquivalent(input.getTagCompound(), rem.getTagCompound())) {
                    info.catalystSlots.set(i); // 真催化剂：NBT 完全不变
                } else if (!input.isItemStackDamageable()) {
                    // 不可损坏但 NBT 有差异：仍视为催化剂（某些 mod 的 NBT 状态）
                    info.catalystSlots.set(i);
                } else if (input.getItem().hasContainerItem(input)
                        && ItemStack.areItemStacksEqual(input.getItem().getContainerItem(input), rem)) {
                    // getContainerItem 明确返回同一物品：视为催化剂
                    info.catalystSlots.set(i);
                } else {
                    // 同一物品但 NBT/耐久变化：消耗性转换（如损坏的工具）
                    info.transformSlots.set(i);
                }
            }
        }

        return info;
    }

    /**
     * 宽松比较两个 NBT：null 与空 tag 视为等价。
     */
    private static boolean areNbtEquivalent(NBTTagCompound a, NBTTagCompound b) {
        if (Objects.equals(a, b)) return true;
        if (a == null) return b == null || b.getKeySet().isEmpty();
        if (b == null) return a.getKeySet().isEmpty();
        return false;
    }

    /**
     * 判断指定配方是否包含催化剂或耐久/转换物品。
     */
    public static boolean hasTransformItems(InventoryCrafting table, World world) {
        return computeRemainInfo(table, world).hasAny();
    }

    /**
     * 判断 handler 返回的虚拟成本中是否包含非物品 IAEStack（如流体、能量、Mana 等）。
     */
    public static boolean hasMixedStackTypes(IVirtualBatchCraftingHandler handler,
                                              World world,
                                              TargetBinding target,
                                              InventoryCrafting table,
                                              IAEItemStack[] outputs,
                                              ICraftingPatternDetails details) {
        List<IAEStack> costs = handler.getVirtualCost(world, target.pos, table, outputs, 1, details);
        if (costs == null || costs.isEmpty()) return false;
        for (IAEStack cost : costs) {
            if (cost != null && !(cost instanceof IAEItemStack)) {
                return true;
            }
        }
        return false;
    }
}

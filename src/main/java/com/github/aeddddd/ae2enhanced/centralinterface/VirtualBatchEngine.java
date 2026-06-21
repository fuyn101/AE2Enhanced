package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.network.packet.PacketVirtualCraftingParticles;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 虚拟批量合成引擎.
 *
 * <p>安装虚拟并行卡后，对支持 {@link HandlerCapabilities#VIRTUAL_BATCH} 的目标
 * 直接从网络扣除资源并返回产物，不占用物理设备。</p>
 */
public class VirtualBatchEngine {

    private final DualityCentralInterface owner;

    /**
     * 失败日志节流：每个目标每 100 tick 最多输出一次，避免日志刷屏。
     */
    private final Map<TargetBinding, Long> lastFailLogTicks = new HashMap<>();

    public VirtualBatchEngine(DualityCentralInterface owner) {
        this.owner = owner;
    }

    /**
     * 尝试对指定目标执行一次虚拟批量合成。
     *
     * @param proxy       网络代理
     * @param patternDetails 配方详情
     * @param originalTable 原始合成台
     * @param target      目标绑定
     * @param handler     批量虚拟合成 handler
     * @param maxParallel 卡片 tier 提供的最大并行数
     * @return true 表示成功完成至少 1 份虚拟合成
     */
    public boolean execute(AENetworkProxy proxy,
                           ICraftingPatternDetails patternDetails,
                           InventoryCrafting originalTable,
                           TargetBinding target,
                           IVirtualBatchCraftingHandler handler,
                           int maxParallel) {
        World world = owner.host.getTileEntity().getWorld();
        if (world.provider.getDimension() != target.dimension) {
            logFail(world, target, "dimension mismatch");
            return false;
        }

        // 防御性冷却/所有权检查
        if (owner.isOnGlobalVirtualCooldown()) {
            logFail(world, target, "on global virtual cooldown");
            return false;
        }
        if (owner.isOnVirtualCooldown(target)) {
            logFail(world, target, "on target virtual cooldown");
            return false;
        }
        TargetSession session = owner.getOrCreateSession(target);
        if (!session.isIdle()) {
            logFail(world, target, "session not idle (" + session.getState() + ")");
            return false;
        }

        if (!handler.isValidTarget(world, target.pos)) {
            session.setUnavailable();
            logFail(world, target, "invalid target");
            return false;
        }

        if (!TargetOwnershipTracker.instance().tryAcquire(target, owner)) {
            logFail(world, target, "ownership already held");
            return false;
        }

        boolean ownershipAcquired = true;
        try {
            InventoryCrafting virtualTable = owner.copyInventoryCrafting(originalTable);
            IAEItemStack[] outputs = patternDetails.getOutputs();

            if (!handler.canCraftVirtually(world, target.pos, virtualTable, outputs)) {
                logFail(world, target, "canCraftVirtually returned false");
                return false;
            }

            IStorageGrid storage;
            IEnergySource energy;
            try {
                storage = proxy.getStorage();
                energy = proxy.getEnergy();
            } catch (GridAccessException e) {
                logFail(world, target, "grid access exception: " + e.getMessage());
                return false;
            }

            // 先计算 parallel=1 时的 netCosts，用于失败诊断；实际执行时再按 actualParallel 重新计算。
            List<IAEStack> netCosts = getNetCosts(handler, world, target, virtualTable, outputs, 1);

            int actualParallel = computeActualParallel(storage, handler, world, target, virtualTable, outputs, maxParallel);
            if (actualParallel <= 0) {
                logFail(world, target, "computeActualParallel returned 0 for netCosts=[" + formatCosts(netCosts) + "]");
                return false;
            }

            IActionSource source = new MachineSource(owner.host);

            // AE2 CPU 已经把第一份物品材料从网络提取到 table 中传入 pushPattern，
            // 因此虚拟合成不应再向网络索取这一份物品；只对额外的 (parallel-1) 份物品
            // 以及全部 secondary 资源（RF/Mana/Starlight/流体等）进行网络提取。
            netCosts = getNetCosts(handler, world, target, virtualTable, outputs, actualParallel);
            if (netCosts == null) {
                logFail(world, target, "getNetCosts returned null for parallel=" + actualParallel);
                return false;
            }

            // 模拟提取
            if (!VirtualCostExtractor.simulateExtract(storage, netCosts, source)) {
                logFail(world, target, "simulateExtract failed for parallel=" + actualParallel + " netCosts=" + netCosts.size());
                return false;
            }

            // 模拟能量
            double energyCost = AE2EnhancedConfig.centralInterface.virtualParallelEnergyCost * actualParallel;
            if (!VirtualCostExtractor.simulateExtractEnergy(energy, energyCost)) {
                logFail(world, target, "simulateExtractEnergy failed (need " + energyCost + " AE)");
                return false;
            }

            // 实际提取资源（仅 extra 物品 + secondary 资源）
            List<IAEStack> extracted = VirtualCostExtractor.extractAll(storage, netCosts, source);
            if (extracted == null) {
                logFail(world, target, "extractAll failed");
                return false;
            }

            // 扣除能量
            if (!VirtualCostExtractor.extractEnergy(energy, energyCost, source)) {
                // 资源已扣但能量不足：理论上前面已模拟，出现竞态时回滚资源
                VirtualCostExtractor.rollbackExtracted(storage, extracted, source);
                logFail(world, target, "extractEnergy failed after resources extracted");
                return false;
            }

            // 执行虚拟合成
            List<ItemStack> products = handler.virtualCraftBatch(world, target.pos, virtualTable, outputs, actualParallel, source);
            if (products == null || products.isEmpty()) {
                // 产物为空视为已消耗，不回滚
                logFail(world, target, "virtualCraftBatch returned no products");
                return false;
            }

            int totalProductCount = 0;
            for (ItemStack p : products) {
                if (!p.isEmpty()) totalProductCount += p.getCount();
            }
            AE2Enhanced.LOGGER.info("[AE2E-VirtualBatch] products {} at {} parallel={} productStacks={} totalItems={}",
                    target.blockId, target.pos, actualParallel, products.size(), totalProductCount);

            products = mergeProducts(products);
            owner.pendingVirtualProducts.addAll(products);

            // 粒子效果
            List<EnumParticleTypes> particleTypes = handler.getVirtualCraftingParticles(world, target.pos);
            int particleType = particleTypes.isEmpty()
                    ? EnumParticleTypes.PORTAL.getParticleID()
                    : particleTypes.get(world.rand.nextInt(particleTypes.size())).getParticleID();
            addParticleTarget(target.pos, particleType);

            // 进入冷却
            owner.virtualCooldowns.put(target, AE2EnhancedConfig.centralInterface.virtualCooldownTargetTicks);
            owner.tryWakeTickDevice();
            AE2Enhanced.LOGGER.info("[AE2E-VirtualBatch] SUCCESS {} at {} parallel={} productStacks={} totalItems={}",
                    target.blockId, target.pos, actualParallel, products.size(), totalProductCount);
            return true;
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

        // 注入待处理的虚拟合成产物
        if (!owner.pendingVirtualProducts.isEmpty()) {
            List<ItemStack> toInject = new ArrayList<>(owner.pendingVirtualProducts);
            owner.pendingVirtualProducts.clear();
            int stacks = toInject.size();
            long total = 0;
            for (ItemStack s : toInject) {
                if (!s.isEmpty()) total += s.getCount();
            }
            AE2Enhanced.LOGGER.info("[AE2E-VirtualBatch] injecting {} stacks / {} items", stacks, total);
            if (owner.injectItemsToNetwork(proxy, world, toInject)) {
                didWork = true;
            } else {
                AE2Enhanced.LOGGER.warn("[AE2E-VirtualBatch] injectItemsToNetwork failed, stashing {} stacks", stacks);
                owner.stashItemsToStorage(world, toInject);
            }
        }

        // 发送粒子包
        if (sendParticlePackets(world)) {
            didWork = true;
        }

        return didWork;
    }

    /**
     * 计算当前网络资源可支撑的最大虚拟并行数。
     *
     * <p>注意：AE2 CPU 已经把第一份物品材料从网络提取并传入 pushPattern 的 table，
     * 因此这里只对额外的 (parallel-1) 份物品以及全部 secondary 资源进行模拟提取。</p>
     */
    private int computeActualParallel(IStorageGrid storage,
                                      IVirtualBatchCraftingHandler handler,
                                      World world,
                                      TargetBinding target,
                                      InventoryCrafting virtualTable,
                                      IAEItemStack[] outputs,
                                      int maxParallel) {
        MachineSource source = new MachineSource(owner.host);
        int cap = Math.min(maxParallel, AE2EnhancedConfig.centralInterface.virtualParallelMaxCap);
        if (cap <= 0) return 0;

        if (!AE2EnhancedConfig.centralInterface.virtualParallelPartialExecution) {
            List<IAEStack> netCosts = getNetCosts(handler, world, target, virtualTable, outputs, cap);
            return VirtualCostExtractor.simulateExtract(storage, netCosts, source) ? cap : 0;
        }

        int low = 1;
        int high = cap;
        int best = 0;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            List<IAEStack> netCosts = getNetCosts(handler, world, target, virtualTable, outputs, mid);
            if (VirtualCostExtractor.simulateExtract(storage, netCosts, source)) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    /**
     * 获取需要从网络额外提取的资源清单。
     *
     * <p>策略：
     * <ul>
     *   <li>物品（IAEItemStack）: 只提取 (parallel-1) 份，因为第 1 份已由 CPU 提供。</li>
     *   <li>Secondary 资源（Mana/Starlight/RF/流体/气体/源质等）: 提取 parallel 份。</li>
     * </ul></p>
     */
    private List<IAEStack> getNetCosts(IVirtualBatchCraftingHandler handler,
                                       World world,
                                       TargetBinding target,
                                       InventoryCrafting virtualTable,
                                       IAEItemStack[] outputs,
                                       int parallel) {
        if (parallel <= 0) {
            return Collections.emptyList();
        }

        List<IAEStack> secondaryCosts = new ArrayList<>();
        List<IAEStack> fullCosts = handler.getVirtualCost(world, target.pos, virtualTable, outputs, parallel);
        if (fullCosts != null) {
            for (IAEStack cost : fullCosts) {
                if (cost == null) continue;
                if (!(cost instanceof IAEItemStack)) {
                    secondaryCosts.add(cost);
                }
            }
        }

        List<IAEStack> itemCosts = Collections.emptyList();
        if (parallel > 1) {
            itemCosts = new ArrayList<>();
            List<IAEStack> costsForExtra = handler.getVirtualCost(world, target.pos, virtualTable, outputs, parallel - 1);
            if (costsForExtra != null) {
                for (IAEStack cost : costsForExtra) {
                    if (cost instanceof IAEItemStack) {
                        itemCosts.add(cost);
                    }
                }
            }
        }

        if (secondaryCosts.isEmpty()) {
            return itemCosts;
        }
        if (itemCosts.isEmpty()) {
            return secondaryCosts;
        }

        List<IAEStack> combined = new ArrayList<>(secondaryCosts.size() + itemCosts.size());
        combined.addAll(secondaryCosts);
        combined.addAll(itemCosts);
        return combined;
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
     * 记录虚拟合成失败原因，按目标节流，避免高频日志刷屏。
     */
    private void logFail(World world, TargetBinding target, String reason) {
        long tick = world.getTotalWorldTime();
        Long last = lastFailLogTicks.get(target);
        if (last != null && tick - last < 100) {
            return;
        }
        lastFailLogTicks.put(target, tick);
        AE2Enhanced.LOGGER.info("[AE2E-VirtualBatch] FAIL {} at {}: {}", target.blockId, target.pos, reason);
    }

    private String formatCosts(List<IAEStack> costs) {
        if (costs == null || costs.isEmpty()) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder();
        for (IAEStack cost : costs) {
            if (cost == null) continue;
            if (sb.length() > 0) sb.append(", ");
            String cls = cost.getClass().getSimpleName();
            if (cost instanceof appeng.api.storage.data.IAEItemStack) {
                appeng.api.storage.data.IAEItemStack is = (appeng.api.storage.data.IAEItemStack) cost;
                sb.append(cls).append("(").append(is.getItem().getRegistryName()).append(" x").append(is.getStackSize()).append(")");
            } else {
                sb.append(cls).append("(x").append(cost.getStackSize()).append(")");
            }
        }
        return sb.toString();
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
}

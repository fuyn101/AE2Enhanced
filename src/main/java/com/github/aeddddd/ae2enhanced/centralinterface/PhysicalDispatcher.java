package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 物理发配调度器.
 *
 * <p>负责把单个样板的材料实际推送到目标机器，并在失败时回退材料和流体。
 * 成功后目标进入 {@link TargetSession} 的 PROCESSING 状态，由
 * {@link DualityCentralInterface#tickingRequest} 继续 tick 收集产物。</p>
 */
public class PhysicalDispatcher {

    private final DualityCentralInterface owner;

    public PhysicalDispatcher(DualityCentralInterface owner) {
        this.owner = owner;
    }

    /**
     * 尝试对指定目标执行一次物理发配。
     *
     * @return true 表示推送成功
     */
    public boolean dispatch(AENetworkProxy proxy,
                            ICraftingPatternDetails patternDetails,
                            InventoryCrafting originalTable,
                            TargetBinding target,
                            IRemoteHandler handler) {
        World world = owner.host.getTileEntity().getWorld();
        if (world.provider.getDimension() != target.dimension) {
            return false;
        }

        TargetSession session = owner.getOrCreateSession(target);

        // 每个 target 同一时刻只能有一份材料在途
        if (!session.isIdle()) {
            return false;
        }

        if (!handler.isValidTarget(world, target.pos)) {
            session.setUnavailable();
            return false;
        }

        // 复制 table，避免一个 target 的失败影响其他 target
        InventoryCrafting table = owner.copyInventoryCrafting(originalTable);
        IActionSource source = new MachineSource(owner.host);

        // 获取全局坐标所有权，进入 PUSHING 状态
        List<FluidStack> pushedFluids = new ArrayList<>();
        if (!session.beginPush(pushedFluids)) {
            return false;
        }

        boolean success = false;
        try {
            // 推送流体输入（如果配方包含流体）
            if (!owner.pushFluidInputs(world, target.pos, table, pushedFluids)) {
                owner.revertPushedFluids(world, target.pos, pushedFluids);
                return false;
            }

            // 发配前回收目标输出槽残留内容
            List<ItemStack> clearedOutputs = handler.clearOutputs(world, target.pos, source);
            if (!clearedOutputs.isEmpty()) {
                if (!owner.injectItemsToNetwork(proxy, world, clearedOutputs)) {
                    owner.stashItemsToStorage(world, clearedOutputs);
                }
            }

            if (!handler.canStart(world, target.pos, table)) {
                owner.revertPushedFluids(world, target.pos, pushedFluids);
                return false;
            }

            if (!handler.pushMaterials(world, target.pos, table, source)) {
                List<ItemStack> reverted = handler.revertMaterials(world, target.pos, source);
                if (!reverted.isEmpty()) {
                    if (!owner.injectItemsToNetwork(proxy, world, reverted)) {
                        owner.stashItemsToStorage(world, reverted);
                    }
                }
                owner.revertPushedFluids(world, target.pos, pushedFluids);
                return false;
            }

            if (!handler.startProcess(world, target.pos, source)) {
                List<ItemStack> reverted = handler.revertMaterials(world, target.pos, source);
                if (!reverted.isEmpty()) {
                    if (!owner.injectItemsToNetwork(proxy, world, reverted)) {
                        owner.stashItemsToStorage(world, reverted);
                    }
                }
                owner.revertPushedFluids(world, target.pos, pushedFluids);
                return false;
            }

            IAEItemStack[] outputs = patternDetails.getOutputs();

            // 保存输入材料快照（pushFluidInputs 后 table 中已移除流体，剩余为物品材料）
            List<ItemStack> inputSnapshot = new ArrayList<>();
            for (int i = 0; i < table.getSizeInventory(); i++) {
                ItemStack stack = table.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    inputSnapshot.add(stack.copy());
                }
            }

            session.commitPush(outputs, inputSnapshot, pushedFluids, world.getTotalWorldTime());
            success = true;
            owner.tryWakeTickDevice();
            return true;
        } finally {
            if (!success) {
                session.reset();
            }
        }
    }

    /**
     * 处理所有处于 PROCESSING / COLLECTING 状态的物理 session。
     *
     * @return 是否在本 tick 中实际做了收集/推进工作
     */
    public boolean tick(AENetworkProxy proxy, World world, int timeoutTicks) {
        boolean didWork = false;

        // 阶段 1：PROCESSING -> COLLECTING
        for (TargetSession session : new ArrayList<>(owner.sessions.values())) {
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
            if (!handler.isIdle(world, target.pos, inputs)) {
                // 需要条件启动的设备在 tick 中尝试启动
                handler.startProcess(world, target.pos, new MachineSource(owner.host));
                continue;
            }

            session.beginCollect();
        }

        // 阶段 2：收集 COLLECTING 产物
        for (TargetSession session : new ArrayList<>(owner.sessions.values())) {
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

            IActionSource source = new MachineSource(owner.host);
            IAEItemStack[] expected = session.getExpectedOutputs();
            List<ItemStack> inputs = session.getInputs();
            List<ItemStack> products = handler.collectProducts(world, target.pos, expected, inputs, source);

            List<ItemStack> fluidProducts = owner.collectFluidProducts(world, target.pos, session);
            if (!fluidProducts.isEmpty()) {
                products.addAll(fluidProducts);
            }

            if (!products.isEmpty()) {
                if (owner.injectItemsToNetwork(proxy, world, products)) {
                    didWork = true;
                } else {
                    owner.stashItemsToStorage(world, products);
                }
            }

            boolean finished = handler.hasFinished(world, target.pos, inputs);
            session.finishCollect(finished);
            if (!products.isEmpty()) {
                didWork = true;
            }
        }

        return didWork;
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
                IActionSource source = new MachineSource(owner.host);
                IAEItemStack[] expected = session.getExpectedOutputs();
                List<ItemStack> inputs = session.getInputs();
                List<ItemStack> leftover = handler.collectProducts(world, target.pos, expected, inputs, source);
                List<ItemStack> leftoverFluids = owner.collectFluidProducts(world, target.pos, session);
                if (!leftoverFluids.isEmpty()) {
                    leftover.addAll(leftoverFluids);
                }
                if (!leftover.isEmpty()) {
                    owner.stashItemsToStorage(world, leftover);
                }
            }
        }
        session.reset();
        return true;
    }

    /**
     * 紧急收集指定目标的产物（用于移除绑定前清理）。
     */
    public void emergencyCollect(TargetBinding binding) {
        TargetSession session = owner.sessions.get(binding);
        if (session == null) return;

        try {
            World world = owner.host.getTileEntity().getWorld();
            if (world == null || world.provider.getDimension() != binding.dimension) return;
            if (!world.isBlockLoaded(binding.pos)) return;

            IRemoteHandler handler = HandlerRegistry.findHandler(binding.blockId);
            if (handler == null) return;

            IActionSource source = new MachineSource(owner.host);
            List<ItemStack> inputs = session.getInputs();
            IAEItemStack[] expected = session.getExpectedOutputs();

            List<ItemStack> products = handler.collectProducts(world, binding.pos, expected, inputs, source);
            List<ItemStack> fluidProducts = owner.collectFluidProducts(world, binding.pos, session);
            if (!fluidProducts.isEmpty()) {
                products.addAll(fluidProducts);
            }
            if (!products.isEmpty()) {
                owner.stashItemsToStorage(world, products);
            }

            List<ItemStack> reverted = handler.revertMaterials(world, binding.pos, source);
            if (!reverted.isEmpty()) {
                owner.stashItemsToStorage(world, reverted);
            }
            owner.revertPushedFluids(world, binding.pos, session.getInputFluids());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Emergency collect failed for binding {}: {}", binding.pos, e.toString());
        }
    }
}

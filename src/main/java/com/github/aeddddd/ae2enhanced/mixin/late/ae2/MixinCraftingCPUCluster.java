package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.cache.CraftingGridCache;
import appeng.me.helpers.MachineSource;
import appeng.tile.crafting.TileCraftingMonitorTile;
import appeng.tile.crafting.TileCraftingTile;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.crafting.scheduler.OptimizedScheduler;
import com.github.aeddddd.ae2enhanced.crafting.scheduler.PatternDepthRegistry;
import com.github.aeddddd.ae2enhanced.crafting.scheduler.TaskProgressAccessor;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyMeInterface;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public class MixinCraftingCPUCluster {

    private static final boolean CRAZYAE_LOADED =
        net.minecraftforge.fml.common.Loader.isModLoaded("crazyae");

    // ==================== Computation Core Support ====================

    @Unique
    private TileComputationCore ae2enhanced$computationCore;

    @Unique
    private OptimizedScheduler ae2e$scheduler;

    @Unique
    private boolean ae2e$schedulerInitFailed = false;

    public void ae2enhanced$setComputationCore(TileComputationCore core) {
        this.ae2enhanced$computationCore = core;
    }

    public TileComputationCore ae2enhanced$getComputationCore() {
        return this.ae2enhanced$computationCore;
    }

    @Shadow
    private MachineSource machineSrc;

    @Shadow
    private String myName;

    @Shadow
    private boolean isDestroyed;

    @Shadow
    private boolean somethingChanged;

    @Shadow
    private List<TileCraftingTile> tiles;

    @Shadow
    private List<TileCraftingMonitorTile> status;

    @Shadow
    private void updateCPU() {
    }

    // ==================== Overwrites for Virtual Clusters ====================

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void onIsActive(CallbackInfoReturnable<Boolean> cir) {
        if (ae2enhanced$computationCore != null) {
            IGridNode node = ae2enhanced$computationCore.getActionableNode();
            cir.setReturnValue(node != null && node.isActive());
        }
    }

    @Inject(method = "markDirty", at = @At("HEAD"), cancellable = true)
    private void onMarkDirty(CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            ae2enhanced$computationCore.markDirty();
            ci.cancel();
        }
    }

    @Inject(method = "getGrid", at = @At("HEAD"), cancellable = true)
    private void onGetGrid(CallbackInfoReturnable<IGrid> cir) {
        if (ae2enhanced$computationCore != null) {
            IGridNode node = ae2enhanced$computationCore.getActionableNode();
            cir.setReturnValue(node != null ? node.getGrid() : null);
        }
    }

    @Inject(method = "getWorld", at = @At("HEAD"), cancellable = true)
    private void onGetWorld(CallbackInfoReturnable<World> cir) {
        if (ae2enhanced$computationCore != null) {
            cir.setReturnValue(ae2enhanced$computationCore.getWorld());
        }
    }

    @Inject(method = "getCore", at = @At("HEAD"), cancellable = true)
    private void onGetCore(CallbackInfoReturnable<TileCraftingTile> cir) {
        if (ae2enhanced$computationCore != null) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "updateName", at = @At("HEAD"), cancellable = true)
    public void onUpdateName(CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            this.myName = net.minecraft.util.text.translation.I18n.translateToLocal("tile.ae2enhanced.computation_core.name");
            ci.cancel();
        }
    }

    @Inject(method = "breakCluster", at = @At("HEAD"), cancellable = true)
    public void onBreakCluster(CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            ci.cancel();
        }
    }

    @Inject(method = "done", at = @At("HEAD"), cancellable = true)
    void onDone(CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            ci.cancel();
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    public void onDestroy(CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            ci.cancel();
        }
    }

    @Inject(method = "updateStatus", at = @At("HEAD"), cancellable = true)
    public void onUpdateStatus(boolean updateGrid, CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) {
            ci.cancel();
        }
    }

    /**
     * 将 CraftingCPUCluster.executeCrafting 中 processing pattern 的 InventoryCrafting 尺寸
     * 从 4×4 扩展为 10×10，以支持超过 16 个输入的 processing pattern。
     * 使用 MixinExtras 的 @ModifyExpressionValue 修改字面量 4，避免与 ae2fc 的 @WrapOperation 在 NEW 上冲突。
     */
    @ModifyExpressionValue(
        method = "executeCrafting",
        at = @At(value = "CONSTANT", args = "intValue=4", ordinal = 0)
    )
    private int modifyCraftingWidth(int constant) {
        return 10;
    }

    @ModifyExpressionValue(
        method = "executeCrafting",
        at = @At(value = "CONSTANT", args = "intValue=4", ordinal = 1)
    )
    private int modifyCraftingHeight(int constant) {
        return 10;
    }

    // ==================== Batch Crafting (Assembly Hub) — retained ====================

    private static Field tasksField;
    private static Field remOpsField;
    private static Field remItemCountField;
    private static Field isCompleteField;
    private static Field waitingForField;
    private static Method postCraftingStatusChange;
    private static Method postChange;
    private static Field taskProgressValueField;
    private static Method completeJobMethod;
    private static Field finalOutputField;
    private static boolean reflectionReady = false;
    private static boolean reflectionFailed = false;

    private static int batchCallCount = 0;
    private static int batchSuccessCount = 0;
    private static int batchFailCount = 0;

    private static appeng.api.storage.data.IAEItemStack fetchFromNetwork(
            CraftingCPUCluster cpu,
            appeng.api.storage.data.IAEItemStack request,
            appeng.api.networking.security.IActionSource source) {
        try {
            appeng.api.networking.security.IActionSource src = cpu.getActionSource();
            if (src instanceof appeng.me.helpers.MachineSource) {
                java.util.Optional<appeng.api.networking.security.IActionHost> hostOpt =
                    ((appeng.me.helpers.MachineSource) src).machine();
                if (hostOpt.isPresent()) {
                    appeng.api.networking.IGridNode node = hostOpt.get().getActionableNode();
                    if (node != null) {
                        appeng.api.networking.IGrid grid = node.getGrid();
                        if (grid != null) {
                            appeng.api.networking.storage.IStorageGrid sg =
                                grid.getCache(appeng.api.networking.storage.IStorageGrid.class);
                            appeng.api.storage.channels.IItemStorageChannel channel =
                                appeng.api.AEApi.instance().storage().getStorageChannel(
                                    appeng.api.storage.channels.IItemStorageChannel.class);
                            appeng.api.storage.IMEMonitor<appeng.api.storage.data.IAEItemStack> storage =
                                sg.getInventory(channel);
                            return storage.extractItems(request, appeng.api.config.Actionable.MODULATE, source);
                        }
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] fetchFromNetwork failed: {}", e.toString());
        }
        return null;
    }

    private static void tryInitReflection() {
        if (reflectionReady || reflectionFailed) return;
        try {
            tasksField = CraftingCPUCluster.class.getDeclaredField("tasks");
            tasksField.setAccessible(true);
            remOpsField = CraftingCPUCluster.class.getDeclaredField("remainingOperations");
            remOpsField.setAccessible(true);
            remItemCountField = CraftingCPUCluster.class.getDeclaredField("remainingItemCount");
            remItemCountField.setAccessible(true);
            isCompleteField = CraftingCPUCluster.class.getDeclaredField("isComplete");
            isCompleteField.setAccessible(true);
            waitingForField = CraftingCPUCluster.class.getDeclaredField("waitingFor");
            waitingForField.setAccessible(true);
            postCraftingStatusChange = CraftingCPUCluster.class.getDeclaredMethod("postCraftingStatusChange", IAEItemStack.class);
            postCraftingStatusChange.setAccessible(true);
            postChange = CraftingCPUCluster.class.getDeclaredMethod("postChange", IAEItemStack.class, appeng.api.networking.security.IActionSource.class);
            postChange.setAccessible(true);
            Class<?> taskProgressClass = Class.forName("appeng.me.cluster.implementations.CraftingCPUCluster$TaskProgress");
            taskProgressValueField = taskProgressClass.getDeclaredField("value");
            taskProgressValueField.setAccessible(true);
            completeJobMethod = CraftingCPUCluster.class.getDeclaredMethod("completeJob");
            completeJobMethod.setAccessible(true);
            finalOutputField = CraftingCPUCluster.class.getDeclaredField("finalOutput");
            finalOutputField.setAccessible(true);
            reflectionReady = true;
        } catch (Exception e) {
            reflectionFailed = true;
            AE2Enhanced.LOGGER.error("[AE2E] Mixin reflection init failed, batch crafting disabled. " +
                "This usually means AE2-UEL class/field names have changed. Details: {}", e.toString());
        }
    }

    @Redirect(
        method = "updateCraftingLogic",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/tile/crafting/TileCraftingTile;isActive()Z"
        )
    )
    private boolean redirectIsActive(TileCraftingTile instance) {
        if (ae2enhanced$computationCore != null) {
            // CrazyAE 兼容：保留默认行为，避免干扰其修改后的 isActive 逻辑。
            if (!CRAZYAE_LOADED) {
                IGridNode node = ae2enhanced$computationCore.getActionableNode();
                return node != null && node.isActive();
            }
        }
        return instance.isActive();
    }

    @Inject(method = "updateCraftingLogic", at = @At("HEAD"))
    private void onUpdateCraftingLogicHead(IGrid grid, IEnergyGrid eg, CraftingGridCache cache, CallbackInfo ci) {
        // CrazyAE 通过 ASM 大幅修改了 CraftingCPUCluster，虚拟集群的字段初始化
        // 与其状态机不兼容；跳过我们的 HEAD 注入以避免干扰 CrazyAE 逻辑。
        if (ae2enhanced$computationCore != null && CRAZYAE_LOADED) return;
        if (reflectionFailed) return;
        try {
            tryInitReflection();
            if (!reflectionReady) return;
            CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;

            @SuppressWarnings("unchecked")
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);
            boolean isComplete = isCompleteField.getBoolean(cpu);

            if (!isComplete && tasks.isEmpty()) {
                @SuppressWarnings("unchecked")
                IItemList<IAEItemStack> waitingFor = (IItemList<IAEItemStack>) waitingForField.get(cpu);
                boolean waitingForEmpty = true;
                if (waitingFor != null) {
                    for (IAEItemStack is : waitingFor) {
                        if (is != null && is.getStackSize() > 0) {
                            waitingForEmpty = false;
                            break;
                        }
                    }
                }
                if (waitingForEmpty) {
                    completeJobMethod.invoke(cpu);
                    // 修复：completeJob() 不重置 finalOutput 也不调用 updateCPU()，
                    // 导致 Crafting Monitor 在任务完成后不清空
                    if (finalOutputField != null) {
                        finalOutputField.set(cpu, null);
                    }
                    updateCPU();
                    // v2.0: clear per-job scheduling state
                    PatternDepthRegistry.clear();
                    if (ae2e$scheduler != null) {
                        ae2e$scheduler.clearWaitMap();
                    }
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] onUpdateCraftingLogicHead unexpected error: {}", e.toString());
        }
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void batchProcessVirtualTasks(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        // CrazyAE 兼容：跳过批量合成注入，避免与其修改后的 executeCrafting 冲突。
        if (ae2enhanced$computationCore != null && CRAZYAE_LOADED) return;
        if (reflectionFailed) return;

        CraftingCPUCluster cpu;
        boolean anyOurTask = false;
        int virtualTasksFound = 0;
        int virtualTasksExecuted = 0;

        try {
            tryInitReflection();
            if (!reflectionReady) return;
            cpu = (CraftingCPUCluster) (Object) this;

            @SuppressWarnings("unchecked")
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);
            if (tasks.isEmpty()) return;

            @SuppressWarnings("unchecked")
            IItemList<IAEItemStack> waitingFor = (IItemList<IAEItemStack>) waitingForField.get(cpu);

            boolean changed;
            int doWhileIterations = 0;
            do {
                changed = false;
                for (Map.Entry<ICraftingPatternDetails, Object> entry : new ArrayList<>(tasks.entrySet())) {
                    ICraftingPatternDetails details = entry.getKey();
                    Object progress = entry.getValue();

                    long remaining = taskProgressValueField.getLong(progress);
                    if (remaining <= 0) continue;

                    List<ICraftingMedium> mediums = cache.getMediums(details);
                    if (mediums == null || mediums.isEmpty()) continue;

                    for (ICraftingMedium medium : mediums) {
                        if (!(medium instanceof TileAssemblyMeInterface)) continue;
                        anyOurTask = true;

                        TileAssemblyController controller = ((TileAssemblyMeInterface) medium).getController();
                        if (controller == null) continue;

                        if (!controller.isVirtualPattern(details)) {
                            if (!controller.canBatch()) break;

                            long cap = controller.getParallelCap();
                            long batchSize = (cap >= Long.MAX_VALUE / 2) ? remaining : Math.min(remaining, cap);
                            long actualBatchSize = batchSize;

                            appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                            controller.setCurrentActionSource(source);
                            try {
                                appeng.crafting.MECraftingInventory meInv = (appeng.crafting.MECraftingInventory) cpu.getInventory();
                                appeng.api.config.Actionable SIMULATE = appeng.api.config.Actionable.SIMULATE;
                                appeng.api.config.Actionable MODULATE = appeng.api.config.Actionable.MODULATE;

                                TileAssemblyController.PatternBatchInfo info = controller.getPatternBatchInfo(details, meInv, source);
                                if (info == null || info.recipe == null || info.slotTemplates == null || info.catalystSlots == null) break;

                                if (info.transformSlots != null && info.transformSlots.cardinality() > 0) {
                                    actualBatchSize = 1;
                                }

                                int estimatedStacks = 1;
                                for (int i = 0; i < info.slotTemplates.length; i++) {
                                    if (info.slotTemplates[i] != null && !info.catalystSlots.get(i)) {
                                        estimatedStacks++;
                                    }
                                }
                                if (!controller.canAcceptRealBatch(estimatedStacks)) break;

                                boolean canExtract = true;
                                for (int i = 0; i < info.slotTemplates.length; i++) {
                                    if (info.slotTemplates[i] == null) continue;
                                    long needCount;
                                    if (info.catalystSlots.get(i) || info.transformSlots.get(i)) {
                                        needCount = 1;
                                    } else {
                                        needCount = actualBatchSize;
                                    }
                                    IAEItemStack need = info.slotTemplates[i].copy();
                                    need.setStackSize(needCount);
                                    IAEItemStack simResult = meInv.extractItems(need, SIMULATE, source);
                                    if (simResult == null || simResult.getStackSize() < needCount) {
                                        if (info.catalystSlots.get(i) || info.transformSlots.get(i)) {
                                            IAEItemStack toFetch = info.slotTemplates[i].copy();
                                            toFetch.setStackSize(1);
                                            IAEItemStack fetched = fetchFromNetwork(cpu, toFetch, source);
                                            if (fetched != null && fetched.getStackSize() > 0) {
                                                meInv.injectItems(fetched, MODULATE, source);
                                                simResult = meInv.extractItems(need, SIMULATE, source);
                                                if (simResult == null || simResult.getStackSize() < needCount) {
                                                    canExtract = false;
                                                }
                                            } else {
                                                canExtract = false;
                                            }
                                        } else {
                                            long missing = needCount - (simResult != null ? simResult.getStackSize() : 0);
                                            IAEItemStack toFetch = info.slotTemplates[i].copy();
                                            toFetch.setStackSize(missing);
                                            IAEItemStack fetched = fetchFromNetwork(cpu, toFetch, source);
                                            if (fetched != null && fetched.getStackSize() > 0) {
                                                meInv.injectItems(fetched, MODULATE, source);
                                                long nowAvailable = (simResult != null ? simResult.getStackSize() : 0)
                                                    + fetched.getStackSize();
                                                actualBatchSize = Math.min(actualBatchSize, nowAvailable);
                                            } else {
                                                actualBatchSize = Math.min(actualBatchSize,
                                                    simResult != null ? simResult.getStackSize() : 0);
                                            }
                                        }
                                    }
                                }
                                if (!canExtract || actualBatchSize <= 0) break;

                                for (int i = 0; i < info.slotTemplates.length; i++) {
                                    if (info.slotTemplates[i] == null) continue;
                                    long needCount;
                                    if (info.catalystSlots.get(i) || info.transformSlots.get(i)) {
                                        needCount = 1;
                                    } else {
                                        needCount = actualBatchSize;
                                    }
                                    IAEItemStack need = info.slotTemplates[i].copy();
                                    need.setStackSize(needCount);
                                    IAEItemStack extracted = meInv.extractItems(need, MODULATE, source);
                                    if (extracted != null && extracted.getStackSize() > 0) {
                                        IAEItemStack diff = extracted.copy();
                                        diff.setStackSize(-diff.getStackSize());
                                        postChange.invoke(cpu, diff, source);
                                        postCraftingStatusChange.invoke(cpu, diff);
                                    }
                                }

                                InventoryCrafting ic = new InventoryCrafting(new net.minecraft.inventory.Container() {
                                    @Override
                                    public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) {
                                        return false;
                                    }
                                }, 3, 3);
                                for (int i = 0; i < info.slotTemplates.length; i++) {
                                    if (info.slotTemplates[i] == null) continue;
                                    ItemStack stack = info.slotTemplates[i].createItemStack();
                                    stack.setCount(1);
                                    ic.setInventorySlotContents(i, stack);
                                }

                                ItemStack output = info.recipe.getCraftingResult(ic);
                                NonNullList<ItemStack> recipeRemaining = info.recipe.getRemainingItems(ic);

                                if (!output.isEmpty()) {
                                    ItemStack batchOutput = output.copy();
                                    batchOutput.setCount(output.getCount() * (int) actualBatchSize);
                                    controller.addPendingOutput(batchOutput);
                                }

                                for (int i = 0; i < recipeRemaining.size(); i++) {
                                    ItemStack rem = recipeRemaining.get(i);
                                    if (rem.isEmpty()) continue;
                                    if (info.catalystSlots.get(i)) {
                                        IAEItemStack catalystReturn = info.slotTemplates[i].copy();
                                        catalystReturn.setStackSize(1);
                                        meInv.injectItems(catalystReturn, MODULATE, source);
                                    } else {
                                        ItemStack batchRem = rem.copy();
                                        batchRem.setCount(rem.getCount() * (int) actualBatchSize);
                                        controller.addPendingOutput(batchRem);
                                    }
                                }

                                long newRemaining = remaining - actualBatchSize;
                                taskProgressValueField.setLong(progress, newRemaining);

                                long oldRemOps = remOpsField.getLong(cpu);
                                remOpsField.setLong(cpu, oldRemOps - actualBatchSize);
                                long oldRemItemCount = remItemCountField.getLong(cpu);
                                long totalOutputCount = 0;
                                for (IAEItemStack out : details.getCondensedOutputs()) {
                                    if (out != null) totalOutputCount += out.getStackSize() * actualBatchSize;
                                }
                                remItemCountField.setLong(cpu, oldRemItemCount - totalOutputCount);

                                controller.setBatchBusy(true);
                                changed = true;
                                controller.resetBatchCooldown();


                            } catch (Exception e) {
                                AE2Enhanced.LOGGER.error("[AE2E] Real batch error: {}", e.toString());
                            } finally {
                                controller.setCurrentActionSource(null);
                            }
                            break;
                        }

                        if (!controller.canBatch()) continue;
                        virtualTasksFound++;

                        long cap = controller.getParallelCap();
                        long batchSize = (cap >= Long.MAX_VALUE / 2) ? remaining : Math.min(remaining, cap);

                        appeng.api.networking.security.IActionSource source = cpu.getActionSource();
                        controller.setCurrentActionSource(source);
                        try {
                            appeng.crafting.MECraftingInventory meInv = (appeng.crafting.MECraftingInventory) cpu.getInventory();
                            IItemList<IAEItemStack> itemList = meInv.getItemList();
                            appeng.api.config.Actionable SIMULATE = appeng.api.config.Actionable.SIMULATE;
                            appeng.api.config.Actionable MODULATE = appeng.api.config.Actionable.MODULATE;

                            boolean canExtract = true;
                            for (int retry = 0; retry < 5; retry++) {
                                canExtract = true;
                                for (IAEItemStack inputTemplate : details.getCondensedInputs()) {
                                    if (inputTemplate == null || inputTemplate.getStackSize() <= 0) continue;
                                    long totalNeed = inputTemplate.getStackSize() * batchSize;
                                    if (totalNeed <= 0) { canExtract = false; batchSize = 0; break; }
                                    IAEItemStack need = inputTemplate.copy();
                                    need.setStackSize(totalNeed);
                                    IAEItemStack simResult = meInv.extractItems(need, SIMULATE, source);
                                    if (simResult == null || simResult.getStackSize() < totalNeed) {
                                        long available = simResult != null ? simResult.getStackSize() : 0;
                                        long missing = totalNeed - available;
                                        if (missing > 0) {
                                            IAEItemStack toFetch = inputTemplate.copy();
                                            toFetch.setStackSize(missing);
                                            IAEItemStack fetched = fetchFromNetwork(cpu, toFetch, source);
                                            if (fetched != null && fetched.getStackSize() > 0) {
                                                meInv.injectItems(fetched, MODULATE, source);
                                                simResult = meInv.extractItems(need, SIMULATE, source);
                                                if (simResult != null && simResult.getStackSize() >= totalNeed) {
                                                    continue;
                                                }
                                                available = simResult != null ? simResult.getStackSize() : 0;
                                            }
                                        }
                                        long maxBatch = available / inputTemplate.getStackSize();
                                        if (maxBatch > 0) {
                                            batchSize = Math.min(batchSize, maxBatch);
                                            canExtract = false; // 需要重试
                                        } else {
                                            canExtract = false;
                                            batchSize = 0;
                                            break;
                                        }
                                    }
                                }
                                if (canExtract) break;
                            }
                            if (!canExtract || batchSize <= 0) {
                                continue;
                            }

                            for (IAEItemStack inputTemplate : details.getCondensedInputs()) {
                                if (inputTemplate == null || inputTemplate.getStackSize() <= 0) continue;
                                long totalNeed = inputTemplate.getStackSize() * batchSize;
                                IAEItemStack need = inputTemplate.copy();
                                need.setStackSize(totalNeed);
                                IAEItemStack extracted = meInv.extractItems(need, MODULATE, source);
                                if (extracted != null && extracted.getStackSize() > 0) {
                                    IAEItemStack diff = extracted.copy();
                                    diff.setStackSize(-diff.getStackSize());
                                    postChange.invoke(cpu, diff, source);
                                    postCraftingStatusChange.invoke(cpu, diff);
                                }
                            }

                            long totalOutputItems = 0;
                            for (IAEItemStack outputTemplate : details.getCondensedOutputs()) {
                                if (outputTemplate == null || outputTemplate.getStackSize() <= 0) continue;
                                long totalCount = outputTemplate.getStackSize() * batchSize;
                                if (totalCount <= 0) continue;
                                totalOutputItems += totalCount;

                                IAEItemStack product = outputTemplate.copy();
                                product.setStackSize(totalCount);
                                itemList.add(product);
                                postChange.invoke(cpu, product.copy(), source);
                                postCraftingStatusChange.invoke(cpu, product.copy());

                                if (waitingFor != null) {
                                    IAEItemStack waiting = waitingFor.findPrecise(outputTemplate);
                                    if (waiting != null) {
                                        waiting.decStackSize(totalCount);
                                        if (waiting.getStackSize() <= 0) {
                                            waiting.setStackSize(0);
                                        }
                                    }
                                }
                            }

                            long newRemaining = remaining - batchSize;
                            taskProgressValueField.setLong(progress, newRemaining);

                            controller.setBatchBusy(true);

                            changed = true;
                            virtualTasksExecuted++;
                            controller.resetBatchCooldown();


                        } finally {
                            controller.setCurrentActionSource(null);
                        }
                        break;
                    }
                }
                doWhileIterations++;
            } while (changed && doWhileIterations < 100000);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] batchProcessVirtualTasks unexpected error: {}", e.toString());
        } finally {
            batchCallCount++;
            if (virtualTasksExecuted > 0) {
                batchSuccessCount += virtualTasksExecuted;

            } else if (anyOurTask && batchCallCount % 20 == 1) {
                batchFailCount++;

            }
        }
    }

    // ==================== v2.0 Optimized Scheduler ====================

    /**
     * Executed AFTER batchProcessVirtualTasks (defined earlier in this class).
     * If the scheduler successfully pushes any normal tasks, cancels native
     * executeCrafting so the HashMap traversal is skipped entirely.
     */
    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2e$optimizedSchedule(IEnergyGrid eg, CraftingGridCache cc, CallbackInfo ci) {
        if (ae2enhanced$computationCore != null) return;
        if (!AE2EnhancedConfig.crafting.schedulerEnabled) return;
        if (ae2e$schedulerInitFailed) return;
        if (!TaskProgressAccessor.isReady()) return;

        CraftingCPUCluster cpu = (CraftingCPUCluster) (Object) this;

        if (ae2e$scheduler == null) {
            ae2e$scheduler = new OptimizedScheduler();
        }

        try {
            if (!reflectionReady) tryInitReflection();
            if (!reflectionReady || tasksField == null || remOpsField == null) return;

            @SuppressWarnings("unchecked")
            Map<ICraftingPatternDetails, Object> tasks = (Map<ICraftingPatternDetails, Object>) tasksField.get(cpu);
            long remainingOps = remOpsField.getLong(cpu);

            if (tasks == null || tasks.isEmpty() || remainingOps <= 0) return;

            int executed = ae2e$scheduler.executeTick(tasks, (int) remainingOps, cc, cpu, eg);
            if (executed > 0) {
                remOpsField.setLong(cpu, remainingOps - executed);
                somethingChanged = true;
                ci.cancel();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Optimized scheduler injection error: {}", e.toString());
            ae2e$schedulerInitFailed = true;
        }
    }
}

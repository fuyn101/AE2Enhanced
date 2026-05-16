# AE2Enhanced 架构重构计划

> 本文件为重构规划，不加入 Git。

## 一、问题全景

三个 explore agent 交叉审计后，项目在 **9 个层级** 存在系统性重复与架构缺陷：

| 层级 | 问题数 | 最高严重度 | 核心反模式 |
|------|--------|-----------|-----------|
| Part/Bus | 11 | **High** | 三重 Part 重复、四类型硬编码分发、反射地狱 |
| Mixin | 12 | **High** | 三重 Mixin 重复、无条件加载崩溃风险、硬编码兼容检查 |
| TileEntity | 7 | **Medium** | AE Proxy 生命周期重复、4 类型 monitor 刷新重复 |
| Storage/Util | 4 | **High** | `EssentiaBusHelper` 5 次反射、`GasStorageAdapter`/`EssentiaStorageAdapter` 复制粘贴 |
| GUI/Container | 9 | **High** | 7 个 GUI 重复颜色/绘制、3 个 Unformed GUI ~90% 相同、Container 硬编码 9 个 sync 字段 |
| Structure | 4 | **High** | 3 个结构类验证/组装逻辑重复、`rotate()` 4 处重复、WorldSavedData 索引复制 |
| Network | 2 | **Medium** | 6 个 Packet 无基类 |
| Crafting | 3 | **Medium** | 两个 Registry 重复、不兼容的 key 格式 |
| Client Render | 3 | **High** | 4 个 TESR GL 状态保存/恢复 ~200 行重复 |

---

## 二、重构原则

1. **先稳后优**：P0 运行时崩溃风险必须先修复，再谈抽象
2. **由下至上**：先抽取 `ReflectionHelper`/`ChannelAccessor` 等基础设施，再重构上层 Part/Mixin
3. **一次一域**：每阶段只动一个垂直域（如"Bus 层"或"Mixin 层"），避免全项目大爆炸
4. **保持兼容**：所有 `@Mixin` 的 target 和 `remap=false` 策略不变；所有 NBT key 不变
5. **删除死代码**：`MixinGuiFluidTerminalHandleClick`（空方法体）、`AE2EnhancedFakeItemRegister`（未完成的重复）直接删除

---

## 三、分阶段执行计划

### 阶段 1：基础设施 + 运行时安全（P0，预估 2–3 小时）

**目标**：消除运行时崩溃风险，建立反射缓存基础设施，为后续重构打地基。

| # | 任务 | 文件 | 预期收益 |
|---|------|------|---------|
| 1.1 | **修复无条件 Mixin 的 `ItemGasDrop` 引用** | `MixinAEBaseContainer`, `MixinInventoryPlayer`, `MixinGuiMEMonitorableClick`, `MixinTileIOPort` | 消除无 Mekanism 时的 `NoClassDefFoundError` |
| 1.2 | **删除 `MixinGuiMEMonitorableGas`** | `mixin/late/MixinGuiMEMonitorableGas.java` | 功能已由 `MixinGuiMEMonitorableTooltip` 覆盖，删除避免双发 |
| 1.3 | **删除 `MixinGuiFluidTerminalHandleClick`** | `mixin/late/MixinGuiFluidTerminalHandleClick.java` | 空方法体，死代码 |
| 1.4 | **删除 `AE2EnhancedFakeItemRegister`** | `util/AE2EnhancedFakeItemRegister.java` | 未完成的重复抽象，功能已被 `FakeItemRegister` 覆盖 |
| 1.5 | **创建 `GasReflectionHelper`**（静态缓存） | 新建 `util/GasReflectionHelper.java` | `IGasStorageChannel`、`IGasHandler` 方法、`GAS_HANDLER_CAPABILITY` 在类加载时一次性反射并缓存，消除每次 tick 的 6 次反射 |
| 1.6 | **创建 `EssentiaChannelAccessor`**（静态缓存） | 新建 `util/EssentiaChannelAccessor.java` | `IEssentiaStorageChannel` 在类加载时一次性反射并缓存，消除 `EssentiaBusHelper` 中 5 次重复反射 |
| 1.7 | **创建 `CapabilityProbe`** | 新建 `util/CapabilityProbe.java` | 统一封装 `hasItemCap`/`hasFluidCap`/`hasGasCap`/`hasEssentiaCap` 探测，三个 Part 类不再重复写 `Loader.isModLoaded` + `Class.forName` 块 |

### 阶段 2：Bus/Part 层重构（P1，预估 4–6 小时）

**目标**：消除三个 Part 类之间的重复，建立 Stocking Handler 抽象。

| # | 任务 | 文件 | 预期收益 |
|---|------|------|---------|
| 2.1 | **抽取 `PartUniversalBusBase`** | 新建 `part/PartUniversalBusBase.java` | 统一 `BusMode`、`ResourceType`、`calculateItemsToSend`、`canDoBusWork`、`updateState`、`getGasCapability`、`availableSlots`、NBT、Model/Collision。`PartUniversalExportBus` 和 `PartUniversalImportBus` 各减少 ~200 行 |
| 2.2 | **抽取 `ItemPushHelper`** | 新建 `util/ItemPushHelper.java` | `pushItemIntoTarget` 在 `PartStockingBus` 和 `PartUniversalExportBus` 中完全重复，提取为静态工具方法 |
| 2.3 | **创建 `StockingHandler<T>` 接口** | 新建 `part/stocking/StockingHandler.java` | 定义统一契约：`canHandle(ItemStack)`、`countExternal(...)`、`supply(...)`、`recover(...)` |
| 2.4 | **实现四个 StockingHandler** | 新建 `part/stocking/ItemStockingHandler.java` 等 4 个文件 | 将 `PartStockingBus` 中 4 个 `handleXxxStocking` + `countXxx` + `supplyXxx` + `recoverXxx` 方法对迁移到各自 handler。`PartStockingBus` 的 `doBusWork` 简化为 handler 链遍历 |
| 2.5 | **重构 `PartStockingBus.doBusWork`** | `part/PartStockingBus.java` | 循环内改为：`for (StockingHandler h : handlers) if (h.canHandle(filter) && h.canHandleTarget(target)) { long delta = targetAmount - h.countExternal(...); ... }`。消灭硬编码 if-else 链 |
| 2.6 | **统一 Fluid 事务模板** | 新建 `util/FluidTransactionHelper.java` | `supplyFluid` 和 `recoverFluid` 的 SIMULATE→确认→MODULATE 四段式提取为 `FluidSupplyTransaction` / `FluidRecoverTransaction` 模板。同时可被 `PartUniversalExportBus.exportFluidSlot` 和 `PartUniversalImportBus.importFluidSlot` 复用 |
| 2.7 | **重构 `ContainerStockingBus` 的 sync 字段** | `container/ContainerStockingBus.java` | 将 `target0`…`target8` 9 个 `@GuiSync` 字段改为 `int[] targetAmounts`，消除 switch-case 地狱 |

### 阶段 3：Mixin 层重构（P2，预估 3–4 小时）

**目标**：消除三重 Mixin 重复，清理兼容检查。

| # | 任务 | 文件 | 预期收益 |
|---|------|------|---------|
| 3.1 | **统一 `NetworkMonitor` 三对 mixin** | 新建抽象基类 `mixin/late/MixinNetworkMonitorBase.java`，`MixinNetworkMonitorFluid`/`MixinNetworkMonitorGas`/`MixinNetworkMonitor` 继承 | 消除 ~500 行中 ~400 行重复 |
| 3.2 | **统一 `NetworkInventoryHandler` 三对 mixin** | 新建抽象基类 `mixin/late/MixinNetworkInventoryHandlerBase.java` | 消除 ~300 行中 ~255 行重复 |
| 3.3 | **统一 `GridStorageCache` 三对 mixin** | 新建抽象基类 `mixin/late/MixinGridStorageCacheBase.java` | 消除 ~255 行中 ~215 行重复 |
| 3.4 | **抽取 `MixinReflectionHelper`** | 新建 `mixin/MixinReflectionHelper.java` | 统一封装 `NetworkMonitor.notifyListenersOfChange` 和 `postChange` 的反射调用，消除 6 个 mixin 中的重复反射块 |
| 3.5 | **清理 `AE2FC_LOADED` 硬编码检查** | 所有受影响的 mixin 文件 | 对于**已处于条件加载配置**（Gas/Thaumic）的 mixin，删除方法体内的 `if (AE2FC_LOADED) return;`。对于**无条件配置**中的 mixin，如果检查是多余的也删除，或统一提取为 `Ae2fcCompat.shouldBypass()` 单一入口 |
| 3.6 | **统一 GUI mixin 的 tooltip 渲染** | `MixinGuiMEMonitorableTooltip.java`, `MixinGuiArcaneTerminal.java`, `MixinGuiCellWorkbench.java`, `MixinGuiCraftAmount.java` | 提取 `FakeItemTooltipRenderer`，将 `if (isFluidDrop) ... else if (isGasDrop) ... else if (isEssentiaDrop)` 三段式统一为 `FakeItemRegister.getHandler(stack).renderTooltip(...)` |

### 阶段 4：TileEntity + Storage 层重构（P3–P4，预估 2–3 小时）

| # | 任务 | 文件 | 预期收益 |
|---|------|------|---------|
| 4.1 | **抽取 `TileAENetworkBase`** | 新建 `tile/TileAENetworkBase.java` | 统一 `AENetworkProxy` 懒加载、`validate`/`invalidate`/`onChunkUnload` 转发、`createProxy`。`TileAssemblyController`、`TileComputationCore`、`TileHyperdimensionalController` 各减少 ~40 行 |
| 4.2 | **抽取 `TileDelegatedProxyBase`** | 新建 `tile/TileDelegatedProxyBase.java` | 统一 `getProxy`/`getGridNode`/`getCableConnectionType` 委托到 controller tile。`TileAssemblyMeInterface`、`TileHyperdimensionalMeInterface`、`TileSuperCraftingInterface` 各减少 ~30 行 |
| 4.3 | **重构 `TileHyperdimensionalController`** | `tile/TileHyperdimensionalController.java` | `refreshNetworkMonitor` 中 4 个类型的重复块提取为 `refreshMonitor(IMEMonitor monitor)` 私有方法；`postItemAlteration`/`postFluidAlteration`/`postGasAlteration`/`postEssentiaAlteration` 提取为泛型 `postAlteration(IStorageChannel<T>, T change)` |
| 4.4 | **重构 `EssentiaBusHelper`** | `util/EssentiaBusHelper.java` | 提取私有 `getEssentiaChannel()` 和 `getEssentiaInventory(grid)`，消除 5 次重复反射。`importEssentias`/`importEssentiaSlot` 合并为统一方法（通过 `AppEngInternalAEInventory` vs `IAEItemStack` 重载） |
| 4.5 | **抽取 `BigIntegerStorageAdapter<T>` 基类** | 新建 `storage/BigIntegerStorageAdapter.java` | `GasStorageAdapter` 和 `EssentiaStorageAdapter` 几乎完全相同，提取基类后两者各剩 ~30 行 |

### 阶段 5：GUI/Container 层重构（P5，预估 3–4 小时）

| # | 任务 | 文件 | 预期收益 |
|---|------|------|---------|
| 5.1 | **抽取 `GuiTechPanel` 基类** | 新建 `client/gui/GuiTechPanel.java` | 统一 tech-panel 背景绘制（main bg → inner panel → accent bar → borders → corner decorations）。`GuiAssemblyFormed`、`GuiAssemblyPattern`、`GuiAssemblyUnformed`、`GuiComputationUnformed`、`GuiHyperdimensionalUnformed` 各减少 ~50 行，共 ~250 行 |
| 5.2 | **抽取 `GuiStructureUnformed`** | 新建 `client/gui/GuiStructureUnformed.java` | 统一 `hasEnoughMaterials()`、`updateButtonState()`、`refreshMissingMap()`、缺失材料列表渲染。`GuiAssemblyUnformed`、`GuiHyperdimensionalUnformed`、`GuiComputationUnformed` 各从 ~90 行压缩到 ~15 行 |
| 5.3 | **抽取 `AbstractUniversalBusContainer`** | 新建 `container/AbstractUniversalBusContainer.java` | 统一 `setupConfig()`（7×9 fake slots + 5 upgrades）、`isSlotEnabled()`、`tryConvertHeldToFake()`、`tryConvertGasToFake()`。`ContainerUniversalImportBus` 和 `ContainerUniversalExportBus` 各减少 ~130 行，且消除气体反射代码的复制 |
| 5.4 | **抽取 `GuiUniversalBus`** | 新建 `client/gui/GuiUniversalBus.java` | 统一 `drawFG()`、`drawBG()`、`getPhantomTargets()`、`actionPerformed()`。`GuiUniversalImportBus` 和 `GuiUniversalExportBus` 各减少 ~100 行 |
| 5.5 | **统一颜色常量** | 新建 `client/gui/GuiColors.java` | 将 `PANEL_BG`、`PANEL_LIGHT`、`ACCENT`、`TEXT_MAIN` 等从 7 个 GUI 类中提取到单一常量类 |

### 阶段 6：Structure + Network + Crafting + Render（P6–P9，预估 2–3 小时）

| # | 任务 | 文件 | 预期收益 |
|---|------|------|---------|
| 6.1 | **抽取 `AbstractMultiBlockStructure`** | 新建 `structure/AbstractMultiBlockStructure.java` | 统一 `validate()`、`countMissing()`、`getMissingMap()`、`assemble()`/`disassemble()`、`placeMissingBlocks()`/`tryConsumeAndPlace()`。三个结构类各减少 ~100 行 |
| 6.2 | **抽取 `BlockPosIndex<T>` 泛型基类** | 新建 `structure/BlockPosIndex.java` | `ControllerIndex` 和 `ComputationCoreIndex` 合并为 `BlockPosIndex<ControllerIndex>` 和 `BlockPosIndex<ComputationCoreIndex>`，各剩 ~10 行 |
| 6.3 | **抽取 `StructureRotationHelper`** | 新建 `structure/StructureRotationHelper.java` | `rotate(BlockPos, EnumFacing)` 从 4 处提取到单一工具类 |
| 6.4 | **重构 `StructureEventHandler`** | `structure/StructureEventHandler.java` | 将 3 路 if-else 改为注册表模式：`Map<Block, StructureValidator>`，新增结构类型时只注册，不修改 event handler |
| 6.5 | **抽取 `BasePacket<T>` 基类** | 新建 `network/BasePacket.java` + `network/BaseHandler.java` | 6 个 Packet 类消除重复的 `fromBytes`/`toBytes`/`Handler`/`addScheduledTask` 样板，各减少 ~50% 行数 |
| 6.6 | **统一 Recipe Registry** | 新建 `crafting/SimpleRecipeRegistry.java` | `BlackHoleRecipeRegistry` 和 `SingularityRecipeRegistry` 统一为泛型实现 |
| 6.7 | **统一 Recipe Key 格式** | `crafting/BlackHoleRecipe.java`, `crafting/SingularityRecipe.java` | 统一使用 `"registryName:meta"` 格式 |
| 6.8 | **抽取 `RenderHelper`** | 新建 `client/render/RenderHelper.java` | `pushTechRenderState()` / `popTechRenderState()`、`unpackRGB(int)`、`drawSphere()`。4 个 TESR 各减少 ~50 行 |

---

## 四、Stocking Bus 专项重构设计

### 4.1 `StockingHandler<T>` 接口

```java
public interface StockingHandler {
    /** 该 handler 是否处理此 filter 类型 */
    boolean canHandle(ItemStack filterStack);
    
    /** 该 handler 是否支持目标容器 */
    boolean canHandleTarget(TileEntity target, EnumFacing side);
    
    /** 探测外部容器当前存量 */
    long countExternal(TileEntity target, EnumFacing side, IAEItemStack filter);
    
    /** 从网络向外部补充，返回实际补充量 */
    long supply(TileEntity target, EnumFacing side, IAEItemStack filter, 
                long amount, IActionSource source) throws Exception;
    
    /** 从外部向网络回收，返回实际回收量 */
    long recover(TileEntity target, EnumFacing side, IAEItemStack filter,
                 long amount, IActionSource source) throws Exception;
}
```

### 4.2 事务模板（统一 supply/recover 的四段式）

```java
public abstract class StockingTransaction<T> {
    protected final IMEMonitor<T> networkInv;
    protected final IActionSource source;
    
    public long execute(T wanted, long maxAmount) {
        long canExtract = simulateExtract(wanted, maxAmount);
        if (canExtract <= 0) return 0;
        long accepted = simulatePush(wanted, canExtract);
        if (accepted <= 0) return 0;
        commitExtract(wanted, accepted);
        commitPush(wanted, accepted);
        return accepted;
    }
    
    protected abstract long simulateExtract(T wanted, long amount);
    protected abstract long simulatePush(T wanted, long amount);
    protected abstract void commitExtract(T wanted, long amount);
    protected abstract void commitPush(T wanted, long amount);
}
```

- `FluidSupplyTransaction extends StockingTransaction<IAEFluidStack>`
- `FluidRecoverTransaction extends StockingTransaction<IAEFluidStack>`
- `GasSupplyTransaction extends StockingTransaction<IAEGasStack>`
- `GasRecoverTransaction extends StockingTransaction<IAEGasStack>`
- `ItemSupplyTransaction` / `ItemRecoverTransaction` / `EssentiaSupplyTransaction` / `EssentiaRecoverTransaction`

### 4.3 重构后的 `PartStockingBus.doBusWork()`

```java
protected TickRateModulation doBusWork() {
    if (!this.getProxy().isActive() || !this.canDoBusWork() || this.isSleeping()) {
        return TickRateModulation.IDLE;
    }
    TileEntity target = ...;
    if (target == null) return TickRateModulation.SLOWER;
    
    boolean worked = false;
    long maxWork = this.calculateItemsToSend();
    
    for (int slot = 0; slot < CONFIG_SIZE; slot++) {
        IAEItemStack filter = this.config.getAEStackInSlot(slot);
        if (filter == null) continue;
        long targetAmount = this.targetAmounts[slot];
        if (targetAmount <= 0) continue;
        
        for (StockingHandler handler : HANDLERS) {
            ItemStack filterStack = filter.createItemStack();
            if (!handler.canHandle(filterStack)) continue;
            if (!handler.canHandleTarget(target, opposite)) continue;
            
            long actual = handler.countExternal(target, opposite, filter);
            long delta = targetAmount - actual;
            
            if (delta > 0 && this.mode != RECOVER_ONLY) {
                long toSupply = Math.min(delta, handler.getMaxTransfer(maxWork));
                worked |= handler.supply(target, opposite, filter, toSupply, this.source) > 0;
            }
            if (delta < 0 && this.mode != SUPPLY_ONLY) {
                long toRecover = Math.min(-delta, handler.getMaxTransfer(maxWork));
                worked |= handler.recover(target, opposite, filter, toRecover, this.source) > 0;
            }
            break; // 找到第一个匹配的 handler 后停止
        }
    }
    return worked ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
}
```

**关键改进**：
- 消灭硬编码的 `if-else` 类型判断链
- `maxWork * 1000` 的魔法数字下放到各 handler（`ItemStockingHandler.getMaxTransfer(1) = 1`，`FluidStockingHandler.getMaxTransfer(1) = 1000`）
- 新增类型时只需新增一个 `StockingHandler` 实现并注册到 `HANDLERS` 列表

---

## 五、预期收益汇总

| 指标 | 当前估算 | 重构后估算 | 减少 |
|------|---------|-----------|------|
| Part/Bus 总代码行 | ~1800 | ~900 | **~50%** |
| Mixin 总代码行 | ~1500 | ~600 | **~60%** |
| TileEntity 重复样板 | ~350 | ~120 | **~65%** |
| GUI 背景绘制重复 | ~400 | ~80 | **~80%** |
| 运行时反射次数/tick | 72+ | 0（类加载时缓存） | **100%** |
| 新增 stocking 类型所需修改文件数 | 5+（Part + Mixin×3 + Helper） | 1（新增 handler） | **~80%** |
| 新增 resource 类型（如 Mana）引入 bug 的概率 | 高（需改 10+ 处 if-else） | 低（注册 handler 即可） | **显著降低** |

---

## 六、执行顺序建议

考虑到编译验证和回滚便利性，建议按以下顺序执行：

1. **阶段 1**（基础设施）：安全、低风险，先做完编译通过
2. **阶段 2 的前半段**（`PartUniversalBusBase` + `ItemPushHelper` + `GasReflectionHelper` + `EssentiaChannelAccessor`）：消除最痛的反射和复制
3. **阶段 3 的前半段**（Mixin 基类 + `MixinReflectionHelper`）：与阶段 2 无依赖，可并行
4. **阶段 2 的后半段**（`StockingHandler` 接口 + 四个实现 + `PartStockingBus` 重构）：这是用户最关心的部分
5. **阶段 4–6**：按依赖关系逐个推进

---

*计划版本：v1.0 | 生成时间：2026-05-16*

# AE2Enhanced 更新日志

## 1.4.3-alpha

### 新增功能
- **库存维持总线（Stocking Bus）配方**：使用一个通用输入总线、一个通用输出总线和一个红石比较器合成。
- **创造物品栏补充**：通用输入总线、通用输出总线和库存维持总线现已加入 AE2Enhanced 创造模式物品栏。
- **库存维持总线中键输入 GUI**：配置槽位中键点击可打开数字输入弹窗，支持数学表达式解析。
- **创造型存储元件容量上限**：内部 stackSize 上限从 `Integer.MAX_VALUE` 提升至 `Long.MAX_VALUE / 2`（约 4.6E18）。

### Bug 修复
- **创造型存储元件显示溢出**：修复了当网络中已存在某种物品时，插入标记该物品的创造元件导致终端显示负数的溢出问题。
- **库存维持总线供应逻辑**：修复了 `supplyFluid`、`supplyGas` 和 `stockEssentias` 对 `extractItems` 返回值的错误解读，解决了网络充裕时非物品无法输出的问题。
- **库存维持总线单位对齐**：流体/气体的 `targetAmount` 现为 1:1 对应 mB/单位；配置槽位 `maxStack` 设为 `Integer.MAX_VALUE`。
- **库存维持总线非物品输出可靠性**：增加 `IFluidHandler` 直接实现回退，优化 `doBusWork()` 的 `maxWork` 跟踪。
- **库存维持总线外部假物品支持**：现支持 `FluidDummyItem`、ae2fc 流体假物品和 `ItemDummyAspect` 作为过滤目标。
- **库存维持总线零数量清空**：数量为 0 的配置槽位现在会被正确清空。
- **流体操作顺序**：调整为先 fill 再 getContainer，防止输出空桶。
- **计算核心幽灵 CPU 泄漏**：在拆解、区块卸载和方块失效时从 `CraftingGridCache` 中移除残留 CPU 条目。
- **源质假物品编码**：将源质编码方式从 metadata 迁移至 NBT，避免冲突。
- **MixinCraftingCPUCluster 稳定性**：将 10 个 `@Overwrite` 替换为 `@Inject` at `HEAD` + `cancellable`，提升与其他模组的兼容性。
- **网络线程安全**：修复 `PacketMEMonitorableAction` 的竞态条件，将气体逻辑抽取到独立辅助类。
- **类加载安全**：将条件加载模组（神秘时代 /  Mekanism）的类引用与无条件加载的代码路径隔离。

## 1.4.2-alpha 及更早版本
- 详见 git 提交历史。

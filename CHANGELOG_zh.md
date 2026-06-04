# AE2Enhanced 更新日志

## 1.5.2

### Feature

- **先进中枢平台**
  - 控制器方块、发电机、区块加载、网络包体系
  - 允许玩家确定选区并且绑定到控制器
  - 对于选区六个方向独立 IO 支持,支持分别过滤, 支持主网络区域与子网过滤(后续可能会调整)
  - 特殊优化的IO过程.
  - 平台上的机器自动识别供电
  - 对多模组供电单独适配, 支持突破模组输入上限(EIO,热力,龙研)
  - GUI 支持，支持主网络区域管理、创建子网、重命名、解绑、删除
  - JEI/HEI支持
  - 每区块 15×15 白色混凝土 + 边缘黑色混凝土生成模式,允许自定义
  - 目前没有添加配方, 因为需要继续调整
- **RF 访问节点**
  - ME 网络新增 RF 存储频道，终端直接显示电量
  - 纯桥接模式，无本地缓存
  - 新增 ItemEnergyDrop 假物品，阻止玩家直接提取 RF
  - 允许外部输入和提取, 目前输入上限为int,后续会提升

### Improve

- **终端与仓储性能**
  - 50万+ 种物品场景增量同步：空桶清理、Pattern 缓存、parallelSort、客户端延迟重建
  - ItemRepo 空搜索快速路径与并行排序
  - 抽屉模组 Hash 索引适配器（FSL / StorageDrawers），替代 O(N) 遍历
- **超维度仓储中枢**
  - HyperdimensionalStorageFile 从 NBT 重构为自定义二进制格式
  - 支持百万甚至千万种复杂NBT物品存储, 并且不会出现任何NBT问题
  - 大幅优化了存储, 彻底解决了其他无限存储的NBT序列化/反序列化过程NBT溢出问题
  - 新增 `/ae2e testhd` 压力测试命令，支持指定 UUID、上限一次性生成100k种类, 生成列表是从注册的所有工具和装备与所有附魔随机排列组合, 生成数量级为Long
- **装配枢纽与远程处理器**
  - 跨维度自动上传，搜索所有已加载维度并按 ME 网络过滤
  - Botania 魔力池/精灵门/泰拉凝聚板物品投掷，改为 `_ae2eInput` 标记区分输入与产物
  - Botania 花药台改为 EntityItem 投掷，由 altar 自身处理水与物品
  - 装配枢纽样板槽增加 `isItemValidForSlot` 校验，防止非法物品静默丢失, 同时禁止非样板物品放入
- **中枢 ME 接口**
  - 修复一些发配问题
  - 无线频道重连验证
  - 升级卡 NBT 读写对齐
  - 超因果结构需求计算修复
- 频道卡
  - 自动重连支持

### Improve

- **中枢 ME 接口多目标回收修复**：将输入材料快照从单例 handler 迁移到每个中枢接口实例，按 `TargetBinding` 隔离，彻底修复多目标并行时产物被误判为残留输入而跳过收集的问题
- **Botania 花药台处理重构**：自动填水、按顺序直接调用 `collideEntityItem`（非种子先、种子后），避免 AABB 扫描不确定性导致合成无法触发
- **HEI 收藏栏 F 搜索修复**：解包 `BookmarkItem` 包装类以获取真实 `ItemStack`，修复收藏栏物品无法填入终端搜索栏的问题
- **配置整理**：移除空的 `Client` 占位类别；将 `DamageMode` 枚举内聚到 `BlackHole` 内部
- **JEI/HEI 隐藏**：先进中枢平台控制器与平台开发许可加入 JEI 黑名单（功能尚未完成，暂不展示）
- **清理**：移除未使用的通用内存卡模式切换按键注册（`CYCLE_UMC_MODE_KEY`）及相关网络包/处理器代码

### Bug fix

- 中枢 ME 接口多目标回收不全（`DefaultSingleBatchHandler` 单例状态覆盖）
- ae2fc `FluidConvertingInventoryCrafting` 数组越界（MixinExtras）
- 虚拟合成核心 `CraftingCPUCluster.getCore` 类型转换崩溃
- 气体假物品检查隔离到 `GasFakeItemChecks`，避免可选模组缺失时 `NoClassDefFoundError`
- 构建系统：引入 fastutil 8.5.12

---

## 1.5.1

### 架构与工程优化

- **注册表重构**：将 `ModBlocks`/`ModItems` 拆分为 `registry/content/` 架构（`BlockRegistry`、`ItemRegistry`、`PartRegistry`、`GameRegistryManager`）
- **Mixin 桥接层**：提取 `TerminalTooltipBridge` 与 `TerminalClickBridge`，将 Mixin 类与业务逻辑解耦
- **项目目录整理**：新建 `docs/` 与 `research/` 目录；清理 17 个临时/反编译目录
- **配方 JSON 化**：将工作台合成配方迁移至 JSON 格式
- **性能优化**：为 `DualityCentralInterface` 新增 `processingTargets` 快速路径 `HashSet`，实现 O(1) 的 PROCESSING 目标访问
- **创造模式标签页清理**：整理标签页

### 中枢 ME 接口（Central ME Interface）

- **增强通用单方块机器的产物回收能力**：`DefaultSingleBatchHandler` 现在记录已推送的输入材料，仅收集非输入物品
  - `isIdle` 通过检测是否存在可抽取的**非输入材料**物品来判断是否有产物，避免状态过早重置为 IDLE
  - `collectProducts` 采用放宽的 NBT 匹配策略（当预期产物无 NBT 时不检查实际产物的 NBT），修复与 Mekanism / Thermal Expansion 的兼容性
  - 收集所有副产物与残余物品，不再仅限于预期输出
  - 600 tick（30 秒）超时回退，防止状态无限卡住
- 新增 **Ender Crafter** 支持（Extended Crafting）
- 修复 ae2fc 流体分发兼容性
- 修复流体假物品在推料后未从合成台中移除的问题

### 全能终端（Omni Terminal）

- 新增 **Alt+JEI 处理配方传输**至右侧样板存储区
- 新增显示元件(View Cell)支持
- 新增 JEI 拖拽支持，用于样板编码器
- 重构 `GuiOmniTerm`：消除每帧反射，缓存 `ResourceLocation`，抽象槽位定位逻辑
- 修复 viewCell 槽位在 `jeiOffset` 与 `extraHeight` 下的定位问题

### 智能样板接口（Smart Pattern Interface）

- 完成智能样板接口，包含 81 槽 **MiniGUI 滚动编辑器**
- 智能样板接口允许绑定一个方块实体,生成该方块实体所能进行的所有配方
- 提供了非常多配置项
- 优秀的兼容性:支持存在JEI页面的所有机器
- 支持AE2fcru
- 采用uuid存储,避免NBT过大导致的相关问题,提供了相应的GC机制回收弃用的智能样板
- 支持分页、锁定/解锁、保留主输出/双倍模式按钮
- 为所有 GUI 操作添加客户端预测
- 修复滚轮、本地化、冲突排序与编码问题

### 通用内存卡（UMC / Universal Memory Card）

- 新增**跨模组升级处理器**：Mekanism、Ender IO、Thermal Expansion
- 集成自定义 UMC GUI 纹理
- 重构升级处理：统一的 `ensureAvailable` + ME 网络回退，原子化粘贴流程
- 修复绑定/清除后渲染不更新的问题
- 修复各类跨模组处理器崩溃与 bug（TE 转换套件索引、MEK 层级安装器、增强验证等）

### 指令支持

- 允许使用`/ae2e`或`/ae2enhanced`来执行模组注册的指令
- channel 允许实时调整频道设置,开关频道限制
- 允许通过指令找回丢失的超维度仓储中枢主方块(包含uuid)
- 允许手动启用智能样板GC

### Bug 修复

- 修复 **CrazyAE + SCC** 兼容性崩溃
- 修复 **ThaumicEnergistics 缺失**时的崩溃
- 修复 `CompressorHandler` 过注入 bug
- 修复控制器方块损坏时的 `StructurePlacementPreview` 崩溃
- 修复 `AstralSorceryHandler` 无效 UUID 与 NPE 问题
- 修复 `ThaumcraftHandler` 注魔矩阵活跃/合成状态混淆

---

## 1.5.0

### 中枢 ME 接口（重大新功能）

- 远程绑定系统：将支持的模组单方块机器绑定至 centralized 接口
- 完整的 `IRemoteHandler` 生命周期，含 `HandlerRegistry` 与 `IVirtualCraftingHandler`
- 支持模组：**植物魔法**（魔力池、精灵门、泰拉凝聚板、符文祭坛、花瓣祭坛）、**血魔法**（炼金台、灵魂锻炉、祭坛）、**实用拓展**、**龙之研究**、**高级合成**（工作台、压缩机、末影合成台）、** Bewitchment**（纺车、蒸馏器、女巫锅）、**星辉魔法**（星辉祭坛、共鸣 Relay）、**神秘时代**（注魔）
- 并行合成识别，支持每个绑定的独立介质
- 通过 `MixinPatternHelper` 与 `CraftingCPUCluster` 扩展实现超过 16 个输入的样板验证
- Alt+右键清除绑定
- 实时绑定同步与绑定线渲染器

### 智能样板接口（重大新功能）

- 核心数据结构、方块、物品与 TileEntity
- 通过 `MixinDualityInterface` 实现智能样板虚拟扩展
- `JEIRecipeHelper` 用于配方查询
- 网络数据包与 UMC 绑定集成
- GUI 交互：分页、切换、锁定、删除、修改

### 通用记忆卡（UMC）

- 支持自定义 Part 状态的复制/粘贴（ stocking 模式、目标数量、总线模式）
- 通过无线频道发射器进行 ME 网络绑定，实现粘贴时自动拉取升级
- 自定义 GUI 纹理与布局

### 全能终端增强

- 无线全能终端物品，带电量检测与 NBT 持久化
- 动态终端样式：SMALL=5 行，TALL=动态行数，支持纹理平铺
- 样板编码：合成模式（9 槽）与处理模式（16 入/6 出）
- JEI 配方传输，支持默认/Shift/Alt 模式
- 合成置顶 + 活跃合成高亮行
- 合成状态按钮与合成完成持久化
- 磁铁与拾取升级卡（即时传送吸收，7 格范围）
- Baubles + Shift+E 快捷键支持
- 右侧存储区，可配置最大堆叠数

### ae2fc 集成

- 中枢 ME 接口、智能样板接口与全能终端的流体/气体/源质显示
- 无硬 ae2fc 依赖的运行时兼容

### 其他

- 修复世界重载后超维度枢纽 ME 网络不同步
- 中枢 ME 接口配方、升级卡注册与掉落处理

---

## 1.4.4

- 强化全能终端无线连接处理，提升 ae2fc 兼容性

## 1.4.3-beta-hotfix1

- 修复 `initConditionalSections` 中 Gas/Essentia Descriptor 加载导致的 `NoClassDefFoundError` 崩溃

## 1.4.3-beta

- 版本号提升并更新更新日志

## 1.4.3-alpha

- **库存维持总线（Stocking Bus）**：在外部容器中维持目标数量，支持物品/流体/气体/源质
  - 非物品 stocking，保留 NBT
  - 中键点击数量输入 GUI
  - 支持外部假物品（FluidDummyItem、ae2fc drops、ItemDummyAspect）
  - 容量升级行为与槽位解锁层级
- **无线频道系统**：发射器 Part / TileEntity、接收卡、远程 GridConnection
  - 为所有 AE2 设备配置额外升级槽
  - JEI 到终端搜索（F 键，可配置）
  - 通过反射为 DualityInterface 提供无线频道连接
- **通用记忆卡（UMC）**：复制/粘贴 AE2 零件与设备的设置
  - 记忆卡合成配方
  - 实时 GUI 同步
  - ME 网络绑定实现粘贴时自动拉取升级
- 修复创造单元显示溢出
- 修复终端操作中的 `ConcurrentModificationException`
- 添加 Stocking Bus 与总线配方；将总线加入创造模式标签页

## 1.4.2-dev

- 移除无条件 Mixin 类中的硬 Mekanism 引用
- 移除对 thaumicenergistics 与 mekeng 的运行时硬引用

## 1.4.1-dev

- 修复升级槽背景渲染与 `GuiMEMonitorable` 无效类崩溃
- 修复模组未安装时的 `NoClassDefFoundError`

## 1.4.0-dev

- **通用输入总线**（E1a）：按槽位顺序导入物品/流体/气体/源质
- **通用输出总线**（E1b）：可配置模式导出物品/流体/气体/源质
- 终端源质通用显示（E2a MVP），兼容 ae2fc
- 无需 ae2fc 依赖的流体与气体假物品显示
- 终端合成数量 `long` 支持与缺失物品优先排序
- 修复 `ConcurrentModificationException`、流体/气体终端交互与气体渲染

## 1.3.3

- 通过禁用光照贴图纹理单元修复 TESR 暗渲染问题

## 1.3.2

- 修复 TESR 暗渲染问题
- 修复世界重载后超维度枢纽 AE2 网络与存储注册

## 1.3.1

- 修复损坏控制器方块时的 StructurePlacementPreview 崩溃
- 修复 TileAssemblyController 忽略 BlackHole damageMode 配置
- 修复 TESR GL 状态泄漏
- 修复专用服务器崩溃：将客户端专用 I18n 替换为服务器安全的 translateToLocal
- 修复大型合成作业后 CraftingMonitor 未清空
- 修复大型合成作业卡住（零 stackSize 幽灵检测）

## 1.3.0

- 物质炮弹药（共形电荷），穿透 1E8，虚空伤害，128 格射程
- 统一创造模式标签页
- 为三个多方块结构添加幽灵方块放置预览
- 结构自动组装
- 缩小幽灵方块并增加透明度；从远到近深度排序
- 修复超维度枢纽 ME 网络不同步
- 修复 BlackHole damageMode 配置
- 修复 StructureEventHandler 中的 ConcurrentModificationException
- 更新 ru_ru.lang
- 代码质量：移除未使用导入、空方法、检查警告

## 1.2.1-dev

- 修复 Mekanism/Thaumcraft 未安装时的 `NoClassDefFoundError`

## 1.2.0

- **超维度仓储中枢**（新多方块）
  - 无限容量存储物品、流体、Mekanism 气体与 Thaumcraft 源质
  - 外部文件持久化（存档安全、更新友好）
  - 全息_tesseract TESR，带能量粒子
  - 自动保存间隔、全息效果开关、最大渲染距离配置
  - 黑洞伤害模式配置
- **超因果计算核心**（新多方块）
  - 大型线框球体 TESR，带分层壳体与环
  - 通过 Mixin CraftingGridCache 实现 `Long.MAX_VALUE` 存储
  - 结构验证与固定并行处理
  - GUI 框架与网络同步
- 黑洞不再随机传送玩家
- 幽灵方块放置预览
- 日语（ja_jp）与俄语（ru_ru）本地化
- 大数字格式化，支持科学记数法与 Shift 切换

## 1.1.1

- 修复 GUI 未成形状态：扩展 ySize 以容纳完整缺失列表与玩家背包
- 修复代理重构后 AssemblyStructure 的 NPE
- 将 AENetworkProxy 移至控制器（共享节点）

## 1.1.0

- 催化剂批次修复
- 运行时网络获取
- 配方检测改进
- 修复 1.12.2 版本中的三个 bug

## 1.0.2

- Kirino TESR 修复
- 配方清理
- 文档更新

## 1.0.1

- 添加所有剩余升级卡纹理（能效、预留2）
- 样板自动上传升级（META_RESERVED1）
- 工具提示与自动上传合成限定筛选
- waitingFor 修复

## 1.0.0

- 初始发布：超因果装配枢纽（344 格多方块合成阵列）
- 黑洞合成系统（微型奇点事件视界配方）
- 升级卡：并行、速度、能效、容量、自动上传
- CraftTweaker 黑洞配方集成
- JEI 集成（黑洞配方分类）

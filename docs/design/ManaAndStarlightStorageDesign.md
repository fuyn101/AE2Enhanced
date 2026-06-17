# Mana / Starlight AE 存储通道设计文档

> 本文档为 AE2Enhanced 中枢 ME 接口“虚拟并行”功能的前置设计。  
> 目标：为 Botania 的 Mana 与 Astral Sorcery 的 Starlight 建立与现有 RF/Essentia/Fluid/Gas 一致的 AE 存储体系，使中枢接口可以从 AE 网络直接扣除这两种资源。

---

## 1. 背景与目标

### 1.1 背景

当前中枢 ME 接口的 handler 多数从世界方块中直接消耗资源（如 `TilePool` 的 mana、`TileAltar` 的 starlight）。  
在“虚拟并行”设计下，handler 不推料、不占用物理设备，因此需要从 **AE 网络存储**中扣除等价资源。

项目当前已支持的网络资源：

| 资源 | 通道 | 备注 |
|---|---|---|
| 物品 | `IItemStorageChannel` | AE2 原生 |
| 流体 | `IFluidStorageChannel` | AE2FC / AE2-UEL |
| 气体 | `IGasStorageChannel` | MekanismEnergistics |
| 源质 | `IEssentiaStorageChannel` | Thaumic Energistics |
| RF | `IEnergyStorageChannel` | AE2Enhanced 自定义 |

**缺口**：Mana（Botania）、Starlight（Astral Sorcery）。

### 1.2 目标

1. 实现 `IManaStorageChannel` 与 `IStarlightStorageChannel`。
2. 提供 `ItemManaDrop`、`ItemStarlightDrop` 假物品，用于在 AE 物品终端中显示存储量。
3. 与超维度仓储中枢（Hyperdimensional Storage Nexus）集成，支持大容量存储。
4. 为中枢 ME 接口虚拟并行提供统一的网络扣资源接口。
5. 保持与现有 RF 能量通道、假物品体系、Nexus 适配器架构一致。

---

## 2. 设计原则

1. **复用 RF 能量通道模式**  
   Mana 与 Starlight 都是单一无子类型资源，与 RF 完全相同。因此直接复用 `AEEnergyStack` / `EnergyList` / `EnergyStorageChannel` 的代码结构。

2. **假物品与 FakeItemHandler 一致**  
   参考 `ItemEnergyDrop` + `FakeEnergies`，创建 `ItemManaDrop` / `ItemStarlightDrop` 与 `FakeMana` / `FakeStarlight`。

3. **条件加载**  
   Botania / Astral Sorcery 为可选依赖。所有相关类、注册、渲染必须在对应 mod 存在时才加载，避免 `NoClassDefFoundError`。

4. **Nexus 集成**  
   与 `EnergyStorageAdapter` 一样，为 Mana/Starlight 提供 `AbstractStorageAdapter` 子类、`Descriptor`、`StorageSection`、`Codec`。

5. **图标复用**  
   - Mana 假物品图标：复用 Botania 的 `mana_tablet` / `mana_pearl` 物品图标。  
   - Starlight 假物品图标：复用 Astral Sorcery 的 `liquid_starlight` 流体图标（与 `FluidDropModel` 类似，动态获取流体精灵图）。

6. **终端可见性**  
   第一阶段仅要求 Nexus / Omni Terminal 能显示和提取 Mana/Starlight；标准 AE 物品终端通过 fake item 机制间接显示。

---

## 3. 整体架构

```
AE2 Network
    ├── IItemStorageChannel      (items)
    ├── IFluidStorageChannel     (fluids)
    ├── IGasStorageChannel       (gases)
    ├── IEssentiaStorageChannel  (essentia)
    ├── IEnergyStorageChannel    (RF)          ← 已有
    ├── IManaStorageChannel      (mana)        ← 新增
    └── IStarlightStorageChannel (starlight)   ← 新增

Fake Items (for display in item terminal)
    ├── ItemEnergyDrop     ← 已有
    ├── ItemManaDrop       ← 新增
    └── ItemStarlightDrop  ← 新增

Hyperdimensional Storage Nexus
    ├── EnergyStorageAdapter     ← 已有
    ├── ManaStorageAdapter       ← 新增
    ├── StarlightStorageAdapter  ← 新增
    ├── EnergyDescriptor         ← 已有
    ├── ManaDescriptor           ← 新增
    └── StarlightDescriptor      ← 新增
```

---

## 4. 文件清单

### 4.1 新增文件

| 路径 | 说明 |
|---|---|
| `src/main/java/.../item/ItemManaDrop.java` | Mana 假物品 |
| `src/main/java/.../item/ItemStarlightDrop.java` | Starlight 假物品 |
| `src/main/java/.../util/fakeitem/FakeMana.java` | Mana 假物品与 `IAEManaStack` 互转 |
| `src/main/java/.../util/fakeitem/FakeStarlight.java` | Starlight 假物品与 `IAEStarlightStack` 互转 |
| `src/main/java/.../storage/mana/IManaStorageChannel.java` | 标记接口 |
| `src/main/java/.../storage/mana/ManaStorageChannel.java` | 通道实现 |
| `src/main/java/.../storage/mana/IAEManaStack.java` | 堆叠标记接口 |
| `src/main/java/.../storage/mana/AEManaStack.java` | 堆叠实现 |
| `src/main/java/.../storage/mana/ManaList.java` | `IItemList` 实现 |
| `src/main/java/.../storage/starlight/IStarlightStorageChannel.java` | 标记接口 |
| `src/main/java/.../storage/starlight/StarlightStorageChannel.java` | 通道实现 |
| `src/main/java/.../storage/starlight/IAEStarlightStack.java` | 堆叠标记接口 |
| `src/main/java/.../storage/starlight/AEStarlightStack.java` | 堆叠实现 |
| `src/main/java/.../storage/starlight/StarlightList.java` | `IItemList` 实现 |
| `src/main/java/.../storage/ManaDescriptor.java` | Nexus descriptor |
| `src/main/java/.../storage/StarlightDescriptor.java` | Nexus descriptor |
| `src/main/java/.../storage/ManaStorageAdapter.java` | Nexus adapter |
| `src/main/java/.../storage/StarlightStorageAdapter.java` | Nexus adapter |
| `src/main/java/.../client/model/StarlightDropModel.java` | Starlight 假物品模型（动态流体图标） |
| `src/main/java/.../client/model/ManaDropModel.java` | Mana 假物品模型（静态物品图标） |

### 4.2 修改文件

| 路径 | 修改内容 |
|---|---|
| `src/main/java/.../registry/content/ItemRegistry.java` | 添加 `MANA_DROP`、`STARLIGHT_DROP` 字段 |
| `src/main/java/.../registry/GameRegistryManager.java` | 条件注册 Mana/Starlight 物品 |
| `src/main/java/.../AE2Enhanced.java` | `init()` 中注册 Mana/Starlight 存储通道 |
| `src/main/java/.../registry/ModContent.java` | `preInit()` 中条件调用 `FakeMana.init()` / `FakeStarlight.init()` |
| `src/main/java/.../proxy/ClientProxy.java` | 注册 Mana/Starlight 模型与颜色 |
| `src/main/java/.../storage/StorageSection.java` | 添加 `MANA`、`STARLIGHT` 枚举 |
| `src/main/java/.../storage/OptionalStorageManager.java` | 初始化 Mana/Starlight Nexus adapter |
| `src/main/java/.../storage/codec/*` | 添加 Mana/Starlight codec |
| `src/main/java/.../storage/HyperdimensionalStorageFile.java` | 读写 Mana/Starlight section |

---

## 5. 假物品设计

### 5.1 ItemManaDrop

```java
public class ItemManaDrop extends AbstractNbtDrop {
    public ItemManaDrop() {
        super("mana_drop");
    }

    public static ItemStack createStack() {
        return new ItemStack(ItemRegistry.MANA_DROP, 1);
    }

    public static boolean isManaDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemManaDrop;
    }
}
```

- 无 NBT，count 固定为 1，实际数量通过 `IAEItemStack.stackSize`。
- 图标使用 Botania `mana_tablet` 或 `mana_pearl` 的注册名。

### 5.2 ItemStarlightDrop

```java
public class ItemStarlightDrop extends AbstractNbtDrop {
    public ItemStarlightDrop() {
        super("starlight_drop");
    }

    public static ItemStack createStack() {
        return new ItemStack(ItemRegistry.STARLIGHT_DROP, 1);
    }

    public static boolean isStarlightDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemStarlightDrop;
    }
}
```

- 无 NBT，count 固定为 1，实际数量通过 `IAEItemStack.stackSize`。
- 图标使用 Astral Sorcery `liquid_starlight` 流体精灵图。

### 5.3 FakeItemHandler 注册

参考 `FakeEnergies`：

```java
public final class FakeMana {
    public static void init() {
        FakeItemRegister.registerHandler(ItemManaDrop.class, new FakeItemHandler<Long, IAEManaStack>() { ... });
    }
}

public final class FakeStarlight {
    public static void init() {
        FakeItemRegister.registerHandler(ItemStarlightDrop.class, new FakeItemHandler<Long, IAEStarlightStack>() { ... });
    }
}
```

---

## 6. 存储通道设计

### 6.1 Mana 存储通道

完全复用 RF 能量通道结构：

- `IManaStorageChannel extends IStorageChannel<IAEManaStack>`（标记接口）
- `ManaStorageChannel implements IManaStorageChannel`
  - `transferFactor() = 1`
  - `getUnitsPerByte() = 8`（与 RF 一致，可配置）
  - `createList()` → `new ManaList()`
  - `createStack(Object)` → 支持 `Number` / `IAEManaStack`
- `IAEManaStack extends IAEStack<IAEManaStack>`（标记接口）
- `AEManaStack extends AEStack<IAEManaStack> implements IAEManaStack`
  - `asItemStackRepresentation()` → `ItemManaDrop.createStack()`
  - `fuzzyComparison(...)` → `other != null`（单类型）
  - `isItem() = false`, `isFluid() = false`
- `ManaList implements IItemList<IAEManaStack>`
  - 内部维护单一 `AEManaStack` 实例。

### 6.2 Starlight 存储通道

与 Mana 完全对称：

- `IStarlightStorageChannel`
- `StarlightStorageChannel`
- `IAEStarlightStack`
- `AEStarlightStack`
- `StarlightList`

### 6.3 注册

在 `AE2Enhanced.init()` 中：

```java
if (Loader.isModLoaded("botania")) {
    AEApi.instance().storage().registerStorageChannel(
        IManaStorageChannel.class,
        new ManaStorageChannel()
    );
}

if (Loader.isModLoaded("astralsorcery")) {
    AEApi.instance().storage().registerStorageChannel(
        IStarlightStorageChannel.class,
        new StarlightStorageChannel()
    );
}
```

---

## 7. 超维度仓储中枢集成

### 7.1 Descriptor

- `ManaDescriptor implements Descriptor`：单例，无字段。
- `StarlightDescriptor implements Descriptor`：单例，无字段。

参考 `EnergyDescriptor`。

### 7.2 StorageAdapter

- `ManaStorageAdapter extends AbstractStorageAdapter<IAEManaStack, ManaDescriptor>`
- `StarlightStorageAdapter extends AbstractStorageAdapter<IAEStarlightStack, StarlightDescriptor>`

与 `HyperdimensionalEnergyStorageAdapter` 一致。

### 7.3 StorageSection

在 `StorageSection` 枚举中添加：

```java
MANA(5, "mana"),
STARLIGHT(6, "starlight");
```

### 7.4 Codec

在 `storage/codec/` 下添加：

- `ManaCodec`：读写 `AEManaStack` 的 `stackSize` / `countRequestable` / `craftable`。
- `StarlightCodec`：同上。

### 7.5 持久化

`HyperdimensionalStorageFile` 增加 `MANA` / `STARLIGHT` section 读写。

### 7.6 初始化

`OptionalStorageManager` 在 `initExternalAdapter()` 中条件注册：

```java
if (Loader.isModLoaded("botania")) {
    registerAdapter(ManaStorageAdapter.class, ...);
}
if (Loader.isModLoaded("astralsorcery")) {
    registerAdapter(StarlightStorageAdapter.class, ...);
}
```

---

## 8. 渲染

### 8.1 Mana 假物品

- 使用静态物品图标。
- 在 `ClientProxy` 中通过 `ModelLoader.registerItemVariants` / `ModelLoader.setCustomModelResourceLocation` 注册。
- 图标资源路径指向 Botania 的 `mana_tablet`（空状态即可）。

### 8.2 Starlight 假物品

- 使用流体精灵图。
- 创建 `StarlightDropModel extends FluidDropModel` 或独立实现。
- 在 `handleItemState()` 中读取 `FluidRegistry.getFluid("astralsorcery.liquid_starlight")` 的 still sprite。
- 注册到 `ModelRegistryEvent`。

---

## 9. 中枢 ME 接口集成（后续阶段）

本设计文档仅覆盖存储支持。后续虚拟并行将使用：

```java
IStorageGrid grid = ...;
IManaStorageChannel manaChannel = AEApi.instance().storage().getStorageChannel(IManaStorageChannel.class);
IMEMonitor<IAEManaStack> manaInv = grid.getInventory(manaChannel);

IAEManaStack request = AEManaStack.create(cost);
IAEManaStack extracted = manaInv.extractItems(request, Actionable.MODULATE, source);
if (extracted != null && extracted.getStackSize() >= cost) {
    // 执行虚拟合成
}
```

Starlight 同理。

---

## 10. 实现阶段

| 阶段 | 内容 | 目标 |
|---|---|---|
| **P0** | 本设计文档定稿 | 文档通过 |
| **P1** | Mana 存储通道 + 假物品 + Nexus 适配 | Botania 环境下网络可存取 Mana |
| **P2** | Starlight 存储通道 + 假物品 + Nexus 适配 | Astral Sorcery 环境下网络可存取 Starlight |
| **P3** | 标准 AE 终端显示（Mixin / fake item 合并） | 玩家在普通 ME 终端看到 Mana/Starlight 数量 |
| **P4** | 中枢 ME 接口虚拟并行接入 | handler 从网络扣资源 |

---

## 11. 待确认问题

1. **Mana 图标选择**  
   使用 `botania:mana_tablet` 还是 `botania:mana_pearl`？建议 `mana_pearl`，因为它作为物品图标更直观。

2. **Starlight 单位换算**  
   1 AE-Starlight = 1 Astral Sorcery 内部 starlight 单位？还是需要缩放？建议 1:1。

3. **Mana 单位换算**  
   1 AE-Mana = 1 Botania mana？建议 1:1。

4. **是否需要在标准 AE 终端中显示？**  
   P3 需要 Mixin 注入 `NetworkMonitor` 把 Mana/Starlight 堆叠合并到 item channel 的 `getAvailableItems`。是否要做？

5. **充能/存入方式**  
   第一阶段是否只通过 Nexus 存入？还是同时需要提供 Mana/Starlight 导入总线？

6. **`unitsPerByte` 数值**  
   Mana/Starlight 的 `unitsPerByte` 是否沿用 RF 的 8？还是更高（因为 mana 数值通常更大）？

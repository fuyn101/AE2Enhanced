# AE2Enhanced Changelog

## 1.4.3-alpha

### New Features
- **Stocking Bus recipe**: Crafted with one Universal Import Bus, one Universal Export Bus, and one Redstone Comparator.
- **Creative tab additions**: Universal Import Bus, Universal Export Bus, and Stocking Bus are now available in the AE2Enhanced creative tab.
- **Stocking Bus middle-click GUI**: Added a popup number input GUI when middle-clicking config slots, supporting math expressions.
- **Creative Cell capacity**: Raised the internal stack size cap from `Integer.MAX_VALUE` to `Long.MAX_VALUE / 2` (approx. 4.6E18).

### Bug Fixes
- **Creative Cell display overflow**: Fixed negative display values when a creative cell containing an existing network item was inserted.
- **Stocking Bus supply logic**: Fixed `supplyFluid`, `supplyGas`, and `stockEssentias` incorrectly interpreting `extractItems` return value, which broke non-item output when the network was abundant.
- **Stocking Bus unit alignment**: `targetAmount` now uses 1:1 mapping with mB/units for fluids and gases; config maxStack set to `Integer.MAX_VALUE`.
- **Stocking Bus non-item output reliability**: Added `IFluidHandler` direct fallback and improved `maxWork` tracking across `doBusWork()`.
- **Stocking Bus external fake items**: Now supports `FluidDummyItem`, ae2fc drops, and `ItemDummyAspect` as filter targets.
- **Stocking Bus zero-amount clearing**: Config slots with zero amount are now properly cleared.
- **Fluid operation order**: Fixed empty bucket output by filling the container before retrieving it.
- **Computation Core ghost CPU leak**: Removed leaked CPU entries from `CraftingGridCache` on disassembly, chunk unload, and tile invalidation.
- **Essentia drop encoding**: Migrated aspect encoding from metadata to NBT to prevent collision.
- **MixinCraftingCPUCluster stability**: Replaced 10 `@Overwrite` methods with `@Inject` at `HEAD` + `cancellable` to improve mod compatibility.
- **Network thread safety**: Fixed race conditions in `PacketMEMonitorableAction` and extracted gas logic to helper classes.
- **Classloading safety**: Isolated conditional-mod class references (Thaumcraft / Mekanism) from unconditionally loaded code paths.

## 1.4.2-alpha and earlier
- See git history for full details.

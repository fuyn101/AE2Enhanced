# AE2Enhanced Changelog

## 1.5.1

### Architecture & Engineering
- **Registry refactor**: Split `ModBlocks`/`ModItems` into `registry/content/` architecture (`BlockRegistry`, `ItemRegistry`, `PartRegistry`, `GameRegistryManager`)
- **Mixin bridge layer**: Extract `TerminalTooltipBridge` and `TerminalClickBridge` to decouple Mixin classes from business logic
- **Project directory reorganization**: New `docs/` and `research/` directories; clean up 17 temporary/decompiled directories
- **Recipe JSON migration**: Move crafting table recipes to JSON format
- **Performance**: Add `processingTargets` fast-path `HashSet` to `DualityCentralInterface` for O(1) PROCESSING target access
- **Creative tab cleanup**: Remove fake drops (`ItemFluidDrop`, `ItemGasDrop`, `ItemEssentiaDrop`) from creative tab

### Central ME Interface
- **Enhanced product recovery for generic machines**: `DefaultSingleBatchHandler` now tracks pushed inputs and only collects non-input items
  - `isIdle` detects products by checking for extractable non-input-material items
  - `collectProducts` uses relaxed NBT matching (no NBT check when expected has no NBT) to fix Mekanism/Thermal Expansion compatibility
  - Collects all byproducts and residual items, not just expected outputs
  - 600-tick timeout fallback to prevent infinite stuck states
- Add **Ender Crafter** support (Extended Crafting)
- Fix ae2fc fluid dispatch compatibility
- Fix fluid drops not being stripped from crafting table after push

### Omni Terminal
- Add **Alt+JEI processing recipe transfer** to right-side pattern storage
- Add **View Cell (display cell)** support
- Add **JEI ghost ingredient drag** support for pattern encoder
- Refactor `GuiOmniTerm`: eliminate per-frame reflection, cache `ResourceLocation`s, abstract slot positioning
- Fix Alt-pull mode: transfer inputs instead of outputs
- Fix viewCell slot positioning with `jeiOffset` and `extraHeight`

### Smart Pattern Interface
- Complete Smart Pattern Interface with 81-slot **MiniGUI scrolling editor**
- Support paging, lock/unlock, keep-primary/double mode buttons
- Add **Smart Pattern garbage collector** with ME interface whitelist and manual `/ae2e` command
- Add client-side prediction for all GUI actions
- Fix scroll wheel, localization, conflict sorting, and encoding issues

### Universal Memory Card (UMC)
- Add **cross-mod upgrade handlers**: Mekanism, Ender IO, Thermal Expansion
- Integrate custom UMC GUI texture
- Refactor upgrade handling: unified `ensureAvailable` with ME network fallback, atomic paste flow
- Fix binding render not updating after bind/clear
- Fix various cross-mod handler crashes (TE Conversion Kit index, MEK tier installer, augment validation)

### Bug Fixes
- Fix **CrazyAE + SCC** compatibility crash
- Fix **ThaumicEnergistics missing** crash
- Fix `CompressorHandler` over-injection bug
- Fix `StructurePlacementPreview` crash on broken controller blocks
- Fix `AstralSorceryHandler` invalid UUID and NPE issues
- Fix `ThaumcraftHandler` infusion matrix active/crafting state confusion

---

## 1.5.0

### Central ME Interface (New Major Feature)
- Remote binding system: bind single-block machines from supported mods to a centralized interface
- Complete `IRemoteHandler` lifecycle with `HandlerRegistry` and `IVirtualCraftingHandler`
- Supported mods: **Botania** (Pool, AlfPortal, TerraPlate, RuneAltar, Petal Apothecary), **BloodMagic** (AlchemyTable, SoulForge, Altar), **ActuallyAdditions**, **DraconicEvolution**, **ExtendedCrafting** (Table, Compressor, Ender Crafter), **Bewitchment** (SpinningWheel, Distillery, WitchesCauldron), **AstralSorcery** (Altar, AttunementRelays), **Thaumcraft** (Infusion)
- Parallel crafting recognition with per-binding medium
- Pattern validation for >16 inputs via `MixinPatternHelper` and `CraftingCPUCluster` expansion
- Alt+Right-click to clear bindings
- Real-time binding sync and binding line renderer

### Smart Pattern Interface (New Major Feature)
- Core data structures, block, items, and tile entity
- Smart pattern virtual expansion via `MixinDualityInterface`
- `JEIRecipeHelper` for recipe querying
- Network packets and UMC bind integration
- GUI interactions: paging, toggle, lock, delete, modify

### Universal Memory Card (UMC)
- Copy/paste support for custom Part states (stocking mode, target amounts, bus mode)
- ME network binding via Wireless Channel Transmitter for auto-pull upgrades
- Custom GUI texture and layout

### Omni Terminal Enhancements
- Wireless Omni Terminal item with power check and NBT persistence
- Dynamic terminal style: SMALL=5 rows, TALL=dynamic rows with texture tiling
- Pattern encoding: crafting mode (9-slot) and processing mode (16-in/6-out)
- JEI recipe transfer with default/shift/alt modes
- Crafting pin + active crafting highlight row
- Crafting status button and crafting completion persistence
- Magnet and Picker upgrade cards (instant teleport absorption, 7-block range)
- Baubles + Shift+E hotkey support
- Right storage with configurable max stack size

### ae2fc Integration
- Fluid/gas/essentia display across Central ME Interface, Smart Pattern Interface, and Omni Terminal
- Runtime compatibility without hard ae2fc dependency

### Other
- Hyperdimensional Nexus ME network desync fix after world reload
- Central ME Interface recipe, upgrade card registration, and drop handling

---

## 1.4.4
- Harden Omni Terminal wireless connection handling for ae2fc compatibility

## 1.4.3-beta-hotfix1
- Fix `NoClassDefFoundError` crash in `initConditionalSections` for Gas/Essentia Descriptor loading

## 1.4.3-beta
- Bump version and update changelog

## 1.4.3-alpha
- **Stocking Bus**: maintain target amounts in external containers for items/fluids/gas/essentia
  - Non-item stocking with NBT preservation
  - Middle-click amount input GUI
  - Support for external fake items (FluidDummyItem, ae2fc drops, ItemDummyAspect)
  - Capacity upgrade behavior and slot unlock tiers
- **Wireless Channel System**: transmitter Part / TileEntity, receiver card, remote GridConnection
  - Configurable extra upgrade slots for all AE2 devices
  - JEI-to-terminal search (F key, configurable)
  - Wireless channel connection for DualityInterface via reflection
- **Universal Memory Card (UMC)**: copy/paste settings for AE2 parts and devices
  - Memory card crafting recipe
  - Real-time GUI sync
  - ME network binding for auto-pull upgrades
- Fix creative cell display overflow
- Fix `ConcurrentModificationException` in terminal operations
- Add Stocking Bus and bus recipes; add buses to creative tab

## 1.4.2-dev
- Remove hard Mekanism references from unconditional Mixin classes
- Remove runtime hard references to thaumicenergistics and mekeng

## 1.4.1-dev
- Fix upgrade slot background rendering and `GuiMEMonitorable` invalid class crash
- Fix `NoClassDefFoundError` for thaumicenergistics when mod is absent

## 1.4.0-dev
- **Universal Import Bus** (E1a): import items/fluids/gas/essentia in slot-based ordering
- **Universal Export Bus** (E1b): export items/fluids/gas/essentia with configurable modes
- Terminal universal display for essentia (E2a MVP) with ae2fc compatibility
- Fluid and gas fake item display without ae2fc dependency
- Terminal crafting amount `long` support and missing-items-first sorting
- Fix `ConcurrentModificationException`, fluid/gas terminal interactions, and gas rendering

## 1.3.3
- Fix TESR dark rendering by disabling lightmap texture unit

## 1.3.2
- Fix TESR dark rendering issues
- Fix Hyperdimensional Nexus AE2 network and storage registration on world reload

## 1.3.1
- Fix StructurePlacementPreview crash on broken controller blocks
- Fix TileAssemblyController ignoring BlackHole damageMode config
- Fix TESR GL state leaks
- Fix dedicated server crash: replace client-only I18n with server-safe translateToLocal
- Fix CraftingMonitor not clearing after large crafting jobs
- Fix large crafting jobs hanging (zero-stackSize ghost check)

## 1.3.0
- Matter cannon ammo (conformal charge) with 1E8 penetration, void damage, 128-block range
- Unified creative tab
- Ghost block placement preview for all three multiblock structures
- Structure auto-assembly
- Shrink ghost blocks and increase transparency; depth-sort far-to-near
- Hyperdimensional Nexus ME network desync fix
- BlackHole damageMode config fix
- Fix ConcurrentModificationException in StructureEventHandler
- Update ru_ru.lang
- Code quality: remove unused imports, empty methods, inspection warnings

## 1.2.1-dev
- Fix `NoClassDefFoundError` when Mekanism/Thaumcraft not installed

## 1.2.0
- **Hyperdimensional Storage Nexus** (new multiblock)
  - Unlimited capacity for items, fluids, Mekanism gases, and Thaumcraft essentia
  - External file persistence (save-safe, update-friendly)
  - Holographic tesseract TESR with energy particles
  - Auto-save interval, holographic effects toggle, max render distance config
  - Black hole damage mode config
- **Supercausal Computation Core** (new multiblock)
  - Massive wireframe sphere TESR with layered shells and rings
  - `Long.MAX_VALUE` storage via Mixin CraftingGridCache
  - Structure validation and fixed parallel processing
  - GUI framework with network sync
- Black holes no longer randomly teleport players
- Ghost block placement preview
- Japanese (ja_jp) and Russian (ru_ru) localization
- Large number formatting with scientific notation and shift toggle

## 1.1.1
- Fix GUI unformed state: expand ySize to fit full missing list and player inventory
- Fix NPE in AssemblyStructure after proxy refactor
- Move AENetworkProxy to controller (shared node)

## 1.1.0
- Catalyst batch fixes
- Runtime network fetch
- Recipe detection improvements
- Fix three bugs in 1.12.2 version

## 1.0.2
- Kirino TESR fix
- Recipe cleanup
- Docs update

## 1.0.1
- Add all remaining upgrade card textures (efficiency, reserved2)
- Pattern auto-upload upgrade (META_RESERVED1)
- Tooltips and auto-upload crafting-only filter
- waitingFor fix

## 1.0.0
- Initial release: Supercausal Assembly Hub (344-block multiblock crafting array)
- Black Hole Crafting system (micro singularity event horizon recipes)
- Upgrade cards: parallel, speed, efficiency, capacity, auto-upload
- CraftTweaker integration for black hole recipes
- JEI integration (black hole recipe category)

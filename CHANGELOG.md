# AE2Enhanced Changelog

## 1.5.3-dev

### Features

- **Advanced ME Omni Tool**
  - Multi-mode tool: travel mode (blink / wall-phasing blink, 32-block range), attack mode (true fixed damage, bypasses invulnerability and creative godmode)
  - Wrench compatibility: AE2 / CoFH / Mekanism / Ender IO
  - Upgrade system with C-key config GUI, UV texture atlas layout, and param-select architecture
- **Advanced Silk Touch**: break blocks while preserving complete NBT data
- **Omni Terminal Pagination Architecture R3**
  - Server-side pagination: search, filter, sort, and paginate entirely on the server
  - Client caches only current visible page ±1 page (max 540 items), completely eliminating sync bottlenecks at 500k+ item-type scales
  - New network packets: `PacketOmniPageRequest` (C→S), `PacketOmniPageResult` (S→C), `PacketOmniUpdateNotify` (S→C lightweight notification)

### Improvements

- **Omni Terminal performance evolution**: V3 custom inventory protocol → V4 flatList + inverted index + async view → V4.2/V5 server-side search index → R3 server-side pagination
- **GUI texture refactor**: all GUI backgrounds now use texture atlases (1.png/2.png/3.png)
- **ForceKillHelper extraction**: extracted force-kill utilities from Omni Tool into standalone helper, unifying anti-heal, chaos damage, and DataManager bypass; applied to MicroSingularity and AssemblyController black hole damage
- **Assembly hub GUI adjustments**: slot offsets, brighter title colors, pattern page button textures
- **Hyperdimensional GUI adapted to new 2.png layout**

### Bug Fixes

- **Black hole / Chaos Core damage fixes**: hybrid damage revert, multipart entity kills, permanent anti-heal (EntityLivingBase.setHealth mixin)
- **forceSetHealthViaDataManager fixes**: direct DataEntry modification, multi-field fallback, catch InvocationTargetException, filter set/setEntry candidate methods
- **Forced entity removal**: removeEntityDangerously insurance when onDeath/setDead is blocked by subclass override
- **ME Omni Tool fixes**: localization keys, texture, sync, attack cooldown, travel mode fall damage reset, relaxed blink safety check
- **Conformal Charge entity protection field access**: via reflection
- **Hyperdimensional essentia storage fixes**: loadSectionReflective missing length-bytes unwrap causing corrupted EssentiaDescriptor aspectTag; NPE when optionalStorage is null
- **SmartPatternStorageFile NPE**: when world is null during TileEntity.readFromNBT
- **Central ME Interface pattern loss**: removed proxy.isReady() check in updateCraftingList()
- **GhostIngredientTarget NoClassDefFoundError**: catch Throwable when reflecting FakeGases
- **SmartRecipe forced processing mode**: force isCrafting=false when loading from NBT to fix old patterns
- **DefaultSingleBatchHandler.isIdle**: wait until all input materials are consumed before collecting products
- **Omni Tool GUI fixes**: small button size 12x18, top buttons arranged vertically, remove bar2 hover highlight, default state uses normal button texture
- **Omni Terminal compatibility fixes**: Object2IntOpenHashMap<>(-1) IllegalArgumentException; fastutil Object2ObjectOpenHashMap.computeIfAbsent NoSuchMethodError
- **Omni Terminal scroll fix**: `OmniItemRepo.size()` now returns `totalCount` from server instead of `activeCrafting.size() + normalView.size()`, fixing inability to scroll in large networks
- **Omni Terminal bidirectional scroll caching**: cache is now centered around the current visible page (previous + current + next), eliminating delay when scrolling up; `getRowSize()` fixed to call `super.getRowSize()` instead of broken reflection that always fell back to 9
- **Omni Terminal missing external storage fix**: terminal now correctly merges hyperdimensional storage with regular ME network storage (drives, external storage buses, etc.). `ItemStorageAdapter` now holds an `externalMonitor` reference and merges its data during query/search/sortedList building
- **Omni Terminal storage merge performance optimization**
  - `externalOnlyCache`: only rebuilds the diff set (external items not in adapter) when dirty, avoiding O(N) scans of the full 500k+ network on every query
  - `descriptorSnapshot`: `HashSet<ItemDescriptor>` snapshot of adapter keys, replacing `ConcurrentHashMap.containsKey()` to eliminate lock overhead
  - `searchCache`: caches full match lists by `(query, searchMode, viewMode)` for up to 32 entries, eliminating repeated indexing when the terminal stays in search mode or returns from JEI
  - `performSearchPaged` / `search` / `getAllItems` now query adapter index + `externalOnlyCache` (typically hundreds of items) instead of scanning the entire network monitor
  - `ContainerOmniTerm.postChange`: caches `ItemStorageAdapter` reference to avoid iterating grid nodes on every network change event

---

## 1.5.2

### Features

- **Advanced Central Platform**
  - Controller block, generator, chunk loading, network packet system
  - Allow players to define a selection area and bind it to the controller
  - Six-direction independent I/O support per selection, with separate filtering for each side; supports main-network-area and subnet filtering (may be adjusted later)
  - Specially optimized I/O processing
  - Automatic power supply recognition for machines on the platform
  - Per-mod power adapters supporting input-limit bypass (Ender IO, Thermal Expansion, Draconic Evolution)
  - GUI support: main network area management, subnet creation, renaming, unbinding, deletion
  - JEI/HEI support
  - Per-chunk 15×15 white concrete + edge black concrete generation pattern, customizable
  - No recipe added yet, as the feature is still being adjusted
- **RF Access Node**
  - New RF storage channel for the ME network; terminals display power directly
  - Pure bridge mode with no local buffer
  - New `ItemEnergyDrop` dummy item to prevent players from extracting RF directly
  - Allows external input and extraction
  - Creative mode boost: long-level injection bypassing int limit; configurable via scientific notation
- **Chunk Power Node**
  - Powers all machines in the same chunk via ME RF storage channel; consumes 1 AE channel
  - Red variant of wireless channel transmitter model
- **F-key JEI search for all AE2 terminals**: implemented via `MixinGuiMEMonitorableKeyHandler`, supports bookmark overlay

### Improvements

- **Terminal & Storage Performance**
  - 500k+ item-type incremental sync: empty-bucket cleanup, pattern caching, parallelSort, client-side deferred rebuild
  - `ItemRepo` empty-search fast path and parallel sorting
  - Drawer-mod hash-index adapters (FSL / StorageDrawers), replacing O(N) iteration
- **Hyperdimensional Storage Nexus**
  - `HyperdimensionalStorageFile` refactored from NBT to custom binary format
  - Supports millions or even tens of millions of complex-NBT items without any NBT issues
  - Massively optimized storage, completely solving the NBT overflow problem during serialization/deserialization that plagues other infinite-storage solutions
  - New `/ae2e testhd` stress-test command: supports specifying UUID, generating up to 100k item types at once from all registered tools/armor combined with all enchantments in random permutations, with quantities at `long` scale
- **Assembly Hub & Remote Handlers**
  - Cross-dimensional automatic upload: searches all loaded dimensions and filters by ME network
  - Botania Pool/AlfPortal/TerraPlate item tossing: now uses `_ae2eInput` tag to distinguish inputs from products
  - Botania Petal Apothecary: changed to EntityItem toss, handled by altar itself for water and items
  - Assembly hub pattern slots added `isItemValidForSlot` validation to prevent illegal items from silently disappearing; also prevents non-pattern items from being inserted
- **Central ME Interface**
  - Fixed some dispatch issues
  - Wireless channel reconnection validation
  - Upgrade card NBT read/write alignment
  - Supercausal structure requirement calculation fixes
- **Channel Receiver Card**
  - Automatic reconnection support

### Improvements (continued)

- **Central ME Interface multi-target collection fix**: moved input-material snapshots from the singleton handler into each Central Interface instance, keyed by `TargetBinding`. Completely fixed the issue where products were misidentified as residual inputs and skipped during multi-target parallel processing.
- **HEI bookmark F-search fix**: unpack the `BookmarkItem` wrapper class to obtain the real `ItemStack`, fixing the issue where bookmark items could not be filled into the terminal search bar.
- **Config cleanup**: removed empty `Client` placeholder category; moved `DamageMode` enum into `BlackHole` for better cohesion.
- **JEI/HEI hiding**: added Advanced Platform Controller and Platform Development License to JEI blacklist (features not yet complete).
- **Cleanup**: removed unused Universal Memory Card mode-cycling keybinding (`CYCLE_UMC_MODE_KEY`) and related network packet/handler code.
- **Energy adapter extensions**: Draconic Evolution `DEEnergyAdapter` bypasses `CraftingInjector` tick-rate limit for instant fill; Thermal Expansion `TEEnergyAdapter` bypasses receive limit; Mekanism coverage expanded to generators/tools submods
- **Advanced Central Platform GUI v2**: main-network zone management, unbind, delete X buttons, JEI ghost drag, 6-direction tooltips, scaled rename field, 256×224 size
- **Botania 20-tick `isIdle` delay removed**
- **CentralInterface anti-duping hardening**: push revert, timeout guard

### Bug Fixes

- Central ME Interface multi-target incomplete collection (`DefaultSingleBatchHandler` singleton state overwrite)
- ae2fc `FluidConvertingInventoryCrafting` array out of bounds (MixinExtras)
- Virtual crafting core `CraftingCPUCluster.getCore` type-cast crash
- Gas dummy-item checks isolated to `GasFakeItemChecks`, preventing `NoClassDefFoundError` when optional mods are missing
- Build system: introduced fastutil 8.5.12
- **Dedicated server crash**: `ItemEnergyDrop` used client-only `I18n`
- **Ender IO machine capacitor**: not recognized after UMC paste; fixed by triggering `setInventorySlotContents` to update capacitor data
- **RF energy system fixes**: empty item replacement, hardcoded name, notification deduplication, extract null contract, grid cache direction, capacitor staleness, NBT accumulation
- **Assembly hub pattern slot item loss**
- **Advanced Central Platform fixes**: IO engine main-net zone IO, subnet filter enforcement, lowered output flush threshold; submenu face mode sync, input/output button tooltips and click interception; GUI 256×224 size, UV coordinate exact matching, removed player inventory slots
- **ModEventHandler.onServerTick NPE**
- **Central ME Interface upgrade card NBT read/write nesting level mismatch**
- **RF Access Node**: added blockstates, fixed ME network connection, renamed `PlatformRFNode` → `RFAccessNode`, fake-item withdrawal exploit fixed
- **Chunk Power Node**: fixed missing item model registration, Mekanism machine facing detection

---

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

# AE2Enhanced

AE2Enhanced is a late-game addon for **AE2 Unofficial Extended Life (AE2-UEL)** that introduces multiple massive multiblock structures and a series of utility items for the endgame:

*Current version: 1.3.3, Dev version: 1.4.2-dev*
> New features and utility items are still under active development in the dev branch. A Release will be published once development is complete.

[中文版本 (Chinese)](/README_zh.md)

## Multiblock Structures

- **Supercausal Assembly Hub** — a 344-block crafting array similar to LazyAE's Large Molecular Assembler, allowing extreme parallelism (up to `Long.MAX_VALUE`) and automatic pattern uploading.
- **Hyperdimensional Storage Nexus** — an unlimited-capacity storage structure using `BigInteger`, supporting items, fluids, Mekanism gases, and Thaumcraft essentia, with data persisted to external files to prevent save bloat.
- **Supercausal Computation Core** — a super crafting CPU with `Long.MAX_VALUE` storage and 16384 parallel accelerator capacity, supporting multi-order concurrency via dynamic virtual CPU cluster pools.

---

## Modifications to AE2-UEL

- **Long-order support**: Allows crafting orders exceeding `Int.MAX_VALUE` items.
- **Crafting plan optimization**: Brings back the "missing items on top" feature from modern AE2 versions to `1.12.2`.
- **Creative cell buff**: Changes creative storage cell capacity to `Long.MAX_VALUE`.
- **Modern ME Terminal**: You no longer need separate fluid, gas, or essentia terminals. Everything is displayed in a single unified terminal, just like in modern AE2 versions. *(Thanks to [AE2fc-rework-unofficial](https://github.com/Circulate233/AE2FluidCraft-Rework-Unofficial/tree/main) for partial implementation reference, with compatibility maintained.)*
- For additional QoL improvements (e.g., crafting pinning), consider using [RandomComplement](https://github.com/Circulate233/RandomComplement), which provides many modern AE2 features.

---

## Additional Utility Items(Dev version now)

- ✅ **Universal Import Bus / Export Bus** — allows all types of IO through a single bus.
- ✅ **Stock Bus** — maintains a specified item count in the target inventory. *(Not yet implemented)*
- ✅ **Wireless Channel Emitter** — broadcasts its channels wirelessly. *(Not yet implemented)*
- ✅ **Channel Receiver Card** — receives channels from the Wireless Channel Emitter (unlimited distance/dimensions, configurable). *(Not yet implemented)*
- 🚧 **ME Interface Mirror** — mirrors an ME Interface. *(Not yet implemented)*
- 🚧 **Universal Memory Card** — works like a standard memory card, plus copying machine configurations and upgrade cards, bulk editing, and linking ME Interface Mirrors. *(Not yet implemented)*
- 🚧 **ME Sender Node** — wirelessly sends items in bulk (similar to an export bus), range set via the Universal Memory Card. *(Not yet implemented)*
- 🚧 **ME Receiver Node** — counterpart to the ME Sender Node. *(Not yet implemented)*
- 🚧 **Concurrency Card** — increases parallelism of AE2 IO devices. *(Not yet implemented)*
- 🚧 **ME Collector Node** — collects items from the world into the ME network. *(Not yet implemented)*

---

## Performance

### Assembly Hub
Uses a hybrid virtual + real crafting mechanism. Even for extremely large orders, `mspt` will not be significantly affected. Nearly all AE2-craftable orders are fully supported.

> **Note on CraftTweaker:** When using `.reuse()` in CRT scripts, AE2 may still request the full amount during ordering since it does not recognize the item as non-consumed.

### Hyperdimensional Storage Nexus
Uses an asynchronous + incremental refresh model with external file storage, fundamentally solving NBT overflow and tick lag issues while supporting extremely high storage capacity.

### Computation Core
Virtual CPU clusters minimize overhead by delegating actual crafting to existing network assemblers and ME interfaces. The dynamic pool ensures resources are only allocated when needed, and always keeps 1 idle CPU available for terminal ordering.

---

## Requirements

- **Minecraft**: 1.12.2
- **Forge**: 14.23.5.2768+

**Hard Dependencies**
- **AE2-UEL**: v0.56.7+
- **MixinBooter**: 8.9+

**Optional**
- **CTM** — provides connected textures
- **Mekanism + MekanismEnergistics** — enables gas storage support
- **Thaumcraft + Thaumic Energistics** — enables essentia storage support
- **CraftTweaker** — provides recipe modification methods
- **JEI/HEI** — related support

**Compatibility**
- **AE2fc-rework-unofficial** — does not re-register fake fluids (compatible)

---

## Download

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/ae2enhanced)  
[Releases](https://github.com/aeddddd/AE2Enhanced/releases)

---

## Compatibility

Compatible with **CleanroomMC**. Maintains good compatibility with other AE2 addons. Tested successfully in the large modpack **Divine Journey 2** (requires updating AE2-UEL to the latest version).

---

### Black Hole Crafting

A unique crafting system that allows players to create a **Micro Singularity** and throw items into its event horizon to transmute them into desired materials.

**Warning**: The Micro Singularity only lasts for `300 seconds`. Right-click it to activate crafting. **Do not stand too close!!!**

Full **CraftTweaker** support:
```zenscript
import mods.ae2enhanced.BlackHole;
BlackHole.addRecipe(IItemStack output, IItemStack[] inputs);
// Example
BlackHole.addRecipe(<minecraft:obsidian>, [<minecraft:stone> * 8, <minecraft:diamond>]);
BlackHole.removeRecipe("test_obsidian");
```

Built-in recipe names:
```
id: "test_obsidian"
id: "stable_spacetime_manifold"
id: "differential_form_stabilizer"
id: "conformal_invariant_charge"
```

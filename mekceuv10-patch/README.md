# AE2Enhanced MEK CEu v10 Patch

This standalone 1.12.2 patch adds the MEK CE Unofficial v10 machine-output
redirect integration to an older AE2Enhanced JAR. It compiles independently of
the AE2Enhanced source tree and its optional-mod libraries.

## Build

Requirements:

- JDK 8
- `libs/Mekanism-CE-Unofficial-All-10.0.0.450-dev.jar` from commit `9fc51fe`
- Network access for ForgeGradle and the MixinBooter compile API

From the repository root, run:

```powershell
.\gradlew.bat -p mekceuv10-patch build
```

If the MEK CEu v10 development JAR is elsewhere, pass its path explicitly:

```powershell
.\gradlew.bat -p mekceuv10-patch build -Pmekceuv10Jar=C:\path\to\Mekanism-CE-Unofficial-All-10.0.0.450-dev.jar
```

The output is `mekceuv10-patch/build/libs/AE2Enhanced-MEKCEuV10-Patch-1.0.0.jar`.

## Runtime

Place the patch JAR in the same `mods` directory as an older AE2Enhanced,
Mekanism CE Unofficial v10, and MixinBooter installation. Do not use this patch
with AE2Enhanced builds that already contain `MekanismV10MixinPlugin`, including
commit `9fc51fe` and newer.

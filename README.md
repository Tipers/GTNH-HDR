## Install

0. **Get Complementary + Euphoria Patches** (skip this if you already have a `ComplementaryUnbound_r5.8.1 + EuphoriaPatches_1.9.3` shader pack):
   - Download **Complementary Unbound r5.8.1** ([Modrinth](https://modrinth.com/shader/complementary-unbound/version/VMHXIk50)) and put the `.zip` **as it is** (don't unzip it) into your instance's **`shaderpacks`** folder.
   - Download **Euphoria Patches 1.9.3 for Forge 1.7.10** ([Modrinth](https://modrinth.com/mod/euphoria-patches/version/KCj70UqE), file `EuphoriaPatcher-1.9.3-r5.8.1-forge1.7.10.jar`) and put it into your **`mods`** folder.
   - Start the game once. Euphoria Patches creates the shader pack folder **`ComplementaryUnbound_r5.8.1 + EuphoriaPatches_1.9.3`** in `shaderpacks`.
   - Use exactly these versions. Newer ones (Complementary r5.9+, Euphoria 1.10+) aren't supported yet.
1. Download **`GTNH-HDR-<version>.jar`** from the [Releases](../../releases) page.
2. Put it into your instance's **`mods`** folder. In Prism Launcher: right-click the instance → **Folder** → open **`mods`**.
3. Start the game.
4. Press **F12** → **Make HDR copy of shader pack**. This makes a copy of your Complementary + Euphoria pack called **`HDR ComplementaryUnbound_r5.8.1 + EuphoriaPatches_1.9.3`**. Your original pack isn't touched.
5. Go to **Options → Video Settings → Shaders** and pick the pack whose name starts with **HDR**.

## Controls

| Key | What it does |
|---|---|
| **F10** | Switch between HDR and **your old picture**, instantly |
| **F12** | The HDR settings screen. The world stays visible behind it, so you see each change live. |

## Performance

| | Cost |
|---|---|
| HDR step (measured in-game, RTX 3090, 2560×1440) | **0.050 ms per frame** |
| Extra CPU work | **none** |
| Extra video memory | about 45 MB |
| Shader pack change | about the same speed as the original pack. The HDR copy stores the picture in a buffer of the same size. |

<img src="https://cdn.discordapp.com/emojis/1322967256476553226.webp?size=300" width="64" alt="">

## Tested on (only one PC so far)

- NVIDIA **RTX 3090**
- a 400-nit-class QD-OLED monitor, 2560×1440 at 240 Hz
- **GTNH 2.9.0-RC-2** in Prism Launcher, Java 25
- Angelica 2.2.41, lwjgl3ify 3.0.37
- Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3

You can report back to me if stuff is working for you too :)

**Not tested yet:** AMD cards, other NVIDIA cards, laptops, Windows 10, other monitors (LCD, mini-LED, 1000+ nits), other launchers, other GTNH versions. Yet should work for most systems. Report issues otherwise

## Known limits

- **Windows only** (Windows 10 or 11, with Windows HDR turned on). Linux and macOS are not supported.
- Only for **Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3**
- **The loading screen during start-up** can look dimmer or different. It's correct once the main menu appears.
- **F2 screenshots** are saved as normal (SDR) pictures, so highlights look clipped in them.
- **In HDR, the patched pack always uses Complementary's standard tonemap curve.** If you picked a different tonemap in the Euphoria settings (ACES, AgX, …), that choice only applies when HDR is off. Contrast, saturation and the other colour settings still work.
- **Intel graphics** are not supported.
- **Laptops** where the screen is connected to an Intel chip may not work.

## For developers

- **Build:** JDK 21. Gradle downloads JDK 25 for its daemon itself (see `gradle/gradle-daemon-jvm.properties`).
  ```bash
  VERSION=0.1.0 ./gradlew build
  ```
  The jar is written to `build/libs/GTNH-HDR-0.1.0.jar`. Without `VERSION`, the GTNH buildscript takes the version from the latest git tag; outside a git repository it falls back to `NO-GIT-TAG-SET`.
- **How it works:** [docs/DESIGN.md](docs/DESIGN.md). **Shader API** (compatible with rrtt217's HDR mod): [docs/SHADER-API.md](docs/SHADER-API.md).
- **Shader patcher from the command line:** `java -cp GTNH-HDR-0.1.0.jar com.gtnhhdr.patch.HdrPackPatcher "<instance>/shaderpacks"`.
  - It finds the places to change by line fingerprints of the player's own files and only inserts its own lines.
  - It builds the HDR tonemap from the pack's own `DoCompTonemap`.
  - No Complementary or Euphoria code is in this repository.
- **Gotchas found while building:**
  1. lwjgl3ify rewrites every `org/lwjgl/` reference in mod classes to `org/lwjglx/`, unless the class has `@me.eigenraven.lwjgl3ify.api.Lwjgl3Aware`. Classes that call LWJGL 3 SDL directly need it.
  2. Angelica loads `StandardMacros` right after the GL context is made, before late mixins exist, so that hook must be an early mixin.
  3. 1.7.10 has no `Minecraft.createDisplay`. Hook `ForgeHooksClient.createDisplay` (Angelica `@Overwrite`s it) with priority > 1000.
  4. With `usesMixins=true` the buildscript writes `TweakClass: MixinTweaker` and `MixinConfigs` into the manifest. FML then skips `FMLCorePlugin` in that jar, so early mixins go through the manifest config.
  5. Angelica 2.2.41 runs a GL 4.6 core profile with fixed-function emulation, and mod GL11/GL20 calls go through GLSM. Raw GL before GLSM is ready: `SDL_GL_GetProcAddress` + `org.lwjgl.system.JNI`.
  6. GTNH's main menu is Custom Main Menu, not `GuiMainMenu`.
  7. A crash that kills the JVM during start-up loses the end of the log4j log, so the mod also writes `logs/GTNH-HDR.log` synchronously.
- **Robustness:**
  - All mixins are optional (`required: false`, `defaultRequire: 0`), so an Angelica update that moves a hooked method disables a feature instead of crashing.
  - Non-NVIDIA/AMD drivers turn the mod off.
- Contributions are welcome. Keep in mind the project itself was written with AI help.
- This is a standalone mod. Please don't send its code to Angelica or GTNH: Angelica doesn't accept AI-written contributions.

## Credits

- **rrtt217**:
  - [Minecraft HDR Mod](https://github.com/rrtt217/Minecraft-HDR-Mod) (MIT): the 16-bit window idea and the shader uniform names this mod follows.
  - [ComplementaryReimagined-hdr](https://github.com/rrtt217/ComplementaryReimagined-hdr): the HDR tonemap approach.
- **EminGT / Complementary Development**: [Complementary Shaders](https://www.complementary.dev).
- **SpacEagle17**: [Euphoria Patches](https://www.euphoriapatches.com).
- **The GTNH team**: Angelica, lwjgl3ify and the mod build template.
- **kurushimee**: the first GTNH HDR proof of concept.

None of them made or support this mod. **If you're one of them and want anything changed or removed, open an issue and it will be done.**

${\color{gray}\text{AI assistance was used in the making of this project!}}$

## Licence

[MIT](LICENSE). Use, copy, change and share it however you like, as long as the credit and licence text stay with it. Third-party notices: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

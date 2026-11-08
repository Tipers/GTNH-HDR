## Install

You need one of these two shader packs first. Click yours to see how to get it, or skip ahead if you already have it.

<details>
<summary><b>Eclipse</b> (optionally with Chroma Patch)</summary>

1. On [Merlin1809/Eclipse-Shader](https://github.com/Merlin1809/Eclipse-Shader), choose **Code → Download ZIP**.
2. Put the `.zip` **as it is** (don't unzip it) into your instance's **`shaderpacks`** folder.
3. Optional, for coloured GTNH lights: put the [Chroma Patch](https://github.com/Summxx/ChromaPatch) mod into your **`mods`** folder and start the game once. It creates **`Eclipse-Shader KuraEdit [OBS07-Chroma Patch].zip`** in `shaderpacks`. It needs GTNH 2.9.0 and Java 25.

</details>

<details>
<summary><b>Complementary + Euphoria Patches</b></summary>

1. Put [Complementary Unbound r5.8.1](https://modrinth.com/shader/complementary-unbound/version/VMHXIk50) **as a `.zip`** (don't unzip it) into your instance's **`shaderpacks`** folder.
2. Put [Euphoria Patches 1.9.3 for Forge 1.7.10](https://modrinth.com/mod/euphoria-patches/version/KCj70UqE) (`EuphoriaPatcher-1.9.3-r5.8.1-forge1.7.10.jar`) into your **`mods`** folder.
3. Start the game once. Euphoria Patches creates the shader pack folder in `shaderpacks`.

Use exactly these versions. Newer ones aren't supported yet.

</details>

Then install GTNH-HDR:

1. Download **`GTNH-HDR-<version>.jar`** from the [Releases](../../releases) page.
2. Put it into your instance's **`mods`** folder. In Prism Launcher: right-click the instance → **Folder** → open **`mods`**.
3. Start the game.
4. Press **F12** → **Make HDR copy of shader pack**. This copies the shader pack you have selected into a new one with **`HDR `** in front of its name. If the selected pack isn't supported, every supported pack gets a copy. Your original pack isn't touched, and packs stored as `.zip` work too.
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
| Shader pack change | about the same speed as the original pack. The Complementary copy stores the picture in a buffer of the same size. The Eclipse copy uses a 16-bit buffer the pack already has. |

<img src="https://cdn.discordapp.com/emojis/1322967256476553226.webp?size=300" width="64" alt="">

## Tested on (only one PC so far)

- NVIDIA **RTX 3090**
- a 400-nit-class QD-OLED monitor, 2560×1440 at 240 Hz
- Prism Launcher, Java 25
- **Eclipse + Chroma Patch** (my own edit of it) on **GTNH 2.9.0**, Angelica 2.2.43, lwjgl3ify 3.0.42
- **Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3** on **GTNH 2.9.0-RC-2**, Angelica 2.2.41, lwjgl3ify 3.0.37

You can report back to me if stuff is working for you too :)

Stock Eclipse and unedited Chroma Patch were only checked outside the game. The patch changes the same lines in them.

**Not tested yet:** AMD cards, other NVIDIA cards, laptops, Windows 10, other monitors (LCD, mini-LED, 1000+ nits), other launchers, other GTNH versions. Yet should work for most systems. Report issues otherwise

## Known limits

- **Windows only** (Windows 10 or 11, with Windows HDR turned on). Linux and macOS are not supported.
- Only for **Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3** and **Eclipse** (the October 2026 Unstable version, with or without Chroma Patch). If a pack's files change, the copy button refuses it instead of making a broken copy.
- **The loading screen during start-up** can look dimmer or different. It's correct once the main menu appears.
- **F2 screenshots** are saved as normal (SDR) pictures, so highlights look clipped in them.
- **In HDR, the patched pack always uses its standard tonemap curve** (Complementary's own curve, or AgX on Eclipse). If you picked a different tonemap in the shader settings, that choice only applies when HDR is off. Contrast, saturation and the other colour settings still work.
- **Intel graphics** are not supported.
- **Laptops** where the screen is connected to an Intel chip may not work.

## For developers

- **Build:** JDK 21. Gradle downloads JDK 25 for its daemon itself (see `gradle/gradle-daemon-jvm.properties`).
  ```bash
  VERSION=0.2.0 ./gradlew build
  ```
  The jar is written to `build/libs/GTNH-HDR-0.2.0.jar`. Without `VERSION`, the GTNH buildscript takes the version from the latest git tag; outside a git repository it falls back to `NO-GIT-TAG-SET`.
- **How it works:** [docs/DESIGN.md](docs/DESIGN.md). **Shader API** (compatible with rrtt217's HDR mod): [docs/SHADER-API.md](docs/SHADER-API.md).
- **Shader patcher from the command line:** `java -cp GTNH-HDR-0.2.0.jar com.gtnhhdr.patch.HdrPackPatcher "<instance>/shaderpacks"`.
  - It finds the places to change by line fingerprints of the player's own files and only inserts its own lines.
  - It builds the HDR tonemap from the pack's own function (`DoCompTonemap`, or `ToneMap_AgX_minimal_merlin` on Eclipse).
  - No Complementary, Euphoria or Eclipse code is in this repository.
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
- **Merlin1809**: Eclipse Shader, and **Chocapic13**, whose work it builds on.
- **OBS07 and KuramA**: Chroma Patch.
- **The GTNH team**: Angelica, lwjgl3ify and the mod build template.
- **kurushimee**: the first GTNH HDR proof of concept.

None of them made or support this mod. **If you're one of them and want anything changed or removed, open an issue and it will be done.**

${\color{gray}\text{AI assistance was used in the making of this project!}}$

## Licence

[MIT](LICENSE). Use, copy, change and share it however you like, as long as the credit and licence text stay with it. Third-party notices: [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

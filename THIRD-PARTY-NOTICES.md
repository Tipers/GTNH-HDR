# Third-party notices

## rrtt217 – Minecraft HDR Mod

GTNH-HDR follows the design of rrtt217's Minecraft HDR Mod: a 16-bit float window that the graphics driver presents as scRGB HDR. It also implements the same shader-pack interface (`HDR_MOD_INSTALLED`, `HDR_ENABLED`, `HdrGamePeakBrightness`, `HdrGamePaperWhiteBrightness`, `HdrGameMinimumBrightness`, `HdrUIBrightness`), so packs written for it can work here too.

<https://github.com/rrtt217/Minecraft-HDR-Mod>

```
MIT License

Copyright (c) 2026 rrtt217

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Complementary Shaders and Euphoria Patches

Complementary Shaders (EminGT / Complementary Development, <https://www.complementary.dev>) and Euphoria Patches (SpacEagle17, <https://www.euphoriapatches.com>) are under their own licences. **None of their code is included in this repository or in the mod jar.**

The in-game "Make HDR copy of shader pack" button works on the player's own copy of the pack:
- it finds the places to change by fingerprints of the lines in that copy
- it inserts only GTNH-HDR's own lines
- it builds the HDR tonemap by copying the pack's own `DoCompTonemap` from the player's `tonemaps.glsl` and swapping a few of its lines for GTNH-HDR's own

The result is a new folder on the player's PC; the original pack is never modified. The HDR tonemap approach follows rrtt217's ComplementaryReimagined-hdr (<https://github.com/rrtt217/ComplementaryReimagined-hdr>).

## Built with

Forge 1.7.10, the GTNH buildscript (GTNHGradle, RetroFuturaGradle) and the Gradle wrapper (Apache-2.0). Compiled against Angelica (LGPL-3.0), lwjgl3ify and LWJGL 3 (BSD-3-Clause). None of these are bundled in the jar.

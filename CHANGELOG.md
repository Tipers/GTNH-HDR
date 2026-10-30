# Changelog

## 0.1.0 – first public test release

**New**
- Real HDR output through a 16-bit float window (scRGB), with the HDR step folded into Minecraft's existing final copy. It costs about 0.05 ms per frame on an RTX 3090.
- **F10**: instant switch between HDR and your old picture.
- **F12** settings screen:
  - split screen and brightness heat map
  - World brightness (automatic by default) and Brightest highlights, with a test square
  - **Make HDR copy of shader pack** for Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3
  - **Copy diagnostics** for bug reports
- Shader-pack interface compatible with rrtt217's Minecraft HDR Mod.

**Safety**
- Falls back to a normal picture when Windows HDR is off, when the window can't be 16-bit, or on Intel graphics.
- An Angelica update that moves the code GTNH-HDR hooks into disables the affected feature instead of crashing.
- Checks every monitor, not only the main one.
- Assumes 600 nits if the monitor doesn't report its brightness.

**Known problems**
- Tested on one PC only (RTX 3090, Windows 11, QD-OLED). AMD is untested.
- The start-up loading screen can look dimmer.
- F2 screenshots are SDR.

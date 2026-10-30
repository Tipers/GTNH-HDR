# Shader API (for shader pack authors)

GTNH-HDR uses the same macro and uniform names as [rrtt217's Minecraft HDR Mod](https://github.com/rrtt217/Minecraft-HDR-Mod/wiki/For-Shaderpack-Devs). A pack written for that mod can support GTNH-HDR with few or no changes.

## Macros

| Macro | When it's defined |
|---|---|
| `HDR_MOD_INSTALLED` | Always, while GTNH-HDR is loaded |
| `HDR_MOD_ANGELICA` | Always (tells you it's the Angelica / GTNH version) |
| `GTNH_HDR_VERSION` | Always, currently `1` |
| `HDR_ENABLED` | Only when the 16-bit HDR window is really active. Put all HDR code behind `#ifdef HDR_ENABLED`, so the pack is unchanged without HDR. |

## Uniforms (all `float`, in nits)

| Uniform | Meaning |
|---|---|
| `HdrGamePeakBrightness` | Brightest highlight: the monitor's peak, or the player's setting |
| `HdrGamePaperWhiteBrightness` | World brightness: how bright a white surface should be (your pack's "1.0") |
| `HdrGameMinimumBrightness` | Black level (0) |
| `HdrUIBrightness` | Menu/GUI white: the player's Windows SDR brightness |
| `HdrPreviewSDR` | GTNH-HDR only: `1` while the player is comparing with the old picture (F10). Output your normal SDR picture then. |
| `HdrSplitX` | GTNH-HDR only: screen x (0..1) from which the old SDR picture should be shown in split view; `1` = nowhere |

## What your final pass should write

After your last pass, Minecraft draws the GUI on top. GTNH-HDR then turns the result into HDR for Windows.

- **Write sRGB-encoded colour where 1.0 = UI white** (`HdrUIBrightness`).
  - A world pixel at paper white is therefore `sRGB(HdrGamePaperWhiteBrightness / HdrUIBrightness)`.
  - Highlights go above 1.0, up to `sRGB(HdrGamePeakBrightness / HdrUIBrightness)`.
- Minecraft's framebuffer is 16-bit float while HDR is active, so values above 1.0 survive. Skip any 8-bit dithering in HDR.
- Pixels shown as "old picture" (`HdrPreviewSDR`, `HdrSplitX`) should be your normal SDR output (0..1). GTNH-HDR maps them exactly the way Windows maps SDR content.

The Complementary patch is a working example. After "Make HDR copy", look at `lib/misc/hdrOutput.glsl` and the lines marked `GTNH-HDR patch` in the HDR copy.

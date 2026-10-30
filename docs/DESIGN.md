# How GTNH-HDR works

## The idea

On Windows with HDR on, NVIDIA and AMD drivers show an OpenGL window with a **16-bit float pixel format** as **scRGB**: linear light where 1.0 = 80 nits, and values above 1.0 are brighter. No DirectX layer is needed. rrtt217's Minecraft HDR Mod uses the same method on NVIDIA and AMD.

This was checked by drawing patches meant to be 80, 240 and 400 nits and reading back what Windows actually sends to the monitor (Desktop Duplication in FP16). They arrived as 80, 240 and 400 nits. A normal 8-bit window clips all three at the SDR white level.

## Frame flow

```
Complementary passes ... composite5: HDR tonemap, ending at peak / paper white,
    stored as sRGB(L / P) + 10-bit dither  ──►  colortex3 (RGB10_A2)
  ──► TAA / FXAA unchanged (they still see 0..1)
  ──► final: decode × P, clamp to peak, × (paper white / UI white), sRGB encode
           ──►  Minecraft framebuffer (RGBA16F instead of RGBA8)
  ──► GUI and HUD drawn on top, unchanged (sRGB 0..1)
  ──► GTNH-HDR's encode inside Minecraft's existing fullscreen copy:
           sRGB decode × (UI white / 80)  ──►  window back buffer (FP16 = scRGB)
  ──► SwapBuffers ──► Windows shows it as HDR
```

`P` = peak ÷ paper white. The number of passes is the same as without the mod. The cost is just the wider formats plus a few instructions in the copy that already exists.

## Where it hooks in

| What | Where |
|---|---|
| 16-bit float window | lwjgl3ify's `DisplayEvents` pre-window-create listener: `SDL_GL_FLOATBUFFERS` + 16-bit RGBA sizes. Registered from `ForgeHooksClient.createDisplay` HEAD (early mixin, priority above Angelica's `@Overwrite`). |
| Check it worked | `createDisplay` RETURN: `glGetFramebufferAttachmentParameteriv(GL_BACK_LEFT, COMPONENT_TYPE)` must be `GL_FLOAT` with 16-bit red, and the driver must be NVIDIA or AMD. Otherwise the mod stays off. |
| HDR encode | `Framebuffer.framebufferRender` HEAD (Angelica `@Overwrite`s it): our small program draws the same fullscreen triangle. |
| 16-bit main framebuffer | `Framebuffer.createFramebuffer` RETURN: the colour texture is re-specified as `GL_RGBA16F`. The texture name stays the same, so every FBO that has it attached keeps working. |
| Shader macros and uniforms | Angelica's Iris classes `StandardMacros.createStandardEnvironmentDefines` and `IrisExclusiveUniforms.addIrisExclusiveUniforms` |
| Monitor info | SDL window properties `SDL_PROP_WINDOW_SDR_WHITE_LEVEL_FLOAT`, `SDL_PROP_WINDOW_HDR_HEADROOM_FLOAT` and `SDL_PROP_WINDOW_HDR_ENABLED_BOOLEAN`, read once a second and on SDL display/HDR events. |

All mixins are optional, so a changed target disables that feature instead of crashing the game.

## Brightness model

- **UI white:** Windows' SDR content brightness (read through SDL). Menus therefore match the desktop.
- **Peak:** what the monitor reports to Windows. If it reports nothing usable, 600 nits.
- **Paper white:** automatic = UI white, but at most half the peak (at least 80). The player can change it in F12.
- **Windows HDR off:** while the game runs, everything maps as SDR (UI white = 80 nits on the scRGB scale = SDR white).

## Why not the other ways

| Method | Why not |
|---|---|
| OpenGL → DirectX 11 interop + D3D11 swapchain | Measured 8–9× the GPU overhead, plus 0.2–0.3 ms render-thread CPU per frame for the interop lock. It also needs native code and swapchain handling. |
| Angelica's SDL GPU (Vulkan) backend | 30–40% slower today, and it requests an SDR swapchain. |
| 10-bit UNORM window | That's SDR "deep colour", not HDR. |
| RTX HDR, Auto HDR, Special K | Guesses HDR from an 8-bit SDR picture: more cost, banding, and no way to recover detail the tonemap already threw away. |

## Shader patch

See [THIRD-PARTY-NOTICES.md](../THIRD-PARTY-NOTICES.md) and `src/main/java/com/gtnhhdr/patch/HdrPackPatcher.java`. It makes eight insertions in four files, adds `lib/misc/hdrOutput.glsl` (ours), and builds `lib/colors/hdrTonemap.glsl` from the pack's own `DoCompTonemap`. All of it is behind `#ifdef HDR_ENABLED`.

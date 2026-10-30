# Performance

All numbers in the first two sections were measured on one PC: RTX 3090 (driver 617.42), Windows 11 with HDR on, 2560×1440 at 240 Hz.

## What GTNH-HDR adds per frame

| Part | GPU | CPU | Video memory |
|---|---|---|---|
| Window becomes 16-bit float, and the HDR step is done inside Minecraft's existing final copy (measured in-game with GPU timer queries) | **0.050 ms** | none | +29.5 MB |
| Minecraft's main framebuffer 8-bit → 16-bit float, so highlights above white survive until the HDR step | ≈ 0.02–0.03 ms | none | +14.7 MB |
| Shader pack: final colour buffer RGBA8 → RGB10_A2 (same 4 bytes per pixel) | ≈ 0 | none | 0 |
| Shader pack: HDR tonemap instead of the SDR one | ≈ ±0.01 ms | none | 0 |
| Brightness and monitor info | — | a few µs, checked once a second | — |

**In total: about 0.05–0.08 ms per frame and no CPU work.** Big GTNH bases are usually limited by the CPU, so the GPU cost often doesn't show up as lost FPS at all.

## Compared with other ways of getting HDR

Measured on the same PC with standalone test programs (full-screen 1440p, near-empty scene):

| Method | Extra time per frame | Extra CPU time per frame |
|---|---|---|
| **16-bit float window (GTNH-HDR)** | **+0.04 ms** | **none** |
| OpenGL → DirectX 11 interop, FP16 swapchain | +0.39 ms | 0.22–0.28 ms |
| OpenGL → DirectX 11 interop, 10-bit HDR10 swapchain | +0.33 ms | 0.20–0.24 ms |
| RTX HDR / NVIDIA TrueHDR | an AI filter on every frame, which reconstructs HDR from an 8-bit SDR picture | — |

## Other PCs (estimates, not measured)

- **The cost grows with resolution.** 4K has 2.25× the pixels of 1440p, so expect roughly 0.1 ms.
- **The cost is higher on slower cards**, because it depends on memory bandwidth. Older or entry-level cards may reach about 0.3 ms.
- **CPU cost stays at zero** on every system.

## Measure it yourself

- Press **F12**: the bottom lines show the live GPU cost of the HDR step and of the shader pack's final pass.
- For an FPS comparison, stand in the same spot with the same view and settings. Note the F3 FPS over about 30 seconds with the mod installed, then again with the mod removed.

If you'd like to help, paste your F12 cost line and your graphics card into an issue. Results from other cards will be added here, separate from the numbers above.

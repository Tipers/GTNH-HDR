# hdrmeter

A small Windows tool that reads back what Windows actually sends to an HDR monitor (Desktop Duplication, FP16 scRGB) for one window, and prints brightness statistics in nits. It's useful to check that menus land at the Windows SDR white level and how bright highlights really get.

It works for windowed and composited borderless windows. A full-screen window that bypasses Windows' compositor is invisible to it.

Build (MinGW-w64 g++ or similar):

```
g++ -O2 -std=c++17 -o hdrmeter.exe hdrmeter.cpp -ld3d11 -ldxgi -luser32 -ldwmapi
```

Usage:

```
hdrmeter once "<part of the window title>" [paperWhiteNits]
```

Source only; no prebuilt binary is provided.

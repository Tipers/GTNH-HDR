# FAQ

**Does it make the game slower?**
Barely. The HDR step measured 0.050 ms per frame on an RTX 3090 at 1440p, and it adds no CPU work. The F12 screen shows the cost on your PC. See [PERFORMANCE.md](PERFORMANCE.md).

**Nothing looks different.**
1. Check that Windows HDR is on (Settings → System → Display → "Use HDR"), then restart the game.
2. Check that you picked the shader pack whose name starts with **HDR** (made with F12 → Make HDR copy of shader pack).
3. Press F12. The lines at the bottom say whether the 16-bit window is active and whether the pack's HDR patch is in use.

**"Make HDR copy" says my pack isn't supported.**
Only **Complementary Unbound r5.8.1 + Euphoria Patches 1.9.3** works, the version GTNH 2.9's EuphoriaPatcher makes. Other versions need a GTNH-HDR update. Packs stored as `.zip` need to be unzipped into a folder first.

**Menus are too bright or too dim.**
They follow Windows' "SDR content brightness" slider (Settings → Display → HDR). Change it there, or set `uiNits` in `config/GTNH-HDR.cfg`.

**The world looks darker than before.**
Raise **World brightness** in F12. When it's equal to your Windows SDR brightness, everyday scenes match your old picture, but there's less room left for highlights.

**Bright daytime scenes dim by themselves.**
That's your monitor protecting itself; most OLEDs and many LCDs do it. Lower World brightness a little.

**Menus look blown-out white, or the colours look badly wrong.**
Your graphics driver doesn't show the 16-bit window as HDR. Remove the mod (or set `enabled=false` in `config/GTNH-HDR.cfg`) and please open an issue with your diagnostics.

**Can I use RTX HDR, Windows Auto HDR, ReShade or Special K too?**
No. Turn them off for Minecraft: they'd try to make an already-HDR picture "HDR" again.

**Does it work with other shader packs?**
They work normally, but without HDR highlights. Pack authors can add support themselves: see [SHADER-API.md](SHADER-API.md). It uses the same names as rrtt217's HDR mod.

**I picked another tonemap in the Euphoria settings and HDR ignores it.**
Yes. In HDR the patched pack always uses Complementary's standard curve, because the other tonemaps are built for SDR. With HDR off (F10) your chosen tonemap is used.

**F12 / F10 clash with another program or mod.**
Change them in Options → Controls → GTNH-HDR. Note that F12 is also Steam's default screenshot key.

**Does it work on Intel graphics, Linux or macOS?**
No.

**Forge says there are two copies of the mod.**
Remove the older jar from `mods`. Only one GTNH-HDR jar may be there.

**Will it keep working after GTNH, Angelica or Complementary updates?**
Maybe not straight away. If Angelica changes, GTNH-HDR switches parts of itself off rather than crashing. If Complementary or Euphoria changes, the copy button refuses the new version. Watch the Releases page.

**Is this part of GTNH or Angelica?**
No. It's an unofficial mod written with AI help. Please don't report its problems to them.

**How do I remove it?**
Delete the jar, pick your original shader pack again, and optionally delete the `HDR …` pack folder and `config/GTNH-HDR.cfg`.

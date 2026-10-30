package com.gtnhhdr.client;

import java.io.File;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** config/GTNH-HDR.cfg. Loaded on first use (window creation), before FML's preInit. */
public final class HdrConfig {

    private static Configuration cfg;
    private static Property pEnabled, pUiNits, pPaperWhite, pPeakNits, pGamma22;

    public static boolean enabled = true;
    /** Nits of GUI/menu white. -1 = follow the Windows "SDR content brightness" setting. */
    public static float uiNits = -1f;
    /** Nits of a white surface in the world (the shader pack's "1.0"). -1 = automatic (see HdrState.paperWhiteNits). */
    public static float paperWhiteNits = -1f;
    /** Brightest highlight. -1 = what the monitor reports to Windows. */
    public static float peakNits = -1f;
    /** Decode SDR with a pure 2.2 gamma instead of the Windows sRGB curve (deeper blacks). */
    public static boolean sdrGamma22 = false;

    private HdrConfig() {}

    public static synchronized void load() {
        if (cfg != null) return;
        cfg = new Configuration(new File(Launch.minecraftHome, "config/GTNH-HDR.cfg"));
        final String g = "general";
        cfg.addCustomCategoryComment(g, "GTNH-HDR. F10 = HDR on / old picture, F12 = HDR settings screen (view, brightness, peak). "
            + "Keys can be changed in Options > Controls > GTNH-HDR.");
        pEnabled = cfg.get(g, "enabled", true, "Create a 16-bit float (HDR) window when Windows HDR is on. Restart to apply.");
        pUiNits = cfg.get(g, "uiNits", -1.0, "Menu/GUI white in nits. -1 = same as the Windows 'SDR content brightness' slider.");
        pPaperWhite = cfg.get(g, "paperWhiteNits", -1.0,
            "World brightness: nits of a white surface in daylight. Higher = brighter overall, less room for highlights. "
                + "-1 = automatic: the Windows SDR brightness, but at most half the monitor's peak.");
        pPeakNits = cfg.get(g, "peakNits", -1.0,
            "Brightest highlight in nits. -1 = what the monitor reports to Windows (600 if it reports nothing usable).");
        pGamma22 = cfg.get(g, "sdrGamma22", false, "Decode menus/SDR with pure gamma 2.2 instead of the Windows sRGB curve.");
        enabled = pEnabled.getBoolean();
        uiNits = (float) pUiNits.getDouble();
        paperWhiteNits = (float) pPaperWhite.getDouble();
        peakNits = (float) pPeakNits.getDouble();
        sdrGamma22 = pGamma22.getBoolean();
        if (cfg.hasCategory("debug")) cfg.removeCategory(cfg.getCategory("debug"));
        if (cfg.hasChanged()) cfg.save();
    }

    /** Turn the mod off for the next start (unsupported graphics). */
    public static synchronized void disable() {
        enabled = false;
        if (cfg == null) return;
        pEnabled.set(false);
        cfg.save();
    }

    public static synchronized void saveBrightness() {
        if (cfg == null) return;
        pPaperWhite.set(paperWhiteNits);
        pPeakNits.set(peakNits);
        cfg.save();
    }
}

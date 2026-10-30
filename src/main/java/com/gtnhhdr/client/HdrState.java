package com.gtnhhdr.client;

/**
 * Everything the encode pass, the shader uniforms and the overlay need, in one place. Written on the client thread
 * (keys, once-a-second display refresh); read on the same thread by the renderer.
 */
public final class HdrState {

    private HdrState() {}

    /** Float window verified: HDR pipeline (macros, 16-bit main framebuffer, encode pass) is on. Fixed after startup. */
    static volatile boolean active;

    // What Windows reports for the window's monitor (via SDL).
    static volatile boolean displayHdr;
    static volatile float sdrWhiteNits = 80f;
    static volatile float monitorPeakNits = 80f;

    /** False when the monitor reported no usable peak and a typical value is assumed. */
    static volatile boolean peakReported = true;
    // OpenGL driver strings, for the Intel check and the diagnostics.
    static volatile String glVendor = "?", glRenderer = "?", glVersion = "?";

    // Live views.
    static volatile boolean sdrPreview;
    static volatile boolean split;
    static volatile boolean heatMap;
    static volatile boolean info;
    /** System.nanoTime() until which the peak test square is shown. */
    static volatile long peakBoxUntil;
    /** Last time a shader program asked for an HDR uniform (= the pack is HDR-patched). */
    static volatile long packUsedNanos;

    public static boolean isActive() {
        return active;
    }

    /** True while Windows HDR is on for our monitor; otherwise everything behaves as plain SDR. */
    public static boolean hdrOutput() {
        return active && displayHdr;
    }

    public static float uiNits() {
        if (!hdrOutput()) return 80f;
        return HdrConfig.uiNits > 0 ? HdrConfig.uiNits : sdrWhiteNits;
    }

    public static float peakNits() {
        if (!hdrOutput()) return uiNits();
        final float p = HdrConfig.peakNits > 0 ? HdrConfig.peakNits : monitorPeakNits;
        return Math.max(p, 80f);
    }

    /** World paper white. Automatic default: the Windows SDR white, but at most half the peak so highlights keep room. */
    public static float paperWhiteNits() {
        if (!hdrOutput()) return uiNits();
        final float p = HdrConfig.paperWhiteNits > 0 ? HdrConfig.paperWhiteNits : autoPaperWhiteNits();
        return Math.max(40f, Math.min(p, peakNits()));
    }

    public static float autoPaperWhiteNits() {
        return Math.max(80f, Math.min(uiNits(), peakNits() * 0.5f));
    }

    /** Show "today's SDR" for the whole screen (HDR off, or the SDR preview toggle). */
    public static boolean showSdr() {
        return !hdrOutput() || sdrPreview;
    }

    /** Fraction of the screen width from which the SDR picture is shown; 1 = none. */
    public static float splitX() {
        if (showSdr()) return 0f;
        return split ? 0.5f : 1f;
    }

    public static boolean peakBoxVisible() {
        return hdrOutput() && !sdrPreview && System.nanoTime() < peakBoxUntil;
    }

    // ---- Uniform suppliers (Iris calls these once per frame for programs that use them) ----

    public static float uniformPeak() {
        packUsedNanos = System.nanoTime();
        return peakNits();
    }

    public static float uniformPaperWhite() {
        return paperWhiteNits();
    }

    public static float uniformUi() {
        return uiNits();
    }

    public static float uniformPreviewSdr() {
        return showSdr() ? 1f : 0f;
    }

    public static float uniformSplitX() {
        return splitX();
    }

    public static boolean packIsHdrPatched() {
        return System.nanoTime() - packUsedNanos < 2_000_000_000L;
    }
}

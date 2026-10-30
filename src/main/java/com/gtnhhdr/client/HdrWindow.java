package com.gtnhhdr.client;

import static org.lwjgl.sdl.SDLVideo.*;

import java.nio.IntBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_EventFilter;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import me.eigenraven.lwjgl3ify.api.DisplayEvents;
import me.eigenraven.lwjgl3ify.api.DisplayWindowContext;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;

/**
 * The float (FP16) window. On NVIDIA/AMD under Windows HDR, a WGL pixel format with 16-bit float channels is presented
 * by the driver as scRGB (1.0 = 80 nits, linear) - real HDR with no DirectX and no extra copy.
 * {@code @Lwjgl3Aware}: uses LWJGL 3 SDL directly, so lwjgl3ify must not remap its org.lwjgl references.
 */
@Lwjgl3Aware
public final class HdrWindow {

    public static final Logger LOG = LogManager.getLogger("GTNH-HDR");

    private static final int GL_FRAMEBUFFER = 0x8D40, GL_BACK_LEFT = 0x0402, GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int GL_COMPONENT_TYPE = 0x8211, GL_RED_SIZE = 0x8212, GL_FLOAT = 0x1406;

    private static boolean registered;
    private static volatile boolean requested;
    private static volatile boolean refreshWanted = true;
    private static SDL_EventFilter eventWatch; // strong ref: the native callback must outlive us
    private static long lastRefresh;

    private HdrWindow() {}

    /** ForgeHooksClient.createDisplay HEAD. */
    public static void registerBeforeDisplayCreate() {
        if (registered) return;
        registered = true;
        HdrEarlyLog.log("GTNH-HDR: before window creation");
        HdrConfig.load();
        if (!HdrConfig.enabled) {
            say("Disabled in config/GTNH-HDR.cfg - normal SDR window.");
            return;
        }
        if (!System.getProperty("os.name", "")
            .toLowerCase()
            .contains("win")) {
            say("Not Windows - normal SDR window.");
            return;
        }
        try {
            DisplayEvents.addPreWindowCreateListener(HdrWindow::onPreWindowCreate);
            DisplayEvents.addPostWindowCreateListener(ctx -> created = true);
        } catch (Throwable t) {
            HdrEarlyLog.log("lwjgl3ify DisplayEvents not available - normal SDR window", t);
        }
    }

    private static volatile boolean created, floatFailed;

    /** Runs on lwjgl3ify's SDL thread, after its own SDL_GL_SetAttribute calls, right before the window is made. */
    private static void onPreWindowCreate(DisplayWindowContext ctx) {
        try {
            // Angelica retries Display.create (other GL versions) when it fails; if our float attempt failed,
            // the retry gets a plain window so the game always starts.
            if (requested && !created) floatFailed = true;
            requested = false;
            created = false;
            if (!ctx.glContextEnabled()) return; // Angelica's SDL-GPU path owns the window
            if (floatFailed) {
                say("The float window could not be created - using the normal 8-bit window.");
                return;
            }
            if (!anyDisplayHdr()) {
                say("Windows HDR is off on every monitor - keeping the normal 8-bit window.");
                return;
            }
            boolean ok = SDL_GL_SetAttribute(SDL_GL_FLOATBUFFERS, 1);
            ok &= SDL_GL_SetAttribute(SDL_GL_RED_SIZE, 16);
            ok &= SDL_GL_SetAttribute(SDL_GL_GREEN_SIZE, 16);
            ok &= SDL_GL_SetAttribute(SDL_GL_BLUE_SIZE, 16);
            ok &= SDL_GL_SetAttribute(SDL_GL_ALPHA_SIZE, 16);
            // sRGB-capable float formats don't exist; lwjgl3ify disables GL_FRAMEBUFFER_SRGB anyway.
            ok &= SDL_GL_SetAttribute(SDL_GL_FRAMEBUFFER_SRGB_CAPABLE, 0);
            requested = ok;
            say("Requested a 16-bit float (scRGB) window: " + (ok ? "ok" : "SDL refused an attribute"));
        } catch (Throwable t) {
            HdrEarlyLog.log("pre-window-create failed", t);
        }
    }

    /** True if Windows HDR is on for any connected monitor (the game may open on, or move to, any of them). */
    private static boolean anyDisplayHdr() {
        final IntBuffer displays = SDL_GetDisplays();
        if (displays == null) {
            final int primary = SDL_GetPrimaryDisplay();
            return primary != 0
                && SDLProperties.SDL_GetBooleanProperty(SDL_GetDisplayProperties(primary), SDL_PROP_DISPLAY_HDR_ENABLED_BOOLEAN, false);
        }
        try {
            boolean any = false;
            for (int i = displays.position(); i < displays.limit(); i++) {
                final int id = displays.get(i);
                final boolean hdr = SDLProperties
                    .SDL_GetBooleanProperty(SDL_GetDisplayProperties(id), SDL_PROP_DISPLAY_HDR_ENABLED_BOOLEAN, false);
                HdrEarlyLog.log("Monitor " + (i + 1) + " (" + SDL_GetDisplayName(id) + "): Windows HDR " + (hdr ? "on" : "off"));
                any |= hdr;
            }
            return any;
        } finally {
            org.lwjgl.sdl.SDLStdinc.SDL_free(displays);
        }
    }

    static void say(String s) {
        HdrEarlyLog.log(s);
        LOG.info(s);
    }

    /** ForgeHooksClient.createDisplay RETURN, client thread, GL context current. */
    public static void verifyAfterDisplayCreate() {
        HdrEarlyLog.log("after window creation: requested=" + requested + " created=" + created);
        if (!requested || HdrState.active) return;
        try {
            final long getIv = SDL_GL_GetProcAddress("glGetIntegerv");
            final long getAttach = SDL_GL_GetProcAddress("glGetFramebufferAttachmentParameteriv");
            if (getIv == 0 || getAttach == 0) {
                LOG.warn("Could not query the window format - staying SDR.");
                return;
            }
            // Raw calls (not through Angelica's state manager, which may not be initialised yet). Read-only.
            try (MemoryStack s = MemoryStack.stackPush()) {
                final IntBuffer v = s.mallocInt(1);
                JNI.callPV(GL_DRAW_FRAMEBUFFER_BINDING, MemoryUtil.memAddress(v), getIv);
                if (v.get(0) != 0) {
                    LOG.warn("Default framebuffer not bound at window creation - cannot verify, staying SDR.");
                    return;
                }
                JNI.callPV(GL_FRAMEBUFFER, GL_BACK_LEFT, GL_COMPONENT_TYPE, MemoryUtil.memAddress(v), getAttach);
                final int type = v.get(0);
                JNI.callPV(GL_FRAMEBUFFER, GL_BACK_LEFT, GL_RED_SIZE, MemoryUtil.memAddress(v), getAttach);
                final int red = v.get(0);
                final boolean isFloat = type == GL_FLOAT && red >= 16;
                say(String.format("Default framebuffer: %s %d-bit -> %s", type == GL_FLOAT ? "FLOAT" : String.format("0x%04X", type),
                    red, isFloat ? "HDR output active (scRGB)" : "not float, staying SDR"));
                HdrState.active = isFloat;
                readGlStrings(s);
            }
        } catch (Throwable t) {
            HdrEarlyLog.log("Window format check failed - staying SDR", t);
            HdrState.active = false;
        }
        if (HdrState.active && !supportedGpu()) {
            HdrState.active = false;
            say("Graphics: " + HdrState.glVendor + " / " + HdrState.glRenderer
                + " - only NVIDIA and AMD are supported. GTNH-HDR turned itself off (enabled=false in config/GTNH-HDR.cfg).");
            HdrConfig.disable();
        }
        if (HdrState.active) {
            installEventWatch();
            try {
                refresh(true);
            } catch (Throwable t) {
                HdrEarlyLog.log("Reading the monitor's HDR info failed", t);
            }
        }
    }

    private static void readGlStrings(MemoryStack s) {
        final long getString = SDL_GL_GetProcAddress("glGetString");
        if (getString == 0) return;
        HdrState.glVendor = glString(getString, 0x1F00);
        HdrState.glRenderer = glString(getString, 0x1F01);
        HdrState.glVersion = glString(getString, 0x1F02);
        say("Graphics: " + HdrState.glVendor + " / " + HdrState.glRenderer + " / " + HdrState.glVersion);
    }

    private static String glString(long fn, int name) {
        final long p = JNI.callP(name, fn);
        return p == 0 ? "?" : MemoryUtil.memUTF8(p);
    }

    /** NVIDIA and AMD present a float window as scRGB HDR. Intel (and anything unknown) is not supported. */
    private static boolean supportedGpu() {
        final String v = HdrState.glVendor.toLowerCase();
        return v.contains("nvidia") || v.contains("ati technologies") || v.contains("amd") || v.contains("advanced micro devices");
    }

    private static void installEventWatch() {
        try {
            eventWatch = SDL_EventFilter.create((userdata, event) -> {
                final int type = MemoryUtil.memGetInt(event);
                if (type == SDLEvents.SDL_EVENT_WINDOW_HDR_STATE_CHANGED || type == SDLEvents.SDL_EVENT_WINDOW_DISPLAY_CHANGED
                    || type == SDLEvents.SDL_EVENT_DISPLAY_CURRENT_MODE_CHANGED) {
                    refreshWanted = true;
                }
                return true;
            });
            SDLEvents.SDL_AddEventWatch(eventWatch, 0L);
        } catch (Throwable t) {
            LOG.warn("Could not watch SDL HDR events ({}); polling once a second instead.", t.toString());
        }
    }

    /** Called every client tick; cheap unless something changed or a second passed. */
    public static void tick() {
        if (!HdrState.active) return;
        final long now = System.nanoTime();
        if (refreshWanted || now - lastRefresh > 1_000_000_000L) refresh(false);
    }

    private static void refresh(boolean log) {
        refreshWanted = false;
        lastRefresh = System.nanoTime();
        final long window = org.lwjglx.opengl.Display.getWindow();
        if (window == 0) return;
        final int props = SDL_GetWindowProperties(window);
        final boolean hdr = SDLProperties.SDL_GetBooleanProperty(props, SDL_PROP_WINDOW_HDR_ENABLED_BOOLEAN, false);
        final float white = SDLProperties.SDL_GetFloatProperty(props, SDL_PROP_WINDOW_SDR_WHITE_LEVEL_FLOAT, 1f);
        final float headroom = SDLProperties.SDL_GetFloatProperty(props, SDL_PROP_WINDOW_HDR_HEADROOM_FLOAT, 1f);
        final float whiteNits = Math.max(white, 1f) * 80f;
        float peak = whiteNits * Math.max(headroom, 1f);
        // Monitors that report no usable peak give headroom 1 (peak = SDR white) or nonsense; assume a typical one.
        final boolean unknownPeak = hdr && (headroom <= 1.01f || peak < 200f || peak > 10000f);
        if (unknownPeak) peak = Math.max(600f, whiteNits * 1.5f);
        HdrState.peakReported = !unknownPeak;
        if (log || hdr != HdrState.displayHdr || Math.abs(whiteNits - HdrState.sdrWhiteNits) > 0.5f
            || Math.abs(peak - HdrState.monitorPeakNits) > 0.5f) {
            say(String.format("Monitor: Windows HDR %s, SDR white %d nits, peak %d nits%s (headroom %.2f)", hdr ? "on" : "off",
                Math.round(whiteNits), Math.round(peak), unknownPeak ? " (not reported by the monitor - assumed)" : "",
                headroom));
        }
        HdrState.displayHdr = hdr;
        HdrState.sdrWhiteNits = whiteNits;
        HdrState.monitorPeakNits = peak;
    }
}

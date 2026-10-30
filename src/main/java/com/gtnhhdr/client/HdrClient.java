package com.gtnhhdr.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.gtnhhdr.Tags;
import com.gtnhhdr.patch.HdrPackPatcher;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Keys, the on-screen messages and the info/cost overlay. */
public final class HdrClient {

    static HdrClient INSTANCE;

    private static final String CAT = "key.categories.gtnhhdr";
    // Defaults avoid keys GTNH already uses (and F8, which 1.7.10 hard-codes to smooth camera) and need no numpad.
    // Everything else is in the F12 screen; the extra bindings exist unbound for anyone who wants direct keys.
    private final KeyBinding kToggle = key("toggle", Keyboard.KEY_F10);
    private final KeyBinding kSettings = key("settings", Keyboard.KEY_F12);
    private final KeyBinding kSplit = key("split", Keyboard.KEY_NONE);
    private final KeyBinding kHeat = key("heatmap", Keyboard.KEY_NONE);
    private final KeyBinding kPaperUp = key("paperUp", Keyboard.KEY_NONE);
    private final KeyBinding kPaperDown = key("paperDown", Keyboard.KEY_NONE);
    private final KeyBinding kPeakUp = key("peakUp", Keyboard.KEY_NONE);
    private final KeyBinding kPeakDown = key("peakDown", Keyboard.KEY_NONE);
    private final KeyBinding kInfo = key("info", Keyboard.KEY_NONE);

    private String message = "";
    private long messageUntil;
    private long saveAt;

    private static KeyBinding key(String name, int code) {
        final KeyBinding k = new KeyBinding("key.gtnhhdr." + name, code, CAT);
        ClientRegistry.registerKeyBinding(k);
        return k;
    }

    public static void init() {
        INSTANCE = new HdrClient();
        FMLCommonHandler.instance()
            .bus()
            .register(INSTANCE);
    }

    void show(String text, double seconds) {
        message = text;
        messageUntil = System.nanoTime() + (long) (seconds * 1e9);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        HdrWindow.tick();
        if (saveAt != 0 && System.nanoTime() > saveAt) {
            saveAt = 0;
            HdrConfig.saveBrightness();
        }
        HdrTimer.enabled = HdrState.info
            || Minecraft.getMinecraft().currentScreen instanceof HdrSettingsScreen;
        boolean any = false;
        while (kToggle.isPressed()) {
            any = true;
            HdrState.sdrPreview = !HdrState.sdrPreview;
        }
        while (kSettings.isPressed()) {
            Minecraft.getMinecraft()
                .displayGuiScreen(new HdrSettingsScreen());
        }
        while (kSplit.isPressed()) {
            any = true;
            toggleSplit();
        }
        while (kHeat.isPressed()) {
            any = true;
            HdrState.heatMap = !HdrState.heatMap;
        }
        while (kInfo.isPressed()) {
            HdrState.info = !HdrState.info;
            HdrTimer.ENCODE.reset();
            HdrTimer.FINAL_PASS.reset();
        }
        if (any) show(modeText(), 3);
        final boolean shift = GuiScreen.isShiftKeyDown();
        while (kPaperUp.isPressed()) adjustPaper(shift ? 0 : 10);
        while (kPaperDown.isPressed()) adjustPaper(shift ? 0 : -10);
        while (kPeakUp.isPressed()) adjustPeak(shift ? 0 : 10);
        while (kPeakDown.isPressed()) adjustPeak(shift ? 0 : -10);
    }

    static void toggleSplit() {
        HdrState.split = !HdrState.split;
        if (HdrState.split) HdrState.sdrPreview = false;
    }

    /** delta 0 = back to the default. */
    void adjustPaper(int delta) {
        if (delta == 0) HdrConfig.paperWhiteNits = -1f;
        else {
            final float next = Math.max(80, Math.min(HdrState.peakNits(), HdrState.paperWhiteNits() + delta));
            HdrConfig.paperWhiteNits = Math.abs(next - HdrState.autoPaperWhiteNits()) < 0.5f ? -1f : next;
        }
        saveAt = System.nanoTime() + 2_000_000_000L;
        if (!HdrState.hdrOutput()) {
            show("World brightness only matters in HDR (Windows HDR is off)", 3);
            return;
        }
        show(String.format("World brightness: %d nits%s   (your old picture = %d)", Math.round(HdrState.paperWhiteNits()),
            HdrConfig.paperWhiteNits < 0 ? " (auto)" : "", Math.round(HdrState.uiNits())), 3);
    }

    /** delta 0 = back to what the monitor reports. */
    void adjustPeak(int delta) {
        final float auto = HdrState.monitorPeakNits;
        if (delta == 0) HdrConfig.peakNits = -1f;
        else {
            final float next = Math.max(HdrState.uiNits(), Math.min(10000f, HdrState.peakNits() + delta));
            HdrConfig.peakNits = Math.abs(next - auto) < 0.5f ? -1f : next;
        }
        saveAt = System.nanoTime() + 2_000_000_000L;
        if (!HdrState.hdrOutput()) {
            show("Peak only matters in HDR (Windows HDR is off)", 3);
            return;
        }
        HdrState.peakBoxUntil = System.nanoTime() + 4_000_000_000L;
        show(String.format("Peak: %d nits%s   - stop where the small square in the middle just disappears",
            Math.round(HdrState.peakNits()), HdrConfig.peakNits < 0 ? " (monitor default)" : ""), 4);
    }

    String modeText() {
        if (!HdrState.isActive()) return "GTNH-HDR is off (no 16-bit window - see logs/GTNH-HDR.log)";
        if (!HdrState.hdrOutput()) return "Windows HDR is off for this monitor - showing normal SDR";
        String s;
        if (HdrState.sdrPreview) s = "Old picture (HDR off)   - F10 to switch back";
        else if (HdrState.split) s = "Split: left = HDR,  right = old picture";
        else s = String.format("HDR on   (world %d nits, highlights up to %d nits)", Math.round(HdrState.paperWhiteNits()),
            Math.round(HdrState.peakNits()));
        if (HdrState.heatMap) s += "   |  heat map on";
        return s;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        final Minecraft mc = Minecraft.getMinecraft();
        final boolean msg = System.nanoTime() < messageUntil;
        final boolean labels = HdrState.hdrOutput() && HdrState.split && !HdrState.sdrPreview;
        final boolean screen = mc.currentScreen instanceof HdrSettingsScreen;
        if (!msg && !HdrState.info && !HdrState.heatMap && !labels) return;
        if (mc.fontRenderer == null) return;
        final ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        final FontRenderer f = mc.fontRenderer;
        final int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        mc.entityRenderer.setupOverlayRendering();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);

        if (labels) {
            box(f, "HDR", w / 4 - f.getStringWidth("HDR") / 2, 4);
            final String t = "Old picture";
            box(f, t, w * 3 / 4 - f.getStringWidth(t) / 2, 4);
        }
        if (msg) box(f, message, (w - f.getStringWidth(message)) / 2, labels ? 18 : 6);
        if (HdrState.heatMap) {
            final String t = "Heat map (brightest colour): blue <100 nits, green 100-200, yellow 200-300, orange 300-400, red 400+, "
                + "pink = at peak";
            box(f, t, (w - f.getStringWidth(t)) / 2, h - 40);
        }
        if (HdrState.info && !screen) {
            int y = 4;
            for (String line : infoLines()) {
                box(f, line, 4, y);
                y += 11;
            }
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    /** F12 button: HDR copies of the supported Complementary + Euphoria Patches packs in shaderpacks/. */
    void makeHdrPackCopies() {
        final File shaderpacks = new File(Minecraft.getMinecraft().mcDataDir, "shaderpacks");
        final List<String> results = HdrPackPatcher.makeHdrCopies(shaderpacks);
        for (String r : results) HdrWindow.say("Make HDR copy: " + r);
        String first = results.get(0);
        for (String r : results) {
            if (r.contains("created")) {
                first = r;
                break;
            }
        }
        show(results.size() > 1 ? first + "   (+" + (results.size() - 1) + " more, see logs/GTNH-HDR.log)" : first, 8);
    }

    /** Everything a bug report needs, as plain text (F12 > Copy diagnostics). */
    static String diagnostics() {
        final Minecraft mc = Minecraft.getMinecraft();
        final StringBuilder sb = new StringBuilder("GTNH-HDR diagnostics\n");
        sb.append("GTNH-HDR ").append(Tags.VERSION).append(", Angelica ").append(modVersion("angelica"))
            .append(", lwjgl3ify ").append(modVersion("lwjgl3ify")).append(", Java ")
            .append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name")).append('\n');
        sb.append("Graphics: ").append(HdrState.glVendor).append(" / ").append(HdrState.glRenderer).append(" / ")
            .append(HdrState.glVersion).append('\n');
        sb.append("Enabled in config: ").append(HdrConfig.enabled).append(", 16-bit float window: ").append(HdrState.isActive())
            .append('\n');
        sb.append(String.format("Windows HDR (this monitor): %s, SDR white %d nits, peak %d nits%s%n",
            HdrState.displayHdr ? "on" : "off", Math.round(HdrState.sdrWhiteNits), Math.round(HdrState.monitorPeakNits),
            HdrState.peakReported ? "" : " (not reported, assumed)"));
        sb.append(String.format("Settings: menus %d nits, world %d nits%s, peak %d nits%s, gamma 2.2 %s%n",
            Math.round(HdrState.uiNits()), Math.round(HdrState.paperWhiteNits()), HdrConfig.paperWhiteNits < 0 ? " (auto)" : "",
            Math.round(HdrState.peakNits()), HdrConfig.peakNits < 0 ? " (auto)" : "", HdrConfig.sdrGamma22));
        sb.append("Window: ").append(mc.displayWidth).append('x').append(mc.displayHeight)
            .append(mc.isFullScreen() ? ", full-screen (F11)" : ", windowed or borderless").append('\n');
        sb.append("Shader pack HDR patch in use: ").append(HdrState.packIsHdrPatched()).append('\n');
        sb.append("GPU cost: HDR copy ").append(ms(HdrTimer.ENCODE.averageMs())).append(", pack final pass ")
            .append(ms(HdrTimer.FINAL_PASS.averageMs())).append('\n');
        return sb.toString();
    }

    private static String modVersion(String modid) {
        final ModContainer m = Loader.instance()
            .getIndexedModList()
            .get(modid);
        return m == null ? "not found" : m.getVersion();
    }

    static void box(FontRenderer f, String s, int x, int y) {
        Gui.drawRect(x - 2, y - 2, x + f.getStringWidth(s) + 2, y + 10, 0xA0000000);
        f.drawStringWithShadow(s, x, y, 0xFFFFFF);
    }

    static List<String> infoLines() {
        final List<String> l = new ArrayList<>();
        l.add("GTNH-HDR - " + (HdrState.isActive() ? "16-bit float window (scRGB)" : "off, normal 8-bit window"));
        if (HdrState.isActive()) {
            l.add(String.format("Windows HDR %s  |  SDR white %d nits  |  monitor peak %d nits", HdrState.displayHdr ? "on" : "off",
                Math.round(HdrState.sdrWhiteNits), Math.round(HdrState.monitorPeakNits)));
            l.add("Shader pack HDR patch: " + (HdrState.packIsHdrPatched() ? "in use" : "not in use (pack shown as SDR)"));
            l.add("GPU cost: HDR copy " + ms(HdrTimer.ENCODE.averageMs()) + ",  pack final pass " + ms(HdrTimer.FINAL_PASS.averageMs()));
        }
        return l;
    }

    static String ms(double v) {
        return v < 0 ? "measuring..." : String.format("%.3f ms", v);
    }
}

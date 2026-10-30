package com.gtnhhdr.client;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

/**
 * F12: a small panel on the left; the world stays visible (no dark background) so every change can be judged live.
 */
public final class HdrSettingsScreen extends GuiScreen {

    private static final int HDR = 0, VIEW = 1, PAPER_DOWN = 2, PAPER_UP = 3, PEAK_DOWN = 4, PEAK_UP = 5, RESET = 6, DONE = 7, MAKE_COPY = 8, DIAG = 9;
    private static final int X = 8, W = 170;

    private GuiButton bHdr, bView, bPaper, bPeak;

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        int y = 26;
        buttonList.add(bHdr = new GuiButton(HDR, X, y, W, 20, ""));
        buttonList.add(bView = new GuiButton(VIEW, X, y += 24, W, 20, ""));
        buttonList.add(new GuiButton(PAPER_DOWN, X, y += 36, 20, 20, "-"));
        buttonList.add(bPaper = new GuiButton(-1, X + 22, y, W - 44, 20, ""));
        buttonList.add(new GuiButton(PAPER_UP, X + W - 20, y, 20, 20, "+"));
        buttonList.add(new GuiButton(PEAK_DOWN, X, y += 36, 20, 20, "-"));
        buttonList.add(bPeak = new GuiButton(-2, X + 22, y, W - 44, 20, ""));
        buttonList.add(new GuiButton(PEAK_UP, X + W - 20, y, 20, 20, "+"));
        buttonList.add(new GuiButton(RESET, X, y += 30, W, 20, "Reset brightness to defaults"));
        buttonList.add(new GuiButton(MAKE_COPY, X, y += 24, W, 20, "Make HDR copy of shader pack"));
        buttonList.add(new GuiButton(DIAG, X, y += 24, W, 20, "Copy diagnostics"));
        buttonList.add(new GuiButton(DONE, X, y += 24, W, 20, "Done"));
        bPaper.enabled = false;
        bPeak.enabled = false;
        refresh();
    }

    private void refresh() {
        bHdr.displayString = HdrState.sdrPreview ? "HDR: off (old picture)" : "HDR: on";
        bView.displayString = "View: " + (HdrState.heatMap ? "brightness heat map" : HdrState.split ? "split screen" : "normal");
        bPaper.displayString = Math.round(HdrState.paperWhiteNits()) + " nits";
        bPeak.displayString = Math.round(HdrState.peakNits()) + " nits";
        final boolean hdr = HdrState.hdrOutput();
        for (Object o : buttonList) {
            final GuiButton b = (GuiButton) o;
            if (b.id >= 0 && b.id != DONE && b.id != MAKE_COPY && b.id != DIAG) b.enabled = hdr;
        }
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        final HdrClient c = HdrClient.INSTANCE;
        switch (b.id) {
            case HDR:
                HdrState.sdrPreview = !HdrState.sdrPreview;
                break;
            case VIEW: // normal -> split -> heat map -> normal
                if (HdrState.heatMap) {
                    HdrState.heatMap = false;
                } else if (HdrState.split) {
                    HdrState.split = false;
                    HdrState.heatMap = true;
                } else {
                    HdrClient.toggleSplit();
                }
                break;
            case PAPER_DOWN: c.adjustPaper(-10); break;
            case PAPER_UP: c.adjustPaper(10); break;
            case PEAK_DOWN: c.adjustPeak(-10); break;
            case PEAK_UP: c.adjustPeak(10); break;
            case RESET:
                c.adjustPeak(0);
                c.adjustPaper(0);
                break;
            case MAKE_COPY:
                c.makeHdrPackCopies();
                break;
            case DIAG:
                setClipboardString(HdrClient.diagnostics());
                c.show("Diagnostics copied - paste them into your bug report", 4);
                break;
            case DONE:
                mc.displayGuiScreen(null);
                return;
            default:
                return;
        }
        refresh();
    }

    @Override
    protected void keyTyped(char ch, int key) {
        if (key == Keyboard.KEY_F12) {
            mc.displayGuiScreen(null);
            return;
        }
        if (key == Keyboard.KEY_F10) {
            HdrState.sdrPreview = !HdrState.sdrPreview;
            refresh();
            return;
        }
        super.keyTyped(ch, key);
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        final int bottom = 26 + 24 + 36 + 36 + 30 + 24 + 24 + 24 + 24;
        final List<String> info = HdrClient.infoLines();
        final List<String> lines = new java.util.ArrayList<>();
        for (String line : info.subList(1, info.size())) {
            for (Object o : fontRendererObj.listFormattedStringToWidth(line, W)) lines.add((String) o);
        }
        Gui.drawRect(X - 4, 4, X + W + 4, bottom + 6 + lines.size() * 10, 0xB0000000);
        drawString(fontRendererObj, "HDR settings  (F12 to close)", X, 10, 0xFFFFFF);
        drawString(fontRendererObj, "World brightness (paper white)", X, 26 + 24 + 24, 0xC0C0C0);
        drawString(fontRendererObj, "Brightest highlights (peak)", X, 26 + 24 + 36 + 24, 0xC0C0C0);
        super.drawScreen(mx, my, pt);
        int y = bottom + 2;
        for (String s : lines) {
            drawString(fontRendererObj, s, X, y, 0xA0A0A0);
            y += 10;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

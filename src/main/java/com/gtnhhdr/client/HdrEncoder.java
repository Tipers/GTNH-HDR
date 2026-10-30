package com.gtnhhdr.client;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;

import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * The single fullscreen pass from Minecraft's framebuffer to the window, with the HDR encode folded in:
 * sRGB decode, x UI nits / 80, written to the FP16 back buffer as scRGB. Replaces (does not add to) the copy that
 * already exists, so the pass count is unchanged. All GL calls go through Angelica's state manager (its redirector
 * rewrites GL11/GL20/... calls in this class), so its state cache stays correct.
 */
public final class HdrEncoder {

    private static final String VS = "#version 330 core\n" + "out vec2 vUv;\n"
        + "void main() {\n"
        + "    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);\n" // one oversized triangle, no vertex buffer
        + "    vUv = p;\n"
        + "    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);\n"
        + "}\n";

    private static final String FS = "#version 330 core\n" + "in vec2 vUv;\n"
        + "layout(location = 0) out vec4 fragColor;\n"
        + "uniform sampler2D uTex;\n"
        + "uniform float uUiScale;\n" // UI nits / 80: sRGB 1.0 -> this many scRGB units
        + "uniform float uPeakScale;\n" // peak nits / 80
        + "uniform float uSdrFrom;\n" // pixel x from which today's SDR picture is shown
        + "uniform float uDivider;\n" // split line x in pixels, < 0 = none
        + "uniform int uHeat;\n"
        + "uniform int uGamma22;\n"
        + "uniform vec4 uBox;\n" // peak test square (x0, y0, x1, y1) in pixels, x0 < 0 = off
        + "vec3 eotf(vec3 c) {\n"
        + "    if (uGamma22 != 0) return pow(c, vec3(2.2));\n"
        + "    return mix(pow((c + 0.055) / 1.055, vec3(2.4)), c / 12.92, lessThan(c, vec3(0.04045)));\n"
        + "}\n"
        + "vec3 heat(vec3 o) {\n"
        + "    float nits = max(o.r, max(o.g, o.b)) * 80.0;\n" // brightest channel = what the panel has to drive
        + "    vec3 band = vec3(0.0, 0.3, 1.0);\n"
        + "    if (nits >= 100.0) band = vec3(0.0, 1.0, 0.0);\n"
        + "    if (nits >= 200.0) band = vec3(1.0, 1.0, 0.0);\n"
        + "    if (nits >= 300.0) band = vec3(1.0, 0.45, 0.0);\n"
        + "    if (nits >= 400.0) band = vec3(1.0, 0.0, 0.0);\n"
        + "    if (nits >= uPeakScale * 79.0) band = vec3(1.0, 0.0, 1.0);\n"
        + "    float y = clamp(dot(o, vec3(0.2126, 0.7152, 0.0722)) / uPeakScale, 0.0, 1.0);\n"
        + "    return band * (0.3 + 0.7 * sqrt(y)) * min(uUiScale, 2.0);\n" // keeps the scene readable, <= 160 nits
        + "}\n"
        + "void main() {\n"
        + "    vec3 c = max(texture(uTex, vUv).rgb, vec3(0.0));\n"
        + "    vec2 p = gl_FragCoord.xy;\n"
        + "    bool sdr = p.x >= uSdrFrom;\n"
        + "    vec3 o = eotf(sdr ? min(c, vec3(1.0)) : c) * uUiScale;\n" // SDR side: exactly how Windows shows SDR
        + "    o = min(o, vec3(uPeakScale));\n"
        + "    if (uHeat != 0) o = heat(o);\n"
        + "    if (uDivider >= 0.0 && abs(p.x - uDivider) < 1.0) o = vec3(uUiScale);\n"
        + "    if (uBox.x >= 0.0 && p.x >= uBox.x && p.y >= uBox.y && p.x < uBox.z && p.y < uBox.w) {\n"
        + "        vec2 m = abs(p - (uBox.xy + uBox.zw) * 0.5);\n"
        + "        o = max(m.x, m.y) < (uBox.z - uBox.x) * 0.22 ? vec3(uPeakScale) : vec3(125.0);\n" // 10000-nit frame
        + "    }\n"
        + "    fragColor = vec4(o, 1.0);\n"
        + "}\n";

    private static int program = -1; // -1 not built, 0 failed
    private static int vao;
    private static int uUiScale, uPeakScale, uSdrFrom, uDivider, uHeat, uGamma22, uBox;

    private static int upgradedTexture = -1, upgradedW = -1, upgradedH = -1;

    private HdrEncoder() {}

    /**
     * Framebuffer.framebufferRender HEAD. Returns true if we drew (the original must then be skipped).
     * Every copy to the float window needs the encode, or SDR white would show at 80 nits: the main framebuffer gets the
     * full HDR treatment, any other one (e.g. the "Loading world" screen's own framebuffer) is shown as plain SDR.
     */
    public static boolean renderMainToWindow(Framebuffer fb, int width, int height) {
        if (!HdrState.active || !OpenGlHelper.isFramebufferEnabled()) return false;
        final boolean main = fb == Minecraft.getMinecraft()
            .getFramebuffer();
        if (!main && GLSMFramebuffer.getDrawFramebuffer() != 0) return false; // not a copy to the window
        if (main) ensureUpgraded(fb);
        if (program < 0) build();
        if (program == 0) return false;

        HdrTimer.ENCODE.begin();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glViewport(0, 0, width, height);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fb.framebufferTexture);
        GL20.glUseProgram(program);

        final float ui = HdrState.uiNits(), peak = HdrState.peakNits();
        GL20.glUniform1f(uUiScale, ui / 80f);
        GL20.glUniform1f(uPeakScale, Math.max(peak, ui) / 80f);
        final float splitX = main ? HdrState.splitX() : 0f;
        GL20.glUniform1f(uSdrFrom, splitX >= 1f ? 1e9f : splitX * width);
        GL20.glUniform1f(uDivider, main && splitX > 0f && splitX < 1f ? (float) Math.floor(splitX * width) : -1f);
        GL20.glUniform1i(uHeat, main && HdrState.heatMap ? 1 : 0);
        GL20.glUniform1i(uGamma22, HdrConfig.sdrGamma22 ? 1 : 0);
        if (main && HdrState.peakBoxVisible()) {
            final float s = Math.max(48f, height * 0.16f), cx = width * 0.5f, cy = height * 0.5f;
            GL20.glUniform4f(uBox, cx - s * 0.5f, cy - s * 0.5f, cx + s * 0.5f, cy + s * 0.5f);
        } else {
            GL20.glUniform4f(uBox, -1f, -1f, -1f, -1f);
        }

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL30.glBindVertexArray(0);

        GL20.glUseProgram(0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDepthMask(true);
        HdrTimer.ENCODE.end();
        return true;
    }

    /** Framebuffer.createFramebuffer RETURN. */
    public static void onFramebufferCreated(Framebuffer fb) {
        if (!HdrState.active || !OpenGlHelper.isFramebufferEnabled()) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || fb != mc.getFramebuffer()) return; // the very first one is caught lazily in renderMainToWindow
        upgradedTexture = -1;
        ensureUpgraded(fb);
    }

    /**
     * Re-specify the main framebuffer's colour texture as RGBA16F (same texture name, so every FBO that has it
     * attached - including Angelica's final-pass target - keeps working). Lets the shader pack's final pass carry
     * highlights above 1.0 while the GUI is still blended on top in plain sRGB 0..1.
     */
    private static void ensureUpgraded(Framebuffer fb) {
        if (fb.framebufferTexture == upgradedTexture && fb.framebufferTextureWidth == upgradedW
            && fb.framebufferTextureHeight == upgradedH) return;
        if (fb.framebufferTexture <= 0) return;
        final int format = GL30.GL_RGBA16F;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, fb.framebufferTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, fb.framebufferTextureWidth, fb.framebufferTextureHeight, 0,
            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        final int got = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        upgradedTexture = fb.framebufferTexture;
        upgradedW = fb.framebufferTextureWidth;
        upgradedH = fb.framebufferTextureHeight;
        fb.framebufferClear();
        HdrWindow.say(String.format("Main framebuffer %dx%d colour texture -> %s", upgradedW, upgradedH,
            got == GL30.GL_RGBA16F ? "RGBA16F" : String.format("0x%04X", got)));
    }

    private static void build() {
        try {
            final int vs = compile(GL20.GL_VERTEX_SHADER, VS);
            final int fs = compile(GL20.GL_FRAGMENT_SHADER, FS);
            final int p = GL20.glCreateProgram();
            GL20.glAttachShader(p, vs);
            GL20.glAttachShader(p, fs);
            GL20.glLinkProgram(p);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException("link: " + GL20.glGetProgramInfoLog(p, 4096));
            }
            GL20.glUseProgram(p);
            GL20.glUniform1i(GL20.glGetUniformLocation(p, "uTex"), 0);
            GL20.glUseProgram(0);
            uUiScale = GL20.glGetUniformLocation(p, "uUiScale");
            uPeakScale = GL20.glGetUniformLocation(p, "uPeakScale");
            uSdrFrom = GL20.glGetUniformLocation(p, "uSdrFrom");
            uDivider = GL20.glGetUniformLocation(p, "uDivider");
            uHeat = GL20.glGetUniformLocation(p, "uHeat");
            uGamma22 = GL20.glGetUniformLocation(p, "uGamma22");
            uBox = GL20.glGetUniformLocation(p, "uBox");
            vao = GL30.glGenVertexArrays();
            program = p;
            HdrWindow.say(String.format("HDR encode pass ready (scRGB, UI white %d nits, paper white %d nits, peak %d nits)",
                Math.round(HdrState.uiNits()), Math.round(HdrState.paperWhiteNits()), Math.round(HdrState.peakNits())));
        } catch (Throwable t) {
            program = 0;
            HdrWindow.LOG.error("HDR encode pass failed to build - the picture will look too dark. Set enabled=false in "
                + "config/GTNH-HDR.cfg.", t);
        }
    }

    private static int compile(int type, String src) {
        final int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == 0) {
            throw new IllegalStateException("compile: " + GL20.glGetShaderInfoLog(s, 4096));
        }
        return s;
    }
}

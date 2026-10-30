// GTNH-HDR patch - shared helpers. Only compiled when the GTNH-HDR mod defines HDR_ENABLED, so the pack is
// unchanged without the mod. Uniform names follow rrtt217's Minecraft HDR Mod (MIT), plus two A/B uniforms.
#ifndef HDR_OUTPUT_GLSL
#define HDR_OUTPUT_GLSL

uniform float HdrGamePeakBrightness;       // nits of the brightest highlight
uniform float HdrGamePaperWhiteBrightness; // nits of the pack's 1.0 (a white surface)
uniform float HdrGameMinimumBrightness;    // nits of black (0 on OLED)
uniform float HdrUIBrightness;             // nits of GUI white
uniform float HdrPreviewSDR;               // 1 = show today's SDR picture instead (instant A/B)
uniform float HdrSplitX;                   // screen x (0..1) from which today's SDR picture is shown, 1 = none

bool hdrOut = false;

// Peak relative to paper white (>= 1).
float HdrPeakRel() {
    return max(HdrGamePeakBrightness / max(HdrGamePaperWhiteBrightness, 1.0), 1.0);
}

bool HdrIsHdrPixel(float x) {
    return HdrPreviewSDR < 0.5 && x < HdrSplitX;
}

// The sRGB curve; above 1.0 its power segment simply continues.
vec3 HdrEncode(vec3 x) {
    x = max(x, vec3(0.0));
    return mix(1.055 * pow(x, vec3(1.0 / 2.4)) - 0.055, 12.92 * x, lessThan(x, vec3(0.0031308)));
}

float HdrEncode(float x) {
    x = max(x, 0.0);
    return x < 0.0031308 ? 12.92 * x : 1.055 * pow(x, 1.0 / 2.4) - 0.055;
}

vec3 HdrDecode(vec3 x) {
    x = max(x, vec3(0.0));
    return mix(pow((x + 0.055) / 1.055, vec3(2.4)), x / 12.92, lessThan(x, vec3(0.04045)));
}

// composite5 -> colortex3 (RGB10_A2). The colour is sRGB-encoded with paper white = 1.0 and highlights up to
// HdrEncode(peak); dividing by that stores exactly 0..1, so TAA, FXAA and final work unchanged. Dither +-0.5 step.
vec3 HdrStoreNormalised(vec3 color, float dither) {
    return clamp(color / HdrEncode(HdrPeakRel()) + (dither - 0.5) / 1023.0, 0.0, 1.0);
}

// final -> Minecraft's (16-bit) framebuffer: back to linear (paper white = 1, peak = P), then into the GUI's sRGB
// space where 1.0 = UI white. The mod's last pass turns that into nits for the display.
vec3 HdrFinalToUi(vec3 color) {
    float P = HdrPeakRel();
    vec3 linearColor = min(HdrDecode(color * HdrEncode(P)), vec3(P));
    return HdrEncode(linearColor * (HdrGamePaperWhiteBrightness / max(HdrUIBrightness, 1.0)));
}

#endif

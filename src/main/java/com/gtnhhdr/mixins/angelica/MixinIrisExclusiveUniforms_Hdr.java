package com.gtnhhdr.mixins.angelica;

import net.coderbot.iris.gl.uniform.FloatSupplier;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.gl.uniform.UniformUpdateFrequency;
import net.coderbot.iris.uniforms.FrameUpdateNotifier;
import net.coderbot.iris.uniforms.IrisExclusiveUniforms;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnhhdr.client.HdrState;

/**
 * Uniforms in nits, same names as rrtt217's HDR mod, plus two of ours for the instant A/B views.
 * Iris only uploads a uniform to programs that declare it, so packs without the HDR patch pay nothing.
 */
@Mixin(value = IrisExclusiveUniforms.class, remap = false)
public abstract class MixinIrisExclusiveUniforms_Hdr {

    @Inject(method = "addIrisExclusiveUniforms", at = @At("TAIL"))
    private static void gtnhhdr$addHdrUniforms(UniformHolder uniforms, FrameUpdateNotifier notifier, CallbackInfo ci) {
        final UniformUpdateFrequency f = UniformUpdateFrequency.PER_FRAME;
        uniforms.uniform1f(f, "HdrGamePeakBrightness", (FloatSupplier) HdrState::uniformPeak);
        uniforms.uniform1f(f, "HdrGamePaperWhiteBrightness", (FloatSupplier) HdrState::uniformPaperWhite);
        uniforms.uniform1f(f, "HdrGameMinimumBrightness", (FloatSupplier) () -> 0.0f);
        uniforms.uniform1f(f, "HdrUIBrightness", (FloatSupplier) HdrState::uniformUi);
        uniforms.uniform1f(f, "HdrPreviewSDR", (FloatSupplier) HdrState::uniformPreviewSdr);
        uniforms.uniform1f(f, "HdrSplitX", (FloatSupplier) HdrState::uniformSplitX);
    }
}

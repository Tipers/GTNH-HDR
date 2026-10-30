package com.gtnhhdr.mixins.angelica;

import net.coderbot.iris.postprocess.FinalPassRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnhhdr.client.HdrTimer;

/** GPU time of the shader pack's final pass (it now writes the 16-bit main framebuffer). Only while timing is on. */
@Mixin(value = FinalPassRenderer.class, remap = false)
public abstract class MixinFinalPassRenderer_HdrTiming {

    @Inject(method = "renderFinalPass", at = @At("HEAD"))
    private void gtnhhdr$finalStart(CallbackInfo ci) {
        HdrTimer.FINAL_PASS.begin();
    }

    @Inject(method = "renderFinalPass", at = @At("RETURN"))
    private void gtnhhdr$finalEnd(CallbackInfo ci) {
        HdrTimer.FINAL_PASS.end();
    }
}

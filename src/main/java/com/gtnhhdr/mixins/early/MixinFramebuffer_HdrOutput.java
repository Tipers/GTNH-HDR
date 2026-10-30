package com.gtnhhdr.mixins.early;

import net.minecraft.client.shader.Framebuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnhhdr.client.HdrEncoder;

/**
 * The one fullscreen copy from Minecraft's framebuffer to the window. Angelica {@code @Overwrite}s it (VBO quad);
 * priority 1100 makes this HEAD injection apply on top of that overwrite. For the main framebuffer we draw our own
 * encode pass instead (same single pass, HDR maths folded in) and cancel the original.
 */
@Mixin(value = Framebuffer.class, priority = 1100)
public abstract class MixinFramebuffer_HdrOutput {

    @Inject(method = "framebufferRender", at = @At("HEAD"), cancellable = true)
    private void gtnhhdr$encodeToWindow(int width, int height, CallbackInfo ci) {
        if (HdrEncoder.renderMainToWindow((Framebuffer) (Object) this, width, height)) {
            ci.cancel();
        }
    }

    /** Main framebuffer re-created (resize, F11): upgrade its colour texture to RGBA16F again. */
    @Inject(method = "createFramebuffer", at = @At("RETURN"))
    private void gtnhhdr$afterCreate(int width, int height, CallbackInfo ci) {
        HdrEncoder.onFramebufferCreated((Framebuffer) (Object) this);
    }
}

package com.gtnhhdr.mixins.early;

import net.minecraftforge.client.ForgeHooksClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnhhdr.client.HdrWindow;

/**
 * Window creation. Priority above 1000 so the injections land in Angelica's {@code @Overwrite} of createDisplay
 * (core-profile context probing) rather than being discarded by it.
 */
@Mixin(value = ForgeHooksClient.class, remap = false, priority = 1100)
public abstract class MixinForgeHooksClient_HdrWindow {

    /** Same timing as Angelica's SDLGPUGate: before Display.create, so the listener sees the SDL GL attributes. */
    @Inject(method = "createDisplay", at = @At("HEAD"))
    private static void gtnhhdr$beforeCreateDisplay(CallbackInfo ci) {
        try {
            HdrWindow.registerBeforeDisplayCreate();
        } catch (Throwable t) {
            // Never break window creation: worst case the game starts in plain SDR.
            System.err.println("[GTNH-HDR] before createDisplay failed: " + t);
            t.printStackTrace();
        }
    }

    /** The GL context is current here: check what pixel format we really got. */
    @Inject(method = "createDisplay", at = @At("RETURN"))
    private static void gtnhhdr$afterCreateDisplay(CallbackInfo ci) {
        try {
            HdrWindow.verifyAfterDisplayCreate();
        } catch (Throwable t) {
            System.err.println("[GTNH-HDR] after createDisplay failed: " + t);
            t.printStackTrace();
        }
    }
}

package com.gtnhhdr.mixins.angelica;

import net.coderbot.iris.gl.shader.StandardMacros;
import net.coderbot.iris.shaderpack.StringPair;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.ImmutableList;
import com.gtnhhdr.client.HdrState;

/**
 * Shader-pack macros, named like rrtt217's HDR mod so packs written for it work here:
 * HDR_MOD_INSTALLED always, HDR_ENABLED only while the float (HDR) window is really active.
 */
@Mixin(value = StandardMacros.class, remap = false)
public abstract class MixinStandardMacros_Hdr {

    @Inject(method = "createStandardEnvironmentDefines", at = @At("RETURN"), cancellable = true)
    private static void gtnhhdr$addHdrMacros(CallbackInfoReturnable<Iterable<StringPair>> cir) {
        final ImmutableList.Builder<StringPair> defines = ImmutableList.builder();
        defines.addAll(cir.getReturnValue());
        defines.add(new StringPair("HDR_MOD_INSTALLED", ""));
        defines.add(new StringPair("HDR_MOD_ANGELICA", ""));
        defines.add(new StringPair("GTNH_HDR_VERSION", "1"));
        if (HdrState.isActive()) {
            defines.add(new StringPair("HDR_ENABLED", ""));
        }
        cir.setReturnValue(defines.build());
    }
}

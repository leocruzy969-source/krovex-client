package com.krovex.client.mixin;

import com.krovex.client.features.Features;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Background FPS Limit: a low frame-rate cap while the window is not focused, normal cap again the moment it is. */
@Mixin(InactivityFpsLimiter.class)
abstract class KrovexBackgroundFpsMixin {
    // method_61937 = InactivityFpsLimiter.update(): the frame-rate cap for this frame
    @Inject(method = "method_61937", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void krovexBackgroundCap(CallbackInfoReturnable<Integer> cir) {
        if (Features.limitInBackground() && !MinecraftClient.getInstance().isWindowFocused()) {
            cir.setReturnValue(Math.min(cir.getReturnValueI(), Features.backgroundFps()));
        }
    }
}
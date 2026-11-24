package com.krovex.client.mixin;

import com.krovex.client.features.Features;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Motion Controls: camera bob, hurt shake and the sprint FOV stretch, each switchable on its own. */
@Mixin(GameRenderer.class)
abstract class KrovexMotionMixin {
    @Shadow(remap = false) private float field_4019;   // fovMultiplier
    @Shadow(remap = false) private float field_3999;   // lastFovMultiplier

    // method_3186 = GameRenderer.bobView(MatrixStack, float)
    @Inject(method = "method_3186", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void krovexNoBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (Features.noBob()) ci.cancel();
    }

    // method_3198 = GameRenderer.tiltViewWhenHurt(MatrixStack, float)
    @Inject(method = "method_3198", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void krovexNoHurtShake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (Features.noHurtShake()) ci.cancel();
    }

    // method_3199 = GameRenderer.updateFovMultiplier()
    @Inject(method = "method_3199", at = @At("RETURN"), remap = false, require = 0)
    private void krovexFlatFov(CallbackInfo ci) {
        if (Features.noSprintFov()) { field_4019 = 1f; field_3999 = 1f; }
    }
}
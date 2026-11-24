package com.krovex.client.mixin;

import com.krovex.client.features.Features;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Motion Controls: the wobbling portal / nausea overlay. */
@Mixin(InGameHud.class)
abstract class KrovexPortalMixin {
    // method_61980 = InGameHud.renderNauseaOverlay(DrawContext, float)
    @Inject(method = "method_61980", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void krovexNoPortalWobble(DrawContext context, float nauseaIntensity, CallbackInfo ci) {
        if (Features.noPortalWobble()) ci.cancel();
    }
}
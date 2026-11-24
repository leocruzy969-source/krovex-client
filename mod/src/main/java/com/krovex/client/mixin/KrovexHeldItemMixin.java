package com.krovex.client.mixin;

import com.krovex.client.hud.KrovexHudOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Equipped Item Indicator replaces vanilla's floating item name above the hotbar while that module is on. */
@Mixin(InGameHud.class)
abstract class KrovexHeldItemMixin {
    // method_1749 = InGameHud.renderHeldItemTooltip(DrawContext)
    @Inject(method = "method_1749", at = @At("HEAD"), cancellable = true, remap = false)
    private void hideVanillaItemName(DrawContext context, CallbackInfo ci) {
        if (KrovexHudOverlay.enabled("equipped-item-indicator")) ci.cancel();
    }
}
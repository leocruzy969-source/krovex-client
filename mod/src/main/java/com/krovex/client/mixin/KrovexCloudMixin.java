package com.krovex.client.mixin;

import com.krovex.client.sky.SkyRenderer;
import net.minecraft.client.render.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom skies paint their own clouds (or none), so vanilla's blocky ones are switched off while one is active. */
@Mixin(CloudRenderer.class)
abstract class KrovexCloudMixin {
    // method_62168 = CloudRenderer.renderClouds (intermediary, like the other Krovex mixins)
    @Inject(method = "method_62168", at = @At("HEAD"), cancellable = true, remap = false)
    private void hideVanillaClouds(CallbackInfo ci) {
        if (SkyRenderer.activeCustom() != null) ci.cancel();
    }
}

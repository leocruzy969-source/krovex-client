package com.krovex.client.mixin;

import com.krovex.client.sky.SkyRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom skies replace vanilla's sky completely: the gradient dome covers its colour, and here the
 * sun, moon, vanilla stars and the sunrise/sunset glow are switched off. (Intermediary names with
 * remap=false, like the other Krovex mixins.)
 */
@Mixin(SkyRendering.class)
abstract class KrovexSkyMixin {
    @Unique private final SkyRenderer krovexSky = new SkyRenderer();

    // method_74926 = updateRenderState
    @Inject(method = "method_74926", at = @At("RETURN"), remap = false)
    private void shadeSky(ClientWorld world, float tickDelta, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (SkyRenderer.activeCustom() == null) return;
        state.sunriseAndSunsetColor = 0;
        state.starBrightness = 0;
    }

    // method_62302 = renderTopSky: the custom sky is drawn right after vanilla's top sky disc
    @Inject(method = "method_62302", at = @At("RETURN"), remap = false)
    private void drawSkyDetails(int color, CallbackInfo ci) {
        krovexSky.draw();
    }

    // method_62307 = renderCelestialBodies (sun, moon, vanilla stars)
    @Inject(method = "method_62307", at = @At("HEAD"), cancellable = true, remap = false)
    private void hideCelestialBodies(CallbackInfo ci) {
        if (SkyRenderer.activeCustom() != null) ci.cancel();
    }

    // method_62306 = renderGlowingSky (sunrise / sunset glow)
    @Inject(method = "method_62306", at = @At("HEAD"), cancellable = true, remap = false)
    private void hideSunriseGlow(CallbackInfo ci) {
        if (SkyRenderer.activeCustom() != null) ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"), remap = false)
    private void closeSky(CallbackInfo ci) {
        krovexSky.close();
    }
}

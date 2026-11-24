package com.krovex.client.mixin;

import com.krovex.client.sky.SkyRenderer;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distant terrain fades into the fog colour, which in vanilla is the (often orange) horizon colour.
 * With a custom sky active it becomes that sky's horizon colour, so the world blends into the sky
 * instead of ending in a mismatched band. Underwater / in lava / powder snow stay vanilla.
 */
@Mixin(FogRenderer.class)
abstract class KrovexFogMixin {
    // method_62185 = FogRenderer.getFogColor(Camera, float, ClientWorld, int, float)
    @Inject(method = "method_62185", at = @At("RETURN"), cancellable = true, remap = false)
    private void skyFogColor(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness,
                             CallbackInfoReturnable<Vector4f> cir) {
        var preset = SkyRenderer.activeCustom();
        if (preset == null || camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        int c = SkyRenderer.horizonColor(preset);
        cir.setReturnValue(new Vector4f((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 1f));
    }
}

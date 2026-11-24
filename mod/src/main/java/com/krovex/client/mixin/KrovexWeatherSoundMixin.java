package com.krovex.client.mixin;

import com.krovex.client.features.Features;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Audio: rain and thunder get their own volume; every other sound is left exactly as it was. */
@Mixin(SoundSystem.class)
abstract class KrovexWeatherSoundMixin {
    // method_4853 = SoundSystem.getAdjustedVolume(SoundInstance)
    @Inject(method = "method_4853", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void krovexWeatherVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        String path = sound.getId().getPath();
        if (path.startsWith("weather.rain")) cir.setReturnValue(cir.getReturnValueF() * Features.rainVolume());
        else if (path.startsWith("entity.lightning_bolt")) cir.setReturnValue(cir.getReturnValueF() * Features.thunderVolume());
    }
}
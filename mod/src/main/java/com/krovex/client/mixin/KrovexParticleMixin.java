package com.krovex.client.mixin;
import com.krovex.client.hud.KrovexHudOverlay;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
abstract class KrovexParticleMixin {
    @Unique private long krovexParticleWindow;
    @Unique private int krovexParticleCount;

    @Inject(method="method_3058", at=@At("HEAD"), cancellable=true, remap=false)
    private void limitKrovexParticleBurst(Particle particle, CallbackInfo ci) {
        if (!KrovexHudOverlay.enabled("particle-controls")) return;
        long now = System.nanoTime();
        if (now - krovexParticleWindow >= 50_000_000L || krovexParticleWindow == 0) {
            krovexParticleWindow = now;
            krovexParticleCount = 0;
        }
        if (++krovexParticleCount > Math.clamp(KrovexHudOverlay.setting("particle-controls", 128), 32, 256)) ci.cancel();
    }
}

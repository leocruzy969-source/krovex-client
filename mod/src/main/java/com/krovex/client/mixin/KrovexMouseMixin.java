package com.krovex.client.mixin;
import com.krovex.client.hud.KrovexHudOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
abstract class KrovexMouseMixin {
    @Inject(method="method_1601", at=@At("HEAD"), remap=false)
    private void recordKrovexClick(long window, MouseInput input, int action, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (action == 1 && client.player != null && client.currentScreen == null
                && KrovexHudOverlay.enabled("cps-counter")) KrovexHudOverlay.recordClick(input.button());
    }
}

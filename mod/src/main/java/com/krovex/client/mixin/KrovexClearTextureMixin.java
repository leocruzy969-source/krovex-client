package com.krovex.client.mixin;

import com.krovex.client.hud.KrovexHudOverlay;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clear Inventory: with the module on, the grey panel textures of vanilla inventories, chests, furnaces, the creative
 * inventory and so on are not drawn (only those background textures; progress arrows, items and mod GUIs are untouched).
 */
@Mixin(DrawContext.class)
abstract class KrovexClearTextureMixin {
    // method_25290 = DrawContext.drawTexture(RenderPipeline, Identifier, int x, int y, float u, float v, int w, int h, int texW, int texH)
    @Inject(method = "method_25290", at = @At("HEAD"), cancellable = true, remap = false)
    private void skipContainerPanel(RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
                                    int width, int height, int textureWidth, int textureHeight, CallbackInfo ci) {
        if (!"minecraft".equals(texture.getNamespace()) || !texture.getPath().startsWith("textures/gui/container/")) return;
        if (!(MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?>)) return;
        if (KrovexHudOverlay.enabled("clear-inventory")) ci.cancel();
    }
}
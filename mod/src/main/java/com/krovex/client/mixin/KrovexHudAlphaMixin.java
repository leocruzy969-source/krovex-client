package com.krovex.client.mixin;

import com.krovex.client.features.HudAlpha;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Dynamic HUD: while {@link HudAlpha} holds a value below 1 (only while one HUD widget is being drawn), every colour
 * the widget fills, draws as text or textures with is faded by it. These are the three terminal draw methods the
 * convenience overloads all end in, so nothing is faded twice.
 */
@Mixin(DrawContext.class)
abstract class KrovexHudAlphaMixin {
    // method_70848 = fill(RenderPipeline, TextureSetup, x1, y1, x2, y2, color, Integer color2): rectangles and gradients
    @ModifyVariable(method = "method_70848", at = @At("HEAD"), argsOnly = true, ordinal = 4, remap = false, require = 0)
    private int krovexFadeFill(int color) { return HudAlpha.apply(color); }

    @ModifyVariable(method = "method_70848", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false, require = 0)
    private Integer krovexFadeFillEnd(Integer color) { return color == null ? null : HudAlpha.apply(color); }

    // method_51430 = drawText(TextRenderer, OrderedText, x, y, color, shadow)
    @ModifyVariable(method = "method_51430", at = @At("HEAD"), argsOnly = true, ordinal = 2, remap = false, require = 0)
    private int krovexFadeText(int color) { return HudAlpha.apply(color); }

    // method_25293 = drawTexture(RenderPipeline, Identifier, x, y, u, v, w, h, regionW, regionH, texW, texH, color)
    @ModifyVariable(method = "method_25293", at = @At("HEAD"), argsOnly = true, ordinal = 8, remap = false, require = 0)
    private int krovexFadeTexture(int color) { return HudAlpha.apply(color); }
}
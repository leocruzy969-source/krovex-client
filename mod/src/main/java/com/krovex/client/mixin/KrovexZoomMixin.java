package com.krovex.client.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

// Retained for compatibility with builds that still list this mixin in their config.
@Mixin(GameRenderer.class)
abstract class KrovexZoomMixin {}

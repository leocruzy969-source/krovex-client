package com.krovex.client.mixin;

import com.krovex.client.gui.MenuStyle;
import com.krovex.client.hud.KrovexHudOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear Inventory: with the panel gone, every slot gets a soft dark plate so you can still see where items go. */
@Mixin(HandledScreen.class)
abstract class KrovexClearInventoryMixin<T extends ScreenHandler> {
    @Shadow(remap = false) protected int field_2776;            // x
    @Shadow(remap = false) protected int field_2800;            // y
    @Shadow(remap = false) @Final protected T field_2797;       // handler

    // method_25420 = Screen.renderBackground(DrawContext, int, int, float); the panel was drawn just before this point
    @Inject(method = "method_25420", at = @At("TAIL"), remap = false)
    private void krovexSlotPlates(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!KrovexHudOverlay.enabled("clear-inventory")) return;
        for (Slot slot : field_2797.slots) {
            if (!slot.isEnabled()) continue;
            int sx = field_2776 + slot.x, sy = field_2800 + slot.y;
            MenuStyle.gradientCardRaw(context, sx - 1, sy - 1, 18, 18, 3, 0x30FFFFFF, 0x66000000, 0x52000000);
        }
    }
}
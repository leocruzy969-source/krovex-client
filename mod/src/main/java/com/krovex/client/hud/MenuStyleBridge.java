package com.krovex.client.hud;

/** Small colour helpers for the HUD. */
final class MenuStyleBridge {
    private MenuStyleBridge() {}

    /** Blends a colour toward white by t (0..1), keeping its alpha. */
    static int lighten(int argb, float t) {
        int r = Math.round((argb >> 16 & 255) + (255 - (argb >> 16 & 255)) * t);
        int g = Math.round((argb >> 8 & 255) + (255 - (argb >> 8 & 255)) * t);
        int b = Math.round((argb & 255) + (255 - (argb & 255)) * t);
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }
}
package com.krovex.client.features;

/**
 * A global alpha multiplier for everything the HUD draws while it is set (used by Dynamic HUD). The DrawContext
 * mixin applies it to fills, gradients, text and textures; the HUD sets it around one widget at a time.
 */
public final class HudAlpha {
    private static float value = 1f;

    private HudAlpha() {}

    public static float get() { return value; }
    public static void set(float v) { value = Math.clamp(v, 0f, 1f); }
    public static void reset() { value = 1f; }

    /** {@code argb} with its alpha scaled by the current multiplier. */
    public static int apply(int argb) {
        if (value >= .999f) return argb;
        int a = Math.round((argb >>> 24) * value);
        return a << 24 | argb & 0xFFFFFF;
    }
}
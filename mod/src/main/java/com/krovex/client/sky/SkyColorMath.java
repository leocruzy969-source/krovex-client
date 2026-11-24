package com.krovex.client.sky;

/**
 * Pure color/phase math for {@link SkyRenderer}, kept dependency-free (no Minecraft
 * types) specifically so it can be unit-tested outside the game, unlike the mixin
 * and renderer that call it.
 */
public final class SkyColorMath {
    private SkyColorMath() {}

    /** 0..1 oscillation from a preset's animation phase, independent of any particular easing curve choice. */
    public static float oscillate(float phase) {
        return (float) (Math.sin(phase * Math.PI * 2) + 1f) / 2f;
    }

    public static int lerpArgb(int a, int b, float t) {
        float ct = Math.max(0f, Math.min(1f, t));
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = Math.round(aa + (ba - aa) * ct);
        int rr = Math.round(ar + (br - ar) * ct);
        int rg = Math.round(ag + (bg - ag) * ct);
        int rb = Math.round(ab + (bb - ab) * ct);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    public static int colorAt(SkyPreset preset, long worldTimeMillis) {
        float phase = preset.animationPhase(worldTimeMillis);
        float t = oscillate(phase) * preset.intensity();
        return lerpArgb(preset.primaryColorArgb(), preset.secondaryColorArgb(), t);
    }
}

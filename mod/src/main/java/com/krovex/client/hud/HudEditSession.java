package com.krovex.client.hud;

/** Tiny marker for "a HUD edit session is in progress on this profile" -- no Minecraft dependency. */
public final class HudEditSession {
    private static HudProfile activeEditTarget;

    private HudEditSession() {}

    public static void begin(HudProfile profile) { activeEditTarget = profile; }
    public static HudProfile activeTarget() { return activeEditTarget; }
    public static boolean isActive() { return activeEditTarget != null; }
    public static void end() { activeEditTarget = null; }
}

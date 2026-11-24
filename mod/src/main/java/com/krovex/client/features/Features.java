package com.krovex.client.features;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.hud.KrovexHudOverlay;

/** Cheap static reads of the module settings, used from mixins and the render loop (no allocation, no file access). */
public final class Features {
    private Features() {}

    private static com.krovex.client.modules.ModuleState state(String id) {
        var store = KrovexClientMod.moduleStateStore();
        return store == null ? null : store.stateFor(id);
    }

    public static boolean on(String id) {
        var s = state(id);
        return s != null && s.enabled();
    }

    public static double value(String id, String key, double fallback) {
        var s = state(id);
        return s == null ? fallback : s.value(key, fallback);
    }

    public static boolean flag(String id, String key, boolean fallback) {
        var s = state(id);
        return s == null ? fallback : s.flag(key, fallback);
    }

    // ---- Motion Controls: each toggle ON means "that motion is switched OFF"
    public static boolean noBob() { return on("motion-controls") && flag("motion-controls", "bob", true); }
    public static boolean noHurtShake() { return on("motion-controls") && flag("motion-controls", "shake", true); }
    public static boolean noSprintFov() { return on("motion-controls") && flag("motion-controls", "fov", false); }
    public static boolean noPortalWobble() { return on("motion-controls") && flag("motion-controls", "portal", false); }

    // ---- Weather Audio: volumes 0..1
    public static float rainVolume() { return on("weather-audio") ? (float) value("weather-audio", "rain", 1.0) : 1f; }
    public static float thunderVolume() { return on("weather-audio") ? (float) value("weather-audio", "thunder", 1.0) : 1f; }

    // ---- Background FPS Limit
    public static boolean limitInBackground() { return on("background-fps"); }
    public static int backgroundFps() { return (int) Math.clamp(Math.round(value("background-fps", "fps", 15)), 1, 120); }
}
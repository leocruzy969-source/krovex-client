package com.krovex.client.features;

import java.util.HashMap;
import java.util.Map;

/**
 * Dynamic HUD: tracks, per HUD widget, when its content last changed. A widget that stays unchanged for the idle
 * timeout fades out over the fade time; any change brings it back quickly. The result is a 0..1 alpha that the
 * overlay hands to {@link HudAlpha} while that widget is drawn.
 */
public final class DynamicHud {
    private static final class Slot { int signature; long changedAt, lastUpdate; float alpha = 1f; boolean fresh = true; }
    private static final Map<String, Slot> SLOTS = new HashMap<>();

    private DynamicHud() {}

    /** The alpha to draw widget {@code id} with, given a number that changes whenever its content does. */
    public static float alphaFor(String id, int signature, long nowNanos) {
        Slot slot = SLOTS.computeIfAbsent(id, k -> new Slot());
        if (slot.fresh) { slot.fresh = false; slot.signature = signature; slot.changedAt = nowNanos; slot.lastUpdate = nowNanos; return 1f; }
        float dt = Math.min(.25f, (nowNanos - slot.lastUpdate) / 1e9f);
        slot.lastUpdate = nowNanos;
        if (signature != slot.signature) { slot.signature = signature; slot.changedAt = nowNanos; }
        float idleFor = (nowNanos - slot.changedAt) / 1e9f;
        float timeout = (float) Features.value("dynamic-hud", "timeout", 5), fade = (float) Features.value("dynamic-hud", "fade", .6);
        float floor = (float) Features.value("dynamic-hud", "floor", 0);
        float target = idleFor > timeout ? floor : 1f;
        if (slot.alpha > target) slot.alpha = Math.max(target, slot.alpha - dt / Math.max(.05f, fade));
        else slot.alpha = Math.min(target, slot.alpha + dt / .15f);
        return slot.alpha;
    }

    public static boolean active() { return Features.on("dynamic-hud"); }
}
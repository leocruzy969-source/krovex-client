package com.krovex.client.hud;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where and how one on-screen module/widget sits (section 3: "Let me edit HUD
 * elements over the actual game view. Support dragging, scaling, alignment, edge
 * snapping, opacity, and resetting individual elements. Preserve anchors when the
 * resolution or GUI scale changes."). Position is stored as a FRACTION of the usable
 * area relative to its anchor corner, not raw pixels -- that's what makes it resize-
 * and GUI-scale-independent: a widget anchored TOP_LEFT at (0.02, 0.02) stays 2% in
 * from the top-left corner whether the window is 1280x720 or 3840x2160.
 */
public final class HudElementLayout {

    public enum Anchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER, TOP_CENTER }

    private final String elementId; // e.g. "music_widget", "fps_counter", "crosshair"
    private Anchor anchor;
    private float offsetXFraction; // fraction of screen width from the anchor corner
    private float offsetYFraction; // fraction of screen height from the anchor corner
    private float scale;
    private float opacity;
    private boolean visible;

    public HudElementLayout(String elementId) {
        this.elementId = elementId;
        reset();
    }

    public String elementId() { return elementId; }
    public Anchor anchor() { return anchor; }
    public void setAnchor(Anchor v) { this.anchor = v; }
    public float offsetXFraction() { return offsetXFraction; }
    public float offsetYFraction() { return offsetYFraction; }
    public float scale() { return scale; }
    public void setScale(float v) { this.scale = Math.max(0.25f, Math.min(3.0f, v)); }
    public float opacity() { return opacity; }
    public void setOpacity(float v) { this.opacity = Math.max(0f, Math.min(1f, v)); }
    public boolean visible() { return visible; }
    public void setVisible(boolean v) { this.visible = v; }

    /** Resolves this layout to actual pixel coordinates for a given screen size and element size. */
    public int[] resolvePixelPosition(int screenWidth, int screenHeight, int elementWidth, int elementHeight) {
        int x = (int) (offsetXFraction * screenWidth);
        int y = (int) (offsetYFraction * screenHeight);
        return switch (anchor) {
            case TOP_LEFT -> new int[]{x, y};
            case TOP_RIGHT -> new int[]{screenWidth - x - elementWidth, y};
            case BOTTOM_LEFT -> new int[]{x, screenHeight - y - elementHeight};
            case BOTTOM_RIGHT -> new int[]{screenWidth - x - elementWidth, screenHeight - y - elementHeight};
            case TOP_CENTER -> new int[]{(screenWidth - elementWidth) / 2 + x, y};
            case CENTER -> new int[]{(screenWidth - elementWidth) / 2 + x, (screenHeight - elementHeight) / 2 + y};
        };
    }

    /**
     * Converts a raw drag to a new anchor + offset, snapping to whichever screen edge
     * the element ends up nearest (the "edge snapping" requirement), while keeping it
     * expressed as fractions so it survives a resolution/GUI-scale change afterward.
     */
    public void dragTo(int newPixelX, int newPixelY, int screenWidth, int screenHeight,
                         int elementWidth, int elementHeight, int snapThresholdPixels) {
        int centerX = newPixelX + elementWidth / 2;
        int centerY = newPixelY + elementHeight / 2;
        boolean nearLeft = newPixelX <= snapThresholdPixels;
        boolean nearRight = (screenWidth - (newPixelX + elementWidth)) <= snapThresholdPixels;
        boolean nearTop = newPixelY <= snapThresholdPixels;
        boolean nearBottom = (screenHeight - (newPixelY + elementHeight)) <= snapThresholdPixels;

        boolean left = nearLeft || (!nearRight && centerX < screenWidth / 2);
        boolean top = nearTop || (!nearBottom && centerY < screenHeight / 2);

        if (left && top) { anchor = Anchor.TOP_LEFT; offsetXFraction = clampFrac(newPixelX, screenWidth); offsetYFraction = clampFrac(newPixelY, screenHeight); }
        else if (!left && top) { anchor = Anchor.TOP_RIGHT; offsetXFraction = clampFrac(screenWidth - newPixelX - elementWidth, screenWidth); offsetYFraction = clampFrac(newPixelY, screenHeight); }
        else if (left) { anchor = Anchor.BOTTOM_LEFT; offsetXFraction = clampFrac(newPixelX, screenWidth); offsetYFraction = clampFrac(screenHeight - newPixelY - elementHeight, screenHeight); }
        else { anchor = Anchor.BOTTOM_RIGHT; offsetXFraction = clampFrac(screenWidth - newPixelX - elementWidth, screenWidth); offsetYFraction = clampFrac(screenHeight - newPixelY - elementHeight, screenHeight); }
    }

    private float clampFrac(int pixels, int total) {
        if (total <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) pixels / total));
    }

    public void reset() {
        this.anchor = Anchor.TOP_LEFT;
        this.offsetXFraction = 0.02f;
        this.offsetYFraction = 0.02f;
        switch (elementId) {
            case "fps-counter" -> { offsetXFraction = .003f; offsetYFraction = .006f; }
            case "ping" -> { offsetXFraction = .003f; offsetYFraction = .028f; }
            case "coordinates" -> { offsetXFraction = .003f; offsetYFraction = .05f; }
            case "compass" -> { anchor = Anchor.TOP_RIGHT; offsetYFraction = 0.02f; }
            case "clock" -> { anchor = Anchor.TOP_RIGHT; offsetYFraction = 0.09f; }
            case "session-timer" -> { anchor = Anchor.TOP_RIGHT; offsetYFraction = 0.16f; }
            case "player-ping" -> { anchor = Anchor.TOP_CENTER; offsetXFraction = 0; offsetYFraction = 0.09f; }
            case "potion-timers" -> { anchor = Anchor.TOP_RIGHT; offsetYFraction = 0.02f; }
            case "music-widget" -> { anchor = Anchor.TOP_CENTER; offsetXFraction = 0; }
            case "keystrokes" -> { anchor = Anchor.BOTTOM_RIGHT; offsetYFraction = 0.02f; }
            case "cps-counter" -> { anchor = Anchor.BOTTOM_RIGHT; offsetYFraction = 0.02f; }
            case "equipped-item-indicator" -> { anchor = Anchor.TOP_RIGHT; offsetYFraction = 0.05f; }
            case "hud-lyrics" -> { anchor = Anchor.BOTTOM_RIGHT; offsetYFraction = 0.20f; }
            case "keybind-conflicts" -> { anchor = Anchor.TOP_CENTER; offsetXFraction = 0; offsetYFraction = 0.17f; }
            default -> { }
        }
        this.scale = 1.0f;
        this.opacity = 1.0f;
        this.visible = true;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("anchor", anchor.name());
        m.put("offsetXFraction", (double) offsetXFraction);
        m.put("offsetYFraction", (double) offsetYFraction);
        m.put("scale", (double) scale);
        m.put("opacity", (double) opacity);
        m.put("visible", visible);
        return m;
    }

    public static HudElementLayout fromMap(String elementId, Map<String, Object> m) {
        HudElementLayout layout = new HudElementLayout(elementId);
        if (m == null) return layout;
        try { layout.anchor = Anchor.valueOf((String) m.getOrDefault("anchor", "TOP_LEFT")); } catch (IllegalArgumentException ignored) {}
        layout.offsetXFraction = (float) doubleOr(m, "offsetXFraction", 0.02);
        layout.offsetYFraction = (float) doubleOr(m, "offsetYFraction", 0.02);
        layout.scale = (float) doubleOr(m, "scale", 1.0);
        layout.opacity = (float) doubleOr(m, "opacity", 1.0);
        layout.visible = m.get("visible") instanceof Boolean b ? b : true;
        boolean oldDefault = layout.offsetXFraction == .02f && switch (elementId) {
            case "fps-counter" -> layout.anchor == Anchor.TOP_LEFT && layout.offsetYFraction == .02f;
            case "music-widget" -> layout.anchor == Anchor.TOP_RIGHT && layout.offsetYFraction == .32f;
            case "coordinates" -> layout.anchor == Anchor.TOP_LEFT && (layout.offsetYFraction == .23f || layout.offsetYFraction == .05f);
            case "potion-timers" -> layout.anchor == Anchor.TOP_RIGHT && layout.offsetYFraction == .23f;
            case "keystrokes" -> layout.anchor == Anchor.BOTTOM_LEFT && layout.offsetYFraction == .05f;
            case "cps-counter" -> layout.anchor == Anchor.BOTTOM_LEFT && layout.offsetYFraction == .12f;
            case "equipped-item-indicator" -> (layout.anchor == Anchor.BOTTOM_RIGHT && layout.offsetYFraction == .12f)
                    || (layout.anchor == Anchor.TOP_RIGHT && layout.offsetYFraction == .08f);
            default -> false;
        };
        if (oldDefault) {
            var defaults = new HudElementLayout(elementId);
            layout.anchor = defaults.anchor;
            layout.offsetXFraction = defaults.offsetXFraction;
            layout.offsetYFraction = defaults.offsetYFraction;
        }
        return layout;
    }

    private static double doubleOr(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }
}


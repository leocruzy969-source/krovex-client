package com.krovex.theme;

import java.util.Objects;
import java.util.UUID;

/**
 * The full set of visual knobs exposed in the "Theme" screen, identical in the
 * launcher (Swing) and the mod (Minecraft GUI). Instances are immutable; every
 * "change" in the editor produces a new {@link KrovexTheme} via {@link #toBuilder()}.
 *
 * Colors are stored as 0xAARRGGBB ints (alpha-first) so opacity can be baked into
 * a color or controlled separately via {@link #opacity()} depending on the surface.
 */
public final class KrovexTheme {

    public static final int DEFAULT_ACCENT = 0xFF8A4FFF;       // violet
    public static final int DEFAULT_BACKGROUND = 0xFF121014;   // deep charcoal
    public static final int DEFAULT_PANEL = 0xFF1C1920;        // slightly lighter charcoal
    public static final int DEFAULT_TEXT = 0xFFF5F3F7;         // crisp white
    public static final String DEFAULT_FONT = "Inter";
    public static final int DEFAULT_FONT_SIZE = 14;
    public static final int DEFAULT_CORNER_RADIUS = 8;
    public static final int DEFAULT_SPACING = 8;
    public static final float DEFAULT_OPACITY = 0.92f;

    private final String id;
    private final String name;
    private final int accentColor;
    private final int backgroundColor;
    private final int panelColor;
    private final int textColor;
    private final String fontFamily;
    private final int fontSize;
    private final int cornerRadius;
    private final int spacing;
    private final float opacity;
    private final boolean blurEnabled;
    private final boolean glowEnabled;
    private final boolean animationsEnabled;
    private final boolean builtin;

    private KrovexTheme(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.name = Objects.requireNonNull(b.name, "name");
        this.accentColor = b.accentColor;
        this.backgroundColor = b.backgroundColor;
        this.panelColor = b.panelColor;
        this.textColor = b.textColor;
        this.fontFamily = Objects.requireNonNull(b.fontFamily, "fontFamily");
        this.fontSize = clamp(b.fontSize, 8, 32);
        this.cornerRadius = clamp(b.cornerRadius, 0, 24);
        this.spacing = clamp(b.spacing, 0, 24);
        this.opacity = clamp(b.opacity, 0.1f, 1.0f);
        this.blurEnabled = b.blurEnabled;
        this.glowEnabled = b.glowEnabled;
        this.animationsEnabled = b.animationsEnabled;
        this.builtin = b.builtin;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static KrovexTheme createDefault(String name) {
        return builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .accentColor(DEFAULT_ACCENT)
                .backgroundColor(DEFAULT_BACKGROUND)
                .panelColor(DEFAULT_PANEL)
                .textColor(DEFAULT_TEXT)
                .fontFamily(DEFAULT_FONT)
                .fontSize(DEFAULT_FONT_SIZE)
                .cornerRadius(DEFAULT_CORNER_RADIUS)
                .spacing(DEFAULT_SPACING)
                .opacity(DEFAULT_OPACITY)
                .blurEnabled(true)
                .glowEnabled(true)
                .animationsEnabled(true)
                .build();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.id = this.id;
        b.name = this.name;
        b.accentColor = this.accentColor;
        b.backgroundColor = this.backgroundColor;
        b.panelColor = this.panelColor;
        b.textColor = this.textColor;
        b.fontFamily = this.fontFamily;
        b.fontSize = this.fontSize;
        b.cornerRadius = this.cornerRadius;
        b.spacing = this.spacing;
        b.opacity = this.opacity;
        b.blurEnabled = this.blurEnabled;
        b.glowEnabled = this.glowEnabled;
        b.animationsEnabled = this.animationsEnabled;
        b.builtin = this.builtin;
        return b;
    }

    /** Returns a copy with a new id/name/builtin=false — used by "Duplicate". */
    public KrovexTheme duplicateAs(String newName) {
        return toBuilder().id(UUID.randomUUID().toString()).name(newName).builtin(false).build();
    }

    public String id() { return id; }
    public String name() { return name; }
    public int accentColor() { return accentColor; }
    public int backgroundColor() { return backgroundColor; }
    public int panelColor() { return panelColor; }
    public int textColor() { return textColor; }
    public String fontFamily() { return fontFamily; }
    public int fontSize() { return fontSize; }
    public int cornerRadius() { return cornerRadius; }
    public int spacing() { return spacing; }
    public float opacity() { return opacity; }
    public boolean blurEnabled() { return blurEnabled; }
    public boolean glowEnabled() { return glowEnabled; }
    public boolean animationsEnabled() { return animationsEnabled; }
    /** Built-in presets can be duplicated but not renamed/overwritten/deleted in place. */
    public boolean builtin() { return builtin; }

    /** Helper for hex color inputs in the editor: "#RRGGBB" or "#AARRGGBB". */
    public static int parseHexColor(String hex) {
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() == 6) {
            return 0xFF000000 | (int) Long.parseLong(h, 16);
        } else if (h.length() == 8) {
            return (int) Long.parseLong(h, 16);
        }
        throw new IllegalArgumentException("Expected #RRGGBB or #AARRGGBB, got: " + hex);
    }

    public static String toHexColor(int argb, boolean includeAlpha) {
        if (includeAlpha) {
            return String.format("#%08X", argb);
        }
        return String.format("#%06X", argb & 0xFFFFFF);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KrovexTheme t)) return false;
        return accentColor == t.accentColor && backgroundColor == t.backgroundColor
                && panelColor == t.panelColor && textColor == t.textColor
                && fontSize == t.fontSize && cornerRadius == t.cornerRadius
                && spacing == t.spacing && Float.compare(opacity, t.opacity) == 0
                && blurEnabled == t.blurEnabled && glowEnabled == t.glowEnabled
                && animationsEnabled == t.animationsEnabled && builtin == t.builtin
                && id.equals(t.id) && name.equals(t.name) && fontFamily.equals(t.fontFamily);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, accentColor, backgroundColor, panelColor, textColor,
                fontFamily, fontSize, cornerRadius, spacing, opacity, blurEnabled, glowEnabled,
                animationsEnabled, builtin);
    }

    public static final class Builder {
        private String id;
        private String name;
        private int accentColor = DEFAULT_ACCENT;
        private int backgroundColor = DEFAULT_BACKGROUND;
        private int panelColor = DEFAULT_PANEL;
        private int textColor = DEFAULT_TEXT;
        private String fontFamily = DEFAULT_FONT;
        private int fontSize = DEFAULT_FONT_SIZE;
        private int cornerRadius = DEFAULT_CORNER_RADIUS;
        private int spacing = DEFAULT_SPACING;
        private float opacity = DEFAULT_OPACITY;
        private boolean blurEnabled = true;
        private boolean glowEnabled = true;
        private boolean animationsEnabled = true;
        private boolean builtin = false;

        public Builder id(String v) { this.id = v; return this; }
        public Builder name(String v) { this.name = v; return this; }
        public Builder accentColor(int v) { this.accentColor = v; return this; }
        public Builder backgroundColor(int v) { this.backgroundColor = v; return this; }
        public Builder panelColor(int v) { this.panelColor = v; return this; }
        public Builder textColor(int v) { this.textColor = v; return this; }
        public Builder fontFamily(String v) { this.fontFamily = v; return this; }
        public Builder fontSize(int v) { this.fontSize = v; return this; }
        public Builder cornerRadius(int v) { this.cornerRadius = v; return this; }
        public Builder spacing(int v) { this.spacing = v; return this; }
        public Builder opacity(float v) { this.opacity = v; return this; }
        public Builder blurEnabled(boolean v) { this.blurEnabled = v; return this; }
        public Builder glowEnabled(boolean v) { this.glowEnabled = v; return this; }
        public Builder animationsEnabled(boolean v) { this.animationsEnabled = v; return this; }
        public Builder builtin(boolean v) { this.builtin = v; return this; }

        public KrovexTheme build() {
            if (id == null) id = UUID.randomUUID().toString();
            return new KrovexTheme(this);
        }
    }
}

package com.krovex.client.sky;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One sky background (section 5). The four built-in animated presets share this same
 * model -- what differs between "flowing topographic lines" and "aurora ribbons" is
 * only the renderer that reads these fields, not the data shape. An imported image
 * sky uses the same class with {@link #isImageSky()} true and the animation/tint
 * controls simply not shown for it (section 5: "Imported skies stay static and keep
 * their original colors, so hide the animation and tint controls for them").
 */
public final class SkyPreset {

    public enum Kind { VANILLA, TOPOGRAPHIC_LINES, THUNDERCLOUDS, AURORA_RIBBONS, STARFIELD, NEBULA, SYNTHWAVE, GALAXY, DUSK,
        SNOWFALL, SAKURA, OCEAN, INFERNO, MATRIX, GOLDEN_HOUR, VORTEX, FIREFLIES,
        PRISM, RAINFALL, COSMIC_RIFT, METEORS, BLACK_HOLE, LANTERNS, TEMPEST, IMAGE }

    private final String id;
    private String name;
    private Kind kind;
    private int primaryColorArgb;
    private int secondaryColorArgb;
    private float animationSpeed;   // 0 (paused) .. 2 (double speed); irrelevant for VANILLA/IMAGE
    private float intensity;        // 0..1 visual strength/brightness of the effect
    private boolean animationEnabled = true;
    private boolean lowMotion;      // reduced-motion accessibility option
    private String imagePath;       // only for Kind.IMAGE

    public SkyPreset(String id, String name, Kind kind) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.primaryColorArgb = 0xFF8A4FFF;
        this.secondaryColorArgb = 0xFF3E7BFA;
        this.animationSpeed = 1.0f;
        this.intensity = 0.7f;
        this.lowMotion = false;
    }

    public String id() { return id; }
    public String name() { return name; }
    public void setName(String v) { this.name = v; }
    public Kind kind() { return kind; }
    public int primaryColorArgb() { return primaryColorArgb; }
    public void setPrimaryColorArgb(int v) { this.primaryColorArgb = v; }
    public int secondaryColorArgb() { return secondaryColorArgb; }
    public void setSecondaryColorArgb(int v) { this.secondaryColorArgb = v; }
    public float animationSpeed() { return animationSpeed; }
    public void setAnimationSpeed(float v) { this.animationSpeed = Math.max(0f, Math.min(2f, v)); }
    public float intensity() { return intensity; }
    public void setIntensity(float v) { this.intensity = Math.max(0f, Math.min(1f, v)); }
    public boolean animationEnabled() { return animationEnabled; }
    public void setAnimationEnabled(boolean value) { animationEnabled = value; }
    public boolean lowMotion() { return lowMotion; }
    public void setLowMotion(boolean v) { this.lowMotion = v; }
    public String imagePath() { return imagePath; }
    public void setImagePath(String v) { this.imagePath = v; }

    public boolean isImageSky() { return kind == Kind.IMAGE; }
    public boolean isVanilla() { return kind == Kind.VANILLA; }
    /** Whether the animation-speed/intensity/color controls apply at all (hidden for Vanilla and imported images). */
    public boolean supportsAnimationControls() { return !isVanilla() && !isImageSky(); }

    /**
     * The effective animation progress for a frame, given elapsed world time and the
     * low-motion option -- renderers multiply their own phase math by this. Kept here
     * (not in the renderer) so the "low motion" and "animation speed" behavior is
     * identical and testable across all four animated presets.
     */
    public float animationPhase(long elapsedMillis) {
        if (!animationEnabled || kind == Kind.VANILLA || kind == Kind.IMAGE) return 0f;
        float effectiveSpeed = lowMotion ? Math.min(animationSpeed, 0.25f) : animationSpeed;
        float cycleMillis = 20_000f; // 20s base cycle, scaled by speed
        return (elapsedMillis * effectiveSpeed / cycleMillis) % 1.0f;
    }

    public static SkyPreset vanilla() {
        return new SkyPreset("vanilla", "Vanilla Sky", Kind.VANILLA);
    }

    public static SkyPreset topographicLines() {
        SkyPreset p = new SkyPreset("topographic-lines", "Topographic Lines", Kind.TOPOGRAPHIC_LINES);
        p.setPrimaryColorArgb(0xFF8A4FFF);
        p.setSecondaryColorArgb(0xFF121014);
        return p;
    }

    public static SkyPreset thunderclouds() {
        SkyPreset p = new SkyPreset("thunderclouds", "Rolling Thunderclouds", Kind.THUNDERCLOUDS);
        p.setPrimaryColorArgb(0xFFC4D0EA);   // lit cloud tops (cool white)
        p.setSecondaryColorArgb(0xFF141826); // dark cloud bases / storm sky
        p.setIntensity(1.0f);
        return p;
    }

    public static SkyPreset auroraRibbons() {
        SkyPreset p = new SkyPreset("aurora-ribbons", "Aurora Ribbons", Kind.AURORA_RIBBONS);
        p.setPrimaryColorArgb(0xFF4DFFA3);
        p.setSecondaryColorArgb(0xFF4F8CFF);
        p.setIntensity(1.0f); // was too subtle at the default 0.7 -- ribbons need to actually glow
        return p;
    }

    public static SkyPreset starfield() {
        SkyPreset p = new SkyPreset("starfield", "Starfield", Kind.STARFIELD);
        p.setPrimaryColorArgb(0xFFFFFFFF);
        p.setSecondaryColorArgb(0xFF0D1220);
        p.setIntensity(1.0f); // the default 0.7 made 2px star points nearly invisible
        p.setAnimationSpeed(0.3f); // a "slow-moving" starfield, per spec
        return p;
    }

    public static SkyPreset nebula() {
        SkyPreset p = new SkyPreset("nebula", "Nebula", Kind.NEBULA);
        p.setPrimaryColorArgb(0xFFC15BFF);
        p.setSecondaryColorArgb(0xFF070518);
        p.setIntensity(1.0f);
        p.setAnimationSpeed(0.5f);
        return p;
    }

    public static SkyPreset synthwave() {
        SkyPreset p = new SkyPreset("synthwave", "Synthwave", Kind.SYNTHWAVE);
        p.setPrimaryColorArgb(0xFFFF3EA5);
        p.setSecondaryColorArgb(0xFF0B0224);
        p.setIntensity(1.0f);
        p.setAnimationSpeed(0.7f);
        return p;
    }

    public static SkyPreset galaxy() {
        SkyPreset p = new SkyPreset("galaxy", "Galaxy", Kind.GALAXY);
        p.setPrimaryColorArgb(0xFFFFD9A0);
        p.setSecondaryColorArgb(0xFF04060F);
        p.setIntensity(1.0f);
        p.setAnimationSpeed(0.4f);
        return p;
    }

    public static SkyPreset dusk() {
        SkyPreset p = new SkyPreset("dusk", "Dusk", Kind.DUSK);
        p.setPrimaryColorArgb(0xFFFF9E73);
        p.setSecondaryColorArgb(0xFF1A1440);
        p.setIntensity(1.0f);
        p.setAnimationSpeed(0.5f);
        return p;
    }

    public static SkyPreset snowfall() { return make("snowfall", "Winter Snowfall", Kind.SNOWFALL, 0xFFEAF4FF, 0xFF16223D, .6f); }
    public static SkyPreset sakura() { return make("sakura", "Cherry Blossom", Kind.SAKURA, 0xFFFFB3CC, 0xFF3A2860, .5f); }
    public static SkyPreset ocean() { return make("ocean", "Deep Ocean", Kind.OCEAN, 0xFF5FE8E0, 0xFF021824, .6f); }
    public static SkyPreset inferno() { return make("inferno", "Inferno", Kind.INFERNO, 0xFFFF7A1F, 0xFF1A0507, .8f); }
    public static SkyPreset matrix() { return make("matrix", "Digital Rain", Kind.MATRIX, 0xFF3DFF8A, 0xFF010A05, .9f); }
    public static SkyPreset goldenHour() { return make("golden-hour", "Golden Hour", Kind.GOLDEN_HOUR, 0xFFFFC46B, 0xFF28466E, .4f); }
    public static SkyPreset vortex() { return make("vortex", "Vortex", Kind.VORTEX, 0xFF4DD2FF, 0xFFE040FF, .8f); }
    public static SkyPreset fireflies() { return make("fireflies", "Firefly Night", Kind.FIREFLIES, 0xFFD6FF63, 0xFF04101A, .7f); }

    public static SkyPreset prism() { return make("prism", "Prism", Kind.PRISM, 0xFFFFFFFF, 0xFF5A8FE0, .4f); }
    public static SkyPreset rainfall() { return make("rainfall", "Rainfall", Kind.RAINFALL, 0xFFBFD0E8, 0xFF1B2230, 1f); }
    public static SkyPreset cosmicRift() { return make("cosmic-rift", "Cosmic Rift", Kind.COSMIC_RIFT, 0xFF8EF3FF, 0xFFD43CFF, .7f); }
    public static SkyPreset meteors() { return make("meteors", "Meteor Shower", Kind.METEORS, 0xFFFFE3B0, 0xFF0B1230, 1f); }

    public static SkyPreset blackHole() { return make("black-hole", "Black Hole", Kind.BLACK_HOLE, 0xFFFFB347, 0xFF050818, .5f); }
    public static SkyPreset lanterns() { return make("lanterns", "Lantern Festival", Kind.LANTERNS, 0xFFFFB04A, 0xFF14183A, .6f); }

    public static SkyPreset tempest() { return make("tempest", "Tempest", Kind.TEMPEST, 0xFF9A9EA8, 0xFF0B0C11, .7f); }

    private static SkyPreset make(String id, String name, Kind kind, int primary, int secondary, float speed) {
        SkyPreset p = new SkyPreset(id, name, kind);
        p.setPrimaryColorArgb(primary);
        p.setSecondaryColorArgb(secondary);
        p.setIntensity(1.0f);
        p.setAnimationSpeed(speed);
        return p;
    }

    public static SkyPreset importedImage(String id, String name, String imagePath) {
        SkyPreset p = new SkyPreset(id, name, Kind.IMAGE);
        p.imagePath = imagePath;
        return p;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("kind", kind.name());
        m.put("primaryColorArgb", (long) primaryColorArgb);
        m.put("secondaryColorArgb", (long) secondaryColorArgb);
        m.put("animationSpeed", (double) animationSpeed);
        m.put("intensity", (double) intensity);
        m.put("animationEnabled", animationEnabled);
        m.put("lowMotion", lowMotion);
        if (imagePath != null) m.put("imagePath", imagePath);
        return m;
    }

    public static SkyPreset fromMap(Map<String, Object> m) {
        String id = (String) m.get("id");
        String name = (String) m.get("name");
        Kind kind = Kind.valueOf((String) m.getOrDefault("kind", "VANILLA"));
        SkyPreset p = new SkyPreset(id, name, kind);
        p.primaryColorArgb = intOr(m, "primaryColorArgb", p.primaryColorArgb);
        p.secondaryColorArgb = intOr(m, "secondaryColorArgb", p.secondaryColorArgb);
        p.animationSpeed = (float) doubleOr(m, "animationSpeed", 1.0);
        p.intensity = (float) doubleOr(m, "intensity", 0.7);
        p.lowMotion = m.get("lowMotion") instanceof Boolean b && b;
        p.imagePath = (String) m.get("imagePath");
        p.animationEnabled = !Boolean.FALSE.equals(m.get("animationEnabled"));
        return p;
    }

    private static int intOr(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOr(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }
}


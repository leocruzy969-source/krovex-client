package com.krovex.client.visuals;

import com.krovex.client.KrovexConfigPaths;
import com.krovex.theme.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Color Grading settings: four sliders (saturation, contrast, warmth, shadows), an on/off switch, and named presets.
 * Natural / Vibrant / Midnight ship built in; everything the player saves is stored next to them in color-grade.json.
 */
public final class ColorGradeStore {
    /** One look. Neutral is 1 / 1 / 0 / 0. */
    public record Look(String name, float saturation, float contrast, float warmth, float shadows) {
        public boolean neutral() { return Math.abs(saturation - 1) < .002f && Math.abs(contrast - 1) < .002f && Math.abs(warmth) < .002f && Math.abs(shadows) < .002f; }
    }

    public static final List<Look> BUILT_IN = List.of(
            new Look("Natural", 1f, 1f, 0f, 0f),
            new Look("Vibrant", 1.4f, 1.1f, .12f, .1f),
            new Look("Midnight", .85f, 1.2f, -.4f, -.3f));

    private static ColorGradeStore instance;

    public static ColorGradeStore get() {
        if (instance == null) instance = load();
        return instance;
    }

    private boolean enabled;
    private float saturation = 1f, contrast = 1f, warmth, shadows;
    private String activePreset = "Natural";
    private final List<Look> saved = new ArrayList<>();

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public float saturation() { return saturation; }
    public float contrast() { return contrast; }
    public float warmth() { return warmth; }
    public float shadows() { return shadows; }
    public String activePreset() { return activePreset; }

    // the sliders: every change is live on the very next frame (the renderer reads these each frame)
    public void setSaturation(float v) { saturation = Math.clamp(v, 0f, 2f); activePreset = ""; }
    public void setContrast(float v) { contrast = Math.clamp(v, .5f, 1.5f); activePreset = ""; }
    public void setWarmth(float v) { warmth = Math.clamp(v, -1f, 1f); activePreset = ""; }
    public void setShadows(float v) { shadows = Math.clamp(v, -1f, 1f); activePreset = ""; }

    public boolean neutral() { return new Look("", saturation, contrast, warmth, shadows).neutral(); }

    /** Built-in presets first, then the player's own. */
    public List<Look> presets() {
        List<Look> all = new ArrayList<>(BUILT_IN);
        all.addAll(saved);
        return all;
    }

    public boolean isBuiltIn(String name) { return BUILT_IN.stream().anyMatch(l -> l.name().equalsIgnoreCase(name)); }

    public void apply(Look look) {
        saturation = look.saturation();
        contrast = look.contrast();
        warmth = look.warmth();
        shadows = look.shadows();
        activePreset = look.name();
        enabled = true;
    }

    /** Saves the current sliders under {@code name} (replacing a saved preset of the same name; built-ins cannot be overwritten). */
    public boolean saveCurrentAs(String name) {
        name = name.strip();
        if (name.isEmpty() || isBuiltIn(name)) return false;
        Look look = new Look(name, saturation, contrast, warmth, shadows);
        saved.removeIf(l -> l.name().equalsIgnoreCase(look.name()));
        saved.add(look);
        activePreset = look.name();
        save();
        return true;
    }

    public boolean delete(String name) {
        boolean removed = saved.removeIf(l -> l.name().equalsIgnoreCase(name));
        if (removed) save();
        return removed;
    }

    private static Map<String, Object> toMap(Look l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", l.name());
        m.put("saturation", (double) l.saturation());
        m.put("contrast", (double) l.contrast());
        m.put("warmth", (double) l.warmth());
        m.put("shadows", (double) l.shadows());
        return m;
    }

    public void save() {
        try {
            var file = KrovexConfigPaths.configDir().resolve("color-grade.json");
            Files.createDirectories(file.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("enabled", enabled);
            root.put("saturation", (double) saturation);
            root.put("contrast", (double) contrast);
            root.put("warmth", (double) warmth);
            root.put("shadows", (double) shadows);
            root.put("activePreset", activePreset);
            List<Object> list = new ArrayList<>();
            for (Look look : saved) list.add(toMap(look));
            root.put("presets", list);
            Files.writeString(file, Json.write(root, true), StandardCharsets.UTF_8);
        } catch (IOException ignored) { /* losing a colour setting is not worth a crash */ }
    }

    private static float number(Map<?, ?> m, String key, float fallback) {
        return m.get(key) instanceof Number n ? n.floatValue() : fallback;
    }

    private static ColorGradeStore load() {
        ColorGradeStore store = new ColorGradeStore();
        var file = KrovexConfigPaths.configDir().resolve("color-grade.json");
        if (!Files.exists(file)) return store;
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            store.enabled = Boolean.TRUE.equals(m.get("enabled"));
            store.saturation = Math.clamp(number(m, "saturation", 1f), 0f, 2f);
            store.contrast = Math.clamp(number(m, "contrast", 1f), .5f, 1.5f);
            store.warmth = Math.clamp(number(m, "warmth", 0f), -1f, 1f);
            store.shadows = Math.clamp(number(m, "shadows", 0f), -1f, 1f);
            store.activePreset = m.get("activePreset") instanceof String s ? s : "";
            if (m.get("presets") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> p && p.get("name") instanceof String name) {
                        store.saved.add(new Look(name, number(p, "saturation", 1f), number(p, "contrast", 1f), number(p, "warmth", 0f), number(p, "shadows", 0f)));
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) { /* keep defaults */ }
        return store;
    }
}
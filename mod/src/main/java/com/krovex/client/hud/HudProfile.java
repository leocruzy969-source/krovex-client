package com.krovex.client.hud;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A complete named set of HUD element positions (section 3/original spec: "Let me
 * save separate HUD and module profiles for PvP, survival, and recording"). Krovex
 * ships three built-in profiles by that name; the user can also make their own.
 */
public final class HudProfile {

    private final String name;
    private String appearanceThemeId;
    private final Map<String, HudElementLayout> elements = new LinkedHashMap<>();

    public HudProfile(String name) {
        this.name = name;
    }

    public String appearanceThemeId() { return appearanceThemeId; }
    public void setAppearanceThemeId(String id) { appearanceThemeId = id; }

    public String name() { return name; }

    public HudElementLayout layoutFor(String elementId) {
        return elements.computeIfAbsent(elementId, HudElementLayout::new);
    }

    public void putLayout(HudElementLayout layout) {
        elements.put(layout.elementId(), layout);
    }

    public Map<String, HudElementLayout> allLayouts() { return Map.copyOf(elements); }

    public void resetElement(String elementId) {
        layoutFor(elementId).reset();
    }

    public void resetAll() {
        for (HudElementLayout layout : elements.values()) layout.reset();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> elementsMap = new LinkedHashMap<>();
        for (var e : elements.entrySet()) elementsMap.put(e.getKey(), e.getValue().toMap());
        m.put("name", name);
        if (appearanceThemeId != null) m.put("appearanceThemeId", appearanceThemeId);
        m.put("elements", elementsMap);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static HudProfile fromMap(Map<String, Object> m) {
        String name = (String) m.getOrDefault("name", "Default");
        HudProfile profile = new HudProfile(name);
        if (m.get("appearanceThemeId") instanceof String id) profile.appearanceThemeId = id;
        Object elementsObj = m.get("elements");
        if (elementsObj instanceof Map<?, ?> elementsMap) {
            for (var e : ((Map<String, Object>) elementsMap).entrySet()) {
                profile.putLayout(HudElementLayout.fromMap(e.getKey(), (Map<String, Object>) e.getValue()));
            }
        }
        return profile;
    }

    /** The three built-in starting points named in the spec; all start from the same sensible defaults. */
    public static HudProfile createBuiltin(String name) {
        return new HudProfile(name);
    }
}

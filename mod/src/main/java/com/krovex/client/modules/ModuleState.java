package com.krovex.client.modules;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-module persisted state: enabled flag, favorite flag, and its rebindable keybind's saved code (if rebound). */
public final class ModuleState {
    private final String moduleId;
    private boolean enabled;
    private boolean favorite;
    private Integer setting;
    private Integer customKeyCode; // null = use the module's shipped default binding
    private int backgroundOpacity = 100;   // HUD widgets: 0 = no background panel, 100 = fully solid

    public ModuleState(String moduleId) {
        this.moduleId = moduleId;
        // defaults for a fresh install: everything on except Coordinates and Motion Controls (the user's chosen starting set)
        this.enabled = !java.util.Set.of("coordinates", "motion-controls").contains(moduleId);
    }

    public String moduleId() { return moduleId; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public boolean favorite() { return favorite; }
    public void setFavorite(boolean v) { this.favorite = v; }
    public Integer customKeyCode() { return customKeyCode; }
    public void setCustomKeyCode(Integer v) { this.customKeyCode = v; }

    public int backgroundOpacity() { return backgroundOpacity; }
    public void setBackgroundOpacity(int percent) { this.backgroundOpacity = Math.max(0, Math.min(100, percent)); }
    public boolean transparent() { return backgroundOpacity <= 0; }

    private final Map<String, Double> values = new LinkedHashMap<>();

    /** Extra named settings for modules with more than one (sliders, toggles stored as 0/1). */
    public double value(String key, double fallback) { Double v = values.get(key); return v == null ? fallback : v; }
    public boolean flag(String key, boolean fallback) { return value(key, fallback ? 1 : 0) >= .5; }
    public void setValue(String key, double v) { values.put(key, v); }
    public void setFlag(String key, boolean v) { values.put(key, v ? 1.0 : 0.0); }

    public int setting(int fallback) { return setting == null ? fallback : setting; }
    public void setSetting(int value) { setting = value; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("moduleId", moduleId);
        m.put("enabled", enabled);
        m.put("favorite", favorite);
        if (backgroundOpacity != 100) m.put("opacity", (long) backgroundOpacity);
        if (setting != null) m.put("setting", setting.longValue());
        if (customKeyCode != null) m.put("customKeyCode", (long) (int) customKeyCode);
        if (!values.isEmpty()) m.put("values", new LinkedHashMap<String, Object>(values));
        return m;
    }

    public static ModuleState fromMap(Map<String, Object> m) {
        ModuleState s = new ModuleState((String) m.get("moduleId"));
        if (m.get("enabled") instanceof Boolean enabled) s.enabled = enabled;
        if (m.get("setting") instanceof Number value) s.setting = value.intValue();
        s.favorite = m.get("favorite") instanceof Boolean b && b;
        if (m.get("opacity") instanceof Number o) s.backgroundOpacity = Math.max(0, Math.min(100, o.intValue()));
        else if (m.get("transparent") instanceof Boolean t && t) s.backgroundOpacity = 0;   // saved by the old Solid/Transparent switch
        s.customKeyCode = m.get("customKeyCode") instanceof Number n ? n.intValue() : null;
        if (m.get("values") instanceof Map<?, ?> saved) {
            for (var e : saved.entrySet()) if (e.getKey() instanceof String k && e.getValue() instanceof Number n) s.values.put(k, n.doubleValue());
        }
        return s;
    }
}

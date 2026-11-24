package com.krovex.client.textures;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row in the Textures tab's priority-ordered list (icon/description/compatibility
 * shown by the panel; this class carries the persisted enabled flag and order index).
 * Order in {@link TexturePackOrderStore#all()} IS the priority order, highest first,
 * matching vanilla's own resource-pack stacking convention.
 */
public final class TexturePackEntry {
    private final String fileName; // matches the actual file/folder name under resourcepacks/
    private boolean enabled;

    public TexturePackEntry(String fileName, boolean enabled) {
        this.fileName = fileName;
        this.enabled = enabled;
    }

    public String fileName() { return fileName; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fileName", fileName);
        m.put("enabled", enabled);
        return m;
    }

    public static TexturePackEntry fromMap(Map<String, Object> m) {
        return new TexturePackEntry((String) m.get("fileName"), !(m.get("enabled") instanceof Boolean b) || b);
    }
}

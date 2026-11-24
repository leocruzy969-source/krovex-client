package com.krovex.client.escapemenu;

import com.krovex.theme.json.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The "Custom Pause Appearance" toggle (section 4), settable from both the Escape
 * menu itself and Krovex Settings, per spec: "Put a Custom Pause Appearance toggle
 * at the bottom, and also in Krovex Settings so I can turn it back on." Turning it
 * off restores vanilla's own pause-screen look while profile data (skin/username
 * lookup, theme) stays intact for whenever it's turned back on.
 */
public final class EscapeMenuSettings {

    private boolean customPauseAppearanceEnabled = true; // on by default -- it's the headline feature

    public boolean customPauseAppearanceEnabled() { return customPauseAppearanceEnabled; }
    public void setCustomPauseAppearanceEnabled(boolean v) { this.customPauseAppearanceEnabled = v; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customPauseAppearanceEnabled", customPauseAppearanceEnabled);
        return m;
    }

    public static EscapeMenuSettings fromMap(Map<String, Object> m) {
        EscapeMenuSettings settings = new EscapeMenuSettings();
        if (m == null) return settings;
        settings.customPauseAppearanceEnabled = !(m.get("customPauseAppearanceEnabled") instanceof Boolean b) || b;
        return settings;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(toMap(), true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static EscapeMenuSettings load(Path file) {
        if (!Files.exists(file)) return new EscapeMenuSettings();
        try {
            return fromMap(Json.parseObject(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return new EscapeMenuSettings();
        }
    }
}

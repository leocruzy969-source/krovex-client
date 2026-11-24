package com.krovex.client.config;

import com.krovex.theme.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** The "Bubbly" look switch (Theme panel): glowing, fully rounded modules and menus. */
public final class StylePreference {
    private boolean bubbly = false;
    private boolean rainbow = false;     // "RGB wave": the accent colour cycles through every hue
    private float rainbowSpeed = 1f;     // 0.25 .. 3

    public boolean bubbly() { return bubbly; }
    public void setBubbly(boolean v) { this.bubbly = v; }
    public boolean rainbow() { return rainbow; }
    public void setRainbow(boolean v) { this.rainbow = v; }
    public float rainbowSpeed() { return rainbowSpeed; }
    public void setRainbowSpeed(float v) { this.rainbowSpeed = Math.max(.25f, Math.min(3f, v)); }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(Map.of("bubbly", bubbly, "rainbow", rainbow, "rainbowSpeed", (double) rainbowSpeed), true), StandardCharsets.UTF_8);
        } catch (IOException ignored) { /* a lost preference is not worth crashing for */ }
    }

    public static StylePreference load(Path file) {
        StylePreference pref = new StylePreference();
        if (!Files.exists(file)) return pref;
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            pref.bubbly = m.get("bubbly") instanceof Boolean b && b;
            pref.rainbow = m.get("rainbow") instanceof Boolean r && r;
            if (m.get("rainbowSpeed") instanceof Number n) pref.setRainbowSpeed(n.floatValue());
        } catch (IOException | RuntimeException ignored) { /* keep default */ }
        return pref;
    }
}
package com.krovex.client.config;

import com.krovex.theme.json.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The single on/off switch the whole popup-vs-full-menu interaction hinges on
 * (original spec, "GET THIS INTERACTION RIGHT"): "Show quick popup," enabled by
 * default. On: Right Shift opens the small popup first. Off: Right Shift jumps
 * straight to the full menu. Settable from Settings AND from inside the full menu.
 */
public final class PopupPreference {

    private boolean showQuickPopup = true;

    public boolean showQuickPopup() { return showQuickPopup; }
    public void setShowQuickPopup(boolean v) { this.showQuickPopup = v; }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(Map.of("showQuickPopup", showQuickPopup), true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PopupPreference load(Path file) {
        PopupPreference pref = new PopupPreference();
        if (!Files.exists(file)) return pref; // default: ON, per spec
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            pref.showQuickPopup = !(m.get("showQuickPopup") instanceof Boolean b) || b;
        } catch (IOException ignored) {
            // fall back to the default
        }
        return pref;
    }
}

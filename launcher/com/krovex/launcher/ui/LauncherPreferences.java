package com.krovex.launcher.ui;

import com.krovex.launcher.model.Installation;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

public final class LauncherPreferences {
    private final Path file;
    private final Properties values = new Properties();

    public LauncherPreferences(Path home) {
        file = home.resolve("launcher-settings.properties");
        try (var input = Files.newInputStream(file)) {
            values.load(input);
        } catch (IOException | IllegalArgumentException ignored) {
            values.clear();
        }
    }

    public String text(String key, String fallback) { return values.getProperty(key, fallback); }
    public boolean flag(String key, boolean fallback) {
        return Boolean.parseBoolean(text(key, Boolean.toString(fallback)));
    }
    public int integer(String key, int fallback) {
        try { return Integer.parseInt(text(key, "")); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    void save(Properties changes) throws IOException {
        Properties updated = new Properties();
        updated.putAll(values);
        updated.putAll(changes);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "settings-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                updated.store(output, "Krovex launcher settings");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            values.putAll(changes);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
    public void applyDefaults(Installation installation) {
        installation.setMemoryMinMb(integer("memory.min", installation.memoryMinMb()));
        installation.setMemoryMaxMb(integer("memory.max", installation.memoryMaxMb()));
        installation.setWindowWidth(integer("window.width", installation.windowWidth()));
        installation.setWindowHeight(integer("window.height", installation.windowHeight()));
        installation.setFullscreen(flag("window.fullscreen", installation.fullscreen()));
        String java = text("java.path", "");
        if (!java.isBlank()) installation.setJavaRuntimePath(Path.of(java));
    }
}

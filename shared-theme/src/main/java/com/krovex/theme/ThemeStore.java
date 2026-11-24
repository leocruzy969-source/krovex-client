package com.krovex.theme;

import com.krovex.theme.json.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads/writes `.krovextheme` files under a themes directory, plus a small
 * `active-theme.json` pointer file. Both the launcher and the mod point this
 * at the SAME on-disk directory (see README "Shared theme directory"), which is
 * how a theme created in one shows up in the other without any network or IPC.
 *
 * Not thread-safe; callers on the Swing EDT / render thread should not need to
 * share an instance across threads, but if they do, synchronize externally.
 */
public final class ThemeStore {

    private static final String ACTIVE_POINTER_FILE = "active-theme.json";
    private static final String THEME_EXTENSION = ".krovextheme";

    private final Path themesDir;

    public ThemeStore(Path krovexHomeDir) {
        this.themesDir = krovexHomeDir.resolve("themes");
    }

    /** Creates the themes directory and writes the four built-in presets if they're missing. */
    public void initializeIfNeeded() {
        try {
            Files.createDirectories(themesDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create themes directory: " + themesDir, e);
        }
        for (KrovexTheme preset : ThemePresets.all()) {
            Path file = fileFor(preset.id());
            if (!Files.exists(file)) {
                save(preset);
            }
        }
        if (!Files.exists(themesDir.resolve(ACTIVE_POINTER_FILE))) {
            setActiveThemeId(ThemePresets.defaultTheme().id());
        }
    }

    private Path fileFor(String themeId) {
        String safe = themeId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return themesDir.resolve(safe + THEME_EXTENSION);
    }

    public List<KrovexTheme> listThemes() {
        List<KrovexTheme> result = new ArrayList<>();
        if (!Files.isDirectory(themesDir)) return result;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(themesDir, "*" + THEME_EXTENSION)) {
            for (Path p : stream) {
                try {
                    result.add(ThemeJsonCodec.fromJson(Files.readString(p, StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    // Skip a corrupt/foreign file rather than failing the whole listing.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        result.sort((a, b) -> {
            if (a.builtin() != b.builtin()) return a.builtin() ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        return result;
    }

    public Optional<KrovexTheme> findById(String id) {
        Path file = fileFor(id);
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(ThemeJsonCodec.fromJson(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void save(KrovexTheme theme) {
        cachedActive = null;
        try {
            Files.createDirectories(themesDir);
            Files.writeString(fileFor(theme.id()), ThemeJsonCodec.toJson(theme, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save theme " + theme.name(), e);
        }
    }

    /** Deletes a theme file. Refuses to delete a builtin preset (use {@link #resetBuiltin}). */
    public void delete(KrovexTheme theme) {
        cachedActive = null;
        if (theme.builtin()) {
            throw new IllegalArgumentException("Built-in presets cannot be deleted, only reset.");
        }
        try {
            Files.deleteIfExists(fileFor(theme.id()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public KrovexTheme duplicate(KrovexTheme theme, String newName) {
        KrovexTheme copy = theme.duplicateAs(newName);
        save(copy);
        return copy;
    }

    public KrovexTheme rename(KrovexTheme theme, String newName) {
        if (theme.builtin()) {
            throw new IllegalArgumentException("Built-in presets cannot be renamed.");
        }
        KrovexTheme renamed = theme.toBuilder().name(newName).build();
        save(renamed);
        return renamed;
    }

    /** Restores a built-in preset (matched by id) to its shipped defaults. */
    public KrovexTheme resetBuiltin(String builtinId) {
        KrovexTheme fresh = ThemePresets.all().stream()
                .filter(t -> t.id().equals(builtinId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown builtin id: " + builtinId));
        save(fresh);
        return fresh;
    }

    public KrovexTheme importFrom(Path file) {
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            KrovexTheme imported = ThemeJsonCodec.fromJson(json);
            // Imported themes always get a fresh id so they never collide with an existing one.
            KrovexTheme withNewId = imported.toBuilder()
                    .id(java.util.UUID.randomUUID().toString())
                    .builtin(false)
                    .build();
            save(withNewId);
            return withNewId;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not import theme from " + file, e);
        }
    }

    public void exportTo(KrovexTheme theme, Path destination) {
        try {
            Files.writeString(destination, ThemeJsonCodec.toJson(theme, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not export theme to " + destination, e);
        }
    }

    public String getActiveThemeId() {
        Path pointer = themesDir.resolve(ACTIVE_POINTER_FILE);
        if (!Files.exists(pointer)) return ThemePresets.defaultTheme().id();
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(pointer, StandardCharsets.UTF_8));
            Object id = m.get("activeThemeId");
            return id instanceof String s ? s : ThemePresets.defaultTheme().id();
        } catch (IOException e) {
            return ThemePresets.defaultTheme().id();
        }
    }

    // The active theme is read by the HUD and menus every frame; reading two files each time is wasteful, so it is cached for a moment.
    private volatile KrovexTheme cachedActive;
    private volatile long cachedActiveAt;

    public KrovexTheme getActiveTheme() {
        long now = System.nanoTime();
        KrovexTheme cached = cachedActive;
        if (cached != null && now - cachedActiveAt < 250_000_000L) return cached;
        KrovexTheme fresh = findById(getActiveThemeId()).orElseGet(ThemePresets::defaultTheme);
        cachedActive = fresh;
        cachedActiveAt = now;
        return fresh;
    }

    public void setActiveThemeId(String themeId) {
        cachedActive = null;
        try {
            Files.createDirectories(themesDir);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("activeThemeId", themeId);
            Files.writeString(themesDir.resolve(ACTIVE_POINTER_FILE), Json.write(m, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

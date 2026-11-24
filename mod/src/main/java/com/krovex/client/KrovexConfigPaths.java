package com.krovex.client;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Where this mod keeps its own files, under the running game's own config directory
 * (`.minecraft/config/krovex-client/` in the official launcher, or the equivalent for
 * whatever launcher/instance directory the user is running -- this is exactly what
 * makes the mod self-contained: it never assumes a Krovex Launcher home directory
 * exists, per the spec's "must work without installing the Krovex Launcher".
 */
public final class KrovexConfigPaths {
    private KrovexConfigPaths() {}

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("krovex-client");
    }

    public static Path themesDir() { return configDir().resolve("themes"); }
    public static Path hudProfilesFile() { return configDir().resolve("hud-profiles.json"); }
    public static Path musicPreferencesFile() { return configDir().resolve("music-preferences.json"); }
    public static Path skyPresetsFile() { return configDir().resolve("sky-presets.json"); }
    public static Path escapeMenuSettingsFile() { return configDir().resolve("escape-menu.json"); }
    public static Path stylePreferenceFile() { return configDir().resolve("style-preference.json"); }
    public static Path popupPreferenceFile() { return configDir().resolve("popup-preference.json"); }
}

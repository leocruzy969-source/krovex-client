package com.krovex.client.hud;

import com.krovex.theme.json.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persists HUD profiles (and which one is active) to `hud-profiles.json` under the mod's config dir. */
public final class HudProfileStore {

    public static final String PVP = "PvP";
    public static final String SURVIVAL = "Survival";
    public static final String RECORDING = "Recording";

    private final Path file;
    private final List<HudProfile> profiles = new ArrayList<>();
    private String activeProfileName = PVP;

    public HudProfileStore(Path modConfigDir) {
        this.file = modConfigDir.resolve("hud-profiles.json");
    }

    public void initializeIfNeeded() {
        load();
        if (profiles.isEmpty()) {
            profiles.add(HudProfile.createBuiltin(PVP));
            profiles.add(HudProfile.createBuiltin(SURVIVAL));
            profiles.add(HudProfile.createBuiltin(RECORDING));
            activeProfileName = PVP;
            save();
        }
    }

    @SuppressWarnings("unchecked")
    public void load() {
        profiles.clear();
        if (!Files.exists(file)) return;
        try {
            Map<String, Object> root = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            activeProfileName = (String) root.getOrDefault("activeProfile", PVP);
            Object list = root.get("profiles");
            if (list instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m) profiles.add(HudProfile.fromMap((Map<String, Object>) m));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("activeProfile", activeProfileName);
            List<Object> list = new ArrayList<>();
            for (HudProfile p : profiles) list.add(p.toMap());
            root.put("profiles", list);
            Files.writeString(file, Json.write(root, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<HudProfile> all() { return List.copyOf(profiles); }

    public HudProfile active() {
        return findByName(activeProfileName).orElseGet(() -> {
            HudProfile fallback = HudProfile.createBuiltin(PVP);
            profiles.add(fallback);
            return fallback;
        });
    }

    public Optional<HudProfile> findByName(String name) {
        return profiles.stream().filter(p -> p.name().equals(name)).findFirst();
    }

    public void setActive(String name) {
        this.activeProfileName = name;
        save();
    }

    public HudProfile createProfile(String name) {
        HudProfile profile = new HudProfile(name);
        profiles.add(profile);
        save();
        return profile;
    }

    public void deleteProfile(String name) {
        profiles.removeIf(p -> p.name().equals(name));
        if (activeProfileName.equals(name) && !profiles.isEmpty()) {
            activeProfileName = profiles.get(0).name();
        }
        save();
    }
}

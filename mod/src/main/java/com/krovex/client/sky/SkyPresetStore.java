package com.krovex.client.sky;

import com.krovex.theme.json.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persists sky presets (built-ins + imported images) and which one is active. */
public final class SkyPresetStore {

    private final Path configDir;
    private final Path indexFile;
    private final Path importedImagesDir;
    private final List<SkyPreset> presets = new ArrayList<>();
    private boolean enabled = true;
    private String activePresetId = "vanilla";

    public SkyPresetStore(Path modConfigDir) {
        this.configDir = modConfigDir;
        this.indexFile = modConfigDir.resolve("sky-presets.json");
        this.importedImagesDir = modConfigDir.resolve("sky-images");
    }

    public void initializeIfNeeded() {
        load();
        boolean changed = presets.isEmpty();
        if (presets.isEmpty()) activePresetId = "vanilla";
        // add any built-in preset a saved config does not have yet (e.g. ones added in a newer version)
        for (SkyPreset builtin : List.of(SkyPreset.vanilla(), SkyPreset.topographicLines(), SkyPreset.thunderclouds(),
                SkyPreset.auroraRibbons(), SkyPreset.starfield(), SkyPreset.nebula(), SkyPreset.synthwave(),
                SkyPreset.galaxy(), SkyPreset.dusk(), SkyPreset.snowfall(), SkyPreset.sakura(), SkyPreset.ocean(),
                SkyPreset.inferno(), SkyPreset.matrix(), SkyPreset.goldenHour(), SkyPreset.vortex(), SkyPreset.fireflies(),
                SkyPreset.prism(), SkyPreset.rainfall(), SkyPreset.cosmicRift(), SkyPreset.meteors(),
                SkyPreset.blackHole(), SkyPreset.lanterns(), SkyPreset.tempest())) {
            if (findById(builtin.id()).isEmpty()) { presets.add(builtin); changed = true; }
        }
        // the storm used to ship dull grey colours; move those saved copies to the new palette
        var storm = findById("thunderclouds");
        if (storm.isPresent() && storm.get().primaryColorArgb() == 0xFF57566A && storm.get().secondaryColorArgb() == 0xFF15141C) {
            storm.get().setPrimaryColorArgb(0xFFB8C0D6);
            storm.get().setSecondaryColorArgb(0xFF1A1C28);
            changed = true;
        }
        if (storm.isPresent() && storm.get().primaryColorArgb() == 0xFFB8C0D6 && storm.get().secondaryColorArgb() == 0xFF1A1C28) {
            storm.get().setPrimaryColorArgb(0xFFC4D0EA);   // the storm got a cooler, darker palette
            storm.get().setSecondaryColorArgb(0xFF141826);
            changed = true;
        }
        if (changed) save();
    }

    @SuppressWarnings("unchecked")
    public void load() {
        presets.clear();
        if (!Files.exists(indexFile)) return;
        try {
            Map<String, Object> root = Json.parseObject(Files.readString(indexFile, StandardCharsets.UTF_8));
            enabled = !Boolean.FALSE.equals(root.get("enabled"));
            activePresetId = (String) root.getOrDefault("activePresetId", "vanilla");
            Object list = root.get("presets");
            if (list instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m) presets.add(SkyPreset.fromMap((Map<String, Object>) m));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(configDir);
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("enabled", enabled);
            root.put("activePresetId", activePresetId);
            List<Object> list = new ArrayList<>();
            for (SkyPreset p : presets) list.add(p.toMap());
            root.put("presets", list);
            Files.writeString(indexFile, Json.write(root, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<SkyPreset> all() { return List.copyOf(presets); }

    public Optional<SkyPreset> findById(String id) {
        return presets.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public SkyPreset active() {
        return findById(activePresetId).orElseGet(SkyPreset::vanilla);
    }

    public boolean enabled() { return enabled; }

    public void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public void setActive(String id) {
        this.activePresetId = id;
        save();
    }

    public void resetToVanilla() {
        setActive("vanilla");
    }

    /** Imports a PNG/JPG as a new static sky preset, copying it into the mod's own config dir. */
    public SkyPreset importImage(Path sourceImage, String displayName) throws IOException {
        String extension = sourceImage.getFileName().toString().toLowerCase().endsWith(".jpg")
                || sourceImage.getFileName().toString().toLowerCase().endsWith(".jpeg") ? "jpg" : "png";
        String id = "image-" + UUID.randomUUID();
        Files.createDirectories(importedImagesDir);
        Path dest = importedImagesDir.resolve(id + "." + extension);
        Files.copy(sourceImage, dest, StandardCopyOption.REPLACE_EXISTING);

        SkyPreset preset = SkyPreset.importedImage(id, displayName, dest.toString());
        presets.add(preset);
        save();
        return preset;
    }

    public void deletePreset(String id) {
        SkyPreset preset = findById(id).orElse(null);
        if (preset == null || preset.isVanilla() && !"vanilla".equals(preset.id())) return;
        if ("vanilla".equals(id)) return; // Vanilla Sky is permanent, never deletable
        if (preset.isImageSky() && preset.imagePath() != null) {
            try { Files.deleteIfExists(Path.of(preset.imagePath())); } catch (IOException ignored) {}
        }
        presets.removeIf(p -> p.id().equals(id));
        if (activePresetId.equals(id)) activePresetId = "vanilla";
        save();
    }
}

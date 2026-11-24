package com.krovex.client.textures;

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

/**
 * Persists the Textures tab's drag-reordered priority list and enabled flags, kept
 * separately from Minecraft's own `options.txt` resourcePacks list so the panel can
 * show one unified list (with new packs the user drops into the folder appearing
 * automatically) and apply changes via a single reload, per spec: "Let me enable,
 * disable, and drag to reorder them, with one Apply that triggers a single resource
 * reload."
 */
public final class TexturePackOrderStore {

    private final Path resourcePacksDir;
    private final Path orderFile;
    private final List<TexturePackEntry> entries = new ArrayList<>();

    public TexturePackOrderStore(Path resourcePacksDir, Path modConfigDir) {
        this.resourcePacksDir = resourcePacksDir;
        this.orderFile = modConfigDir.resolve("texture-pack-order.json");
    }

    /** Reconciles the saved order against what's actually on disk: new files append at the bottom, removed files drop out. */
    public void refreshFromDisk() {
        load();
        List<String> onDisk = listPackFiles();
        entries.removeIf(e -> !onDisk.contains(e.fileName()));
        for (String fileName : onDisk) {
            if (entries.stream().noneMatch(e -> e.fileName().equals(fileName))) {
                entries.add(new TexturePackEntry(fileName, false)); // newly discovered packs start disabled
            }
        }
        save();
    }

    private List<String> listPackFiles() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(resourcePacksDir)) return names;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(resourcePacksDir)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.startsWith(".")) continue;
                names.add(name);
            }
        } catch (IOException ignored) {
            // an unreadable folder just means no packs are listed
        }
        return names;
    }

    public List<TexturePackEntry> all() { return List.copyOf(entries); }

    /** Moves the pack at fromIndex to toIndex, re-numbering priority -- the drag-reorder operation. */
    public void reorder(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= entries.size() || toIndex < 0 || toIndex >= entries.size()) return;
        TexturePackEntry moved = entries.remove(fromIndex);
        entries.add(toIndex, moved);
        save();
    }

    public void setEnabled(String fileName, boolean enabled) {
        for (TexturePackEntry e : entries) {
            if (e.fileName().equals(fileName)) { e.setEnabled(enabled); break; }
        }
        save();
    }

    @SuppressWarnings("unchecked")
    public void load() {
        entries.clear();
        if (!Files.exists(orderFile)) return;
        try {
            Map<String, Object> root = Json.parseObject(Files.readString(orderFile, StandardCharsets.UTF_8));
            Object list = root.get("packs");
            if (list instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m) entries.add(TexturePackEntry.fromMap((Map<String, Object>) m));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(orderFile.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            List<Object> list = new ArrayList<>();
            for (TexturePackEntry e : entries) list.add(e.toMap());
            root.put("packs", list);
            Files.writeString(orderFile, Json.write(root, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

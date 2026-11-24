package com.krovex.client.gui.screens;

import com.krovex.client.KrovexConfigPaths;
import com.krovex.theme.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pack Switcher: named groups of texture packs (their ids, lowest priority first = the order they are applied in). */
final class PackGroupStore {
    record Group(String name, List<String> packs) {}

    private static PackGroupStore instance;
    private final List<Group> groups = new ArrayList<>();

    static PackGroupStore get() {
        if (instance == null) instance = load();
        return instance;
    }

    List<Group> groups() { return List.copyOf(groups); }

    void put(String name, List<String> packs) {
        groups.removeIf(g -> g.name().equalsIgnoreCase(name));
        groups.add(new Group(name, List.copyOf(packs)));
        save();
    }

    boolean remove(String name) {
        boolean removed = groups.removeIf(g -> g.name().equalsIgnoreCase(name));
        if (removed) save();
        return removed;
    }

    private void save() {
        try {
            var file = KrovexConfigPaths.configDir().resolve("pack-groups.json");
            Files.createDirectories(file.getParent());
            List<Object> list = new ArrayList<>();
            for (Group g : groups) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", g.name());
                m.put("packs", new ArrayList<Object>(g.packs()));
                list.add(m);
            }
            Files.writeString(file, Json.write(Map.of("groups", list), true), StandardCharsets.UTF_8);
        } catch (IOException ignored) { /* a lost group is not worth a crash */ }
    }

    private static PackGroupStore load() {
        PackGroupStore store = new PackGroupStore();
        var file = KrovexConfigPaths.configDir().resolve("pack-groups.json");
        if (!Files.exists(file)) return store;
        try {
            Map<String, Object> root = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            if (root.get("groups") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m && m.get("name") instanceof String name && m.get("packs") instanceof List<?> packs) {
                        List<String> ids = new ArrayList<>();
                        for (Object p : packs) if (p instanceof String id) ids.add(id);
                        store.groups.add(new Group(name, ids));
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) { /* start empty */ }
        return store;
    }
}
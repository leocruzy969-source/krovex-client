package com.krovex.client.modules;

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

/** Persists enabled/favorite/keybind-override state for every module in {@link ModuleRegistry}. */
public final class ModuleStateStore {

    private final Path file;
    private final Map<String, ModuleState> states = new LinkedHashMap<>();

    public ModuleStateStore(Path modConfigDir) {
        this.file = modConfigDir.resolve("modules.json");
    }

    public void initializeIfNeeded() {
        load();
        for (ModuleDefinition def : ModuleRegistry.all()) {
            states.computeIfAbsent(def.id(), ModuleState::new);
        }
        save();
    }

    @SuppressWarnings("unchecked")
    public void load() {
        states.clear();
        if (!Files.exists(file)) return;
        try {
            Map<String, Object> root = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            Object list = root.get("modules");
            if (list instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m) {
                        ModuleState s = ModuleState.fromMap((Map<String, Object>) m);
                        states.put(s.moduleId(), s);
                    }
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
            List<Object> list = new ArrayList<>();
            for (ModuleState s : states.values()) list.add(s.toMap());
            root.put("modules", list);
            Files.writeString(file, Json.write(root, true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ModuleState stateFor(String moduleId) {
        return states.computeIfAbsent(moduleId, ModuleState::new);
    }

    public List<ModuleState> all() { return List.copyOf(states.values()); }
}

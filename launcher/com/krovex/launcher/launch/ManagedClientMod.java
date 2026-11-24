package com.krovex.launcher.launch;

import com.krovex.launcher.model.Installation;
import com.krovex.theme.json.Json;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

public final class ManagedClientMod {
    private static final String VERSION = "1.21.11";
    private static final String KROVEX = "Krovex-Client-1.21.11-0.1.54.jar";
    private static final String FABRIC_API = "fabric-api-0.141.6+1.21.11.jar";

    private ManagedClientMod() {}

    public static boolean supports(String version, Installation.Loader loader) {
        return VERSION.equals(version) && loader == Installation.Loader.FABRIC;
    }

    public static List<Path> prepare(Path home, Installation installation) throws IOException {
        if (!supports(installation.minecraftVersion(), installation.loader())) return List.of();
        Set<String> existing = activeModIds(installation.modsDirectory());
        Path cache = home.resolve("managed-mods").resolve(VERSION).toAbsolutePath();
        List<Path> managed = new ArrayList<>(2);
        if (!existing.contains("krovex-client")) managed.add(extract(cache, KROVEX));
        if (!existing.contains("fabric-api")) managed.add(extract(cache, FABRIC_API));
        return managed;
    }

    private static Path extract(Path cache, String name) throws IOException {
        Files.createDirectories(cache);
        Path destination = cache.resolve(name);
        if (Files.isRegularFile(destination) && Files.size(destination) > 0) return destination;
        try (var resource = ManagedClientMod.class.getResourceAsStream("/assets/managed-mods/" + name)) {
            if (resource == null) throw new IOException("Missing bundled client file " + name);
            Path staging = Files.createTempFile(cache, "managed-", ".tmp");
            try {
                Files.copy(resource, staging, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(staging, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(staging);
            }
        }
        return destination;
    }

    private static Set<String> activeModIds(Path mods) throws IOException {
        Set<String> ids = new HashSet<>();
        if (!Files.isDirectory(mods)) return ids;
        try (var files = Files.list(mods)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (var archive = new ZipFile(file.toFile())) {
                    var entry = archive.getEntry("fabric.mod.json");
                    if (entry == null) continue;
                    try (var metadata = archive.getInputStream(entry)) {
                        byte[] bytes = metadata.readNBytes(65_537);
                        if (bytes.length <= 65_536 && Json.parseObject(new String(bytes, StandardCharsets.UTF_8))
                                .get("id") instanceof String id) ids.add(id);
                    }
                } catch (IOException | RuntimeException ignored) {
                    // Fabric can report unreadable user-installed mods normally.
                }
            }
        }
        return ids;
    }
}

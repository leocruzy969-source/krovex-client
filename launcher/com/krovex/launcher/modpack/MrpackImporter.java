package com.krovex.launcher.modpack;

import com.krovex.launcher.download.DownloadManager;
import com.krovex.launcher.model.Installation;
import com.krovex.launcher.model.InstallationStore;
import com.krovex.launcher.modpack.ModpackFormat;
import com.krovex.launcher.modpack.ModpackPreview;
import com.krovex.theme.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MrpackImporter {
    private final DownloadManager downloadManager = new DownloadManager();

    public ModpackPreview preview(Path archive) throws IOException {
        Map<String, Object> index = this.readIndex(archive);
        String name = (String)index.getOrDefault("name", archive.getFileName().toString());
        Map<?, ?> dependencies = index.get("dependencies") instanceof Map<?, ?> entries ? entries : Map.of();
        String mcVersion = (String)dependencies.get("minecraft");
        String loaderName = null;
        if (dependencies.containsKey("fabric-loader")) {
            loaderName = "FABRIC";
        } else if (dependencies.containsKey("quilt-loader")) {
            loaderName = "QUILT";
        } else if (dependencies.containsKey("forge")) {
            loaderName = "FORGE";
        } else if (dependencies.containsKey("neoforge")) {
            loaderName = "NEOFORGE";
        }
        var files = indexedFiles(index);
        var disabled = new java.util.LinkedHashSet<String>();
        long downloadable = 0;
        for (Map<?, ?> file : files) {
            if (unsupportedOnClient(file)) continue;
            downloadable++;
            if (file.get("path") instanceof String path && isDisabled(path)) disabled.add(path);
        }
        ArrayList<String> contents = new ArrayList<>();
        contents.add(downloadable + " file(s) to download (mods/resource packs/shaders as declared by the pack)");
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            long overridesCount = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = entry.getName();
                if (entry.isDirectory() || !(path.startsWith("overrides/") || path.startsWith("client-overrides/"))) continue;
                overridesCount++;
                String relative = path.substring(path.indexOf('/') + 1);
                if (isDisabled(relative)) disabled.add(relative);
            }
            if (overridesCount > 0) {
                contents.add(overridesCount + " bundled override file(s) (configs, extra resources)");
            }
        }
        if (!disabled.isEmpty()) {
            contents.add(disabled.size() + " disabled file(s)");
            disabled.stream().limit(8).forEach(path -> contents.add("Disabled · " + path));
            if (disabled.size() > 8) contents.add("And " + (disabled.size() - 8) + " more disabled files");
        }
        ArrayList<String> warnings = new ArrayList<String>();
        if (loaderName == null) {
            warnings.add("Could not determine the mod loader from this pack's dependencies.");
        }
        return new ModpackPreview(ModpackFormat.MODRINTH_MRPACK, name, mcVersion, loaderName, contents, warnings);
    }

    public Installation importAsNewInstance(Path archive, InstallationStore installationStore, BiConsumer<Integer, Integer> onProgress) throws IOException {
        Map<String, Object> index = this.readIndex(archive);
        String name = (String)index.getOrDefault("name", "Imported Modpack");
        Map<?, ?> dependencies = index.get("dependencies") instanceof Map<?, ?> entries ? entries : Map.of();
        String mcVersion = (String)dependencies.get("minecraft");
        Installation.Loader loader = Installation.Loader.VANILLA;
        String loaderVersion = null;
        if (dependencies.containsKey("fabric-loader")) {
            loader = Installation.Loader.FABRIC;
            loaderVersion = (String)dependencies.get("fabric-loader");
        } else if (dependencies.containsKey("quilt-loader")) {
            loader = Installation.Loader.QUILT;
            loaderVersion = (String)dependencies.get("quilt-loader");
        } else if (dependencies.containsKey("forge")) {
            loader = Installation.Loader.FORGE;
            loaderVersion = (String)dependencies.get("forge");
        } else if (dependencies.containsKey("neoforge")) {
            loader = Installation.Loader.NEOFORGE;
            loaderVersion = (String)dependencies.get("neoforge");
        }
        Installation installation = installationStore.create(name, mcVersion == null ? "unknown" : mcVersion, loader);
        if (loaderVersion != null) {
            installation.setLoaderVersion(loaderVersion);
        }
        try (ZipFile zip = new ZipFile(archive.toFile());){
            this.extractOverrides(zip, "overrides/", installation.gameDirectory());
            this.extractOverrides(zip, "client-overrides/", installation.gameDirectory());
        }
        var files = indexedFiles(index).stream().filter(file -> !unsupportedOnClient(file)).toList();
        Path root = installation.gameDirectory().toAbsolutePath().normalize();
        int completed = 0;
        for (Map<?, ?> file : files) {
            if (file.get("path") instanceof String path && file.get("downloads") instanceof List<?> urls
                    && file.get("hashes") instanceof Map<?, ?> hashes && hashes.get("sha1") instanceof String sha1) {
                Path destination = safeDestination(root, path);
                if (destination != null) {
                    for (Object candidate : urls) {
                        if (!(candidate instanceof String url)) continue;
                        try {
                            downloadManager.download(url, destination, sha1, null);
                            break;
                        } catch (IOException ignored) {
                            // Try the next mirror.
                        }
                    }
                }
            }
            completed++;
            if (onProgress != null) onProgress.accept(completed, files.size());
        }
        installationStore.save();
        return installation;
    }

    private static List<Map<?, ?>> indexedFiles(Map<String, Object> index) {
        if (!(index.get("files") instanceof List<?> files)) return List.of();
        List<Map<?, ?>> entries = new ArrayList<>();
        for (Object file : files) if (file instanceof Map<?, ?> entry) entries.add(entry);
        return entries;
    }

    private static boolean unsupportedOnClient(Map<?, ?> file) {
        return file.get("env") instanceof Map<?, ?> environment && "unsupported".equals(environment.get("client"));
    }

    private static boolean isDisabled(String path) {
        return path.toLowerCase(java.util.Locale.ROOT).endsWith(".disabled");
    }

    private void extractOverrides(ZipFile zip, String prefix, Path destination) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().startsWith(prefix)) continue;
            Path target = safeDestination(root, entry.getName().substring(prefix.length()));
            if (target == null) continue;
            Files.createDirectories(target.getParent());
            try (InputStream input = zip.getInputStream(entry)) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path safeDestination(Path root, String relative) {
        if (relative.isBlank() || relative.indexOf(':') >= 0) return null;
        try {
            Path path = Path.of(relative);
            if (path.isAbsolute()) return null;
            for (Path segment : path) if (segment.toString().equals("..")) return null;
            Path result = root.resolve(path).normalize();
            return result.startsWith(root) && !result.equals(root) ? result : null;
        } catch (java.nio.file.InvalidPathException ignored) {
            return null;
        }
    }

    private Map<String, Object> readIndex(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry("modrinth.index.json");
            if (entry == null) throw new IOException("Not a valid .mrpack (missing modrinth.index.json)");
            try (InputStream input = zip.getInputStream(entry)) {
                return Json.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}

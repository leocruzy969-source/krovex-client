package com.krovex.launcher.launch;

import com.krovex.launcher.download.DownloadManager;
import com.krovex.launcher.launch.AssetSyncer;
import com.krovex.launcher.launch.JavaRuntimeLocator;
import com.krovex.launcher.launch.LaunchArgumentBuilder;
import com.krovex.launcher.launch.LibraryResolver;
import com.krovex.launcher.launch.NativesExtractor;
import com.krovex.launcher.model.Installation;
import com.krovex.launcher.model.MicrosoftAccount;
import com.krovex.launcher.model.MinecraftVersion;
import com.krovex.launcher.network.FabricMetaClient;
import com.krovex.launcher.network.LoaderCatalog;
import com.krovex.launcher.network.QuiltMetaClient;
import com.krovex.launcher.network.VersionManifestClient;
import com.krovex.launcher.network.VersionMetadata;
import com.krovex.launcher.network.VersionMetadataClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

public final class GameLauncher {
    private final Path krovexHome;
    private final VersionMetadataClient versionMetadataClient = new VersionMetadataClient();
    private final FabricMetaClient fabricMetaClient = new FabricMetaClient();
    private final QuiltMetaClient quiltMetaClient = new QuiltMetaClient();
    private final LoaderCatalog loaderCatalog = new LoaderCatalog();
    private final DownloadManager downloadManager = new DownloadManager();
    private final AssetSyncer assetSyncer = new AssetSyncer();
    private final NativesExtractor nativesExtractor = new NativesExtractor();
    private final LaunchArgumentBuilder argumentBuilder = new LaunchArgumentBuilder();

    public GameLauncher(Path krovexHome) {
        this.krovexHome = krovexHome;
    }

    private Path librariesDir() {
        return this.krovexHome.resolve("libraries");
    }

    private Path assetsDir() {
        return this.krovexHome.resolve("assets");
    }

    private Path versionsDir() {
        return this.krovexHome.resolve("versions");
    }

    public void launch(Installation installation, MicrosoftAccount account, boolean exitLauncherOnLaunch, Consumer<LaunchEvent> onEvent) {
        launch(installation, account, new LaunchSession(), onEvent);
    }

    public void launch(Installation installation, MicrosoftAccount account, LaunchSession session,
                       Consumer<LaunchEvent> onEvent) {
        try {
            session.begin();
            if (account == null || account.isOffline()) {
                onEvent.accept(new Failed("Sign in with a Microsoft account that owns Minecraft: Java Edition before playing.", null));
                return;
            }
            try {
                account = new com.krovex.launcher.auth.AccountManager(
                        krovexHome, com.krovex.launcher.auth.MicrosoftRegistration.clientId(krovexHome))
                        .ensureFreshToken(account);
            } catch (com.krovex.launcher.auth.MicrosoftAuthFlow.AuthException error) {
                onEvent.accept(new Failed("Could not verify your Minecraft session. " + error.getMessage(), error));
                return;
            }
            var preferences = new com.krovex.launcher.ui.LauncherPreferences(krovexHome);
            onEvent.accept(new Status("Resolving version metadata for " + installation.minecraftVersion() + "..."));
            VersionMetadata metadata = this.cachedMetadata(installation);
            int requiredJava = metadata.requiredJavaMajorVersion();
            JavaRuntimeLocator javaLocator = new JavaRuntimeLocator();
            JavaRuntimeLocator.JavaRuntime runtime = this.resolveJavaRuntime(installation, javaLocator, requiredJava);
            if (runtime == null) {
                onEvent.accept(new Failed(JavaRuntimeLocator.describeIncompatibility(requiredJava, null), null));
                return;
            }
            onEvent.accept(new Status("Checking libraries..."));
            LinkedHashSet<String> features = new LinkedHashSet<String>();
            LibraryResolver libraryResolver = new LibraryResolver(features);
            List<LibraryResolver.ResolvedLibrary> libraries = libraryResolver.resolve(metadata);
            ArrayList<String> classpath = new ArrayList<String>();
            Path nativesDir = installation.gameDirectory().resolve(".krovex-natives-" + metadata.id());
            Files.createDirectories(nativesDir, new FileAttribute[0]);
            Path clientJar = versionsDir().resolve(installation.minecraftVersion())
                    .resolve(installation.minecraftVersion() + ".jar");
            var jobs = new ArrayList<java.util.concurrent.Callable<Void>>();
            if (metadata.clientJarUrl() != null) {
                jobs.add(() -> {
                    downloadManager.downloadCached(metadata.clientJarUrl(), clientJar, metadata.clientJarSha1());
                    return null;
                });
            }
            for (LibraryResolver.ResolvedLibrary library : libraries) {
                Path file = librariesDir().resolve(library.relativePath());
                if (!library.isNative()) classpath.add(file.toString());
                if (library.url() != null) jobs.add(() -> {
                    downloadManager.downloadCached(library.url(), file, library.sha1());
                    return null;
                });
            }
            LaunchDownloads.run(jobs, completed -> onEvent.accept(new Progress("Game files", completed, jobs.size())));
            for (LibraryResolver.ResolvedLibrary library : libraries) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (library.isNative()) nativesExtractor.extract(librariesDir().resolve(library.relativePath()), nativesDir);
            }
            onEvent.accept(new Status("Checking game assets..."));
            assetSyncer.sync(metadata, assetsDir(), (completed, total) ->
                    onEvent.accept(new Progress("Assets", completed, total)));
            LaunchArgumentBuilder.LaunchCommand command = this.argumentBuilder.build(metadata, installation, account, classpath, nativesDir, this.assetsDir(), clientJar);
            ArrayList<String> fullCommand = new ArrayList<String>();
            fullCommand.add(runtime.javaBinary().toString());
            var managedMods = ManagedClientMod.prepare(krovexHome, installation);
            if (!managedMods.isEmpty()) {
                fullCommand.add("-Dfabric.addMods=" + managedMods.stream().map(Path::toString)
                        .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator)));
            }
            fullCommand.addAll(command.fullCommandAfterJavaBinary());
            onEvent.accept(new Status("Launching Minecraft " + installation.minecraftVersion() + "..."));
            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            pb.directory(installation.gameDirectory().toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(this.gameLogFile(installation).toFile()));
            String before = preferences.text("hook.before", "");
            if (!before.isBlank()) {
                onEvent.accept(new Status("Running before-launch hook…"));
                Process hook = startHook(before, installation, "before-launch");
                try {
                    if (hook.waitFor() != 0) throw new IOException("Before-launch hook failed. Check before-launch.log.");
                } finally {
                    if (hook.isAlive()) hook.destroyForcibly();
                }
            }
            long startedAtMs = System.currentTimeMillis();
            Process process = session.start(pb);
            onEvent.accept(new Started(process));
            process.onExit().thenAccept(finished -> {
                long secondsPlayed = Math.max(0L, (System.currentTimeMillis() - startedAtMs) / 1000L);
                String after = preferences.text("hook.after", "");
                if (!after.isBlank()) {
                    try {
                        startHook(after, installation, "after-exit");
                    } catch (IOException | RuntimeException error) {
                        try {
                            Files.writeString(gameLogFile(installation).resolveSibling("after-exit.log"),
                                    "After-exit hook could not start: " + error.getMessage());
                        } catch (IOException ignored) { }
                    }
                }
                onEvent.accept(new Stopped(secondsPlayed, finished.exitValue()));
            });
        }
        catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            onEvent.accept(new Failed(session.isStopping() ? "Launch cancelled." : this.readableError(e), e));
        }
    }

    private Process startHook(String command, Installation installation, String log) throws IOException {
        Path executable = Path.of(command);
        if (!executable.isAbsolute() || !Files.isRegularFile(executable)) {
            throw new IOException("The " + log + " executable does not exist.");
        }
        return new ProcessBuilder(executable.toString()).directory(installation.gameDirectory().toFile())
                .redirectErrorStream(true)
                .redirectOutput(gameLogFile(installation).resolveSibling(log + ".log").toFile())
                .start();
    }

    private Path gameLogFile(Installation installation) throws IOException {
        Path logsDir = installation.gameDirectory().resolve("krovex-launcher-logs");
        Files.createDirectories(logsDir, new FileAttribute[0]);
        return logsDir.resolve("latest.log");
    }

    private VersionMetadata cachedMetadata(Installation installation) throws IOException, InterruptedException {
        Path cached = metadataFile(installation);
        if (Files.isRegularFile(cached)) {
            try {
                var metadata = new VersionMetadata(com.krovex.theme.json.Json.parseObject(Files.readString(cached)));
                if (metadata.mainClass() != null && metadata.clientJarUrl() != null && metadata.id() != null) {
                    return metadata;
                }
            } catch (IOException | RuntimeException ignored) {
                // Replace an unreadable cache entry with fresh version metadata.
            }
        }
        VersionMetadata metadata = resolveMetadata(installation);
        cached = metadataFile(installation);
        Files.createDirectories(cached.getParent());
        Path temporary = Files.createTempFile(cached.getParent(), "version-", ".tmp");
        try {
            Files.writeString(temporary, com.krovex.theme.json.Json.write(metadata.raw(), false));
            try {
                Files.move(temporary, cached, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, cached, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return metadata;
    }

    private Path metadataFile(Installation installation) {
        String key = installation.minecraftVersion() + "|" + installation.loader() + "|" + installation.loaderVersion();
        return versionsDir().resolve("metadata").resolve(
                java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".json");
    }

    private VersionMetadata resolveMetadata(Installation installation) throws IOException, InterruptedException {
        if (!this.loaderCatalog.isLaunchable(installation.loader())) {
            throw new IOException(installation.loader().name() + " instances can be created and configured in Krovex Launcher, but launching them isn't implemented yet -- Forge/NeoForge require driving their own installer jar rather than fetching a ready profile like Fabric/Quilt. See the README's \"Known limitations\" section.");
        }
        if (installation.loader() == Installation.Loader.FABRIC) {
            String loaderVersion = installation.loaderVersion();
            if (loaderVersion == null) {
                List<FabricMetaClient.LoaderVersion> options = this.fabricMetaClient.compatibleLoaderVersions(installation.minecraftVersion());
                if (options.isEmpty()) {
                    throw new IOException("Fabric has no loader build for Minecraft " + installation.minecraftVersion());
                }
                loaderVersion = options.get(0).version();
                installation.setLoaderVersion(loaderVersion);
            }
            return inherit(this.vanillaMetadata(installation.minecraftVersion()), this.fabricMetaClient.fetchLaunchProfile(installation.minecraftVersion(), loaderVersion));
        }
        if (installation.loader() == Installation.Loader.QUILT) {
            String loaderVersion = installation.loaderVersion();
            if (loaderVersion == null) {
                List<QuiltMetaClient.LoaderVersion> options = this.quiltMetaClient.compatibleLoaderVersions(installation.minecraftVersion());
                if (options.isEmpty()) {
                    throw new IOException("Quilt has no loader build for Minecraft " + installation.minecraftVersion());
                }
                loaderVersion = options.get(0).version();
                installation.setLoaderVersion(loaderVersion);
            }
            return inherit(this.vanillaMetadata(installation.minecraftVersion()), this.quiltMetaClient.fetchLaunchProfile(installation.minecraftVersion(), loaderVersion));
        }
        return vanillaMetadata(installation.minecraftVersion());
    }

    private VersionMetadata vanillaMetadata(String minecraftVersion) throws IOException, InterruptedException {
        VersionManifestClient.Manifest manifest = new VersionManifestClient().fetch();
        MinecraftVersion entry = manifest.versions().stream().filter(v -> v.id().equals(minecraftVersion)).findFirst().orElseThrow(() -> new IOException("Unknown Minecraft version: " + minecraftVersion));
        return this.versionMetadataClient.fetch(entry.url());
    }

    static VersionMetadata inherit(VersionMetadata parent, VersionMetadata child) {
        Map<String, Object> merged = new LinkedHashMap<>(parent.raw());
        merged.putAll(child.raw());
        Map<Object, Object> libraries = new LinkedHashMap<>();
        for (VersionMetadata metadata : List.of(parent, child)) {
            for (Object library : metadata.libraries()) {
                if (library instanceof Map<?, ?> entry && entry.get("name") instanceof String name) {
                    String[] coordinates = name.split(":", 4);
                    String key = coordinates.length >= 3 ? coordinates[0] + ":" + coordinates[1] +
                            (coordinates.length == 4 ? ":" + coordinates[3] : "") : name;
                    libraries.put(key, library);
                } else {
                    libraries.put(library, library);
                }
            }
        }
        merged.put("libraries", List.copyOf(libraries.values()));
        if (parent.usesStructuredArguments() || child.usesStructuredArguments()) {
            List<Object> game = new ArrayList<>(parent.gameArguments());
            game.addAll(child.gameArguments());
            List<Object> jvm = new ArrayList<>(parent.jvmArguments());
            jvm.addAll(child.jvmArguments());
            merged.put("arguments", Map.of("game", game, "jvm", jvm));
        }
        return new VersionMetadata(merged);
    }

    private JavaRuntimeLocator.JavaRuntime resolveJavaRuntime(Installation installation, JavaRuntimeLocator locator, int required) {
        if (installation.javaRuntimePath() != null) {
            return locator.probe(installation.javaRuntimePath()).orElse(null);
        }
        return locator.findCompatible(required).orElse(null);
    }

    private String readableError(Exception e) {
        if (e instanceof DownloadManager.DownloadException) {
            return DownloadManager.describeFailure(e);
        }
        if (e instanceof IOException && e.getMessage() != null && e.getMessage().contains("Unknown Minecraft version")) {
            return e.getMessage();
        }
        return "Could not launch Minecraft: " + e.getMessage();
    }

    public record Status(String message) implements LaunchEvent
    {
    }

    public record Failed(String readableMessage, Throwable cause) implements LaunchEvent
    {
    }

    public record Progress(String phase, long completed, long total) implements LaunchEvent
    {
    }

    public record Started(Process process) implements LaunchEvent
    {
    }

    public record Stopped(long secondsPlayed, int exitCode) implements LaunchEvent
    {
    }

    public static sealed interface LaunchEvent
    permits Status, Progress, Failed, Started, Stopped {
    }
}



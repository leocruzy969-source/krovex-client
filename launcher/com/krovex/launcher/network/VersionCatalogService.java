package com.krovex.launcher.network;

import com.krovex.launcher.model.MinecraftVersion;
import com.krovex.launcher.network.VersionManifestClient;
import java.io.IOException;
import java.util.List;

public final class VersionCatalogService {
    private final VersionManifestClient client = new VersionManifestClient();
    private volatile VersionManifestClient.Manifest cached;
    private volatile boolean usingFallback;
    private static final List<MinecraftVersion> FALLBACK_VERSIONS = List.of(new MinecraftVersion("1.21.11", "release", "", "", null), new MinecraftVersion("1.21.4", "release", "", "", null), new MinecraftVersion("1.21.1", "release", "", "", null), new MinecraftVersion("1.20.6", "release", "", "", null), new MinecraftVersion("1.20.1", "release", "", "", null), new MinecraftVersion("1.19.4", "release", "", "", null), new MinecraftVersion("1.18.2", "release", "", "", null), new MinecraftVersion("1.16.5", "release", "", "", null), new MinecraftVersion("1.12.2", "release", "", "", null), new MinecraftVersion("1.8.9", "release", "", "", null));

    public List<MinecraftVersion> knownVersions() {
        return FALLBACK_VERSIONS;
    }

    public synchronized List<MinecraftVersion> fetchOrFallback() {
        if (this.cached != null) {
            return this.cached.versions();
        }
        try {
            this.cached = this.client.fetch();
            this.usingFallback = false;
            return this.cached.versions();
        }
        catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            this.usingFallback = true;
            return FALLBACK_VERSIONS;
        }
    }

    public boolean isUsingFallback() {
        return this.usingFallback;
    }
}



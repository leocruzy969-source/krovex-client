package com.krovex.launcher.launch;

import com.krovex.launcher.download.DownloadManager;
import com.krovex.launcher.network.VersionMetadata;
import com.krovex.theme.json.Json;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

public final class AssetSyncer {
    private final DownloadManager downloads = new DownloadManager();

    public void sync(VersionMetadata metadata, Path assets, BiConsumer<Integer, Integer> progress) throws IOException {
        String id = metadata.assetIndexId();
        if (id == null) return;
        Path index = assets.resolve("indexes").resolve(id + ".json");
        Object indexMetadata = metadata.raw().get("assetIndex");
        String sha1 = indexMetadata instanceof Map<?, ?> info && info.get("sha1") instanceof String sha ? sha : null;
        if (metadata.assetIndexUrl() != null) downloads.downloadCached(metadata.assetIndexUrl(), index, sha1);
        if (!Files.exists(index)) return;
        var jobs = new ArrayList<Callable<Void>>();
        Object objects = Json.parseObject(Files.readString(index)).get("objects");
        if (!(objects instanceof Map<?, ?> entries)) throw new IOException("Invalid Minecraft asset index.");
        var hashes = new java.util.HashSet<String>();
        for (Object value : entries.values()) {
            if (!(value instanceof Map<?, ?> asset) || !(asset.get("hash") instanceof String hash)
                    || !hash.matches("[0-9a-f]{40}")) throw new IOException("Invalid Minecraft asset hash.");
            if (!hashes.add(hash)) continue;
            String directory = hash.substring(0, 2);
            Path destination = assets.resolve("objects").resolve(directory).resolve(hash);
            jobs.add(() -> {
                downloads.downloadCached("https://resources.download.minecraft.net/" + directory + "/" + hash,
                        destination, hash);
                return null;
            });
        }
        try {
            LaunchDownloads.run(jobs, count -> {
                if (progress != null && (count % 24 == 0 || count == jobs.size())) progress.accept(count, jobs.size());
            });
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Asset preparation cancelled.");
        }
    }
}

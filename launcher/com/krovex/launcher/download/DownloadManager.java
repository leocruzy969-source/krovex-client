package com.krovex.launcher.download;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.LongConsumer;

public final class DownloadManager {
    private static final class Network {
        static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10L)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private record Verified(String hash, long size, java.nio.file.attribute.FileTime modified) {}
    private static final java.util.concurrent.ConcurrentMap<Path, Verified> VERIFIED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final Object[] CACHE_LOCKS = new Object[256];
    static { java.util.Arrays.setAll(CACHE_LOCKS, index -> new Object()); }

    public void downloadCached(String url, Path destination, String sha1) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled.");
        Path key = destination.toAbsolutePath().normalize();
        synchronized (CACHE_LOCKS[Math.floorMod(key.hashCode(), CACHE_LOCKS.length)]) {
            if (Files.isRegularFile(key) && Files.size(key) > 0) {
                if (sha1 == null) return;
                if (signature(key, sha1).equals(VERIFIED.get(key))) return;
            }
            download(url, key, sha1, null);
            if (sha1 != null) VERIFIED.put(key, signature(key, sha1));
        }
    }

    private Verified signature(Path file, String hash) throws IOException {
        var attributes = Files.readAttributes(file, java.nio.file.attribute.BasicFileAttributes.class);
        return new Verified(hash, attributes.size(), attributes.lastModifiedTime());
    }

    public void download(String url, Path destination, String expectedSha1, LongConsumer onProgress) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Download cancelled.");
        if (expectedSha1 != null && Files.exists(destination, new LinkOption[0]) && this.sha1Matches(destination, expectedSha1)) {
            return;
        }
        IOException lastError = null;
        for (int attempt = 1; attempt <= 3; ++attempt) {
            try {
                HttpResponse<Path> resp;
                Files.createDirectories(destination.getParent(), new FileAttribute[0]);
                Path tmp = Files.createTempFile(destination.getParent(), ".download-", ".part");
                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "KrovexLauncher/0.1.0").timeout(Duration.ofMinutes(5L)).GET().build();
                    try {
                        resp = Network.CLIENT.send(req, HttpResponse.BodyHandlers.ofFile(tmp, java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING));
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new DownloadException("Interrupted while downloading " + url, e);
                    }
                    if (resp.statusCode() / 100 != 2) {
                        Files.deleteIfExists(tmp);
                        throw new DownloadException("HTTP " + resp.statusCode() + " downloading " + url);
                    }
                    if (onProgress != null) {
                        onProgress.accept(Files.size(tmp));
                    }
                    if (expectedSha1 != null && !this.sha1Matches(tmp, expectedSha1)) {
                        Files.deleteIfExists(tmp);
                        throw new DownloadException("sha1 mismatch for " + url + " (attempt " + attempt + ")");
                    }
                    Files.move(tmp, destination, StandardCopyOption.REPLACE_EXISTING);
                    return;
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
            catch (IOException e) {
                if (Thread.currentThread().isInterrupted()) throw e;
                lastError = e;
                try {
                    Thread.sleep(500L * (long)attempt);
                }
                catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }
        }
        throw new DownloadException("Failed to download " + url + " after 3 attempts: " + (lastError == null ? "unknown error" : lastError.getMessage()), lastError);
    }

    public boolean sha1Matches(Path file, String expectedSha1) {
        if (expectedSha1 == null || expectedSha1.isBlank()) {
            return true;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (var input = new java.security.DigestInputStream(Files.newInputStream(file), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            byte[] hash = digest.digest();
            String actual = HexFormat.of().formatHex(hash);
            return actual.equalsIgnoreCase(expectedSha1);
        }
        catch (IOException | NoSuchAlgorithmException e) {
            return false;
        }
    }

    public static String describeFailure(Exception e) {
        if (e instanceof DownloadException) {
            String msg;
            DownloadException de = (DownloadException)e;
            String string = msg = de.getMessage() == null ? "" : de.getMessage();
            if (msg.contains("sha1 mismatch")) {
                return "A downloaded file didn't match Mojang's checksum. This is usually a flaky network connection -- try Play again. If it keeps happening, check your antivirus/firewall isn't modifying downloaded files.";
            }
            if (msg.contains("HTTP 403") || msg.contains("HTTP 404")) {
                return "Mojang's servers rejected a download for this version. The version may have been delisted, or Mojang's CDN is temporarily unavailable.";
            }
            return "Download failed: " + msg;
        }
        return "Unexpected error: " + e.getMessage();
    }

    public static final class DownloadException
    extends IOException {
        public DownloadException(String message, Throwable cause) {
            super(message, cause);
        }

        public DownloadException(String message) {
            super(message);
        }
    }
}





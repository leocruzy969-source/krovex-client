package com.krovex.boot;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The starter of the installed Krovex Launcher. It is the only program code that Windows ever sees as a new file, so it
 * is tiny and never changes: it starts the real launcher from a .jar, and keeps that .jar up to date.
 *
 *  1. Picks the newest launcher jar whose signature is valid: the one bundled with the installer, or one downloaded earlier.
 *  2. Asks the releases site whether a newer one exists; if so downloads it and checks its signature against the public key
 *     compiled in below (nothing unsigned, wrong-signed, or older than what is running is ever used).
 *  3. Starts com.krovex.launcher.Main from the chosen jar. If an updated jar cannot start, it is thrown away and the
 *     bundled one is used instead.
 *
 * No update, no network, or any failure of the update step simply means the launcher starts as it is.
 */
public final class Boot {
    /** Base64 X.509 RSA public key whose private half signs every update (build-boot.ps1 fills this in). */
    private static final String PUBLIC_KEY = "@PUBLIC_KEY@";
    private static final String BASE = System.getProperty("krovex.update.base", "https://raw.githubusercontent.com/leocruzy969-source/krovex-releases/main/");
    private static final String MAIN_CLASS = "com.krovex.launcher.Main";
    private static final Pattern MOD_ENTRY = Pattern.compile("assets/managed-mods/Krovex-Client-1\\.21\\.11-(\\d+\\.\\d+\\.\\d+)\\.jar");
    private static final Pattern UPDATE_NAME = Pattern.compile("Krovex-Launcher-(\\d+\\.\\d+\\.\\d+)\\.jar");

    private static Path logFile;

    public static void main(String[] args) throws Exception {
        Path updates = updateDir();
        try { Files.createDirectories(updates); } catch (Exception ignored) { }
        logFile = updates.resolve("boot.log");
        Path bundled = bundledJar();
        log("start; bundled=" + bundled);

        Candidate best = new Candidate(bundled, versionOf(bundled), false);
        for (Path file : localUpdates(updates)) {
            Candidate candidate = verifiedLocal(file);
            if (candidate != null && compare(candidate.version, best.version) > 0) best = candidate;
        }
        try {
            Candidate fresh = checkForUpdate(updates, best.version);
            if (fresh != null) best = fresh;
        } catch (Throwable error) {
            log("update check skipped: " + error);
        }

        ClassLoader loader = null;
        Method main = null;
        try {
            loader = loaderFor(best.jar);
            main = loader.loadClass(MAIN_CLASS).getMethod("main", String[].class);
        } catch (Throwable error) {
            log("cannot load " + best.jar + ": " + error);
            if (best.updated) {
                deleteQuietly(best.jar);
                deleteQuietly(sigFor(best.jar));
                best = new Candidate(bundled, versionOf(bundled), false);
                loader = loaderFor(best.jar);
                main = loader.loadClass(MAIN_CLASS).getMethod("main", String[].class);
            } else {
                throw error;
            }
        }
        log("starting launcher " + best.version + " from " + best.jar);
        Thread.currentThread().setContextClassLoader(loader);
        try {
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException error) {
            if (!best.updated) throw error;
            log("updated launcher failed to start, using the bundled one: " + error.getCause());
            deleteQuietly(best.jar);
            deleteQuietly(sigFor(best.jar));
            ClassLoader fallback = loaderFor(bundled);
            Thread.currentThread().setContextClassLoader(fallback);
            fallback.loadClass(MAIN_CLASS).getMethod("main", String[].class).invoke(null, (Object) args);
        }
    }

    // ------------------------------------------------------------------ choosing a jar

    private record Candidate(Path jar, String version, boolean updated) { }

    private static Path updateDir() {
        String override = System.getProperty("krovex.update.dir");
        if (override != null) return Path.of(override);
        String local = System.getenv("LOCALAPPDATA");
        return (local != null ? Path.of(local) : Path.of(System.getProperty("user.home"))).resolve("Krovex").resolve("updates");
    }

    private static Path bundledJar() throws Exception {
        String override = System.getProperty("krovex.bundled");
        if (override != null) return Path.of(override);
        Path self = Path.of(Boot.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return self.resolveSibling("Krovex-Launcher.jar");
    }

    private static List<Path> localUpdates(Path dir) {
        List<Path> found = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dir, "Krovex-Launcher-*.jar")) {
            for (Path file : stream) if (UPDATE_NAME.matcher(file.getFileName().toString()).matches()) found.add(file);
        } catch (Exception ignored) { }
        found.sort(Comparator.comparing((Path file) -> file.getFileName().toString()));
        return found;
    }

    private static Candidate verifiedLocal(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (!signatureValid(bytes, Files.readString(sigFor(file)).strip())) { log("ignoring " + file + ": bad signature"); return null; }
            String version = versionOf(file);
            return version.isEmpty() ? null : new Candidate(file, version, true);
        } catch (Exception error) {
            log("ignoring " + file + ": " + error);
            return null;
        }
    }

    private static Path sigFor(Path jar) { return jar.resolveSibling(jar.getFileName() + ".sig"); }

    /** The launcher's version = the version of the mod jar that is bundled inside it. */
    private static String versionOf(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Matcher matcher = MOD_ENTRY.matcher(entry.getName());
                if (matcher.matches()) return matcher.group(1);
            }
        } catch (Exception ignored) { }
        return "";
    }

    static int compare(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() ? (b.isEmpty() ? 0 : -1) : 1;
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    // ------------------------------------------------------------------ updating

    private static Candidate checkForUpdate(Path updates, String current) throws Exception {
        String versionJson = new String(download(BASE + "version.json", 2500, 4000, 64 * 1024), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("\"launcher\"\\s*:\\s*\"(\\d+\\.\\d+\\.\\d+)\"").matcher(versionJson);
        if (!matcher.find() || compare(matcher.group(1), current) <= 0) { log("up to date (" + current + ")"); return null; }
        log("update " + matcher.group(1) + " available (running " + current + ")");

        Splash splash = Splash.show("Updating Krovex Launcher to " + matcher.group(1) + "...");
        try {
            byte[] jar = download(BASE + "downloads/update/Krovex-Launcher.jar", 4000, 60000, 80L * 1024 * 1024);
            String signature = new String(download(BASE + "downloads/update/Krovex-Launcher.jar.sig", 4000, 8000, 16 * 1024), StandardCharsets.US_ASCII).strip();
            if (!signatureValid(jar, signature)) { log("downloaded update rejected: signature does not match"); return null; }
            Path temp = updates.resolve("download.tmp");
            Files.write(temp, jar);
            String version = versionOf(temp);
            if (version.isEmpty() || compare(version, current) <= 0) { log("downloaded update is not newer (" + version + "): ignored"); deleteQuietly(temp); return null; }
            Path target = updates.resolve("Krovex-Launcher-" + version + ".jar");
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(sigFor(target), signature, StandardCharsets.US_ASCII);
            pruneOld(updates, target);
            log("installed update " + version);
            return new Candidate(target, version, true);
        } finally {
            splash.close();
        }
    }

    private static void pruneOld(Path dir, Path keep) {
        List<Path> all = localUpdates(dir);
        all.sort(Comparator.comparing((Path file) -> versionOf(file), Boot::compare).reversed());
        int kept = 0;
        for (Path file : all) {
            if (file.equals(keep) || kept++ < 1) continue;       // keep the newest two: this one and the one before
            deleteQuietly(file);
            deleteQuietly(sigFor(file));
        }
    }

    private static byte[] download(String address, int connectMs, int readMs, long maxBytes) throws Exception {
        URL url = URI.create(address).toURL();
        var connection = url.openConnection();
        connection.setConnectTimeout(connectMs);
        connection.setReadTimeout(readMs);
        connection.setRequestProperty("User-Agent", "KrovexLauncher-Updater");
        if (connection instanceof HttpURLConnection http && http.getResponseCode() != 200) throw new java.io.IOException("HTTP " + http.getResponseCode() + " for " + address);
        try (InputStream in = connection.getInputStream()) {
            byte[] data = in.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
            if (data.length > maxBytes) throw new java.io.IOException("too large: " + address);
            return data;
        }
    }

    private static boolean signatureValid(byte[] data, String signatureBase64) {
        try {
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update(data);
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception error) {
            return false;
        }
    }

    // ------------------------------------------------------------------ plumbing

    private static ClassLoader loaderFor(Path jar) throws Exception {
        return new URLClassLoader(new URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private static void deleteQuietly(Path file) {
        try { Files.deleteIfExists(file); } catch (Exception ignored) { }
    }

    private static void log(String message) {
        try {
            if (logFile == null) return;
            if (Files.exists(logFile) && Files.size(logFile) > 200_000) Files.delete(logFile);
            Files.writeString(logFile, java.time.LocalDateTime.now() + "  " + message + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    /** A tiny "Updating..." window, only while an update is downloading. */
    private static final class Splash {
        private final javax.swing.JWindow window;

        private Splash(javax.swing.JWindow window) { this.window = window; }

        static Splash show(String text) {
            try {
                if (java.awt.GraphicsEnvironment.isHeadless()) return new Splash(null);
                javax.swing.JWindow window = new javax.swing.JWindow();
                javax.swing.JLabel label = new javax.swing.JLabel(text, javax.swing.SwingConstants.CENTER);
                label.setForeground(new java.awt.Color(0xF2F2F2));
                label.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 14));
                label.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                        javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0x3A3A3A)),
                        javax.swing.BorderFactory.createEmptyBorder(18, 28, 18, 28)));
                window.getContentPane().setBackground(new java.awt.Color(0x16161A));
                window.getContentPane().add(label);
                window.pack();
                window.setLocationRelativeTo(null);
                window.setAlwaysOnTop(true);
                window.setVisible(true);
                return new Splash(window);
            } catch (Throwable error) {
                return new Splash(null);
            }
        }

        void close() {
            try { if (window != null) window.dispose(); } catch (Throwable ignored) { }
        }
    }
}

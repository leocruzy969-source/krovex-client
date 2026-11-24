package com.krovex.client.music;

import com.krovex.client.KrovexConfigPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Reads what the Spotify desktop app is playing straight from Windows' media session (the same
 * source as the volume flyout), so there is nothing to sign in to. A small hidden PowerShell script
 * ({@code krovex-media-session.ps1}) prints one line a second; this class parses it into a
 * {@link SpotifyTrack} and extrapolates the playback position between lines so lyrics stay in sync.
 *
 * Windows only; on anything else (or if PowerShell is missing) it simply never reports a track.
 * Play/pause/next are sent through the same session, so they work for free accounts too.
 */
public final class LocalMediaSession {
    private static final LocalMediaSession INSTANCE = new LocalMediaSession();
    private static final long STALE_AFTER_MS = 5_000, RESTART_DELAY_MS = 20_000;

    private record Snapshot(SpotifyTrack track, long capturedNanos, String coverToken) {}

    private volatile Snapshot snapshot;
    private volatile Process process;
    private volatile long lastStartAttempt;
    private volatile long firstCall;
    private volatile Path script;
    private final java.util.concurrent.atomic.AtomicBoolean starting = new java.util.concurrent.atomic.AtomicBoolean();
    private boolean hookInstalled;

    private LocalMediaSession() {}

    public static LocalMediaSession get() { return INSTANCE; }

    private static boolean windows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    /**
     * Starts the background reader if it isn't running (cheap to call every tick). Spawning PowerShell can take
     * seconds on a busy machine, so it never happens on the game thread, and not while the world is still loading:
     * the first start waits a few seconds after the first call.
     */
    public void ensureRunning() {
        if (!windows()) return;
        Process current = process;
        if (current != null && current.isAlive()) return;
        long now = System.currentTimeMillis();
        if (firstCall == 0) firstCall = now;
        if (now - firstCall < 12_000) return;
        if (now - lastStartAttempt < RESTART_DELAY_MS) return;
        if (!starting.compareAndSet(false, true)) return;
        lastStartAttempt = now;
        Thread starter = new Thread(this::spawn, "krovex-media-start");
        starter.setDaemon(true);
        starter.start();
    }

    private void spawn() {
        try {
            Path file = scriptFile();
            Process started = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-ExecutionPolicy", "Bypass", "-File", file.toString(), "-CoverPath", coverFile().toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process = started;
            Thread reader = new Thread(() -> read(started), "krovex-media-session");
            reader.setDaemon(true);
            reader.start();
            synchronized (this) {
                if (!hookInstalled) {
                    hookInstalled = true;
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        Process p = process;
                        if (p != null) p.destroyForcibly();
                    }, "krovex-media-stop"));
                }
            }
        } catch (IOException | RuntimeException e) {
            process = null; // PowerShell unavailable: auto-detect just stays off
        } finally {
            starting.set(false);
        }
    }
    private Path scriptFile() throws IOException {
        if (script != null && Files.exists(script)) return script;
        Path target = KrovexConfigPaths.configDir().resolve("media-session.ps1");
        Files.createDirectories(target.getParent());
        try (InputStream in = LocalMediaSession.class.getResourceAsStream("/krovex-media-session.ps1")) {
            if (in == null) throw new IOException("media session script missing from the mod jar");
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        script = target;
        return target;
    }

    private void read(Process started) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isEmpty() && line.charAt(0) == '﻿') line = line.substring(1);
                snapshot = parse(line);
            }
        } catch (IOException ignored) {
            // process ended; ensureRunning() restarts it after a delay
        }
    }

    private static Snapshot parse(String line) {
        String[] parts = line.split("\t", 6);
        if (parts.length < 6) return null; // "none": Spotify isn't running or has nothing loaded
        try {
            boolean playing = "1".equals(parts[0]);
            long position = Long.parseLong(parts[1]), duration = Long.parseLong(parts[2]);
            return new Snapshot(new SpotifyTrack(parts[5], parts[4], null, playing, Math.max(0, position), Math.max(0, duration)),
                    System.nanoTime(), parts[3]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The track Spotify is playing (or has paused), with the position brought up to date. */
    private volatile long lastCommandMs;
    private volatile Boolean expectedPlaying;
    private volatile long expectedAtMs;

    /**
     * Play when paused, pause when playing -- sent as an explicit command (not a blind toggle), ignored if pressed again
     * within a moment, and the expected state is shown right away so a slow Spotify update can't flip it back.
     */
    public void togglePlayback() {
        long now = System.currentTimeMillis();
        if (now - lastCommandMs < 450) return;
        lastCommandMs = now;
        boolean playing = current().map(SpotifyTrack::isPlaying).orElse(false);
        expectedPlaying = !playing;
        expectedAtMs = now;
        send(playing ? "pause" : "play");
    }

    /** Next / previous song, with the same double-press guard. */
    public void skip(String command) {
        long now = System.currentTimeMillis();
        if (now - lastCommandMs < 350) return;
        lastCommandMs = now;
        send(command);
    }

    public Optional<SpotifyTrack> current() {
        Snapshot s = snapshot;
        if (s == null) return Optional.empty();
        long ageMs = (System.nanoTime() - s.capturedNanos()) / 1_000_000;
        if (ageMs > STALE_AFTER_MS) return Optional.empty();
        SpotifyTrack t = s.track();
        Boolean expected = expectedPlaying;
        if (expected != null && System.currentTimeMillis() - expectedAtMs < 1800 && expected != t.isPlaying()) {
            t = new SpotifyTrack(t.title(), t.artist(), t.albumArtUrl(), expected, t.progressMs(), t.durationMs());
        }
        if (!t.isPlaying()) return Optional.of(t);
        long position = t.progressMs() + ageMs;
        if (t.durationMs() > 0) position = Math.min(position, t.durationMs());
        return Optional.of(new SpotifyTrack(t.title(), t.artist(), t.albumArtUrl(), true, position, t.durationMs()));
    }

    /** Changes whenever Spotify's cover image for the current song was (re)written to {@link #coverFile()}; empty = none. */
    public String coverToken() {
        Snapshot s = snapshot;
        return s == null ? "" : s.coverToken();
    }

    public static Path coverFile() { return KrovexConfigPaths.configDir().resolve("cover.png"); }

    public boolean available() { return current().isPresent(); }

    /** "toggle", "next" or "previous", sent to the Spotify app (off the calling thread). */
    public void send(String command) {
        if (!windows()) return;
        Thread.ofVirtual().start(() -> {
            try {
                new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                        "-ExecutionPolicy", "Bypass", "-File", scriptFile().toString(), command)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start().waitFor();
            } catch (IOException | InterruptedException ignored) {
                // best effort
            }
        });
    }
}

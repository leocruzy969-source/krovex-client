package com.krovex.client.music;

import com.krovex.theme.json.Json;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds synced lyrics for the playing track by itself (LRCLIB, a free public lyrics database, no account or key).
 * Only the artist, title and length of the playing song are sent. Lookups run off the game thread; results are
 * kept in memory and in the config folder so a song is only ever looked up once.
 */
public final class LyricsFetcher {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Map<String, Optional<LrcLyrics>> RESULTS = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    /** When a song that came back empty may be looked up again (a network error is retried soon, "not found" much later). */
    private static final Map<String, Long> RETRY_AT = new ConcurrentHashMap<>();
    private static volatile String lastStatus = "";

    private LyricsFetcher() {}

    /** Short text for the Music panel: what the last lookup did. */
    public static String status() { return lastStatus; }

    /** What we have for this track right now (empty while loading or when nothing was found). Never blocks. */
    public static Optional<LrcLyrics> lookup(SpotifyTrack track, Path cacheDir) {
        if (track == null || !track.hasTrack()) return Optional.empty();
        String key = ((track.artist() == null ? "" : track.artist()) + "|" + track.title()).toLowerCase(java.util.Locale.ROOT);
        Optional<LrcLyrics> known = RESULTS.get(key);
        if (known != null) {
            Long again = RETRY_AT.get(key);
            if (known.isPresent() || again == null || System.currentTimeMillis() < again) return known;
            RESULTS.remove(key);      // time to try again
            RETRY_AT.remove(key);
        }
        if (LOADING.add(key)) {
            Thread.ofVirtual().start(() -> {
                Optional<LrcLyrics> found = Optional.empty();
                long retryAfterMs = 10 * 60_000L;      // genuinely not found: ask again in 10 minutes
                try {
                    found = load(track, key, cacheDir);
                } catch (Exception e) {
                    retryAfterMs = 20_000L;            // network trouble: try again very soon
                    lastStatus = "Lyrics lookup failed (" + e.getClass().getSimpleName() + "), retrying";
                } finally {
                    if (found.isEmpty()) RETRY_AT.put(key, System.currentTimeMillis() + retryAfterMs);
                    RESULTS.put(key, found);
                    LOADING.remove(key);
                }
            });
        }
        return Optional.empty();
    }

    private static Optional<LrcLyrics> load(SpotifyTrack track, String key, Path cacheDir) throws Exception {
        Path cached = cacheDir.resolve(Integer.toHexString(key.hashCode()) + ".lrc");
        if (Files.isRegularFile(cached)) {
            LrcLyrics parsed = LrcLyrics.loadFromFile(cached);
            if (!parsed.isEmpty()) { lastStatus = "Lyrics loaded for " + track.title(); return Optional.of(parsed); }
        }
        String artist = firstArtist(track.artist());
        String title = track.title();
        String synced = fetchExact(artist, title, track.durationMs());
        if (synced == null) synced = fetchSearch(artist, cleanTitle(title), track.durationMs());
        if (synced == null || synced.isBlank()) { lastStatus = "No synced lyrics found for " + title; return Optional.empty(); }
        LrcLyrics parsed = LrcLyrics.parse(synced);
        if (parsed.isEmpty()) { lastStatus = "No synced lyrics found for " + title; return Optional.empty(); }
        try {
            Files.createDirectories(cacheDir);
            Files.writeString(cached, synced, StandardCharsets.UTF_8);
        } catch (Exception ignored) { /* cache is optional */ }
        lastStatus = "Lyrics loaded for " + title;
        return Optional.of(parsed);
    }

    private static String fetchExact(String artist, String title, long durationMs) throws Exception {
        StringBuilder url = new StringBuilder("https://lrclib.net/api/get?artist_name=").append(enc(artist))
                .append("&track_name=").append(enc(title));
        if (durationMs > 0) url.append("&duration=").append(Math.round(durationMs / 1000.0));
        HttpResponse<String> response = send(url.toString());
        if (response.statusCode() / 100 != 2) return null;
        return syncedOf(Json.parseObject(response.body()));
    }

    @SuppressWarnings("unchecked")
    private static String fetchSearch(String artist, String title, long durationMs) throws Exception {
        HttpResponse<String> response = send("https://lrclib.net/api/search?track_name=" + enc(title)
                + "&artist_name=" + enc(artist));
        if (response.statusCode() / 100 != 2) return null;
        Object parsed = Json.parse(response.body());
        if (!(parsed instanceof List<?> list)) return null;
        String best = null;
        double bestGap = Double.MAX_VALUE;
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            String synced = syncedOf((Map<String, Object>) map);
            if (synced == null || synced.isBlank()) continue;
            Object seconds = map.get("duration");
            double gap = durationMs > 0 && seconds instanceof Number n ? Math.abs(n.doubleValue() - durationMs / 1000.0) : 0;
            if (gap < bestGap) { bestGap = gap; best = synced; }
        }
        return bestGap <= 8 ? best : null; // a wildly different length is a different recording
    }

    private static String syncedOf(Map<String, Object> body) {
        return body.get("syncedLyrics") instanceof String s ? s : null;
    }

    private static HttpResponse<String> send(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "KrovexClient/0.1 (Minecraft mod)")
                .GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String enc(String text) { return URLEncoder.encode(text == null ? "" : text, StandardCharsets.UTF_8); }

    private static String firstArtist(String artist) {
        if (artist == null) return "";
        String first = artist.split("[,;&]")[0].trim();
        return first.isEmpty() ? artist : first;
    }

    /** "Song - Remastered 2011" / "Song (feat. X)" -> "Song". */
    private static String cleanTitle(String title) {
        String t = title.replaceAll("\\s*[\\(\\[].*?[\\)\\]]", "").replaceAll("\\s+-\\s+.*$", "").trim();
        return t.isEmpty() ? title : t;
    }
}
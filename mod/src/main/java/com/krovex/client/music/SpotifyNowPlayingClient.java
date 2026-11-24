package com.krovex.client.music;

import com.krovex.theme.json.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls Spotify's Web API for the currently-playing track and exposes play/pause/next
 * commands for the keybinds in section 3. All of this is plain {@code java.net.http} --
 * no Minecraft dependency -- so the mod's rendering code (which DOES need Minecraft
 * classes) only ever reads an immutable {@link SpotifyTrack} snapshot from here and
 * never touches the network itself, keeping HTTP calls off the render thread as the
 * PERFORMANCE section of the original spec requires everywhere else too.
 */
public final class SpotifyNowPlayingClient {

    private static final String CURRENTLY_PLAYING_ENDPOINT = "https://api.spotify.com/v1/me/player/currently-playing";
    private static final String PLAYER_ENDPOINT = "https://api.spotify.com/v1/me/player";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    /**
     * The last snapshot fetched off the render thread. The HUD overlay (KrovexHudOverlay)
     * only ever reads this cached reference, never calls {@link #fetchCurrentlyPlaying}
     * itself, which is what keeps network I/O off the render thread per the spec's
     * performance requirements ("catalog/downloads/image decoding off render thread"
     * applies just as much to polling now-playing status every frame).
     */
    private final AtomicReference<SpotifyTrack> cachedTrack = new AtomicReference<>();
    private volatile long lastPollMillis = 0;
    private static final long POLL_INTERVAL_MILLIS = 3000;

    /**
     * What is playing right now. The Spotify desktop app (read from Windows' media session, no sign-in,
     * position always fresh) wins; the Web API snapshot is the fallback for when the app isn't visible
     * to Windows, e.g. playing on a phone.
     */
    public Optional<SpotifyTrack> currentTrack() {
        var local = LocalMediaSession.get().current();
        if (local.isPresent()) return local;
        SpotifyTrack t = cachedTrack.get();
        return (t == null || !t.hasTrack()) ? Optional.empty() : Optional.of(t);
    }

    /** Called periodically (e.g. from the client tick handler) with a non-render thread; throttles itself to {@link #POLL_INTERVAL_MILLIS}. */
    public void pollAsync(String accessToken) {
        long now = System.currentTimeMillis();
        if (now - lastPollMillis < POLL_INTERVAL_MILLIS) return;
        lastPollMillis = now;
        Thread.ofVirtual().start(() -> {
            try {
                cachedTrack.set(fetchCurrentlyPlaying(accessToken));
            } catch (IOException | InterruptedException ignored) {
                // Leaves the previous cached snapshot in place rather than flashing "Not playing"
                // on a single transient network hiccup.
            }
        });
    }

    /** Thrown when Spotify says playback control isn't available right now (section 3: "clear status if unavailable"). */
    public static final class PlaybackUnavailableException extends IOException {
        public PlaybackUnavailableException(String message) { super(message); }
    }

    @SuppressWarnings("unchecked")
    public SpotifyTrack fetchCurrentlyPlaying(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(CURRENTLY_PLAYING_ENDPOINT))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 204 || response.body() == null || response.body().isBlank()) {
            return SpotifyTrack.NOTHING_PLAYING; // nothing playing right now -- not an error
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Spotify returned HTTP " + response.statusCode());
        }

        Map<String, Object> body = Json.parseObject(response.body());
        Object itemObj = body.get("item");
        if (itemObj == null) return SpotifyTrack.NOTHING_PLAYING;
        Map<String, Object> item = (Map<String, Object>) itemObj;

        String title = (String) item.get("name");
        List<Object> artists = (List<Object>) item.getOrDefault("artists", List.of());
        String artist = artists.isEmpty() ? "" : String.join(", ", artists.stream()
                .map(a -> (String) ((Map<String, Object>) a).get("name")).toList());

        Map<String, Object> album = (Map<String, Object>) item.get("album");
        String artUrl = null;
        if (album != null) {
            List<Object> images = (List<Object>) album.getOrDefault("images", List.of());
            if (!images.isEmpty()) artUrl = (String) ((Map<String, Object>) images.get(0)).get("url");
        }

        boolean isPlaying = Boolean.TRUE.equals(body.get("is_playing"));
        long progressMs = numberOr(body.get("progress_ms"), 0L);
        long durationMs = numberOr(item.get("duration_ms"), 0L);

        return new SpotifyTrack(title, artist, artUrl, isPlaying, progressMs, durationMs);
    }

    public void playPause(String accessToken, boolean currentlyPlaying) throws IOException, InterruptedException {
        String action = currentlyPlaying ? "pause" : "play";
        HttpRequest request = HttpRequest.newBuilder(URI.create(PLAYER_ENDPOINT + "/" + action))
                .header("Authorization", "Bearer " + accessToken)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        checkControlResponse(response);
    }

    public void nextTrack(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(PLAYER_ENDPOINT + "/next"))
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        checkControlResponse(response);
    }

    public void previousTrack(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(PLAYER_ENDPOINT + "/previous"))
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        checkControlResponse(response);
    }

    private void checkControlResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() == 404) {
            throw new PlaybackUnavailableException("No active Spotify device found -- open Spotify and start "
                    + "playback there first, then the keybind will work.");
        }
        if (response.statusCode() == 403) {
            throw new PlaybackUnavailableException("Spotify Premium is required for playback control "
                    + "(play/pause/next); a free account can still show what's playing.");
        }
        if (response.statusCode() / 100 != 2 && response.statusCode() != 204) {
            throw new IOException("Spotify playback control returned HTTP " + response.statusCode());
        }
    }

    private static long numberOr(Object o, long def) {
        return o instanceof Number n ? n.longValue() : def;
    }
}

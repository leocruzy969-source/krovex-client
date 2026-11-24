package com.krovex.client.music;

import com.krovex.theme.json.Json;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Spotify Authorization Code + PKCE flow (public client, no client secret needed),
 * entirely self-contained within the mod -- see {@link SpotifyPkceUtil}'s javadoc for
 * why this duplicates rather than reuses the launcher's near-identical Microsoft flow.
 *
 * SETUP REQUIRED (documented in the top-level README): using this requires a Spotify
 * Developer Dashboard app (free) so Krovex has its own client ID, with
 * "http://127.0.0.1:<any>/krovex-spotify-callback" style redirect URIs allowed --
 * Spotify's dashboard accepts a loopback address with a wildcard port for exactly
 * this "native app" use case.
 */
public final class SpotifyAuthFlow {

    private static final String AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_ENDPOINT = "https://accounts.spotify.com/api/token";
    private static final String SCOPES = "user-read-currently-playing user-read-playback-state user-modify-playback-state";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public static final class SpotifyAuthException extends Exception {
        public SpotifyAuthException(String message) { super(message); }
        public SpotifyAuthException(String message, Throwable cause) { super(message, cause); }
    }

    public record SpotifySession(String accessToken, long accessTokenExpiresAtMs) {
        public boolean valid() { return System.currentTimeMillis() < accessTokenExpiresAtMs - 15_000; }
    }

    private final String clientId;
    private final SpotifyCredentialStore credentialStore;

    public SpotifyAuthFlow(String spotifyClientId, java.nio.file.Path modConfigDir) {
        this.clientId = spotifyClientId;
        this.credentialStore = new SpotifyCredentialStore(modConfigDir);
    }

    /** Blocking -- opens the system browser and waits for the redirect. Call off the render thread. */
    public SpotifySession connectInteractive() throws SpotifyAuthException {
        try (SpotifyLoopbackRedirectServer redirectServer = new SpotifyLoopbackRedirectServer()) {
            String verifier = SpotifyPkceUtil.newCodeVerifier();
            String challenge = SpotifyPkceUtil.codeChallengeFor(verifier);
            String state = SpotifyPkceUtil.newState();

            String authUrl = AUTHORIZE_ENDPOINT
                    + "?client_id=" + enc(clientId)
                    + "&response_type=code"
                    + "&redirect_uri=" + enc(redirectServer.redirectUri())
                    + "&scope=" + enc(SCOPES)
                    + "&state=" + enc(state)
                    + "&code_challenge_method=S256"
                    + "&code_challenge=" + enc(challenge);

            openSystemBrowser(authUrl);

            Map<String, String> callback = redirectServer.awaitCallback(5, TimeUnit.MINUTES);
            if (callback.containsKey("error")) {
                throw new SpotifyAuthException("Spotify connection was cancelled or failed: "
                        + callback.getOrDefault("error_description", callback.get("error")));
            }
            if (!state.equals(callback.get("state"))) {
                throw new SpotifyAuthException("Spotify's response did not match the request; please try again.");
            }
            String code = callback.get("code");
            if (code == null) throw new SpotifyAuthException("Spotify did not return an authorization code.");

            Map<String, Object> tokenResponse = exchangeCodeForToken(code, verifier, redirectServer.redirectUri());
            return persistAndBuildSession(tokenResponse);
        } catch (SpotifyAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new SpotifyAuthException("Could not connect to Spotify: " + e.getMessage(), e);
        }
    }

    /** True if a previously connected session exists (whether or not its access token is still fresh). */
    private volatile boolean connectedCache;
    private volatile long connectedCheckedAt;

    /** Called every client tick, so the stored (encrypted) token file is only re-read every few seconds. */
    public boolean isConnected() {
        long now = System.nanoTime();
        if (connectedCheckedAt != 0 && now - connectedCheckedAt < 3_000_000_000L) return connectedCache;
        boolean connected;
        try {
            connected = credentialStore.loadRefreshToken() != null;
        } catch (IOException e) {
            connected = false;
        }
        connectedCache = connected;
        connectedCheckedAt = now;
        return connected;
    }

    public void disconnect() throws IOException {
        credentialStore.clear();
        connectedCheckedAt = 0;
    }

    /** Refreshes using the stored refresh token. Blocking. */
    public SpotifySession refresh() throws SpotifyAuthException {
        try {
            String refreshToken = credentialStore.loadRefreshToken();
            if (refreshToken == null) {
                throw new SpotifyAuthException("Not connected to Spotify.");
            }
            Map<String, Object> form = Map.of(
                    "client_id", clientId,
                    "grant_type", "refresh_token",
                    "refresh_token", refreshToken
            );
            Map<String, Object> tokenResponse = postForm(form);
            return persistAndBuildSession(tokenResponse);
        } catch (SpotifyAuthException e) {
            throw e;
        } catch (Exception e) {
            throw new SpotifyAuthException("Could not refresh Spotify session: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> exchangeCodeForToken(String code, String verifier, String redirectUri) throws IOException, InterruptedException {
        Map<String, Object> form = Map.of(
                "client_id", clientId,
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", redirectUri,
                "code_verifier", verifier
        );
        return postForm(form);
    }

    private Map<String, Object> postForm(Map<String, Object> form) throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        for (var e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(enc(e.getKey())).append('=').append(enc(String.valueOf(e.getValue())));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_ENDPOINT))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Spotify token endpoint returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return Json.parseObject(response.body());
    }

    private SpotifySession persistAndBuildSession(Map<String, Object> tokenResponse) throws IOException {
        String accessToken = (String) tokenResponse.get("access_token");
        String refreshToken = (String) tokenResponse.get("refresh_token"); // only present on the FIRST exchange
        long expiresInSeconds = ((Number) tokenResponse.getOrDefault("expires_in", 3600)).longValue();
        if (refreshToken != null) {
            credentialStore.storeRefreshToken(refreshToken);
        }
        return new SpotifySession(accessToken, System.currentTimeMillis() + expiresInSeconds * 1000);
    }

    private void openSystemBrowser(String url) throws SpotifyAuthException {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (IOException ignored) { /* fall through */ }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb = os.contains("win") ? new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url)
                    : os.contains("mac") ? new ProcessBuilder("open", url)
                    : new ProcessBuilder("xdg-open", url);
            pb.start();
        } catch (IOException e) {
            throw new SpotifyAuthException("Could not open your browser automatically. Open this URL manually: " + url, e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}

package com.krovex.launcher.auth;

import com.krovex.launcher.auth.CredentialStore;
import com.krovex.launcher.auth.LoopbackRedirectServer;
import com.krovex.launcher.auth.PkceUtil;
import com.krovex.launcher.model.MicrosoftAccount;
import com.krovex.launcher.network.HttpJson;
import com.krovex.theme.json.Json;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class MicrosoftAuthFlow {
    private static final String AUTHORIZE_ENDPOINT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_ENDPOINT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBOX_AUTH_ENDPOINT = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_AUTHORIZE_ENDPOINT = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_WITH_XBOX_ENDPOINT = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile";
    private final String clientId;
    private final CredentialStore credentialStore;

    public MicrosoftAuthFlow(String azureClientId, CredentialStore credentialStore) {
        this.clientId = azureClientId;
        this.credentialStore = credentialStore;
    }

    public MicrosoftAccount signInInteractive() throws AuthException {
        requireClientId();
        try (LoopbackRedirectServer redirectServer = new LoopbackRedirectServer()) {
            String verifier = PkceUtil.newCodeVerifier();
            String state = PkceUtil.newState();
            openSystemBrowser(authorizationUrl(redirectServer.redirectUri(), state, PkceUtil.codeChallengeFor(verifier)));
            Map<String, String> callback = redirectServer.awaitCallback(5L, TimeUnit.MINUTES);
            if (!state.equals(callback.get("state"))) {
                throw new AuthException("The sign-in response did not match this request. Please try again.");
            }
            if (callback.containsKey("error")) {
                throw new AuthException("Microsoft sign-in failed: " +
                        callback.getOrDefault("error_description", callback.get("error")));
            }
            String code = callback.get("code");
            if (code == null || code.isBlank()) {
                throw new AuthException("Microsoft did not return an authorization code.");
            }
            return completeChainAndPersist(exchangeCodeForToken(code, verifier, redirectServer.redirectUri()));
        } catch (AuthException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AuthException("Microsoft sign-in was cancelled.", error);
        } catch (Exception error) {
            throw new AuthException("Sign-in failed: " + error.getMessage(), error);
        }
    }

    String authorizationUrl(String redirectUri, String state, String challenge) {
        return AUTHORIZE_ENDPOINT + "?client_id=" + enc(clientId)
                + "&response_type=code&response_mode=query&prompt=select_account"
                + "&redirect_uri=" + enc(redirectUri) + "&scope=" + enc("XboxLive.signin offline_access")
                + "&state=" + enc(state) + "&code_challenge=" + enc(challenge) + "&code_challenge_method=S256";
    }

    public boolean isConfigured() {
        return MicrosoftRegistration.valid(clientId);
    }

    private void requireClientId() throws AuthException {
        if (!isConfigured()) throw new AuthException("Microsoft sign-in is unavailable in this build.");
    }

    public MicrosoftAccount refresh(MicrosoftAccount account) throws AuthException {
        requireClientId();
        try {
            String refreshToken = this.credentialStore.loadRefreshToken(account.accountId());
            if (refreshToken == null) {
                throw new AuthException("No stored session for this account; please sign in again.");
            }
            Map<String, Object> form = Map.of("client_id", this.clientId, "grant_type", "refresh_token", "refresh_token", refreshToken, "scope", "XboxLive.signin offline_access");
            Map<String, Object> tokenResponse = this.postForm(TOKEN_ENDPOINT, form);
            return this.completeChainAndPersist(tokenResponse);
        }
        catch (AuthException e) {
            throw e;
        }
        catch (Exception e) {
            throw new AuthException("Could not refresh session: " + e.getMessage(), e);
        }
    }

    private void openSystemBrowser(String url) throws AuthException {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        }
        catch (IOException iOException) {
            // empty catch block
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb = os.contains("win") ? new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url) : (os.contains("mac") ? new ProcessBuilder("open", url) : new ProcessBuilder("xdg-open", url));
            pb.start();
        }
        catch (IOException e) {
            throw new AuthException("Could not open your browser automatically. Open this URL manually: " + url, e);
        }
    }

    private Map<String, Object> exchangeCodeForToken(String code, String verifier, String redirectUri) throws IOException, InterruptedException {
        Map<String, Object> form = Map.of("client_id", this.clientId, "grant_type", "authorization_code", "code", code, "redirect_uri", redirectUri, "code_verifier", verifier, "scope", "XboxLive.signin offline_access");
        return this.postForm(TOKEN_ENDPOINT, form);
    }

    private Map<String, Object> postForm(String url, Map<String, Object> form) throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, Object> e : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(MicrosoftAuthFlow.enc(e.getKey())).append('=').append(MicrosoftAuthFlow.enc(String.valueOf(e.getValue())));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(30)).header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response = HttpJson.rawClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Microsoft could not exchange the sign-in code (HTTP " + response.statusCode() + ").");
        }
        return Json.parseObject(response.body());
    }

    private MicrosoftAccount completeChainAndPersist(Map<String, Object> msTokenResponse) throws AuthException, IOException, InterruptedException {
        Map<String, Object> profile;
        Map<String, Object> xstsResponse;
        String msAccessToken = (String)msTokenResponse.get("access_token");
        String msRefreshToken = (String)msTokenResponse.get("refresh_token");
        if (msAccessToken == null) {
            throw new AuthException("Microsoft did not return an access token.");
        }
        Map<String, Object> xboxRequest = Map.of("Properties", Map.of("AuthMethod", "RPS", "SiteName", "user.auth.xboxlive.com", "RpsTicket", "d=" + msAccessToken), "RelyingParty", "http://auth.xboxlive.com", "TokenType", "JWT");
        Map<String, Object> xboxResponse = HttpJson.postJson(XBOX_AUTH_ENDPOINT, xboxRequest, Map.of());
        String xboxToken = (String)xboxResponse.get("Token");
        String userHash = this.extractUserHash(xboxResponse);
        Map<String, Object> xstsRequest = Map.of("Properties", Map.of("SandboxId", "RETAIL", "UserTokens", List.of(xboxToken)), "RelyingParty", "rp://api.minecraftservices.com/", "TokenType", "JWT");
        try {
            xstsResponse = HttpJson.postJson(XSTS_AUTHORIZE_ENDPOINT, xstsRequest, Map.of());
        }
        catch (HttpJson.HttpException e) {
            throw this.translateXstsError(e);
        }
        String xstsToken = (String)xstsResponse.get("Token");
        Map<String, Object> mcLoginRequest = Map.of("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        Map<String, Object> mcLoginResponse = HttpJson.postJson(MC_LOGIN_WITH_XBOX_ENDPOINT, mcLoginRequest, Map.of());
        String minecraftAccessToken = (String)mcLoginResponse.get("access_token");
        long expiresInSeconds = ((Number)mcLoginResponse.getOrDefault("expires_in", 86400)).longValue();
        try {
            HttpRequest profileRequest = HttpRequest.newBuilder(URI.create(MC_PROFILE_ENDPOINT)).header("Authorization", "Bearer " + minecraftAccessToken).GET().build();
            HttpResponse<String> profileResponse = HttpJson.rawClient().send(profileRequest, HttpResponse.BodyHandlers.ofString());
            if (profileResponse.statusCode() == 404) {
                throw new AuthException("This Microsoft account does not own Minecraft: Java Edition. Sign in with the account you purchased the game on, or buy it at minecraft.net.");
            }
            if (profileResponse.statusCode() / 100 != 2) {
                throw new AuthException("Could not verify game ownership (HTTP " + profileResponse.statusCode() + ").");
            }
            profile = Json.parseObject(profileResponse.body());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException("Sign-in was interrupted.", e);
        }
        String uuid = (String)profile.get("id");
        String username = (String)profile.get("name");
        String accountId = uuid != null ? uuid : UUID.randomUUID().toString();
        MicrosoftAccount account = new MicrosoftAccount(accountId);
        account.setMinecraftUuid(uuid);
        account.setMinecraftUsername(username);
        account.setSkinUrl(this.extractSkinUrl(profile));
        account.setMinecraftAccessToken(minecraftAccessToken, System.currentTimeMillis() + expiresInSeconds * 1000L);
        if (msRefreshToken != null) {
            this.credentialStore.storeRefreshToken(accountId, msRefreshToken);
        }
        return account;
    }

    private String extractUserHash(Map<String, Object> xboxResponse) throws AuthException {
        Map displayClaims = (Map)xboxResponse.get("DisplayClaims");
        if (displayClaims == null) {
            throw new AuthException("Unexpected Xbox Live response (missing DisplayClaims).");
        }
        List xuiList = (List)displayClaims.get("xui");
        if (xuiList == null || xuiList.isEmpty()) {
            throw new AuthException("Unexpected Xbox Live response (missing xui).");
        }
        Map xui = (Map)xuiList.get(0);
        return (String)xui.get("uhs");
    }

    private String extractSkinUrl(Map<String, Object> profile) {
        List skins = (List)profile.get("skins");
        if (skins == null || skins.isEmpty()) {
            return null;
        }
        Map active = (Map)skins.get(0);
        return (String)active.get("url");
    }

    private AuthException translateXstsError(HttpJson.HttpException e) {
        String body = e.getMessage();
        if (body != null && body.contains("2148916233")) {
            return new AuthException("This Microsoft account has no Xbox profile. Create one at xbox.com, then try signing in again.");
        }
        if (body != null && body.contains("2148916238")) {
            return new AuthException("This account is a child account that needs to be added to a Microsoft family group before it can sign in to Minecraft.");
        }
        return new AuthException("Xbox Live rejected this sign-in: " + body);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static final class AuthException
    extends Exception {
        public AuthException(String message) {
            super(message);
        }

        public AuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}


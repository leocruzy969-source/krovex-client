package com.krovex.client.music;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * RFC 7636 PKCE helpers, duplicated (not shared) from the launcher's identical
 * {@code com.krovex.launcher.auth.PkceUtil}. Deliberate duplication: the mod must
 * work with the Krovex Launcher completely uninstalled (DESKTOP spec: "without
 * installing the Krovex Launcher or signing into a separate Krovex account"), so it
 * cannot depend on the launcher module's classes at all.
 */
final class SpotifyPkceUtil {
    private SpotifyPkceUtil() {}

    static String newCodeVerifier() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String codeChallengeFor(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String newState() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

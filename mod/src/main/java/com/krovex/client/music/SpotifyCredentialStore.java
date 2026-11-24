package com.krovex.client.music;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Set;

/**
 * Stores the Spotify refresh token encrypted at rest under the mod's own config
 * directory (section 3: "Spotify account access stays local and is never included
 * in a modpack" -- so this file lives outside any instance's game directory that a
 * modpack export could ever touch). Same AES-256-GCM-at-rest approach as the
 * launcher's {@code CredentialStore}, independently implemented for the same
 * standalone-mod reason given in {@link SpotifyPkceUtil}.
 */
final class SpotifyCredentialStore {

    private final Path file;
    private final Path keyFile;

    SpotifyCredentialStore(Path modConfigDir) {
        this.file = modConfigDir.resolve("spotify-session.bin");
        this.keyFile = modConfigDir.resolve(".spotify-key");
    }

    void storeRefreshToken(String refreshToken) throws IOException {
        SecretKey key = loadOrCreateKey();
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            Files.createDirectories(file.getParent());
            try (OutputStream raw = Files.newOutputStream(file)) {
                raw.write(iv);
                try (CipherOutputStream cos = new CipherOutputStream(raw, cipher)) {
                    cos.write(refreshToken.getBytes(StandardCharsets.UTF_8));
                }
            }
            restrictPermissions(file);
        } catch (Exception e) {
            throw new IOException("Could not store Spotify session", e);
        }
    }

    String loadRefreshToken() throws IOException {
        if (!Files.exists(file)) return null;
        SecretKey key = loadOrCreateKey();
        try (InputStream raw = Files.newInputStream(file)) {
            byte[] iv = raw.readNBytes(12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            try (CipherInputStream cis = new CipherInputStream(raw, cipher)) {
                return new String(cis.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new IOException("Could not read Spotify session", e);
        }
    }

    void clear() throws IOException {
        Files.deleteIfExists(file);
    }

    private SecretKey loadOrCreateKey() throws IOException {
        if (Files.exists(keyFile)) {
            return new SecretKeySpec(Files.readAllBytes(keyFile), "AES");
        }
        try {
            Files.createDirectories(keyFile.getParent());
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey key = keyGen.generateKey();
            Files.write(keyFile, key.getEncoded());
            restrictPermissions(keyFile);
            return key;
        } catch (Exception e) {
            throw new IOException("Could not create Spotify credential key", e);
        }
    }

    private void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Not POSIX (Windows) -- relies on the user profile directory's own ACLs.
        }
    }
}

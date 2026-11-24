package com.krovex.launcher.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MicrosoftRegistration {
    private static final String RESOURCE = "/krovex-release/microsoft-client-id.txt";
    private MicrosoftRegistration() {}

    public static boolean valid(String id) {
        return id != null && id.matches("(?i)[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
                && !id.equals("00000000-0000-0000-0000-000000000000");
    }

    public static String clientId(Path home) {
        try (var resource = MicrosoftRegistration.class.getResourceAsStream(RESOURCE)) {
            if (resource != null) {
                String bundled = new String(resource.readNBytes(128), StandardCharsets.UTF_8).strip();
                return valid(bundled) ? bundled : "";
            }
        } catch (IOException error) {
            return "";
        }
        // Development overrides apply only to builds without a publisher registration.
        String developerId = System.getenv("KROVEX_MICROSOFT_CLIENT_ID");
        if (developerId != null && valid(developerId.strip())) return developerId.strip();
        try {
            String local = Files.readString(home.resolve("microsoft-client-id.txt")).strip();
            if (valid(local)) return local;
        } catch (IOException ignored) { }
        return "";
    }
}

package com.krovex.client.music;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;

/** The song cover Spotify reports (via the Windows media session), as a texture. Render thread only. */
public final class CoverArt {
    public static final Identifier ID = Identifier.of("krovex-client", "now_playing_cover");
    /** The cover image is this many pixels square. */
    public static final int SIZE = 64;
    private static String loadedToken = "";
    private static boolean has;

    private CoverArt() {}

    /** The texture id of the current cover, or null when Spotify has none. */
    public static Identifier current() {
        String token = LocalMediaSession.get().coverToken();
        if (token.isEmpty()) return has ? ID : null;
        if (!token.equals(loadedToken)) {
            loadedToken = token;
            try (InputStream in = Files.newInputStream(LocalMediaSession.coverFile())) {
                NativeImage image = NativeImage.read(in);
                var textures = MinecraftClient.getInstance().getTextureManager();
                textures.destroyTexture(ID);
                textures.registerTexture(ID, new NativeImageBackedTexture(() -> "krovex_cover", image));
                has = true;
            } catch (Exception e) {
                has = false;
            }
        }
        return has ? ID : null;
    }
}
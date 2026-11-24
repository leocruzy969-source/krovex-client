package com.krovex.client.gui;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public final class MenuIcons {
    @FunctionalInterface public interface ImageSource { NativeImage read() throws IOException; }

    private final String group;
    private final Map<String, Optional<Identifier>> textures = new HashMap<>();

    public MenuIcons(String group) { this.group = group; }

    public static NativeImage read(InputStream stream) throws IOException {
        try (stream) { return NativeImage.read(stream); }
    }

    public void draw(DrawContext c, String key, ImageSource source, int x, int y, int size) {
        Optional<Identifier> texture = textures.computeIfAbsent(key, ignored -> load(source));
        if (texture.isPresent()) {
            c.drawTexture(RenderPipelines.GUI_TEXTURED, texture.get(), x, y, 0, 0, size, size, size, size);
        } else {
            letterTile(c, key, x, y, size);
        }
    }

    /** Placeholder for an item with no icon of its own: its first letter on a gradient tile, colour chosen from its name. */
    private static void letterTile(DrawContext c, String key, int x, int y, int size) {
        int hash = key.hashCode();
        float hue = (hash & 0x7FFFFFFF) % 360 / 360f;
        int base = 0xFF000000 | java.awt.Color.HSBtoRGB(hue, .55f, .62f);
        MenuStyle.gradientCard(c, x, y, size, size, 3, MenuStyle.withAlpha(MenuStyle.shade(base, 1.5f), 0xAA),
                MenuStyle.shade(base, 1.2f), MenuStyle.shade(base, .7f));
        String letter = "?";
        for (char ch : key.toCharArray()) if (Character.isLetterOrDigit(ch)) { letter = String.valueOf(Character.toUpperCase(ch)); break; }
        var font = MinecraftClient.getInstance().textRenderer;
        var text = MenuStyle.lettering(net.minecraft.text.Text.literal(letter), true);
        c.drawText(font, text, x + (size - font.getWidth(text)) / 2, y + (size - 8) / 2, 0xFFFFFFFF, false);
    }

    private Optional<Identifier> load(ImageSource source) {
        try {
            NativeImage image = source.read();
            if (image == null) return Optional.empty();
            var name = Identifier.of("krovex-client", "menu/" + group + "/" + textures.size());
            MinecraftClient.getInstance().getTextureManager().registerTexture(name,
                    new NativeImageBackedTexture(() -> group, image));
            return Optional.of(name);
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public void clear() {
        var manager = MinecraftClient.getInstance().getTextureManager();
        for (var texture : textures.values()) texture.ifPresent(manager::destroyTexture);
        textures.clear();
    }
}

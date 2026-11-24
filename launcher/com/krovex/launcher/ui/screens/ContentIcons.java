package com.krovex.launcher.ui.screens;

import com.krovex.launcher.ui.LauncherStyle;
import com.krovex.theme.json.Json;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.swing.*;

final class ContentIcons {
    static final int SIZE = 44;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Icon PLACEHOLDER = new ImageIcon(placeholder());
    private static final LinkedHashMap<Object, Icon> CACHE = new LinkedHashMap<>(128, .75f, true);
    private static final Map<Object, java.util.Set<JComponent>> PENDING = new java.util.HashMap<>();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(3,
            Thread.ofPlatform().name("krovex-content-icon-", 0).daemon().factory());

    static {
        try {
            var provider = (javax.imageio.spi.ImageReaderSpi) Class
                    .forName("com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi")
                    .getConstructor().newInstance();
            javax.imageio.spi.IIORegistry.getDefaultInstance().registerServiceProvider(provider);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Other formats remain available when running a source-only build.
        }
    }

    private ContentIcons() {}

    static Icon remote(String url, JComponent view) {
        if (url == null || url.isBlank()) return PLACEHOLDER;
        return request(url, () -> download(url), view);
    }

    static Icon local(Path content, JComponent view) {
        if (content == null) return PLACEHOLDER;
        return request(content, () -> readLocal(content), view);
    }

    private static Icon request(Object key, Supplier<BufferedImage> load, JComponent view) {
        Icon icon = CACHE.get(key);
        if (icon != null) return icon;
        var waiting = PENDING.get(key);
        if (waiting != null) {
            waiting.add(view);
            return PLACEHOLDER;
        }
        PENDING.put(key, new java.util.HashSet<>(java.util.Set.of(view)));
        WORKERS.execute(() -> {
            BufferedImage image = load.get();
            Icon result = image == null ? PLACEHOLDER : new ImageIcon(new java.awt.image.BaseMultiResolutionImage(
                    scale(image, SIZE), scale(image, SIZE * 2)));
            SwingUtilities.invokeLater(() -> {
                var subscribers = PENDING.remove(key);
                CACHE.put(key, result);
                if (CACHE.size() > 400) CACHE.pollFirstEntry();
                subscribers.forEach(JComponent::repaint);
            });
        });
        return PLACEHOLDER;
    }

    static void refreshLocal(Path folder) {
        CACHE.keySet().removeIf(key -> key instanceof Path path && path.startsWith(folder));
    }

    static void remember(Path content, String url) {
        if (url == null || url.isBlank()) return;
        BufferedImage image = download(url);
        if (image == null) return;
        Path icon = sidecar(content);
        try {
            Files.createDirectories(icon.getParent());
            ImageIO.write(scale(image, SIZE * 2), "png", icon.toFile());
        } catch (IOException ignored) {
            // The installed file remains usable if its icon could not be cached.
        }
    }

    private static Path sidecar(Path content) {
        String name = content.getFileName().toString();
        if (name.endsWith(".disabled")) name = name.substring(0, name.length() - ".disabled".length());
        return content.resolveSibling(".krovex-icons").resolve(name + ".png");
    }

    private static BufferedImage readLocal(Path content) {
        try {
            BufferedImage fallback = null;
            Path saved = sidecar(content);
            if (Files.isRegularFile(saved)) {
                try (InputStream input = Files.newInputStream(saved)) {
                    BufferedImage cached = decode(input);
                    if (cached != null && cached.getWidth() >= SIZE * 2) return cached;
                    fallback = cached;
                }
            }
            if (Files.isDirectory(content)) {
                Path packIcon = content.resolve("pack.png");
                if (Files.isRegularFile(packIcon)) {
                    try (InputStream input = Files.newInputStream(packIcon)) { return decode(input); }
                }
                return fallback;
            }
            try (ZipFile zip = new ZipFile(content.toFile())) {
                Object icon = metadataIcon(zip);
                if (icon instanceof Map<?, ?> bySize) {
                    icon = bySize.entrySet().stream().filter(e -> e.getValue() instanceof String)
                            .min(java.util.Comparator.comparingInt(e -> sizeDistance(e.getKey())))
                            .map(Map.Entry::getValue).orElse(null);
                }
                if (icon instanceof String path) {
                    var entry = zip.getEntry(path);
                    if (entry != null && entry.getSize() <= MAX_BYTES) {
                        try (InputStream input = zip.getInputStream(entry)) {
                            BufferedImage image = decode(input);
                            if (image != null) return image;
                        }
                    }
                }
                var packIcon = zip.getEntry("pack.png");
                if (packIcon != null && packIcon.getSize() <= MAX_BYTES) {
                    try (InputStream input = zip.getInputStream(packIcon)) {
                        var image = decode(input);
                        if (image != null) return image;
                    }
                }
            }
            return fallback;
        } catch (IOException | RuntimeException ignored) {
            // A missing or unsupported icon uses the placeholder.
        }
        return null;
    }

    private static int sizeDistance(Object key) {
        try { return Math.abs(Integer.parseInt(String.valueOf(key)) - SIZE * 2); }
        catch (NumberFormatException ignored) { return Integer.MAX_VALUE; }
    }

    private static Object metadataIcon(ZipFile zip) throws IOException {
        for (String path : new String[]{"fabric.mod.json", "quilt.mod.json",
                "META-INF/neoforge.mods.toml", "META-INF/mods.toml"}) {
            var entry = zip.getEntry(path);
            if (entry == null || entry.getSize() > MAX_BYTES) continue;
            String text;
            try (InputStream input = zip.getInputStream(entry)) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) continue;
                text = new String(bytes, StandardCharsets.UTF_8);
            }
            if (path.endsWith(".toml")) {
                var match = java.util.regex.Pattern.compile("(?m)^\\s*logoFile\\s*=\\s*[\"']([^\"']+)[\"']").matcher(text);
                if (match.find()) return match.group(1);
            } else {
                try {
                    Map<String, Object> json = Json.parseObject(text);
                    if (json.get("icon") != null) return json.get("icon");
                    if (json.get("quilt_loader") instanceof Map<?, ?> loader
                            && loader.get("metadata") instanceof Map<?, ?> metadata
                            && metadata.get("icon") != null) return metadata.get("icon");
                } catch (RuntimeException ignored) { }
            }
        }
        return null;
    }

    static void loadPreview(String url, JLabel label) {
        WORKERS.execute(() -> {
            BufferedImage image = download(url);
            SwingUtilities.invokeLater(() -> {
                if (image == null) {
                    label.setText("Preview unavailable");
                    return;
                }
                double ratio = Math.min(1.0, Math.min(410.0 / image.getWidth(), 240.0 / image.getHeight()));
                int width = Math.max(1, (int) (image.getWidth() * ratio));
                int height = Math.max(1, (int) (image.getHeight() * ratio));
                label.setIcon(new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH)));
                label.setText("");
            });
        });
    }

    static boolean canLoadRemote(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null && java.util.Set.of("cdn.modrinth.com",
                    "media.forgecdn.net", "mediafilez.forgecdn.net").contains(host.toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static BufferedImage download(String url) {
        try {
            if (!canLoadRemote(url)) return null;
            URI uri = URI.create(url);
            var connection = (javax.net.ssl.HttpsURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            try (InputStream input = connection.getInputStream()) {
                return decode(input);
            } finally {
                connection.disconnect();
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static BufferedImage decode(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) return null;
        try (var data = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(data);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(data);
                if (reader.getWidth(0) > 2048 || reader.getHeight(0) > 2048) return null;
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage placeholder() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(LauncherStyle.EDGE);
        graphics.fillRoundRect(0, 0, SIZE, SIZE, 10, 10);
        graphics.setColor(LauncherStyle.MUTED);
        graphics.drawRoundRect(12, 10, 13, 17, 3, 3);
        graphics.drawLine(15, 16, 22, 16);
        graphics.drawLine(15, 20, 22, 20);
        graphics.dispose();
        return image;
    }

    private static BufferedImage scale(BufferedImage original, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, Math.max(original.getWidth(), original.getHeight()) <= 32
                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double factor = Math.min((double) size / original.getWidth(), (double) size / original.getHeight());
        int width = Math.max(1, (int) Math.round(original.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(original.getHeight() * factor));
        graphics.drawImage(original, (size - width) / 2, (size - height) / 2, width, height, null);
        graphics.dispose();
        return image;
    }
}

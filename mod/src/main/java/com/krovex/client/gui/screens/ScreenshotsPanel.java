package com.krovex.client.gui.screens;

import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public final class ScreenshotsPanel implements KrovexSubPanel {
    private record Preview(Path file, Identifier texture, int width, int height) {}
    private final MinecraftClient game = MinecraftClient.getInstance();
    private final Path folder = game.runDirectory.toPath().resolve("screenshots");
    private final List<Preview> previews = new ArrayList<>();
    private List<Path> images = List.of();
    private int page;
    private Path album;
    private List<Path> albums = List.of();
    private boolean showAlbums;
    private long totalBytes;
    private static int captureTicks;

    static {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (captureTicks <= 0 || --captureTicks > 0 || client.world == null || client.currentScreen != null) return;
            net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(),
                    message -> client.execute(() -> client.inGameHud.getChatHud().addMessage(message)));
        });
    }
    private boolean newest = true;
    private boolean grid = true;
    private final DropList sortList = new DropList(), viewList = new DropList();
    /** True while the wheel is scrolling the gallery: thumbnails already loaded are kept instead of read from disk again. */
    private boolean scrolling;
    private int lastX, lastY, lastW, lastH, lastColumns = 3, lastRows = 2;

    @Override public boolean mouseClicked(double mx, double my, int button) {
        return sortList.mouseClicked(mx, my, button) || viewList.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        if (sortList.mouseScrolled(mx, my, vertical) || viewList.mouseScrolled(mx, my, vertical)) return true;
        if (showAlbums || mx < lastX || mx >= lastX + lastW || my < lastY + 22 || my >= lastY + lastH) return false;
        int totalRows = (images.size() + lastColumns - 1) / lastColumns;
        int next = Math.clamp(page - (int) Math.signum(vertical), 0, Math.max(0, totalRows - lastRows));
        if (next != page && game.currentScreen instanceof KrovexMenuScreen menu) {
            page = next;
            scrolling = true;
            try { menu.refreshPanel(); } finally { scrolling = false; }
        }
        return true;
    }

    @Override public void renderOverlay(DrawContext c, int mx, int my) {
        sortList.renderOverlay(c, mx, my);
        viewList.renderOverlay(c, mx, my);
    }

    @Override public void onShown() {
        if (scrolling) return;
        try (var folders = Files.list(folder)) {
            albums = folders.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) { albums = List.of(); }
        if (album != null && !albums.contains(album)) album = null;
        try (var files = Files.list(album == null ? folder : album)) {
            Comparator<Path> order = Comparator.comparing(this::modified);
            images = files.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                    .sorted(newest ? order.reversed() : order).toList();
        } catch (IOException e) {
            images = List.of();
        }
        totalBytes = images.stream().mapToLong(ScreenshotsPanel::fileSize).sum();
    }

    private static long fileSize(Path file) {
        try { return Files.size(file); } catch (IOException e) { return 0; }
    }

    private static String size(long value) {
        return String.format(java.util.Locale.ROOT, "%.1f MB", value / 1048576.0);
    }

    private long modified(Path file) {
        try { return Files.getLastModifiedTime(file).toMillis(); }
        catch (IOException e) { return 0; }
    }

    @Override public void createWidgets(Screen host, int x, int y, int w, int h) {
        var menu = (KrovexMenuScreen) host;
        int columns = grid ? 3 : 1;
        int rows = h >= 133 ? 2 : 1;
        int count = rows * columns, tileW = (w - 7 * (columns - 1)) / columns;
        int tileH = Math.max(35, (h - 51) / rows);
        lastX = x; lastY = y; lastW = w; lastH = h; lastColumns = columns; lastRows = rows;
        page = Math.clamp(page, 0, Math.max(0, (images.size() + columns - 1) / columns - rows));      // first visible row of tiles
        menu.addPanelWidget(MenuStyle.button("▧  Gallery", x + 3, y, 57, 19, showAlbums ? MenuStyle.CARD : MenuStyle.PURPLE,
                () -> { showAlbums = false; album = null; page = 0; menu.refreshPanel(); }));
        menu.addPanelWidget(MenuStyle.button("▱  Albums", x + 63, y, 59, 19, showAlbums ? MenuStyle.PURPLE : MenuStyle.CARD,
                () -> { showAlbums = true; menu.refreshPanel(); }));
        if (showAlbums) {
            int countAlbums = Math.min(albums.size(), Math.max(0, (h - 43) / 20));
            for (int n = 0; n < countAlbums; n++) {
                Path selected = albums.get(n);
                menu.addPanelWidget(MenuStyle.button(selected.getFileName().toString(), x + 6, y + 26 + n * 20,
                        Math.min(180, w - 12), 18, MenuStyle.CARD,
                        () -> { album = selected; page = 0; showAlbums = false; menu.refreshPanel(); }));
            }
            return;
        }
        sortList.button(menu, "Sort:  ", x + w - 190, y, 112, 18,
                List.of(new DropList.Item("Newest first"), new DropList.Item("Oldest first")), newest ? 0 : 1,
                index -> { newest = index == 0; page = 0; menu.refreshPanel(); });
        viewList.button(menu, "View:  ", x + w - 74, y, 74, 18,
                List.of(new DropList.Item("Grid"), new DropList.Item("List")), grid ? 0 : 1,
                index -> { grid = index == 0; page = 0; menu.refreshPanel(); });
        menu.addPanelWidget(MenuStyle.button("Open folder", x + w - 190, y + h - 20, 83, 20, MenuStyle.CARD, () -> {
            try { Files.createDirectories(folder); Util.getOperatingSystem().open(folder); }
            catch (IOException e) { game.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal("Could not open screenshots folder.")); }
        }));
        menu.addPanelWidget(MenuStyle.button("Take Screenshot", x + w - 102, y + h - 20, 102, 20, MenuStyle.LIME,
                () -> { captureTicks = 3; menu.close(); })).active = game.world != null;
        // keep the thumbnails that are still on screen, load only the new ones, drop the rest
        var old = new ArrayList<>(previews);
        previews.clear();
        for (int slot = 0; slot < count && page * columns + slot < images.size(); slot++) {
            Path file = images.get(page * columns + slot);
            Preview kept = null;
            for (Preview candidate : old) if (candidate.file().equals(file)) kept = candidate;
            if (kept != null) old.remove(kept); else kept = load(file, slot);
            previews.add(kept);
            int left = x + slot % columns * (tileW + 7);
            int top = y + 25 + slot / columns * tileH;
            menu.addPanelWidget(MenuStyle.button("⋯", left + tileW - 23, top + tileH - 19, 18, 14,
                    MenuStyle.CARD, () -> Util.getOperatingSystem().open(file)));
        }
        for (Preview gone : old) if (gone.texture() != null) game.getTextureManager().destroyTexture(gone.texture());
    }

    private Preview load(Path file, int slot) {
        try (var stream = Files.newInputStream(file); var full = NativeImage.read(stream)) {
            int width = full.getWidth(), height = full.getHeight();
            var thumb = new NativeImage(240, Math.max(1, Math.min(240, 240 * height / width)), false);
            full.resizeSubRectTo(0, 0, width, height, thumb);
            Identifier id = Identifier.of("krovex-client", "gallery/" + Integer.toHexString(file.toString().hashCode()));
            game.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "Krovex screenshot", thumb));
            return new Preview(file, id, width, height);
        } catch (IOException e) {
            return new Preview(file, null, 0, 0);
        }
    }

    @Override public void onHidden() {
        if (!scrolling) clearPreviews();
    }

    private void clearPreviews() {
        for (Preview preview : previews) if (preview.texture() != null) game.getTextureManager().destroyTexture(preview.texture());
        previews.clear();
    }

    @Override public void render(DrawContext c, int x, int y, int w, int h, int mx, int my, float delta) {
        MenuStyle.card(c, x, y + 22, w, Math.max(1, h - 47), 9, MenuStyle.EDGE, MenuStyle.PANEL);
        if (showAlbums) {
            if (albums.isEmpty()) MenuStyle.text(c, "No albums yet", x + 12, y + 37, MenuStyle.MUTED, 1);
            return;
        }
        int columns = grid ? 3 : 1;
        int rows = h >= 133 ? 2 : 1;
        int tileW = (w - 7 * (columns - 1)) / columns;
        int tileH = Math.max(35, (h - 51) / rows);
        for (int slot = 0; slot < previews.size(); slot++) {
            Preview preview = previews.get(slot);
            int left = x + slot % columns * (tileW + 7);
            int top = y + 25 + slot / columns * tileH;
            MenuStyle.card(c, left, top, tileW, tileH - 5, 5, MenuStyle.EDGE, MenuStyle.CARD);
            if (preview.texture() != null) {
                int imageWidth = tileW - 4, imageHeight = Math.max(1, tileH - 25);
                int sourceWidth = 240, sourceHeight = Math.max(1, Math.min(240, 240 * preview.height() / preview.width()));
                float scale = Math.max(imageWidth / (float) sourceWidth, imageHeight / (float) sourceHeight);
                int cropW = Math.min(sourceWidth, Math.max(1, Math.round(imageWidth / scale)));
                int cropH = Math.min(sourceHeight, Math.max(1, Math.round(imageHeight / scale)));
                c.drawTexture(RenderPipelines.GUI_TEXTURED, preview.texture(), left + 2, top + 2,
                        (sourceWidth - cropW) / 2f, (sourceHeight - cropH) / 2f,
                        imageWidth, imageHeight, cropW, cropH, sourceWidth, sourceHeight);
            }
            String name = game.textRenderer.trimToWidth(preview.file().getFileName().toString(), Math.max(1, tileW - 43));
            MenuStyle.text(c, name, left + 4, top + tileH - 22, MenuStyle.WHITE, .82f);
            if (tileW >= 88 && preview.width() > 0) MenuStyle.text(c, preview.width() + " × " + preview.height() + " · " + size(fileSize(preview.file())),
                    left + 4, top + tileH - 12, MenuStyle.MUTED, .74f);
        }
        MenuStyle.scrollBar(c, x + w - 4, y + 26, Math.max(12, h - 55), page, rows, (images.size() + columns - 1) / columns);
        if (images.isEmpty() && !showAlbums) {
            int cx = x + w / 2, cy = y + 22 + Math.max(1, h - 47) / 2;
            MenuStyle.gradientCard(c, cx - 22, cy - 40, 44, 44, 10, 0xFFC17AFE, 0xFF4A2A8C, 0xFF21103F);
            MenuStyle.navIcon(c, "shots", cx - 7, cy - 27, MenuStyle.LIME);
            var big = MenuStyle.lettering(net.minecraft.text.Text.literal("No screenshots yet"), true);
            var font = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            c.drawText(font, big, cx - font.getWidth(big) / 2, cy + 12, MenuStyle.WHITE, false);
            var hint = MenuStyle.lettering(net.minecraft.text.Text.literal("Press F2 in game, or use Take Screenshot below."), false);
            c.drawText(font, hint, cx - font.getWidth(hint) / 2, cy + 27, MenuStyle.MUTED, false);
        }
        MenuStyle.text(c, images.isEmpty() ? "No screenshots yet" : images.size() + " screenshots",
                x + 3, y + h - 19, MenuStyle.WHITE, 1);
        MenuStyle.text(c, "Total size: " + size(totalBytes), x + 3, y + h - 8, MenuStyle.MUTED, .77f);
    }
}

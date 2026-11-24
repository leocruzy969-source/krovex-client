package com.krovex.client.gui.screens;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import com.krovex.client.sky.SkyPreset;
import com.krovex.client.sky.SkyPresetStore;
import com.krovex.client.sky.SkyRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/** Sky presets as a grid of gradient preview tiles on the left, effect controls on the right. */
public final class SkyPanel implements KrovexSubPanel {
    private static final int COLUMNS = 3, GAP = 6, TILE_H = 56;
    private final SkyPresetStore store = KrovexClientMod.skyPresetStore();

    /** Which page of presets is showing (there are more presets than fit on one screen). Survives panel refreshes. */
    private static int page;

    public SkyPanel(Screen parent) {}

    /** How many scroll positions there are: "page" is the first visible ROW of tiles. */
    private int pages(int height) {
        int rows = (store.all().size() + COLUMNS - 1) / COLUMNS;
        return Math.max(1, rows - capacity(height) / COLUMNS + 1);
    }

    private int lastX, lastY, lastWidth, lastHeight;

    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        if (mx < lastX || mx >= lastX + leftWidth(lastWidth) || my < lastY || my >= lastY + lastHeight) return false;
        int next = Math.clamp(page - (int) Math.signum(vertical), 0, pages(lastHeight) - 1);
        if (next != page && net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof KrovexMenuScreen menu) {
            page = next;
            menu.refreshPanel();
        }
        return true;
    }

    private static int leftWidth(int width) { return Math.max(150, width * 62 / 100); }

    private static int tileWidth(int width) { return (leftWidth(width) - 16 - (COLUMNS - 1) * GAP) / COLUMNS; }

    private int capacity(int height) { return COLUMNS * Math.max(1, (height - 34) / (TILE_H + GAP)); }

    @Override public void createWidgets(Screen host, int x, int y, int width, int height) {
        var menu = (KrovexMenuScreen) host;
        lastX = x; lastY = y; lastWidth = width; lastHeight = height;
        int leftW = leftWidth(width), tileW = tileWidth(width);
        List<SkyPreset> presets = store.all();
        int cap = capacity(height), total = pages(height);
        page = Math.clamp(page, 0, total - 1);
        for (int i = page * COLUMNS; i < Math.min(presets.size(), page * COLUMNS + cap); i++) {
            var preset = presets.get(i);
            int slot = i - page * COLUMNS;
            int tx = x + 8 + (slot % COLUMNS) * (tileW + GAP), ty = y + 28 + (slot / COLUMNS) * (TILE_H + GAP);
            menu.addPanelWidget(MenuStyle.button("", tx, ty, tileW, TILE_H, 0,
                    () -> { store.setActive(preset.id()); store.setEnabled(true); menu.refreshPanel(); }));
        }
        int inner = x + leftW + 8 + 12, right = width - leftW - 8 - 24;
        var sky = store.active();
        menu.addPanelWidget(MenuStyle.toggle("Custom sky", inner, y + 30, right, 18, store.enabled(),
                () -> { store.setEnabled(!store.enabled()); menu.refreshPanel(); }));
        if (!store.enabled() || !sky.supportsAnimationControls()) return;
        menu.addPanelWidget(MenuStyle.toggle("Animate sky", inner, y + 55, right, 18, sky.animationEnabled(),
                () -> { sky.setAnimationEnabled(!sky.animationEnabled()); store.save(); menu.refreshPanel(); }));
        var reduce = menu.addPanelWidget(MenuStyle.toggle("Reduce motion", inner, y + 80, right, 18, sky.lowMotion(),
                () -> { sky.setLowMotion(!sky.lowMotion()); store.save(); menu.refreshPanel(); }));
        reduce.active = sky.animationEnabled();
        if (height >= 150) {
            var speed = menu.addPanelWidget(new MenuStyle.Slider(inner, y + 108, right, 18,
                    speedLabel(sky.animationSpeed()), sky.animationSpeed() / 2.0) {
                @Override protected void updateMessage() {setMessage(speedLabel(value * 2));}
                @Override protected void applyValue() {sky.setAnimationSpeed((float) (value * 2)); store.save();}
            });
            speed.active = sky.animationEnabled();
            menu.addPanelWidget(new MenuStyle.Slider(inner, y + 132, right, 18, intensityLabel(sky.intensity()), sky.intensity()) {
                @Override protected void updateMessage() {setMessage(intensityLabel(value));}
                @Override protected void applyValue() {sky.setIntensity((float) value); store.save();}
            });
        }
    }

    @Override public void render(DrawContext c, int x, int y, int width, int height, int mx, int my, float delta) {
        int leftW = leftWidth(width), tileW = tileWidth(width), h = Math.max(90, height - 4);
        MenuStyle.card(c, x, y, leftW, h, 8, MenuStyle.EDGE, MenuStyle.PANEL);
        MenuStyle.card(c, x + leftW + 8, y, width - leftW - 8, h, 8, MenuStyle.EDGE, MenuStyle.PANEL);
        MenuStyle.text(c, "Presets", x + 12, y + 9, MenuStyle.WHITE, 1.16f);
        MenuStyle.text(c, "Effects", x + leftW + 20, y + 9, MenuStyle.WHITE, 1.16f);
        var font = MinecraftClient.getInstance().textRenderer;
        List<SkyPreset> presets = store.all();
        var active = store.active();
        int cap = capacity(height), total = pages(height);
        if (total > 1) {
            MenuStyle.text(c, "scroll for more", x + leftW - 76, y + 10, MenuStyle.MUTED, .85f);
            int rowsVisible = cap / COLUMNS;
            MenuStyle.scrollBar(c, x + leftW - 6, y + 28, rowsVisible * (TILE_H + GAP) - GAP, page, rowsVisible, (presets.size() + COLUMNS - 1) / COLUMNS);
        }
        for (int i = page * COLUMNS; i < Math.min(presets.size(), page * COLUMNS + cap); i++) {
            var preset = presets.get(i);
            int slot = i - page * COLUMNS;
            int tx = x + 8 + (slot % COLUMNS) * (tileW + GAP), ty = y + 28 + (slot / COLUMNS) * (TILE_H + GAP);
            int[] colors = SkyRenderer.previewColors(preset.kind());
            boolean chosen = active.id().equals(preset.id()) && store.enabled();
            if (chosen) MenuStyle.glow(c, tx, ty, tileW, TILE_H, 6, MenuStyle.LIME);
            MenuStyle.gradientCard(c, tx, ty, tileW, TILE_H, 6, chosen ? MenuStyle.LIME : MenuStyle.EDGE, colors[0], colors[1]);
            c.fillGradient(tx + 2, ty + TILE_H / 2, tx + tileW - 2, ty + TILE_H - 8, colors[1], colors[2]);
            previewScene(c, preset, tx, ty, tileW, TILE_H - 16);
            c.fill(tx + 1, ty + TILE_H - 15, tx + tileW - 1, ty + TILE_H - 1, 0xB30F0A1C);
            var label = MenuStyle.lettering(Text.literal(preset.name()), chosen);
            int textWidth = font.getWidth(label);
            c.enableScissor(tx + 2, ty + TILE_H - 15, tx + tileW - 2, ty + TILE_H - 1);
            c.drawText(font, label, tx + Math.max(3, (tileW - textWidth) / 2), ty + TILE_H - 12, chosen ? MenuStyle.LIME : MenuStyle.WHITE, false);
            c.disableScissor();
        }
    }

    /** A tiny hand-drawn scene on each preview tile so the presets are recognisable at a glance. */
    private static void previewScene(DrawContext c, SkyPreset preset, int x, int y, int w, int h) {
        java.util.Random r = new java.util.Random(preset.kind().ordinal() * 977L + 13);
        int main = preset.primaryColorArgb();
        switch (preset.kind()) {
            case VANILLA -> MenuStyle.box(c, x + w - 20, y + 8, 10, 10, 1, 0xFFFFF3A8);
            case TOPOGRAPHIC_LINES -> {
                for (int ring = 0; ring < 4; ring++) outline(c, x + w * 2 / 5, y + h / 2, 5 + ring * 5, MenuStyle.withAlpha(main, 190 - ring * 30));
                outline(c, x + w * 3 / 4, y + h / 3, 7, MenuStyle.withAlpha(main, 130));
            }
            case AURORA_RIBBONS -> {
                for (int i = 0; i < w - 8; i += 3) {
                    int top = y + 6 + (int) (6 * Math.sin(i * .35)), a = 150 - (int) (60 * Math.abs(Math.sin(i * .21)));
                    c.fillGradient(x + 4 + i, top, x + 6 + i, y + h - 4, MenuStyle.withAlpha(0xFF4DFFA3, a), 0x004DFFA3);
                }
            }
            case STARFIELD, NEBULA, GALAXY -> {
                if (preset.kind() != SkyPreset.Kind.STARFIELD) {
                    int[] gas = preset.kind() == SkyPreset.Kind.NEBULA ? new int[] {0xFFC15BFF, 0xFF36C6FF, 0xFFFF6BA8} : new int[] {0xFFFFD9A0, 0xFF7EA2FF};
                    for (int i = 0; i < 3; i++) MenuStyle.disc(c, x + 12 + r.nextInt(Math.max(1, w - 24)), y + 8 + r.nextInt(Math.max(1, h - 16)), 7 + r.nextInt(4), MenuStyle.withAlpha(gas[i % gas.length], 46));
                }
                for (int i = 0; i < 18; i++) {
                    int sx = x + 3 + r.nextInt(Math.max(1, w - 6)), sy = y + 3 + r.nextInt(Math.max(1, h - 6));
                    c.fill(sx, sy, sx + 1, sy + 1, i % 5 == 0 ? 0xFFFFFFFF : 0x99FFFFFF);
                }
            }
            case SYNTHWAVE -> {
                int base = y + h - 14;
                c.fill(x + 2, base, x + w - 2, base + 1, 0xFF2BE6FF);
                for (int i = 1; i <= 4; i++) c.fill(x + 2, base + i * 3, x + w - 2, base + i * 3 + 1, MenuStyle.withAlpha(main, 220 - i * 20));
                for (int i = 0; i <= 6; i++) c.fill(x + w / 2 + (i - 3) * (w / 7), base + 1, x + w / 2 + (i - 3) * (w / 7) + 1, y + h, MenuStyle.withAlpha(main, 140));
            }
            case THUNDERCLOUDS -> {
                for (int i = 0; i < 5; i++) MenuStyle.disc(c, x + 8 + i * (w - 16) / 4, y + h - 12 - r.nextInt(8), 7 + r.nextInt(4), 0xAA6C7186);
                c.fill(x + w / 2 + 3, y + 8, x + w / 2 + 4, y + h - 18, 0xFFE8EEFF);
            }
            case DUSK -> {
                for (int i = 0; i < 4; i++) c.fillGradient(x + 3 + i * 4, y + 8 + i * 7, x + w - 3 - i * 6, y + 10 + i * 7, 0x00FFB48A, MenuStyle.withAlpha(0xFFFF7FA8, 200));
            }
            case SNOWFALL -> {
                for (int i = 0; i < 26; i++) {
                    int sx = x + 3 + r.nextInt(Math.max(1, w - 6)), sy = y + 3 + r.nextInt(Math.max(1, h - 6));
                    c.fill(sx, sy, sx + 2, sy + 2, i % 3 == 0 ? 0xFFFFFFFF : 0xB0E6F0FF);
                }
            }
            case SAKURA -> {
                for (int i = 0; i < 4; i++) c.fillGradient(x + 3 + i * 3, y + 8 + i * 6, x + w - 3 - i * 5, y + 10 + i * 6, 0x00FFD0DC, MenuStyle.withAlpha(0xFFFFB3CC, 190));
                for (int i = 0; i < 12; i++) c.fill(x + 4 + r.nextInt(Math.max(1, w - 8)), y + 3 + r.nextInt(Math.max(1, h - 6)), x + 6 + r.nextInt(1), y + 5, 0xFFFFE6F0);
            }
            case OCEAN -> {
                for (int i = 0; i < 5; i++) c.fillGradient(x + 6 + i * (w - 12) / 5, y + 2, x + 9 + i * (w - 12) / 5, y + h - 4, 0x90BFFFF4, 0x005FE8E0);
                for (int i = 0; i < 7; i++) c.fill(x + 4 + r.nextInt(Math.max(1, w - 8)), y + h / 2 + r.nextInt(Math.max(1, h / 2 - 4)), x + 6, y + h, 0x00000000);
            }
            case INFERNO -> {
                c.fillGradient(x + 2, y + h - 18, x + w - 2, y + h - 3, 0x00FF5A12, 0xDDFF5A12);
                for (int i = 0; i < 14; i++) {
                    int sx = x + 4 + r.nextInt(Math.max(1, w - 8)), sy = y + 3 + r.nextInt(Math.max(1, h - 10));
                    c.fill(sx, sy, sx + 1, sy + 2, i % 3 == 0 ? 0xFFFFD27A : 0xFFFF8A2A);
                }
            }
            case MATRIX -> {
                for (int i = 0; i < 9; i++) {
                    int sx = x + 4 + i * (w - 8) / 9, top = y + 2 + r.nextInt(6);
                    c.fillGradient(sx, top, sx + 1, top + 10 + r.nextInt(10), 0x003DFF8A, 0xFF3DFF8A);
                    c.fill(sx, top + 12, sx + 1, top + 14, 0xFFD8FFE8);
                }
            }
            case GOLDEN_HOUR -> {
                for (int i = 0; i < 4; i++) c.fillGradient(x + 3 + i * 3, y + 6 + i * 6, x + w - 3 - i * 5, y + 9 + i * 6, 0x00FFE8B8, MenuStyle.withAlpha(0xFFFFB866, 210));
            }
            case VORTEX -> {
                for (int ring = 0; ring < 4; ring++) outline(c, x + w / 2 + ring, y + h / 3 + ring, 4 + ring * 4, MenuStyle.withAlpha(0xFF4DD2FF, 200 - ring * 35));
            }
            case FIREFLIES -> {
                for (int i = 0; i < 9; i++) {
                    int sx = x + 4 + r.nextInt(Math.max(1, w - 8)), sy = y + 5 + r.nextInt(Math.max(1, h - 16));
                    MenuStyle.disc(c, sx, sy, 2, 0x33D6FF63);
                    c.fill(sx, sy, sx + 1, sy + 1, 0xFFF5FF9A);
                }
                for (int i = 0; i < w - 4; i += 3) c.fill(x + 2 + i, y + h - 5 - (int) (3 + 3 * Math.abs(Math.sin(i * .5))), x + 5 + i, y + h, 0xFF03090C);
            }
            case PRISM -> {
                int[] bow = {0xFFFF4D4D, 0xFFFFB03D, 0xFFFFE347, 0xFF55E36A, 0xFF4DB8FF, 0xFF8A5CFF};
                for (int i = 0; i < bow.length; i++) outline(c, x + w / 2, y + h - 2, 13 + (bow.length - i), MenuStyle.withAlpha(bow[i], 210));
                for (int i = 0; i < 3; i++) MenuStyle.disc(c, x + 8 + i * (w - 16) / 2, y + 8, 6, 0x66FFFFFF);
            }
            case RAINFALL -> {
                for (int i = 0; i < 14; i++) {
                    int sx = x + 3 + r.nextInt(Math.max(1, w - 6)), sy = y + 2 + r.nextInt(Math.max(1, h - 12));
                    c.fillGradient(sx, sy, sx + 1, sy + 7, 0x00C8D8F0, 0xCCC8D8F0);
                }
            }
            case COSMIC_RIFT -> {
                int rx = x + w / 2;
                for (int i = 0; i < h - 2; i += 2) {
                    int off = (int) (3 * Math.sin(i * .9));
                    c.fill(rx + off - 3, y + 1 + i, rx + off + 4, y + 3 + i, 0x44D43CFF);
                    c.fill(rx + off, y + 1 + i, rx + off + 1, y + 3 + i, 0xFFE8FFFF);
                }
            }
            case BLACK_HOLE -> {
                int cx = x + w / 2, cy = y + h / 2 - 2;
                c.fillGradient(cx - 18, cy - 1, cx + 18, cy + 2, 0xFFFFB347, 0xFFFF5A1A);
                MenuStyle.disc(c, cx, cy, 6, 0xFFFFE0A0);
                MenuStyle.disc(c, cx, cy, 5, 0xFF000000);
                c.fill(cx - 14, cy + 1, cx + 14, cy + 2, 0xFFFFF0C8);
            }
            case TEMPEST -> {
                for (int i = 0; i < 10; i++) {
                    int sx = x + 3 + r.nextInt(Math.max(1, w - 30)), sy = y + 4 + r.nextInt(Math.max(1, h - 10)), len = 12 + r.nextInt(18);
                    c.fill(sx, sy, sx + len, sy + 1, i % 3 == 0 ? 0x80A6AAB4 : 0x90383B45);
                    c.fill(sx + 3, sy + 1, sx + len - 2, sy + 2, i % 3 == 0 ? 0x40A6AAB4 : 0x60202228);
                }
            }
            case LANTERNS -> {
                for (int i = 0; i < 9; i++) {
                    int sx = x + 5 + r.nextInt(Math.max(1, w - 10)), sy = y + 4 + r.nextInt(Math.max(1, h - 12));
                    MenuStyle.disc(c, sx, sy, 3, 0x40FFB04A);
                    c.fill(sx - 1, sy - 1, sx + 1, sy + 2, 0xFFFFD98A);
                }
            }
            case METEORS -> {
                for (int i = 0; i < 4; i++) {
                    int sx = x + 6 + i * (w - 16) / 3, sy = y + 4 + (i * 7) % 12;
                    for (int k = 0; k < 9; k++) c.fill(sx - k, sy + k / 3, sx - k + 1, sy + k / 3 + 1, MenuStyle.withAlpha(0xFFFFE3B0, 255 - k * 26));
                }
            }
            default -> {}
        }
    }

    private static void outline(DrawContext c, int cx, int cy, int radius, int color) {
        for (int a = 0; a < 360; a += 360 / (radius * 4)) {
            int px = cx + (int) Math.round(radius * 1.35 * Math.cos(Math.toRadians(a))), py = cy + (int) Math.round(radius * Math.sin(Math.toRadians(a)));
            c.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static Text speedLabel(double speed) {
        return Text.literal(String.format(java.util.Locale.ROOT, "Speed %.2fx", speed));
    }

    private static Text intensityLabel(double intensity) {
        return Text.literal("Intensity " + Math.round(intensity * 100) + "%");
    }
}

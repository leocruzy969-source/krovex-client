package com.krovex.client.hud;

import com.krovex.client.gui.KrovexFontRegistry;
import com.krovex.theme.KrovexTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

final class InputHud {
    private static final int KEYBOARD_WIDTH = 96;
    private static final int INPUT_WIDTH = 170;
    private static final int INPUT_HEIGHT = 98;
    private static final int CPS_WIDTH = 62;
    private static final int CPS_HEIGHT = 20;
    private static final int GAP = 10;

    private record Key(Text label, int code, int width) {}

    private static final Key[][] ROWS = {
            {key("Esc", GLFW.GLFW_KEY_ESCAPE, 13), key("1", GLFW.GLFW_KEY_1, 13),
                    key("2", GLFW.GLFW_KEY_2, 13), key("3", GLFW.GLFW_KEY_3, 13),
                    key("4", GLFW.GLFW_KEY_4, 13), key("5", GLFW.GLFW_KEY_5, 13)},
            {key("Tab", GLFW.GLFW_KEY_TAB, 19), key("Q", GLFW.GLFW_KEY_Q, 13),
                    key("W", GLFW.GLFW_KEY_W, 13), key("E", GLFW.GLFW_KEY_E, 13),
                    key("R", GLFW.GLFW_KEY_R, 13), key("T", GLFW.GLFW_KEY_T, 13)},
            {key("Caps", GLFW.GLFW_KEY_CAPS_LOCK, 22), key("A", GLFW.GLFW_KEY_A, 13),
                    key("S", GLFW.GLFW_KEY_S, 13), key("D", GLFW.GLFW_KEY_D, 13),
                    key("F", GLFW.GLFW_KEY_F, 13), key("G", GLFW.GLFW_KEY_G, 13)},
            {key("Shift", GLFW.GLFW_KEY_LEFT_SHIFT, 28), key("Z", GLFW.GLFW_KEY_Z, 13),
                    key("X", GLFW.GLFW_KEY_X, 13), key("C", GLFW.GLFW_KEY_C, 13),
                    key("V", GLFW.GLFW_KEY_V, 13)},
            {key("Ctrl", GLFW.GLFW_KEY_LEFT_CONTROL, 19), key("Alt", GLFW.GLFW_KEY_LEFT_ALT, 19),
                    key("", GLFW.GLFW_KEY_SPACE, 50)}
    };

    private InputHud() {}

    /** A number that changes whenever a key or mouse button is pressed or released (for Dynamic HUD). */
    static int activity(MinecraftClient client, int leftClicks, int rightClicks) {
        long window = client.getWindow().getHandle();
        int hash = 17;
        for (Key[] row : ROWS) for (Key key : row) hash = hash * 31 + (GLFW.glfwGetKey(window, key.code()) == GLFW.GLFW_PRESS ? 1 : 0);
        hash = hash * 31 + GLFW.glfwGetMouseButton(window, 0) + 2 * GLFW.glfwGetMouseButton(window, 1);
        return hash * 31 + Math.min(1, leftClicks + rightClicks);
    }

    private static Key key(String name, int code, int width) {
        return new Key(Text.literal(name), code, width);
    }

    static void render(DrawContext context, MinecraftClient client, HudProfile profile, KrovexTheme theme, int left, int right,
                       java.util.Map<String, java.awt.Rectangle> bounds) {
        HudElementLayout keys = profile.layoutFor("keystrokes");
        HudElementLayout cps = profile.layoutFor("cps-counter");
        boolean showKeys = keys.visible() && KrovexHudOverlay.enabled("keystrokes");
        boolean showCps = cps.visible() && KrovexHudOverlay.enabled("cps-counter");
        if (!showKeys && !showCps) return;
        boolean joined = false; // the keystrokes panel now carries its own left/right click counts; CPS is its own pill
        if (showKeys) {
            int width = INPUT_WIDTH + (joined ? CPS_WIDTH + GAP : 0);
            var rectangle = drawAt(context, keys, width, INPUT_HEIGHT, () -> {
                if (joined) drawCps(context, client, theme, 0, INPUT_HEIGHT - CPS_HEIGHT - 4, left, right);
                drawKeys(context, client, theme, joined ? CPS_WIDTH + GAP : 0, left, right);
            });
            if (bounds != null) {
                bounds.put("keystrokes", joined
                        ? new java.awt.Rectangle(rectangle.x + CPS_WIDTH + GAP, rectangle.y, INPUT_WIDTH, INPUT_HEIGHT)
                        : rectangle);
                if (joined) bounds.put("cps-counter", new java.awt.Rectangle(rectangle.x,
                        rectangle.y + INPUT_HEIGHT - CPS_HEIGHT - 4, CPS_WIDTH, CPS_HEIGHT));
            }
        }
        if (showCps && !joined) {
            if (showKeys && defaultPosition(cps) && keys.anchor() == HudElementLayout.Anchor.BOTTOM_RIGHT) {
                cps = new HudElementLayout("cps-counter");
                int screenWidth = context.getScaledWindowWidth(), screenHeight = context.getScaledWindowHeight();
                int[] keyPosition = keys.resolvePixelPosition(screenWidth, screenHeight,
                        Math.round(INPUT_WIDTH * keys.scale()), Math.round(INPUT_HEIGHT * keys.scale()));
                cps.dragTo(Math.max(0, keyPosition[0] - CPS_WIDTH - GAP),
                        Math.max(0, keyPosition[1] + Math.round(INPUT_HEIGHT * keys.scale()) - CPS_HEIGHT - 4),
                        screenWidth, screenHeight, CPS_WIDTH, CPS_HEIGHT, 10);
            }
            var rectangle = drawAt(context, cps, CPS_WIDTH, CPS_HEIGHT, () -> drawCps(context, client, theme, 0, 0, left, right));
            if (bounds != null) bounds.put("cps-counter", rectangle);
        }
    }

    private static boolean defaultPosition(HudElementLayout layout) {
        return layout.anchor() == HudElementLayout.Anchor.BOTTOM_RIGHT && layout.offsetXFraction() == .02f
                && layout.offsetYFraction() == .02f && layout.scale() == 1f;
    }

    static java.awt.Rectangle drawAt(DrawContext context, HudElementLayout layout, int width, int height, Runnable draw) {
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        float scale = Math.min(layout.scale(), Math.min((float) screenWidth / width, (float) screenHeight / height));
        int scaledWidth = (int) Math.ceil(width * scale);
        int scaledHeight = (int) Math.ceil(height * scale);
        int[] pos = layout.resolvePixelPosition(screenWidth, screenHeight, scaledWidth, scaledHeight);
        int x = Math.clamp(pos[0], 0, Math.max(0, screenWidth - scaledWidth));
        int y = Math.clamp(pos[1], 0, Math.max(0, screenHeight - scaledHeight));
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(x, y);
            matrices.scale(scale, scale);
            draw.run();
        } finally {
            matrices.popMatrix();
        }
        return new java.awt.Rectangle(x, y, scaledWidth, scaledHeight);
    }

    private static void drawKeys(DrawContext context, MinecraftClient client, KrovexTheme theme, int offset, int left, int right) {
        long window = client.getWindow().getHandle();
        float bg = KrovexHudOverlay.backgroundAlpha("keystrokes");
        boolean clear = bg <= 0f;
        int accent = theme.accentColor();
        bubble(context, offset, 0, INPUT_WIDTH, INPUT_HEIGHT, 8, accent, bg, theme, true);
        // header
        context.drawText(client.textRenderer, KrovexFontRegistry.sized(Text.literal("KEYSTROKES"), false, 8f), offset + 8, 5,
                scaleAlpha(theme.textColor(), .8f), false);
        int pad = offset + 7;
        for (int row = 0; row < ROWS.length; row++) {
            int x = pad;
            int y = 17 + row * 15;
            for (Key key : ROWS[row]) {
                boolean down = GLFW.glfwGetKey(window, key.code()) == GLFW.GLFW_PRESS;
                if (down) {
                    hiCard(context, x - 1, y - 1, key.width() + 2, 16, 6, scaleAlpha(accent, .35f), scaleAlpha(accent, .22f), scaleAlpha(accent, .16f));
                    hiCard(context, x, y, key.width(), 14, 5, MenuStyleBridge.lighten(accent, .5f),
                            MenuStyleBridge.lighten(accent, .25f), accent);
                } else {
                    hiCard(context, x, y, key.width(), 14, 5, scaleAlpha(accent, .45f),
                            0xE61A1724, 0xE6131120);
                }
                if (!key.label().getString().isEmpty()) {
                    smallText(context, client, key.label(), x + key.width() / 2, y + 3, down ? 0xFFFFFFFF : theme.textColor());
                } else {
                    context.drawCenteredTextWithShadow(client.textRenderer, KrovexFontRegistry.sized(Text.literal("SPACE"), false, 8f),
                            x + key.width() / 2, y + 3, scaleAlpha(theme.textColor(), .8f));
                }
                x += key.width() + 2;
            }
        }
        // mouse
        int mx = pad + KEYBOARD_WIDTH + 24;
        hiCard(context, mx, 17, 20, 40, 9, scaleAlpha(accent, .5f), 0xE61A1724, 0xE6131120);
        context.fill(mx + 1, 32, mx + 19, 33, scaleAlpha(accent, .45f));
        context.fill(mx + 9, 20, mx + 11, 30, scaleAlpha(accent, .35f));
        if (GLFW.glfwGetMouseButton(window, 0) == GLFW.GLFW_PRESS)
            hiCard(context, mx + 1, 18, 9, 14, 4, MenuStyleBridge.lighten(accent, .5f), MenuStyleBridge.lighten(accent, .2f), accent);
        if (GLFW.glfwGetMouseButton(window, 1) == GLFW.GLFW_PRESS)
            hiCard(context, mx + 10, 18, 9, 14, 4, MenuStyleBridge.lighten(accent, .5f), MenuStyleBridge.lighten(accent, .2f), accent);
        // click counts
        bigNumber(context, client, Integer.toString(left), mx - 14, 62, 0xFFFFFFFF);
        bigNumber(context, client, Integer.toString(right), mx + 36, 62, 0xFFFFFFFF);
        smallText(context, client, Text.literal("LEFT"), mx - 14, 77, scaleAlpha(theme.textColor(), .75f));
        smallText(context, client, Text.literal("RIGHT"), mx + 36, 77, scaleAlpha(theme.textColor(), .75f));
    }

    /** A number centred on cx at a larger native size of the chosen font. */
    static void bigNumber(DrawContext context, MinecraftClient client, String value, int cx, int top, int color) {
        Text text = KrovexFontRegistry.sized(Text.literal(value), false, KrovexFontRegistry.isVanilla() ? 9f : 12f);
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(cx, top);
            if (KrovexFontRegistry.isVanilla()) matrices.scale(1.3f, 1.3f);
            context.drawCenteredTextWithShadow(client.textRenderer, text, 0, 0, color);
        } finally { matrices.popMatrix(); }
    }

    private static void drawCps(DrawContext context, MinecraftClient client, KrovexTheme theme, int x, int y, int left, int right) {
        panel(context, "cps-counter", x, y, CPS_WIDTH, CPS_HEIGHT, 8, theme);
        context.drawText(client.textRenderer, KrovexFontRegistry.sized(Text.literal("CPS"), false, 8f), x + 8, y + (CPS_HEIGHT - 8) / 2,
                scaleAlpha(theme.textColor(), .8f), false);
        Text number = KrovexFontRegistry.sized(Text.literal(Integer.toString(left)), false, KrovexFontRegistry.isVanilla() ? 9f : 12f);
        int w = client.textRenderer.getWidth(number);
        context.drawText(client.textRenderer, number, x + CPS_WIDTH - 8 - w, y + (CPS_HEIGHT - 9) / 2, theme.textColor(), false);
    }
    /**
     * Tiny label centred on cx. Uses the chosen font's native "small" cut so it stays sharp (the old
     * code drew the normal font and shrank it, which blurred it). The vanilla pixel font has no small
     * cut, so only that one falls back to scaling.
     */
    private static void smallText(DrawContext context, MinecraftClient client, Text raw, int cx, int top, int color) {
        boolean vanilla = KrovexFontRegistry.isVanilla();
        Text text = vanilla ? raw : KrovexFontRegistry.sized(raw, false, 8f);
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(cx, top);
            if (vanilla) matrices.scale(.8f, .8f);
            context.drawCenteredTextWithShadow(client.textRenderer, text, 0, 0, color);
        } finally { matrices.popMatrix(); }
    }

    static int scaleAlpha(int argb, float factor) {
        return Math.round((argb >>> 24) * Math.clamp(factor, 0f, 1f)) << 24 | argb & 0xFFFFFF;
    }

    /** The HUD background panel for a module, at that module's own opacity (nothing is drawn at 0%). */
    static void panel(DrawContext context, String moduleId, int x, int y, int width, int height, int radius, KrovexTheme theme) {
        panel(context, moduleId, x, y, width, height, radius, theme, theme.accentColor());
    }

    /** Same, with a different outline colour (e.g. the ping colour). */
    static void panel(DrawContext context, String moduleId, int x, int y, int width, int height, int radius, KrovexTheme theme, int outline) {
        float opacity = KrovexHudOverlay.backgroundAlpha(moduleId);
        if (opacity <= 0f) return;
        bubble(context, x, y, width, height, radius, outline, opacity, theme, false);
    }

    /**
     * The module look: near-black glass, a thin glowing outline in the accent colour. With the Bubbly style on
     * the corners become fully round and the outline gets a thick soft halo.
     */
    static void bubble(DrawContext context, int x, int y, int width, int height, int radius, int outline, float opacity,
                       KrovexTheme theme, boolean large) {
        boolean bubbly = com.krovex.client.gui.MenuStyle.bubbly();
        int r = bubbly ? Math.min(height / 2, radius + (large ? 6 : 6)) : radius;
        float halo = theme.glowEnabled() ? (bubbly ? 1f : .0f) : 0f;
        if (halo > 0) {
            for (int i = 5; i >= 1; i--) {
                hiCard(context, x - i, y - i, width + 2 * i, height + 2 * i, r + i,
                        scaleAlpha(outline, .11f * opacity * (6 - i) / 3f), 0, 0);
            }
        }
        int fillTop = scaleAlpha(com.krovex.client.gui.MenuStyle.shade(theme.panelColor() | 0xFF000000, .72f), Math.max(.55f, opacity) * .96f);
        int fillBottom = scaleAlpha(com.krovex.client.gui.MenuStyle.shade(theme.panelColor() | 0xFF000000, .52f), Math.max(.55f, opacity) * .96f);
        hiCard(context, x, y, width, height, r, scaleAlpha(outline, bubbly ? opacity : opacity * .8f), fillTop, fillBottom);
        if (bubbly && width > 6 && height > 6) {
            hiCard(context, x + 1, y + 1, width - 2, height - 2, Math.max(1, r - 1),
                    scaleAlpha(outline, opacity * .45f), 0, 0);
        }
    }
    static void outlinedPill(DrawContext context, int x, int y, int width, int height, int radius, int outline, int fill) {
        round(context, x, y, width, height, radius, outline);
        round(context, x + 1, y + 1, width - 2, height - 2, radius - 1, fill);
    }

    private static int contrast(int color) {
        int brightness = ((color >> 16 & 255) * 299 + (color >> 8 & 255) * 587 + (color & 255) * 114) / 1000;
        return brightness >= 150 ? 0xFF151515 : 0xFFFFFFFF;
    }

    /** Screen pixels per GUI pixel. Shapes are drawn at screen resolution so corners are smooth, not blocky. */
    static int guiScale() {
        return Math.max(1, (int) MinecraftClient.getInstance().getWindow().getScaleFactor());
    }

    /** A rounded card drawn at screen-pixel resolution (the outline keeps the thickness of one GUI pixel). Cached. */
    static void hiCard(DrawContext c, int x, int y, int w, int h, int r, int edge, int top, int bottom) {
        int s = guiScale();
        var matrices = c.getMatrices();
        matrices.pushMatrix();
        try {
            if (s > 1) matrices.scale(1f / s, 1f / s);
            if (!ShapeCache.draw(c, x * s, y * s, w * s, h * s, r * s, Math.min(s, 3), edge, top, bottom)) {
                com.krovex.client.gui.MenuStyle.gradientCardRaw(c, x * s, y * s, w * s, h * s, r * s, edge, top, bottom);
            }
        } finally {
            matrices.popMatrix();
        }
    }

    static void round(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        int s = guiScale();
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            if (s > 1) matrices.scale(1f / s, 1f / s);
            if (radius <= 0 || !ShapeCache.draw(context, x * s, y * s, width * s, height * s, radius * s, 0, color, color, color)) {
                roundRaw(context, x * s, y * s, width * s, height * s, radius * s, color);
            }
        } finally {
            matrices.popMatrix();
        }
    }
    private static void roundRaw(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (radius <= 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }
        for (int row = 0; row < radius; row++) {
            double dy = radius - row - .5;
            int inset = (int) Math.round(radius - Math.sqrt(radius * radius - dy * dy));
            context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            context.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
        context.fill(x, y + radius, x + width, y + height - radius, color);
    }
}

package com.krovex.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Everything the Krovex menus are drawn with: gradient cards, glowing buttons, switches, sliders,
 * sidebar items with vector icons. All text goes through {@link KrovexFontRegistry}, so the whole menu
 * uses the one chosen font at native sizes.
 */
public final class MenuStyle {
    public static final int WHITE = 0xFFF9F5FF, MUTED = 0xFFA9A2BC, LIME = 0xFFC4FF42;
    public static final int PANEL = 0x9E14111F, CARD = 0x8A1E1A2E, EDGE = 0x34FFFFFF;
    /** A slightly lighter pane for cards that sit on a PANEL. */
    public static final int GLASS = 0x70262238, HAIRLINE = 0x1EFFFFFF;
    public static final int PURPLE = 0xFFE106F6;
    public static final int VIOLET = 0xFFB04CFF, PINK = 0xFFFF4FD8, HOVER_EDGE = 0xFFC17AFE;
    private MenuStyle() {}

    // ------------------------------------------------------------------ colour helpers

    // The menu palette was drawn around the default violet accent. To make it follow the active theme, every
    // palette colour is hue-rotated (and re-saturated) by the difference between the theme accent and that default.
    private static long themeSyncedAt;
    private static float hueShift, satScale = 1f;
    private static boolean tintActive, bubbly, rainbow;
    private static float rainbowSpeed = 1f;
    private static int baseAccent = 0xFF8A4FFF, shiftedFor;

    private static void syncTheme() {
        long now = System.nanoTime();
        if (themeSyncedAt != 0 && now - themeSyncedAt < 300_000_000L) return;
        themeSyncedAt = now;
        try {
            baseAccent = com.krovex.client.KrovexClientMod.themeStore().getActiveTheme().accentColor();
            var style = com.krovex.client.KrovexClientMod.stylePreference();
            bubbly = style != null && style.bubbly();
            rainbow = style != null && style.rainbow();
            rainbowSpeed = style == null ? 1f : style.rainbowSpeed();
            if (!rainbow) shiftTo(baseAccent);
        } catch (RuntimeException ignored) { tintActive = false; }
    }

    private static void shiftTo(int accent) {
        if (accent == shiftedFor) return;
        shiftedFor = accent;
        float[] a = java.awt.Color.RGBtoHSB(accent >> 16 & 255, accent >> 8 & 255, accent & 255, null);
        float[] d = java.awt.Color.RGBtoHSB(0x8A, 0x4F, 0xFF, null);
        hueShift = a[0] - d[0];
        satScale = Math.clamp(a[1] / d[1], 0f, 1.35f);
        tintActive = Math.abs(hueShift) > .004f || Math.abs(satScale - 1f) > .03f;
    }

    /** True while the "RGB wave" is on (the accent cycles through every hue). */
    public static boolean rainbow() { syncTheme(); return rainbow; }

    /**
     * The accent colour to draw with right now: the theme's own, or, with the RGB wave on, a hue that travels round
     * the colour wheel (in 120 small steps per lap, so cached shapes are reused instead of rebuilt every frame).
     */
    public static int liveAccent(int accent) {
        syncTheme();
        if (!rainbow) return accent;
        double lap = 9.0 / rainbowSpeed;
        int step = (int) ((System.nanoTime() / 1e9 % lap) / lap * 120);
        return accent & 0xFF000000 | java.awt.Color.HSBtoRGB(step / 120f, .82f, 1f) & 0xFFFFFF;
    }

    /** True when the Bubbly look is switched on in the Theme panel. */
    public static boolean bubbly() { syncTheme(); return bubbly; }

    /** A palette colour recoloured to the active theme (neutral greys and whites are left alone). */
    public static int tint(int argb) {
        syncTheme();
        if (rainbow) shiftTo(liveAccent(baseAccent));
        if (!tintActive) return argb;
        float[] hsb = java.awt.Color.RGBtoHSB(argb >> 16 & 255, argb >> 8 & 255, argb & 255, null);
        if (hsb[1] < .07f) return argb;
        float h = hsb[0] + hueShift;
        h -= (float) Math.floor(h);
        int rgb = java.awt.Color.HSBtoRGB(h, Math.min(1f, hsb[1] * satScale), hsb[2]) & 0xFFFFFF;
        return argb & 0xFF000000 | rgb;
    }

    public static int shade(int argb, float factor) {
        int r = Math.min(255, Math.round((argb >> 16 & 255) * factor));
        int g = Math.min(255, Math.round((argb >> 8 & 255) * factor));
        int b = Math.min(255, Math.round((argb & 255) * factor));
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static int blend(int a, int b, float t) {
        t = Math.clamp(t, 0f, 1f);
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = a >> shift & 255, cb = b >> shift & 255;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }

    public static int withAlpha(int argb, int alpha) { return argb & 0xFFFFFF | Math.clamp(alpha, 0, 255) << 24; }

    // ------------------------------------------------------------------ shapes

    public static void box(DrawContext c, int x, int y, int w, int h, int r, int color) {
        boxRaw(c, x, y, w, h, r, tint(color));
    }

    /** Same as {@link #box} but the colour is used as given (for colours that already come from the theme). */
    public static void boxRaw(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        c.fill(x, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - .5;
            int inset = r - (int) Math.sqrt(r * r - dy * dy);
            c.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            c.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
        }
    }

    public static void disc(DrawContext c, int cx, int cy, int r, int color) {
        color = tint(color);
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, r * r + r - dy * dy)));
            c.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** Rounded card with a soft vertical gradient and a 1px top sheen. */
    public static void card(DrawContext c, int x, int y, int w, int h, int r, int edge, int color) {
        gradientCard(c, x, y, w, h, r, edge, shade(color, 1.16f), shade(color, .88f));
    }

    public static void gradientCard(DrawContext c, int x, int y, int w, int h, int r, int edge, int top, int bottom) {
        int rounder = (bubbly() ? (h <= 26 ? 4 : 3) : 0) + (r >= 5 && h >= 30 ? 2 : 0);
        gradientCardRaw(c, x, y, w, h, r + (r > 0 ? rounder : 0), tint(edge), tint(top), tint(bottom));
    }

    /** Rounded card with the colours used as given (HUD modules pass theme colours and must not be re-tinted). */
    public static void cardRaw(DrawContext c, int x, int y, int w, int h, int r, int edge, int color) {
        gradientCardRaw(c, x, y, w, h, r, edge, shade(color, 1.16f), shade(color, .88f));
    }

    public static void gradientCardRaw(DrawContext c, int x, int y, int w, int h, int r, int edge, int top, int bottom) {
        if (w <= 1 || h <= 1) return;
        r = Math.min(r, Math.min(w, h) / 2);
        int cap = Math.max(1, r);
        for (int row = 0; row < cap; row++) {
            int outer = inset(row, h, r);
            int inner = row == 0 ? w / 2 : 1 + inset(row - 1, h - 2, Math.max(0, r - 1));
            borderRow(c, x, y + row, w, outer, inner, row == 0, edge, blend(top, bottom, (row + .5f) / h));
            borderRow(c, x, y + h - row - 1, w, outer, inner, row == 0, edge, blend(top, bottom, (h - row - .5f) / h));
        }
        if (h > 2 * cap) {
            c.fill(x, y + cap, x + 1, y + h - cap, edge);
            c.fillGradient(x + 1, y + cap, x + w - 1, y + h - cap, blend(top, bottom, (float) cap / h), blend(top, bottom, (float) (h - cap) / h));
            c.fill(x + w - 1, y + cap, x + w, y + h - cap, edge);
        }
        if (h >= 12 && w > 2 * r + 2 && (top >>> 24) > 40) c.fill(x + r, y + 1, x + w - r, y + 2, 0x18FFFFFF);
    }

    private static void borderRow(DrawContext c, int x, int y, int w, int outer, int inner,
                                  boolean edgeOnly, int edge, int fill) {
        if (edgeOnly || inner * 2 >= w) {
            c.fill(x + outer, y, x + w - outer, y + 1, edge);
            return;
        }
        c.fill(x + outer, y, x + inner, y + 1, edge);
        c.fill(x + inner, y, x + w - inner, y + 1, fill);
        c.fill(x + w - inner, y, x + w - outer, y + 1, edge);
    }

    private static int inset(int row, int height, int radius) {
        int nearest = Math.min(row, height - row - 1);
        if (nearest >= radius) return 0;
        double dy = radius - nearest - .5;
        return radius - (int) Math.sqrt(radius * radius - dy * dy);
    }

    /** A soft coloured halo around a rounded rectangle (used for hover / selection). */
    public static void glow(DrawContext c, int x, int y, int w, int h, int r, int color) {
        card(c, x - 1, y - 1, w + 2, h + 2, r + 1, withAlpha(color, 0x58), 0);
        card(c, x - 2, y - 2, w + 4, h + 4, r + 2, withAlpha(color, 0x26), 0);
    }

    /** The halo at a strength from 0 to 1 (hover fades use this). */
    public static void glow(DrawContext c, int x, int y, int w, int h, int r, int color, float strength) {
        strength = Math.clamp(strength, 0f, 1f);
        card(c, x - 1, y - 1, w + 2, h + 2, r + 1, withAlpha(color, Math.round(0x58 * strength)), 0);
        card(c, x - 2, y - 2, w + 4, h + 4, r + 2, withAlpha(color, Math.round(0x26 * strength)), 0);
    }

    public static void glass(DrawContext c, int x, int y, int w, int h, int r) {
        // a soft dark drop shadow (falls slightly downward), then the coloured halo, then the panel itself
        for (int i = 9; i > 0; i--) {
            gradientCardRaw(c, x - i, y - i + 3, w + 2 * i, h + 2 * i, r + i, (int) (0x1A * (10 - i) / 10f) << 24, 0, 0);
        }
        for (int i = 4; i > 0; i--) {
            card(c, x - i, y - i, w + 2 * i, h + 2 * i, r + i, 0x167D42DC, 0);
        }
        gradientCard(c, x, y, w, h, r, 0x70B79BE8, 0xF016131F, 0xF00B0A12);
        // a faint light falling from the top, and a low accent glow rising from the bottom-left corner
        c.fillGradient(x + r, y + 2, x + w - r, y + Math.min(h - 2, 80), 0x10FFFFFF, 0x00FFFFFF);
        int glowH = Math.min(h / 3, 110);
        c.fillGradient(x + 2, y + h - glowH - r, x + Math.min(w / 2, 260), y + h - r, 0x00000000, tint(0x207A3CFF));
    }

    /** Hover / press fade shared by the menu's buttons: call {@link #step} once per frame. */
    public static final class Fade {
        private float value;
        private long last = System.nanoTime();

        public float step(boolean on) {
            long now = System.nanoTime();
            float dt = Math.min(.1f, (now - last) / 1e9f);
            last = now;
            value = on ? Math.min(1f, value + dt / .11f) : Math.max(0f, value - dt / .16f);
            return value * value * (3 - 2 * value);
        }
    }

    /** A slim scroll bar for a list showing {@code visible} of {@code total} rows starting at {@code first}. */
    public static void scrollBar(DrawContext c, int x, int y, int h, int first, int visible, int total) {
        if (total <= visible || h < 12) return;
        box(c, x, y, 3, h, 1, 0x40000000);
        int thumb = Math.max(12, h * visible / total);
        int at = y + (h - thumb) * first / Math.max(1, total - visible);
        box(c, x, at, 3, thumb, 1, LIME);
    }

    /** A hover highlight for a list row. */
    public static void rowHover(DrawContext c, int x, int y, int w, int h) {
        c.fillGradient(x, y, x + w, y + h, 0x24FFFFFF, 0x12FFFFFF);
    }

    // ------------------------------------------------------------------ text

    public static Text lettering(Text value, boolean bold) {
        return bold ? KrovexFontRegistry.bold(value) : KrovexFontRegistry.regular(value);
    }

    public static void text(DrawContext c, String value, int x, int y, int color, float scale) {
        color = tint(color);
        var font = MinecraftClient.getInstance().textRenderer;
        boolean bold = color == WHITE && scale > 1.05f;
        if (!KrovexFontRegistry.isVanilla()) {
            // Draw at the matching NATIVE font size instead of scaling a 9px font by a fractional
            // factor: scaled glyphs get resampled and look soft/uneven, native ones are crisp.
            c.drawText(font, KrovexFontRegistry.sized(Text.literal(value), bold, 9f * scale), x, y, color, false);
            return;
        }
        var matrices = c.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(x, y);
            matrices.scale(scale, scale);
            c.drawText(font, lettering(Text.literal(value), bold), 0, 0, color, false);
        } finally {
            matrices.popMatrix();
        }
    }

    public static void logo(DrawContext c, int x, int y, int size) {
        c.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                net.minecraft.util.Identifier.of("krovex-client", "textures/gui/krovex.png"),
                x, y, 0, 0, size, size, size, size);
    }

    /** Page title with a short accent bar under it, then the muted subtitle. */
    public static void heading(DrawContext c, int x, int y, String title, String subtitle) {
        text(c, title, x, y, WHITE, 1.75f);
        box(c, x, y + 19, 26, 3, 1, LIME);
        box(c, x + 28, y + 19, 10, 3, 1, VIOLET);
        text(c, subtitle, x, y + 27, MUTED, 1);
    }

    // ------------------------------------------------------------------ inputs

    public static net.minecraft.client.gui.widget.TextFieldWidget field(int x, int y, int w, int h, Text label) {
        var field = new net.minecraft.client.gui.widget.TextFieldWidget(
                MinecraftClient.getInstance().textRenderer, x, y, w, h, label) {
            @Override public void setPlaceholder(Text placeholder) { super.setPlaceholder(lettering(placeholder, false)); }

            @Override public void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
                if (isFocused()) glow(c, getX(), getY(), getWidth(), getHeight(), 5, VIOLET);
                gradientCard(c, getX(), getY(), getWidth(), getHeight(), 5, isFocused() ? 0xFFD592FF : EDGE, 0xC0181524, 0xC0100E1A);
                var matrices = c.getMatrices();
                matrices.pushMatrix();
                try {
                    matrices.translate(5, (getHeight() - 9) / 2f);
                    super.renderWidget(c, mouseX, mouseY, delta);
                } finally { matrices.popMatrix(); }
            }
        };
        field.addFormatter((text, offset) -> lettering(Text.literal(text), false).asOrderedText());
        field.setDrawsBackground(false);
        field.setTextShadow(false);
        field.setEditableColor(WHITE);
        return field;
    }

    public static abstract class Slider extends net.minecraft.client.gui.widget.SliderWidget {
        public Slider(int x, int y, int w, int h, Text label, double value) { super(x, y, w, h, label, value); }

        @Override public void onClick(net.minecraft.client.gui.Click click, boolean doubleClick) {
            updateFromPointer(click);
        }

        @Override protected void onDrag(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
            updateFromPointer(click);
        }

        private void updateFromPointer(net.minecraft.client.gui.Click click) {
            int start = getX() + getWidth() / 2;
            setValue((click.x() - start) / Math.max(3, getWidth() - getWidth() / 2 - 8));
        }

        @Override public void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), width = getWidth(), height = getHeight();
            int trackX = x + width / 2;
            int trackWidth = Math.max(3, width - width / 2 - 8);
            int center = y + height / 2;
            int knob = (int) (Math.clamp(value, 0, 1) * trackWidth);
            text(c, getMessage().getString(), x + 4, center - 4, active ? WHITE : MUTED, .9f);
            box(c, trackX, center - 3, trackWidth, 6, 3, 0xCC16131F);
            box(c, trackX, center - 3, trackWidth, 1, 0, 0x22FFFFFF);
            if (knob > 0) c.fillGradient(trackX + 1, center - 2, trackX + 1 + Math.max(2, knob), center + 2, VIOLET, PINK);
            boolean hover = isHovered() || isFocused();
            if (hover) disc(c, trackX + knob, center, 7, 0x40FF4FD8);
            disc(c, trackX + knob, center, 5, 0x66000000);
            disc(c, trackX + knob, center, 4, active ? WHITE : MUTED);
        }
    }

    /** A switch like the launcher's mod toggles: lime when on, dark when off. */
    public static ButtonWidget toggle(String name, int x, int y, int w, int h, boolean enabled, Runnable run) {
        return new ButtonWidget(x, y, w, h, Text.literal(name), ignored -> run.run(), supplier -> supplier.get()) {
            @Override protected void drawIcon(DrawContext c, int mouseX, int mouseY, float delta) {
                int switchX = getX() + getWidth() - 27, cy = getY() + getHeight() / 2;
                boolean hover = isHovered() || isFocused();
                if (hover) glow(c, switchX, cy - 7, 27, 14, 7, enabled ? LIME : VIOLET);
                if (enabled) gradientCard(c, switchX, cy - 7, 27, 14, 7, 0xFFE4FF9A, 0xFFD6FF6B, 0xFF9FE018);
                else gradientCard(c, switchX, cy - 7, 27, 14, 7, 0x50FFFFFF, 0xFF2A2638, 0xFF1C1928);
                int knobX = switchX + (enabled ? 14 : 2);
                disc(c, knobX + 5, cy, 6, 0x40000000);
                disc(c, knobX + 5, cy, 5, enabled ? 0xFFFFFFFF : 0xFFD9D0E8);
                if (getWidth() > 44 && !name.isEmpty()) text(c, name, getX() + 2, cy - 4, WHITE, 1);
            }
        };
    }

    public static ButtonWidget button(String label, int x, int y, int w, int h, int fill, Runnable run) {
        return new ButtonWidget(x, y, w, h, Text.literal(label), ignored -> run.run(), supplier -> supplier.get()) {
            @Override protected void drawLabel(net.minecraft.client.font.DrawnTextConsumer text) {}

            private final Fade fade = new Fade();

            @Override
            protected void drawIcon(DrawContext c, int mouseX, int mouseY, float delta) {
                // hover fades in and out (about a tenth of a second) instead of snapping
                float t = fade.step((isHovered() || isSelected()) && active);
                boolean primary = fill == PURPLE, lime = fill == LIME, ghost = fill == 0;
                int top, bottom, edge;
                if (!active) {
                    top = 0x70302646; bottom = 0x70231A38; edge = 0x44FFFFFF;
                } else if (primary) {
                    top = blend(0xFFF03CFF, 0xFFFF62FF, t); bottom = blend(0xFFAD0CCF, 0xFFC41EE6, t); edge = 0xFFFF9CFF;
                } else if (lime) {
                    top = blend(0xFFD4FF5C, 0xFFE3FF86, t); bottom = blend(0xFFA4E818, 0xFFB6F52F, t); edge = 0xFFEAFFA8;
                } else if (ghost) {
                    top = blend(0, 0x40FFFFFF, t); bottom = blend(0, 0x22FFFFFF, t); edge = blend(0, 0x66FFFFFF, t);
                } else {
                    top = blend(shade(fill, 1.25f), shade(fill, 1.55f), t); bottom = blend(shade(fill, .92f), shade(fill, 1.2f), t);
                    edge = blend(EDGE, HOVER_EDGE, t);
                }
                if (t > .02f && !ghost) glow(c, getX(), getY(), getWidth(), getHeight(), 5, primary ? PINK : lime ? LIME : VIOLET, t);
                if (top != 0 || bottom != 0 || edge != 0) gradientCard(c, getX(), getY(), getWidth(), getHeight(), 5, edge, top, bottom);
                var font = MinecraftClient.getInstance().textRenderer;
                var text = lettering(getMessage(), primary || lime);
                int color = lime && active ? 0xFF17101E : active ? WHITE : MUTED;
                int textWidth = font.getWidth(text);
                int padding = ghost ? 4 : 2;
                c.enableScissor(getX() + padding, getY(), getX() + getWidth() - padding, getY() + getHeight());
                c.drawText(font, text, getX() + Math.max(padding, (getWidth() - textWidth) / 2),
                        getY() + (getHeight() - 8) / 2, color, false);
                c.disableScissor();
            }
        };
    }

    /** A sidebar entry: vector icon in a fixed column, left-aligned label, accent bar + gradient when selected. */
    public static ButtonWidget nav(String label, String icon, int x, int y, int w, int h, boolean selected, Runnable run) {
        return new ButtonWidget(x, y, w, h, Text.literal(label), ignored -> run.run(), supplier -> supplier.get()) {
            @Override protected void drawLabel(net.minecraft.client.font.DrawnTextConsumer text) {}

            private final Fade fade = new Fade();

            @Override
            protected void drawIcon(DrawContext c, int mouseX, int mouseY, float delta) {
                float t = fade.step(isHovered() || isSelected());
                int gx = getX(), gy = getY(), gw = getWidth(), gh = getHeight();
                if (selected) {
                    gradientCard(c, gx, gy, gw, gh, 6, 0x38FFFFFF, 0x30FFFFFF, 0x1AFFFFFF);
                    c.fillGradient(gx + 1, gy + 4, gx + 3, gy + gh - 4, LIME, 0xFF8FD60F);
                } else if (t > .02f) {
                    gradientCard(c, gx, gy, gw, gh, 6, blend(0, 0x20FFFFFF, t), blend(0, 0x1CFFFFFF, t), blend(0, 0x0CFFFFFF, t));
                }
                int color = selected ? 0xFFFFFFFF : blend(0xFFA9A2BC, WHITE, t);
                int slide = selected ? 0 : Math.round(2 * t);          // the label nudges right as you point at it
                navIcon(c, icon, gx + 11 + slide, gy + (gh - 10) / 2, selected ? LIME : color);
                var font = MinecraftClient.getInstance().textRenderer;
                c.enableScissor(gx + 26, gy, gx + gw - 3, gy + gh);
                c.drawText(font, lettering(getMessage(), selected), gx + 27 + slide, gy + (gh - 8) / 2, color, false);
                c.disableScissor();
            }
        };
    }

    /** A smaller, indented sidebar entry with a count on the right (the module categories under "Modules"). */
    public static ButtonWidget subNav(String label, int count, int x, int y, int w, int h, boolean selected, Runnable run) {
        return new ButtonWidget(x, y, w, h, Text.literal(label), ignored -> run.run(), supplier -> supplier.get()) {
            private final Fade fade = new Fade();

            @Override protected void drawLabel(net.minecraft.client.font.DrawnTextConsumer text) {}

            @Override
            protected void drawIcon(DrawContext c, int mouseX, int mouseY, float delta) {
                float t = fade.step(isHovered() || isSelected());
                int gx = getX(), gy = getY(), gw = getWidth(), gh = getHeight();
                if (t > .02f && !selected) gradientCard(c, gx, gy, gw, gh, 5, 0, blend(0, 0x18FFFFFF, t), blend(0, 0x0AFFFFFF, t));
                int color = selected ? WHITE : blend(0xFF8F88A4, WHITE, t);
                MenuStyle.text(c, label, gx + 8, gy + (gh - 8) / 2, color, .95f);
                if (selected) box(c, gx + 8, gy + gh - 2, Math.min(gw - 40, 30), 1, 0, LIME);
                String number = Integer.toString(count);
                var font = MinecraftClient.getInstance().textRenderer;
                MenuStyle.text(c, number, gx + gw - 6 - font.getWidth(number), gy + (gh - 8) / 2, selected ? WHITE : 0xFF7C7690, .85f);
            }
        };
    }

    /** A frosted module card: hairline border, soft fill, brighter border under the mouse or when its module is on. */
    public static void moduleCard(DrawContext c, int x, int y, int w, int h, boolean hover, boolean on) {
        if (on) glow(c, x, y, w, h, 8, LIME, .28f);
        gradientCard(c, x, y, w, h, 8, on ? 0x66C4FF42 : hover ? 0x60FFFFFF : EDGE, hover ? 0x8A2A2540 : 0x7A1D1A2B, hover ? 0x8A1E1A30 : 0x7A141220);
    }

    // ------------------------------------------------------------------ icons

    /** Draws one of the sidebar icons inside a 10x10 box at (x, y). */
    public static void navIcon(DrawContext c, String name, int x, int y, int col) {
        switch (name) {
            case "home" -> {
                for (int i = 0; i < 4; i++) c.fill(x + 4 - i, y + i, x + 6 + i, y + i + 1, col);
                c.fill(x + 2, y + 4, x + 3, y + 10, col);
                c.fill(x + 7, y + 4, x + 8, y + 10, col);
                c.fill(x + 2, y + 9, x + 8, y + 10, col);
                c.fill(x + 4, y + 6, x + 6, y + 10, col);
            }
            case "modules" -> {
                c.fill(x, y, x + 4, y + 4, col); c.fill(x + 6, y, x + 10, y + 4, col);
                c.fill(x, y + 6, x + 4, y + 10, col); c.fill(x + 6, y + 6, x + 10, y + 10, withAlpha(col, 150));
            }
            case "mods" -> {
                c.fill(x, y, x + 10, y + 1, col); c.fill(x, y + 9, x + 10, y + 10, col);
                c.fill(x, y, x + 1, y + 10, col); c.fill(x + 9, y, x + 10, y + 10, col);
                c.fill(x + 4, y + 2, x + 6, y + 8, col); c.fill(x + 2, y + 4, x + 8, y + 6, col);
            }
            case "textures" -> {
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
                    if ((i + j) % 2 == 0) c.fill(x + i * 3 + (i > 0 ? 1 : 0), y + j * 3 + (j > 0 ? 1 : 0), x + i * 3 + 3, y + j * 3 + 3, col);
                    else c.fill(x + i * 3 + (i > 0 ? 1 : 0), y + j * 3 + (j > 0 ? 1 : 0), x + i * 3 + 3, y + j * 3 + 3, withAlpha(col, 90));
                }
            }
            case "settings" -> {
                disc(c, x + 5, y + 5, 4, col);
                disc(c, x + 5, y + 5, 2, 0xFF1A1230);
                c.fill(x + 4, y, x + 6, y + 2, col); c.fill(x + 4, y + 8, x + 6, y + 10, col);
                c.fill(x, y + 4, x + 2, y + 6, col); c.fill(x + 8, y + 4, x + 10, y + 6, col);
            }
            case "hud" -> {
                c.fill(x, y + 1, x + 10, y + 2, col); c.fill(x, y + 8, x + 10, y + 9, col);
                c.fill(x, y + 1, x + 1, y + 9, col); c.fill(x + 9, y + 1, x + 10, y + 9, col);
                c.fill(x + 1, y + 4, x + 9, y + 5, col); c.fill(x + 4, y + 5, x + 5, y + 8, col);
            }
            case "theme" -> {
                disc(c, x + 5, y + 5, 5, col);
                c.fill(x + 2, y + 3, x + 4, y + 5, 0xFF1A1230); c.fill(x + 6, y + 2, x + 8, y + 4, 0xFF1A1230);
                c.fill(x + 6, y + 6, x + 8, y + 8, 0xFF1A1230);
            }
            case "shots" -> {
                c.fill(x, y + 2, x + 10, y + 3, col); c.fill(x, y + 9, x + 10, y + 10, col);
                c.fill(x, y + 2, x + 1, y + 10, col); c.fill(x + 9, y + 2, x + 10, y + 10, col);
                c.fill(x + 3, y, x + 7, y + 2, col);
                c.fill(x + 3, y + 4, x + 7, y + 8, col);
            }
            case "sky" -> {
                disc(c, x + 5, y + 5, 2, col);
                c.fill(x + 4, y, x + 6, y + 2, col); c.fill(x + 4, y + 8, x + 6, y + 10, col);
                c.fill(x, y + 4, x + 2, y + 6, col); c.fill(x + 8, y + 4, x + 10, y + 6, col);
                c.fill(x + 1, y + 1, x + 2, y + 2, col); c.fill(x + 8, y + 1, x + 9, y + 2, col);
                c.fill(x + 1, y + 8, x + 2, y + 9, col); c.fill(x + 8, y + 8, x + 9, y + 9, col);
            }
            case "music" -> {
                disc(c, x + 3, y + 7, 2, col);
                c.fill(x + 5, y + 1, x + 6, y + 8, col);
                c.fill(x + 6, y + 1, x + 9, y + 3, col);
            }
            case "about" -> {
                disc(c, x + 5, y + 5, 5, col);
                c.fill(x + 4, y + 2, x + 6, y + 4, 0xFF1A1230);
                c.fill(x + 4, y + 5, x + 6, y + 8, 0xFF1A1230);
            }
            default -> disc(c, x + 5, y + 5, 3, col);
        }
    }
}

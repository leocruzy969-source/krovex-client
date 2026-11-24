package com.krovex.client.gui.screens;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.KrovexConfigPaths;
import com.krovex.client.gui.KrovexFontRegistry;
import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import com.krovex.theme.KrovexTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Theme: preset on top, then three pages of controls.
 *  - Colours: pick what to colour (accent / HUD panel / HUD text / background) and set it with a hex code, a hue
 *    slider or red / green / blue sliders, so every colour is reachable.
 *  - Style: font, opacity, corner radius, Bubbly look.
 *  - Effects: RGB wave (the accent travels through every hue), blur, glow, animations.
 */
public final class ThemePanel implements KrovexSubPanel {
    private static final String[] PAGES = {"Colours", "Style", "Effects"};
    private static final String[] TARGETS = {"Accent", "HUD panel", "HUD text", "Background"};
    /** Which page / colour target is showing. Static so they survive panel refreshes. */
    private static int page, target;

    private final com.krovex.theme.ThemeStore store = KrovexClientMod.themeStore();
    private KrovexTheme theme;
    private TextFieldWidget hexField;
    private boolean syncingHex;
    private final DropList presetList = new DropList(), targetList = new DropList(), fontList = new DropList();

    public ThemePanel(Screen parent) { theme = store.getActiveTheme(); }

    @Override public void onShown() { theme = store.getActiveTheme(); }

    // ------------------------------------------------------------------ colour plumbing

    private int colour() {
        return switch (target) {
            case 1 -> theme.panelColor();
            case 2 -> theme.textColor();
            case 3 -> theme.backgroundColor();
            default -> theme.accentColor();
        };
    }

    private void setColour(int argb) {
        argb |= 0xFF000000;
        var b = theme.toBuilder();
        switch (target) {
            case 1 -> b.panelColor(argb);
            case 2 -> b.textColor(argb);
            case 3 -> b.backgroundColor(argb);
            default -> b.accentColor(argb);
        }
        save(b.build());
    }

    private void save(KrovexTheme updated) {
        // never overwrite a built-in preset: the first edit makes your own copy ("<name> (edited)") and edits that, so a built-in
        // theme (and "Reset to default") always means exactly what it shipped as
        if (updated.builtin()) {
            updated = updated.toBuilder().id("custom-" + updated.id()).name(updated.name() + " (edited)").builtin(false).build();
        }
        theme = updated;
        store.save(updated);
        store.setActiveThemeId(updated.id());
        KrovexFontRegistry.invalidate(); // new font on the very next frame, in the menu and on the HUD
    }

    /** One slider of the colour mixer: 0 red, 1 green, 2 blue, 3 hue. Follows the colour when it changes elsewhere. */
    private final class ColourSlider extends MenuStyle.Slider {
        private final int channel;

        ColourSlider(int x, int y, int w, int h, int channel) {
            super(x, y, w, h, Text.empty(), 0);
            this.channel = channel;
            value = read();
            updateMessage();
        }

        private double read() {
            int c = colour();
            if (channel == 3) return java.awt.Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null)[0];
            return (c >> (16 - channel * 8) & 255) / 255.0;
        }

        @Override protected void updateMessage() {
            String name = new String[] {"Red", "Green", "Blue", "Hue"}[channel];
            setMessage(Text.literal(name + "  " + (channel == 3 ? Math.round(value * 360) + "°" : Integer.toString((int) Math.round(value * 255)))));
        }

        @Override protected void applyValue() {
            int c = colour();
            if (channel == 3) {
                float[] hsb = java.awt.Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
                if (hsb[1] < .08f) { hsb[1] = .8f; hsb[2] = Math.max(hsb[2], .9f); }   // a grey has no hue: give it some colour
                setColour(java.awt.Color.HSBtoRGB((float) value, hsb[1], hsb[2]));
            } else {
                int shift = 16 - channel * 8;
                setColour(c & ~(255 << shift) | (int) Math.round(value * 255) << shift);
            }
        }

        @Override public void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            if (!isHovered() && !isFocused()) {      // someone else changed the colour (hex box, another slider, a preset)
                double now = read();
                if (Math.abs(now - value) > .002) { value = now; updateMessage(); }
            }
            super.renderWidget(c, mouseX, mouseY, delta);
        }
    }

    // ------------------------------------------------------------------ widgets

    @Override public void createWidgets(Screen host, int x, int y, int w, int h) {
        var menu = (KrovexMenuScreen) host;
        hexField = null;
        int controls = w * 58 / 100, size = Math.max(45, controls - 12);
        int step = Math.clamp((h - 5) / 8, 12, 23), bh = step - 2, left = x + 5;
        var themes = store.listThemes();
        // reset: asks first, then puts the theme options back to the shipped defaults
        int cardLeft = x + w * 58 / 100, cardSpan = x + w - cardLeft;
        menu.addPanelWidget(MenuStyle.button("Reset to default", cardLeft + cardSpan - 96, y + 3, 90, 16, MenuStyle.CARD, () ->
                menu.askConfirm("Are you sure you want to reset your options for theme to default?", () -> resetToDefault(menu))));
        // preset: a list of every theme, each with a chip of its accent colour
        var presetItems = themes.stream().map(t -> new DropList.Item(t.name(), t.accentColor())).toList();
        int presetIndex = 0;
        for (int i = 0; i < themes.size(); i++) if (themes.get(i).id().equals(theme.id())) presetIndex = i;
        presetList.button(menu, "Preset:  ", left, y + 3, size, bh, presetItems, presetIndex, index -> {
            store.setActiveThemeId(themes.get(index).id());
            theme = store.getActiveTheme();
            KrovexFontRegistry.invalidate();
            menu.refreshPanel();
        });

        switch (page) {
            case 0 -> {
                targetList.button(menu, "Colour:  ", left, row(y, step, 1), size, bh,
                        java.util.Arrays.stream(TARGETS).map(DropList.Item::new).toList(), target, index -> { target = index; menu.refreshPanel(); });
                hexField = menu.addPanelWidget(MenuStyle.field(x + 29, row(y, step, 2), size - 24, bh, Text.literal("Hex colour")));
                hexField.setMaxLength(9);
                hexField.setText(KrovexTheme.toHexColor(colour(), false));
                hexField.setChangedListener(value -> {
                    if (syncingHex) return;
                    try { setColour(KrovexTheme.parseHexColor(value)); }
                    catch (IllegalArgumentException ignored) {}
                });
                menu.addPanelWidget(new ColourSlider(left, row(y, step, 3), size, bh, 3));
                menu.addPanelWidget(new ColourSlider(left, row(y, step, 4), size, bh, 0));
                menu.addPanelWidget(new ColourSlider(left, row(y, step, 5), size, bh, 1));
                menu.addPanelWidget(new ColourSlider(left, row(y, step, 6), size, bh, 2));
            }
            case 1 -> {
                var fonts = KrovexFontRegistry.FAMILIES;
                fontList.button(menu, "Font:  ", left, row(y, step, 1), size, bh, fonts.stream().map(DropList.Item::new).toList(),
                        Math.max(0, fonts.indexOf(theme.fontFamily())), index -> {
                            save(theme.toBuilder().fontFamily(fonts.get(index)).build());
                            menu.refreshPanel();
                        });
                slider(menu, left, row(y, step, 2), size, bh, "Opacity", (theme.opacity() - .1) / .9,
                        value -> save(theme.toBuilder().opacity((float) (.1 + .9 * value)).build()));
                slider(menu, left, row(y, step, 3), size, bh, "Radius", theme.cornerRadius() / 24.0,
                        value -> save(theme.toBuilder().cornerRadius((int) (24 * value)).build()));
                var style = KrovexClientMod.stylePreference();
                menu.addPanelWidget(MenuStyle.toggle("Bubbly style", left, row(y, step, 4), size, bh, style.bubbly(), () -> {
                    style.setBubbly(!style.bubbly());
                    style.save(KrovexConfigPaths.stylePreferenceFile());
                    if (style.bubbly()) save(theme.toBuilder().fontFamily("Fredoka").glowEnabled(true).build()); // the bubbly look uses the rounded Fredoka font
                    menu.refreshPanel();
                }));
                slider(menu, left, row(y, step, 5), size, bh, "Font size", (theme.fontSize() - 8) / 24.0,
                        value -> save(theme.toBuilder().fontSize(8 + (int) (24 * value)).build()));
                slider(menu, left, row(y, step, 6), size, bh, "Spacing", theme.spacing() / 24.0,
                        value -> save(theme.toBuilder().spacing((int) (24 * value)).build()));
            }
            default -> {
                var style = KrovexClientMod.stylePreference();
                menu.addPanelWidget(MenuStyle.toggle("RGB wave", left, row(y, step, 1), size, bh, style.rainbow(), () -> {
                    style.setRainbow(!style.rainbow());
                    style.save(KrovexConfigPaths.stylePreferenceFile());
                    menu.refreshPanel();
                }));
                menu.addPanelWidget(new MenuStyle.Slider(left, row(y, step, 2), size, bh, Text.empty(), (style.rainbowSpeed() - .25) / 2.75) {
                    { updateMessage(); }
                    @Override protected void updateMessage() {
                        setMessage(Text.literal(String.format(java.util.Locale.ROOT, "Wave speed  %.1fx", .25 + value * 2.75)));
                    }
                    @Override protected void applyValue() {
                        style.setRainbowSpeed((float) (.25 + value * 2.75));
                        style.save(KrovexConfigPaths.stylePreferenceFile());
                    }
                });
                menu.addPanelWidget(MenuStyle.toggle("Blur", left, row(y, step, 3), size, bh, theme.blurEnabled(),
                        () -> { save(theme.toBuilder().blurEnabled(!theme.blurEnabled()).build()); menu.refreshPanel(); }));
                menu.addPanelWidget(MenuStyle.toggle("Glow", left, row(y, step, 4), size, bh, theme.glowEnabled(),
                        () -> { save(theme.toBuilder().glowEnabled(!theme.glowEnabled()).build()); menu.refreshPanel(); }));
                menu.addPanelWidget(MenuStyle.toggle("Animations", left, row(y, step, 5), size, bh, theme.animationsEnabled(),
                        () -> { save(theme.toBuilder().animationsEnabled(!theme.animationsEnabled()).build()); menu.refreshPanel(); }));
            }
        }
        // the three pages as always-visible tabs (no flipping through them)
        int tab = (size - 4) / 3;
        for (int i = 0; i < PAGES.length; i++) {
            int pageIndex = i;
            menu.addPanelWidget(MenuStyle.button(PAGES[i], left + i * (tab + 2), row(y, step, 7), tab, bh,
                    page == i ? MenuStyle.PURPLE : MenuStyle.CARD, () -> { page = pageIndex; menu.refreshPanel(); }));
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        return presetList.mouseClicked(mx, my, button) || targetList.mouseClicked(mx, my, button) || fontList.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        return presetList.mouseScrolled(mx, my, vertical) || targetList.mouseScrolled(mx, my, vertical) || fontList.mouseScrolled(mx, my, vertical);
    }

    @Override public void renderOverlay(DrawContext c, int mx, int my) {
        presetList.renderOverlay(c, mx, my);
        targetList.renderOverlay(c, mx, my);
        fontList.renderOverlay(c, mx, my);
    }

    /** Theme options back to the shipped defaults: the default preset (restored), its font, and the Bubbly / RGB wave style switches. Your saved custom themes are kept. */
    private void resetToDefault(KrovexMenuScreen menu) {
        var fresh = store.resetBuiltin(com.krovex.theme.ThemePresets.defaultTheme().id());
        store.setActiveThemeId(fresh.id());
        theme = store.getActiveTheme();
        var style = KrovexClientMod.stylePreference();
        style.setBubbly(false);
        style.setRainbow(false);
        style.setRainbowSpeed(1f);
        style.save(KrovexConfigPaths.stylePreferenceFile());
        // a HUD profile can carry its own appearance theme that "Apply" would switch back to: drop those too, so nothing re-applies the old look
        var profiles = KrovexClientMod.hudProfileStore();
        for (var profile : profiles.all()) profile.setAppearanceThemeId(null);
        profiles.save();
        page = 0;
        target = 0;
        KrovexFontRegistry.invalidate();
        menu.refreshPanel();
    }

    private static int row(int y, int step, int index) { return y + index * step + 3; }

    private static void slider(KrovexMenuScreen menu, int x, int y, int w, int h, String name, double initial,
                               java.util.function.DoubleConsumer apply) {
        menu.addPanelWidget(new MenuStyle.Slider(x, y, w, h, Text.literal(name), Math.clamp(initial, 0, 1)) {
            @Override protected void updateMessage() { setMessage(Text.literal(name + "  " + Math.round(value * 100) + "%")); }
            @Override protected void applyValue() { apply.accept(value); }
        });
    }

    // ------------------------------------------------------------------ drawing

    @Override public void render(DrawContext c, int x, int y, int w, int h, int mx, int my, float delta) {
        int left = x + w * 58 / 100, span = x + w - left;
        MenuStyle.card(c, left, y, span, h, 7, MenuStyle.EDGE, MenuStyle.CARD);
        MenuStyle.text(c, "Live Preview", left + 8, y + 7, MenuStyle.WHITE, 1);
        int step = Math.clamp((h - 5) / 8, 12, 23);
        int accent = MenuStyle.liveAccent(theme.accentColor());
        if (page == 0) {
            // the colour being edited, next to its hex box (which follows the sliders unless you are typing in it)
            MenuStyle.boxRaw(c, x + 6, row(y, step, 2) + 1, 17, step - 4, 3, colour() | 0xFF000000);
            if (hexField != null && !hexField.isFocused()) {
                String hex = KrovexTheme.toHexColor(colour(), false);
                if (!hex.equalsIgnoreCase(hexField.getText())) {
                    syncingHex = true;
                    hexField.setText(hex);
                    syncingHex = false;
                }
            }
        }
        int top = y + 22, height = Math.min(h - 30, 220);
        MenuStyle.cardRaw(c, left + 5, top, span - 10, height, 7, accent, theme.backgroundColor());
        MenuStyle.logo(c, left + 11, top + 5, 15);
        MenuStyle.text(c, "KROVEX", left + 29, top + 9, theme.textColor(), .96f);
        MenuStyle.boxRaw(c, left + 11, top + 25, span - 22, Math.max(12, height - 32), 5, theme.panelColor());
        MenuStyle.text(c, "Sample Card", left + 17, top + 31, theme.textColor(), .95f);
        if (height > 68) {
            MenuStyle.boxRaw(c, left + 16, top + height - 24, span - 32, 15, 4, accent);
            MenuStyle.text(c, "Sample Button", left + 21, top + height - 20, 0xFF16101E, .85f);
        }
    }
}

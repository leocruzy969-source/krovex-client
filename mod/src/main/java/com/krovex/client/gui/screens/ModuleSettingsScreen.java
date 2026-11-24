package com.krovex.client.gui.screens;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.features.KeybindConflicts;
import com.krovex.client.modules.ModuleDefinition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import com.krovex.client.gui.MenuStyle;

final class ModuleSettingsScreen extends Screen {
    /** Modules drawn on the HUD, which all get the Solid / Transparent background option. */
    private static final java.util.Set<String> HUD_WIDGETS = java.util.Set.of(
            "fps-counter", "ping", "player-ping", "coordinates", "keystrokes", "cps-counter",
            "potion-timers", "equipped-item-indicator");

    private final Screen parent;
    private final ModuleDefinition module;
    private int rowY, previewY;
    private final DropList list = new DropList();

    ModuleSettingsScreen(Screen parent, ModuleDefinition module) {
        super(MenuStyle.lettering(Text.literal(module.displayName()), true));
        this.parent = parent;
        this.module = module;
    }

    /** How many 24px rows of controls the module needs (before the background slider and Done). */
    private int controlRows() {
        return switch (module.id()) {
            case "keystrokes", "equipped-item-indicator" -> 1;
            case "particle-controls" -> 1;
            case "motion-controls" -> 4;
            case "weather-audio" -> 3;
            case "dynamic-hud" -> 3;
            case "background-fps" -> 1;
            case "puddles" -> 7;
            default -> 0;
        };
    }

    /** Height of the extra area under the controls (previews, conflict list). */
    private int extraHeight() {
        return switch (module.id()) {
            case "motion-controls" -> 62;
            case "keybind-conflicts" -> 92;
            default -> 0;
        };
    }

    @Override protected void init() {
        int x = width / 2 - 95;
        var store = KrovexClientMod.moduleStateStore();
        var state = store.stateFor(module.id());
        boolean hud = HUD_WIDGETS.contains(module.id());
        int rows = controlRows() + (hud ? 1 : 0) + 1;      // + Done
        int total = rows * 24 + extraHeight();
        rowY = Math.max(56, height / 2 - total / 2 + 14);
        int y = rowY;
        switch (module.id()) {
            case "keystrokes" -> {
                var profiles = KrovexClientMod.hudProfileStore();
                var layout = profiles.active().layoutFor(module.id());
                float[] scales = {.75f, 1f, 1.25f, 1.5f};
                int at = 1;
                for (int i = 0; i < scales.length; i++) if (Math.abs(scales[i] - layout.scale()) < .01f) at = i;
                list.button(this::addDrawableChild, "Scale:  ", x, y, 190, 20,
                        java.util.Arrays.stream(new String[] {"75%", "100%", "125%", "150%"}).map(DropList.Item::new).toList(), at, index -> {
                            layout.setScale(scales[index]);
                            profiles.save();
                        });
                y += 24;
            }
            case "particle-controls" -> {
                cycle(x, y, "Burst limit", state.setting(128),
                        java.util.List.of(32, 64, 128, 256), value -> MenuStyle.lettering(Text.literal(Integer.toString(value)), false));
                y += 24;
            }
            case "equipped-item-indicator" -> {
                cycle(x, y, "Show attack readiness", state.setting(1),
                        java.util.List.of(0, 1), value -> MenuStyle.lettering(Text.literal(value == 1 ? "On" : "Off"), false));
                y += 24;
            }
            case "motion-controls" -> {
                toggle(x, y, "Camera bob", "bob", true, "off", "on"); y += 24;
                toggle(x, y, "Hurt shake", "shake", true, "off", "on"); y += 24;
                toggle(x, y, "Sprint FOV stretch", "fov", false, "off", "on"); y += 24;
                toggle(x, y, "Portal wobble", "portal", false, "off", "on"); y += 24;
                previewY = y + 2;
                y += 62;
            }
            case "weather-audio" -> {
                percentSlider(x, y, "Rain volume", "rain", 1.0, 1.0); y += 24;
                percentSlider(x, y, "Thunder volume", "thunder", 1.0, 1.0); y += 24;
                addDrawableChild(MenuStyle.button("Test rain", x, y, 93, 20, MenuStyle.CARD, () -> playTest(net.minecraft.sound.SoundEvents.WEATHER_RAIN)));
                addDrawableChild(MenuStyle.button("Test thunder", x + 97, y, 93, 20, MenuStyle.CARD, () -> playTest(net.minecraft.sound.SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER)));
                y += 24;
            }
            case "dynamic-hud" -> {
                slider(x, y, "Fade after", "timeout", 1, 30, 5, v -> String.format(java.util.Locale.ROOT, "%.0f s idle", v)); y += 24;
                slider(x, y, "Fade time", "fade", .1, 3, .6, v -> String.format(java.util.Locale.ROOT, "%.1f s", v)); y += 24;
                slider(x, y, "Faded opacity", "floor", 0, .6, 0, v -> Math.round(v * 100) + "%"); y += 24;
            }
            case "puddles" -> {
                // two columns: the storm itself on the left, its colour grade on the right (defaults = the reference look)
                int lx = width / 2 - 200, rx = width / 2 + 10, top = y;
                slider(lx, y, "Sky brightness", "sky", 0, 1, .35, ModuleSettingsScreen::two); y += 24;
                slider(lx, y, "Wet reflections", "reflect", 0, 1, .85, ModuleSettingsScreen::two); y += 24;
                slider(lx, y, "Puddles", "density", 0, 1, .55, ModuleSettingsScreen::two); y += 24;
                slider(lx, y, "Haze", "haze", 0, 1, 1.0, ModuleSettingsScreen::two); y += 24;
                slider(lx, y, "Glow", "glow", 0, 1, .65, ModuleSettingsScreen::two); y += 24;
                flagSwitch(lx, y, "Rain", "rain", false); y += 24;
                flagSwitch(lx, y, "Always on (ignore weather)", "always", false); y += 24;
                int ry = top;
                flagSwitch(rx, ry, "Color grade", "grade", true); ry += 24;
                slider(rx, ry, "Saturation", "saturation", 0, 2, .65, ModuleSettingsScreen::two); ry += 24;
                slider(rx, ry, "Brightness", "brightness", 0, 2, .95, ModuleSettingsScreen::two); ry += 24;
                slider(rx, ry, "Contrast", "contrast", 0, 2, 1.05, ModuleSettingsScreen::two); ry += 24;
                slider(rx, ry, "Color tint", "tint", 0, 1, .30, ModuleSettingsScreen::two); ry += 24;
            }
            case "background-fps" -> {
                slider(x, y, "Background cap", "fps", 1, 60, 15, v -> Math.round(v) + " FPS"); y += 24;
            }
            case "keybind-conflicts" -> y += extraHeight();
            default -> { }
        }
        if (hud) {
            addDrawableChild(new MenuStyle.Slider(x, y, 190, 20, opacityLabel(state.backgroundOpacity()), state.backgroundOpacity() / 100.0) {
                @Override protected void updateMessage() { setMessage(opacityLabel((int) Math.round(value * 100))); }
                @Override protected void applyValue() { state.setBackgroundOpacity((int) Math.round(value * 100)); }
                @Override public void onRelease(net.minecraft.client.gui.Click click) { store.save(); }   // one write per drag, not one per pixel
            });
            y += 24;
        }
        addDrawableChild(MenuStyle.button("Done", x, y + 6, 190, 20, MenuStyle.LIME, this::close));
    }

    private static Text opacityLabel(int percent) {
        return Text.literal("Background " + percent + "%");
    }

    private void cycle(int x, int y, String name, int current, java.util.List<Integer> values,
                       java.util.function.Function<Integer, Text> label) {
        var store = KrovexClientMod.moduleStateStore();
        int initial = Math.max(0, values.indexOf(current));
        list.button(this::addDrawableChild, name + ":  ", x, y, 190, 20,
                values.stream().map(v -> new DropList.Item(label.apply(v).getString())).toList(), initial, index -> {
                    store.stateFor(module.id()).setSetting(values.get(index));
                    store.save();
                });
    }

    /** On/off switch kept in the module's named values; the effect it controls changes the very next frame. */
    private void toggle(int x, int y, String name, String key, boolean fallback, String offWord, String onWord) {
        var store = KrovexClientMod.moduleStateStore();
        boolean initial = store.stateFor(module.id()).flag(key, fallback);
        // a real switch (on = the effect is turned off), not a button you click through
        addDrawableChild(MenuStyle.toggle("Turn off " + name.toLowerCase(java.util.Locale.ROOT), x, y, 190, 20, initial, () -> {
            store.stateFor(module.id()).setFlag(key, !initial);
            store.save();
            clearAndInit();
        }));
    }

    private static String two(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }

    /** A plain on/off switch kept in the module's named values. */
    private void flagSwitch(int x, int y, String name, String key, boolean fallback) {
        var store = KrovexClientMod.moduleStateStore();
        boolean current = store.stateFor(module.id()).flag(key, fallback);
        addDrawableChild(MenuStyle.toggle(name, x, y, 190, 20, current, () -> {
            store.stateFor(module.id()).setFlag(key, !current);
            store.save();
            clearAndInit();
        }));
    }

    private void slider(int x, int y, String name, String key, double min, double max, double fallback,
                        java.util.function.Function<Double, String> format) {
        var store = KrovexClientMod.moduleStateStore();
        var state = store.stateFor(module.id());
        double current = state.value(key, fallback);
        addDrawableChild(new MenuStyle.Slider(x, y, 190, 20, Text.empty(), (current - min) / (max - min)) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Text.literal(name + ": " + format.apply(min + value * (max - min)))); }
            @Override protected void applyValue() { state.setValue(key, min + value * (max - min)); }
            @Override public void onRelease(net.minecraft.client.gui.Click click) { store.save(); }
        });
    }

    private void percentSlider(int x, int y, String name, String key, double fallback, double max) {
        slider(x, y, name, key, 0, max, fallback, v -> Math.round(v * 100) + "%");
    }

    /** Plays a weather sound through the normal sound system, so the Weather Audio volume applies to it exactly as in the game. */
    private void playTest(net.minecraft.sound.SoundEvent event) {
        if (client != null) client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ambient(event, 1f, 1f));
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height, 0x8A0B0714, 0xB00B0714);
        int rows = controlRows() + (HUD_WIDGETS.contains(module.id()) ? 1 : 0) + 1;
        int panelH = rows * 24 + extraHeight() + 66, panelW = module.id().equals("keybind-conflicts") ? 340 : module.id().equals("puddles") ? 440 : 230;
        int panelX = width / 2 - panelW / 2, panelY = rowY - 46;
        MenuStyle.glass(context, panelX, panelY, panelW, panelH, 12);
        MenuStyle.text(context, module.displayName(), panelX + 14, panelY + 11, MenuStyle.WHITE, 1.3f);
        MenuStyle.box(context, panelX + 14, panelY + 26, 22, 2, 1, MenuStyle.LIME);
        var blurb = textRenderer.getTextHandler().wrapLines(module.description(), (int) ((panelW - 28) * .92f), net.minecraft.text.Style.EMPTY);
        if (!blurb.isEmpty()) MenuStyle.text(context, blurb.getFirst().getString() + (blurb.size() > 1 ? "…" : ""), panelX + 14, panelY + 31, MenuStyle.MUTED, .85f);
        super.render(context, mouseX, mouseY, delta);
        int x = width / 2 - 95;
        if (module.id().equals("motion-controls")) drawMotionPreview(context, x);
        if (module.id().equals("keybind-conflicts")) drawConflicts(context, x);
        list.renderOverlay(context, mouseX, mouseY);
    }

    /** Four little tiles that move the way each effect moves, and hold still when that effect is switched off. */
    private void drawMotionPreview(DrawContext c, int x) {
        var state = KrovexClientMod.moduleStateStore().stateFor(module.id());
        double t = System.currentTimeMillis() / 1000.0;
        String[] names = {"Bob", "Shake", "FOV", "Portal"};
        String[] keys = {"bob", "shake", "fov", "portal"};
        boolean[] defaults = {true, true, false, false};
        for (int i = 0; i < 4; i++) {
            int tx = x + i * 49, ty = previewY;
            boolean moving = !state.flag(keys[i], defaults[i]);      // "disabled" = the tile stays still
            MenuStyle.card(c, tx, ty, 45, 44, 5, moving ? MenuStyle.LIME : MenuStyle.EDGE, 0xC0201A33);
            c.enableScissor(tx + 2, ty + 2, tx + 43, ty + 30);
            int cx = tx + 22, cy = ty + 16;
            switch (i) {
                case 0 -> {   // the horizon line sways up and down
                    int dy = moving ? (int) Math.round(Math.sin(t * 6) * 4) : 0;
                    c.fill(tx + 2, cy + dy, tx + 43, ty + 30, 0xFF3F7F3A);
                    c.fill(tx + 2, ty + 2, tx + 43, cy + dy, 0xFF6FA8E0);
                }
                case 1 -> {   // the whole picture jitters
                    int dx = moving ? (int) Math.round(Math.sin(t * 37) * 2) : 0, dy = moving ? (int) Math.round(Math.cos(t * 43) * 2) : 0;
                    c.fill(tx + 6 + dx, ty + 6 + dy, tx + 39 + dx, ty + 27 + dy, 0xFFC8423A);
                }
                case 2 -> {   // the picture stretches in and out
                    int grow = moving ? (int) Math.round((Math.sin(t * 4) * .5 + .5) * 6) : 0;
                    c.fill(cx - 8 - grow, cy - 6 - grow / 2, cx + 8 + grow, cy + 6 + grow / 2, 0xFFE8B83A);
                }
                default -> {  // a wobbling ring
                    for (int a = 0; a < 360; a += 20) {
                        double wob = moving ? Math.sin(t * 5 + a * .05) * 3 : 0;
                        int px = cx + (int) Math.round(Math.cos(Math.toRadians(a)) * (11 + wob)), py = cy + (int) Math.round(Math.sin(Math.toRadians(a)) * (8 + wob));
                        c.fill(px, py, px + 2, py + 2, 0xFFB04CFF);
                    }
                }
            }
            c.disableScissor();
            MenuStyle.text(c, names[i], tx + 4, ty + 33, moving ? MenuStyle.WHITE : MenuStyle.MUTED, .85f);
        }
    }

    private void drawConflicts(DrawContext c, int x) {
        MenuStyle.card(c, x - 60, rowY, 310, extraHeight() - 6, 6, MenuStyle.EDGE, 0xC0201A33);
        var conflicts = KeybindConflicts.current();
        if (conflicts.isEmpty()) {
            MenuStyle.text(c, "No conflicts: no other keybind uses a Krovex key.", x - 50, rowY + 8, MenuStyle.LIME, .95f);
            MenuStyle.text(c, "When you rebind a Krovex key to one that is taken, the menu shows both actions first.", x - 50, rowY + 24, MenuStyle.MUTED, .8f);
            return;
        }
        MenuStyle.text(c, conflicts.size() + (conflicts.size() == 1 ? " conflict" : " conflicts") + ": the same key does two things", x - 50, rowY + 7, 0xFFFFC857, .95f);
        for (int i = 0; i < Math.min(4, conflicts.size()); i++) {
            var conflict = conflicts.get(i);
            String line = conflict.ours().getBoundKeyLocalizedText().getString() + "   " + KeybindConflicts.describe(conflict.ours())
                    + "   with   " + KeybindConflicts.describe(conflict.other()) + " (" + KeybindConflicts.owner(conflict.other()) + ")";
            MenuStyle.text(c, textRenderer.trimToWidth(line, 292), x - 50, rowY + 22 + i * 14, MenuStyle.WHITE, .85f);
        }
    }

    @Override public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        return list.mouseClicked(click.x(), click.y(), click.button()) || super.mouseClicked(click, doubled);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return list.mouseScrolled(mouseX, mouseY, vertical) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void close() {
        if (client != null) client.setScreen(parent);
    }
}

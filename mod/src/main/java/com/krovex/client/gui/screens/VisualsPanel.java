package com.krovex.client.gui.screens;

import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import com.krovex.client.visuals.ColorGrade;
import com.krovex.client.visuals.ColorGradeStore;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Color Grading tab: four sliders that change the whole game view live (the shader reads them every frame, so there is
 * no confirm step), a switch, and named presets. The right-hand card shows the same grade applied to a strip of sample
 * colours, so you can see the effect even while this menu covers the world.
 */
public final class VisualsPanel implements KrovexSubPanel {
    private static final int[] SAMPLE_COLOURS = {0xFF6FB4F0, 0xFF3F8F3C, 0xFFD9B27C, 0xFFE8E8E8, 0xFFC8423A, 0xFFE8B83A, 0xFF5A3F7C, 0xFF20242C};

    private final ColorGradeStore store = ColorGradeStore.get();
    private static String presetName = "";
    private String status = "";
    private TextFieldWidget nameField;
    private final DropList presetList = new DropList();

    public VisualsPanel(Screen parent) {}

    @Override public void onHidden() { store.save(); }

    @Override public boolean mouseClicked(double mx, double my, int button) { return presetList.mouseClicked(mx, my, button); }
    @Override public boolean mouseScrolled(double mx, double my, double vertical) { return presetList.mouseScrolled(mx, my, vertical); }
    @Override public void renderOverlay(DrawContext c, int mx, int my) { presetList.renderOverlay(c, mx, my); }

    /** One slider on a min..max range. Changes apply on the next frame; the file is written when the mouse is released. */
    private abstract static class GradeSlider extends MenuStyle.Slider {
        private final String name;
        private final float min, max;
        private final java.util.function.Consumer<Float> apply;

        GradeSlider(int x, int y, int w, int h, String name, float min, float max, float current, java.util.function.Consumer<Float> apply) {
            super(x, y, w, h, Text.empty(), (current - min) / (max - min));
            this.name = name;
            this.min = min;
            this.max = max;
            this.apply = apply;
            updateMessage();
        }

        private float real() { return min + (float) value * (max - min); }

        @Override protected void updateMessage() {
            float v = min + (float) value * (max - min);
            setMessage(Text.literal(name + "  " + (name.equals("Saturation") || name.equals("Contrast")
                    ? Math.round(v * 100) + "%" : String.format(java.util.Locale.ROOT, "%+.0f", v * 100))));
        }

        @Override protected void applyValue() { apply.accept(real()); }
    }

    private void slider(KrovexMenuScreen menu, int x, int y, int w, int h, String name, float min, float max, float current,
                        java.util.function.Consumer<Float> apply) {
        menu.addPanelWidget(new GradeSlider(x, y, w, h, name, min, max, current, apply) {
            @Override public void onRelease(net.minecraft.client.gui.Click click) {
                store.save();
                status = "";
            }
        });
    }

    @Override public void createWidgets(Screen host, int x, int y, int w, int h) {
        var menu = (KrovexMenuScreen) host;
        int controls = w * 58 / 100, size = Math.max(60, controls - 12);
        int step = Math.clamp((h - 5) / 9, 14, 23), bh = step - 2, left = x + 5;
        int row = 0;
        menu.addPanelWidget(MenuStyle.toggle("Color grading", left, y + 3, size, bh, store.enabled(), () -> {
            store.setEnabled(!store.enabled());
            store.save();
            menu.refreshPanel();
        }));
        row++;
        // presets: a list of every look (the three built-ins first, then your own)
        List<ColorGradeStore.Look> presets = store.presets();
        int current = -1;
        for (int i = 0; i < presets.size(); i++) if (presets.get(i).name().equalsIgnoreCase(store.activePreset())) current = i;
        presetList.button(menu, "Preset:  ", left, y + 3 + row * step, size, bh,
                presets.stream().map(look -> new DropList.Item(look.name())).toList(), current, index -> {
                    store.apply(presets.get(index));
                    store.save();
                    menu.refreshPanel();
                });
        row++;
        slider(menu, left, y + 3 + row++ * step, size, bh, "Saturation", 0f, 2f, store.saturation(), v -> { store.setSaturation(v); store.setEnabled(true); });
        slider(menu, left, y + 3 + row++ * step, size, bh, "Contrast", .5f, 1.5f, store.contrast(), v -> { store.setContrast(v); store.setEnabled(true); });
        slider(menu, left, y + 3 + row++ * step, size, bh, "Warmth", -1f, 1f, store.warmth(), v -> { store.setWarmth(v); store.setEnabled(true); });
        slider(menu, left, y + 3 + row++ * step, size, bh, "Shadows", -1f, 1f, store.shadows(), v -> { store.setShadows(v); store.setEnabled(true); });
        // save under a name / delete a saved one
        nameField = menu.addPanelWidget(MenuStyle.field(left, y + 3 + row * step, size - 78, bh, Text.literal("Preset name")));
        nameField.setPlaceholder(Text.literal("Name this look…"));
        nameField.setMaxLength(24);
        nameField.setText(presetName);
        nameField.setChangedListener(value -> presetName = value);
        menu.addPanelWidget(MenuStyle.button("Save", left + size - 74, y + 3 + row * step, 36, bh, MenuStyle.LIME, () -> {
            if (store.isBuiltIn(presetName.strip())) status = "Built-in presets can't be replaced: use another name.";
            else if (store.saveCurrentAs(presetName)) { status = "Saved \"" + presetName.strip() + "\"."; menu.refreshPanel(); }
            else status = "Type a name first.";
        }));
        menu.addPanelWidget(MenuStyle.button("Delete", left + size - 36, y + 3 + row * step, 36, bh, MenuStyle.CARD, () -> {
            String active = store.activePreset();
            if (!active.isEmpty() && !store.isBuiltIn(active) && store.delete(active)) { status = "Deleted \"" + active + "\"."; menu.refreshPanel(); }
            else status = "Pick one of your own presets to delete it.";
        }));
        row++;
        menu.addPanelWidget(MenuStyle.button("Reset to default", left, y + 3 + row * step, size, bh, MenuStyle.CARD, () ->
                menu.askConfirm("Are you sure you want to reset your options for color grading to default?", () -> {
                    store.apply(ColorGradeStore.BUILT_IN.getFirst());
                    store.save();
                    menu.refreshPanel();
                })));
    }

    /** The shader's maths on one colour, so the preview strip matches what the game shows. */
    private int graded(int argb) {
        float r = (argb >> 16 & 255) / 255f, g = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
        float luma = .2126f * r + .7152f * g + .0722f * b;
        float dark = 1f - smooth(0f, .55f, luma);
        r += store.shadows() * .18f * dark; g += store.shadows() * .18f * dark; b += store.shadows() * .18f * dark;
        r = luma + (r - luma) * store.saturation(); g = luma + (g - luma) * store.saturation(); b = luma + (b - luma) * store.saturation();
        r = (r - .5f) * store.contrast() + .5f; g = (g - .5f) * store.contrast() + .5f; b = (b - .5f) * store.contrast() + .5f;
        r *= 1f + .14f * store.warmth(); g *= 1f + .03f * store.warmth(); b *= 1f - .14f * store.warmth();
        return 0xFF000000 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static int clamp(float v) { return Math.clamp(Math.round(v * 255f), 0, 255); }

    @Override public void render(DrawContext c, int x, int y, int w, int h, int mx, int my, float delta) {
        int left = x + w * 58 / 100, span = x + w - left;
        MenuStyle.card(c, left, y, span, h, 7, MenuStyle.EDGE, MenuStyle.CARD);
        MenuStyle.text(c, "Live Preview", left + 8, y + 7, MenuStyle.WHITE, 1);
        MenuStyle.text(c, store.enabled() ? "Applied to the game view right now" : "Switched off", left + 8, y + 20, MenuStyle.MUTED, .85f);
        // before / after strips of sample colours
        int stripW = span - 16, cell = Math.max(4, stripW / SAMPLE_COLOURS.length), top = y + 40;
        MenuStyle.text(c, "Original", left + 8, top, MenuStyle.MUTED, .85f);
        for (int i = 0; i < SAMPLE_COLOURS.length; i++) MenuStyle.boxRaw(c, left + 8 + i * cell, top + 11, cell - 2, 22, 3, SAMPLE_COLOURS[i]);
        MenuStyle.text(c, "With grading", left + 8, top + 40, MenuStyle.MUTED, .85f);
        for (int i = 0; i < SAMPLE_COLOURS.length; i++) MenuStyle.boxRaw(c, left + 8 + i * cell, top + 51, cell - 2, 22, 3, store.enabled() ? graded(SAMPLE_COLOURS[i]) : SAMPLE_COLOURS[i]);
        // a little scene: sky, hills, water
        int sceneTop = top + 84, sceneH = Math.max(20, Math.min(70, h - (sceneTop - y) - 30));
        for (int pass = 0; pass < 2; pass++) {
            int sx = left + 8 + pass * (stripW / 2 + 2), sw = stripW / 2 - 2;
            int[] sky = {0xFF86C5F5, 0xFFF4D6A0}, hill = {0xFF3C7A38, 0xFF2B5A2C}, water = {0xFF3A7CC4};
            boolean on = pass == 1 && store.enabled();
            c.fillGradient(sx, sceneTop, sx + sw, sceneTop + sceneH * 6 / 10, on ? graded(sky[0]) : sky[0], on ? graded(sky[1]) : sky[1]);
            c.fillGradient(sx, sceneTop + sceneH * 6 / 10, sx + sw, sceneTop + sceneH * 8 / 10, on ? graded(hill[0]) : hill[0], on ? graded(hill[1]) : hill[1]);
            c.fill(sx, sceneTop + sceneH * 8 / 10, sx + sw, sceneTop + sceneH, on ? graded(water[0]) : water[0]);
        }
        if (!status.isEmpty()) MenuStyle.text(c, status, left + 8, y + h - 14, MenuStyle.MUTED, .88f);
        if (ColorGrade.failed()) MenuStyle.text(c, "Grading hit a graphics error and switched itself off.", left + 8, y + h - 26, 0xFFFF8080, .85f);
    }
}

package com.krovex.client.hud;

import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import com.krovex.client.gui.MenuStyle;

public final class HudEditScreen extends Screen {
    private final HudProfileStore profileStore;
    private final HudProfile profile;
    private final Screen parent;
    private final LinkedHashMap<String, Rectangle> bounds = new LinkedHashMap<>();
    private String dragging;
    private Rectangle dragBounds;
    private int offsetX, offsetY;
    private boolean dirty;
    private String selected;
    private static final float MIN_SCALE = .5f, MAX_SCALE = 3f, STEP = .1f;

    public HudEditScreen(HudProfileStore profileStore) { this(profileStore, null); }

    public HudEditScreen(HudProfileStore profileStore, Screen parent) {
        super(MenuStyle.lettering(Text.literal("Krovex HUD Editor"), true));
        this.profileStore = profileStore;
        this.profile = profileStore.active();
        this.parent = parent;
    }

    @Override protected void init() {
        addDrawableChild(ButtonWidget.builder(MenuStyle.lettering(Text.literal("Save & Done"), false), button -> close())
                .dimensions(width / 2 - 50, height - 28, 100, 20).build());
        addDrawableChild(MenuStyle.button("-", width / 2 + 60, height - 28, 22, 20, MenuStyle.CARD, () -> resize(selected, -STEP)));
        addDrawableChild(MenuStyle.button("+", width / 2 + 86, height - 28, 22, 20, MenuStyle.CARD, () -> resize(selected, STEP)));
    }

    /** Grows or shrinks one widget (drawAt keeps it on-screen), remembered in the profile. */
    private void resize(String id, float delta) {
        if (id == null) return;
        var layout = profile.layoutFor(id);
        float next = Math.clamp(Math.round((layout.scale() + delta) * 20f) / 20f, MIN_SCALE, MAX_SCALE);
        if (next == layout.scale()) return;
        layout.setScale(next);
        dirty = true;
        save();
    }

    private String widgetAt(double x, double y) {
        for (var item : bounds.sequencedEntrySet().reversed()) if (item.getValue().contains(x, y)) return item.getKey();
        return null;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        String target = widgetAt(mouseX, mouseY);
        if (target == null) target = selected;
        if (target == null || vertical == 0) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        selected = target;
        resize(target, vertical > 0 ? STEP : -STEP);
        return true;
    }

    @Override public boolean shouldPause() { return false; }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        bounds.clear();
        if (client != null) KrovexHudOverlay.renderEditor(context, client, profile, bounds);
        for (var item : bounds.entrySet()) {
            Rectangle box = item.getValue();
            int x = box.x, y = box.y, right = box.x + box.width, bottom = box.y + box.height;
            boolean chosen = item.getKey().equals(selected);
            int color = chosen || box.contains(mouseX, mouseY) || item.getKey().equals(dragging) ? 0xFFAB78FF : 0x70FFFFFF;
            context.fill(x - 1, y - 1, right + 1, y, color);
            context.fill(x - 1, bottom, right + 1, bottom + 1, color);
            context.fill(x - 1, y, x, bottom, color);
            context.fill(right, y, right + 1, bottom, color);
        }
        context.drawCenteredTextWithShadow(textRenderer, MenuStyle.lettering(Text.literal(
                "Drag to move  ·  scroll or - / + to resize"), false), width / 2, height - 42, 0xFFFFFFFF);
        String label = selected == null ? "Click a widget to select it"
                : "Size " + Math.round(profile.layoutFor(selected).scale() * 100) + "%";
        context.drawTextWithShadow(textRenderer, MenuStyle.lettering(Text.literal(label), false), width / 2 + 114, height - 22, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(Click click, boolean doubleClick) {
        if (super.mouseClicked(click, doubleClick)) return true;
        if (click.button() != 0) return false;
        for (var item : bounds.sequencedEntrySet().reversed()) {
            var box = item.getValue();
            if (!box.contains(click.x(), click.y())) continue;
            dragging = item.getKey();
            selected = dragging;
            dragBounds = box;
            offsetX = (int) click.x() - box.x;
            offsetY = (int) click.y() - box.y;
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging == null) return super.mouseDragged(click, deltaX, deltaY);
        int x = Math.clamp((int) click.x() - offsetX, 0, Math.max(0, width - dragBounds.width));
        int y = Math.clamp((int) click.y() - offsetY, 0, Math.max(0, height - dragBounds.height));
        profile.layoutFor(dragging).dragTo(x, y, width, height, dragBounds.width, dragBounds.height, 12);
        dirty = true;
        return true;
    }

    @Override public boolean mouseReleased(Click click) {
        if (dragging == null) return super.mouseReleased(click);
        dragging = null;
        save();
        return true;
    }

    private void save() {
        if (!dirty) return;
        profileStore.save();
        dirty = false;
    }

    @Override public void removed() {
        save();
        HudEditSession.end();
        super.removed();
    }

    @Override public void close() {
        save();
        if (client != null) client.setScreen(parent);
    }
}

package com.krovex.client.gui.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/**
 * One tab's content inside {@link com.krovex.client.gui.KrovexMenuScreen}'s right-hand
 * area. Any interactive widgets (buttons, checkboxes, sliders) MUST be registered via
 * {@link #createWidgets} using the host screen's own {@code addDrawableChild}, not
 * drawn manually in {@link #render} -- Minecraft only routes mouse/keyboard input to
 * widgets that are actual children of the {@link Screen}. KrovexMenuScreen removes
 * the previous panel's widgets (tracking what each panel added) before calling
 * {@code createWidgets} on the newly selected one.
 */
public interface KrovexSubPanel {
    /** Adds this panel's interactive widgets to {@code hostScreen} for the given content-area bounds. */
    void createWidgets(Screen hostScreen, int x, int y, int width, int height);

    /** Draws anything that isn't an interactive widget (labels, previews, the crosshair/sky preview, etc). */
    void render(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY, float delta);

    /** Called every time the sidebar switches to this tab, so it can refresh live data (playtime, now-playing, etc). */
    default void onShown() {}

    default void onHidden() {}

    /** Mouse hooks for panels that do their own dragging (the texture list). Return true to swallow the event. */
    default boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    default boolean mouseDragged(double mouseX, double mouseY, int button) { return false; }
    default boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
    default boolean mouseScrolled(double mouseX, double mouseY, double vertical) { return false; }

    /** Files dropped onto the game window while this tab is showing (the texture list imports packs from them). */
    default void filesDropped(java.util.List<java.nio.file.Path> paths) {}

    /** Drawn above every widget (open drop-down lists). */
    default void renderOverlay(DrawContext context, int mouseX, int mouseY) {}
}

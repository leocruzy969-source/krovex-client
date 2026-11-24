package com.krovex.client.gui;

import static com.krovex.client.gui.MenuStyle.lettering;

import com.krovex.client.KrovexClientMod;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.LockButtonWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Gives vanilla's main Options screen the same Krovex look as the pause screen: a glass panel with a
 * header, and every button / cycling button / slider repainted as a Krovex card. The real vanilla
 * widgets stay in place (nearly transparent) so all the clicking, dragging, keyboard and narration
 * behaviour is untouched. Follows the same on/off switch as the pause screen.
 *
 * Only this top-level screen is restyled; the sub-screens it opens (Video Settings, Controls, ...)
 * are still vanilla.
 */
public final class OptionsStyle {
    private static final Field SLIDER_VALUE = findSliderValueField();

    private OptionsStyle() {}

    /** Called from the screen-init hook for vanilla's OptionsScreen. */
    public static void apply(Screen screen) {
        if (!KrovexClientMod.escapeMenuSettings().customPauseAppearanceEnabled()) return;
        List<ClickableWidget> styled = new ArrayList<>();
        for (ClickableWidget widget : Screens.getButtons(screen)) {
            boolean slider = widget instanceof SliderWidget;
            boolean button = widget instanceof PressableWidget && !(widget instanceof LockButtonWidget)
                    && !widget.getMessage().getString().isEmpty();
            if (!slider && !button) continue;
            widget.setAlpha(.02f); // must stay above ~1.5% or Minecraft draws the label fully opaque
            styled.add(widget);
        }
        if (styled.isEmpty()) return;
        ScreenEvents.afterBackground(screen).register((s, c, mx, my, delta) -> c.fill(0, 0, s.width, s.height, 0x6612091F));
        ScreenEvents.afterRender(screen).register((s, c, mx, my, delta) -> paint(s, c, styled));
    }

    private static void paint(Screen screen, DrawContext c, List<ClickableWidget> widgets) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = 0, maxY = 0;
        for (ClickableWidget w : widgets) {
            if (isDone(w)) continue; // the Done button sits below the panel on its own
            minX = Math.min(minX, w.getX());
            minY = Math.min(minY, w.getY());
            maxX = Math.max(maxX, w.getX() + w.getWidth());
            maxY = Math.max(maxY, w.getY() + w.getHeight());
        }
        int left = Math.max(4, minX - 20), right = Math.min(screen.width - 4, maxX + 20);
        int top = Math.max(4, Math.min(6, minY - 34)), bottom = Math.min(screen.height - 4, maxY + 14);

        // glass panel, opaque enough to hide vanilla's own "Options" title and widget art underneath
        for (int i = 3; i > 0; i--) {
            MenuStyle.card(c, left - i, top - i, right - left + 2 * i, bottom - top + 2 * i, 10 + i, 0x157D42DC, 0);
        }
        MenuStyle.gradientCard(c, left, top, right - left, bottom - top, 10, 0xDDAD7AE3, 0xF22B2050, 0xF2150E2C);
        c.fillGradient(left + 8, top + 2, right - 8, top + 24, 0x494D3667, 0x004D3667);
        MenuStyle.logo(c, left + 12, top + 3, 18);
        MenuStyle.text(c, "KROVEX  -  Game options", left + 34, top + 7, MenuStyle.WHITE, 1f);

        var font = MinecraftClient.getInstance().textRenderer;
        for (ClickableWidget w : widgets) {
            boolean hover = w.isHovered() || w.isFocused();
            boolean done = isDone(w);
            int edge = hover && w.active ? MenuStyle.HOVER_EDGE : MenuStyle.EDGE;
            if (done) {
                if (hover) MenuStyle.glow(c, w.getX(), w.getY(), w.getWidth(), w.getHeight(), 5, MenuStyle.LIME);
                MenuStyle.gradientCard(c, w.getX(), w.getY(), w.getWidth(), w.getHeight(), 5, 0xFFEAFFA8,
                        hover ? 0xFFE3FF86 : 0xFFD4FF5C, hover ? 0xFFB6F52F : 0xFFA4E818);
            } else {
                if (hover && w.active) MenuStyle.glow(c, w.getX(), w.getY(), w.getWidth(), w.getHeight(), 5, MenuStyle.VIOLET);
                int base = !w.active ? MenuStyle.PANEL : 0xFF2B2145;
                MenuStyle.gradientCard(c, w.getX(), w.getY(), w.getWidth(), w.getHeight(), 5, edge,
                        MenuStyle.shade(base, hover ? 1.55f : 1.25f), MenuStyle.shade(base, hover ? 1.2f : .92f));
            }
            if (w instanceof SliderWidget slider) {
                int track = w.getWidth() - 4, filled = (int) Math.round(Math.clamp(sliderValue(slider), 0, 1) * track);
                if (filled > 1) c.fillGradient(w.getX() + 2, w.getY() + 2, w.getX() + 2 + filled, w.getY() + w.getHeight() - 2, MenuStyle.VIOLET, 0xFF7A2CC8);
                if (filled > 0) MenuStyle.box(c, w.getX() + 1 + Math.max(filled - 1, 1), w.getY() + 3, 3, w.getHeight() - 6, 1, MenuStyle.WHITE);
            }
            Text label = lettering(w.getMessage(), done);
            int textWidth = font.getWidth(label);
            int color = done && w.active ? 0xFF17101E : w.active ? MenuStyle.WHITE : MenuStyle.MUTED;
            c.enableScissor(w.getX() + 2, w.getY(), w.getX() + w.getWidth() - 2, w.getY() + w.getHeight());
            c.drawText(font, label, w.getX() + Math.max(2, (w.getWidth() - textWidth) / 2),
                    w.getY() + (w.getHeight() - 8) / 2, color, false);
            c.disableScissor();
        }
    }

    private static boolean isDone(ClickableWidget w) { return w.getMessage().equals(ScreenTexts.DONE); }

    private static double sliderValue(SliderWidget slider) {
        if (SLIDER_VALUE == null) return .5;
        try { return SLIDER_VALUE.getDouble(slider); } catch (IllegalAccessException e) { return .5; }
    }

    /** SliderWidget.value is protected and the runtime name differs from the dev name, so resolve it by mapping. */
    private static Field findSliderValueField() {
        try {
            // at runtime the game uses intermediary names (field_22753 = SliderWidget.value); in a dev
            // environment it is still called "value"
            Field field;
            try { field = SliderWidget.class.getDeclaredField("field_22753"); }
            catch (NoSuchFieldException e) { field = SliderWidget.class.getDeclaredField("value"); }
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null; // slider fill just shows half-way instead of the real value
        }
    }
}

package com.krovex.client.gui.screens;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.KrovexConfigPaths;
import com.krovex.client.config.PopupPreference;
import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public final class SettingsPanel implements KrovexSubPanel {
    public SettingsPanel(Screen parent) {}

    @Override public void createWidgets(Screen host, int x, int y, int w, int h) {
        var menu = (KrovexMenuScreen) host;
        var popup = PopupPreference.load(KrovexConfigPaths.popupPreferenceFile());
        var escape = KrovexClientMod.escapeMenuSettings();
        toggle(menu, x + w - 40, y + 16, popup.showQuickPopup(), () -> {
            popup.setShowQuickPopup(!popup.showQuickPopup());
            popup.save(KrovexConfigPaths.popupPreferenceFile());
        });
        toggle(menu, x + w - 40, y + 59, escape.customPauseAppearanceEnabled(), () -> {
            escape.setCustomPauseAppearanceEnabled(!escape.customPauseAppearanceEnabled());
            escape.save(KrovexConfigPaths.escapeMenuSettingsFile());
        });
        var key = KrovexClientMod.KROVEX_MENU_KEY;
        var holder = new net.minecraft.client.gui.widget.ButtonWidget[1];
        holder[0] = menu.addPanelWidget(MenuStyle.button(key.getBoundKeyLocalizedText().getString() + "  ⌄",
                x + w - 81, y + 101, 72, 19, MenuStyle.CARD, () -> menu.beginKeyCapture(key, holder[0])));
    }

    private static void toggle(KrovexMenuScreen menu, int x, int y, boolean enabled, Runnable change) {
        menu.addPanelWidget(MenuStyle.toggle("", x, y, 30, 16, enabled, () -> { change.run(); menu.refreshPanel(); }));
    }

    @Override public void render(DrawContext c, int x, int y, int w, int h, int mx, int my, float delta) {
        MenuStyle.card(c, x, y + 4, w, 130, 7, MenuStyle.EDGE, MenuStyle.CARD);
        row(c, x, y + 14, "Show quick popup", "Show a compact Open Menu popup.");
        row(c, x, y + 57, "Custom pause appearance", "KROVEX style for the pause and options screens.");
        row(c, x, y + 99, "Menu shortcut", "Key to open the KROVEX menu.");
        c.fill(x + 8, y + 46, x + w - 8, y + 47, MenuStyle.EDGE);
        c.fill(x + 8, y + 88, x + w - 8, y + 89, MenuStyle.EDGE);
    }

    private static void row(DrawContext c, int x, int y, String title, String detail) {
        MenuStyle.text(c, title, x + 13, y, MenuStyle.WHITE, 1.15f);
        MenuStyle.text(c, detail, x + 13, y + 15, MenuStyle.MUTED, .92f);
    }
}

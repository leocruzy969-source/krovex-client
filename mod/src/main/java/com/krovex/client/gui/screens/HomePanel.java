package com.krovex.client.gui.screens;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.gui.KrovexFontRegistry;
import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import com.krovex.client.modules.ModuleRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public final class HomePanel implements KrovexSubPanel {
    private static final int BUTTON_H = 44;

    public HomePanel(Screen parentScreen) {}

    private static boolean tiles(int h) { return h >= 236; }

    private static int themeY(int y, int h) { return tiles(h) ? y + 172 : y + 92; }

    @Override public void createWidgets(Screen host, int x, int y, int w, int h) {
        var menu = (KrovexMenuScreen) host;
        int cell = (w - 12) / 3;
        menu.addPanelWidget(MenuStyle.button("Edit HUD  ›", x, y + 24, cell, BUTTON_H, MenuStyle.CARD, () -> menu.showTab("HUD_EDITOR")));
        menu.addPanelWidget(MenuStyle.button("Browse mods  ›", x + cell + 6, y + 24, cell, BUTTON_H, MenuStyle.CARD, () -> menu.showTab("MODS")));
        menu.addPanelWidget(MenuStyle.button("Resume", x + 2 * (cell + 6), y + 24, cell, BUTTON_H, MenuStyle.LIME, menu::close));
        menu.addPanelWidget(MenuStyle.button("Edit theme  ›", x + w - 84, themeY(y, h) + 15, 76, 20, MenuStyle.CARD, () -> menu.showTab("THEME")));
    }

    @Override public void render(DrawContext c, int x, int y, int w, int h, int mx, int my, float delta) {
        MenuStyle.text(c, "Quick Actions", x, y + 6, MenuStyle.WHITE, 1.16f);
        int cell = (w - 12) / 3;
        MenuStyle.text(c, "Customize your HUD", x + 6, y + 24 + BUTTON_H + 4, MenuStyle.MUTED, .88f);
        MenuStyle.text(c, "Manage installed mods", x + cell + 12, y + 24 + BUTTON_H + 4, MenuStyle.MUTED, .88f);
        MenuStyle.text(c, "Return to game", x + 2 * (cell + 6) + 6, y + 24 + BUTTON_H + 4, MenuStyle.MUTED, .88f);

        if (tiles(h)) {
            MenuStyle.text(c, "At a glance", x, y + 92, MenuStyle.WHITE, 1.16f);
            var client = MinecraftClient.getInstance();
            var store = KrovexClientMod.moduleStateStore();
            long enabled = ModuleRegistry.all().stream().filter(m -> store.stateFor(m.id()).enabled()).count();
            var sky = KrovexClientMod.skyPresetStore();
            String[][] stats = {
                    {"PERFORMANCE", client.getCurrentFps() + " FPS"},
                    {"MODULES ON", enabled + " of " + ModuleRegistry.all().size()},
                    {"SKY", sky.enabled() ? sky.active().name() : "Vanilla Sky"},
                    {"FONT", KrovexFontRegistry.activeFamily()}};
            int tile = (w - 3 * 6) / 4;
            for (int i = 0; i < stats.length; i++) {
                int tx = x + i * (tile + 6), ty = y + 112;
                MenuStyle.card(c, tx, ty, tile, 50, 8, MenuStyle.EDGE, MenuStyle.CARD);
                MenuStyle.box(c, tx + 8, ty + 8, 3, 34, 1, i % 2 == 0 ? MenuStyle.LIME : MenuStyle.VIOLET);
                MenuStyle.text(c, stats[i][0], tx + 17, ty + 9, MenuStyle.MUTED, .85f);
                var font = client.textRenderer;
                String value = font.trimToWidth(stats[i][1], tile - 24);
                MenuStyle.text(c, value, tx + 17, ty + 25, MenuStyle.WHITE, 1.16f);
            }
        }
        int ty = themeY(y, h);
        MenuStyle.card(c, x, ty, w, 50, 8, MenuStyle.EDGE, MenuStyle.CARD);
        var theme = KrovexClientMod.themeStore().getActiveTheme();
        MenuStyle.gradientCard(c, x + 8, ty + 8, 34, 34, 7, 0xFFC17AFE, MenuStyle.withAlpha(theme.accentColor(), 255), 0xFF3A1A78);
        MenuStyle.text(c, "ACTIVE THEME", x + 52, ty + 10, MenuStyle.MUTED, .85f);
        MenuStyle.text(c, theme.name(), x + 52, ty + 25, MenuStyle.WHITE, 1.16f);
    }
}

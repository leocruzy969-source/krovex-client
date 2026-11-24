package com.krovex.client.gui.screens;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import com.krovex.client.hud.HudEditScreen;
import com.krovex.client.hud.HudElementLayout;
import com.krovex.client.hud.HudProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public final class HudEditorPanel implements KrovexSubPanel {
    private final Screen parent;
    private final com.krovex.client.hud.HudProfileStore profiles = KrovexClientMod.hudProfileStore();
    private final com.krovex.theme.ThemeStore themes = KrovexClientMod.themeStore();
    private HudProfile selected = profiles.active();
    private boolean appearance;
    private int page;
    private final DropList themeList = new DropList();
    public HudEditorPanel(Screen parent) { this.parent = parent; }

    private static int rowHeight(int h) { return Math.clamp((h - 72) / 4, 22, 30); }
    private static int pageSize(int h) { return Math.max(1, (h - 72) / rowHeight(h)); }

    @Override public void createWidgets(Screen host, int x, int y, int w, int h) {
        var menu = (KrovexMenuScreen) host;
        var game = MinecraftClient.getInstance();
        int cell = w * 44 / 100, pane = x + cell + 9, paneW = w - cell - 9;
        var all = profiles.all();
        int count = pageSize(h), row = rowHeight(h), foot = y + Math.max(130, h) - 20;
        page = Math.clamp(page, 0, Math.max(0, all.size() - count));      // first visible profile
        listX = x; listY = y + 25; listW = cell; listCount = count;
        menu.addPanelWidget(MenuStyle.button("▱  Profiles", x + 3, y + 1, 72, 20,
                appearance ? MenuStyle.CARD : MenuStyle.PURPLE, () -> { appearance = false; menu.refreshPanel(); }));
        menu.addPanelWidget(MenuStyle.button("◇  Appearance", x + 79, y + 1, 91, 20,
                appearance ? MenuStyle.PURPLE : MenuStyle.CARD, () -> { appearance = true; menu.refreshPanel(); }));
        for (int i = page; i < Math.min(all.size(), page + count); i++) {
            var profile = all.get(i);
            int by = y + 29 + (i - page) * row;
            menu.addPanelWidget(MenuStyle.button(profile.name(), x + 6, by, cell - 12, row - 4,
                    selected == profile ? MenuStyle.PURPLE : MenuStyle.CARD, () -> { selected = profile; menu.refreshPanel(); }));
        }
        int createY = foot - 25;
        menu.addPanelWidget(MenuStyle.button("+ Create Profile", x + 6, createY, cell - 12, 18, MenuStyle.CARD,
                () -> { selected = profiles.createProfile(unique("New Profile")); page = Math.max(0, profiles.all().indexOf(selected) - count + 1); menu.refreshPanel(); }));
        if (appearance) {
            var choices = themes.listThemes();
            String active = selected.appearanceThemeId();
            int index = -1;
            for (int i = 0; i < choices.size(); i++) if (choices.get(i).id().equals(active)) index = i;
            themeList.button(menu, "", pane + 10, y + 82, paneW - 20, 20,
                    choices.stream().map(t -> new DropList.Item(t.name(), t.accentColor())).toList(), index, pick -> {
                        selected.setAppearanceThemeId(choices.get(pick).id());
                        profiles.save();
                        menu.refreshPanel();
                    });
        } else {
            menu.addPanelWidget(MenuStyle.button("Edit HUD layout  ›", pane + 10, y + 82, paneW - 20, 20, MenuStyle.CARD, () -> {
                profiles.setActive(selected.name());
                game.setScreen(new HudEditScreen(profiles, parent));
            }));
        }
        int button = (w - 8) / 3;
        menu.addPanelWidget(MenuStyle.button("Duplicate", x + w - 3 * button - 8, foot, button, 20, MenuStyle.CARD, () -> {
            var copy = profiles.createProfile(unique(selected.name() + " Copy"));
            selected.allLayouts().forEach((id, layout) -> copy.putLayout(HudElementLayout.fromMap(id, layout.toMap())));
            copy.setAppearanceThemeId(selected.appearanceThemeId());
            profiles.save();
            selected = copy;
            page = Math.max(0, profiles.all().indexOf(copy) - count + 1);
            menu.refreshPanel();
        }));
        var delete = menu.addPanelWidget(MenuStyle.button("Delete", x + w - 2 * button - 4, foot, button, 20, MenuStyle.CARD, () -> {
            String name = selected.name();
            game.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) profiles.deleteProfile(name);
                selected = profiles.active();
                game.setScreen(parent);
            }, Text.literal("Delete profile?"), Text.literal(name)));
        }));
        delete.active = !java.util.List.of("PvP", "Survival", "Recording").contains(selected.name());
        menu.addPanelWidget(MenuStyle.button("✓  Apply", x + w - button, foot, button, 20, MenuStyle.LIME, () -> {
            profiles.setActive(selected.name());
            if (selected.appearanceThemeId() != null && themes.findById(selected.appearanceThemeId()).isPresent())
                themes.setActiveThemeId(selected.appearanceThemeId());
            menu.refreshPanel();
        }));
    }

    @Override public boolean mouseClicked(double mx, double my, int button) { return themeList.mouseClicked(mx, my, button); }
    private int listX, listY, listW, listCount = 1;

    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        if (themeList.mouseScrolled(mx, my, vertical)) return true;
        if (mx < listX || mx >= listX + listW || my < listY) return false;
        int next = Math.clamp(page - (int) Math.signum(vertical), 0, Math.max(0, profiles.all().size() - listCount));
        if (next != page && MinecraftClient.getInstance().currentScreen instanceof KrovexMenuScreen menu) {
            page = next;
            menu.refreshPanel();
        }
        return true;
    }
    @Override public void renderOverlay(DrawContext c, int mx, int my) { themeList.renderOverlay(c, mx, my); }

    private String themeName() {
        String id = selected.appearanceThemeId();
        return id == null ? "Current theme" : themes.findById(id).map(com.krovex.theme.KrovexTheme::name).orElse("Current theme");
    }

    private String unique(String base) {
        String name = base;
        for (int i = 2; profiles.findByName(name).isPresent(); i++) name = base + " " + i;
        return name;
    }

    @Override public void render(DrawContext c, int x, int y, int w, int h, int mx, int my, float delta) {
        int cell = w * 44 / 100, pane = x + cell + 9, paneW = w - cell - 9;
        MenuStyle.card(c, x, y + 25, cell, Math.max(93, h - 50), 9, MenuStyle.EDGE, MenuStyle.PANEL);
        MenuStyle.card(c, pane, y + 25, paneW, Math.max(93, h - 50), 9, MenuStyle.EDGE, MenuStyle.PANEL);
        MenuStyle.scrollBar(c, x + cell - 5, y + 30, pageSize(h) * rowHeight(h) - 4, page, pageSize(h), profiles.all().size());
        MenuStyle.box(c, pane + 10, y + 34, 27, 26, 6, 0xFF5F4587);
        MenuStyle.text(c, appearance ? "◇" : "⚔", pane + 15, y + 40, MenuStyle.WHITE, 1.4f);
        MenuStyle.text(c, selected.name(), pane + 44, y + 37, MenuStyle.WHITE, 1.35f);
        if (profiles.active() == selected) {
            MenuStyle.box(c, pane + paneW - 47, y + 35, 39, 13, 5, MenuStyle.PURPLE);
            MenuStyle.text(c, "✓ Active", pane + paneW - 43, y + 37, MenuStyle.WHITE, .82f);
        }
        c.fill(pane + 9, y + 65, pane + paneW - 9, y + 66, MenuStyle.EDGE);
        MenuStyle.text(c, appearance ? "Appearance Preset" : "HUD Preset", pane + 10, y + 71, MenuStyle.WHITE, 1.08f);
        if (h >= 184) {
            MenuStyle.card(c, pane + 9, y + 110, paneW - 18, 45, 6, MenuStyle.EDGE, MenuStyle.CARD);
            MenuStyle.text(c, "ⓘ  Profile Details", pane + 15, y + 117, MenuStyle.WHITE, 1);
            MenuStyle.text(c, "HUD", pane + 16, y + 130, MenuStyle.MUTED, .88f);
            MenuStyle.text(c, selected.name(), pane + 16 + 88, y + 130, MenuStyle.WHITE, .88f);
            MenuStyle.text(c, "Appearance", pane + 16, y + 142, MenuStyle.MUTED, .88f);
            MenuStyle.text(c, themeName(), pane + 16 + 88, y + 142, MenuStyle.WHITE, .88f);
        }
    }
}

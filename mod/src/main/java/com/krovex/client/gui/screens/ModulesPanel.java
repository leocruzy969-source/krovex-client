package com.krovex.client.gui.screens;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import com.krovex.client.modules.ModuleCategory;
import com.krovex.client.modules.ModuleDefinition;
import com.krovex.client.modules.ModuleRegistry;
import com.krovex.client.modules.ModuleStateStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The module page as a grid of frosted cards, two to a row: name, what it does, a settings button (when the module
 * has settings), a favourite star and its switch. The category comes from the sidebar entries under "Modules"
 * ({@link #category}); search, favourites-only and enabled-only filter on top of that. The grid scrolls with the wheel.
 * Backed entirely by {@link ModuleRegistry} / {@link ModuleStateStore}: no module behaves differently, only the layout.
 */
public final class ModulesPanel implements KrovexSubPanel {
    private static final int CARD_H = 56, GAP = 6, TOP = 28;

    /** The category picked in the sidebar (null = all). Static: the sidebar and this page share it. */
    private static ModuleCategory category;

    public static ModuleCategory category() { return category; }
    public static void setCategory(ModuleCategory value) { category = value; firstRow = 0; }

    /** How many modules a category holds (null = all), for the numbers next to the sidebar entries. */
    public static int count(ModuleCategory value) {
        return (int) ModuleRegistry.all().stream().filter(def -> value == null || def.category() == value).count();
    }

    private static int firstRow;

    private final ModuleStateStore stateStore;
    private String searchQuery = "";
    private boolean favoritesOnly, enabledOnly;
    private KrovexMenuScreen menu;
    private int contentX, contentY, contentWidth, contentHeight;
    private final List<net.minecraft.client.gui.Element> cards = new ArrayList<>();

    public ModulesPanel(Screen parentScreen) {
        this.stateStore = KrovexClientMod.moduleStateStore();
    }

    private int columns() { return contentWidth >= 300 ? 2 : 1; }
    private int cardWidth() { return (contentWidth - 8 - (columns() - 1) * GAP) / columns(); }
    private int visibleRows() { return Math.max(1, (contentHeight - TOP + GAP) / (CARD_H + GAP)); }

    @Override
    public void createWidgets(Screen hostScreen, int x, int y, int width, int height) {
        this.menu = (KrovexMenuScreen) hostScreen;
        this.contentX = x;
        this.contentY = y;
        this.contentWidth = width;
        this.contentHeight = height;
        cards.clear();

        int searchWidth = Math.clamp(width - 250, 70, 200);
        var search = menu.addPanelWidget(MenuStyle.field(x, y, searchWidth, 20, Text.literal("Search modules")));
        search.setPlaceholder(Text.literal("Search modules…"));
        search.setText(searchQuery);
        search.setChangedListener(text -> { searchQuery = text; firstRow = 0; rebuildCards(); });
        menu.addPanelWidget(MenuStyle.toggle("Favorites", x + searchWidth + 12, y + 1, 80, 18, favoritesOnly,
                () -> { favoritesOnly = !favoritesOnly; firstRow = 0; menu.refreshPanel(); }));
        menu.addPanelWidget(MenuStyle.toggle("Enabled", x + searchWidth + 104, y + 1, 74, 18, enabledOnly,
                () -> { enabledOnly = !enabledOnly; firstRow = 0; menu.refreshPanel(); }));
        rebuildCards();
    }

    private void rebuildCards() {
        for (var widget : cards) menu.removePanelWidget(widget);
        cards.clear();
        var modules = filteredModules();
        int columns = columns(), rows = visibleRows(), cardW = cardWidth();
        int totalRows = (modules.size() + columns - 1) / columns;
        firstRow = Math.clamp(firstRow, 0, Math.max(0, totalRows - rows));
        var client = MinecraftClient.getInstance();
        for (int slot = 0; slot < rows * columns; slot++) {
            int index = firstRow * columns + slot;
            if (index >= modules.size()) break;
            ModuleDefinition def = modules.get(index);
            var state = stateStore.stateFor(def.id());
            int cx = contentX + (slot % columns) * (cardW + GAP), cy = contentY + TOP + (slot / columns) * (CARD_H + GAP);
            cards.add(menu.addPanelWidget(MenuStyle.toggle("", cx + cardW - 37, cy + 7, 27, 16, state.enabled(), () -> {
                state.setEnabled(!state.enabled());
                stateStore.save();
                rebuildCards();
            })));
            cards.add(menu.addPanelWidget(MenuStyle.button(state.favorite() ? "★" : "☆", cx + cardW - 58, cy + 7, 16, 16, 0, () -> {
                state.setFavorite(!state.favorite());
                stateStore.save();
                rebuildCards();
            })));
            if (def.hasSettings()) cards.add(menu.addPanelWidget(MenuStyle.button("", cx + cardW - 78, cy + 7, 16, 16, 0,
                    () -> client.setScreen(new ModuleSettingsScreen(menu, def)))));
        }
    }

    /** Pure filter logic -- the part exercised by ModuleTest-style assertions without any GUI. */
    List<ModuleDefinition> filteredModules() {
        String query = searchQuery.toLowerCase(Locale.ROOT);
        return ModuleRegistry.all().stream()
                .filter(def -> category == null || def.category() == category)
                .filter(def -> query.isEmpty() || def.displayName().toLowerCase(Locale.ROOT).contains(query)
                        || def.description().toLowerCase(Locale.ROOT).contains(query))
                .filter(def -> !favoritesOnly || stateStore.stateFor(def.id()).favorite())
                .filter(def -> !enabledOnly || stateStore.stateFor(def.id()).enabled())
                .toList();
    }

    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        if (menu == null || mx < contentX || my < contentY + TOP - 4) return false;
        firstRow -= (int) Math.signum(vertical);
        rebuildCards();
        return true;
    }

    @Override
    public void render(DrawContext context, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
        var all = filteredModules();
        var font = MinecraftClient.getInstance().textRenderer;
        String total = all.size() + (all.size() == 1 ? " module" : " modules");
        MenuStyle.text(context, total, x + width - 10 - font.getWidth(total), y + 6, MenuStyle.MUTED, .95f);
        int columns = columns(), rows = visibleRows(), cardW = cardWidth();
        if (all.isEmpty()) {
            MenuStyle.text(context, "No modules match.", x + 4, y + TOP + 10, MenuStyle.MUTED, 1f);
            return;
        }
        for (int slot = 0; slot < rows * columns; slot++) {
            int index = firstRow * columns + slot;
            if (index >= all.size()) break;
            ModuleDefinition def = all.get(index);
            boolean on = stateStore.stateFor(def.id()).enabled();
            int cx = x + (slot % columns) * (cardW + GAP), cy = y + TOP + (slot / columns) * (CARD_H + GAP);
            boolean hover = mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + CARD_H;
            MenuStyle.moduleCard(context, cx, cy, cardW, CARD_H, hover, on);
            int textRoom = Math.max(30, cardW - 100);
            MenuStyle.text(context, font.trimToWidth(def.displayName(), textRoom), cx + 11, cy + 10, MenuStyle.WHITE, 1.12f);
            // the description on up to two lines
            var lines = font.getTextHandler().wrapLines(def.description(), Math.max(40, (int) ((cardW - 24) * .92f)), Style.EMPTY);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                String line = lines.get(i).getString();
                if (i == 1 && lines.size() > 2) line = font.trimToWidth(line, Math.max(20, (int) ((cardW - 40) * .92f))) + "…";
                MenuStyle.text(context, line, cx + 11, cy + 27 + i * 11, MenuStyle.MUTED, .88f);
            }
            if (def.hasSettings()) MenuStyle.navIcon(context, "settings", cx + cardW - 75, cy + 10, hover ? MenuStyle.WHITE : 0xFF8F88A4);
        }
        int totalRows = (all.size() + columns - 1) / columns;
        MenuStyle.scrollBar(context, x + width - 5, y + TOP, rows * (CARD_H + GAP) - GAP, firstRow, rows, totalRows);
    }
}

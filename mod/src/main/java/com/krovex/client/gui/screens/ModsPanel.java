package com.krovex.client.gui.screens;

import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import com.krovex.client.mods.InstalledModInfo;
import com.krovex.client.mods.InstalledModScanner;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Installed mods, one scrolling list (mouse wheel, no pages). Works like Mod Menu where it matters: a mod that has a
 * settings screen gets a Settings button that opens that very screen (the one Mod Menu would open), and "Open Mod
 * Menu" jumps to Mod Menu's own list. Both go through Mod Menu by reflection, so nothing breaks when it is not installed.
 */
public final class ModsPanel implements KrovexSubPanel {
    private static final int ROW = 23, CHIP_W = 56;

    private final com.krovex.client.gui.MenuIcons icons = new com.krovex.client.gui.MenuIcons("mods");
    private final InstalledModScanner scanner = new InstalledModScanner();
    private final DropList filterList = new DropList();
    private final Map<String, Boolean> hasSettings = new HashMap<>();
    private List<InstalledModInfo> installed = List.of();
    private String query = "";
    private String status = "";
    private int scroll;
    private boolean requiresDependencies;
    private int px, py, pw, ph;

    public ModsPanel(Screen parent) {}

    @Override public void onShown() { installed = scanner.scan(); }
    @Override public void onHidden() { icons.clear(); filterList.close(); }

    private List<InstalledModInfo> matches() {
        String value = query.toLowerCase(Locale.ROOT);
        return installed.stream().filter(mod -> !mod.isKrovexClient())
                .filter(mod -> !requiresDependencies || !mod.dependsOnModIds().isEmpty())
                .filter(mod -> mod.name().toLowerCase(Locale.ROOT).contains(value) || mod.modId().contains(value)).toList();
    }

    private int visibleRows() { return Math.max(1, (ph - 44) / ROW); }

    // ------------------------------------------------------------------ Mod Menu bridge (reflection: it may not be installed)

    private static boolean modMenuInstalled() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("modmenu");
    }

    private boolean hasSettings(String modId) {
        return hasSettings.computeIfAbsent(modId, id -> {
            if (!modMenuInstalled()) return false;
            try {
                Object result = Class.forName("com.terraformersmc.modmenu.ModMenu").getMethod("hasConfigScreen", String.class).invoke(null, id);
                return Boolean.TRUE.equals(result);
            } catch (Throwable e) {
                return false;
            }
        });
    }

    private void openSettings(InstalledModInfo mod) {
        var game = MinecraftClient.getInstance();
        try {
            Object screen = Class.forName("com.terraformersmc.modmenu.ModMenu")
                    .getMethod("getConfigScreen", String.class, Screen.class).invoke(null, mod.modId(), game.currentScreen);
            if (screen instanceof Screen settings) game.setScreen(settings);
            else status = mod.name() + " has no settings screen.";
        } catch (Throwable e) {
            status = "Could not open the settings of " + mod.name() + ".";
        }
    }

    private void openModMenu() {
        var game = MinecraftClient.getInstance();
        try {
            Object screen = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen").getConstructor(Screen.class).newInstance(game.currentScreen);
            game.setScreen((Screen) screen);
        } catch (Throwable e) {
            status = "Could not open Mod Menu.";
        }
    }

    // ------------------------------------------------------------------ widgets + mouse

    @Override public void createWidgets(Screen host, int x, int y, int w, int h) {
        var menu = (KrovexMenuScreen) host;
        px = x; py = y; pw = w; ph = h;
        boolean modMenu = modMenuInstalled();
        int filterW = 118, menuW = modMenu ? 92 : 0;
        int searchWidth = Math.max(50, w - filterW - menuW - (modMenu ? 12 : 6));
        var search = menu.addPanelWidget(MenuStyle.field(x, y, searchWidth, 19, Text.literal("Search mods")));
        search.setText(query);
        search.setPlaceholder(Text.literal("Search mods…"));
        search.setChangedListener(value -> { query = value; scroll = 0; });
        filterList.button(menu, "Show:  ", x + searchWidth + 6, y, filterW, 19,
                List.of(new DropList.Item("All mods"), new DropList.Item("With dependencies")), requiresDependencies ? 1 : 0, index -> {
                    requiresDependencies = index == 1;
                    scroll = 0;
                    menu.refreshPanel();
                });
        if (modMenu) menu.addPanelWidget(MenuStyle.button("Open Mod Menu", x + w - menuW, y, menuW, 19, MenuStyle.CARD, this::openModMenu));
    }

    private int chipX() { return px + pw - CHIP_W - 10; }

    private int rowAt(double mx, double my) {
        int top = py + 44;
        if (mx < px || mx >= px + pw || my < top || my >= top + visibleRows() * ROW) return -1;
        return scroll + (int) ((my - top) / ROW);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (filterList.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        int row = rowAt(mx, my);
        var mods = matches();
        if (row < 0 || row >= mods.size()) return false;
        var mod = mods.get(row);
        if (mx >= chipX() && mx < chipX() + CHIP_W && hasSettings(mod.modId())) {
            openSettings(mod);
            return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        if (filterList.mouseScrolled(mx, my, vertical)) return true;
        if (mx < px || mx >= px + pw || my < py + 24 || my >= py + ph) return false;
        scroll = Math.clamp(scroll - (int) Math.signum(vertical) * 2, 0, Math.max(0, matches().size() - visibleRows()));
        return true;
    }

    @Override public void renderOverlay(DrawContext c, int mx, int my) { filterList.renderOverlay(c, mx, my); }

    // ------------------------------------------------------------------ drawing

    @Override public void render(DrawContext c, int x, int y, int w, int h, int mx, int my, float delta) {
        var font = MinecraftClient.getInstance().textRenderer;
        boolean modMenu = modMenuInstalled();
        int versionX = x + w * 46 / 100, depsX = x + w * 62 / 100, chipX = chipX();
        List<InstalledModInfo> mods = matches();
        int rows = visibleRows();
        scroll = Math.clamp(scroll, 0, Math.max(0, mods.size() - rows));
        MenuStyle.card(c, x, y + 24, w, h - 24, 7, MenuStyle.EDGE, MenuStyle.CARD);
        MenuStyle.text(c, "Mod  ·  " + mods.size(), x + 9, y + 30, MenuStyle.WHITE, 1);
        MenuStyle.text(c, "Version", versionX, y + 30, MenuStyle.WHITE, 1);
        MenuStyle.text(c, "Dependencies", depsX, y + 30, MenuStyle.WHITE, 1);
        if (modMenu) MenuStyle.text(c, "Settings", chipX + 6, y + 30, MenuStyle.WHITE, 1);
        int hover = filterList.isOpen() ? -1 : rowAt(mx, my);
        for (int index = scroll; index < Math.min(mods.size(), scroll + rows); index++) {
            int top = y + 44 + (index - scroll) * ROW;
            var mod = mods.get(index);
            c.fill(x + 1, top, x + w - 1, top + 1, MenuStyle.EDGE);
            if (index == hover) MenuStyle.rowHover(c, x + 1, top + 1, w - 2, ROW - 1);
            MenuStyle.text(c, mod.compatibleWithCurrentMinecraft() ? "✓" : "!", x + 8, top + 7, MenuStyle.LIME, 1);
            icons.draw(c, mod.modId(), () -> {
                var container = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(mod.modId());
                if (container.isEmpty()) return null;
                var file = container.get().getMetadata().getIconPath(32).flatMap(container.get()::findPath);
                return file.isEmpty() ? null : com.krovex.client.gui.MenuIcons.read(java.nio.file.Files.newInputStream(file.get()));
            }, x + 23, top + 3, 17);
            MenuStyle.text(c, font.trimToWidth(mod.name(), Math.max(1, versionX - x - 50)), x + 44, top + 4, MenuStyle.WHITE, 1);
            var container = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(mod.modId());
            container.ifPresent(item -> MenuStyle.text(c, font.trimToWidth(item.getMetadata().getDescription().replace('\n', ' '),
                    Math.max(1, versionX - x - 50)), x + 44, top + 13, MenuStyle.MUTED, .72f));
            MenuStyle.text(c, font.trimToWidth(mod.version(), depsX - versionX - 7), versionX, top + 7, 0xFFE6C7FF, .95f);
            String dependencies = mod.dependsOnModIds().isEmpty() ? "—" : String.join(", ", mod.dependsOnModIds());
            MenuStyle.text(c, font.trimToWidth(dependencies, Math.max(1, (modMenu ? chipX : x + w) - depsX - 8)), depsX, top + 7, MenuStyle.MUTED, .92f);
            if (modMenu && hasSettings(mod.modId())) {
                boolean over = index == hover && mx >= chipX && mx < chipX + CHIP_W;
                MenuStyle.card(c, chipX, top + 4, CHIP_W, 15, 4, over ? MenuStyle.HOVER_EDGE : MenuStyle.EDGE, over ? 0xC05A3F8C : MenuStyle.CARD);
                MenuStyle.text(c, "Settings", chipX + 9, top + 8, MenuStyle.WHITE, .88f);
            }
        }
        MenuStyle.scrollBar(c, x + w - 5, y + 45, rows * ROW - 2, scroll, rows, mods.size());
        if (!status.isEmpty()) MenuStyle.text(c, status, x + 120, y + 30, 0xFFFFC857, .85f);
    }
}

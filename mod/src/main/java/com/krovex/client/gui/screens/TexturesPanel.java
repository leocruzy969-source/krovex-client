package com.krovex.client.gui.screens;

import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;

/**
 * Textures, working like Minecraft's own Resource Packs screen: Available on the left, Selected on the right, and only
 * the packs vanilla's screen would list from the resourcepacks folder (Minecraft's own packs, mods' built-in packs and
 * packs a mod manages and hides itself, such as Bauloo Styles' downloads, stay as they are in the background and are
 * never listed). Point at a pack and its icon shows arrows, exactly as in vanilla: the side arrow moves it across, the
 * up / down arrows on a Selected pack change its priority (top wins); a double click also moves it across. Scroll
 * either list with the mouse wheel. "Open folder" opens the resourcepacks folder in the file explorer, like vanilla's
 * button; dropping a pack on the window copies it there, like vanilla. Pack groups save the Selected stack by name.
 */
public final class TexturesPanel implements KrovexSubPanel {
    private static final int ROW = 26, TOP = 46, HEADER = 20, FOOT = 26;

    private final com.krovex.client.gui.MenuIcons icons = new com.krovex.client.gui.MenuIcons("textures");
    private final MinecraftClient game = MinecraftClient.getInstance();
    private final PackGroupStore groups = PackGroupStore.get();
    private final DropList groupList = new DropList();
    /** Every enabled pack id, lowest priority first. Packs that are not texture packs are kept here but never shown. */
    private final List<String> selected = new ArrayList<>();
    private String query = "";
    private static String groupName = "";
    private String groupPick = "";
    private volatile String status = "";
    private boolean dirty;
    private KrovexMenuScreen menu;

    // geometry, set in createWidgets
    private int availX, selX, listW, listY, rowsVisible, cardTop, cardHeight;
    private int scrollAvail, scrollSel;


    public TexturesPanel(Screen parent) {}

    @Override public void onShown() {
        if (!dirty) reset();
    }

    @Override public void onHidden() { icons.clear(); groupList.close(); }

    private void reset() {
        var manager = game.getResourcePackManager();
        manager.scanPacks();
        listed.clear();
        selected.clear();
        selected.addAll(manager.getEnabledIds());
        dirty = false;
    }

    /** Markers of packs that mods manage themselves and hide from vanilla's pack screen (Bauloo Styles hides exactly these). */
    private static final String[] MOD_MANAGED = {"bauloo_styles:", "bauloo_upload_", "bauloo_cloud_", "bauloo_anchor_glow", "bauloo_crystal_core", "bauloo_glint"};

    private final java.util.Map<String, Boolean> listed = new java.util.HashMap<>();

    /** Would vanilla's Resource Packs screen show this pack as one of the player's own (a file in the resourcepacks folder)? */
    private boolean isTexturePack(ResourcePackProfile pack) {
        return listed.computeIfAbsent(pack.getId(), id -> {
            if (!id.startsWith("file/") || pack.isPinned() || pack.isRequired()) return false;
            String lower = id.toLowerCase(Locale.ROOT);
            for (String marker : MOD_MANAGED) if (lower.contains(marker)) return false;
            if (pack.getDescription().getString().toLowerCase(Locale.ROOT).contains("built-in: bauloo styles")) return false;
            try {       // Fabric lets mods mark a pack as hidden from the pack screen
                Object hidden = pack.getClass().getMethod("fabric_isHidden").invoke(pack);
                if (Boolean.TRUE.equals(hidden)) return false;
            } catch (ReflectiveOperationException | RuntimeException ignored) { /* no such flag on this pack */ }
            try {       // and it has to be an actual file or folder in the resourcepacks folder
                return Files.exists(packDir().resolve(id.substring(5)));
            } catch (RuntimeException e) {
                return true;
            }
        });
    }

    private List<ResourcePackProfile> available() {
        return game.getResourcePackManager().getProfiles().stream()
                .filter(this::isTexturePack)
                .filter(pack -> !selected.contains(pack.getId()))
                .filter(pack -> pack.getDisplayName().getString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** The enabled texture packs, top (wins) to bottom. */
    private List<ResourcePackProfile> active() {
        var manager = game.getResourcePackManager();
        return selected.reversed().stream().map(manager::getProfile).filter(java.util.Objects::nonNull)
                .filter(this::isTexturePack).toList();
    }

    /** Writes a new top-to-bottom order of the visible packs back into {@link #selected}, keeping hidden packs where they were (at the bottom). */
    private void setActiveOrder(List<String> topToBottom) {
        var manager = game.getResourcePackManager();
        List<String> hidden = selected.stream().filter(id -> {
            var profile = manager.getProfile(id);
            return profile == null || !isTexturePack(profile);
        }).toList();
        selected.clear();
        selected.addAll(hidden);
        selected.addAll(topToBottom.reversed());
        dirty = true;
    }

    private List<String> visibleIdsLowestFirst() {
        return active().reversed().stream().map(ResourcePackProfile::getId).toList();
    }

    private void apply() {
        var manager = game.getResourcePackManager();
        manager.setEnabledProfiles(selected);
        game.options.refreshResourcePacks(manager);
        dirty = false;
    }

    private void refresh() {
        if (game.currentScreen instanceof KrovexMenuScreen m) m.refreshPanel();
    }

    // ------------------------------------------------------------------ widgets

    @Override public void createWidgets(Screen host, int x, int y, int w, int h) {
        menu = (KrovexMenuScreen) host;
        int cell = (w - 14) / 2;
        availX = x;
        selX = x + cell + 14;
        listW = cell;
        cardTop = y + TOP;
        cardHeight = h - TOP - FOOT + 6;
        listY = cardTop + HEADER;
        rowsVisible = Math.max(1, (cardHeight - HEADER - 4) / ROW);

        // row 1: import, open folder, search
        menu.addPanelWidget(MenuStyle.button("Open folder", x, y + 2, 84, 17, MenuStyle.CARD, this::openFolder));
        var search = menu.addPanelWidget(MenuStyle.field(x + 88, y + 2, Math.max(40, w - 88), 17, Text.literal("Search texture packs")));
        search.setText(query);
        search.setPlaceholder(Text.literal("Search texture packs…"));
        search.setChangedListener(value -> { query = value; scrollAvail = 0; });

        // row 2: pack groups (a list of saved groups on the left, naming a new one on the right)
        var saved = groups.groups();
        if (!saved.isEmpty() && saved.stream().noneMatch(g -> g.name().equals(groupPick))) groupPick = saved.getFirst().name();
        int pick = 0;
        for (int i = 0; i < saved.size(); i++) if (saved.get(i).name().equals(groupPick)) pick = i;
        if (saved.isEmpty()) {
            menu.addPanelWidget(MenuStyle.button("No saved groups yet", x, y + 23, cell - 68, 17, MenuStyle.CARD, () -> {})).active = false;
        } else {
            groupList.button(menu, "Group:  ", x, y + 23, cell - 68, 17,
                    saved.stream().map(g -> new DropList.Item(g.name() + "  (" + g.packs().size() + ")")).toList(), pick, index -> {
                        groupPick = saved.get(index).name();
                        refresh();
                    });
        }
        menu.addPanelWidget(MenuStyle.button("Use", x + cell - 66, y + 23, 42, 17, MenuStyle.LIME, this::useGroup)).active = !saved.isEmpty();
        menu.addPanelWidget(MenuStyle.button("×", x + cell - 22, y + 23, 22, 17, MenuStyle.CARD, () -> {
            if (!groupPick.isEmpty() && groups.remove(groupPick)) { status = "Deleted group \"" + groupPick + "\"."; groupPick = ""; refresh(); }
        })).active = !saved.isEmpty();
        var field = menu.addPanelWidget(MenuStyle.field(selX, y + 23, cell - 80, 17, Text.literal("Group name")));
        field.setPlaceholder(Text.literal("Name this group…"));
        field.setMaxLength(24);
        field.setText(groupName);
        field.setChangedListener(value -> groupName = value);
        menu.addPanelWidget(MenuStyle.button("Save group", selX + cell - 76, y + 23, 76, 17, MenuStyle.CARD, () -> {
            String name = groupName.strip();
            if (name.isEmpty()) { status = "Type a name for the group first."; return; }
            groups.put(name, visibleIdsLowestFirst());
            groupPick = name;
            status = "Saved group \"" + name + "\" (" + active().size() + " packs, in this order).";
            refresh();
        }));

        // bottom bar
        menu.addPanelWidget(MenuStyle.button("Reset", x + w - 149, y + h - 20, 47, 18, MenuStyle.CARD, () -> { reset(); refresh(); }));
        menu.addPanelWidget(MenuStyle.button("Apply Textures", x + w - 98, y + h - 20, 98, 18, MenuStyle.LIME, () -> { apply(); refresh(); }));
    }

    // ------------------------------------------------------------------ adding packs

    private Path packDir() { return game.getResourcePackDir(); }

    /** Opens the resourcepacks folder in the file explorer, the same call vanilla's "Open Pack Folder" button makes. */
    private void openFolder() {
        Path dir = packDir();
        try {
            Files.createDirectories(dir);
            net.minecraft.util.Util.getOperatingSystem().open(dir);
        } catch (IOException | RuntimeException e) {
            status = "Could not open the folder: " + dir;
        }
    }

    @Override public void filesDropped(List<Path> paths) { Thread.ofVirtual().start(() -> importPacks(paths)); }

    /** Copies packs into the resourcepacks folder (the one Minecraft's own menu reads), then rescans so they appear here and there. */
    private void importPacks(List<Path> sources) {
        int added = 0, skipped = 0;
        String last = "";
        try {
            Files.createDirectories(packDir());
            for (Path source : sources) {
                String name = source.getFileName().toString();
                boolean folder = Files.isDirectory(source);
                if (!folder && !name.toLowerCase(Locale.ROOT).endsWith(".zip")) { skipped++; continue; }
                if (folder && !Files.exists(source.resolve("pack.mcmeta"))) { skipped++; continue; }
                Path target = packDir().resolve(name);
                for (int n = 2; Files.exists(target); n++) {
                    String base = folder ? name : name.substring(0, name.length() - 4);
                    target = packDir().resolve(base + " (" + n + ")" + (folder ? "" : ".zip"));
                }
                if (folder) copyTree(source, target); else Files.copy(source, target);
                added++;
                last = name;
            }
        } catch (IOException e) {
            status = "Could not copy the pack: " + e.getMessage();
            return;
        }
        int addedFinal = added, skippedFinal = skipped;
        String lastFinal = last;
        game.execute(() -> {
            if (addedFinal > 0) { game.getResourcePackManager().scanPacks(); listed.clear(); }
            status = addedFinal == 0 ? "Nothing added: pick .zip files or pack folders (with a pack.mcmeta)."
                    : "Added " + (addedFinal == 1 ? "\"" + lastFinal + "\"" : addedFinal + " packs") + " to your resourcepacks folder"
                    + (skippedFinal > 0 ? " (" + skippedFinal + " skipped)." : ".") + " It is in Minecraft's own list too.";
            refresh();
        });
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (var walk = Files.walk(from)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                Path dest = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) Files.createDirectories(dest); else Files.copy(path, dest);
            }
        }
    }

    // ------------------------------------------------------------------ groups

    /** One click: switch the whole saved group on, in its saved order, and apply it. */
    private void useGroup() {
        var group = groups.groups().stream().filter(g -> g.name().equals(groupPick)).findFirst().orElse(null);
        if (group == null) return;
        var manager = game.getResourcePackManager();
        manager.scanPacks();
        listed.clear();
        List<String> wanted = group.packs().stream().filter(id -> {
            var profile = manager.getProfile(id);
            return profile != null && isTexturePack(profile);
        }).toList();
        int missing = group.packs().size() - wanted.size();
        selected.clear();
        selected.addAll(manager.getEnabledIds().stream().filter(id -> {
            var profile = manager.getProfile(id);
            return profile == null || !isTexturePack(profile);     // keep the built-in packs that are always on
        }).toList());
        selected.addAll(wanted);
        apply();
        status = "Switched to \"" + group.name() + "\"" + (missing > 0 ? " (" + missing + " pack" + (missing == 1 ? "" : "s") + " no longer in the folder)." : ".");
        refresh();
    }

    // ------------------------------------------------------------------ mouse: vanilla's icon arrows, double click, wheel

    /** Width of the clickable icon zone at the left of a row (the pack icon with its arrows). */
    private static final int ZONE = 32;

    private boolean inList(double mx, double my, int listX) {
        return mx >= listX && mx < listX + listW && my >= listY && my < listY + rowsVisible * ROW;
    }

    private int rowAt(double my, int scroll) { return scroll + (int) Math.floor((my - listY) / ROW); }

    /** Which arrow of a row the mouse is on: 0 none, 1 move across, 2 up, 3 down. */
    private int arrowAt(double mx, double my, int listX, boolean enabled, int position, int count) {
        double rx = mx - (listX + 5), ry = (my - listY) % ROW;
        if (rx < 0 || rx >= ZONE) return 0;
        if (!enabled) return 1;
        if (rx < ZONE / 2.0) return 1;
        if (ry < ROW / 2.0) return position > 0 ? 2 : 0;
        return position < count - 1 ? 3 : 0;
    }

    private long lastClickAt;
    private String lastClickId = "";

    private boolean doubleClick(String id) {
        long now = System.currentTimeMillis();
        boolean twice = id.equals(lastClickId) && now - lastClickAt < 350;
        lastClickAt = twice ? 0 : now;
        lastClickId = id;
        return twice;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (groupList.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        if (inList(mx, my, availX)) {
            int position = rowAt(my, scrollAvail);
            var packs = available();
            if (position < 0 || position >= packs.size()) return true;
            String id = packs.get(position).getId();
            if (arrowAt(mx, my, availX, false, position, packs.size()) == 1 || doubleClick(id)) {
                selected.add(id);                       // newest = top priority, as in vanilla
                dirty = true;
                refresh();
            }
            return true;
        }
        if (inList(mx, my, selX)) {
            int position = rowAt(my, scrollSel);
            var ids = new ArrayList<>(active().stream().map(ResourcePackProfile::getId).toList());
            if (position < 0 || position >= ids.size()) return true;
            String id = ids.get(position);
            int arrow = arrowAt(mx, my, selX, true, position, ids.size());
            if (arrow == 1 || (arrow == 0 && doubleClick(id))) {
                selected.remove(id);
                dirty = true;
                refresh();
            } else if (arrow == 2 || arrow == 3) {
                java.util.Collections.swap(ids, position, position + (arrow == 2 ? -1 : 1));
                setActiveOrder(ids);
                refresh();
            }
            return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        if (groupList.mouseScrolled(mx, my, vertical)) return true;
        int step = (int) -Math.signum(vertical);
        if (inList(mx, my, availX) || (mx >= availX && mx < availX + listW && my >= cardTop && my < cardTop + cardHeight)) {
            scrollAvail = Math.clamp(scrollAvail + step, 0, Math.max(0, available().size() - rowsVisible));
            return true;
        }
        if (mx >= selX && mx < selX + listW && my >= cardTop && my < cardTop + cardHeight) {
            scrollSel = Math.clamp(scrollSel + step, 0, Math.max(0, active().size() - rowsVisible));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ drawing

    @Override public void renderOverlay(DrawContext c, int mx, int my) { groupList.renderOverlay(c, mx, my); }

    @Override public void render(DrawContext c, int x, int y, int w, int h, int mx, int my, float delta) {
        var availablePacks = available();
        var activePacks = active();
        scrollAvail = Math.clamp(scrollAvail, 0, Math.max(0, availablePacks.size() - rowsVisible));
        scrollSel = Math.clamp(scrollSel, 0, Math.max(0, activePacks.size() - rowsVisible));
        drawList(c, availX, availablePacks, scrollAvail, false, mx, my);
        drawList(c, selX, activePacks, scrollSel, true, mx, my);
        String shown = dirty ? "Changes not applied yet: press Apply Textures." : status;
        if (!shown.isEmpty()) MenuStyle.text(c, game.textRenderer.trimToWidth(shown, Math.max(40, w - 160)), x + 2, y + h - 14, dirty ? 0xFFFFC857 : MenuStyle.MUTED, .88f);
    }

    private void drawList(DrawContext c, int x, List<ResourcePackProfile> packs, int scroll, boolean enabled, int mx, int my) {
        MenuStyle.card(c, x, cardTop, listW, cardHeight, 7, enabled ? MenuStyle.LIME : MenuStyle.EDGE, MenuStyle.CARD);
        MenuStyle.disc(c, x + 12, cardTop + 11, 3, enabled ? MenuStyle.LIME : 0xFF7A6D92);
        MenuStyle.text(c, (enabled ? "Selected" : "Available") + " (" + packs.size() + ")", x + 20, cardTop + 7, enabled ? MenuStyle.WHITE : MenuStyle.MUTED, 1.1f);
        if (enabled) MenuStyle.text(c, "top wins", x + listW - 46, cardTop + 9, MenuStyle.MUTED, .8f);
        if (packs.isEmpty()) {
            MenuStyle.text(c, enabled ? "No texture packs selected." : "No more packs in your resourcepacks folder.", x + 12, listY + 8, MenuStyle.MUTED, .9f);
            MenuStyle.text(c, enabled ? "Point at a pack on the left and click its arrow." : "Put packs in the folder (Open folder) or drop them here.", x + 12, listY + 21, MenuStyle.MUTED, .8f);
            return;
        }
        c.enableScissor(x + 1, listY, x + listW - 1, listY + rowsVisible * ROW);
        for (int row = 0; row < rowsVisible && scroll + row < packs.size(); row++) {
            int position = scroll + row, top = listY + row * ROW;
            boolean hover = mx >= x && mx < x + listW && my >= top && my < top + ROW && !groupList.isOpen();
            if (hover) MenuStyle.rowHover(c, x + 3, top, listW - 6, ROW - 1);
            int arrow = hover ? arrowAt(mx, my, x, enabled, position, packs.size()) : 0;
            drawRow(c, x, top, packs.get(position), enabled, hover, arrow, position, packs.size());
        }
        c.disableScissor();
        MenuStyle.scrollBar(c, x + listW - 5, listY + 2, rowsVisible * ROW - 4, scroll, rowsVisible, packs.size());
    }

    private void drawRow(DrawContext c, int x, int top, ResourcePackProfile profile, boolean enabled, boolean hover, int arrow, int position, int count) {
        if (enabled) c.fill(x + 3, top + 2, x + 5, top + ROW - 4, MenuStyle.LIME);      // lime bar: this one is on
        icons.draw(c, profile.getId(), () -> {
            try (var pack = profile.createResourcePack()) {
                var icon = pack.openRoot("pack.png");
                return icon == null ? null : com.krovex.client.gui.MenuIcons.read(icon.get());
            }
        }, x + 11, top + 2, 20);
        if (hover) {
            // vanilla's way: the icon darkens and shows arrows; the one under the mouse lights up
            c.fill(x + 6, top + 1, x + 6 + ZONE - 1, top + ROW - 2, 0xA0000000);
            if (!enabled) {
                arrow(c, x + 21, top + ROW / 2 - 1, 0, arrow == 1 ? MenuStyle.LIME : 0xFFFFFFFF);          // select: points right
            } else {
                arrow(c, x + 13, top + ROW / 2 - 1, 1, arrow == 1 ? MenuStyle.LIME : 0xFFFFFFFF);          // unselect: points left
                if (position > 0) arrow(c, x + 28, top + 7, 2, arrow == 2 ? MenuStyle.LIME : 0xFFFFFFFF);
                if (position < count - 1) arrow(c, x + 28, top + ROW - 9, 3, arrow == 3 ? MenuStyle.LIME : 0xFFFFFFFF);
            }
        }
        int left = x + 41, room = Math.max(10, listW - 41 - 10);
        String name = profile.getDisplayName().getString();
        String description = profile.getDescription().getString().replace('\n', ' ');
        MenuStyle.text(c, game.textRenderer.trimToWidth(name, room), left, top + 3, enabled ? MenuStyle.WHITE : MenuStyle.MUTED, .95f);
        MenuStyle.text(c, game.textRenderer.trimToWidth(description, room), left, top + 14, MenuStyle.MUTED, .78f);
        c.fill(x + 5, top + ROW - 1, x + listW - 5, top + ROW, MenuStyle.EDGE);
    }

    /** A small solid triangle centred on (cx, cy): 0 right, 1 left, 2 up, 3 down. */
    private static void arrow(DrawContext c, int cx, int cy, int direction, int color) {
        for (int i = 0; i < 5; i++) {
            int half = 4 - i;      // widest at the base, a point at the tip
            switch (direction) {
                case 0 -> c.fill(cx - 2 + i, cy - half, cx - 1 + i, cy + half + 1, color);
                case 1 -> c.fill(cx + 2 - i, cy - half, cx + 3 - i, cy + half + 1, color);
                case 2 -> c.fill(cx - half, cy + 2 - i, cx + half + 1, cy + 3 - i, color);
                default -> c.fill(cx - half, cy - 2 + i, cx + half + 1, cy - 1 + i, color);
            }
        }
    }
}
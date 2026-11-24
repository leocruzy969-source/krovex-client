package com.krovex.client.gui.screens;

import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * A pick-from-a-list control that replaces the old "‹ name ›" flip-through buttons: one click opens a list with every
 * option visible (scrollable when long), one more click picks it. A panel owns one instance per list, calls
 * {@link #button} while building its widgets, and forwards {@link #mouseClicked}, {@link #mouseScrolled} and
 * {@link #renderOverlay} from the matching {@link KrovexSubPanel} hooks (the menu calls them on top of everything).
 */
public final class DropList {
    /** One entry: its label and an optional colour chip (0 = none). */
    public record Item(String label, int swatch) {
        public Item(String label) { this(label, 0); }
    }

    private static final int ROW = 15, MAX_ROWS = 9;

    private boolean open;
    private int scroll;
    private int ax, ay, aw, ah;
    private List<Item> items = List.of();
    private int selected = -1;
    private IntConsumer onPick = i -> {};

    /** Adds the closed-state button to the menu and remembers where the list drops from. */
    public void button(KrovexMenuScreen menu, String caption, int x, int y, int w, int h, List<Item> items, int selectedIndex, IntConsumer onPick) {
        button(widget -> menu.addPanelWidget(widget), caption, x, y, w, h, items, selectedIndex, onPick);
    }

    /** Same, for screens that are not menu panels: {@code add} registers the button (e.g. {@code addDrawableChild}). */
    public void button(java.util.function.Consumer<net.minecraft.client.gui.widget.ClickableWidget> add, String caption, int x, int y, int w, int h,
                       List<Item> items, int selectedIndex, IntConsumer onPick) {
        this.ax = x; this.ay = y; this.aw = w; this.ah = h;
        this.items = items;
        this.selected = selectedIndex;
        this.onPick = onPick;
        this.open = false;
        String shown = selectedIndex >= 0 && selectedIndex < items.size() ? items.get(selectedIndex).label() : "Custom";
        add.accept(MenuStyle.button(caption + shown + "  ▾", x, y, w, h, MenuStyle.CARD, this::toggle));
    }

    private void toggle() {
        open = !open;
        if (open) scroll = Math.clamp(selected - 3, 0, Math.max(0, items.size() - MAX_ROWS));
    }

    public boolean isOpen() { return open; }
    public void close() { open = false; }

    private int visibleRows() { return Math.min(items.size(), MAX_ROWS); }
    private int listHeight() { return visibleRows() * ROW + 6; }

    private int listY() {
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int below = ay + ah + 2;
        return below + listHeight() <= screenH - 4 ? below : Math.max(4, ay - listHeight() - 2);
    }

    private int rowAt(double mx, double my) {
        int ly = listY();
        if (mx < ax || mx >= ax + aw || my < ly + 3 || my >= ly + 3 + visibleRows() * ROW) return -1;
        int index = scroll + (int) ((my - ly - 3) / ROW);
        return index < items.size() ? index : -1;
    }

    /** While open, every click belongs to the list: a row picks it, anywhere else closes it. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!open) return false;
        int row = button == 0 ? rowAt(mx, my) : -1;
        open = false;
        if (row >= 0) onPick.accept(row);
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double vertical) {
        if (!open) return false;
        scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, items.size() - MAX_ROWS));
        return true;
    }

    public void renderOverlay(DrawContext c, int mx, int my) {
        if (!open || items.isEmpty()) return;
        int ly = listY(), lh = listHeight();
        MenuStyle.card(c, ax, ly, aw, lh, 6, MenuStyle.LIME, 0xF2181022);
        int hover = rowAt(mx, my);
        c.enableScissor(ax + 1, ly + 1, ax + aw - 1, ly + lh - 1);
        for (int r = 0; r < visibleRows(); r++) {
            int index = scroll + r;
            if (index >= items.size()) break;
            int ry = ly + 3 + r * ROW;
            if (index == hover) c.fill(ax + 3, ry, ax + aw - 3, ry + ROW - 1, 0x40FFFFFF);
            if (index == selected) c.fill(ax + 3, ry + 1, ax + 5, ry + ROW - 2, MenuStyle.LIME);
            int tx = ax + 10;
            var item = items.get(index);
            if (item.swatch() != 0) {
                MenuStyle.boxRaw(c, tx, ry + 2, 10, ROW - 5, 3, item.swatch() | 0xFF000000);
                tx += 15;
            }
            String label = MinecraftClient.getInstance().textRenderer.trimToWidth(item.label(), Math.max(10, ax + aw - tx - (items.size() > MAX_ROWS ? 9 : 5)));
            MenuStyle.text(c, label, tx, ry + 3, index == selected ? MenuStyle.WHITE : MenuStyle.MUTED, .95f);
        }
        c.disableScissor();
        if (items.size() > MAX_ROWS) {      // slim scroll bar
            int trackH = lh - 8, thumbH = Math.max(10, trackH * MAX_ROWS / items.size());
            int thumbY = ly + 4 + (trackH - thumbH) * scroll / Math.max(1, items.size() - MAX_ROWS);
            c.fill(ax + aw - 6, thumbY, ax + aw - 4, thumbY + thumbH, MenuStyle.LIME);
        }
    }
}

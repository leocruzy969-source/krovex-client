package com.krovex.launcher.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicMenuItemUI;

/** Context-menu rows: roomy, rounded hover, red for destructive "Delete ..." items. */
public final class LauncherMenuItemUI extends BasicMenuItemUI {
    public static ComponentUI createUI(JComponent component) { return new LauncherMenuItemUI(); }

    @Override protected void installDefaults() {
        super.installDefaults();
        menuItem.setOpaque(false);
        menuItem.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 30));
        menuItem.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        menuItem.setIconTextGap(12);
    }

    private static boolean destructive(JMenuItem item) {
        return item.getText() != null && item.getText().toLowerCase(java.util.Locale.ROOT).startsWith("delete");
    }

    @Override protected void paintBackground(Graphics graphics, JMenuItem item, Color background) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (item.getModel().isArmed() || item.isSelected()) {
            g.setColor(destructive(item) ? new Color(52, 30, 34) : new Color(40, 43, 52));
            g.fillRoundRect(4, 1, item.getWidth() - 8, item.getHeight() - 2, 10, 10);
        }
        g.dispose();
    }

    @Override protected void paintText(Graphics graphics, JMenuItem item, Rectangle rect, String text) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(item.getFont());
        g.setColor(destructive(item) ? new Color(232, 96, 104) : LauncherStyle.INK);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, rect.x, rect.y + metrics.getAscent());
        g.dispose();
    }
}

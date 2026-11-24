package com.krovex.launcher.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

public final class PillScrollBarUI extends BasicScrollBarUI {
    private static final Color TRACK = new Color(32, 33, 37);
    private static final Color THUMB = new Color(88, 92, 102);
    private double highlight;
    private final Timer fade = new Timer(16, event -> animate());

    public static ComponentUI createUI(JComponent component) { return new PillScrollBarUI(); }

    @Override protected JButton createDecreaseButton(int orientation) { return hiddenButton(); }
    @Override protected JButton createIncreaseButton(int orientation) { return hiddenButton(); }

    private JButton hiddenButton() {
        JButton button = new JButton();
        Dimension zero = new Dimension();
        button.setPreferredSize(zero);
        button.setMinimumSize(zero);
        button.setMaximumSize(zero);
        button.setFocusable(false);
        return button;
    }

    @Override protected Dimension getMinimumThumbSize() { return new Dimension(12, 28); }

    @Override protected void setThumbRollover(boolean active) {
        if (active == isThumbRollover()) return;
        super.setThumbRollover(active);
        fade.start();
    }

    private void animate() {
        double target = isThumbRollover() ? 1 : 0;
        highlight += (target - highlight) * .25;
        if (Math.abs(target - highlight) < .025 || scrollbar == null || !scrollbar.isShowing()) {
            highlight = target;
            fade.stop();
        }
        if (scrollbar != null) scrollbar.repaint();
    }

    @Override protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
        paintPill(graphics, bounds, TRACK);
    }

    @Override protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
        if (!component.isEnabled() || bounds.isEmpty()) return;
        int lift = (int) Math.round(highlight * 16);
        paintPill(graphics, bounds, new Color(THUMB.getRed() + lift, THUMB.getGreen() + lift, THUMB.getBlue() + lift));
    }

    private void paintPill(Graphics graphics, Rectangle bounds, Color color) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.fillRoundRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2, 14, 14);
        g.dispose();
    }

    @Override public void uninstallUI(JComponent component) {
        fade.stop();
        super.uninstallUI(component);
    }
}

package com.krovex.launcher.ui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public final class MockupChrome {
    private MockupChrome() {}

    public static Icon icon(String type, int size) {
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y);
                g.scale(size / 32.0, size / 32.0);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(LauncherStyle.INK);
                switch (type) {
                    case "brand" -> {
                        g.setColor(LauncherStyle.ACCENT);
                        g.fillPolygon(new int[]{3, 8, 14, 11, 24, 31, 17, 27, 19, 12, 0},
                                new int[]{29, 5, 2, 15, 3, 1, 16, 29, 29, 21, 32}, 11);
                    }
                    case "microsoft" -> {
                        Color[] tiles = {new Color(242, 80, 34), new Color(127, 186, 0),
                                new Color(0, 164, 239), new Color(255, 185, 0)};
                        for (int i = 0; i < tiles.length; i++) {
                            g.setColor(tiles[i]); g.fillRect(i % 2 * 16 + 1, i / 2 * 16 + 1, 14, 14);
                        }
                    }
                    case "download" -> {
                        g.drawLine(16, 3, 16, 20);
                        g.drawPolyline(new int[]{10, 16, 22}, new int[]{14, 20, 14}, 3);
                        g.drawPolyline(new int[]{5, 5, 27, 27}, new int[]{22, 28, 28, 22}, 4);
                    }
                    case "home" -> {
                        g.drawPolygon(new int[]{4, 16, 28, 28, 4}, new int[]{14, 4, 14, 28, 28}, 5);
                        g.drawRect(13, 18, 6, 10);
                    }
                    case "plus" -> { g.drawLine(16, 5, 16, 27); g.drawLine(5, 16, 27, 16); }
                    case "gamepad" -> {
                        g.drawRoundRect(3, 8, 26, 15, 12, 12);
                        g.drawLine(11, 12, 11, 19);
                        g.drawLine(8, 15, 14, 15);
                        g.fillOval(19, 13, 3, 3);
                        g.fillOval(23, 17, 3, 3);
                    }
                    default -> {
                        g.drawPolygon(new int[]{4, 16, 28, 28, 16, 4}, new int[]{9, 3, 9, 23, 29, 23}, 6);
                        g.drawPolyline(new int[]{4, 16, 28}, new int[]{9, 15, 9}, 3);
                        g.drawLine(16, 15, 16, 29);
                    }
                }
                g.dispose();
            }
        };
    }

    public static void dimOwner(JDialog dialog) {
        if (!(dialog.getOwner() instanceof JFrame owner)) return;
        Component prior = owner.getGlassPane();
        boolean visible = prior.isVisible();
        JPanel shade = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                graphics.setColor(new Color(3, 4, 6, 158));
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        shade.setOpaque(false);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) {
                owner.setGlassPane(shade);
                shade.setVisible(true);
            }
            @Override public void windowClosed(WindowEvent event) {
                if (owner.getGlassPane() != shade) return;
                owner.setGlassPane(prior);
                prior.setVisible(visible);
            }
        });
    }
}

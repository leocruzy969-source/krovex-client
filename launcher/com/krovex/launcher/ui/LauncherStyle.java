package com.krovex.launcher.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.AbstractBorder;

public final class LauncherStyle {
    public static final Color BASE = new Color(13, 14, 17);
    public static final Color SURFACE = new Color(24, 26, 31);
    public static final Color EDGE = new Color(67, 71, 82);
    public static final Color INK = new Color(241, 245, 255);
    public static final Color MUTED = new Color(188, 191, 200);
    public static Color ACCENT = new Color(181, 245, 62);
    private static final Color DARK_INK = new Color(15, 20, 10);
    private LauncherStyle() {}

    public static void install() {
        var defaults = UIManager.getDefaults();
        defaults.put("ComboBoxUI", LauncherComboBoxUI.class.getName());
        defaults.put(LauncherComboBoxUI.class.getName(), LauncherComboBoxUI.class);
        defaults.put("PopupMenu.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(EDGE), BorderFactory.createEmptyBorder(6, 4, 6, 4)));
        defaults.put("MenuItemUI", LauncherMenuItemUI.class.getName());
        defaults.put(LauncherMenuItemUI.class.getName(), LauncherMenuItemUI.class);
        defaults.put("MenuItem.selectionBackground", new Color(49, 69, 24));
        defaults.put("MenuItem.selectionForeground", INK);
        defaults.put("ScrollBarUI", PillScrollBarUI.class.getName());
        defaults.put(PillScrollBarUI.class.getName(), PillScrollBarUI.class);
        defaults.put("ScrollPaneUI", SmoothScrollPaneUI.class.getName());
        defaults.put(SmoothScrollPaneUI.class.getName(), SmoothScrollPaneUI.class);
        defaults.put("ScrollBar.width", 14);
        defaults.put("ScrollBar.background", BASE);
        for (String kind : new String[]{"Panel", "Button", "ToggleButton", "TextField",
                "ComboBox", "Table", "TableHeader", "List", "Viewport", "ScrollPane",
                "MenuItem", "PopupMenu", "OptionPane", "Label", "CheckBox", "TabbedPane"}) {
            UIManager.put(kind + ".background", SURFACE);
            UIManager.put(kind + ".foreground", INK);
            UIManager.put(kind + ".font", new Font("Segoe UI", Font.PLAIN, 14));
        }
        UIManager.put("TextField.background", new Color(13, 14, 17));
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(outline(10),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        UIManager.put("Table.selectionBackground", new Color(48, 57, 34));
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TabbedPane.selected", SURFACE);
        UIManager.put("TabbedPane.contentAreaColor", BASE);
        UIManager.put("TabbedPane.focus", EDGE);
        UIManager.put("TabbedPane.borderHightlightColor", EDGE);
        UIManager.put("TabbedPane.darkShadow", EDGE);
        UIManager.put("TabbedPane.shadow", EDGE);
        UIManager.put("TabbedPane.light", EDGE);
        UIManager.put("TabbedPane.highlight", EDGE);
        UIManager.put("TableHeader.cellBorder", BorderFactory.createMatteBorder(0, 0, 1, 0, EDGE));
        UIManager.put("ToggleButton.select", new Color(48, 64, 30));
        UIManager.put("Table.gridColor", EDGE);
        UIManager.put("OptionPane.messageForeground", INK);
    }

    public static JPanel card(LayoutManager layout, int padding) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, new Color(30, 32, 40),
                        0, Math.max(1, getHeight()), SURFACE));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(outline(16),
                BorderFactory.createEmptyBorder(padding, padding, padding, padding)));
        return panel;
    }

    public static AbstractBorder outline(int radius) {
        return new AbstractBorder() {
            @Override public void paintBorder(Component component, Graphics graphics, int x, int y, int w, int h) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(component.isFocusOwner() ? ACCENT : EDGE);
                g.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
                g.dispose();
            }
        };
    }

    public static JButton button(String text, boolean primary, Runnable action) {
        JButton button = new JButton(text) {
            private Color fill;
            private Color from;
            private Color to;
            private long started;
            private static final double TRANSITION_NS = 150_000_000.0;
            private final Timer transition = new Timer(16, event -> {
                if (isShowing()) repaint();
                else ((Timer) event.getSource()).stop();
            });
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = primary && isEnabled() ? ACCENT : SURFACE;
                Color target = getModel().isPressed() ? base.darker() : getModel().isRollover() ? base.brighter() : base;
                if (fill == null) fill = target;
                if (!target.equals(to)) {
                    from = fill;
                    to = target;
                    started = System.nanoTime();
                }
                double elapsed = Math.min(1, (System.nanoTime() - started) / TRANSITION_NS);
                double progress = elapsed * elapsed * (3 - 2 * elapsed);
                fill = new Color(
                        (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * progress),
                        (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * progress),
                        (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * progress));
                if (elapsed < 1 && isShowing()) transition.start();
                else transition.stop();
                g.setPaint(new GradientPaint(0, 0, new Color(
                        Math.min(255, fill.getRed() + 9), Math.min(255, fill.getGreen() + 9),
                        Math.min(255, fill.getBlue() + 9)), 0, Math.max(1, getHeight()), fill));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g.dispose();
                setForeground(primary && isEnabled() ? DARK_INK : INK);
                super.paintComponent(graphics);
            }
        };
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setForeground(primary ? DARK_INK : INK);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setBorder(BorderFactory.createCompoundBorder(outline(12),
                BorderFactory.createEmptyBorder(9, 15, 9, 15)));
        button.addActionListener(event -> action.run());
        return button;
    }
}


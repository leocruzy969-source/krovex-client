package com.krovex.launcher.ui;

import com.krovex.launcher.model.MinecraftVersion;
import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

public final class LauncherComboBoxUI extends BasicComboBoxUI {
    private static final Color OUTLINE = new Color(117, 133, 169);
    private static final Color ROW = new Color(34, 37, 47);
    private static final Color ON = new Color(49, 69, 24);

    public static ComponentUI createUI(JComponent component) { return new LauncherComboBoxUI(); }

    @Override public void installUI(JComponent component) {
        super.installUI(component);
        comboBox.setOpaque(false);
        comboBox.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                     boolean selected, boolean focused) {
                String text = value instanceof MinecraftVersion version ? version.id() : java.util.Objects.toString(value, "");
                boolean current = index >= 0 && index == comboBox.getSelectedIndex();
                super.getListCellRendererComponent(list, text, index, selected, focused);
                putClientProperty("html.disable", true);
                setForeground(LauncherStyle.INK);
                setBackground(current ? ON : index < 0 ? LauncherStyle.SURFACE : selected ? ROW.brighter() : ROW);
                setOpaque(index >= 0);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, index < 0 ? 0 : 1, 0, new Color(78, 88, 112)),
                        BorderFactory.createEmptyBorder(7, 10, 7, 10)));
                setIconTextGap(10);
                setIcon(index < 0 ? null : current ? checkIcon() : null);
                setHorizontalTextPosition(SwingConstants.LEFT);
                return this;
            }
        });
    }

    @Override protected ComboPopup createPopup() {
        var popup = new BasicComboPopup(comboBox);
        popup.setBorder(BorderFactory.createLineBorder(OUTLINE));
        popup.setBackground(ROW);
        popup.setLightWeightPopupEnabled(true);
        return popup;
    }

    @Override protected JButton createArrowButton() {
        JButton arrow = new JButton(chevron());
        arrow.setContentAreaFilled(false);
        arrow.setFocusPainted(false);
        arrow.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 8));
        arrow.setFocusable(false);
        return arrow;
    }

    @Override public void paint(Graphics graphics, JComponent component) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(LauncherStyle.SURFACE);
        g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 12, 12);
        g.setColor(OUTLINE);
        g.drawRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 12, 12);
        g.dispose();
        super.paint(graphics, component);
    }

    @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean focused) {}

    private static Icon checkIcon() { return lineIcon(true); }
    public static Icon chevron() { return lineIcon(false); }
    private static Icon lineIcon(boolean check) {
        return new Icon() {
            public int getIconWidth() { return 15; }
            public int getIconHeight() { return 13; }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(check ? LauncherStyle.ACCENT : LauncherStyle.INK);
                g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (check) g.drawPolyline(new int[]{x + 1, x + 5, x + 13}, new int[]{y + 6, y + 10, y + 2}, 3);
                else g.drawPolyline(new int[]{x + 1, x + 7, x + 13}, new int[]{y + 4, y + 10, y + 4}, 3);
                g.dispose();
            }
        };
    }
}

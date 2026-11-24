package com.krovex.launcher.ui.screens;

import com.krovex.launcher.ui.LauncherStyle;
import java.awt.*;
import java.util.function.IntConsumer;
import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

final class ContentAction extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
    private record Transition(String name, long started, boolean enabled) {}
    private static final String TRANSITION = "krovex.contentTransition";
    private static final long DURATION = 160_000_000L;
    private final boolean trash;
    private JButton rendererButton;

    static void animate(JTable table, String name, boolean enabled) {
        var transition = new Transition(name, System.nanoTime(), enabled);
        table.putClientProperty(TRANSITION, transition);
        Timer timer = new Timer(16, null);
        timer.addActionListener(event -> {
            if (table.getClientProperty(TRANSITION) != transition) {
                timer.stop();
                return;
            }
            if (System.nanoTime() - transition.started() >= DURATION) {
                table.putClientProperty(TRANSITION, null);
                timer.stop();
            }
            table.repaint();
        });
        timer.start();
    }
    private final IntConsumer action;

    ContentAction(boolean trash, IntConsumer action) {
        this.trash = trash;
        this.action = action;
    }

    private JButton button(JTable table, Object value, boolean selected, int row) {
        boolean enabled = Boolean.TRUE.equals(value);
        double position = enabled ? 1 : 0;
        if (!trash && table.getClientProperty(TRANSITION) instanceof Transition transition
                && transition.enabled() == enabled && transition.name().equals(table.getValueAt(row, 0))) {
            double progress = Math.min(1, (double) (System.nanoTime() - transition.started()) / DURATION);
            double ease = progress * progress * (3 - 2 * progress);
            position = enabled ? ease : 1 - ease;
        }
        if (rendererButton == null) rendererButton = makeButton(table, enabled, position);
        rendererButton.putClientProperty("switch.position", position);
        return rendererButton;
    }

    private JButton makeButton(JTable table, boolean enabled, double position) {
        JButton button = new JButton(new Icon() {
            public int getIconWidth() { return 44; }
            public int getIconHeight() { return 24; }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double thumb = ((Number) ((JButton) component).getClientProperty("switch.position")).doubleValue();
                if (trash) {
                    g.setColor(new Color(239, 150, 158));
                    g.setStroke(new BasicStroke(1.7f));
                    g.drawLine(13, 6, 31, 6);
                    g.drawLine(19, 3, 25, 3);
                    g.drawRoundRect(16, 8, 12, 12, 3, 3);
                    g.drawLine(20, 11, 20, 17);
                    g.drawLine(24, 11, 24, 17);
                } else {
                    Color on = LauncherStyle.ACCENT;
                    g.setColor(new Color(blend(94, on.getRed(), thumb), blend(101, on.getGreen(), thumb),
                            blend(113, on.getBlue(), thumb)));
                    g.fillRoundRect(3, 3, 38, 18, 18, 18);
                    g.setColor(new Color(242, 247, 250));
                    g.fillOval(6 + (int) Math.round(17 * thumb), 5, 14, 14);
                }
                g.dispose();
            }
        });
        button.putClientProperty("switch.position", position);
        String label = trash ? "Delete content" : enabled ? "Disable content" : "Enable content";
        button.getAccessibleContext().setAccessibleName(label);
        button.setToolTipText(label);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                             boolean focused, int row, int column) {
        return button(table, value, selected, row);
    }

    @Override public Component getTableCellEditorComponent(JTable table, Object value, boolean selected,
                                                           int row, int column) {
        int modelRow = table.convertRowIndexToModel(row);
        boolean enabled = Boolean.TRUE.equals(value);
        JButton control = makeButton(table, enabled, enabled ? 1 : 0);
        control.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) {
                if (table.getClientProperty("content.keyboardFocus") == Boolean.TRUE) {
                    control.setBorder(LauncherStyle.outline(8));
                }
            }

            @Override public void focusLost(java.awt.event.FocusEvent event) {
                control.setBorder(BorderFactory.createEmptyBorder());
            }
        });
        control.addActionListener(event -> {
            cancelCellEditing();
            action.accept(modelRow);
        });
        return control;
    }

    private static int blend(int from, int to, double amount) {
        return (int) Math.round(from + (to - from) * amount);
    }

    @Override public Object getCellEditorValue() { return null; }
}

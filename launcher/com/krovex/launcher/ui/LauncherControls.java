package com.krovex.launcher.ui;

import java.awt.*;
import java.awt.geom.Path2D;
import javax.swing.*;
import javax.swing.plaf.basic.BasicSpinnerUI;

/** Modern controls (pill switch, stepper, checkbox, field) shared by the launcher pages. */
final class LauncherControls {
    private LauncherControls() {}

    /** Pill-shaped on/off switch with a sliding knob. Behaves like a JToggleButton. */
    static JToggleButton pillSwitch(boolean on) {
        JToggleButton toggle = new JToggleButton() {
            private float position = isSelected() ? 1f : 0f;
            private final Timer slide = new Timer(14, event -> {
                float target = isSelected() ? 1f : 0f;
                position += (target - position) * 0.35f;
                if (Math.abs(target - position) < 0.02f) { position = target; ((Timer) event.getSource()).stop(); }
                repaint();
            });
            @Override public void setSelected(boolean selected) {
                super.setSelected(selected);
                if (isShowing()) slide.start(); else position = selected ? 1f : 0f;
            }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                Color off = new Color(40, 42, 52);
                Color track = blend(off, LauncherStyle.ACCENT, position);
                g.setColor(track);
                g.fillRoundRect(0, 0, w - 1, h - 1, h, h);
                g.setColor(isSelected() ? track : LauncherStyle.EDGE);
                g.drawRoundRect(0, 0, w - 1, h - 1, h, h);
                int knob = h - 8;
                int x = 4 + Math.round((w - knob - 8) * position);
                g.setColor(isSelected() ? new Color(15, 20, 10) : new Color(104, 102, 122));
                g.fillOval(x, 4, knob, knob);
                g.dispose();
            }
        };
        toggle.setSelected(on);
        toggle.setPreferredSize(new Dimension(48, 26));
        toggle.setMinimumSize(new Dimension(48, 26));
        toggle.setContentAreaFilled(false);
        toggle.setBorderPainted(false);
        toggle.setFocusPainted(false);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return toggle;
    }

    /** Rounded checkbox with an accent tick. */
    static JCheckBox checkBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setOpaque(false);
        box.setFocusPainted(false);
        box.setForeground(LauncherStyle.INK);
        box.setIconTextGap(10);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.setIcon(tick(false));
        box.setSelectedIcon(tick(true));
        return box;
    }

    private static Icon tick(boolean checked) {
        return new Icon() {
            public int getIconWidth() { return 20; }
            public int getIconHeight() { return 20; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (checked) {
                    g.setColor(LauncherStyle.ACCENT);
                    g.fillRoundRect(x, y, 19, 19, 7, 7);
                    g.setColor(new Color(15, 20, 10));
                    g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D.Float path = new Path2D.Float();
                    path.moveTo(x + 5, y + 10); path.lineTo(x + 8.5, y + 13.5); path.lineTo(x + 14.5, y + 6);
                    g.draw(path);
                } else {
                    g.setColor(new Color(30, 32, 40));
                    g.fillRoundRect(x, y, 19, 19, 7, 7);
                    g.setColor(LauncherStyle.EDGE);
                    g.drawRoundRect(x, y, 19, 19, 7, 7);
                }
                g.dispose();
            }
        };
    }

    /** Spinner with round minus/plus buttons on either side of a centred value. */
    static JSpinner stepper(int value, int minimum, int maximum, int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(
                Math.max(minimum, Math.min(maximum, value)), minimum, maximum, step));
        spinner.setUI(new BasicSpinnerUI() {
            @Override protected Component createNextButton() { return arrow(true, SwingConstants.NORTH); }
            @Override protected Component createPreviousButton() { return arrow(false, SwingConstants.SOUTH); }
            private JButton arrow(boolean next, int direction) {
                JButton button = new JButton() {
                    @Override protected void paintComponent(Graphics graphics) {
                        Graphics2D g = (Graphics2D) graphics.create();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(getModel().isRollover() ? LauncherStyle.INK : LauncherStyle.MUTED);
                        int cx = getWidth() / 2, cy = getHeight() / 2;
                        int d = next ? -2 : 2;
                        g.fillPolygon(new int[]{cx - 3, cx + 3, cx}, new int[]{cy - d, cy - d, cy + d}, 3);
                        g.dispose();
                    }
                };
                button.setRolloverEnabled(true);
                button.setContentAreaFilled(false);
                button.setBorderPainted(false);
                button.setFocusPainted(false);
                button.setPreferredSize(new Dimension(20, 14));
                if (next) installNextButtonListeners(button); else installPreviousButtonListeners(button);
                return button;
            }
        });        spinner.setBorder(BorderFactory.createCompoundBorder(LauncherStyle.outline(10),
                BorderFactory.createEmptyBorder(3, 4, 3, 4)));
        spinner.setBackground(new Color(13, 14, 17));
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            JTextField field = editor.getTextField();
            field.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setBackground(new Color(13, 14, 17));
            field.setForeground(LauncherStyle.INK);
            field.setColumns(5);
        }
        return spinner;
    }

    /** Text field with a soft recessed fill. */
    static JTextField field(String text, int columns) {
        JTextField field = new JTextField(text, columns);
        field.setBackground(new Color(13, 14, 17));
        field.setForeground(LauncherStyle.INK);
        field.setCaretColor(LauncherStyle.ACCENT);
        return field;
    }

    /** Small rounded label chip. */
    static JComponent chip(String text, Color fill, Color ink) {
        JLabel label = new JLabel(text, SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        label.setForeground(ink);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        return label;
    }

    /** Round accent badge with a tick. */
    static JComponent checkBadge() {
        return new JComponent() {
            { setPreferredSize(new Dimension(26, 26)); }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(LauncherStyle.ACCENT);
                g.fillOval(1, 1, 24, 24);
                g.setColor(new Color(15, 20, 10));
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolyline(new int[]{7, 11, 19}, new int[]{13, 17, 9}, 3);
                g.dispose();
            }
        };
    }

    /** Borderless sidebar button with a soft hover fill. */
    static JButton flatNav(String text, Icon icon, Runnable action) {
        Hover[] hover = new Hover[1];
        JButton button = new JButton(text, icon) {
            @Override protected void paintComponent(Graphics graphics) {
                float t = hover[0] == null ? 0 : hover[0].amount();
                if (t > 0.01f) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(34, 36, 44, Math.round(255 * t)));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        hover[0] = new Hover(button);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setForeground(LauncherStyle.INK);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        button.setIconTextGap(10);
        button.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 10));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(event -> action.run());
        return button;
    }

    static Color blend(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}

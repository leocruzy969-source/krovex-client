package com.krovex.launcher.ui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Custom title bar (drag, double-click maximize, min/max/close) and edge resizing for an undecorated frame. */
final class WindowChrome {
    private static final int EDGE = 6;
    private WindowChrome() {}

    static JComponent install(JFrame frame) {
        frame.setUndecorated(true);
        frame.getRootPane().setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(38, 40, 48)),
                BorderFactory.createEmptyBorder(EDGE - 1, EDGE - 1, EDGE - 1, EDGE - 1)));
        frame.getRootPane().setBackground(LauncherStyle.BASE);
        frame.getRootPane().addPropertyChangeListener(event -> { });
        frame.setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        installResize(frame);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(13, 14, 17));
        bar.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        JLabel title = new JLabel("Krovex Client", MockupChrome.icon("brand", 16), SwingConstants.LEFT);
        title.setIconTextGap(8);
        title.setForeground(LauncherStyle.MUTED);
        title.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bar.add(title, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(control("min", frame, () -> frame.setState(Frame.ICONIFIED)));
        buttons.add(control("max", frame, () -> toggle(frame)));
        buttons.add(control("close", frame, () -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING))));
        bar.add(buttons, BorderLayout.EAST);
        bar.setPreferredSize(new Dimension(10, 34));

        MouseAdapter drag = new MouseAdapter() {
            private Point origin;
            @Override public void mousePressed(MouseEvent event) {
                origin = SwingUtilities.isLeftMouseButton(event) ? event.getPoint() : null;
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (origin == null) return;
                if (maximized(frame)) return;
                Point screen = event.getLocationOnScreen();
                frame.setLocation(screen.x - origin.x - EDGE, screen.y - origin.y - EDGE);
            }
            @Override public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2) toggle(frame);
            }
        };
        for (Component part : new Component[]{bar, title}) {
            part.addMouseListener(drag);
            part.addMouseMotionListener(drag);
        }
        return bar;
    }

    private static final String MAX = "krovex.maximized";
    private static final String RESTORE_TO = "krovex.restoreBounds";
    private static Timer animation;

    static boolean maximized(JFrame frame) { return frame.getRootPane().getClientProperty(MAX) == Boolean.TRUE; }

    /** Animated maximize / restore (the frame is undecorated, so we move the bounds ourselves). */
    private static void toggle(JFrame frame) {
        if (animation != null && animation.isRunning()) return;
        boolean wasMaximized = maximized(frame);
        Rectangle from = frame.getBounds();
        Rectangle to;
        if (wasMaximized) {
            to = frame.getRootPane().getClientProperty(RESTORE_TO) instanceof Rectangle r ? r : new Rectangle(from.x + 80, from.y + 60, 1380, 865);
        } else {
            frame.getRootPane().putClientProperty(RESTORE_TO, from);
            to = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        }
        frame.getRootPane().putClientProperty(MAX, !wasMaximized);
        frame.getRootPane().repaint();
        long started = System.nanoTime();
        final Rectangle target = to;
        animation = new Timer(8, event -> {
            double t = Math.min(1.0, (System.nanoTime() - started) / 190_000_000.0);
            double e = 1 - Math.pow(1 - t, 3);
            frame.setBounds((int) Math.round(from.x + (target.x - from.x) * e),
                    (int) Math.round(from.y + (target.y - from.y) * e),
                    (int) Math.round(from.width + (target.width - from.width) * e),
                    (int) Math.round(from.height + (target.height - from.height) * e));
            if (t >= 1.0) ((Timer) event.getSource()).stop();
        });
        animation.start();
    }

    private static JButton control(String kind, JFrame frame, Runnable action) {
        Hover[] hover = new Hover[1];
        JButton button = new JButton() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean close = kind.equals("close");
                float t = hover[0] == null ? 0 : hover[0].amount();
                if (t > 0.01f) {
                    Color fill = close ? new Color(196, 52, 66) : new Color(38, 40, 48);
                    g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), Math.round(255 * t)));
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                g.setColor(close ? LauncherControls.blend(new Color(232, 96, 104), LauncherStyle.INK, t) : LauncherStyle.INK);
                g.setStroke(new BasicStroke(1.3f));
                int cx = getWidth() / 2, cy = getHeight() / 2;
                switch (kind) {
                    case "min" -> g.drawLine(cx - 5, cy, cx + 5, cy);
                    case "max" -> {
                        if (maximized(frame)) {
                            g.drawRect(cx - 5, cy - 2, 7, 7);
                            g.drawPolyline(new int[]{cx - 2, cx - 2, cx + 5, cx + 5, cx + 2}, new int[]{cy - 2, cy - 5, cy - 5, cy + 2, cy + 2}, 5);
                        } else g.drawRect(cx - 5, cy - 5, 10, 10);
                    }
                    default -> { g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5); g.drawLine(cx - 5, cy + 5, cx + 5, cy - 5); }
                }
                g.dispose();
            }
        };
        hover[0] = new Hover(button);
        button.setPreferredSize(new Dimension(46, 34));
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.getAccessibleContext().setAccessibleName(kind.equals("close") ? "Close" : kind.equals("max") ? "Maximize" : "Minimize");
        button.addActionListener(event -> action.run());
        return button;
    }

    private static void installResize(JFrame frame) {
        JRootPane root = frame.getRootPane();
        MouseAdapter resize = new MouseAdapter() {
            private int edges;
            private Point start;
            private Rectangle bounds;
            private int edgesAt(MouseEvent event) {
                int e = 0, w = root.getWidth(), h = root.getHeight();
                if (event.getX() < EDGE) e |= 1;
                if (event.getX() >= w - EDGE) e |= 2;
                if (event.getY() < EDGE) e |= 4;
                if (event.getY() >= h - EDGE) e |= 8;
                return e;
            }
            @Override public void mouseMoved(MouseEvent event) {
                int e = maximized(frame) ? 0 : edgesAt(event);
                int cursor = switch (e) {
                    case 1 -> Cursor.W_RESIZE_CURSOR; case 2 -> Cursor.E_RESIZE_CURSOR;
                    case 4 -> Cursor.N_RESIZE_CURSOR; case 8 -> Cursor.S_RESIZE_CURSOR;
                    case 5 -> Cursor.NW_RESIZE_CURSOR; case 6 -> Cursor.NE_RESIZE_CURSOR;
                    case 9 -> Cursor.SW_RESIZE_CURSOR; case 10 -> Cursor.SE_RESIZE_CURSOR;
                    default -> Cursor.DEFAULT_CURSOR;
                };
                root.setCursor(Cursor.getPredefinedCursor(cursor));
            }
            @Override public void mousePressed(MouseEvent event) {
                edges = maximized(frame) ? 0 : edgesAt(event);
                start = event.getLocationOnScreen();
                bounds = frame.getBounds();
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (edges == 0 || start == null) return;
                Point now = event.getLocationOnScreen();
                int dx = now.x - start.x, dy = now.y - start.y;
                Rectangle next = new Rectangle(bounds);
                Dimension min = frame.getMinimumSize();
                if ((edges & 1) != 0) { next.x += dx; next.width -= dx; }
                if ((edges & 2) != 0) next.width += dx;
                if ((edges & 4) != 0) { next.y += dy; next.height -= dy; }
                if ((edges & 8) != 0) next.height += dy;
                if (next.width < min.width) { if ((edges & 1) != 0) next.x -= min.width - next.width; next.width = min.width; }
                if (next.height < min.height) { if ((edges & 4) != 0) next.y -= min.height - next.height; next.height = min.height; }
                frame.setBounds(next);
            }
            @Override public void mouseReleased(MouseEvent event) { edges = 0; }
        };
        root.addMouseListener(resize);
        root.addMouseMotionListener(resize);
    }
}

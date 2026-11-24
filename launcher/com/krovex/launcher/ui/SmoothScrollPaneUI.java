package com.krovex.launcher.ui;

import java.awt.Component;
import java.awt.event.MouseWheelListener;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollPaneUI;

public final class SmoothScrollPaneUI extends BasicScrollPaneUI {
    private Timer timer;
    private JScrollBar active;
    private int target;
    private int lastValue;
    private double position;

    public static ComponentUI createUI(JComponent component) { return new SmoothScrollPaneUI(); }

    @Override protected MouseWheelListener createMouseWheelListener() {
        var standard = super.createMouseWheelListener();
        return event -> {
            if (event.isConsumed() || !scrollpane.isWheelScrollingEnabled()) return;
            JScrollBar bar = event.isShiftDown() ? scrollpane.getHorizontalScrollBar() : scrollpane.getVerticalScrollBar();
            if (!bar.isVisible() && !event.isShiftDown()) bar = scrollpane.getHorizontalScrollBar();
            if (!bar.isVisible() || event.getScrollType() != java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL) {
                stop();
                standard.mouseWheelMoved(event);
                return;
            }
            double rotation = event.getPreciseWheelRotation();
            if (rotation == 0) return;
            int direction = rotation < 0 ? -1 : 1;
            int current = bar.getValue();
            if (active != bar || current != lastValue) {
                active = bar;
                position = target = lastValue = current;
            }
            int step = Math.max(12, bar.getUnitIncrement(direction));
            int delta = (int) Math.round(rotation * event.getScrollAmount() * step);
            int next = Math.clamp((long) target + delta, bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
            if (next == target && target == current) {
                for (Component parent = scrollpane.getParent(); parent != null; parent = parent.getParent()) {
                    if (parent instanceof JScrollPane outer) {
                        outer.dispatchEvent(SwingUtilities.convertMouseEvent(scrollpane, event, outer));
                        event.consume();
                        break;
                    }
                }
                return;
            }
            target = next;
            event.consume();
            if (timer == null) timer = new Timer(15, tick -> advance());
            timer.start();
        };
    }

    private void advance() {
        if (active == null || !scrollpane.isShowing() || active.getValue() != lastValue || active.getValueIsAdjusting()) {
            stop();
            return;
        }
        position += (target - position) * .25;
        if (Math.abs(target - position) < 1) position = target;
        active.setValue((int) Math.round(position));
        lastValue = active.getValue();
        if (position == target || target > active.getMaximum() - active.getVisibleAmount()) stop();
    }

    private void stop() {
        if (timer != null) timer.stop();
        active = null;
    }

    @Override public void uninstallUI(JComponent component) {
        stop();
        super.uninstallUI(component);
    }
}

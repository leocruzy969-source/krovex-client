package com.krovex.launcher.ui;

import java.awt.event.*;
import javax.swing.*;

/** Smooth 0..1 hover amount for a button: eases toward 1 on enter/press, back to 0 on exit. */
final class Hover {
    private final AbstractButton button;
    private float amount;
    private final Timer timer;

    Hover(AbstractButton button) {
        this.button = button;
        timer = new Timer(15, event -> {
            float target = button.getModel().isRollover() || button.getModel().isPressed() ? 1f : 0f;
            amount += (target - amount) * 0.28f;
            if (Math.abs(target - amount) < 0.02f) { amount = target; ((Timer) event.getSource()).stop(); }
            button.repaint();
        });
        button.setRolloverEnabled(true);
        button.getModel().addChangeListener(event -> { if (button.isShowing() && !timer.isRunning()) timer.start(); });
    }

    float amount() { return amount; }
}
package com.krovex.launcher.ui;

import com.krovex.theme.KrovexTheme;
import java.awt.Color;
import java.awt.Font;

public final class SwingThemeUtil {
    private SwingThemeUtil() {
    }

    public static Color accent(KrovexTheme t) {
        return LauncherStyle.ACCENT;
    }

    public static Color background(KrovexTheme t) {
        return LauncherStyle.BASE;
    }

    public static Color panel(KrovexTheme t) {
        return LauncherStyle.SURFACE;
    }

    public static Color text(KrovexTheme t) {
        return LauncherStyle.INK;
    }

    public static Color panelHover(KrovexTheme t) {
        Color p = SwingThemeUtil.panel(t);
        return new Color(Math.min(255, p.getRed() + 14), Math.min(255, p.getGreen() + 14), Math.min(255, p.getBlue() + 14), p.getAlpha());
    }

    public static Color subtleText(KrovexTheme t) {
        Color c = SwingThemeUtil.text(t);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 160);
    }

    public static Font baseFont(KrovexTheme t, int style) {
        Font f = new Font(t.fontFamily(), style, t.fontSize());
        if (!f.getFamily().equalsIgnoreCase(t.fontFamily())) {
            f = new Font("SansSerif", style, t.fontSize());
        }
        return f;
    }

    public static Font titleFont(KrovexTheme t) {
        return SwingThemeUtil.baseFont(t, 1).deriveFont((float)(t.fontSize() + 6));
    }

    public static Font headingFont(KrovexTheme t) {
        return SwingThemeUtil.baseFont(t, 1).deriveFont((float)(t.fontSize() + 2));
    }
}


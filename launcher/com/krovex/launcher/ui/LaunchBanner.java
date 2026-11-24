package com.krovex.launcher.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class LaunchBanner extends JPanel {
    private static final BufferedImage LANDSCAPE = load();
    private final float shade;
    private BufferedImage backdrop;
    private int cachedWidth;
    private int cachedHeight;
    private double cachedScaleX;
    private double cachedScaleY;

    public LaunchBanner(int height, float shade) {
        super(new BorderLayout(14, 0));
        this.shade = shade;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        setAlignmentX(LEFT_ALIGNMENT);
        setPreferredSize(new Dimension(610, height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private static BufferedImage load() {
        try (var stream = LaunchBanner.class.getResourceAsStream("/assets/krovex-valley.png")) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (java.io.IOException ignored) { return null; }
    }

    public static Icon thumbnail(int width, int height, int variant) {
        if (LANDSCAPE == null) return null;
        return new Icon() {
            public int getIconWidth() { return width; }
            public int getIconHeight() { return height; }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y);
                g.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, width, height, 14, 14));
                paintLandscape(g, width, height, 0.4 + Math.floorMod(variant, 5) * 0.13);
                g.dispose();
            }
        };
    }

    private static void paintLandscape(Graphics2D g, int w, int h, double position) {
        if (LANDSCAPE == null) {
            g.setPaint(new GradientPaint(0, 0, new Color(28, 32, 23), w, h, LauncherStyle.EDGE));
            g.fillRect(0, 0, w, h);
            return;
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double scale = Math.max((double) w / LANDSCAPE.getWidth(), (double) h / LANDSCAPE.getHeight());
        int width = (int) Math.ceil(LANDSCAPE.getWidth() * scale);
        int height = (int) Math.ceil(LANDSCAPE.getHeight() * scale);
        g.drawImage(LANDSCAPE, (int) ((w - width) * position), (h - height) / 2, width, height, null);
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) { g.dispose(); return; }
        g.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, 20, 20));
        double scaleX = g.getTransform().getScaleX();
        double scaleY = g.getTransform().getScaleY();
        if (backdrop == null || cachedWidth != w || cachedHeight != h
                || cachedScaleX != scaleX || cachedScaleY != scaleY) {
            cachedWidth = w;
            cachedHeight = h;
            cachedScaleX = scaleX;
            cachedScaleY = scaleY;
            backdrop = new BufferedImage((int) Math.ceil(w * scaleX), (int) Math.ceil(h * scaleY),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D image = backdrop.createGraphics();
            image.scale(scaleX, scaleY);
            paintLandscape(image, w, h, .62);
            image.setPaint(new GradientPaint(0, 0, new Color(8, 10, 12, (int) (255 * shade)),
                    w * .80f, 0, new Color(8, 10, 12, 25)));
            image.fillRect(0, 0, w, h);
            image.dispose();
        }
        g.drawImage(backdrop, 0, 0, w, h, null);
        g.dispose();
    }
}

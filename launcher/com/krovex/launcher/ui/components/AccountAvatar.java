package com.krovex.launcher.ui.components;

import com.krovex.launcher.model.MicrosoftAccount;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;

final class AccountAvatar {
    private AccountAvatar() {}

    static void load(MicrosoftAccount account, JButton target) {
        if (account.skinUrl() == null) return;
        new SwingWorker<Icon, Void>() {
            @Override protected Icon doInBackground() throws IOException {
                URI uri = URI.create(account.skinUrl());
                if (!"textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                        || uri.getPath() == null || !uri.getPath().matches("/texture/[a-fA-F0-9]+")) return null;
                URI texture = URI.create("https://textures.minecraft.net" + uri.getPath());
                var connection = (HttpsURLConnection) texture.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(3500);
                connection.setReadTimeout(3500);
                try (var input = connection.getInputStream()) {
                    byte[] bytes = input.readNBytes(131073);
                    if (bytes.length > 131072) return null;
                    try (var image = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                        var readers = ImageIO.getImageReaders(image);
                        if (!readers.hasNext()) return null;
                        var reader = readers.next();
                        try {
                            reader.setInput(image);
                            int height = reader.getHeight(0);
                            if (reader.getWidth(0) != 64 || (height != 32 && height != 64)) return null;
                            return face(reader.read(0));
                        } finally { reader.dispose(); }
                    }
                } finally { connection.disconnect(); }
            }

            @Override protected void done() {
                try {
                    Icon icon = get();
                    if (icon == null || target.getParent() == null) return;
                    target.add(new JLabel(icon), BorderLayout.WEST);
                    target.revalidate();
                    target.repaint();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ignored) {
                    // Unavailable textures leave the account name visible.
                }
            }
        }.execute();
    }

    static Icon face(BufferedImage skin) {
        var head = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        var g = head.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(skin, 0, 0, 40, 40, 8, 8, 16, 16, null);
        g.drawImage(skin, 0, 0, 40, 40, 40, 8, 48, 16, null);
        g.dispose();
        return new ImageIcon(head);
    }

    static Icon removeIcon() {
        return new Icon() {
            public int getIconWidth() { return 15; }
            public int getIconHeight() { return 15; }
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                var g = (Graphics2D) graphics.create();
                g.setColor(new Color(248, 102, 111));
                g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x + 3, y + 3, x + 11, y + 11);
                g.drawLine(x + 11, y + 3, x + 3, y + 11);
                g.dispose();
            }
        };
    }

    static void slide(JPanel panel, int expandedHeight) {
        if (panel.getClientProperty("animation") instanceof Timer previous) previous.stop();
        int start = panel.getMaximumSize().height;
        boolean opening = !Boolean.TRUE.equals(panel.getClientProperty("expanded"));
        panel.putClientProperty("expanded", opening);
        int target = opening ? expandedHeight : 0;
        panel.setVisible(true);
        long started = System.nanoTime();
        Timer timer = new Timer(15, event -> {
            double elapsed = Math.min(1, (System.nanoTime() - started) / 165_000_000.0);
            double progress = 1 - Math.pow(1 - elapsed, 3);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    (int) Math.round(start + (target - start) * progress)));
            panel.revalidate();
            if (panel.getParent() != null) panel.getParent().repaint();
            if (elapsed >= 1 || panel.getParent() == null) {
                ((Timer) event.getSource()).stop();
                if (!opening) panel.setVisible(false);
            }
        });
        panel.putClientProperty("animation", timer);
        timer.start();
    }
}

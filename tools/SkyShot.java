package com.krovex.client.sky;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Offline software renderer for the sky meshes (what the in-game pass draws), for tuning the presets by eye. */
public final class SkyShot {
    static int W = 1100, H = 620;

    static SkyPreset presetFor(String name) throws Exception {
        return (SkyPreset) SkyPreset.class.getMethod(name).invoke(null);
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(System.getProperty("shot.out"));
        double t = Double.parseDouble(System.getProperty("shot.t", "4.2"));
        for (String name : args) {
            SkyPreset p = presetFor(name);
            var built = SkyRenderer.buildForPreview(new SkyRenderer.MeshKey(p.kind(), p.primaryColorArgb(), p.secondaryColorArgb()));
            for (int view = 0; view < 2; view++) {
                double yaw = Math.toRadians(view == 0 ? 40 : 215), pitch = Math.toRadians(view == 0 ? 16 : 52);
                BufferedImage img = render(built, p, t, yaw, pitch);
                javax.imageio.ImageIO.write(img, "png", out.resolve(name + "-" + view + ".png").toFile());
            }
            System.out.println(name + " quads=" + built.mesh().count / 4 + " layers=" + built.layers().size());
        }
    }

    static BufferedImage render(SkyRenderer.Built built, SkyPreset p, double t, double yaw, double pitch) {
        float[] r = new float[W * H], g = new float[W * H], b = new float[W * H];
        double fx = Math.cos(pitch) * Math.cos(yaw), fy = Math.sin(pitch), fz = Math.cos(pitch) * Math.sin(yaw);
        double rx = -Math.sin(yaw), ry = 0, rz = Math.cos(yaw);                       // right
        double ux = fy * rz - fz * ry, uy = fz * rx - fx * rz, uz = fx * ry - fy * rx;  // up = f x r
        double ul = Math.sqrt(ux * ux + uy * uy + uz * uz); ux /= ul; uy /= ul; uz /= ul;
        if (uy < 0) { ux = -ux; uy = -uy; uz = -uz; }
        double focal = (H / 2.0) / Math.tan(Math.toRadians(75) / 2);
        float rotation = (float) (t * p.animationSpeed() * .045);
        var mesh = built.mesh();
        for (SkyRenderer.Layer layer : built.layers()) {
            float level = layer.pulse() == null ? 1f : layer.pulse().at(t);
            if (level <= .004f) continue;
            float turn = rotation * layer.spin() + (layer.drift() == null ? 0f : layer.drift().at(t));
            float dy = layer.rise() == null ? 0f : layer.rise().at(t);
            float opacity = layer.solid() ? Math.min(1f, level) : Math.min(1f, p.intensity() * level);
            double cs = Math.cos(turn), sn = Math.sin(turn);
            int n = layer.quads() * 4;
            double[][] sv = new double[n][];
            for (int i = 0; i < n; i++) {
                int idx = layer.baseVertex() + i;
                double x = mesh.pos[idx * 3], y = mesh.pos[idx * 3 + 1], z = mesh.pos[idx * 3 + 2];
                double x2 = x * cs + z * sn, z2 = -x * sn + z * cs, y2 = y + dy;
                double xc = x2 * rx + y2 * ry + z2 * rz, yc = x2 * ux + y2 * uy + z2 * uz, zc = x2 * fx + y2 * fy + z2 * fz;
                if (zc <= .5) { sv[i] = null; continue; }
                int c = mesh.col[idx];
                sv[i] = new double[]{W / 2.0 + xc / zc * focal, H / 2.0 - yc / zc * focal, c >> 16 & 255, c >> 8 & 255, c & 255, (c >>> 24) * (double) opacity};
            }
            for (int q = 0; q < layer.quads(); q++) {
                double[] a = sv[q * 4], bb = sv[q * 4 + 1], cc = sv[q * 4 + 2], d = sv[q * 4 + 3];
                if (a == null || bb == null || cc == null || d == null) continue;
                tri(r, g, b, a, bb, cc);
                tri(r, g, b, a, cc, d);
            }
        }
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < H; py++) for (int px = 0; px < W; px++) {
            // ground: flat dark grass for rays that point below the horizon
            double dx = (px + .5 - W / 2.0) / focal, dyy = -(py + .5 - H / 2.0) / focal;
            double vy = fy + dyy * uy + dx * ry;
            int i = py * W + px;
            float rr = r[i], gg = g[i], bb = b[i];
            if (vy < -0.0005) { double k = Math.min(1, -vy * 6); rr = (float) (22 + 12 * (1 - k)); gg = (float) (46 + 16 * (1 - k)); bb = (float) (20 + 8 * (1 - k)); }
            img.setRGB(px, py, clamp(rr) << 16 | clamp(gg) << 8 | clamp(bb));
        }
        return img;
    }

    static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v))); }

    static void tri(float[] r, float[] g, float[] b, double[] p0, double[] p1, double[] p2) {
        double minX = Math.min(p0[0], Math.min(p1[0], p2[0])), maxX = Math.max(p0[0], Math.max(p1[0], p2[0]));
        double minY = Math.min(p0[1], Math.min(p1[1], p2[1])), maxY = Math.max(p0[1], Math.max(p1[1], p2[1]));
        if (maxX < 0 || maxY < 0 || minX >= W || minY >= H || maxX - minX > 3000) return;
        int x0 = Math.max(0, (int) Math.floor(minX)), x1 = Math.min(W - 1, (int) Math.ceil(maxX));
        int y0 = Math.max(0, (int) Math.floor(minY)), y1 = Math.min(H - 1, (int) Math.ceil(maxY));
        double den = (p1[1] - p2[1]) * (p0[0] - p2[0]) + (p2[0] - p1[0]) * (p0[1] - p2[1]);
        if (Math.abs(den) < 1e-9) return;
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) {
            double px = x + .5, py = y + .5;
            double l0 = ((p1[1] - p2[1]) * (px - p2[0]) + (p2[0] - p1[0]) * (py - p2[1])) / den;
            double l1 = ((p2[1] - p0[1]) * (px - p2[0]) + (p0[0] - p2[0]) * (py - p2[1])) / den;
            double l2 = 1 - l0 - l1;
            if (l0 < -1e-6 || l1 < -1e-6 || l2 < -1e-6) continue;
            double a = (l0 * p0[5] + l1 * p1[5] + l2 * p2[5]) / 255.0;
            if (a <= 0) continue;
            double cr = l0 * p0[2] + l1 * p1[2] + l2 * p2[2], cg = l0 * p0[3] + l1 * p1[3] + l2 * p2[3], cb = l0 * p0[4] + l1 * p1[4] + l2 * p2[4];
            int i = y * W + x;
            r[i] = (float) (cr * a + r[i] * (1 - a));
            g[i] = (float) (cg * a + g[i] * (1 - a));
            b[i] = (float) (cb * a + b[i] * (1 - a));
        }
    }
}
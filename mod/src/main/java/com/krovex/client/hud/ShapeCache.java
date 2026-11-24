package com.krovex.client.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rounded cards (fill gradient + outline) drawn as one or two textured quads instead of hundreds of 1-pixel fills.
 * Each distinct shape is rasterised once, with smooth anti-aliased edges, into a small texture and reused every
 * frame. Outlines and single-colour fills are stored as WHITE masks and tinted when drawn, so a colour that changes
 * every frame (the RGB wave) never creates new textures.
 */
final class ShapeCache {
    /** kind 0 = gradient fill (top/bottom baked in), 1 = white fill mask, 2 = white outline mask. */
    private record Key(int kind, int w, int h, int r, int t, int top, int bottom) {}
    private record Doomed(Identifier id, long frame) {}

    private static final int LIMIT = 320, MAX_PIXELS = 700 * 400;
    private static final Map<Key, Identifier> CACHE = new LinkedHashMap<>(512, .75f, true);
    private static final ArrayDeque<Doomed> DOOMED = new ArrayDeque<>();
    private static long frame;
    private static int counter;

    private ShapeCache() {}

    /** Call once per rendered frame; frees textures that were evicted a few frames ago. */
    static void nextFrame() {
        frame++;
        var textures = MinecraftClient.getInstance().getTextureManager();
        while (!DOOMED.isEmpty() && frame - DOOMED.peekFirst().frame() > 4) textures.destroyTexture(DOOMED.pollFirst().id());
    }

    /**
     * Draws the card at (x, y) in the CURRENT coordinate space, w x h of it (callers use screen pixels).
     * Returns false when the card is too big to cache so the caller can draw it the slow way.
     */
    static boolean draw(DrawContext c, int x, int y, int w, int h, int r, int thickness, int edge, int top, int bottom) {
        if (w <= 0 || h <= 0) return true;
        if ((long) w * h > MAX_PIXELS) return false;
        r = Math.min(r, Math.min(w, h) / 2);
        boolean hasFill = (top >>> 24) > 0 || (bottom >>> 24) > 0;
        if (hasFill) {
            if (top == bottom) {
                Identifier mask = get(new Key(1, w, h, r, 0, 0, 0));
                if (mask == null) return false;
                c.drawTexture(RenderPipelines.GUI_TEXTURED, mask, x, y, 0f, 0f, w, h, w, h, top);
            } else {
                Identifier fill = get(new Key(0, w, h, r, 0, top, bottom));
                if (fill == null) return false;
                c.drawTexture(RenderPipelines.GUI_TEXTURED, fill, x, y, 0f, 0f, w, h, w, h);
            }
        }
        if (thickness > 0 && (edge >>> 24) > 0) {
            Identifier outline = get(new Key(2, w, h, r, thickness, 0, 0));
            if (outline == null) return false;
            c.drawTexture(RenderPipelines.GUI_TEXTURED, outline, x, y, 0f, 0f, w, h, w, h, edge);
        }
        return true;
    }

    private static Identifier get(Key key) {
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        id = create(key);
        if (id == null) return null;
        CACHE.put(key, id);
        while (CACHE.size() > LIMIT) {
            var eldest = CACHE.entrySet().iterator().next();
            DOOMED.add(new Doomed(eldest.getValue(), frame));
            CACHE.remove(eldest.getKey());
        }
        return id;
    }

    private static Identifier create(Key k) {
        try {
            NativeImage image = new NativeImage(k.w(), k.h(), false);
            double hw = k.w() / 2.0, hh = k.h() / 2.0, r = k.r();
            for (int py = 0; py < k.h(); py++) {
                int rowColour = k.kind() == 0 ? blend(k.top(), k.bottom(), (py + .5f) / k.h()) : 0xFFFFFFFF;
                for (int px = 0; px < k.w(); px++) {
                    double qx = Math.abs(px + .5 - hw) - (hw - r), qy = Math.abs(py + .5 - hh) - (hh - r);
                    double d = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - r;
                    double cover = Math.clamp(.5 - d, 0, 1);            // d is negative inside the shape
                    if (k.kind() == 2) cover *= Math.clamp(k.t() + d + .5, 0, 1);   // keep only the band along the edge
                    if (cover <= 0) { image.setColorArgb(px, py, 0); continue; }
                    int a = (int) Math.round((rowColour >>> 24) * cover);
                    image.setColorArgb(px, py, a << 24 | rowColour & 0xFFFFFF);
                }
            }
            Identifier id = Identifier.of("krovex-client", "shape/" + (counter++));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "krovex_shape", image));
            return id;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int blend(int a, int b, float t) {
        int out = 0;
        for (int shift = 24; shift >= 0; shift -= 8) {
            int ca = a >> shift & 255, cb = b >> shift & 255;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }
}

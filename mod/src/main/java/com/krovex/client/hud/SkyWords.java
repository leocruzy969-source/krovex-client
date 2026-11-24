package com.krovex.client.hud;

import com.krovex.client.gui.KrovexFontRegistry;
import com.krovex.client.music.LrcLyrics;
import com.krovex.client.music.MusicPreferences;
import com.krovex.theme.KrovexTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Random;

/**
 * Sky lyrics: every word of the line being sung pops up as its own big word hanging in the sky around where you
 * are looking, then fades. With "follow your view" off a word stays where it appeared in the world (turn your head
 * and it slides across the screen); on, it stays at the same spot on screen.
 *
 * Each word is drawn LETTER BY LETTER (one draw per glyph, advancing by the glyph width plus the "Letter spacing"
 * setting), so the breathing room between letters is adjustable. Letters rise in one after another, words crossfade
 * into the next line, new words pick a spot away from the words already hanging, and the outline has a dark halo so
 * it stays readable on any sky (light or dark) whatever the theme colour is.
 */
final class SkyWords {
    private static final long LIFE_MS = 4200, FADE_IN_MS = 240, FADE_OUT_MS = 700, LINE_LINGER_MS = 1500, LETTER_STAGGER_MS = 32;
    private static final int[] CUTS = {14, 16, 20, 24, 28, 32};
    private static final double[] COS = new double[8], SIN = new double[8];
    static {
        for (int k = 0; k < 8; k++) { COS[k] = Math.cos(k * Math.PI / 4); SIN[k] = Math.sin(k * Math.PI / 4); }
    }

    private static final class Word {
        String text;
        long born;
        long endedAt;                 // when the line this word belongs to finished being sung (0 = still being sung)
        float worldYaw, worldPitch;   // where it hangs in the world
        float relYaw, relPitch;       // where it sits relative to the view when it appeared
        float tilt;                   // degrees
    }

    private final ArrayList<Word> words = new ArrayList<>();
    private final Random random = new Random();
    private int line = Integer.MIN_VALUE, spawned, counter;
    private LrcLyrics lastLyrics;
    private long lastProgress;

    private static int withAlpha(int argb, float alpha) {
        return argb & 0xFFFFFF | Math.round(255 * Math.clamp(alpha, 0f, 1f)) << 24;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static long lifeEnd(Word w) {
        long end = w.born + LIFE_MS;
        if (w.endedAt > 0) end = Math.min(end, w.endedAt + LINE_LINGER_MS);   // the old line dissolves while the next one comes in
        return end;
    }

    void draw(DrawContext c, MinecraftClient client, KrovexTheme theme, LrcLyrics lyrics, long progressMs, MusicPreferences prefs) {
        if (client.player == null) return;
        float camYaw = client.player.getYaw(), camPitch = client.player.getPitch();
        long now = System.currentTimeMillis();
        int index = lyrics.indexAt(progressMs);
        if (lyrics != lastLyrics || progressMs + 1500 < lastProgress) {   // new song or the track jumped back
            words.clear();
            line = Integer.MIN_VALUE;
            lastLyrics = lyrics;
        }
        lastProgress = progressMs;
        if (index != line) {
            for (Word w : words) if (w.endedAt == 0) w.endedAt = now;
            line = index;
            spawned = 0;
        }
        if (index >= 0 && prefs.skyLyricsEnabled()) spawnDue(lyrics, index, progressMs, camYaw, camPitch, now, prefs);
        words.removeIf(w -> now > lifeEnd(w));
        if (words.isEmpty()) return;

        var font = client.textRenderer;
        int screenW = c.getScaledWindowWidth(), screenH = c.getScaledWindowHeight();
        double fov = Math.toRadians(client.options.getFov().getValue());
        double focal = (screenH / 2.0) / Math.tan(fov / 2.0);
        boolean follow = prefs.skyFollowView(), outline = prefs.skyOutline();
        float target = 28f * prefs.skyScale();
        int cut = CUTS[0];
        for (int candidate : CUTS) if (Math.abs(candidate - target) < Math.abs(cut - target)) cut = candidate;
        boolean vanilla = KrovexFontRegistry.isVanilla();
        float extra = vanilla ? target / 9f : target / cut;
        float spacing = prefs.skyLetterSpacing() * (vanilla ? 1f : cut / 28f);   // spacing is in "pixels of a 28px letter", so it scales with the letters
        int accent = theme.accentColor();
        int fillColour = outline ? theme.textColor() | 0xFF000000 : accent | 0xFF000000;
        int ringColour = outline ? accent | 0xFF000000 : 0xFF0A0A12;
        int haloColour = 0xFF05050A;
        float edge = Math.max(.7f, target / 22f) * (outline ? 1.2f : .8f);

        for (Word w : words) {
            long age = now - w.born;
            float out = Math.clamp((lifeEnd(w) - now) / (float) FADE_OUT_MS, 0f, 1f);
            if (out < .02f) continue;
            float yaw = follow ? camYaw + w.relYaw : w.worldYaw;
            float pitch = follow ? camPitch + w.relPitch : w.worldPitch;
            float dy = wrap(yaw - camYaw), dp = pitch - camPitch;
            if (Math.abs(dy) > 78f || Math.abs(dp) > 70f) continue;
            double sx = screenW / 2.0 + focal * Math.tan(Math.toRadians(dy));
            double sy = screenH / 2.0 + focal * Math.tan(Math.toRadians(dp));
            sy = Math.clamp(sy, screenH * .07, screenH * .70);                  // stays clear of the hotbar, keystrokes and CPS
            float pop = age < 260 ? .55f + .45f * (float) (1 - Math.pow(1 - age / 260.0, 3)) : 1f;
            float scale = extra * pop * (float) (1.0 / Math.max(.55, Math.cos(Math.toRadians(dy))));

            int[] cps = w.text.codePoints().toArray();
            int n = cps.length;
            Text[] glyph = new Text[n];
            float[] gx = new float[n];
            float total = 0;
            for (int i = 0; i < n; i++) {
                glyph[i] = KrovexFontRegistry.sized(Text.literal(new String(Character.toChars(cps[i]))), true, cut);
                gx[i] = total;
                total += font.getWidth(glyph[i]) + (i < n - 1 ? spacing : 0f);
            }

            var matrices = c.getMatrices();
            matrices.pushMatrix();
            try {
                matrices.translate((float) sx, (float) sy);
                matrices.rotate((float) Math.toRadians(w.tilt + dy * .18f));
                matrices.scale(scale, scale);
                matrices.translate(-total / 2f, -cut / 2f);
                for (int i = 0; i < n; i++) {
                    // letters come in one after another, so the word is revealed instead of flashing on
                    float in = Math.clamp((age - i * LETTER_STAGGER_MS) / (float) FADE_IN_MS, 0f, 1f);
                    float alpha = in * out;
                    if (alpha < .02f) continue;
                    matrices.pushMatrix();
                    try {
                        matrices.translate(gx[i], (1f - in) * 3f);             // each letter settles up from 3px below
                        // soft drop shadow
                        matrices.pushMatrix();
                        matrices.translate(edge * .9f, edge * 1.4f);
                        c.drawText(font, glyph[i], 0, 0, withAlpha(haloColour, alpha * .5f), false);
                        matrices.popMatrix();
                        if (outline) ring(c, font, glyph[i], edge * 1.9f, withAlpha(haloColour, alpha * .6f));     // dark halo: readable on a bright sky
                        ring(c, font, glyph[i], edge, withAlpha(ringColour, alpha));
                        c.drawText(font, glyph[i], 0, 0, withAlpha(fillColour, alpha), false);
                    } finally {
                        matrices.popMatrix();
                    }
                }
            } finally {
                matrices.popMatrix();
            }
        }
    }

    /** The glyph drawn eight times around its place, radius {@code r}: an outline. */
    private static void ring(DrawContext c, net.minecraft.client.font.TextRenderer font, Text glyph, float r, int argb) {
        var matrices = c.getMatrices();
        for (int k = 0; k < 8; k++) {
            matrices.pushMatrix();
            matrices.translate((float) (COS[k] * r), (float) (SIN[k] * r));
            c.drawText(font, glyph, 0, 0, argb, false);
            matrices.popMatrix();
        }
    }

    /** Picks, from a few random spots, the one farthest from the words already hanging, so lines do not pile up. */
    private void place(Word w, float camYaw, float camPitch, boolean sky, boolean follow) {
        float bestScore = -1, bestYaw = 0, bestPitch = 0;
        for (int attempt = 0; attempt < 6; attempt++) {
            float spreadA = random.nextFloat(), spreadB = random.nextFloat();
            float relYaw, relPitch;
            if (sky) {
                relYaw = (spreadA - .5f) * 112f;
                relPitch = (-12f - spreadB * 40f) - camPitch;       // well above the horizon: in the sky, never at the crosshair
            } else {
                relYaw = (spreadA - .5f) * 66f;
                relPitch = (spreadB - .5f) * 28f - 4f;
            }
            float worldYaw = camYaw + relYaw, worldPitch = camPitch + relPitch;
            float nearest = 999f;
            for (Word o : words) {
                float oy = follow ? camYaw + o.relYaw : o.worldYaw, op = follow ? camPitch + o.relPitch : o.worldPitch;
                float d = (float) Math.hypot(wrap(worldYaw - oy), (worldPitch - op) * 2.2f);   // words are wide: weigh the vertical gap more
                nearest = Math.min(nearest, d);
            }
            if (nearest > bestScore) { bestScore = nearest; bestYaw = relYaw; bestPitch = relPitch; }
        }
        w.relYaw = bestYaw;
        w.relPitch = bestPitch;
        w.worldYaw = camYaw + w.relYaw;
        w.worldPitch = camPitch + w.relPitch;
    }

    private void spawnDue(LrcLyrics lyrics, int index, long progressMs, float camYaw, float camPitch, long now, MusicPreferences prefs) {
        String raw = lyrics.text(index);
        if (raw == null || raw.isBlank()) return;
        String[] parts = raw.trim().split("\\s+");
        long start = lyrics.startMs(index), end = lyrics.endMs(index);
        long span = Math.max(600, Math.min(end - start, 7000));
        int total = 0;
        for (String p : parts) total += p.length() + 1;
        int consumed = 0;
        boolean sky = "SKY".equals(prefs.skyStyle());
        for (int k = 0; k < parts.length; k++) {
            long at = start + Math.round(span * .92 * consumed / (double) total);
            consumed += parts[k].length() + 1;
            if (k < spawned) continue;
            if (progressMs < at) break;
            spawned = k + 1;
            Word w = new Word();
            w.text = parts[k].toUpperCase(java.util.Locale.ROOT);
            w.born = now;
            counter++;
            place(w, camYaw, camPitch, sky, prefs.skyFollowView());
            w.tilt = (random.nextFloat() - .5f) * 16f;
            words.add(w);
            if (words.size() > 16) words.remove(0);
        }
    }
}

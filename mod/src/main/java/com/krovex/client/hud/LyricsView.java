package com.krovex.client.hud;

import com.krovex.client.gui.KrovexFontRegistry;
import com.krovex.client.music.CoverArt;
import com.krovex.client.music.LrcLyrics;
import com.krovex.theme.KrovexTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.awt.Rectangle;

/**
 * The two HUD lyrics looks (both are movable and resizable in the HUD editor):
 *  - Spotlight: big centred lines with a karaoke sweep in the theme colour, with a small now-playing pill above. No song cover.
 *  - Song cover: a glass card with the song's cover, title and artist, the lines, and a progress bar.
 * Create one instance per place it is drawn (each keeps its own line-change animation).
 */
final class LyricsView {
    /** What the lyrics are being drawn for. */
    record Song(String title, String artist, long durationMs) {}

    private static final float SLIDE_MS = 300f;
    private int lastIndex = Integer.MIN_VALUE;
    private long changedAtNanos;

    /** Sample lines for the HUD editor preview. */
    static final LrcLyrics SAMPLE = LrcLyrics.parse("""
            [00:00.00]These are your lyrics
            [00:04.00]Drag this block anywhere
            [00:08.00]Scroll over it to resize
            [00:12.00]And your song plays here
            """);

    /** Returns the eased 0..1 progress of the slide that starts whenever the current line changes. */
    private float slide(int index) {
        long now = System.nanoTime();
        if (index != lastIndex) { lastIndex = index; changedAtNanos = now; }
        float t = Math.min(1f, (now - changedAtNanos) / 1e6f / SLIDE_MS);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static int withAlpha(int argb, float alpha) {
        return argb & 0xFFFFFF | Math.max(6, Math.round(255 * Math.clamp(alpha, 0f, 1f))) << 24;
    }

    private static String shown(String text) { return text == null || text.isBlank() ? "♪" : text; }

    private static Text styled(String text, boolean bold, float px) {
        return KrovexFontRegistry.sized(Text.literal(text), bold, px);
    }

    /** Trims with an ellipsis until the text fits. */
    private static String fit(TextRenderer font, String text, boolean bold, float px, int maxWidth) {
        String out = text;
        while (out.length() > 1 && font.getWidth(styled(out, bold, px)) > maxWidth) out = out.substring(0, out.length() - 1);
        return out.equals(text) ? text : out.stripTrailing() + "…";
    }

    private static float fraction(LrcLyrics lyrics, int index, long progressMs) {
        long start = lyrics.startMs(index), end = lyrics.endMs(index);
        if (end <= start) return 1f;
        // the fill runs through the first ~90% of the line so it is complete just before the next one starts
        return Math.clamp((progressMs - start) / ((end - start) * .9f), 0f, 1f);
    }

    /** One line, centred on cx, with the karaoke fill when {@code fill} is in 0..1 (negative = no fill). */
    private static void drawLine(DrawContext c, TextRenderer font, String text, boolean bold, float px, int cx, int y,
                                 int bright, int dim, float alpha, float fill, boolean shadow) {
        Text whole = styled(text, bold, px);
        int total = font.getWidth(whole), x = cx - total / 2;
        if (fill < 0f) {
            c.drawText(font, whole, x, y, withAlpha(bright, alpha), shadow);
            return;
        }
        int split = Math.round(text.length() * fill);
        String done = text.substring(0, split), rest = text.substring(split);
        Text doneText = styled(done, bold, px);
        if (!done.isEmpty()) c.drawText(font, doneText, x, y, withAlpha(bright, alpha), shadow);
        if (!rest.isEmpty()) c.drawText(font, styled(rest, bold, px), x + font.getWidth(doneText), y, withAlpha(dim, alpha), shadow);
    }

    // ------------------------------------------------------------------ Spotlight (no cover)

    Rectangle drawSpotlight(DrawContext c, MinecraftClient client, HudElementLayout layout, KrovexTheme theme,
                            LrcLyrics lyrics, long progressMs, Song song) {
        final int width = 340, height = song != null && song.title() != null ? 96 : 76;
        final int pillH = song != null && song.title() != null ? 16 : 0;
        var font = client.textRenderer;
        int index = lyrics.indexAt(progressMs);
        float eased = slide(index);
        return InputHud.drawAt(c, layout, width, height, () -> {
            int accent = theme.accentColor();
            if (pillH > 0) {
                String head = "\u266A  " + (song.artist() == null || song.artist().isBlank() ? song.title() : song.title() + "  -  " + song.artist());
                String trimmed = fit(font, head, false, 8, width - 40);
                Text label = styled(trimmed, false, 8);
                int w = font.getWidth(label) + 20, x = (width - w) / 2;
                InputHud.bubble(c, x, 0, w, pillH, 7, accent, .9f, theme, false);
                c.drawText(font, label, x + 10, (pillH - 8) / 2 + 1, withAlpha(theme.textColor(), .95f), false);
            }
            int middle = pillH + (height - pillH) / 2, rowGap = 22;
            for (int i = index - 1; i <= index + 2; i++) {
                if (i < 0 || i >= lyrics.size()) continue;
                float slot = i - index + (1f - eased);
                float alpha = 1f - Math.abs(slot) * .62f;
                if (alpha <= .05f) continue;
                boolean current = Math.abs(slot) < .5f;
                if (!current && lyrics.text(i).isBlank()) continue;
                float px = current ? 20f : 12f;
                int rowY = middle + Math.round(slot * rowGap) - (current ? 10 : 6);
                String line = fit(font, shown(lyrics.text(i)), current, px, width - 16);
                float fill = current && i == index ? fraction(lyrics, i, progressMs) : -1f;
                drawLine(c, font, line, current, px, width / 2, rowY, current ? 0xFFFFFFFF : theme.textColor(),
                        current ? accent : theme.textColor(), current ? alpha : alpha * .6f, fill, true);
            }
        });
    }

    // ------------------------------------------------------------------ Song cover

    Rectangle drawSongCover(DrawContext c, MinecraftClient client, HudElementLayout layout, KrovexTheme theme,
                            LrcLyrics lyrics, long progressMs, Song song, float panelOpacity) {
        final int width = 270, height = 116, rowGap = 15;
        var font = client.textRenderer;
        int index = lyrics.indexAt(progressMs);
        float eased = slide(index);
        return InputHud.drawAt(c, layout, width, height, () -> {
            int accent = theme.accentColor();
            InputHud.bubble(c, 0, 0, width, height, 9, accent, Math.max(.35f, panelOpacity), theme, true);
            // cover
            var cover = CoverArt.current();
            if (cover != null) {
                c.drawTexture(RenderPipelines.GUI_TEXTURED, cover, 10, 10, 0f, 0f, 34, 34, CoverArt.SIZE, CoverArt.SIZE, CoverArt.SIZE, CoverArt.SIZE);
            } else {
                InputHud.hiCard(c, 10, 10, 34, 34, 6, withAlpha(accent, .6f), withAlpha(accent, .55f), withAlpha(accent, .25f));
                c.drawCenteredTextWithShadow(font, styled("\u266A", true, 16), 27, 19, 0xFFFFFFFF);
            }
            InputHud.hiCard(c, 9, 9, 36, 36, 6, withAlpha(accent, .55f), 0, 0);
            // title + artist
            if (song != null && song.title() != null) {
                c.drawText(font, styled(fit(font, song.title(), true, 12, width - 78), true, 12), 54, 12, 0xFFFFFFFF, false);
                String artist = song.artist() == null ? "" : song.artist();
                c.drawText(font, styled(fit(font, artist, false, 9, width - 78), false, 9), 54, 27, withAlpha(theme.textColor(), .7f), false);
            }
            MenuStyleDot.dot(c, width - 12, 14, accent);
            // lines
            int middle = 71;
            for (int i = index - 2; i <= index + 2; i++) {
                if (i < 0 || i >= lyrics.size()) continue;
                float slot = i - index + (1f - eased);
                float alpha = 1f - Math.abs(slot) * .6f;
                if (alpha <= .05f) continue;
                boolean current = Math.abs(slot) < .5f;
                int rowY = middle + Math.round(slot * rowGap) - (current ? 7 : 5);
                if (rowY < 46 || rowY > height - 30) continue;
                float px = current ? 12 : 9;
                String line = fit(font, shown(lyrics.text(i)), current, px, width - 24);
                float fill = current && i == index ? fraction(lyrics, i, progressMs) : -1f;
                drawLine(c, font, line, current, px, width / 2, rowY, current ? 0xFFFFFFFF : theme.textColor(),
                        current ? withAlpha(accent, .95f) : theme.textColor(), current ? alpha : alpha * .75f, fill, current);
            }
            // progress bar, and under it "1:00 - 2:14" (where the song is - how long it is)
            if (song != null && song.durationMs() > 0) {
                int barWidth = width - 24, barY = height - 20;
                InputHud.round(c, 12, barY, barWidth, 3, 1, withAlpha(0xFFFFFFFF, .16f));
                int filled = Math.round(barWidth * Math.clamp(progressMs / (float) song.durationMs(), 0f, 1f));
                if (filled > 1) InputHud.round(c, 12, barY, filled, 3, 1, withAlpha(accent, .95f));
                String clock = com.krovex.client.music.SpotifyTrack.timeLabel(progressMs, song.durationMs());
                Text clockText = styled(clock, false, 9);
                c.drawText(font, clockText, 12 + barWidth - font.getWidth(clockText), height - 14, withAlpha(theme.textColor(), .75f), false);
            }
        });
    }

    /** Tiny helper so the status dot stays a plain round shape. */
    private static final class MenuStyleDot {
        static void dot(DrawContext c, int cx, int cy, int color) {
            InputHud.round(c, cx - 2, cy - 2, 5, 5, 2, withAlpha(color, .95f));
        }
    }
}
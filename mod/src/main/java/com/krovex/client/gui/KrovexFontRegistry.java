package com.krovex.client.gui;

import com.krovex.client.KrovexClientMod;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * The ONE font every Krovex surface uses: the menus and every HUD widget draw their text through
 * here, and the family comes from the active theme, so they can never disagree.
 *
 * Fonts are bundled as static TTFs (see tools/make-fonts.py) with three cuts per family:
 * regular and bold for normal text, and a "small" cut for tiny labels (CPS, keystrokes) that is
 * rasterised natively at that size, so it stays sharp instead of being a big font scaled down.
 */
public final class KrovexFontRegistry {
    private KrovexFontRegistry() {}

    public enum Weight { REGULAR, BOLD, SMALL }

    /** What the font picker offers, in order. "Minecraft" is the vanilla pixel font. */
    public static final List<String> FAMILIES = List.of(
            "Inter", "Inter Display", "Manrope", "Roboto", "Roboto Condensed", "JetBrains Mono",
            "Fredoka", "Baloo", "Nunito", "Quicksand", "Comfortaa", "Poppins", "Rubik", "Orbitron", "Righteous",
            "Bangers", "Pacifico", "Press Start 2P", "Minecraft");

    private static final java.util.concurrent.ConcurrentHashMap<String, StyleSpriteSource.Font> FONTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile String cachedFamily = "Inter";
    private static volatile long cachedAt;

    private static String key(String family) {
        return switch (family == null ? "" : family) {
            case "Inter Display" -> "inter_display";
            case "Manrope" -> "manrope";
            case "Roboto" -> "roboto";
            case "Roboto Condensed" -> "roboto_condensed";
            case "JetBrains Mono" -> "jetbrains_mono";
            case "Fredoka" -> "fredoka";
            case "Baloo" -> "baloo2";
            case "Nunito" -> "nunito";
            case "Quicksand" -> "quicksand";
            case "Comfortaa" -> "comfortaa";
            case "Poppins" -> "poppins";
            case "Rubik" -> "rubik";
            case "Orbitron" -> "orbitron";
            case "Righteous" -> "righteous";
            case "Bangers" -> "bangers";
            case "Pacifico" -> "pacifico";
            case "Press Start 2P" -> "pressstart2p";
            case "Minecraft" -> null;
            default -> "inter";
        };
    }

    /** The family from the active theme, cached briefly (it's read for every piece of text drawn). */
    public static String activeFamily() {
        long now = System.nanoTime();
        if (now - cachedAt > 300_000_000L) {
            try {
                var store = KrovexClientMod.themeStore();
                if (store != null) cachedFamily = store.getActiveTheme().fontFamily();
            } catch (RuntimeException ignored) {
                // keep the last known family
            }
            cachedAt = now;
        }
        return cachedFamily;
    }

    /** Call after the theme's font changes so the new family shows up on the very next frame. */
    public static void invalidate() { cachedAt = 0; }

    /** True when the active font is Minecraft's own pixel font (which has no separate small cut). */
    public static boolean isVanilla() { return key(activeFamily()) == null; }

    /** Native sizes the bundled fonts are cut at (GUI px). Must match tools/make-fonts.py. */
    private static final int[] REGULAR_SIZES = {7, 8, 9, 10, 12, 14, 16};
    private static final int[] BOLD_SIZES = {9, 10, 12, 14, 16, 20, 24, 28, 32};

    private static int nearest(int[] sizes, float px) {
        int best = sizes[0];
        for (int size : sizes) if (Math.abs(size - px) < Math.abs(best - px)) best = size;
        return best;
    }

    /**
     * {@code label} in the closest native cut to {@code px} GUI pixels. Text is never scaled: every size
     * is rasterised at its own size, so glyphs land on whole screen pixels and stay sharp.
     */
    public static Text sized(Text label, String family, boolean bold, float px) {
        String key = key(family);
        if (key == null) return bold ? label.copy().styled(s -> s.withBold(true)) : label;
        // The bundled fonts only hold Latin text + a few symbols. Anything else (Chinese, Japanese, Cyrillic,
        // emoji...) is drawn in Minecraft's own font, which has every character, instead of showing boxes.
        if (!coverable(label.getString())) return bold ? label.copy().styled(s -> s.withBold(true)) : label;
        String name = bold ? key + "_b" + nearest(BOLD_SIZES, px) : key + "_" + nearest(REGULAR_SIZES, px);
        StyleSpriteSource.Font font = FONTS.computeIfAbsent(name, n -> new StyleSpriteSource.Font(Identifier.of("krovex-client", n)));
        return label.copy().styled(style -> style.withFont(font));
    }

    /** True when every character is one the bundled fonts can draw (must match KEEP in tools/make-fonts.py + the symbols font). */
    static boolean coverable(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < 0x250) continue;
            if (ch >= 0x2010 && ch <= 0x203A) continue;
            switch (ch) {
                case 0x20AC, 0x2122, 0x2190, 0x2191, 0x2192, 0x2193, 0x2212, 0x221E,
                     0x266A, 0x2304, 0x2605, 0x2606, 0x2713, 0x22EF, 0x25B1, 0x25A7, 0x25C7, 0x2694, 0x24D8 -> { }
                default -> { return false; }
            }
        }
        return true;
    }

    /** Same, in the active family. */
    public static Text sized(Text label, boolean bold, float px) { return sized(label, activeFamily(), bold, px); }

    public static Text styled(Text label, String family, Weight weight) {
        return switch (weight) {
            case REGULAR -> sized(label, family, false, 9);
            case BOLD -> sized(label, family, true, 9);
            case SMALL -> sized(label, family, false, 7);
        };
    }

    /** Regular text in the active font. The family argument is accepted for old call sites and ignored. */
    public static Text apply(Text label, String ignoredFamily) { return styled(label, activeFamily(), Weight.REGULAR); }

    public static Text regular(Text label) { return styled(label, activeFamily(), Weight.REGULAR); }

    public static Text bold(Text label) { return styled(label, activeFamily(), Weight.BOLD); }

    public static Text small(Text label) { return styled(label, activeFamily(), Weight.SMALL); }
}

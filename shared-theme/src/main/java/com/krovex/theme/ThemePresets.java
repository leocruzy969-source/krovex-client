package com.krovex.theme;

import java.util.List;

/** The four built-in presets every fresh install ships with. */
public final class ThemePresets {

    private ThemePresets() {}

    public static KrovexTheme krovexPurple() {
        return KrovexTheme.builder()
                .id("builtin-krovex-purple")
                .name("Krovex Purple")
                .accentColor(0xFF8A4FFF)
                .backgroundColor(0xFF121014)
                .panelColor(0xFF1C1920)
                .textColor(0xFFF5F3F7)
                .fontFamily(KrovexTheme.DEFAULT_FONT)
                .fontSize(KrovexTheme.DEFAULT_FONT_SIZE)
                .cornerRadius(8)
                .spacing(8)
                .opacity(0.92f)
                .blurEnabled(true)
                .glowEnabled(true)
                .animationsEnabled(true)
                .builtin(true)
                .build();
    }

    public static KrovexTheme midnightBlue() {
        return KrovexTheme.builder()
                .id("builtin-midnight-blue")
                .name("Midnight Blue")
                .accentColor(0xFF3E7BFA)
                .backgroundColor(0xFF0D1220)
                .panelColor(0xFF161D2E)
                .textColor(0xFFEDF1FA)
                .fontFamily(KrovexTheme.DEFAULT_FONT)
                .fontSize(KrovexTheme.DEFAULT_FONT_SIZE)
                .cornerRadius(8)
                .spacing(8)
                .opacity(0.92f)
                .blurEnabled(true)
                .glowEnabled(true)
                .animationsEnabled(true)
                .builtin(true)
                .build();
    }

    public static KrovexTheme crimson() {
        return KrovexTheme.builder()
                .id("builtin-crimson")
                .name("Crimson")
                .accentColor(0xFFE23D52)
                .backgroundColor(0xFF160D0F)
                .panelColor(0xFF221317)
                .textColor(0xFFFBEEEF)
                .fontFamily(KrovexTheme.DEFAULT_FONT)
                .fontSize(KrovexTheme.DEFAULT_FONT_SIZE)
                .cornerRadius(8)
                .spacing(8)
                .opacity(0.92f)
                .blurEnabled(true)
                .glowEnabled(true)
                .animationsEnabled(true)
                .builtin(true)
                .build();
    }

    public static KrovexTheme monochrome() {
        return KrovexTheme.builder()
                .id("builtin-monochrome")
                .name("Monochrome")
                .accentColor(0xFFBFBFBF)
                .backgroundColor(0xFF121212)
                .panelColor(0xFF1E1E1E)
                .textColor(0xFFF2F2F2)
                .fontFamily(KrovexTheme.DEFAULT_FONT)
                .fontSize(KrovexTheme.DEFAULT_FONT_SIZE)
                .cornerRadius(6)
                .spacing(8)
                .opacity(0.92f)
                .blurEnabled(false)
                .glowEnabled(false)
                .animationsEnabled(true)
                .builtin(true)
                .build();
    }

    private static KrovexTheme make(String id, String name, int accent, int background, int panel, int text) {
        return KrovexTheme.builder().id(id).name(name).accentColor(accent).backgroundColor(background).panelColor(panel)
                .textColor(text).fontFamily(KrovexTheme.DEFAULT_FONT).fontSize(KrovexTheme.DEFAULT_FONT_SIZE)
                .cornerRadius(8).spacing(8).opacity(0.92f).blurEnabled(true).glowEnabled(true).animationsEnabled(true)
                .builtin(true).build();
    }

    public static List<KrovexTheme> all() {
        return List.of(krovexPurple(), midnightBlue(), crimson(), monochrome(),
                make("builtin-emerald", "Emerald", 0xFF2EDC8A, 0xFF0B1512, 0xFF12201B, 0xFFEAFBF3),
                make("builtin-sunset", "Sunset", 0xFFFF7A3D, 0xFF17100C, 0xFF241812, 0xFFFFF1E8),
                make("builtin-rose", "Rose", 0xFFFF5FA2, 0xFF170D13, 0xFF24141D, 0xFFFFEEF5),
                make("builtin-ocean", "Ocean", 0xFF22C7D6, 0xFF091417, 0xFF0F1F24, 0xFFE8FAFC),
                make("builtin-gold", "Gold", 0xFFFFC53D, 0xFF15120A, 0xFF211C10, 0xFFFFF8E6),
                make("builtin-lime", "Lime", 0xFFB5F53E, 0xFF10150A, 0xFF19210F, 0xFFF6FFE6),
                make("builtin-ice", "Ice", 0xFF74C6FF, 0xFF0B1118, 0xFF121B26, 0xFFEAF5FF),
                make("builtin-lavender", "Lavender", 0xFFB69CFF, 0xFF110F1A, 0xFF1B1829, 0xFFF3EFFF),
                make("builtin-blood-moon", "Blood Moon", 0xFFFF3B30, 0xFF120808, 0xFF1E0E0E, 0xFFFFECEA),
                make("builtin-cotton-candy", "Cotton Candy", 0xFFFF8AD8, 0xFF0F1220, 0xFF19203A, 0xFFF2F6FF),
                make("builtin-mint", "Mint", 0xFF59E3C0, 0xFF0A1413, 0xFF11201E, 0xFFE9FBF7),
                make("builtin-amber-night", "Amber Night", 0xFFFFA630, 0xFF0C0F1C, 0xFF151A2E, 0xFFFFF4E0));
    }

    /** The look Krovex starts with and "Reset to default" goes back to: white / grey Monochrome (no accent colour on the HUD). */
    public static KrovexTheme defaultTheme() {
        return monochrome();
    }
}

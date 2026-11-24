package com.krovex.theme;

import com.krovex.theme.json.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts {@link KrovexTheme} to/from the `.krovextheme` JSON shape used by both apps. */
public final class ThemeJsonCodec {

    /** Bump if a future field addition would break older files; readers must stay backward compatible. */
    public static final int FORMAT_VERSION = 1;

    private ThemeJsonCodec() {}

    public static Map<String, Object> toMap(KrovexTheme theme) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("formatVersion", (long) FORMAT_VERSION);
        m.put("id", theme.id());
        m.put("name", theme.name());
        m.put("accentColor", KrovexTheme.toHexColor(theme.accentColor(), false));
        m.put("backgroundColor", KrovexTheme.toHexColor(theme.backgroundColor(), false));
        m.put("panelColor", KrovexTheme.toHexColor(theme.panelColor(), false));
        m.put("textColor", KrovexTheme.toHexColor(theme.textColor(), false));
        m.put("fontFamily", theme.fontFamily());
        m.put("fontSize", (long) theme.fontSize());
        m.put("cornerRadius", (long) theme.cornerRadius());
        m.put("spacing", (long) theme.spacing());
        m.put("opacity", (double) theme.opacity());
        m.put("blurEnabled", theme.blurEnabled());
        m.put("glowEnabled", theme.glowEnabled());
        m.put("animationsEnabled", theme.animationsEnabled());
        m.put("builtin", theme.builtin());
        return m;
    }

    public static String toJson(KrovexTheme theme, boolean pretty) {
        return Json.write(toMap(theme), pretty);
    }

    @SuppressWarnings("unchecked")
    public static KrovexTheme fromMap(Map<String, Object> m) {
        KrovexTheme.Builder b = KrovexTheme.builder();
        if (m.containsKey("id")) b.id((String) m.get("id"));
        b.name(stringOr(m, "name", "Custom Theme"));
        b.accentColor(colorOr(m, "accentColor", KrovexTheme.DEFAULT_ACCENT));
        b.backgroundColor(colorOr(m, "backgroundColor", KrovexTheme.DEFAULT_BACKGROUND));
        b.panelColor(colorOr(m, "panelColor", KrovexTheme.DEFAULT_PANEL));
        b.textColor(colorOr(m, "textColor", KrovexTheme.DEFAULT_TEXT));
        b.fontFamily(stringOr(m, "fontFamily", KrovexTheme.DEFAULT_FONT));
        b.fontSize(intOr(m, "fontSize", KrovexTheme.DEFAULT_FONT_SIZE));
        b.cornerRadius(intOr(m, "cornerRadius", KrovexTheme.DEFAULT_CORNER_RADIUS));
        b.spacing(intOr(m, "spacing", KrovexTheme.DEFAULT_SPACING));
        b.opacity((float) doubleOr(m, "opacity", KrovexTheme.DEFAULT_OPACITY));
        b.blurEnabled(boolOr(m, "blurEnabled", true));
        b.glowEnabled(boolOr(m, "glowEnabled", true));
        b.animationsEnabled(boolOr(m, "animationsEnabled", true));
        b.builtin(boolOr(m, "builtin", false));
        return b.build();
    }

    public static KrovexTheme fromJson(String json) {
        return fromMap(Json.parseObject(json));
    }

    private static String stringOr(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s ? s : def;
    }

    private static int intOr(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOr(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    private static boolean boolOr(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : def;
    }

    private static int colorOr(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof String s) {
            try {
                return KrovexTheme.parseHexColor(s);
            } catch (IllegalArgumentException ignored) {
                return def;
            }
        }
        return def;
    }
}

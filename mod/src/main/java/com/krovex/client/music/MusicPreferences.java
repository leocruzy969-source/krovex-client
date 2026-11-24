package com.krovex.client.music;

import com.krovex.theme.json.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Section 3's Music settings. Defaults to OFF, and stays off unless explicitly turned
 * on in-game or via an imported Krovex Settings bundle that enables it (section 3:
 * "Music defaults to OFF unless I explicitly import Krovex settings that enable it").
 * Turning it off must not pause whatever is actually playing in Spotify -- this class
 * only ever gates Krovex's own widget/lyrics/shortcuts, never sends a pause command.
 */
public final class MusicPreferences {

    public enum WidgetCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private boolean enabled = true;  // Spotify is picked up automatically, so no setup is needed
    private WidgetCorner widgetCorner = WidgetCorner.TOP_LEFT;
    private float widgetScale = 1.0f;        // "widget size"
    private float backgroundOpacity = 0.6f;
    private boolean showProgressBar = true;
    private boolean hideWhenStopped = true;
    private boolean hudLyricsEnabled = false;  // independent toggle, see section 3
    private boolean skyLyricsEnabled = false;  // independent toggle, see section 3
    private String playPauseKeybind = null;    // null = unassigned, per spec default
    private String nextTrackKeybind = null;    // null = unassigned, per spec default
    private String hudLyricsStyle = "SPOTLIGHT";   // SPOTLIGHT (no cover) or SONG_COVER
    private String skyStyle = "SKY";                // SKY or FLOATING
    private boolean skyFollowView = false;          // false: a word stays where it appeared in the world
    private boolean skyOutline = true;              // false: text filled with the theme colour, true: outlined
    private float skyScale = 1.0f;
    private float skyLetterSpacing = 2.5f;          // extra pixels between letters of a sky word (in a 28px letter), 0 .. 8
    private boolean autoLyrics = true;        // look lyrics up automatically for the playing song
    private boolean mediaKeysInitialised = false; // one-time: give the music keys their arrow-key defaults
    private String lyricsFilePath = null;      // imported .lrc file for the current track, null = none loaded

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public WidgetCorner widgetCorner() { return widgetCorner; }
    public void setWidgetCorner(WidgetCorner v) { this.widgetCorner = v; }
    public float widgetScale() { return widgetScale; }
    public void setWidgetScale(float v) { this.widgetScale = Math.max(0.5f, Math.min(2.0f, v)); }
    public float backgroundOpacity() { return backgroundOpacity; }
    public void setBackgroundOpacity(float v) { this.backgroundOpacity = Math.max(0f, Math.min(1f, v)); }
    public boolean showProgressBar() { return showProgressBar; }
    public void setShowProgressBar(boolean v) { this.showProgressBar = v; }
    public boolean hideWhenStopped() { return hideWhenStopped; }
    public void setHideWhenStopped(boolean v) { this.hideWhenStopped = v; }
    public boolean hudLyricsEnabled() { return hudLyricsEnabled; }
    public void setHudLyricsEnabled(boolean v) { this.hudLyricsEnabled = v; }
    public boolean skyLyricsEnabled() { return skyLyricsEnabled; }
    public void setSkyLyricsEnabled(boolean v) { this.skyLyricsEnabled = v; }
    public String playPauseKeybind() { return playPauseKeybind; }
    public void setPlayPauseKeybind(String v) { this.playPauseKeybind = v; }
    public String nextTrackKeybind() { return nextTrackKeybind; }
    public void setNextTrackKeybind(String v) { this.nextTrackKeybind = v; }
    public String hudLyricsStyle() { return hudLyricsStyle; }
    public void setHudLyricsStyle(String v) { this.hudLyricsStyle = "SONG_COVER".equals(v) ? "SONG_COVER" : "SPOTLIGHT"; }
    public String skyStyle() { return skyStyle; }
    public void setSkyStyle(String v) { this.skyStyle = "FLOATING".equals(v) ? "FLOATING" : "SKY"; }
    public boolean skyFollowView() { return skyFollowView; }
    public void setSkyFollowView(boolean v) { this.skyFollowView = v; }
    public boolean skyOutline() { return skyOutline; }
    public void setSkyOutline(boolean v) { this.skyOutline = v; }
    public float skyScale() { return skyScale; }
    public void setSkyScale(float v) { this.skyScale = Math.max(0.4f, Math.min(2.0f, v)); }
    public float skyLetterSpacing() { return skyLetterSpacing; }
    public void setSkyLetterSpacing(float v) { this.skyLetterSpacing = Math.max(0f, Math.min(8f, v)); }
    public boolean autoLyrics() { return autoLyrics; }
    public void setAutoLyrics(boolean v) { this.autoLyrics = v; }
    public boolean mediaKeysInitialised() { return mediaKeysInitialised; }
    public void setMediaKeysInitialised(boolean v) { this.mediaKeysInitialised = v; }
    public String lyricsFilePath() { return lyricsFilePath; }
    public void setLyricsFilePath(String v) { this.lyricsFilePath = v; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("autoSpotify", true);
        m.put("widgetCorner", widgetCorner.name());
        m.put("widgetScale", (double) widgetScale);
        m.put("backgroundOpacity", (double) backgroundOpacity);
        m.put("showProgressBar", showProgressBar);
        m.put("hideWhenStopped", hideWhenStopped);
        m.put("hudLyricsEnabled", hudLyricsEnabled);
        m.put("skyLyricsEnabled", skyLyricsEnabled);
        m.put("playPauseKeybind", playPauseKeybind);
        m.put("nextTrackKeybind", nextTrackKeybind);
        m.put("autoLyrics", autoLyrics);
        m.put("hudLyricsStyle", hudLyricsStyle);
        m.put("skyStyle", skyStyle);
        m.put("skyFollowView", skyFollowView);
        m.put("skyOutline", skyOutline);
        m.put("skyScale", (double) skyScale);
        m.put("skyLetterSpacing", (double) skyLetterSpacing);
        m.put("skyDefaultsV2", true);
        m.put("mediaKeysInitialised", mediaKeysInitialised);
        m.put("lyricsFilePath", lyricsFilePath);
        return m;
    }

    public static MusicPreferences fromMap(Map<String, Object> m) {
        MusicPreferences prefs = new MusicPreferences();
        if (m == null) return prefs; // absent entirely -> stays at OFF defaults
        // configs written before auto-detect existed have no "autoSpotify" key and say "off" only because that
        // was the old default: switch them on once
        prefs.enabled = m.containsKey("autoSpotify") ? boolOr(m, "enabled", true) : true;
        try {
            prefs.widgetCorner = WidgetCorner.valueOf((String) m.getOrDefault("widgetCorner", "TOP_LEFT"));
        } catch (IllegalArgumentException ignored) { /* keep default */ }
        prefs.widgetScale = (float) doubleOr(m, "widgetScale", 1.0);
        prefs.backgroundOpacity = (float) doubleOr(m, "backgroundOpacity", 0.6);
        prefs.showProgressBar = boolOr(m, "showProgressBar", true);
        prefs.hideWhenStopped = boolOr(m, "hideWhenStopped", true);
        prefs.hudLyricsEnabled = boolOr(m, "hudLyricsEnabled", false);
        prefs.skyLyricsEnabled = boolOr(m, "skyLyricsEnabled", false);
        prefs.playPauseKeybind = (String) m.get("playPauseKeybind");
        prefs.nextTrackKeybind = (String) m.get("nextTrackKeybind");
        prefs.autoLyrics = boolOr(m, "autoLyrics", true);
        prefs.setHudLyricsStyle((String) m.get("hudLyricsStyle"));
        prefs.setSkyStyle((String) m.get("skyStyle"));
        prefs.skyFollowView = boolOr(m, "skyFollowView", true);
        prefs.skyOutline = boolOr(m, "skyOutline", false);
        prefs.setSkyScale((float) doubleOr(m, "skyScale", 0.8));
        prefs.setSkyLetterSpacing((float) doubleOr(m, "skyLetterSpacing", 2.5));
        if (!boolOr(m, "skyDefaultsV2", false)) {   // first load after the sky words redesign: words hang in the world, readable outline
            prefs.skyFollowView = false;
            prefs.skyOutline = true;
            prefs.skyScale = 1.0f;
        }
        prefs.mediaKeysInitialised = boolOr(m, "mediaKeysInitialised", false);
        prefs.lyricsFilePath = (String) m.get("lyricsFilePath");
        return prefs;
    }

    private static boolean boolOr(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : def;
    }

    private static double doubleOr(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(toMap(), true), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static MusicPreferences load(Path file) {
        if (!Files.exists(file)) return new MusicPreferences(); // OFF by default, per spec
        try {
            Map<String, Object> m = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            return fromMap(m);
        } catch (IOException e) {
            return new MusicPreferences();
        }
    }
}

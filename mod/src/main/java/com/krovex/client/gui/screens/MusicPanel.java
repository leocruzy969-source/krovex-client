package com.krovex.client.gui.screens;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.KrovexConfigPaths;
import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.MenuStyle;
import com.krovex.client.music.MusicPreferences;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * NOT compiled here (see QuickPopupScreen's header note). Section 3's Music tab:
 * connect/disconnect Spotify, the enabled switch, widget size/opacity/progress-bar/
 * hide-when-stopped options, and the independent HUD Lyrics / Sky Lyrics toggles.
 * All actual state lives in {@link MusicPreferences} and {@link SpotifyAuthFlow},
 * both plain-Java and already unit-tested outside Minecraft -- this panel is a thin
 * view over them.
 */
public final class MusicPanel implements KrovexSubPanel {

    private final MusicPreferences prefs;
    private final DropList hudLookList = new DropList(), skyStyleList = new DropList(), skyTextList = new DropList();

    @Override public boolean mouseClicked(double mx, double my, int button) {
        return hudLookList.mouseClicked(mx, my, button) || skyStyleList.mouseClicked(mx, my, button) || skyTextList.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        return hudLookList.mouseScrolled(mx, my, vertical) || skyStyleList.mouseScrolled(mx, my, vertical) || skyTextList.mouseScrolled(mx, my, vertical);
    }

    @Override public void renderOverlay(DrawContext c, int mx, int my) {
        hudLookList.renderOverlay(c, mx, my);
        skyStyleList.renderOverlay(c, mx, my);
        skyTextList.renderOverlay(c, mx, my);
    }
    private ButtonWidget lyricsButton;
    private ButtonWidget clearLyricsButton;
    private String statusMessage = "";

    public MusicPanel(Screen parentScreen) {
        this.prefs = KrovexClientMod.musicPreferences();
    }

    /** False: the main page. True: the lyrics page (styles, sky options). Survives panel refreshes. */
    private static boolean lyricsPage;

    @Override
    public void createWidgets(Screen hostScreen, int x, int y, int width, int height) {
        KrovexMenuScreen menu = (KrovexMenuScreen) hostScreen;

        int w = Math.min(310, width - 22), left = x + 10;
        if (lyricsPage) {
            lyricsWidgets(menu, left, y, w);
            return;
        }
        toggle(menu, "Enable Music", left, y + 5, w, prefs.enabled(), prefs::setEnabled);
        toggle(menu, "Show progress bar", left, y + 27, w, prefs.showProgressBar(), prefs::setShowProgressBar);
        toggle(menu, "Hide paused widget", left, y + 47, w, prefs.hideWhenStopped(), prefs::setHideWhenStopped);
        menu.addPanelWidget(MenuStyle.button("Lyrics settings  \u203A", left, y + 67, w, 18, MenuStyle.CARD, () -> {
            lyricsPage = true;
            menu.refreshPanel();
        }));
        menu.addPanelWidget(new MenuStyle.Slider(left, y + 88, w, 18,
                Text.literal("Widget size"), (prefs.widgetScale() - 0.5) / 1.5) {
            @Override protected void updateMessage() {
                setMessage(Text.literal(String.format(java.util.Locale.ROOT, "Widget size %.1fx", 0.5 + value * 1.5)));
            }
            @Override protected void applyValue() {
                prefs.setWidgetScale((float) (0.5 + value * 1.5));
                prefs.save(KrovexConfigPaths.musicPreferencesFile());
            }
        });
        int rw0 = width - (w + 30), rx0 = x + w + 30;
        if (rw0 >= 150) {
            keyButton(menu, "Play / pause", KrovexClientMod.MUSIC_PLAY_PAUSE_KEY, rx0, y + 124, rw0);
            keyButton(menu, "Next song", KrovexClientMod.MUSIC_NEXT_TRACK_KEY, rx0, y + 144, rw0);
            keyButton(menu, "Previous song", KrovexClientMod.MUSIC_PREVIOUS_TRACK_KEY, rx0, y + 164, rw0);
        }
    }

    private void lyricsWidgets(KrovexMenuScreen menu, int left, int y, int w) {
        menu.addPanelWidget(MenuStyle.button("\u2039  Back to music", left, y + 5, w, 18, MenuStyle.CARD, () -> {
            lyricsPage = false;
            menu.refreshPanel();
        }));
        toggle(menu, "HUD lyrics", left, y + 27, w, prefs.hudLyricsEnabled(), prefs::setHudLyricsEnabled);
        boolean cover = "SONG_COVER".equals(prefs.hudLyricsStyle());
        hudLookList.button(menu, "HUD look:  ", left, y + 47, w, 18,
                java.util.List.of(new DropList.Item("Spotlight (no cover)"), new DropList.Item("Song cover")), cover ? 1 : 0, index -> {
                    prefs.setHudLyricsStyle(index == 1 ? "SONG_COVER" : "SPOTLIGHT");
                    save(menu);
                });
        toggle(menu, "Sky lyrics", left, y + 71, w, prefs.skyLyricsEnabled(), prefs::setSkyLyricsEnabled);
        boolean floating = "FLOATING".equals(prefs.skyStyle());
        skyStyleList.button(menu, "Lyrics style:  ", left, y + 91, w, 18,
                java.util.List.of(new DropList.Item("Sky"), new DropList.Item("Floating")), floating ? 1 : 0, index -> {
                    prefs.setSkyStyle(index == 1 ? "FLOATING" : "SKY");
                    save(menu);
                });
        toggle(menu, "Sky lyrics follow your view", left, y + 111, w, prefs.skyFollowView(), prefs::setSkyFollowView);
        skyTextList.button(menu, "Sky text:  ", left, y + 131, w, 18,
                java.util.List.of(new DropList.Item("Outlined"), new DropList.Item("Filled with theme colour")), prefs.skyOutline() ? 0 : 1, index -> {
                    prefs.setSkyOutline(index == 0);
                    save(menu);
                });
        menu.addPanelWidget(new MenuStyle.Slider(left, y + 151, w, 18,
                Text.literal("Size"), (prefs.skyScale() - 0.4) / 1.6) {
            @Override protected void updateMessage() {
                setMessage(Text.literal(String.format(java.util.Locale.ROOT, "Size %.2fx", 0.4 + value * 1.6)));
            }
            @Override protected void applyValue() {
                prefs.setSkyScale((float) (0.4 + value * 1.6));
                prefs.save(KrovexConfigPaths.musicPreferencesFile());
            }
        });
        menu.addPanelWidget(new MenuStyle.Slider(left, y + 173, w, 18,
                Text.literal("Letter spacing"), prefs.skyLetterSpacing() / 8.0) {
            @Override protected void updateMessage() {
                setMessage(Text.literal(String.format(java.util.Locale.ROOT, "Letter spacing %.1f", value * 8.0)));
            }
            @Override protected void applyValue() {
                prefs.setSkyLetterSpacing((float) (value * 8.0));
                prefs.save(KrovexConfigPaths.musicPreferencesFile());
            }
        });
        toggle(menu, "Find lyrics automatically", left, y + 195, w, prefs.autoLyrics(), prefs::setAutoLyrics);
        boolean hasLyrics = prefs.lyricsFilePath() != null && !prefs.lyricsFilePath().isBlank();
        lyricsButton = menu.addPanelWidget(MenuStyle.button(
                hasLyrics ? "Change lyrics file (.lrc)" : "Import lyrics (.lrc)",
                left, y + 215, hasLyrics ? w - 78 : w, 18, MenuStyle.CARD, this::handleImportLyricsClicked));
        if (hasLyrics) {
            clearLyricsButton = menu.addPanelWidget(MenuStyle.button("Clear",
                    left + w - 74, y + 215, 74, 18, MenuStyle.CARD, this::handleClearLyricsClicked));
        }
    }

    private void save(KrovexMenuScreen menu) {
        prefs.save(KrovexConfigPaths.musicPreferencesFile());
        menu.refreshPanel();
    }
    /** A button showing the key bound to a music action; click it, then press the key you want. */
    private void keyButton(KrovexMenuScreen menu, String label, net.minecraft.client.option.KeyBinding binding,
                           int x, int y, int w) {
        ButtonWidget[] self = new ButtonWidget[1];
        self[0] = menu.addPanelWidget(MenuStyle.button(label + ": " + binding.getBoundKeyLocalizedText().getString(),
                x, y, w, 18, MenuStyle.CARD, () -> menu.beginKeyCapture(binding, self[0], label)));
    }

    private void toggle(KrovexMenuScreen menu, String label, int x, int y, int w, boolean enabled,
                        java.util.function.Consumer<Boolean> change) {
        menu.addPanelWidget(MenuStyle.toggle(label, x, y, w, 18, enabled, () -> {
            change.accept(!enabled);
            prefs.save(KrovexConfigPaths.musicPreferencesFile());
            menu.refreshPanel();
        }));
    }

    /**
     * Opens a native file picker for a .lrc file -- AWT's {@link javax.swing.JFileChooser}
     * works fine from a desktop JVM even though the window it's running is LWJGL/GLFW,
     * same reasoning as using {@link java.awt.Desktop} for the Spotify browser handoff
     * above. Run off the render thread so the picker dialog never freezes the game.
     */
    private void handleImportLyricsClicked() {
        Thread.ofVirtual().start(() -> {
            var chooser = new javax.swing.JFileChooser();
            chooser.setDialogTitle("Import synced lyrics (.lrc)");
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("LRC lyrics (*.lrc)", "lrc"));
            int result = chooser.showOpenDialog(null);
            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                prefs.setLyricsFilePath(path);
                prefs.save(KrovexConfigPaths.musicPreferencesFile());
                statusMessage = "Loaded lyrics from " + chooser.getSelectedFile().getName();
            }
        });
    }

    private void handleClearLyricsClicked() {
        prefs.setLyricsFilePath(null);
        prefs.save(KrovexConfigPaths.musicPreferencesFile());
        statusMessage = "Lyrics cleared.";
    }

    @Override
    public void render(DrawContext c, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
        int w = Math.min(310, width - 22);
        int cardHeight = lyricsPage ? 240 : 112;
        MenuStyle.card(c, x, y - 2, w + 20, cardHeight, 9, MenuStyle.EDGE, MenuStyle.PANEL);
        int rx = x + w + 30, rw = width - (w + 30);
        if (rw >= 150) {
            var track = KrovexClientMod.spotifyNowPlayingClient().currentTrack().filter(com.krovex.client.music.SpotifyTrack::hasTrack);
            MenuStyle.card(c, rx, y - 2, rw, 118, 9, MenuStyle.EDGE, MenuStyle.CARD);
            MenuStyle.text(c, "NOW PLAYING", rx + 14, y + 8, MenuStyle.MUTED, .85f);
            MenuStyle.gradientCard(c, rx + 14, y + 24, 54, 54, 8, 0xFFC17AFE, 0xFF5A2FA8, 0xFF24103F);
            MenuStyle.navIcon(c, "music", rx + 34, y + 45, MenuStyle.LIME);
            var font = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
            boolean detected = com.krovex.client.music.LocalMediaSession.get().available();
            String title = track.map(com.krovex.client.music.SpotifyTrack::title).orElse("Nothing playing");
            String artist = track.map(com.krovex.client.music.SpotifyTrack::artist).orElse("Open Spotify and press play");
            MenuStyle.text(c, font.trimToWidth(title, rw - 92), rx + 78, y + 32, MenuStyle.WHITE, 1.16f);
            MenuStyle.text(c, font.trimToWidth(artist == null ? "" : artist, rw - 92), rx + 78, y + 49, MenuStyle.MUTED, .92f);
            float progress = track.map(com.krovex.client.music.SpotifyTrack::progressFraction).orElse(0f);
            MenuStyle.box(c, rx + 14, y + 92, rw - 28, 5, 2, 0xCC241B3A);
            if (progress > 0) MenuStyle.box(c, rx + 14, y + 92, Math.max(4, Math.round((rw - 28) * progress)), 5, 2, MenuStyle.LIME);
            String clock = track.map(com.krovex.client.music.SpotifyTrack::timeLabel).orElse("");
            if (!clock.isEmpty()) MenuStyle.text(c, clock, rx + rw - 14 - font.getWidth(clock), y + 79, MenuStyle.MUTED, .88f);
            // connection status: the Spotify app is picked up automatically, no sign-in needed
            MenuStyle.disc(c, rx + 18, y + 108, 2, detected ? MenuStyle.LIME : 0xFF7A6D92);
            MenuStyle.text(c, detected ? "Connected automatically to the Spotify app" : "Waiting for the Spotify app", rx + 26, y + 104, detected ? MenuStyle.LIME : MenuStyle.MUTED, .88f);
        }
        String lookup = prefs.autoLyrics() ? com.krovex.client.music.LyricsFetcher.status() : "";
        if (statusMessage.isEmpty() && !lookup.isEmpty()) {
            MenuStyle.text(c, lookup, x + 10, y + cardHeight + 4, MenuStyle.MUTED, .92f);
        }
        if (!statusMessage.isEmpty()) {
            MenuStyle.text(c, statusMessage, x + 10, y + cardHeight + 4, MenuStyle.MUTED, .92f);
        }
    }
}

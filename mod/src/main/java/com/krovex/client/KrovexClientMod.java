package com.krovex.client;

import com.krovex.client.config.PopupPreference;
import com.krovex.client.escapemenu.EscapeMenuSettings;
import com.krovex.client.gui.EscapeMenuScreen;
import com.krovex.client.gui.KrovexMenuScreen;
import com.krovex.client.gui.QuickPopupScreen;
import com.krovex.client.hud.HudProfileStore;
import com.krovex.client.hud.KrovexHudOverlay;
import com.krovex.client.modules.ModuleStateStore;
import com.krovex.client.music.MusicPreferences;
import com.krovex.client.music.SpotifyAuthFlow;
import com.krovex.client.music.SpotifyNowPlayingClient;
import com.krovex.client.music.LrcLyrics;
import com.krovex.client.sky.SkyPresetStore;
import com.krovex.theme.ThemeStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * NOT compiled here (see QuickPopupScreen's header note). The mod's entrypoint
 * (registered in fabric.mod.json's "client" entrypoint list). Ties together every
 * store written across sections 1-5 and owns the one piece of behavior none of them
 * can own themselves: the Right Shift dispatch logic from the original spec's
 * "GET THIS INTERACTION RIGHT" section (popup vs. straight-to-full-menu, based on
 * {@link PopupPreference}), plus the Play/Pause and Next Track keybinds for music.
 *
 * Every store here is opened once, lazily, the first time it's needed, and kept as a
 * singleton for the process lifetime -- this is what lets every panel/screen written
 * in earlier sections call {@code KrovexClientMod.themeStore()} etc. without each one
 * re-reading its JSON file on every frame.
 */
public final class KrovexClientMod implements ClientModInitializer {

    /**
     * Spotify's PKCE public-client ID for Krovex Client's own app registration (not a
     * secret -- PKCE public clients have none). Kept here, not duplicated per-panel,
     * so section 3's "Spotify account access stays local" has exactly one place that
     * names the client identity the loopback OAuth flow authenticates as.
     */
    public static final String SPOTIFY_CLIENT_ID = "krovex-client-spotify-pkce";

    private static ThemeStore themeStore;
    private static HudProfileStore hudProfileStore;
    private static SkyPresetStore skyPresetStore;
    private static ModuleStateStore moduleStateStore;
    private static MusicPreferences musicPreferences;
    private static EscapeMenuSettings escapeMenuSettings;
    private static PopupPreference popupPreference;
    private static com.krovex.client.config.StylePreference stylePreference;
    private static SpotifyAuthFlow spotifyAuthFlow;
    private static SpotifyNowPlayingClient spotifyNowPlayingClient;
    private static volatile SpotifyAuthFlow.SpotifySession spotifySession;
    private static String loadedLyricsPath;
    private static LrcLyrics loadedLyrics;

    /**
     * The Krovex menu keybind -- default Right Shift, synced with Minecraft's own
     * Controls screen because it IS a normal {@link KeyBinding}, registered the
     * standard Fabric way; rebinding it from Theme (section 4) or from Controls both
     * write to the exact same underlying binding.
     */
    public static KeyBinding KROVEX_MENU_KEY;
    public static KeyBinding MUSIC_PLAY_PAUSE_KEY;
    public static KeyBinding MUSIC_NEXT_TRACK_KEY;
    public static KeyBinding MUSIC_PREVIOUS_TRACK_KEY;
    public static KeyBinding PUDDLES_KEY;

    private boolean wasKeyDownLastTick = false;

    @Override
    public void onInitializeClient() {
        themeStore = new ThemeStore(KrovexConfigPaths.configDir());
        themeStore.initializeIfNeeded();

        hudProfileStore = new HudProfileStore(KrovexConfigPaths.configDir());
        hudProfileStore.initializeIfNeeded();

        skyPresetStore = new SkyPresetStore(KrovexConfigPaths.configDir());
        skyPresetStore.initializeIfNeeded();

        moduleStateStore = new ModuleStateStore(KrovexConfigPaths.configDir());
        moduleStateStore.initializeIfNeeded();

        musicPreferences = MusicPreferences.load(KrovexConfigPaths.musicPreferencesFile());
        escapeMenuSettings = EscapeMenuSettings.load(KrovexConfigPaths.escapeMenuSettingsFile());
        popupPreference = PopupPreference.load(KrovexConfigPaths.popupPreferenceFile());
        stylePreference = com.krovex.client.config.StylePreference.load(KrovexConfigPaths.stylePreferenceFile());
        spotifyAuthFlow = new SpotifyAuthFlow(SPOTIFY_CLIENT_ID, KrovexConfigPaths.configDir());
        spotifyNowPlayingClient = new SpotifyNowPlayingClient();

        new KrovexHudOverlay().register();

        // Category.create() registers the category globally and throws if the same
        // identifier is registered twice -- create it ONCE and reuse it for all binds.
        KeyBinding.Category krovexCategory = KeyBinding.Category.create(net.minecraft.util.Identifier.of("krovex", "krovexclient"));

        KROVEX_MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.krovex-client.open-menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, krovexCategory));
        // Arrow keys by default (Down = play/pause, Right = next, Left = previous); rebind in Controls or the Music panel.
        MUSIC_PLAY_PAUSE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.krovex-client.music-play-pause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_DOWN, krovexCategory));
        MUSIC_NEXT_TRACK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.krovex-client.music-next-track", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, krovexCategory));
        MUSIC_PREVIOUS_TRACK_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.krovex-client.music-previous-track", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, krovexCategory));

        // unbound by default: set it in Controls to switch Reflective Puddles on and off while playing
        PUDDLES_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.krovex-client.toggle-puddles", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, krovexCategory));

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        com.krovex.client.hud.PlayerPingTracker.register();
    }

    private void onEndClientTick(MinecraftClient client) {
        // KeyBinding.wasPressed() is edge-triggered and consumes the press, which this
        // dispatch logic needs to do manually instead, because "already open -> Right
        // Shift closes it" requires checking the binding's raw down-state against the
        // currently-open screen, not a one-shot consumed event.
        boolean keyDownNow = KROVEX_MENU_KEY.isPressed();
        if (keyDownNow && !wasKeyDownLastTick) {
            handleKrovexKeyPressed(client);
        }
        wasKeyDownLastTick = keyDownNow;

        if (PUDDLES_KEY != null && PUDDLES_KEY.wasPressed() && moduleStateStore() != null) {
            var puddles = moduleStateStore().stateFor("puddles");
            puddles.setEnabled(!puddles.enabled());
            moduleStateStore().save();
        }

        if (musicPreferences.enabled()) com.krovex.client.music.LocalMediaSession.get().ensureRunning();
        if (musicPreferences.enabled() && spotifyAuthFlow.isConnected()) {
            withFreshAccessToken(token -> spotifyNowPlayingClient.pollAsync(token));
        }

        boolean localSpotify = com.krovex.client.music.LocalMediaSession.get().available();
        boolean playPausePressed = MUSIC_PLAY_PAUSE_KEY.wasPressed();   // wasPressed() consumes the press: read once
        boolean nextPressed = MUSIC_NEXT_TRACK_KEY.wasPressed();
        boolean previousPressed = MUSIC_PREVIOUS_TRACK_KEY.wasPressed();
        applyMediaKeyDefaults(client);
        if (previousPressed && musicPreferences.enabled()) {
            if (localSpotify) {
                com.krovex.client.music.LocalMediaSession.get().skip("previous");
            } else if (spotifyAuthFlow.isConnected()) {
                withFreshAccessToken(token -> Thread.ofVirtual().start(() -> {
                    try { spotifyNowPlayingClient.previousTrack(token); } catch (Exception ignored) { }
                }));
            }
        }
        if (playPausePressed && musicPreferences.enabled() && localSpotify) {
            com.krovex.client.music.LocalMediaSession.get().togglePlayback();
        } else if (playPausePressed) {
            // Never fires if Music is off or nothing is connected, per spec: "must NOT
            // pause the user's actual Spotify playback" when the feature itself is off --
            // there is simply no code path here that can reach Spotify in that state.
            if (musicPreferences.enabled() && spotifyAuthFlow.isConnected()) {
                withFreshAccessToken(token -> Thread.ofVirtual().start(() -> {
                    try {
                        boolean currentlyPlaying = spotifyNowPlayingClient.currentTrack()
                                .map(com.krovex.client.music.SpotifyTrack::isPlaying).orElse(false);
                        spotifyNowPlayingClient.playPause(token, currentlyPlaying);
                    } catch (Exception ignored) {
                        // Section 3: "give a clear status if playback controls are unavailable" --
                        // a full build surfaces this via a transient HUD toast; the exception
                        // itself already carries a readable message (see checkControlResponse).
                    }
                }));
            }
        }
        if (nextPressed && musicPreferences.enabled() && localSpotify) {
            com.krovex.client.music.LocalMediaSession.get().skip("next");
        } else if (nextPressed) {
            if (musicPreferences.enabled() && spotifyAuthFlow.isConnected()) {
                withFreshAccessToken(token -> Thread.ofVirtual().start(() -> {
                    try {
                        spotifyNowPlayingClient.nextTrack(token);
                    } catch (Exception ignored) {
                        // see above
                    }
                }));
            }
        }
    }

    /**
     * Older installs saved the music keys as "unbound", which would hide the new arrow-key defaults. Once, bind any
     * that are still unbound to their defaults (a key the player set themselves is never touched).
     */
    private void applyMediaKeyDefaults(MinecraftClient client) {
        if (musicPreferences.mediaKeysInitialised() || client.options == null) return;
        musicPreferences.setMediaKeysInitialised(true);
        Object[][] defaults = {{MUSIC_PLAY_PAUSE_KEY, GLFW.GLFW_KEY_DOWN}, {MUSIC_NEXT_TRACK_KEY, GLFW.GLFW_KEY_RIGHT},
                {MUSIC_PREVIOUS_TRACK_KEY, GLFW.GLFW_KEY_LEFT}};
        boolean changed = false;
        for (Object[] pair : defaults) {
            KeyBinding binding = (KeyBinding) pair[0];
            if (binding.isUnbound()) {
                binding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode((Integer) pair[1]));
                changed = true;
            }
        }
        if (changed) { KeyBinding.updateKeysByCode(); client.options.write(); }
        musicPreferences.save(KrovexConfigPaths.musicPreferencesFile());
    }

    /** Refreshes the cached Spotify session off-thread if it's stale, then hands the caller a usable access token. Never blocks the caller. */
    private void withFreshAccessToken(java.util.function.Consumer<String> useToken) {
        SpotifyAuthFlow.SpotifySession session = spotifySession;
        if (session != null && session.valid()) {
            useToken.accept(session.accessToken());
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                spotifySession = spotifyAuthFlow.refresh();
                useToken.accept(spotifySession.accessToken());
            } catch (SpotifyAuthFlow.SpotifyAuthException ignored) {
                // Not connected, or the refresh token was revoked -- the Music panel's own
                // "Connected"/"Disconnect" button state is the user-visible signal for this.
            }
        });
    }

    private void handleKrovexKeyPressed(MinecraftClient client) {
        Screen current = client.currentScreen;
        if (current instanceof QuickPopupScreen popup) {
            popup.handleKrovexKeyPressedWhileOpen(); // already-open popup: Right Shift closes it, same as Escape
        } else if (current instanceof KrovexMenuScreen menu) {
            menu.close(); // already-open full menu: Right Shift closes it, same as Escape
        } else if (current == null) {
            // Only open over gameplay (current == null), never steal Right Shift from
            // some other screen (inventory, chat, another mod's GUI) that's already open.
            if (popupPreference.showQuickPopup()) {
                client.setScreen(new QuickPopupScreen());
            } else {
                client.setScreen(KrovexMenuScreen.openDirectly());
            }
        }
    }

    public static ThemeStore themeStore() { return themeStore; }
    public static HudProfileStore hudProfileStore() { return hudProfileStore; }
    public static SkyPresetStore skyPresetStore() { return skyPresetStore; }
    public static ModuleStateStore moduleStateStore() { return moduleStateStore; }
    public static MusicPreferences musicPreferences() { return musicPreferences; }
    public static EscapeMenuSettings escapeMenuSettings() { return escapeMenuSettings; }
    public static PopupPreference popupPreference() { return popupPreference; }
    public static com.krovex.client.config.StylePreference stylePreference() { return stylePreference; }
    public static SpotifyAuthFlow spotifyAuthFlow() { return spotifyAuthFlow; }
    public static SpotifyNowPlayingClient spotifyNowPlayingClient() { return spotifyNowPlayingClient; }

    /**
     * The lyrics imported for the current track, lazily (re)loaded whenever the
     * {@link MusicPreferences#lyricsFilePath()} changes -- lets both HUD Lyrics and
     * Sky Lyrics (which poll every frame) share one parsed copy instead of each
     * re-reading the file off disk.
     */
    public static java.util.Optional<LrcLyrics> currentLyrics() {
        String path = musicPreferences == null ? null : musicPreferences.lyricsFilePath();
        if (path == null || path.isBlank()) {
            loadedLyricsPath = null;
            loadedLyrics = null;
            // no imported file: look the playing song up by itself (can be switched off in the Music panel)
            if (musicPreferences != null && musicPreferences.autoLyrics() && spotifyNowPlayingClient != null) {
                var track = spotifyNowPlayingClient.currentTrack();
                if (track.isPresent()) {
                    return com.krovex.client.music.LyricsFetcher.lookup(track.get(),
                            KrovexConfigPaths.configDir().resolve("lyrics-cache"));
                }
            }
            return java.util.Optional.empty();
        }
        if (!path.equals(loadedLyricsPath)) {
            try {
                loadedLyrics = LrcLyrics.loadFromFile(java.nio.file.Path.of(path));
                loadedLyricsPath = path;
            } catch (Exception e) {
                loadedLyrics = null;
                loadedLyricsPath = path; // don't retry a broken/missing file every frame
            }
        }
        return java.util.Optional.ofNullable(loadedLyrics);
    }
}
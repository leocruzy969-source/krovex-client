package com.krovex.client.hud;

import com.krovex.client.KrovexClientMod;
import com.krovex.client.modules.ModuleDefinition;
import com.krovex.client.modules.ModuleRegistry;
import com.krovex.client.modules.ModuleState;
import com.krovex.client.music.MusicPreferences;
import com.krovex.client.music.SpotifyNowPlayingClient;
import com.krovex.client.music.SpotifyTrack;
import com.krovex.theme.KrovexTheme;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * The single
 * {@link HudRenderCallback} registration that draws every enabled HUD element over
 * the actual game view, each at the position/scale/anchor {@link HudProfileStore}
 * holds for it -- this is what makes the HUD editor (HudEditScreen, written earlier)
 * meaningful: whatever the player drags there is exactly what this renders here.
 *
 * Skips all per-module work for a disabled module (just a map lookup + boolean
 * check), per spec's "disabled modules skip rendering and per-tick work" --
 * nothing here allocates or queries network/IO state for an element the player
 * has turned off.
 */
public final class KrovexHudOverlay {
    private static KrovexHudOverlay registered;
    private record HudSample(long timestamp, Text value) {}
    private final java.util.Map<String, HudSample> samples = new java.util.HashMap<>();
    private KrovexTheme cachedTheme;
    private long themeReadAt;

    public void register() {
        registered = this;
        HudRenderCallback.EVENT.register(this::onHudRender);
    }

    private void onHudRender(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            com.krovex.client.visuals.Puddles.grade(client);    // the Storm shader's colour grade (hand included), before any HUD
            com.krovex.client.visuals.ColorGrade.apply(client); // colour grading over the world, before any HUD is drawn
        }
        if (client.player == null || client.options.hudHidden
                || client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen
                || client.currentScreen instanceof HudEditScreen) return;
        ShapeCache.nextFrame();
        renderWidgets(context, client, KrovexClientMod.hudProfileStore().active(), null);
    }

    static void renderEditor(DrawContext context, MinecraftClient client, HudProfile profile,
                             java.util.Map<String, java.awt.Rectangle> bounds) {
        if (registered != null) registered.renderWidgets(context, client, profile, bounds);
    }

    private void renderWidgets(DrawContext context, MinecraftClient client, HudProfile profile,
                               java.util.Map<String, java.awt.Rectangle> bounds) {
        long now = System.nanoTime();
        if (cachedTheme == null || now - themeReadAt >= 500_000_000L) {
            cachedTheme = KrovexClientMod.themeStore().getActiveTheme();
            themeReadAt = now;
        }
        KrovexTheme theme = cachedTheme;
        if (com.krovex.client.gui.MenuStyle.rainbow()) {   // RGB wave: the whole HUD follows the travelling colour
            theme = theme.toBuilder().accentColor(com.krovex.client.gui.MenuStyle.liveAccent(theme.accentColor())).build();
        }


        boolean dynamic = bounds == null && com.krovex.client.features.DynamicHud.active();   // never fade in the HUD editor
        for (ModuleDefinition def : ModuleRegistry.all()) {
            ModuleState state = KrovexClientMod.moduleStateStore().stateFor(def.id());
            if (!state.enabled() || def.id().equals("cps-counter") || def.id().equals("keystrokes")) continue;
            if (dynamic && FADEABLE.contains(def.id())) {
                float alpha = com.krovex.client.features.DynamicHud.alphaFor(def.id(), signatureFor(def.id(), client), now);
                if (alpha <= .01f) continue;
                com.krovex.client.features.HudAlpha.set(alpha);
            }
            try { renderModule(context, profile, def, theme, client, now, bounds); }
            finally { com.krovex.client.features.HudAlpha.reset(); }
        }

        int leftNow = clicks(leftClicks), rightNow = clicks(rightClicks);
        float inputAlpha = dynamic ? com.krovex.client.features.DynamicHud.alphaFor("input", InputHud.activity(client, leftNow, rightNow), now) : 1f;
        if (inputAlpha > .01f) {
            com.krovex.client.features.HudAlpha.set(inputAlpha);
            try { InputHud.render(context, client, profile, theme, leftNow, rightNow, bounds); }
            finally { com.krovex.client.features.HudAlpha.reset(); }
        }
        boolean lyricsShown = renderHudLyrics(context, profile, theme, bounds);
        if (!lyricsShown) {                 // the lyrics looks already include the song info
            float musicAlpha = 1f;
            if (dynamic) {
                var playing = KrovexClientMod.spotifyNowPlayingClient().currentTrack();
                musicAlpha = com.krovex.client.features.DynamicHud.alphaFor("music-widget",
                        playing.map(t -> java.util.Objects.hash(t.title(), t.isPlaying())).orElse(0), now);
            }
            if (musicAlpha > .01f) {
                com.krovex.client.features.HudAlpha.set(musicAlpha);
                try { renderMusicWidget(context, profile, theme, client, bounds); }
                finally { com.krovex.client.features.HudAlpha.reset(); }
            }
        }
        if (bounds == null) renderSkyLyrics(context, theme);
    }

    /** The widgets Dynamic HUD may fade (the rest of the module list has no HUD of its own). */
    private static final java.util.Set<String> FADEABLE = java.util.Set.of("fps-counter", "ping", "coordinates", "player-ping",
            "potion-timers", "equipped-item-indicator");

    /** A number that changes whenever a widget has something new to show (time ticking down or fps wobbling do not count). */
    private int signatureFor(String id, MinecraftClient client) {
        var player = client.player;
        return switch (id) {
            case "fps-counter" -> client.getCurrentFps() / 10;
            case "coordinates" -> player == null ? 0 : player.getBlockPos().hashCode();
            case "equipped-item-indicator" -> player == null ? 0 : java.util.Objects.hash(player.getMainHandStack().getItem(),
                    player.getMainHandStack().getDamage(), player.getAttackCooldownProgress(0f) < .99f);
            case "potion-timers" -> {
                int hash = 7;
                if (player != null) for (var effect : player.getStatusEffects()) hash = hash * 31 + effect.getEffectType().hashCode() * 3 + effect.getAmplifier();
                yield hash;
            }
            case "player-ping" -> java.util.Objects.hashCode(PlayerPingTracker.current());
            default -> moduleValueText(id, client).hashCode();
        };
    }

    private void renderModule(DrawContext context, HudProfile profile, ModuleDefinition def, KrovexTheme theme, MinecraftClient client, long now, java.util.Map<String, java.awt.Rectangle> bounds) {
        HudElementLayout layout = profile.layoutFor(def.id());
        if (!layout.visible()) return;
        if (def.id().equals("player-ping")) {
            var rectangle = HudWidgets.playerPing(context, client, layout, theme, bounds != null);
            if (rectangle != null && bounds != null) bounds.put(def.id(), rectangle);
            return;
        }
        if (def.id().equals("ping")) {
            var rectangle = HudWidgets.ping(context, client, layout, theme, bounds != null);
            if (rectangle != null && bounds != null) bounds.put(def.id(), rectangle);
            return;
        }
        if (def.id().equals("equipped-item-indicator")) {
            var rectangle = HudWidgets.heldItem(context, client, layout, theme, bounds != null, setting("equipped-item-indicator", 1) != 0);
            if (rectangle != null && bounds != null) bounds.put(def.id(), rectangle);
            return;
        }
        if (def.id().equals("potion-timers")) {
            var rectangle = HudWidgets.potions(context, client, layout, theme, bounds != null);
            if (rectangle != null && bounds != null) bounds.put(def.id(), rectangle);
            return;
        }
        HudSample sample = samples.get(def.id());
        if (sample == null || now - sample.timestamp() >= 100_000_000L) {
            sample = new HudSample(now, Text.literal(moduleValueText(def.id(), client)));
            samples.put(def.id(), sample);
        }
        String text = sample.value().getString();
        if (text.isEmpty() && bounds != null) {
            text = switch (def.id()) {
                case "ping" -> "42 ms";
                case "potion-timers" -> "Speed 1:30";
                case "equipped-item-indicator" -> "Diamond Sword 1561/1561";
                default -> "";
            };
        }
        if (text.isEmpty()) return;
        String preview = client.textRenderer.trimToWidth(text, Math.max(1, context.getScaledWindowWidth() / 3));
        Text label = com.krovex.client.gui.KrovexFontRegistry.apply(Text.literal(preview), theme.fontFamily());
        int textWidth = client.textRenderer.getWidth(label);
        int width = Math.max(1, textWidth + 16);
        int height = 18;
        var rectangle = InputHud.drawAt(context, layout, width, height, () -> {
            InputHud.panel(context, def.id(), 0, 0, width, height, 8, theme);
            context.drawText(client.textRenderer, label, 8, (height - 8) / 2, theme.textColor(), false);
        });
        if (bounds != null) bounds.put(def.id(), rectangle);
    }

    /** Each module's actual live value -- the rendering position above is shared, but the value each one shows is module-specific. */
    private String moduleValueText(String moduleId, MinecraftClient client) {
        return switch (moduleId) {
            case "ping" -> {
                var connection = client.getNetworkHandler();
                var entry = connection == null || client.player == null || client.isInSingleplayer()
                        ? null : connection.getPlayerListEntry(client.player.getUuid());
                yield entry == null ? "" : entry.getLatency() + " ms";
            }
            case "fps-counter" -> client.getCurrentFps() + " FPS";
            case "potion-timers" -> client.player == null ? "" : client.player.getStatusEffects().stream()
                    .map(effect -> effect.getEffectType().value().getName().getString() + " " + duration(effect.getDuration() / 20))
                    .collect(java.util.stream.Collectors.joining("  "));
            case "equipped-item-indicator" -> client.player == null || client.player.getMainHandStack().isEmpty() ? ""
                    : client.player.getMainHandStack().getName().getString() + " "
                    + durability(client.player.getMainHandStack())
                    + (setting("equipped-item-indicator", 1) == 0 ? "" :
                    " · " + Math.round(client.player.getAttackCooldownProgress(0) * 100) + "% ready");
            case "coordinates" -> client.player != null
                    ? String.format("%.0f, %.0f, %.0f", client.player.getX(), client.player.getY(), client.player.getZ())
                    : "";
            default -> "";
        };
    }

    private static final long[] leftClicks = new long[128];
    private static final long[] rightClicks = new long[128];
    private static int leftIndex;
    private static int rightIndex;

    public static void recordClick(int button) {
        if (button == 0) leftClicks[leftIndex++ & 127] = System.nanoTime();
        if (button == 1) rightClicks[rightIndex++ & 127] = System.nanoTime();
    }

    public static int setting(String id, int fallback) {
        return KrovexClientMod.moduleStateStore().stateFor(id).setting(fallback);
    }

    /** True when the player chose the "Transparent" background for this module (no panel, just the content). */
    public static boolean transparent(String id) {
        return KrovexClientMod.moduleStateStore().stateFor(id).transparent();
    }

    /** The module's background opacity as 0..1 (set per module in its settings). */
    public static float backgroundAlpha(String id) {
        return KrovexClientMod.moduleStateStore().stateFor(id).backgroundOpacity() / 100f;
    }

    public static boolean enabled(String id) {
        return KrovexClientMod.moduleStateStore().stateFor(id).enabled();
    }

    private static int clicks(long[] buffer) {
        long cutoff = System.nanoTime() - 1_000_000_000L;
        int count = 0;
        for (long time : buffer) if (time != 0 && time > cutoff) count++;
        return count;
    }

    private static String durability(net.minecraft.item.ItemStack stack) {
        return !stack.isDamageable() ? "—" : (stack.getMaxDamage() - stack.getDamage()) + "/" + stack.getMaxDamage();
    }

    private static String duration(int seconds) {
        if (seconds < 0) return "∞";
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    private void renderMusicWidget(DrawContext context, HudProfile profile, KrovexTheme theme, MinecraftClient client,
                                   java.util.Map<String, java.awt.Rectangle> bounds) {
        MusicPreferences prefs = KrovexClientMod.musicPreferences();
        boolean editor = bounds != null;
        if (!prefs.enabled()) return; // music widget stays fully inert when Music is off

        Optional<SpotifyTrack> current = KrovexClientMod.spotifyNowPlayingClient().currentTrack();
        if (current.isEmpty() && prefs.hideWhenStopped() && !editor) return;

        HudElementLayout layout = profile.layoutFor("music-widget");
        int baseWidth = (int) (160 * prefs.widgetScale());
        int baseHeight = Math.max(14, (int) (19 * prefs.widgetScale()));
        if (!layout.visible()) return;
        float opacity = Math.max(.35f, prefs.backgroundOpacity());
        // shrinks to a small pause pill while paused / nothing playing, and grows back when it plays again
        long nowNs = System.nanoTime();
        float dt = musicShrinkAt == 0 ? 0f : Math.min(.1f, (nowNs - musicShrinkAt) / 1e9f);
        musicShrinkAt = nowNs;
        float target = !editor && (current.isEmpty() || !current.get().isPlaying()) ? 1f : 0f;
        float step = dt / .38f;
        musicShrink = musicShrink < target ? Math.min(target, musicShrink + step) : Math.max(target, musicShrink - step);
        float eased = musicShrink * musicShrink * (3 - 2 * musicShrink);
        final int compactWidth = baseHeight + 10;
        final int widgetWidth = Math.round(baseWidth + (compactWidth - baseWidth) * eased);
        String title = current.map(SpotifyTrack::title).orElse(editor ? "Your song \u00B7 Artist" : "Not playing");
        final String clock = prefs.showProgressBar() && current.isPresent() ? current.get().timeLabel() : "";
        final int clockWidth = clock.isEmpty() ? 0 : client.textRenderer.getWidth(clock) + 6;
        String trimmed = client.textRenderer.trimToWidth(title, Math.max(1, baseWidth - 34 - clockWidth));
        Text label = com.krovex.client.gui.KrovexFontRegistry.apply(Text.literal(trimmed), theme.fontFamily());
        var rectangle = InputHud.drawAt(context, layout, widgetWidth, baseHeight, () -> {
            InputHud.bubble(context, 0, 0, widgetWidth, baseHeight, baseHeight / 2, theme.accentColor(), opacity, theme, false);
            float textAlpha = Math.clamp(1f - eased * 1.7f, 0f, 1f);
            if (textAlpha > .03f) {
                context.enableScissor(0, 0, widgetWidth - 4, baseHeight);
                context.drawText(client.textRenderer, "\u266A", 7, (baseHeight - 8) / 2, InputHud.scaleAlpha(theme.accentColor(), textAlpha), false);
                context.drawText(client.textRenderer, label, 19, (baseHeight - 8) / 2, InputHud.scaleAlpha(theme.textColor(), textAlpha), false);
                context.disableScissor();
            }
            float iconAlpha = Math.clamp((eased - .4f) / .6f, 0f, 1f);
            if (iconAlpha > .03f) {      // pause glyph: two little bars
                int bx = widgetWidth / 2 - 3, by = baseHeight / 2 - 4;
                int colour = InputHud.scaleAlpha(theme.accentColor(), iconAlpha);
                context.fill(bx, by, bx + 2, by + 8, colour);
                context.fill(bx + 4, by, bx + 6, by + 8, colour);
            }
            if (prefs.showProgressBar() && current.isPresent() && textAlpha > .03f) {
                if (!clock.isEmpty()) {     // "1:00 - 2:14" at the right end
                    context.drawText(client.textRenderer, clock, baseWidth - 8 - client.textRenderer.getWidth(clock) + 2, (baseHeight - 8) / 2,
                            InputHud.scaleAlpha(theme.textColor(), textAlpha * .75f), false);
                }
                int lineW = baseWidth - 20;       // a thin progress line along the bottom edge
                context.fill(10, baseHeight - 3, 10 + lineW, baseHeight - 2, InputHud.scaleAlpha(0xFF777180, textAlpha * .6f));
                int done = Math.round(lineW * current.get().progressFraction());
                if (done > 0) context.fill(10, baseHeight - 3, 10 + done, baseHeight - 2, InputHud.scaleAlpha(theme.accentColor(), textAlpha));
            }
        });        if (editor) bounds.put("music-widget", rectangle);
    }

    private float musicShrink;
    private long musicShrinkAt;
    private final LyricsView hudLyricsView = new LyricsView();
    private final SkyWords skyWords = new SkyWords();

    /**
     * HUD lyrics in the chosen look (Spotlight or Song cover); both include the song info, so the plain music pill is
     * hidden while one is showing. Shown in the HUD editor with sample lines so it can be placed and resized.
     * Returns true when a lyrics look was drawn.
     */
    private boolean renderHudLyrics(DrawContext context, HudProfile profile, KrovexTheme theme,
                                    java.util.Map<String, java.awt.Rectangle> bounds) {
        MusicPreferences prefs = KrovexClientMod.musicPreferences();
        boolean editor = bounds != null;
        if (!prefs.enabled() || !prefs.hudLyricsEnabled()) return false; // independent of Sky Lyrics
        HudElementLayout layout = profile.layoutFor("hud-lyrics");
        if (!layout.visible()) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        var track = KrovexClientMod.spotifyNowPlayingClient().currentTrack().filter(SpotifyTrack::hasTrack);
        var lyrics = track.isPresent() ? KrovexClientMod.currentLyrics() : java.util.Optional.<com.krovex.client.music.LrcLyrics>empty();
        boolean real = track.isPresent() && lyrics.isPresent() && !lyrics.get().isEmpty();
        if (!real && !editor) return false;
        boolean cover = "SONG_COVER".equals(prefs.hudLyricsStyle());
        com.krovex.client.music.LrcLyrics lines = real ? lyrics.get() : LyricsView.SAMPLE;
        long progress = real ? track.get().progressMs() : System.currentTimeMillis() % 16_000;
        LyricsView.Song song = real ? new LyricsView.Song(track.get().title(), track.get().artist(), track.get().durationMs())
                : new LyricsView.Song("Your song", "Artist", 16_000);
        java.awt.Rectangle rectangle = cover
                ? hudLyricsView.drawSongCover(context, client, layout, theme, lines, progress, song, prefs.backgroundOpacity())
                : hudLyricsView.drawSpotlight(context, client, layout, theme, lines, progress, song);
        if (editor && rectangle != null) bounds.put("hud-lyrics", rectangle);
        return true;
    }

    private void renderSkyLyrics(DrawContext context, KrovexTheme theme) {
        MusicPreferences prefs = KrovexClientMod.musicPreferences();
        if (!prefs.enabled() || !prefs.skyLyricsEnabled()) return; // independent toggle from HUD Lyrics
        var track = KrovexClientMod.spotifyNowPlayingClient().currentTrack();
        var lyrics = KrovexClientMod.currentLyrics();
        if (track.isEmpty() || !track.get().hasTrack() || lyrics.isEmpty() || lyrics.get().isEmpty()) return;
        skyWords.draw(context, MinecraftClient.getInstance(), theme, lyrics.get(), track.get().progressMs(), prefs);
    }
}
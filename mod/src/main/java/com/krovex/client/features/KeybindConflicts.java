package com.krovex.client.features;

import com.krovex.client.KrovexClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds keys that Krovex shares with other bindings, reading Minecraft's own keybind list (which every mod that
 * registers a KeyBinding the normal Fabric way ends up in).
 */
public final class KeybindConflicts {
    private KeybindConflicts() {}

    /** One clash: our binding and the other binding that uses the same key. */
    public record Conflict(KeyBinding ours, KeyBinding other) {}

    /** All Krovex keybinds. */
    public static List<KeyBinding> ours() {
        List<KeyBinding> list = new ArrayList<>();
        for (KeyBinding binding : new KeyBinding[] {KrovexClientMod.KROVEX_MENU_KEY, KrovexClientMod.MUSIC_PLAY_PAUSE_KEY,
                KrovexClientMod.MUSIC_NEXT_TRACK_KEY, KrovexClientMod.MUSIC_PREVIOUS_TRACK_KEY}) {
            if (binding != null) list.add(binding);
        }
        return list;
    }

    private static boolean isOurs(KeyBinding binding) {
        for (KeyBinding ours : ours()) if (ours == binding) return true;
        return false;
    }

    /** Every non-Krovex binding currently bound to {@code key}. */
    /** Every binding on this key except {@code self}: other mods', the game's, and other Krovex keys too. */
    public static List<KeyBinding> allUsing(InputUtil.Key key, KeyBinding self) {
        List<KeyBinding> found = new ArrayList<>();
        var options = MinecraftClient.getInstance().options;
        if (options == null || key == null || key.equals(InputUtil.UNKNOWN_KEY)) return found;
        for (KeyBinding other : options.allKeys) {
            if (other == self || other.isUnbound()) continue;
            if (InputUtil.fromTranslationKey(other.getBoundKeyTranslationKey()).equals(key)) found.add(other);
        }
        return found;
    }

    public static List<KeyBinding> othersUsing(InputUtil.Key key) {
        List<KeyBinding> found = new ArrayList<>();
        var options = MinecraftClient.getInstance().options;
        if (options == null || key == null || key.equals(InputUtil.UNKNOWN_KEY)) return found;
        for (KeyBinding other : options.allKeys) {
            if (isOurs(other) || other.isUnbound()) continue;
            if (InputUtil.fromTranslationKey(other.getBoundKeyTranslationKey()).equals(key)) found.add(other);
        }
        return found;
    }

    /** Every clash right now, for the module's settings screen. */
    public static List<Conflict> current() {
        List<Conflict> conflicts = new ArrayList<>();
        for (KeyBinding ours : ours()) {
            if (ours.isUnbound()) continue;
            for (KeyBinding other : othersUsing(InputUtil.fromTranslationKey(ours.getBoundKeyTranslationKey()))) {
                conflicts.add(new Conflict(ours, other));
            }
        }
        return conflicts;
    }

    private static int cachedCount;
    private static long countedAt;

    /** How many clashes there are, re-checked every couple of seconds (it is read from the HUD). */
    public static int count() {
        long now = System.nanoTime();
        if (now - countedAt > 2_000_000_000L) { cachedCount = current().size(); countedAt = now; }
        return cachedCount;
    }

    /** "Category: Action  (Mod name)" for any binding. */
    public static String describe(KeyBinding binding) {
        String action = net.minecraft.client.resource.language.I18n.translate(binding.getId());
        String category = binding.getCategory().getLabel().getString();
        return category + ": " + action;
    }

    public static String owner(KeyBinding binding) {
        String namespace = binding.getCategory().id().getNamespace();
        if (namespace.equals("minecraft")) return "Minecraft";
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(namespace)
                .map(c -> c.getMetadata().getName()).orElse(namespace);
    }
}
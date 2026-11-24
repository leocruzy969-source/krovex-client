package com.krovex.client.hud;

import com.krovex.client.gui.KrovexFontRegistry;
import com.krovex.theme.KrovexTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** HUD widgets that are more than a single text pill. Currently: the potion-effect list with per-effect icons. */
final class HudWidgets {
    private record Row(Identifier sprite, Text name, String time, boolean urgent) {}

    private static final int ROW = 16, ICON = 14, MAX_ROWS = 8;

    private HudWidgets() {}

    /** Returns null when there is nothing to show (no active effects outside the HUD editor). */
    static Rectangle potions(DrawContext c, MinecraftClient client, HudElementLayout layout, KrovexTheme theme, boolean editor) {
        List<StatusEffectInstance> effects = new ArrayList<>();
        if (client.player != null) effects.addAll(client.player.getStatusEffects());
        if (effects.isEmpty() && editor) {
            effects.add(new StatusEffectInstance(StatusEffects.SPEED, 20 * 90, 1));
            effects.add(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 45));
            effects.add(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 8));
        }
        if (effects.isEmpty()) return null;
        if (effects.size() > MAX_ROWS) effects = effects.subList(0, MAX_ROWS);

        var font = client.textRenderer;
        List<Row> rows = new ArrayList<>();
        int nameWidth = 0, timeWidth = 0;
        for (StatusEffectInstance effect : effects) {
            Identifier sprite = effect.getEffectType().getKey()
                    .map(key -> key.getValue().withPrefixedPath("mob_effect/")).orElse(Identifier.ofVanilla("mob_effect/speed"));
            String level = effect.getAmplifier() > 0 ? " " + roman(effect.getAmplifier() + 1) : "";
            Text name = KrovexFontRegistry.regular(
                    Text.literal(effect.getEffectType().value().getName().getString() + level));
            String time = effect.isInfinite() ? "∞" : clock(effect.getDuration() / 20);
            boolean urgent = !effect.isInfinite() && effect.getDuration() <= 20 * 10;
            rows.add(new Row(sprite, name, time, urgent));
            nameWidth = Math.max(nameWidth, font.getWidth(name));
            timeWidth = Math.max(timeWidth, font.getWidth(KrovexFontRegistry.regular(Text.literal(time))));
        }
        int width = 6 + ICON + 5 + nameWidth + 10 + timeWidth + 6, height = rows.size() * ROW + 6;
        int w = width;
        return InputHud.drawAt(c, layout, width, height, () -> {
            InputHud.panel(c, "potion-timers", 0, 0, w, height, 5, theme);
            int y = 3;
            for (Row row : rows) {
                if (com.krovex.client.features.HudAlpha.get() >= .5f) c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, row.sprite(), 6, y + (ROW - ICON) / 2, ICON, ICON);
                c.drawTextWithShadow(font, row.name(), 6 + ICON + 5, y + (ROW - 8) / 2, theme.textColor());
                Text time = KrovexFontRegistry.regular(Text.literal(row.time()));
                c.drawTextWithShadow(font, time, w - 6 - font.getWidth(time), y + (ROW - 8) / 2,
                        row.urgent() ? 0xFFFF6B6B : theme.accentColor());
                y += ROW;
            }
        });
    }

    /** Four little bars like the multiplayer list: more filled = better ping, in the ping colour. */
    private static void signal(DrawContext c, int x, int y, int ping) {
        signal(c, x, y, ping, PlayerPingTracker.colorFor(ping));
    }

    private static void signal(DrawContext c, int x, int y, int ping, int colour) {
        int bars = ping < 65 ? 4 : ping < 105 ? 3 : ping < 160 ? 2 : 1;
        for (int i = 0; i < 4; i++) {
            int h = 3 + 2 * i;
            c.fill(x + i * 3, y + 9 - h, x + i * 3 + 2, y + 9, i < bars ? colour : 0x55FFFFFF);
        }
    }

    /** The Ping Counter module: signal bars + "42 ms" in a pill outlined in the ping colour. */
    static Rectangle ping(DrawContext c, MinecraftClient client, HudElementLayout layout, KrovexTheme theme, boolean editor) {
        var connection = client.getNetworkHandler();
        var entry = connection == null || client.player == null || client.isInSingleplayer()
                ? null : connection.getPlayerListEntry(client.player.getUuid());
        int ping;
        if (entry != null) ping = entry.getLatency();
        else if (editor) ping = 42;
        else return null;
        var font = client.textRenderer;
        Text text = KrovexFontRegistry.bold(Text.literal(ping + " ms"));
        int colour = theme.accentColor();   // the Ping Counter follows the Krovex theme colour
        int w = 8 + 11 + 5 + font.getWidth(text) + 8, h = 18;
        return InputHud.drawAt(c, layout, w, h, () -> {
            InputHud.panel(c, "ping", 0, 0, w, h, 8, theme, colour);
            signal(c, 8, 5, ping, colour);
            c.drawText(font, text, 8 + 11 + 5, (h - 8) / 2, colour, false);
        });
    }

    /** Head + name + signal bars + coloured ping of the player you last hit. Returns null when there is nothing to show. */
    static Rectangle playerPing(DrawContext c, MinecraftClient client, HudElementLayout layout, KrovexTheme theme, boolean editor) {
        java.util.UUID id = PlayerPingTracker.current();
        net.minecraft.client.network.PlayerListEntry entry = id == null || client.getNetworkHandler() == null ? null
                : client.getNetworkHandler().getPlayerListEntry(id);
        net.minecraft.entity.player.SkinTextures skin;
        String name;
        int ping;
        if (entry != null) {
            skin = entry.getSkinTextures();
            name = entry.getProfile().name();
            ping = entry.getLatency();
        } else if (editor && client.player != null) {
            skin = client.player.getSkin();
            name = client.player.getGameProfile().name();
            ping = 42;
        } else {
            return null;
        }
        var font = client.textRenderer;
        Text label = KrovexFontRegistry.bold(Text.literal(name));
        Text pingText = KrovexFontRegistry.bold(Text.literal(ping + " ms"));
        int colour = theme.accentColor();     // like the Ping Counter: the HUD theme colour, not a good/bad traffic light
        int width = 7 + 14 + 7 + font.getWidth(label) + 9 + 11 + 4 + font.getWidth(pingText) + 8, height = 20;
        int w = width;
        return InputHud.drawAt(c, layout, width, height, () -> {
            InputHud.panel(c, "player-ping", 0, 0, w, height, 8, theme, colour);
            net.minecraft.client.gui.PlayerSkinDrawer.draw(c, skin, 7, 3, 14);
            c.drawText(font, label, 7 + 14 + 7, (height - 8) / 2, theme.textColor(), false);
            int bx = 7 + 14 + 7 + font.getWidth(label) + 9;
            signal(c, bx, (height - 9) / 2, ping, colour);
            c.drawText(font, pingText, bx + 11 + 4, (height - 8) / 2, colour, false);
        });
    }

    /**
     * The Equipped Item Indicator: the item in your hand inside a ring that fills as it becomes ready, with its
     * name and "100% ready" beside it. Returns null when your hand is empty (outside the HUD editor).
     */
    static Rectangle heldItem(DrawContext c, MinecraftClient client, HudElementLayout layout, KrovexTheme theme, boolean editor,
                              boolean showPercent) {
        net.minecraft.item.ItemStack stack = client.player == null ? net.minecraft.item.ItemStack.EMPTY : client.player.getMainHandStack();
        if (stack.isEmpty()) {
            if (!editor) return null;
            stack = new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_SWORD);
        }
        final net.minecraft.item.ItemStack shown = stack;
        float ready = 1f;
        if (client.player != null && !editor) {
            ready = client.player.getAttackCooldownProgress(0f);
            try {
                ready = Math.min(ready, 1f - client.player.getItemCooldownManager().getCooldownProgress(shown, 0f));
            } catch (RuntimeException ignored) { /* no item cooldown info: attack cooldown alone */ }
        }
        final float progress = Math.clamp(ready, 0f, 1f);
        var font = client.textRenderer;
        Text name = KrovexFontRegistry.bold(Text.literal(stack.getName().getString()));
        String detail = (showPercent ? Math.round(progress * 100) + "% ready" : "")
                + (shown.isDamageable() ? (showPercent ? "  \u00B7  " : "") + (shown.getMaxDamage() - shown.getDamage()) + "/" + shown.getMaxDamage() : "");
        Text detailText = KrovexFontRegistry.regular(Text.literal(detail.isEmpty() ? " " : detail));
        int textWidth = Math.max(font.getWidth(name), font.getWidth(detailText));
        int width = 8 + 26 + 8 + textWidth + 10, height = 32;
        int ringColour = progress >= .99f ? 0xFFB5F53E : 0xFFFFC857;
        return InputHud.drawAt(c, layout, width, height, () -> {
            InputHud.panel(c, "equipped-item-indicator", 0, 0, width, height, 9, theme);
            int cx = 8 + 13, cy = height / 2;
            int s = InputHud.guiScale(), steps = 120;
            var matrices = c.getMatrices();
            matrices.pushMatrix();
            try {
                matrices.scale(1f / s, 1f / s);      // draw the ring at screen resolution so it is a smooth circle
                float radius = 11.5f * s;
                int thick = Math.max(2, Math.round(1.7f * s));
                for (int k = 0; k < steps; k++) {
                    double angle = -Math.PI / 2 + k * 2 * Math.PI / steps;
                    int px = cx * s + (int) Math.round(Math.cos(angle) * radius) - thick / 2;
                    int py = cy * s + (int) Math.round(Math.sin(angle) * radius) - thick / 2;
                    c.fill(px, py, px + thick, py + thick, k < progress * steps ? ringColour : 0x3DFFFFFF);
                }
            } finally {
                matrices.popMatrix();
            }            if (com.krovex.client.features.HudAlpha.get() >= .5f) c.drawItem(shown, cx - 8, cy - 8);
            c.drawText(font, name, 8 + 26 + 8, 7, theme.textColor(), false);
            c.drawText(font, detailText, 8 + 26 + 8, 18, showPercent ? ringColour : 0xFFB8B2C4, false);
        });
    }

    private static String clock(int seconds) {
        return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    private static String roman(int n) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < numerals.length ? numerals[n] : Integer.toString(n);
    }
}

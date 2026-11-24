package com.krovex.client.hud;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;

import java.util.UUID;

/** Remembers the last player you hit, for the Player Ping Display module. */
public final class PlayerPingTracker {
    /** How long the display stays up after a hit. */
    private static final long SHOW_MS = 6_000;

    private static volatile UUID target;
    private static volatile long hitAt;

    private PlayerPingTracker() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && entity instanceof PlayerEntity other && other != player) {
                target = other.getUuid();
                hitAt = System.currentTimeMillis();
            }
            return ActionResult.PASS;
        });
    }

    /** The player to show right now, or null if you haven't hit anyone in the last few seconds. */
    public static UUID current() {
        UUID id = target;
        return id != null && System.currentTimeMillis() - hitAt <= SHOW_MS ? id : null;
    }

    /** Ping colour: under 65 ms green, 65 up to 105 ms yellow, 105 ms and above red. */
    public static int colorFor(int pingMs) {
        if (pingMs < 65) return 0xFF55E87A;
        if (pingMs < 105) return 0xFFFFD84A;
        return 0xFFFF5555;
    }
}

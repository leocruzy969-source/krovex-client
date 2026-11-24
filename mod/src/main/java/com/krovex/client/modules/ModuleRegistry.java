package com.krovex.client.modules;

import java.util.List;

/**
 * The fixed catalog of built-in Krovex modules from the original spec's HUD/overlay
 * list. "Built-in Krovex modules" are deliberately a separate concept from
 * "installed mods" (the Mods tab's Fabric JARs) -- toggling one of these never
 * touches the mods directory, and the Mods tab must never list these as if they
 * were installed JARs, per spec: "clearly distinguish Krovex's own built-in
 * modules from separately installed mods."
 */
public final class ModuleRegistry {

    private static final List<ModuleDefinition> ALL = List.of(
            new ModuleDefinition("fps-counter", "FPS Counter", ModuleCategory.HUD, "Current frames per second.", true),
            new ModuleDefinition("ping", "Ping Counter", ModuleCategory.HUD, "Server latency in milliseconds.", true),
            new ModuleDefinition("coordinates", "Coordinates", ModuleCategory.HUD, "Player X/Y/Z position.", true),
            new ModuleDefinition("keystrokes", "Keystrokes", ModuleCategory.HUD, "WASD + mouse-button display.", true),
            new ModuleDefinition("cps-counter", "CPS Counter", ModuleCategory.COMBAT, "Clicks per second.", true),
            new ModuleDefinition("player-ping", "Player Ping Display", ModuleCategory.HUD,
                    "Shows the head, name and ping of the player you just hit.", true),
            new ModuleDefinition("potion-timers", "Potion Timers", ModuleCategory.HUD, "Remaining duration of active effects.", true),
            new ModuleDefinition("clear-inventory", "Clear Inventory", ModuleCategory.RENDERING,
                    "See-through inventories, chests and containers: no panel, just your items on soft dark slots.", false),
            new ModuleDefinition("particle-controls", "Particle Controls", ModuleCategory.RENDERING, "Reduce or hide distracting particle effects.", true),
            new ModuleDefinition("equipped-item-indicator", "Equipped Item Indicator", ModuleCategory.COMBAT,
                    "Durability + attack-cooldown for spear/mace PvP.", true),
            new ModuleDefinition("motion-controls", "Motion Controls", ModuleCategory.RENDERING,
                    "Turn off camera bob, hurt shake, sprint FOV change and the portal wobble.", true),
            new ModuleDefinition("weather-audio", "Weather Audio", ModuleCategory.RENDERING,
                    "Separate volume for rain and thunder, without ducking other sounds.", true),
            new ModuleDefinition("dynamic-hud", "Dynamic HUD", ModuleCategory.HUD,
                    "HUD widgets fade out when nothing changes and fade back in on activity.", true),
            new ModuleDefinition("keybind-conflicts", "Keybind Conflicts", ModuleCategory.HUD,
                    "Asks before you bind a Krovex key that another mod or the game already uses.", true),
            new ModuleDefinition("puddles", "Storm Shader", ModuleCategory.RENDERING,
                    "Reflective puddles, haze, glow and a moody colour grade. Puddles mirror the sky, including your Krovex sky.", true),
            new ModuleDefinition("background-fps", "Background FPS Limit", ModuleCategory.RENDERING,
                    "Caps the frame rate while the game window is not in focus.", true)
    );

    private ModuleRegistry() {}

    public static List<ModuleDefinition> all() { return ALL; }

    public static ModuleDefinition byId(String id) {
        return ALL.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
    }
}

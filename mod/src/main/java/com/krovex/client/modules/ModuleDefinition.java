package com.krovex.client.modules;

/**
 * Static metadata for one built-in Krovex module (not a Fabric mod -- see
 * {@link ModuleRegistry}'s javadoc on the built-in-module-vs-installed-mod
 * distinction the Mods tab has to preserve). The actual render/tick hook for each
 * of these (FPS graph, coords, compass, keystrokes, CPS, armor durability, potion
 * timers, clock, session timer, zoom, screenshot tools, particle controls, custom
 * crosshair editor, equipped-item durability + attack-cooldown indicator) is a
 * HudRenderCallback/ClientTickEvent registration in {@code KrovexClientMod} --
 * this class only carries what the Modules screen needs to list, search, filter,
 * and persist per-module state for, independent of how each one actually renders.
 */
public final class ModuleDefinition {
    private final String id;
    private final String displayName;
    private final ModuleCategory category;
    private final String description;
    private final boolean hasSettings;

    public ModuleDefinition(String id, String displayName, ModuleCategory category, String description, boolean hasSettings) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.description = description;
        this.hasSettings = hasSettings;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public ModuleCategory category() { return category; }
    public String description() { return description; }
    public boolean hasSettings() { return hasSettings; }
}

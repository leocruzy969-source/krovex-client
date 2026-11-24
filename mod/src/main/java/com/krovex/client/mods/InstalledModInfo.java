package com.krovex.client.mods;

import java.util.List;

/**
 * One entry in the Mods tab's list of separately installed Fabric mods -- deliberately
 * NOT the same list as {@link com.krovex.client.modules.ModuleRegistry}'s built-in
 * Krovex modules. Per spec: "clearly distinguish Krovex's own built-in modules from
 * separately installed mods" and "show installed mod names, versions, dependencies,
 * and compatibility." {@code isKrovexClient} flags the one entry (this mod itself)
 * that should never be shown as removable from this list.
 */
public record InstalledModInfo(
        String modId,
        String name,
        String version,
        List<String> dependsOnModIds,
        boolean compatibleWithCurrentMinecraft,
        boolean isKrovexClient
) {
}

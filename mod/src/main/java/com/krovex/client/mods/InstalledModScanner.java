package com.krovex.client.mods;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the CURRENT instance's actual mod set via FabricLoader's own mod list -- this
 * is what makes the Mods tab correct no matter which launcher started this JVM (the
 * official launcher, Modrinth App, Prism, or Krovex Launcher): there is no separate
 * "installed mods" bookkeeping of our own to go stale, we just ask Fabric what's
 * actually loaded right now, per spec's "mod manages the current instance."
 */
public final class InstalledModScanner {

    public List<InstalledModInfo> scan() {
        List<InstalledModInfo> result = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            var meta = container.getMetadata();
            List<String> deps = new ArrayList<>();
            for (ModDependency dep : meta.getDependencies()) {
                if (dep.getKind() == ModDependency.Kind.DEPENDS) deps.add(dep.getModId());
            }
            boolean compatible = meta.getDependencies().stream()
                    .filter(d -> d.getKind() == ModDependency.Kind.DEPENDS && "minecraft".equals(d.getModId()))
                    .allMatch(d -> FabricLoader.getInstance().getModContainer("minecraft")
                            .map(mc -> d.matches(mc.getMetadata().getVersion()))
                            .orElse(true));
            result.add(new InstalledModInfo(
                    meta.getId(),
                    meta.getName(),
                    versionString(meta.getVersion()),
                    deps,
                    compatible,
                    "krovex-client".equals(meta.getId())
            ));
        }
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    private String versionString(Version v) {
        return v == null ? "unknown" : v.getFriendlyString();
    }
}

package com.krovex.launcher.launch;

import com.krovex.launcher.launch.RuleEvaluator;
import com.krovex.launcher.model.Installation;
import com.krovex.launcher.model.MicrosoftAccount;
import com.krovex.launcher.network.VersionMetadata;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class LaunchArgumentBuilder {
    public LaunchCommand build(VersionMetadata metadata, Installation installation, MicrosoftAccount account, List<String> classpathEntries, Path nativesDir, Path assetsRoot, Path clientJar) {
        LinkedHashSet<String> features = new LinkedHashSet<String>();
        if (installation.windowWidth() > 0 && installation.windowHeight() > 0) {
            features.add("has_custom_resolution");
        }
        Map<String, String> substitutions = this.buildSubstitutions(metadata, installation, account, nativesDir, assetsRoot);
        ArrayList<String> classpath = new ArrayList<String>(classpathEntries);
        classpath.add(clientJar.toString());
        ArrayList<String> jvmArgs = new ArrayList<String>();
        jvmArgs.add("-Xms" + installation.memoryMinMb() + "M");
        jvmArgs.add("-Xmx" + installation.memoryMaxMb() + "M");
        jvmArgs.add("-Djava.library.path=" + String.valueOf(nativesDir));
        jvmArgs.add("-Dminecraft.launcher.brand=krovex-launcher");
        jvmArgs.add("-Dminecraft.launcher.version=0.1.0");
        RuleEvaluator ruleEvaluator = new RuleEvaluator(features);
        if (metadata.usesStructuredArguments()) {
            jvmArgs.addAll(this.resolveArgumentList(metadata.jvmArguments(), ruleEvaluator, substitutions));
        }
        List<String> gameArgs = metadata.usesStructuredArguments() ? this.resolveArgumentList(metadata.gameArguments(), ruleEvaluator, substitutions) : this.resolveLegacyArguments(metadata.legacyMinecraftArguments(), substitutions);
        if (features.contains("has_custom_resolution") && !this.containsFlag(gameArgs, "--width")) {
            gameArgs.add("--width");
            gameArgs.add(String.valueOf(installation.windowWidth()));
            gameArgs.add("--height");
            gameArgs.add(String.valueOf(installation.windowHeight()));
        }
        if (installation.fullscreen() && !gameArgs.contains("--fullscreen")) gameArgs.add("--fullscreen");
        return new LaunchCommand(jvmArgs, metadata.mainClass(), gameArgs, classpath);
    }

    private boolean containsFlag(List<String> args, String flag) {
        return args.contains(flag);
    }

    private Map<String, String> buildSubstitutions(VersionMetadata metadata, Installation installation, MicrosoftAccount account, Path nativesDir, Path assetsRoot) {
        return Map.ofEntries(Map.entry("auth_player_name", account != null ? account.minecraftUsername() : "Player"), Map.entry("version_name", metadata.id()), Map.entry("game_directory", installation.gameDirectory().toString()), Map.entry("assets_root", assetsRoot.toString()), Map.entry("game_assets", assetsRoot.toString()), Map.entry("assets_index_name", metadata.assetIndexId() == null ? "legacy" : metadata.assetIndexId()), Map.entry("auth_uuid", account != null && account.minecraftUuid() != null ? account.minecraftUuid() : "00000000-0000-0000-0000-000000000000"), Map.entry("auth_access_token", account != null && account.minecraftAccessToken() != null ? account.minecraftAccessToken() : "0"), Map.entry("auth_session", "token:0:0"), Map.entry("user_type", account != null && account.isOffline() ? "legacy" : "msa"), Map.entry("user_properties", "{}"), Map.entry("version_type", metadata.type() == null ? "release" : metadata.type()), Map.entry("natives_directory", nativesDir.toString()), Map.entry("launcher_name", "Krovex Launcher"), Map.entry("launcher_version", "0.1.0"), Map.entry("clientid", "krovex-launcher"), Map.entry("auth_xuid", "0"), Map.entry("resolution_width", String.valueOf(installation.windowWidth())), Map.entry("resolution_height", String.valueOf(installation.windowHeight())));
    }

    private List<String> resolveArgumentList(List<Object> rawArgs, RuleEvaluator ruleEvaluator, Map<String, String> substitutions) {
        ArrayList<String> result = new ArrayList<String>();
        for (Object entry : rawArgs) {
            Map m;
            Map rule;
            List rules;
            if (entry instanceof String) {
                String s = (String)entry;
                result.add(this.substitute(s, substitutions));
                continue;
            }
            if (!(entry instanceof Map) || !ruleEvaluator.isAllowed(rules = (List)(rule = (m = (Map)entry)).get("rules"))) continue;
            Object value = rule.get("value");
            if (value instanceof String) {
                String s = (String)value;
                result.add(this.substitute(s, substitutions));
                continue;
            }
            if (!(value instanceof List)) continue;
            List list = (List)value;
            for (Object v : list) {
                result.add(this.substitute((String)v, substitutions));
            }
        }
        return result;
    }

    private List<String> resolveLegacyArguments(String flat, Map<String, String> substitutions) {
        ArrayList<String> result = new ArrayList<String>();
        if (flat == null || flat.isBlank()) {
            return result;
        }
        for (String token : flat.split(" ")) {
            result.add(this.substitute(token, substitutions));
        }
        return result;
    }

    private String substitute(String template, Map<String, String> substitutions) {
        String result = template;
        for (Map.Entry<String, String> e : substitutions.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }
        return result;
    }

    public record LaunchCommand(List<String> jvmArgs, String mainClass, List<String> gameArgs, List<String> classpath) {
        public List<String> fullCommandAfterJavaBinary() {
            ArrayList<String> all = new ArrayList<String>(this.jvmArgs);
            all.add("-cp");
            all.add(String.join((CharSequence)System.getProperty("path.separator"), this.classpath));
            all.add(this.mainClass);
            all.addAll(this.gameArgs);
            return all;
        }
    }
}



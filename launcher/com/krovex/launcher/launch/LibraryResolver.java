package com.krovex.launcher.launch;

import com.krovex.launcher.launch.RuleEvaluator;
import com.krovex.launcher.network.VersionMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LibraryResolver {
    private final RuleEvaluator ruleEvaluator;

    public LibraryResolver(Set<String> activeFeatures) {
        this.ruleEvaluator = new RuleEvaluator(activeFeatures);
    }

    public List<ResolvedLibrary> resolve(VersionMetadata metadata) {
        ArrayList<ResolvedLibrary> result = new ArrayList<ResolvedLibrary>();
        for (Object o : metadata.libraries()) {
            String classifierKey;
            Map nativeArtifact;
            String osKey;
            Object classifierKeyObj;
            Map lib = (Map)o;
            List rules = (List)lib.get("rules");
            if (!this.ruleEvaluator.isAllowed(rules)) continue;
            String name = (String)lib.get("name");
            Map downloads = (Map)lib.get("downloads");
            if (downloads == null) {
                if (lib.get("url") instanceof String repository) {
                    String[] parts = name.split(":", 4);
                    if (parts.length >= 3) {
                        String classifier = parts.length == 4 ? "-" + parts[3] : "";
                        String path = parts[0].replace('.', '/') + "/" + parts[1] + "/" +
                                parts[2] + "/" + parts[1] + "-" + parts[2] + classifier + ".jar";
                        String url = repository.endsWith("/") ? repository : repository + "/";
                        result.add(new ResolvedLibrary(name, path, url + path, null, 0L, false));
                    }
                }
                continue;
            }
            Map artifact = (Map)downloads.get("artifact");
            if (artifact != null) {
                result.add(this.toResolved(name, artifact, false));
            }
            Map natives = (Map)lib.get("natives");
            Map classifiers = (Map)downloads.get("classifiers");
            if (natives == null || classifiers == null || (classifierKeyObj = natives.get(osKey = RuleEvaluator.detectOsName())) == null || (nativeArtifact = (Map)classifiers.get(classifierKey = ((String)classifierKeyObj).replace("${arch}", LibraryResolver.is64Bit() ? "64" : "32"))) == null) continue;
            result.add(this.toResolved(name + ":" + classifierKey, nativeArtifact, true));
        }
        return result;
    }

    private static boolean is64Bit() {
        return System.getProperty("os.arch", "").contains("64");
    }

    private ResolvedLibrary toResolved(String mavenName, Map<String, Object> artifact, boolean isNative) {
        long l;
        String path = (String)artifact.get("path");
        String url = (String)artifact.get("url");
        String sha1 = (String)artifact.get("sha1");
        Object sizeObj = artifact.get("size");
        if (sizeObj instanceof Number) {
            Number n = (Number)sizeObj;
            l = n.longValue();
        } else {
            l = 0L;
        }
        long size = l;
        return new ResolvedLibrary(mavenName, path, url, sha1, size, isNative);
    }

    public record ResolvedLibrary(String mavenName, String relativePath, String url, String sha1, long size, boolean isNative) {
    }
}



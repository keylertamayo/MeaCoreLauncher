package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Evaluates {@code rules} on argument fragments: only rules whose OS/feature clauses match the host apply; then
 * the last matching applicable rule wins (allow/disallow). If no rule applies to the current host, the fragment
 * is allowed by default (Mojang-style manifests).
 */
public final class ArgumentRules {

    private ArgumentRules() {}

    public static boolean fragmentAllowed(JsonNode argObject, OsContext os, LaunchFeatures features) {
        JsonNode rules = argObject.get("rules");
        if (rules == null || !rules.isArray() || rules.isEmpty()) {
            return true;
        }
        Boolean allowed = null;
        for (JsonNode rule : rules) {
            if (!osClauseMatches(rule, os)) {
                continue;
            }
            if (!featureClauseMatches(rule, features)) {
                continue;
            }
            String action = rule.path("action").asText("allow");
            if ("allow".equals(action)) {
                allowed = true;
            } else if ("disallow".equals(action)) {
                allowed = false;
            }
        }
        if (allowed == null) {
            return true;
        }
        return allowed;
    }

    private static boolean osClauseMatches(JsonNode rule, OsContext os) {
        JsonNode osNode = rule.get("os");
        if (osNode == null || osNode.isNull()) {
            return true;
        }
        String name = osNode.path("name").asText("");
        if (!name.isEmpty() && !name.equalsIgnoreCase(os.name())) {
            return false;
        }
        String arch = osNode.path("arch").asText("");
        if (!arch.isEmpty()) {
            String cur = "arm64".equals(os.arch()) ? "arm64" : "x86";
            if (!arch.equalsIgnoreCase(cur)) {
                return false;
            }
        }
        return true;
    }

    private static boolean featureClauseMatches(JsonNode rule, LaunchFeatures features) {
        JsonNode feats = rule.get("features");
        if (feats == null || feats.isNull()) {
            return true;
        }
        var it = feats.fields();
        while (it.hasNext()) {
            var e = it.next();
            boolean want = e.getValue().asBoolean();
            if (features.isOn(e.getKey()) != want) {
                return false;
            }
        }
        return true;
    }
}

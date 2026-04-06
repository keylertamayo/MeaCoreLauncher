package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

public final class RuleEvaluator {

    private RuleEvaluator() {}

    public static boolean libraryAllowed(JsonNode library, OsContext os) {
        JsonNode rules = library.get("rules");
        if (rules == null || !rules.isArray()) {
            return true;
        }
        Boolean last = null;
        for (JsonNode rule : rules) {
            String action = text(rule, "action");
            boolean matches = matchesRule(rule, os);
            if ("allow".equals(action)) {
                last = matches;
            } else if ("disallow".equals(action)) {
                if (matches) {
                    last = false;
                }
            }
        }
        return last == null || last;
    }

    private static boolean matchesRule(JsonNode rule, OsContext os) {
        JsonNode osNode = rule.get("os");
        if (osNode == null || osNode.isNull()) {
            return true;
        }
        String name = text(osNode, "name");
        if (name != null && !name.equalsIgnoreCase(os.name())) {
            return false;
        }
        String arch = text(osNode, "arch");
        if (arch != null) {
            String current = "arm64".equals(os.arch()) ? "arm64" : "x86";
            if ("x86".equals(arch) && !"x86".equals(current)) {
                return false;
            }
            if ("arm64".equals(arch) && !"arm64".equals(current)) {
                return false;
            }
        }
        return true;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}

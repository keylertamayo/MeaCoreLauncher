package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.JsonNode;


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
            String raw = os.arch();
            String current;
            if ("arm64".equals(raw) || "aarch64".equals(raw)) {
                current = "arm64";
            } else if ("x86".equals(raw) || "i386".equals(raw)) {
                current = "x86";
            } else {
                current = "x64";
            }
            if (!current.equals(arch)) {
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

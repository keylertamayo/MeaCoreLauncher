package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public final class ArgumentFlattener {

    private ArgumentFlattener() {}

    public static List<String> flatten(JsonNode arguments, String key, OsContext os, LaunchFeatures features) {
        List<String> out = new ArrayList<>();
        if (arguments == null || !arguments.isObject()) {
            return out;
        }
        JsonNode arr = arguments.get(key);
        if (arr == null || !arr.isArray()) {
            return out;
        }
        for (JsonNode node : arr) {
            if (node.isTextual()) {
                out.add(node.asText());
            } else if (node.isObject()) {
                if (!ArgumentRules.fragmentAllowed(node, os, features)) {
                    continue;
                }
                JsonNode value = node.get("value");
                if (value == null) {
                    continue;
                }
                if (value.isTextual()) {
                    out.add(value.asText());
                } else if (value.isArray()) {
                    for (JsonNode v : value) {
                        out.add(v.asText());
                    }
                }
            }
        }
        return out;
    }
}

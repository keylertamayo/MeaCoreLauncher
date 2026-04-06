package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merges Mojang version JSON chains ({@code inheritsFrom}).
 */
public final class VersionMerge {

    private static final ObjectMapper M = new ObjectMapper();

    private VersionMerge() {}

    public static JsonNode merge(JsonNode parent, JsonNode child) {
        if (parent == null || parent.isNull()) {
            return child.deepCopy();
        }
        ObjectNode out = parent.deepCopy();
        child.fields().forEachRemaining(e -> {
            String k = e.getKey();
            JsonNode v = e.getValue();
            if ("libraries".equals(k) && v.isArray()) {
                out.set(k, mergeLibraries(out.get("libraries"), (ArrayNode) v));
            } else if ("arguments".equals(k) && v.isObject()) {
                JsonNode existing = out.get("arguments");
                ObjectNode p = existing != null && existing.isObject() ? (ObjectNode) existing : null;
                out.set(k, mergeArguments(p, (ObjectNode) v));
            } else {
                out.set(k, v);
            }
        });
        return out;
    }

    private static ArrayNode mergeLibraries(JsonNode parentLibs, ArrayNode childLibs) {
        Map<String, JsonNode> byName = new LinkedHashMap<>();
        if (parentLibs != null && parentLibs.isArray()) {
            for (JsonNode lib : parentLibs) {
                String n = lib.path("name").asText("");
                if (!n.isBlank()) {
                    byName.put(n, lib);
                }
            }
        }
        for (JsonNode lib : childLibs) {
            String n = lib.path("name").asText("");
            if (!n.isBlank()) {
                byName.put(n, lib);
            } else {
                byName.put("__anon_" + byName.size(), lib);
            }
        }
        ArrayNode arr = M.createArrayNode();
        byName.values().forEach(arr::add);
        return arr;
    }

    private static ObjectNode mergeArguments(ObjectNode parentArgs, ObjectNode childArgs) {
        ObjectNode out = M.createObjectNode();
        ObjectNode p =
                parentArgs == null || parentArgs.isNull() ? M.createObjectNode() : parentArgs.deepCopy();
        if (childArgs == null || childArgs.isNull()) {
            return p;
        }
        for (String key : new String[] {"jvm", "game"}) {
            ArrayNode merged = M.createArrayNode();
            if (p.has(key) && p.get(key).isArray()) {
                merged.addAll((ArrayNode) p.get(key));
            }
            if (childArgs.has(key) && childArgs.get(key).isArray()) {
                merged.addAll((ArrayNode) childArgs.get(key));
            }
            if (merged.size() > 0) {
                out.set(key, merged);
            }
        }
        return out;
    }
}

package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MojangVersionResolver {

    public static final String MANIFEST_V2 =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final ObjectMapper M = new ObjectMapper();

    private final Path versionsDir;

    public MojangVersionResolver(Path versionsDir) {
        this.versionsDir = versionsDir;
    }

    public JsonNode loadManifest() throws Exception {
        byte[] bytes = HttpFiles.getBytes(MANIFEST_V2);
        return M.readTree(bytes);
    }

    public static List<ManifestVersionEntry> versionEntriesFromManifest(JsonNode manifest) {
        List<ManifestVersionEntry> out = new ArrayList<>();
        JsonNode versions = manifest.get("versions");
        if (versions == null || !versions.isArray()) {
            return out;
        }
        for (JsonNode v : versions) {
            String id = v.path("id").asText("");
            if (id.isBlank()) continue;
            String type = v.path("type").asText("");
            out.add(new ManifestVersionEntry(id, type));
        }
        return out;
    }

    public String findVersionUrl(JsonNode manifest, String versionId) {
        for (JsonNode v : manifest.get("versions")) {
            if (versionId.equals(v.get("id").asText())) {
                return v.get("url").asText();
            }
        }
        throw new IllegalArgumentException("Unknown version id: " + versionId);
    }

    public String findVersionSha1(JsonNode manifest, String versionId) {
        for (JsonNode v : manifest.get("versions")) {
            if (versionId.equals(v.get("id").asText())) {
                return v.path("sha1").asText(null);
            }
        }
        return null;
    }

    public JsonNode resolveMerged(String versionId) throws Exception {
        JsonNode manifest = loadManifest();
        return resolveMerged(versionId, manifest);
    }

    public JsonNode resolveMerged(String versionId, JsonNode manifest) throws Exception {
        Map<String, JsonNode> memo = new HashMap<>();
        return resolveRecursive(versionId, manifest, memo);
    }

    private JsonNode resolveRecursive(String versionId, JsonNode manifest, Map<String, JsonNode> memo)
            throws Exception {
        if (memo.containsKey(versionId)) {
            return memo.get(versionId);
        }
        JsonNode node = null;
        try {
            String url = findVersionUrl(manifest, versionId);
            String sha1 = findVersionSha1(manifest, versionId);
            Path raw = versionsDir.resolve(versionId).resolve("version-original.json");
            Files.createDirectories(raw.getParent());
            HttpFiles.downloadIfHashMismatch(url, raw, sha1);
            node = M.readTree(Files.readAllBytes(raw));
        } catch (IllegalArgumentException ex) {
            Path localJson = versionsDir.resolve(versionId).resolve(versionId + ".json");
            if (!Files.exists(localJson)) {
                localJson = versionsDir.resolve(versionId).resolve("version.json");
            }
            if (Files.exists(localJson)) {
                node = M.readTree(Files.readAllBytes(localJson));
            } else {
                throw ex;
            }
        }
        
        JsonNode merged;
        if (node.has("inheritsFrom") && !node.get("inheritsFrom").isNull()) {
            String parentId = node.get("inheritsFrom").asText();
            JsonNode parentMerged = resolveRecursive(parentId, manifest, memo);
            merged = VersionMerge.merge(parentMerged, node);
        } else {
            merged = node;
        }
        Path mergedPath = versionsDir.resolve(versionId).resolve("version.json");
        String content = M.writerWithDefaultPrettyPrinter().writeValueAsString(merged);
        Files.writeString(mergedPath, content);
        Files.writeString(versionsDir.resolve(versionId).resolve(versionId + ".json"), content);
        memo.put(versionId, merged);
        return merged;
    }

    /** Cached merged JSON for a version if {@link #resolveMerged(String)} already ran. */
    public Path mergedJsonPath(String versionId) {
        return versionsDir.resolve(versionId).resolve("version.json");
    }
}

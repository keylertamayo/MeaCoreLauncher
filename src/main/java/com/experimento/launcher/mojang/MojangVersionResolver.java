package com.experimento.launcher.mojang;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
            if (id.isBlank()) {
                continue;
            }
            String type = v.path("type").asText("");
            if ("old_alpha".equalsIgnoreCase(type) || "old_beta".equalsIgnoreCase(type)) {
                continue;
            }
            long releasedAtMs = 0L;
            String releaseTime = v.path("releaseTime").asText("");
            if (!releaseTime.isBlank()) {
                try {
                    releasedAtMs = Instant.parse(releaseTime).toEpochMilli();
                } catch (Exception ignored) {
                    releasedAtMs = 0L;
                }
            }
            out.add(new ManifestVersionEntry(id, type, releasedAtMs));
        }
        out.sort(Comparator.comparingLong(ManifestVersionEntry::releasedAtMs));
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
        String url = findVersionUrl(manifest, versionId);
        String sha1 = findVersionSha1(manifest, versionId);
        Path raw = versionsDir.resolve(versionId).resolve("version-original.json");
        Files.createDirectories(raw.getParent());
        HttpFiles.downloadIfHashMismatch(url, raw, sha1);
        JsonNode node = M.readTree(Files.readAllBytes(raw));
        JsonNode merged;
        if (node.has("inheritsFrom") && !node.get("inheritsFrom").isNull()) {
            String parentId = node.get("inheritsFrom").asText();
            JsonNode parentMerged = resolveRecursive(parentId, manifest, memo);
            merged = VersionMerge.merge(parentMerged, node);
        } else {
            merged = node;
        }
        Path mergedPath = versionsDir.resolve(versionId).resolve("version.json");
        Files.writeString(mergedPath, M.writerWithDefaultPrettyPrinter().writeValueAsString(merged));
        memo.put(versionId, merged);
        return merged;
    }

    /** Cached merged JSON for a version if {@link #resolveMerged(String)} already ran. */
    public Path mergedJsonPath(String versionId) {
        return versionsDir.resolve(versionId).resolve("version.json");
    }
}

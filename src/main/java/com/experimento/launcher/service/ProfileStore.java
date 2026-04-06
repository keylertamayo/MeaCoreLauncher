package com.experimento.launcher.service;

import com.experimento.launcher.model.LauncherProfile;
import com.experimento.launcher.util.OfflineUuid;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProfileStore {

    private final Path file;
    private final ObjectMapper mapper =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public ProfileStore(Path launcherDataDir) {
        this.file = launcherDataDir.resolve("profiles.json");
    }

    public List<LauncherProfile> loadOrCreateDefault() throws Exception {
        if (!Files.isRegularFile(file)) {
            List<LauncherProfile> list = new ArrayList<>();
            list.add(LauncherProfile.createDefault());
            save(list);
            return list;
        }
        List<LauncherProfile> list =
                mapper.readValue(file.toFile(), new TypeReference<List<LauncherProfile>>() {});
        for (LauncherProfile p : list) {
            if (p.offlineUuid == null || p.offlineUuid.isBlank()) {
                p.offlineUuid = OfflineUuid.toString(OfflineUuid.forUsername(p.username));
            }
            if (p.instanceId == null || p.instanceId.isBlank()) {
                p.instanceId = p.id;
            }
        }
        return list;
    }

    public void save(List<LauncherProfile> profiles) throws Exception {
        Files.createDirectories(file.getParent());
        mapper.writeValue(file.toFile(), profiles);
    }
}

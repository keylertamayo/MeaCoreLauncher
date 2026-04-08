package com.experimento.launcher.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LauncherProfile {
    public String id = UUID.randomUUID().toString();
    public String displayName = "Perfil";
    public String username = "Player";
    /** Offline UUID string; must match OfflinePlayer rule for username or be kept stable across launches. */
    public String offlineUuid = "";
    public String lastVersionId = "1.21.4";
    /** Directory name under instances/ */
    public String instanceId = "";
    public JvmPresetKind jvmPreset = JvmPresetKind.AUTO;
    public String customJvmArgs = "";
    public String javaExecutable = "";
    public boolean useGlobalMinecraftFolder = false;
    public List<ServerEntry> servers = new ArrayList<>();

    public LauncherProfile() {}

    public static LauncherProfile createDefault() {
        LauncherProfile p = new LauncherProfile();
        p.instanceId = p.id;
        p.displayName = "Principal";
        p.username = "Player";
        p.offlineUuid = com.experimento.launcher.util.OfflineUuid.toString(
                com.experimento.launcher.util.OfflineUuid.forUsername(p.username));
        return p;
    }
}

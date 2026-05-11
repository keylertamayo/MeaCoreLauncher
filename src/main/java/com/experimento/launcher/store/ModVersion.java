package com.experimento.launcher.store;

import java.util.List;

public record ModVersion(
    String versionId,
    String versionNumber,
    String downloadUrl,
    String fileName,
    long fileSize,
    List<String> gameVersions,
    List<String> loaders,
    String changelog
) {}

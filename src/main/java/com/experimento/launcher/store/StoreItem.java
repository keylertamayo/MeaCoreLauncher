package com.experimento.launcher.store;

public record StoreItem(
    String id,
    String title,
    String author,
    String description,
    String thumbnailUrl,
    long downloads,
    String latestVersion,
    String latestMcVersion,
    String downloadUrl,
    StoreCategory category,
    String sourceUrl
) {}

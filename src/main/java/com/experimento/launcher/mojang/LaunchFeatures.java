package com.experimento.launcher.mojang;

/** Feature flags referenced by modern version.json argument rules. */
public final class LaunchFeatures {

    public final boolean hasCustomResolution;
    public final int resolutionWidth;
    public final int resolutionHeight;

    public LaunchFeatures(boolean hasCustomResolution, int resolutionWidth, int resolutionHeight) {
        this.hasCustomResolution = hasCustomResolution;
        this.resolutionWidth = resolutionWidth;
        this.resolutionHeight = resolutionHeight;
    }

    public static LaunchFeatures defaults() {
        return new LaunchFeatures(false, 854, 480);
    }

    public boolean isOn(String key) {
        return switch (key) {
            case "has_custom_resolution" -> hasCustomResolution;
            case "has_quick_plays_support",
                    "is_quick_play_singleplayer",
                    "is_quick_play_multiplayer",
                    "is_quick_play_realms" -> false;
            default -> false;
        };
    }
}

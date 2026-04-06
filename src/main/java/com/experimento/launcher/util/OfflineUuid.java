package com.experimento.launcher.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class OfflineUuid {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private OfflineUuid() {}

    /** Same rule as vanilla {@code net.minecraft.util.UUIDUtil}. */
    public static UUID forUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username required");
        }
        String u = username.trim();
        if (!USERNAME.matcher(u).matches()) {
            throw new IllegalArgumentException("username must be 3-16 chars [a-zA-Z0-9_]");
        }
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + u).getBytes(StandardCharsets.UTF_8));
    }

    public static String toString(UUID uuid) {
        return uuid.toString().toLowerCase(Locale.ROOT);
    }

    public static UUID parse(String s) {
        return UUID.fromString(s);
    }

    public static boolean uuidMatchesUsername(String username, String storedUuid) {
        if (username == null || storedUuid == null || storedUuid.isBlank()) {
            return false;
        }
        try {
            UUID expected = forUsername(username.trim());
            UUID got = UUID.fromString(storedUuid.trim());
            return expected.equals(got);
        } catch (Exception e) {
            return false;
        }
    }
}

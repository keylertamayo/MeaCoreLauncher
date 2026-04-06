package com.experimento.launcher.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineUuidTest {

    @Test
    void roundTripMatchesUsername() {
        String u = OfflineUuid.toString(OfflineUuid.forUsername("TestUser"));
        assertTrue(OfflineUuid.uuidMatchesUsername("TestUser", u));
        assertFalse(OfflineUuid.uuidMatchesUsername("OtherUser", u));
    }
}

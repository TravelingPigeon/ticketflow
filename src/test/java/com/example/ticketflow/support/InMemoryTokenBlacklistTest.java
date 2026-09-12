package com.example.ticketflow.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTokenBlacklistTest {

    private final InMemoryTokenBlacklist blacklist =
            new InMemoryTokenBlacklist();

    @Test
    void shouldRememberBlacklistedToken() {
        blacklist.blacklist("jti-1", Duration.ofMinutes(10));

        assertTrue(blacklist.isBlacklisted("jti-1"));
        assertFalse(blacklist.isBlacklisted("jti-2"));
    }

    @Test
    void shouldTreatExpiredEntryAsValid() {
        blacklist.blacklist("jti-3", Duration.ofMillis(-1));

        assertFalse(blacklist.isBlacklisted("jti-3"));
    }
}

package com.example.ticketflow.support;

import com.example.ticketflow.auth.security.TokenBlacklist;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("test")
public class InMemoryTokenBlacklist implements TokenBlacklist {

    private final Map<String, Long> entries = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String tokenId, Duration ttl) {
        entries.put(
                tokenId,
                System.currentTimeMillis() + ttl.toMillis()
        );
    }

    @Override
    public boolean isBlacklisted(String tokenId) {
        Long expiresAt = entries.get(tokenId);

        if (expiresAt == null) {
            return false;
        }

        if (expiresAt < System.currentTimeMillis()) {
            entries.remove(tokenId);
            return false;
        }

        return true;
    }
}
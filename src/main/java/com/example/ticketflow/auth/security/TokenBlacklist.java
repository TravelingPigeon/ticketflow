package com.example.ticketflow.auth.security;

import java.time.Duration;

public interface TokenBlacklist {

    void blacklist(String tokenId, Duration ttl);

    boolean isBlacklisted(String tokenId);
}
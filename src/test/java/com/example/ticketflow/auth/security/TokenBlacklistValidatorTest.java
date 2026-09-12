package com.example.ticketflow.auth.security;

import com.example.ticketflow.support.InMemoryTokenBlacklist;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBlacklistValidatorTest {

    private final TokenBlacklist blacklist =
            new InMemoryTokenBlacklist();

    private final TokenBlacklistValidator validator =
            new TokenBlacklistValidator(blacklist);

    @Test
    void shouldPassWhenTokenIsNotBlacklisted() {
        OAuth2TokenValidatorResult result =
                validator.validate(tokenWithId("jti-active"));

        assertFalse(result.hasErrors());
    }

    @Test
    void shouldFailWhenTokenIsBlacklisted() {
        blacklist.blacklist("jti-revoked", Duration.ofMinutes(10));

        OAuth2TokenValidatorResult result =
                validator.validate(tokenWithId("jti-revoked"));

        assertTrue(result.hasErrors());
    }

    @Test
    void shouldNotAffectOtherTokens() {
        blacklist.blacklist("jti-revoked", Duration.ofMinutes(10));

        OAuth2TokenValidatorResult result =
                validator.validate(tokenWithId("jti-other"));

        assertFalse(result.hasErrors());
    }

    private Jwt tokenWithId(String tokenId) {
        Instant now = Instant.now();

        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("alice")
                .jti(tokenId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }
}

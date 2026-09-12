package com.example.ticketflow.auth.security;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Profile("!test")
public class RedisTokenBlacklist implements TokenBlacklist {

    private static final String KEY_PREFIX = "ticketflow:blacklist:jti:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenBlacklist(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklist(String tokenId, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }

        redisTemplate.opsForValue().set(key(tokenId), "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(tokenId)));
    }

    private String key(String tokenId) {
        return KEY_PREFIX + tokenId;
    }
}
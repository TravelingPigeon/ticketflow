package com.example.ticketflow.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ticketflow.jwt")
public record JwtProperties(
        String secret,
        Duration ttl
) {
}
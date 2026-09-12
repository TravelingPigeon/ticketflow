package com.example.ticketflow.user.dto;

public record LoginResponse<T>(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        T profile
) {
}
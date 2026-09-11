package com.example.ticketflow.auth.security;

import com.example.ticketflow.user.domain.enums.UserRole;

public record CurrentActor(
        Long tenantId,
        Long userId,
        String username,
        UserRole role
) {
    public boolean isRequester() {
        return role == UserRole.REQUESTER;
    }
}
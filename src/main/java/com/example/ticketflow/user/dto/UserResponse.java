package com.example.ticketflow.user.dto;

import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserRole;
import com.example.ticketflow.user.domain.enums.UserStatus;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        Long tenantId,
        String username,
        String displayName,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
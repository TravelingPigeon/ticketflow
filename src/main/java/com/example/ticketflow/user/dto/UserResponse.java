package com.example.ticketflow.user.dto;

import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        Long tenantId,
        String username,
        String displayName,
        List<String> roles,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse of(UserAccount user, List<String> roles) {
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getDisplayName(),
                List.copyOf(roles),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
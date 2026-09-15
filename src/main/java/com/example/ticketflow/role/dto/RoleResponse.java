package com.example.ticketflow.role.dto;

import com.example.ticketflow.role.domain.Role;

import java.util.List;

public record RoleResponse(
        Long id,
        String code,
        String name,
        Boolean builtIn,
        Boolean enabled,
        List<String> permissions
) {

    public static RoleResponse of(Role role, List<String> permissions) {
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getBuiltIn(),
                role.getEnabled(),
                List.copyOf(permissions)
        );
    }
}
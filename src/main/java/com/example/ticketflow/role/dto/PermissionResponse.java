package com.example.ticketflow.role.dto;

import com.example.ticketflow.role.domain.Permission;

public record PermissionResponse(
        Long id,
        String code,
        String name,
        String permissionGroup,
        String description
) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getName(),
                permission.getPermissionGroup(),
                permission.getDescription()
        );
    }
}
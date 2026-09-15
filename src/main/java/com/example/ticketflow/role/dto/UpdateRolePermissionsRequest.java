package com.example.ticketflow.role.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateRolePermissionsRequest(

        @NotNull(message = "permissions 不能为 null，清空权限请传空数组")
        List<String> permissions
) {
}
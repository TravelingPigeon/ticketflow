package com.example.ticketflow.role.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AssignMemberRolesRequest(

        @NotNull(message = "roleCodes 不能为 null，清空角色请传空数组")
        List<String> roleCodes
) {
}
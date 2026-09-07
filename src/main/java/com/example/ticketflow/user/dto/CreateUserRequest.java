package com.example.ticketflow.user.dto;

import com.example.ticketflow.user.domain.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

        @NotNull(message = "租户不能为空")
        Long tenantId,

        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名不能超过64个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须在8到72个字符之间")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 128, message = "显示名称不能超过128个字符")
        String displayName,

        UserRole role
) {
}
package com.example.ticketflow.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRoleRequest(

        @NotBlank(message = "角色编码不能为空")
        @Size(max = 64, message = "角色编码不能超过64个字符")
        @Pattern(
                regexp = "^[A-Z][A-Z0-9_]*$",
                message = "角色编码只能包含大写字母、数字和下划线，且以字母开头"
        )
        String code,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 128, message = "角色名称不能超过128个字符")
        String name,

        List<String> permissions
) {
}
package com.example.ticketflow.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(

        @NotBlank(message = "租户编码不能为空")
        @Size(max = 64, message = "租户编码不能超过64个字符")
        String code,

        @NotBlank(message = "租户名称不能为空")
        @Size(max = 128, message = "租户名称不能超过128个字符")
        String name
) {
}
package com.example.ticketflow.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerLoginRequest(

        @NotBlank(message = "租户编码不能为空")
        String tenantCode,

        @NotBlank(message = "邮箱不能为空")
        String email,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
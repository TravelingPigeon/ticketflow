package com.example.ticketflow.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCustomerRequest(

        @NotBlank(message = "租户编码不能为空")
        @Size(max = 64, message = "租户编码不能超过64个字符")
        String tenantCode,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过128个字符")
        String email,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须在8到72个字符之间")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 128, message = "显示名称不能超过128个字符")
        String displayName
) {
}
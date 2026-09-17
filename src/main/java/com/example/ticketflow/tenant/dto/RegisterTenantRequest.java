package com.example.ticketflow.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 租户自助注册请求。
 *
 * <p>一次请求同时创建"租户 + 内置角色 + 第一个管理员"，所以请求体里既有租户信息，
 * 也有管理员账号信息。这个接口是**匿名可调**的——否则第一个管理员永远建不出来。</p>
 */
public record RegisterTenantRequest(

        @NotBlank(message = "租户编码不能为空")
        @Size(max = 64, message = "租户编码不能超过64个字符")
        @Pattern(
                regexp = "^[a-z][a-z0-9-]*$",
                message = "租户编码只能包含小写字母、数字和连字符，且以小写字母开头"
        )
        String tenantCode,

        @NotBlank(message = "租户名称不能为空")
        @Size(max = 128, message = "租户名称不能超过128个字符")
        String tenantName,

        @NotBlank(message = "管理员用户名不能为空")
        @Size(max = 64, message = "管理员用户名不能超过64个字符")
        String adminUsername,

        @NotBlank(message = "管理员密码不能为空")
        @Size(min = 8, max = 72, message = "管理员密码长度必须在8到72个字符之间")
        String adminPassword,

        @NotBlank(message = "管理员显示名称不能为空")
        @Size(max = 128, message = "管理员显示名称不能超过128个字符")
        String adminDisplayName
) {
}
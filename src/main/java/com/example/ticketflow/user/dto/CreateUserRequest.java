package com.example.ticketflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名不能超过64个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度必须在8到72个字符之间")
        String password,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 128, message = "显示名称不能超过128个字符")
        String displayName,

        /**
         * 角色编码列表，可以传自定义角色的编码。
         *
         * <p>不传或传空数组都按 {@code AGENT} 处理——建一个"什么都不能做"的账号
         * 几乎不会是调用方的本意，与其静默建出来，不如给一个能用的默认值。</p>
         */
        List<String> roleCodes
) {
}
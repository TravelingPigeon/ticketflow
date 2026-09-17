package com.example.ticketflow.tenant.dto;

import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.user.dto.UserResponse;

/** 租户注册结果：新建的租户 + 它的第一个管理员。 */
public record TenantRegistrationResponse(
        Tenant tenant,
        UserResponse admin
) {
}
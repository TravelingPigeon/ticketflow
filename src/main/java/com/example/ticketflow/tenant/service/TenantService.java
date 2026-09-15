package com.example.ticketflow.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.service.RoleService;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService {

    private final TenantMapper tenantMapper;
    private final RoleService roleService;

    public TenantService(
            TenantMapper tenantMapper,
            RoleService roleService
    ) {
        this.tenantMapper = tenantMapper;
        this.roleService = roleService;
    }

    @Transactional
    public Tenant createTenant(CreateTenantRequest request) {
        Tenant existing = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getCode, request.code())
        );

        if (existing != null) {
            throw new BusinessException(
                    "TENANT_CODE_EXISTS",
                    "租户编码已存在"
            );
        }

        Tenant tenant = new Tenant();
        tenant.setCode(request.code().trim());
        tenant.setName(request.name().trim());
        tenant.setStatus("ACTIVE");

        tenantMapper.insert(tenant);

        // 新租户必须自带两个内置角色，否则这个租户里的用户一个权限都没有
        roleService.createBuiltInRoles(tenant.getId());

        return tenant;
    }
}
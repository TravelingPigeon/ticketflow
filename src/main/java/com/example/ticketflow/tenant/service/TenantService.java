package com.example.ticketflow.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TenantService {

    private final TenantMapper tenantMapper;

    public TenantService(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    public List<Tenant> findActiveTenants() {
        return tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getStatus, "ACTIVE")
                .orderByAsc(Tenant::getId));
    }

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
        return tenant;
    }
}

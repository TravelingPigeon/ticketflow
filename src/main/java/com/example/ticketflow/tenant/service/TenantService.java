package com.example.ticketflow.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    public Page<Tenant> pageActiveTenants(long current, long size) {
        if (current < 1) {
            throw new BusinessException(
                    "INVALID_PAGE",
                    "页码必须大于等于1"
            );
        }

        if (size < 1 || size > 100) {
            throw new BusinessException(
                    "INVALID_PAGE_SIZE",
                    "每页数量必须在1到100之间"
            );
        }

        Page<Tenant> page = new Page<>(current, size);

        LambdaQueryWrapper<Tenant> wrapper =
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getStatus, "ACTIVE")
                        .orderByAsc(Tenant::getId);

        return tenantMapper.selectPage(page, wrapper);
    }
}

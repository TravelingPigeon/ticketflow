package com.example.ticketflow.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.role.service.MemberRoleService;
import com.example.ticketflow.role.service.RoleService;
import com.example.ticketflow.sla.service.SlaPolicyService;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.dto.RegisterTenantRequest;
import com.example.ticketflow.tenant.dto.TenantRegistrationResponse;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.dto.CreateUserRequest;
import com.example.ticketflow.user.dto.UserResponse;
import com.example.ticketflow.user.service.UserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TenantService {

    private final TenantMapper tenantMapper;
    private final RoleService roleService;
    private final UserAccountService userAccountService;
    private final MemberRoleService memberRoleService;
    private final SlaPolicyService slaPolicyService;

    public TenantService(
            TenantMapper tenantMapper,
            RoleService roleService,
            UserAccountService userAccountService,
            MemberRoleService memberRoleService,
            SlaPolicyService slaPolicyService
    ) {
        this.tenantMapper = tenantMapper;
        this.roleService = roleService;
        this.userAccountService = userAccountService;
        this.memberRoleService = memberRoleService;
        this.slaPolicyService = slaPolicyService;
    }

    @Transactional
    public Tenant createTenant(CreateTenantRequest request) {
        Tenant existing = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getCode, request.code())
        );

        if (existing != null) {
            throw new BusinessException(
                    ErrorCode.TENANT_CODE_EXISTS,
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

        slaPolicyService.createDefaultPolicies(tenant.getId());

        return tenant;
    }

    /**
     * 租户自助注册：一个事务里建好"租户 + 内置角色 + 第一个管理员 + 管理员角色关联"。
     *
     * <p>这四件事必须一起成功或一起失败。少了角色，这个租户里的人拿不到任何权限；
     * 少了管理员，这个租户谁也进不去——两种半成品都比"注册失败"更难处理。</p>
     */
    @Transactional
    public TenantRegistrationResponse register(RegisterTenantRequest request) {
        Tenant tenant = createTenant(
                new CreateTenantRequest(
                        request.tenantCode(),
                        request.tenantName()
                )
        );

        UserAccount admin = userAccountService.createUser(
                tenant.getId(),
                new CreateUserRequest(
                        request.adminUsername(),
                        request.adminPassword(),
                        request.adminDisplayName(),
                        List.of(BuiltInRoles.ADMIN)
                )
        );

        return new TenantRegistrationResponse(
                tenant,
                UserResponse.of(
                        admin,
                        memberRoleService.roleCodesOf(tenant.getId(), admin.getId())
                )
        );
    }
}

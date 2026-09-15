package com.example.ticketflow.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.service.MemberRoleService;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserRole;
import com.example.ticketflow.user.domain.enums.UserStatus;
import com.example.ticketflow.user.dto.CreateUserRequest;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserAccountService {

    private final UserAccountMapper userAccountMapper;
    private final TenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final MemberRoleService memberRoleService;

    public UserAccountService(
            UserAccountMapper userAccountMapper,
            TenantMapper tenantMapper,
            PasswordEncoder passwordEncoder,
            MemberRoleService memberRoleService
    ) {
        this.userAccountMapper = userAccountMapper;
        this.tenantMapper = tenantMapper;
        this.passwordEncoder = passwordEncoder;
        this.memberRoleService = memberRoleService;
    }

    public UserAccount createUser(
            Long tenantId,
            CreateUserRequest request
    ) {
        Tenant tenant = tenantMapper.selectById(tenantId);

        if (tenant == null) {
            throw new BusinessException(
                    "TENANT_NOT_FOUND",
                    "租户不存在"
            );
        }

        UserAccount existingUser = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(
                                UserAccount::getTenantId,
                                tenantId
                        )
                        .eq(
                                UserAccount::getUsername,
                                request.username()
                        )
        );

        if (existingUser != null) {
            throw new BusinessException(
                    "USERNAME_EXISTS",
                    "当前租户下用户名已存在"
            );
        }

        UserAccount user = new UserAccount();
        user.setTenantId(tenantId);
        user.setUsername(request.username().trim());
        user.setDisplayName(request.displayName().trim());

        String passwordHash =
                passwordEncoder.encode(request.password());

        user.setPasswordHash(passwordHash);

        if (request.role() == null) {
            user.setRole(UserRole.AGENT);
        } else {
            user.setRole(request.role());
        }

        user.setStatus(UserStatus.ACTIVE);

        userAccountMapper.insert(user);

        // 关键：tf_user.role 只是过渡期的兼容列，真正决定权限的是 tf_member_role。
        // 不写这张关联表，新建出来的成员就是一个"能登录但什么都做不了"的账号。
        memberRoleService.grantRoleByCode(
                tenantId,
                user.getId(),
                user.getRole().name()
        );

        return userAccountMapper.selectById(user.getId());
    }
}
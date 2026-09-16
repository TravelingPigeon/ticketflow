package com.example.ticketflow.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.role.service.MemberRoleService;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserStatus;
import com.example.ticketflow.user.dto.CreateUserRequest;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional
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

        List<String> roleCodes = request.roleCodes() == null
                || request.roleCodes().isEmpty()
                ? List.of(BuiltInRoles.AGENT)
                : request.roleCodes().stream().distinct().toList();

        user.setStatus(UserStatus.ACTIVE);

        userAccountMapper.insert(user);

        // 角色关系是权限的唯一来源，建账号时必须一起建。
        // 不建的话，这个账号能登录、却什么都做不了。
        memberRoleService.replaceMemberRoles(
                tenantId,
                user.getId(),
                roleCodes
        );

        return userAccountMapper.selectById(user.getId());
    }
}
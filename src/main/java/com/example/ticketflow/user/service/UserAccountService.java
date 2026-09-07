package com.example.ticketflow.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
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

    public UserAccountService(
            UserAccountMapper userAccountMapper,
            TenantMapper tenantMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountMapper = userAccountMapper;
        this.tenantMapper = tenantMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount createUser(CreateUserRequest request) {
        Tenant tenant = tenantMapper.selectById(request.tenantId());

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
                                request.tenantId()
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
        user.setTenantId(request.tenantId());
        user.setUsername(request.username().trim());
        user.setDisplayName(request.displayName().trim());

        String passwordHash =
                passwordEncoder.encode(request.password());

        user.setPasswordHash(passwordHash);

        if (request.role() == null) {
            user.setRole(UserRole.REQUESTER);
        } else {
            user.setRole(request.role());
        }

        user.setStatus(UserStatus.ACTIVE);

        userAccountMapper.insert(user);

        return userAccountMapper.selectById(user.getId());
    }
}
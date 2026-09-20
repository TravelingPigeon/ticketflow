package com.example.ticketflow.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.audit.service.AuditLogService;
import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserStatus;
import com.example.ticketflow.user.dto.LoginRequest;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final TenantMapper tenantMapper;
    private final AuditLogService auditLogService;

    public AuthService(
            UserAccountMapper userAccountMapper,
            PasswordEncoder passwordEncoder,
            TenantMapper tenantMapper,
            AuditLogService auditLogService
    ) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
        this.tenantMapper = tenantMapper;
        this.auditLogService = auditLogService;
    }

    public UserAccount verifyCredentials(LoginRequest request) {
        Tenant tenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getCode, request.tenantCode().trim())
        );

        if (tenant == null) {
            // 租户都是错的，没有 tenantId 可写——但这恰恰是最该记下来的一种尝试
            auditLogService.recordLoginFailure(
                    ActorType.MEMBER,
                    null,
                    null,
                    request.tenantCode(),
                    request.username(),
                    "TENANT_NOT_FOUND"
            );

            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "租户、用户名或密码错误"
            );
        }

        UserAccount user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getTenantId, tenant.getId())
                        .eq(UserAccount::getUsername, request.username())
        );

        if (user == null) {
            auditLogService.recordLoginFailure(
                    ActorType.MEMBER,
                    tenant.getId(),
                    null,
                    request.tenantCode(),
                    request.username(),
                    "USER_NOT_FOUND"
            );

            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "租户、用户名或密码错误"
            );
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            auditLogService.recordLoginFailure(
                    ActorType.MEMBER,
                    tenant.getId(),
                    user.getId(),
                    request.tenantCode(),
                    request.username(),
                    "USER_LOCKED"
            );

            throw new BusinessException(
                    ErrorCode.USER_LOCKED,
                    "用户已被锁定"
            );
        }

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            auditLogService.recordLoginFailure(
                    ActorType.MEMBER,
                    tenant.getId(),
                    user.getId(),
                    request.tenantCode(),
                    request.username(),
                    "BAD_PASSWORD"
            );

            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "用户名或密码错误"
            );
        }

        // 成功也记：只有失败记录的日志，回答不了"这个账号最近正常登录过吗"
        auditLogService.recordLoginSuccess(
                ActorType.MEMBER,
                tenant.getId(),
                user.getId(),
                request.tenantCode(),
                request.username()
        );

        return user;
    }

}
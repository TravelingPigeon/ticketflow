package com.example.ticketflow.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.UnauthenticatedException;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserStatus;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorService {

    private final CurrentTenantService currentTenantService;
    private final TenantMapper tenantMapper;
    private final UserAccountMapper userAccountMapper;

    public CurrentActorService(
            CurrentTenantService currentTenantService,
            TenantMapper tenantMapper,
            UserAccountMapper userAccountMapper
    ) {
        this.currentTenantService = currentTenantService;
        this.tenantMapper = tenantMapper;
        this.userAccountMapper = userAccountMapper;
    }

    public CurrentActor requireActor(
            HttpSession session,
            Authentication authentication
    ) {
        Long tenantId =
                currentTenantService.requireTenantId(session);

        Tenant tenant = tenantMapper.selectById(tenantId);

        if (tenant == null) {
            throw new BusinessException(
                    "TENANT_NOT_FOUND",
                    "租户不存在"
            );
        }

        if (authentication == null) {
            throw new UnauthenticatedException("请先登录");
        }

        UserAccount user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getTenantId, tenantId)
                        .eq(
                                UserAccount::getUsername,
                                authentication.getName()
                        )
                        .eq(UserAccount::getStatus, UserStatus.ACTIVE)
        );

        if (user == null) {
            throw new UnauthenticatedException("当前登录用户不存在");
        }

        return new CurrentActor(
                tenantId,
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
    }
}

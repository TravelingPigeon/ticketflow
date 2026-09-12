package com.example.ticketflow.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.UnauthenticatedException;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserStatus;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorService {

    private final TenantMapper tenantMapper;
    private final UserAccountMapper userAccountMapper;

    public CurrentActorService(
            TenantMapper tenantMapper,
            UserAccountMapper userAccountMapper
    ) {
        this.tenantMapper = tenantMapper;
        this.userAccountMapper = userAccountMapper;
    }

    public CurrentActor requireActor() {
        Jwt jwt = extractJwt(
                SecurityContextHolder.getContext().getAuthentication()
        );

        Long tenantId = requireLongClaim(jwt, "tenantId");
        Long actorId = requireLongClaim(jwt, "actorId");

        Tenant tenant = tenantMapper.selectById(tenantId);

        if (tenant == null) {
            throw new BusinessException(
                    "TENANT_NOT_FOUND",
                    "租户不存在"
            );
        }

        UserAccount user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getId, actorId)
                        .eq(UserAccount::getTenantId, tenantId)
                        .eq(UserAccount::getStatus, UserStatus.ACTIVE)
        );

        if (user == null) {
            throw new UnauthenticatedException("当前登录用户不存在或已停用");
        }

        return new CurrentActor(
                tenantId,
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }

        throw new UnauthenticatedException("请先登录");
    }

    private Long requireLongClaim(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);

        if (value instanceof Number number) {
            return number.longValue();
        }

        throw new UnauthenticatedException("令牌缺少 " + name);
    }
}
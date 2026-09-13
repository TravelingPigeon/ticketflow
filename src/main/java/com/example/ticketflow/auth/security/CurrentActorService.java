package com.example.ticketflow.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.UnauthenticatedException;
import com.example.ticketflow.customer.domain.Customer;
import com.example.ticketflow.customer.domain.enums.CustomerStatus;
import com.example.ticketflow.customer.mapper.CustomerMapper;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserStatus;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorService {

    private final TenantMapper tenantMapper;
    private final UserAccountMapper userAccountMapper;
    private final CustomerMapper customerMapper;

    public CurrentActorService(
            TenantMapper tenantMapper,
            UserAccountMapper userAccountMapper,
            CustomerMapper customerMapper
    ) {
        this.tenantMapper = tenantMapper;
        this.userAccountMapper = userAccountMapper;
        this.customerMapper = customerMapper;
    }

    public CurrentActor requireMember() {
        Jwt jwt = requireToken();

        if (extractActorType(jwt) != ActorType.MEMBER) {
            throw new AccessDeniedException("该接口仅限企业成员访问");
        }

        Long tenantId = requireLongClaim(jwt, "tenantId");
        Long actorId = requireLongClaim(jwt, "actorId");

        requireTenant(tenantId);

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
                ActorType.MEMBER,
                user.getId(),
                user.getUsername(),
                user.getRole()
        );
    }

    public CurrentActor requireCustomer() {
        Jwt jwt = requireToken();

        if (extractActorType(jwt) != ActorType.CUSTOMER) {
            throw new AccessDeniedException("该接口仅限客户访问");
        }

        Long tenantId = requireLongClaim(jwt, "tenantId");
        Long actorId = requireLongClaim(jwt, "actorId");

        requireTenant(tenantId);

        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getId, actorId)
                        .eq(Customer::getTenantId, tenantId)
                        .eq(Customer::getStatus, CustomerStatus.ACTIVE)
        );

        if (customer == null) {
            throw new UnauthenticatedException("当前客户不存在或已停用");
        }

        return new CurrentActor(
                tenantId,
                ActorType.CUSTOMER,
                customer.getId(),
                customer.getEmail(),
                null
        );
    }

    private Jwt requireToken() {
        return extractJwt(
                SecurityContextHolder.getContext().getAuthentication()
        );
    }

    private void requireTenant(Long tenantId) {
        Tenant tenant = tenantMapper.selectById(tenantId);

        if (tenant == null) {
            throw new BusinessException(
                    "TENANT_NOT_FOUND",
                    "租户不存在"
            );
        }
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }

        throw new UnauthenticatedException("请先登录");
    }

    private ActorType extractActorType(Jwt jwt) {
        String value = jwt.getClaimAsString("actorType");

        if (value == null) {
            throw new UnauthenticatedException("令牌缺少 actorType");
        }

        try {
            return ActorType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new UnauthenticatedException("令牌中的 actorType 不合法");
        }
    }

    private Long requireLongClaim(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);

        if (value instanceof Number number) {
            return number.longValue();
        }

        throw new UnauthenticatedException("令牌缺少 " + name);
    }
}

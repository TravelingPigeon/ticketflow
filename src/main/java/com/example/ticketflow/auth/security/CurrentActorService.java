package com.example.ticketflow.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

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
                permissionsOf(
                        SecurityContextHolder.getContext().getAuthentication()
                )
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
                Set.of()
        );
    }

    /**
     * 从当前认证信息里取出权限编码。
     *
     * <p>这些权限是安全过滤器链解析令牌时从数据库查出来的（见 SecurityConfig），
     * 这里直接复用，好处有两个：同一个请求里不用查两次库；
     * "注解判断的权限"和"服务层判断的权限"永远一致。</p>
     */
    private Set<String> permissionsOf(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
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
                    ErrorCode.TENANT_NOT_FOUND,
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

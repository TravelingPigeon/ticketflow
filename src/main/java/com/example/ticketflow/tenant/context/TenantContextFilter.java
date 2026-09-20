package com.example.ticketflow.tenant.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把令牌里的 {@code tenantId} 放进 {@link TenantContext}，请求结束再清掉。
 *
 * <p>注册成普通的 {@code @Component} 过滤器就够了：Spring Security 的过滤器链
 * 排在很靠前的位置（order = -100），这个过滤器默认排在最后，
 * 所以它执行时 SecurityContext 已经填好了，能直接读令牌。</p>
 *
 * <p>登录、注册这类匿名请求拿不到令牌，这里返回 {@code null}——
 * 于是租户拦截器对它们不插手（那些查询本来也只按租户编码查，不依赖上下文）。</p>
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            TenantContext.set(tenantIdOfCurrentRequest());

            filterChain.doFilter(request, response);
        } finally {
            // 必须清：线程会被复用，不清就是下一个请求读到上一个租户的 ID
            TenantContext.clear();
        }
    }

    private Long tenantIdOfCurrentRequest() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken token) {
            Object claim = token.getToken().getClaim("tenantId");

            if (claim instanceof Number number) {
                return number.longValue();
            }
        }

        return null;
    }
}
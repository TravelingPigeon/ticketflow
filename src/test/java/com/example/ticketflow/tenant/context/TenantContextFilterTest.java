package com.example.ticketflow.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 租户上下文过滤器：设置、清理、异常时也清理。
 *
 * <p>这是个纯单元测试——直接调 filter，不经 Spring 容器。
 * 这样做是因为这里要验证的恰恰是"<b>进入链路前设好、离开链路后清掉</b>"这两个时刻，
 * 而不是业务流程。</p>
 */
class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPublishTenantIdFromTokenToTheChain() throws Exception {
        authenticateWithTenant(42L);

        AtomicReference<Long> seenInsideChain = new AtomicReference<>();

        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) ->
                        seenInsideChain.set(TenantContext.get())
        );

        // 链路里面拿得到
        assertEquals(42L, seenInsideChain.get());
    }

    @Test
    void shouldClearTenantContextAfterTheRequest() throws Exception {
        authenticateWithTenant(42L);

        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) -> {
                }
        );

        // 线程会被复用：不清掉的话，下一个请求会读到上一个租户的 ID
        assertNull(TenantContext.get());
    }

    @Test
    void shouldClearTenantContextEvenWhenTheChainThrows() {
        authenticateWithTenant(42L);

        assertThrows(
                IllegalStateException.class,
                () -> filter.doFilter(
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse(),
                        (request, response) -> {
                            throw new IllegalStateException("业务炸了");
                        }
                )
        );

        // 异常路径同样必须清——否则"出错的那次请求"会把租户泄漏给下一个请求
        assertNull(TenantContext.get());
    }

    @Test
    void shouldLeaveContextEmptyForAnonymousRequest() throws Exception {
        // 登录、注册这类请求没有令牌
        SecurityContextHolder.clearContext();

        AtomicReference<Long> seenInsideChain = new AtomicReference<>(0L);

        filter.doFilter(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                (request, response) ->
                        seenInsideChain.set(TenantContext.get())
        );

        assertNull(seenInsideChain.get());
    }

    private void authenticateWithTenant(long tenantId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("someone")
                .claim("tenantId", tenantId)
                .claim("actorId", 1L)
                .claim("actorType", "MEMBER")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(), "someone")
        );
    }
}

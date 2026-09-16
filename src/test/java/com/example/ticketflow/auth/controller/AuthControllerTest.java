package com.example.ticketflow.auth.controller;

import com.jayway.jsonpath.JsonPath;
import com.example.ticketflow.auth.security.TokenBlacklist;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.role.service.MemberRoleService;
import com.example.ticketflow.role.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private TokenBlacklist tokenBlacklist;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MemberRoleService memberRoleService;

    private static final String RAW_PASSWORD = "Password123";

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tf_ticket_operation");
        jdbcTemplate.update("DELETE FROM tf_ticket_comment");
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_customer");
        jdbcTemplate.update("DELETE FROM tf_member_role");
        jdbcTemplate.update("DELETE FROM tf_role_permission");
        jdbcTemplate.update("DELETE FROM tf_role");
        jdbcTemplate.update("DELETE FROM tf_user");
        jdbcTemplate.update("DELETE FROM tf_tenant");

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (1, 'auth-tenant', 'Auth Tenant', 'ACTIVE')
                """);

        jdbcTemplate.update(
                """
                        INSERT INTO tf_user
                            (id, tenant_id, username, password_hash, display_name, role, status)
                        VALUES (?, 1, ?, ?, ?, 'ADMIN', 'ACTIVE')
                        """,
                1L,
                "alice",
                passwordEncoder.encode(RAW_PASSWORD),
                "Alice"
        );

        // 登录响应里的 roles 来自 tf_member_role，所以测试租户要有内置角色
        roleService.createBuiltInRoles(1L);
        memberRoleService.replaceMemberRoles(
                1L,
                1L,
                List.of(BuiltInRoles.ADMIN)
        );
    }

    private String loginAndExtractToken() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "tenantCode": "auth-tenant",
                                          "username": "alice",
                                          "password": "Password123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.accessToken"
        );
    }

    @Test
    void shouldLogoutSuccessfully() throws Exception {
        // 无状态认证下服务端不再保存会话，登出即让客户端丢弃令牌；
        // 令牌的主动撤销由后续的 Redis 黑名单实现。
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnAccessTokenOnLogin() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "tenantCode": "auth-tenant",
                                          "username": "alice",
                                          "password": "Password123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(7200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.profile.id").value(1))
                .andExpect(jsonPath("$.data.profile.username").value("alice"))
                .andExpect(jsonPath("$.data.profile.roles[0]").value("ADMIN"));
    }

    @Test
    void shouldIssueTokenWithExpectedClaims() throws Exception {
        String token = loginAndExtractToken();

        Jwt jwt = jwtDecoder.decode(token);

        assertEquals("alice", jwt.getSubject());
        assertEquals(
                1L,
                ((Number) jwt.getClaim("tenantId")).longValue()
        );
        assertEquals(
                1L,
                ((Number) jwt.getClaim("actorId")).longValue()
        );
        assertEquals("MEMBER", jwt.getClaimAsString("actorType"));
        // 令牌只携带身份，不携带角色/权限——权限在每次请求时按 actorId 查库
        assertNull(jwt.getClaim("roles"));
        assertNotNull(jwt.getId());
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
    }

    @Test
    void shouldIssueTokenWithUniqueIdOnEachLogin() throws Exception {
        Jwt first = jwtDecoder.decode(loginAndExtractToken());
        Jwt second = jwtDecoder.decode(loginAndExtractToken());

        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void shouldBlacklistTokenOnLogout() throws Exception {
        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("alice")
                .jti("logout-jti-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(
                        jwt,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        ),
                        "alice"
                );

        assertFalse(tokenBlacklist.isBlacklisted("logout-jti-1"));

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertTrue(tokenBlacklist.isBlacklisted("logout-jti-1"));
    }
}

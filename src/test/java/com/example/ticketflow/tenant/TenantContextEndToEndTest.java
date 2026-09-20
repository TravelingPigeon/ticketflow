package com.example.ticketflow.tenant;

import com.example.ticketflow.auth.security.TokenService;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.role.service.RoleService;
import com.example.ticketflow.tenant.context.TenantContext;
import com.example.ticketflow.user.domain.UserAccount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户上下文在<b>真实过滤器链</b>里是否被正确填充。
 *
 * <p>为什么需要这个测试：`TenantContextFilter` 是普通 {@code @Component} 过滤器，
 * 它能不能拿到令牌，取决于它在过滤器链里排得比 Spring Security 靠后。
 * 如果顺序反了，上下文永远是 null，拦截器就永远不会生效——
 * 而<b>业务接口一个都不会报错</b>（因为显式条件仍然在挡着）。
 * 这种"静默失效"只能靠直接观察上下文来发现。</p>
 *
 * <p>所以这里挂了一个只存在于测试里的回显端点。它映射在 {@code /test-fixture} 下而不是
 * {@code /api/v1} 下：{@code PermissionAnnotationTest} 会扫描 {@code /api/v1} 下的接口
 * 并要求它们声明权限注解，而这个端点不需要权限。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TenantContextEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long tenantId;

    private long memberId;

    private String token;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        jdbcTemplate.update("DELETE FROM tf_notification");
        jdbcTemplate.update("DELETE FROM tf_ticket_operation");
        jdbcTemplate.update("DELETE FROM tf_ticket_comment");
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_customer");
        jdbcTemplate.update("DELETE FROM tf_member_role");
        jdbcTemplate.update("DELETE FROM tf_role_permission");
        jdbcTemplate.update("DELETE FROM tf_role");
        jdbcTemplate.update("DELETE FROM tf_sla_policy");
        jdbcTemplate.update("DELETE FROM tf_user");
        jdbcTemplate.update("DELETE FROM tf_tenant");

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (code, name, status)
                VALUES ('tenant-context-e2e', 'Tenant Context E2E', 'ACTIVE')
                """);

        tenantId = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_tenant WHERE code = 'tenant-context-e2e'",
                Long.class
        );

        roleService.createBuiltInRoles(tenantId);

        jdbcTemplate.update(
                """
                        INSERT INTO tf_user
                            (tenant_id, username, password_hash, display_name, status)
                        VALUES (?, 'ctx-admin', 'test-hash', 'Ctx Admin', 'ACTIVE')
                        """,
                tenantId
        );

        memberId = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM tf_user
                        WHERE tenant_id = ?
                          AND username = 'ctx-admin'
                        """,
                Long.class,
                tenantId
        );

        UserAccount user = new UserAccount();
        user.setId(memberId);
        user.setTenantId(tenantId);
        user.setUsername("ctx-admin");

        token = tokenService.issueMemberToken(user).accessToken();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldPopulateTenantContextInTheRealFilterChain() throws Exception {
        mockMvc.perform(
                        get("/test-fixture/tenant-context")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(tenantId));

        // MockMvc 就在当前线程上跑请求，所以请求结束后可以直接观察：
        // 上下文必须已经被 finally 清掉（否则下一个请求会串号）
        assertNull(TenantContext.get());
    }

    @TestConfiguration
    static class TenantContextFixtureConfig {

        @RestController
        static class TenantContextFixtureController {

            @GetMapping("/test-fixture/tenant-context")
            Map<String, Object> tenantContext() {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("tenantId", TenantContext.get());

                return body;
            }
        }
    }
}

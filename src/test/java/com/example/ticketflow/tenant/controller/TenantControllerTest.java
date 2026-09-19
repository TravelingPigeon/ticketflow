package com.example.ticketflow.tenant.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 租户自助注册接口。
 *
 * <p>这个接口**不需要认证**（否则第一个管理员永远建不出来），所以这里没有令牌相关的用例。
 * "匿名真的能调通、注册出来的管理员真的能用"由 {@link TenantRegistrationEndToEndTest}
 * 走完整安全过滤器链覆盖。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class TenantControllerTest {

    private static final String TENANT_CODE = "signup-tenant";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {

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
    }

    @Test
    void shouldRegisterTenantWithBuiltInRolesAndFirstAdmin() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tenants/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody(TENANT_CODE))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenant.code").value(TENANT_CODE))
                .andExpect(jsonPath("$.data.tenant.name").value("注册测试公司"))
                .andExpect(jsonPath("$.data.tenant.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.admin.username").value("owner"))
                .andExpect(jsonPath("$.data.admin.displayName").value("Owner"))
                .andExpect(jsonPath("$.data.admin.roles[0]").value("ADMIN"));

        long tenantId = tenantIdOf(TENANT_CODE);

        // 四件事必须一起落库，缺任何一件这个租户都是残废的：
        // 没有角色 → 里面的人拿不到权限；没有管理员 → 谁也进不去
        assertEquals(
                1,
                countRows("SELECT COUNT(*) FROM tf_tenant WHERE id = ?", tenantId)
        );
        assertEquals(
                2,
                countRows("SELECT COUNT(*) FROM tf_role WHERE tenant_id = ?", tenantId)
        );
        assertEquals(
                17,
                countRows(
                        "SELECT COUNT(*) FROM tf_role_permission WHERE tenant_id = ?",
                        tenantId
                )
        );
        assertEquals(
                1,
                countRows("SELECT COUNT(*) FROM tf_user WHERE tenant_id = ?", tenantId)
        );
        assertEquals(
                1,
                countRows(
                        "SELECT COUNT(*) FROM tf_member_role WHERE tenant_id = ?",
                        tenantId
                )
        );
        // SLA 默认规则也是注册的一部分：少一条，那个优先级的工单就算不出截止时间
        assertEquals(
                4,
                countRows(
                        "SELECT COUNT(*) FROM tf_sla_policy WHERE tenant_id = ?",
                        tenantId
                )
        );
    }

    @Test
    void shouldRejectDuplicateTenantCodeWithoutSideEffects() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tenants/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody(TENANT_CODE))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/tenants/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody(TENANT_CODE))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_CODE_EXISTS"));

        // 失败之后不能多出任何东西：多一个租户、多一个用户都是脏数据
        assertEquals(1, countRows("SELECT COUNT(*) FROM tf_tenant"));
        assertEquals(1, countRows("SELECT COUNT(*) FROM tf_user"));
        assertEquals(2, countRows("SELECT COUNT(*) FROM tf_role"));
    }

    @Test
    void shouldRejectTenantCodeWithInvalidFormat() throws Exception {
        // 大写字母加空格：这个编码会出现在登录请求里，必须是稳定的 slug 形式
        mockMvc.perform(
                        post("/api/v1/tenants/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody("Demo Team"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertEquals(0, countRows("SELECT COUNT(*) FROM tf_tenant"));
    }

    @Test
    void shouldRejectTooShortAdminPassword() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tenants/register")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "tenantCode": "short-pwd",
                                          "tenantName": "密码太短的租户",
                                          "adminUsername": "owner",
                                          "adminPassword": "short",
                                          "adminDisplayName": "Owner"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 参数校验发生在进服务层之前，所以一行数据都不该写进去
        assertEquals(0, countRows("SELECT COUNT(*) FROM tf_tenant"));
        assertEquals(0, countRows("SELECT COUNT(*) FROM tf_user"));
    }

    private String registerBody(String tenantCode) {
        return """
                {
                  "tenantCode": "%s",
                  "tenantName": "注册测试公司",
                  "adminUsername": "owner",
                  "adminPassword": "Password123",
                  "adminDisplayName": "Owner"
                }
                """.formatted(tenantCode);
    }

    private long tenantIdOf(String code) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_tenant WHERE code = ?",
                Long.class,
                code
        );

        return id == null ? -1L : id;
    }

    private int countRows(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                args
        );

        return count == null ? 0 : count;
    }
}

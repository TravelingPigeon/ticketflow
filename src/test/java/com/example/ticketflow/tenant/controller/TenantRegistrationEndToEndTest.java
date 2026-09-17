package com.example.ticketflow.tenant.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 从空库到"可用的租户"的完整链路。
 *
 * <p>其它 MockMvc 测试都写着 {@code addFilters = false}——那样即使接口是公开的，
 * 也验证不了"匿名究竟能不能调通"，更验证不了注册出来的管理员能不能干活。
 * 这个类不加那个开关，走真实安全过滤器链：<b>注册 → 登录 → 调受保护接口</b>，
 * 一次性验证"第一个租户 + 第一个管理员"这条路是真的通的。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class TenantRegistrationEndToEndTest {

    private static final String TENANT_CODE = "e2e-tenant";
    private static final String PASSWORD = "Password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
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
    void shouldGoFromEmptyDatabaseToUsableTenant() throws Exception {
        // 1. 匿名注册：这一步以前只能靠手写六段 SQL
        mockMvc.perform(
                        post("/api/v1/tenants/register")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "tenantCode": "%s",
                                          "tenantName": "新注册公司",
                                          "adminUsername": "owner",
                                          "adminPassword": "%s",
                                          "adminDisplayName": "Owner"
                                        }
                                        """.formatted(TENANT_CODE, PASSWORD))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.admin.roles[0]").value("ADMIN"));

        // 2. 用注册出来的管理员登录：真实密码校验 + 真实令牌签发
        String ownerToken = login("owner");

        // 3. 他立刻就能调受保护接口——这正是"注册完能不能用"的关键
        mockMvc.perform(
                        get("/api/v1/tickets")
                                .header("Authorization", "Bearer " + ownerToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 4. 他还能建客服（需要 user:create，管理员角色里有）
        mockMvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "agent",
                                          "password": "%s",
                                          "displayName": "客服一号",
                                          "roleCodes": ["AGENT"]
                                        }
                                        """.formatted(PASSWORD))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("AGENT"));

        // 5. 新建的客服也能登录并干活（验证建号时确实挂上了角色）
        String agentToken = login("agent");

        mockMvc.perform(
                        get("/api/v1/tickets")
                                .header("Authorization", "Bearer " + agentToken)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "ticketNo": "REG-001",
                                          "title": "注册之后的第一张单"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());
    }

    @Test
    void shouldRejectSecondRegistrationWithSameTenantCode() throws Exception {
        registerTenant();

        mockMvc.perform(
                        post("/api/v1/tenants/register")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "tenantCode": "%s",
                                          "tenantName": "想抢注的公司",
                                          "adminUsername": "someone-else",
                                          "adminPassword": "%s",
                                          "adminDisplayName": "Someone Else"
                                        }
                                        """.formatted(TENANT_CODE, PASSWORD))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_CODE_EXISTS"));
    }

    private void registerTenant() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tenants/register")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "tenantCode": "%s",
                                          "tenantName": "新注册公司",
                                          "adminUsername": "owner",
                                          "adminPassword": "%s",
                                          "adminDisplayName": "Owner"
                                        }
                                        """.formatted(TENANT_CODE, PASSWORD))
                )
                .andExpect(status().isCreated());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "tenantCode": "%s",
                                          "username": "%s",
                                          "password": "%s"
                                        }
                                        """.formatted(TENANT_CODE, username, PASSWORD))
                )
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.accessToken"
        );
    }
}

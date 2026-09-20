package com.example.ticketflow.role.controller;

import com.example.ticketflow.role.service.RoleService;
import com.example.ticketflow.support.TestAuthorities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleService roleService;

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

        jdbcTemplate.update(
                """
                        INSERT INTO tf_tenant (id, code, name, status)
                        VALUES (1, 'role-tenant-1', 'Role Tenant 1', 'ACTIVE')
                        """
        );

        jdbcTemplate.update(
                """
                        INSERT INTO tf_tenant (id, code, name, status)
                        VALUES (2, 'role-tenant-2', 'Role Tenant 2', 'ACTIVE')
                        """
        );

        jdbcTemplate.update(
                """
                        INSERT INTO tf_user
                            (id, tenant_id, username, password_hash, display_name, status)
                        VALUES
                            (1, 1, 'admin-one', 'test-hash', 'Admin One', 'ACTIVE'),
                            (2, 1, 'agent-one', 'test-hash', 'Agent One', 'ACTIVE'),
                            (4, 2, 'admin-two', 'test-hash', 'Admin Two', 'ACTIVE')
                        """
        );

        roleService.createBuiltInRoles(1L);
        roleService.createBuiltInRoles(2L);
    }

    @Test
    void shouldListRolesWithPermissions() throws Exception {
        mockMvc.perform(
                        get("/api/v1/roles")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].builtIn").value(true))
                // 管理员 = 字典总数 - ticket:claim；字典在 V17 之后是 13 条
                .andExpect(jsonPath("$.data[0].permissions.length()").value(12))
                .andExpect(jsonPath("$.data[1].code").value("AGENT"))
                .andExpect(jsonPath("$.data[1].permissions.length()").value(6));
    }

    @Test
    void shouldRejectListingRolesWithoutRoleManagePermission() throws Exception {
        // 客服没有任何角色管理权限，连查看角色列表都不行
        mockMvc.perform(
                        get("/api/v1/roles")
                                .with(memberToken("agent-one", 1L, "AGENT"))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldCreateCustomRole() throws Exception {
        mockMvc.perform(
                        post("/api/v1/roles")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "code": "READONLY",
                                          "name": "只读观察者",
                                          "permissions": ["ticket:read"]
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("READONLY"))
                .andExpect(jsonPath("$.data.builtIn").value(false))
                .andExpect(jsonPath("$.data.permissions[0]").value("ticket:read"));
    }

    @Test
    void shouldRejectRoleCodeWithInvalidFormat() throws Exception {
        mockMvc.perform(
                        post("/api/v1/roles")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "code": "admin role",
                                          "name": "带空格的角色编码"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectUnknownPermissionCode() throws Exception {
        mockMvc.perform(
                        post("/api/v1/roles")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "code": "BROKEN",
                                          "name": "编码写错了",
                                          "permissions": ["ticket:assgin"]
                                        }
                                        """)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_PERMISSION"));
    }

    @Test
    void shouldRejectNullPermissionsButAcceptEmptyArray() throws Exception {
        // null 表示"没说要干什么"，直接拒绝
        mockMvc.perform(
                        put("/api/v1/roles/" + roleIdOf(1L, "AGENT") + "/permissions")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "permissions": null
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 空数组表示"把权限清空"，是明确的意图
        mockMvc.perform(
                        put("/api/v1/roles/" + roleIdOf(1L, "AGENT") + "/permissions")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "permissions": []
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(0));
    }

    @Test
    void shouldNotChangeRoleOfAnotherTenant() throws Exception {
        long roleIdOfTenantOne = roleIdOf(1L, "AGENT");

        mockMvc.perform(
                        put("/api/v1/roles/" + roleIdOfTenantOne + "/permissions")
                                .with(memberToken("admin-two", 2L, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "permissions": []
                                        }
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));

        // 租户 2 的管理员改不动租户 1 的角色
        mockMvc.perform(
                        get("/api/v1/roles")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].permissions.length()").value(6));
    }

    @Test
    void shouldListPermissionDictionary() throws Exception {
        mockMvc.perform(
                        get("/api/v1/permissions")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                )
                .andExpect(status().isOk())
                // 13 = V11 的 11 条 + V13 的 sla:manage + V17 的 audit:read
                .andExpect(jsonPath("$.data.length()").value(13))
                .andExpect(jsonPath("$.data[?(@.code == 'ticket:handle:any')]")
                        .exists());
    }

    private long roleIdOf(long tenantId, String code) {
        Long id = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM tf_role
                        WHERE tenant_id = ?
                          AND code = ?
                        """,
                Long.class,
                tenantId,
                code
        );

        return id == null ? -1L : id;
    }

    private RequestPostProcessor memberToken(
            String username,
            long tenantId,
            String role
    ) {
        List<Long> ids = jdbcTemplate.queryForList(
                """
                        SELECT id
                        FROM tf_user
                        WHERE tenant_id = ?
                          AND username = ?
                        """,
                Long.class,
                tenantId,
                username
        );

        long actorId = ids.isEmpty() ? 0L : ids.get(0);

        return request -> {
            Jwt jwt = Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject(username)
                    .claim("tenantId", tenantId)
                    .claim("actorId", actorId)
                    .claim("actorType", "MEMBER")
                    .claim("roles", List.of(role))
                    .build();

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(
                            jwt,
                            TestAuthorities.authorities(jdbcTemplate, role),
                            username
                    );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);

            TestSecurityContextHolder.setContext(context);

            return request;
        };
    }
}

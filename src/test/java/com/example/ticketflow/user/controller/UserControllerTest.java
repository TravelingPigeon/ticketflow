package com.example.ticketflow.user.controller;

import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.role.service.PermissionService;
import com.example.ticketflow.role.service.RoleService;
import com.example.ticketflow.support.InMemoryPermissionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.ticketflow.support.TestAuthorities;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private InMemoryPermissionCache permissionCache;

    @BeforeEach
    void setUp() {
        permissionCache.clear();

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
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (1, 'user-tenant-1', 'User Tenant 1', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (2, 'user-tenant-2', 'User Tenant 2', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_user
                    (id, tenant_id, username, password_hash, display_name, status)
                VALUES
                    (1, 1, 'admin-one', 'test-hash', 'Admin One', 'ACTIVE'),
                    (2, 1, 'agent-one', 'test-hash', 'Agent One', 'ACTIVE'),
                    (4, 2, 'admin-two', 'test-hash', 'Admin Two', 'ACTIVE'),
                    (5, 2, 'agent-one', 'test-hash', 'Agent One Of Tenant Two', 'ACTIVE')
                """);

        // 新建成员时服务会去挂内置角色，所以测试租户必须像真实租户一样先有角色
        roleService.createBuiltInRoles(1L);
        roleService.createBuiltInRoles(2L);
    }

    @Test
    void shouldCreateUserInCurrentTenant() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "agent-new",
                                          "password": "password123",
                                          "displayName": "  New Agent  ",
                                          "roleCodes": ["AGENT"]
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").value(1))
                .andExpect(jsonPath("$.data.username").value("agent-new"))
                .andExpect(jsonPath("$.data.displayName").value("New Agent"))
                .andExpect(jsonPath("$.data.roles[0]").value("AGENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void shouldIgnoreTenantIdSentInRequestBody() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "tenantId": 2,
                                          "username": "sneaky-admin",
                                          "password": "password123",
                                          "displayName": "Sneaky Admin",
                                          "roleCodes": ["ADMIN"]
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tenantId").value(1));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT tenant_id FROM tf_user WHERE username = ?",
                "sneaky-admin"
        );

        assertEquals(
                1L,
                ((Number) row.get("tenant_id")).longValue()
        );
    }

    @Test
    void shouldRejectAgentCreatingUser() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("agent-one", 1, "AGENT"))
                                .contentType(APPLICATION_JSON)
                                .content(createBody("agent-created"))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertEquals(0, countUser("agent-created"));
    }

    @Test
    void shouldRejectAnonymousCreatingUser() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .contentType(APPLICATION_JSON)
                                .content(createBody("anonymous-created"))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertEquals(0, countUser("anonymous-created"));
    }

    @Test
    void shouldRejectCreatingUserWithoutLoginSession() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .contentType(APPLICATION_JSON)
                                .content(createBody("no-session-created"))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        assertEquals(0, countUser("no-session-created"));
    }

    @Test
    void shouldRejectUsernameExistingInSameTenant() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content(createBody("agent-one"))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    @Test
    void shouldAllowUsernameUsedByAnotherTenant() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content(createBody("admin-two"))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tenantId").value(1))
                .andExpect(jsonPath("$.data.username").value("admin-two"));
    }

    @Test
    void shouldDefaultRoleToAgent() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "plain-user",
                                          "password": "password123",
                                          "displayName": "Plain User"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("AGENT"));
    }

    @Test
    void shouldRejectShortPassword() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "short-password-user",
                                          "password": "short",
                                          "displayName": "Short Password",
                                          "role": "AGENT"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String createBody(String username) {
        return """
                {
                  "username": "%s",
                  "password": "password123",
                  "displayName": "Created User",
                  "roleCodes": ["AGENT"]
                }
                """.formatted(username);
    }

    @Test
    void shouldCreateUserWithMultipleRoleCodes() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "dual-role",
                                          "password": "password123",
                                          "displayName": "Dual Role",
                                          "roleCodes": ["ADMIN", "AGENT"]
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles.length()").value(2));

        // 两个角色的权限取并集：管理员有 role:manage、客服有 ticket:claim
        Set<String> permissions = permissionService.permissionsOf(
                1L,
                memberIdOf(1L, "dual-role")
        );

        assertTrue(permissions.contains("role:manage"));
        assertTrue(permissions.contains("ticket:claim"));
    }

    @Test
    void shouldRejectUnknownRoleCodeWhenCreatingUser() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "bad-role",
                                          "password": "password123",
                                          "displayName": "Bad Role",
                                          "roleCodes": ["NOT_A_ROLE"]
                                        }
                                        """)
                )
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE_CODE"));

        // 角色不存在要整体失败，不能留下一个没有角色的半成品账号
        assertEquals(0, countUser("bad-role"));
    }

    @Test
    void shouldGrantAgentPermissionsToCreatedUser() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content(createBody("fresh-agent"))
                )
                .andExpect(status().isCreated());

        // 新建出来的成员必须立刻拿到客服权限。
        // 不写 tf_member_role 的话，他会是一个"能登录、却什么都做不了"的账号。
        assertEquals(
                new TreeSet<>(BuiltInRoles.AGENT_PERMISSIONS),
                new TreeSet<>(
                        permissionService.permissionsOf(
                                1L,
                                memberIdOf(1L, "fresh-agent")
                        )
                )
        );
    }

    @Test
    void shouldGrantAdminPermissionsToCreatedUser() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "fresh-admin",
                                          "password": "password123",
                                          "displayName": "Fresh Admin",
                                          "roleCodes": ["ADMIN"]
                                        }
                                        """)
                )
                .andExpect(status().isCreated());

        Set<String> permissions = permissionService.permissionsOf(
                1L,
                memberIdOf(1L, "fresh-admin")
        );

        assertTrue(permissions.contains("user:create"));
        assertTrue(permissions.contains("role:manage"));
        assertFalse(permissions.contains("ticket:claim"));
    }

    @Test
    void shouldReplaceMemberRoles() throws Exception {
        long targetId = memberIdOf(1L, "agent-one");

        mockMvc.perform(
                        put("/api/v1/users/" + targetId + "/roles")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "roleCodes": ["ADMIN", "AGENT"]
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 两个角色的权限取并集：客服有 ticket:claim，管理员有 role:manage
        Set<String> permissions = permissionService.permissionsOf(1L, targetId);

        assertTrue(permissions.contains("ticket:claim"));
        assertTrue(permissions.contains("role:manage"));
    }

    @Test
    void shouldRejectReplacingRolesWithoutRoleManagePermission() throws Exception {
        long targetId = memberIdOf(1L, "agent-one");

        // 客服能建账号（user:create 没有），但改别人权限需要 role:manage
        mockMvc.perform(
                        put("/api/v1/users/" + targetId + "/roles")
                                .with(jwtFor("agent-one", 1, "AGENT"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "roleCodes": ["ADMIN"]
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectReplacingRolesForUnknownMember() throws Exception {
        mockMvc.perform(
                        put("/api/v1/users/9999/roles")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "roleCodes": ["ADMIN"]
                                        }
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    private long memberIdOf(long tenantId, String username) {
        Long id = jdbcTemplate.queryForObject(
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

        return id == null ? -1L : id;
    }

    private int countUser(String username) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tf_user WHERE username = ?",
                Integer.class,
                username
        );

        return count == null ? 0 : count;
    }

    private RequestPostProcessor jwtFor(
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

        return jwtFor(username, tenantId, actorId, role);
    }

    private RequestPostProcessor jwtFor(
            String username,
            long tenantId,
            long actorId,
            String role
    ) {
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

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);

            TestSecurityContextHolder.setContext(context);

            return request;
        };
    }
}

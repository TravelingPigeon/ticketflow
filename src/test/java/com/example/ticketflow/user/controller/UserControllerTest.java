package com.example.ticketflow.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
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

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tf_ticket_comment");
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_customer");
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
                    (id, tenant_id, username, password_hash, display_name, role, status)
                VALUES
                    (1, 1, 'admin-one', 'test-hash', 'Admin One', 'ADMIN', 'ACTIVE'),
                    (2, 1, 'agent-one', 'test-hash', 'Agent One', 'AGENT', 'ACTIVE'),
                    (3, 1, 'requester-one', 'test-hash', 'Requester One', 'REQUESTER', 'ACTIVE'),
                    (4, 2, 'admin-two', 'test-hash', 'Admin Two', 'ADMIN', 'ACTIVE'),
                    (5, 2, 'agent-one', 'test-hash', 'Agent One Of Tenant Two', 'AGENT', 'ACTIVE')
                """);
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
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
                                          "role": "AGENT"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").value(1))
                .andExpect(jsonPath("$.data.username").value("agent-new"))
                .andExpect(jsonPath("$.data.displayName").value("New Agent"))
                .andExpect(jsonPath("$.data.role").value("AGENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
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
                                          "role": "ADMIN"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tenantId").value(1));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT tenant_id, role FROM tf_user WHERE username = ?",
                "sneaky-admin"
        );

        assertEquals(
                1L,
                ((Number) row.get("tenant_id")).longValue()
        );
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
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
    @WithMockUser(username = "requester-one", roles = "REQUESTER")
    void shouldRejectRequesterCreatingUser() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("requester-one", 1, "REQUESTER"))
                                .contentType(APPLICATION_JSON)
                                .content(createBody("requester-created"))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertEquals(0, countUser("requester-created"));
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
    @WithMockUser(username = "admin-one", roles = "ADMIN")
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
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldRejectUsernameExistingInSameTenant() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(jwtFor("admin-one", 1, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content(createBody("agent-one"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
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
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldDefaultRoleToRequester() throws Exception {
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
                .andExpect(jsonPath("$.data.role").value("REQUESTER"));
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
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
                  "role": "AGENT"
                }
                """.formatted(username);
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
                            List.of(
                                    new SimpleGrantedAuthority(
                                            "ROLE_" + role
                                    )
                            ),
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

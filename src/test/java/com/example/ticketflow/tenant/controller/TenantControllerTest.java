package com.example.ticketflow.tenant.controller;

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

import com.example.ticketflow.support.TestAuthorities;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class TenantControllerTest {

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
        jdbcTemplate.update("DELETE FROM tf_user");
        jdbcTemplate.update("DELETE FROM tf_tenant");
    }

    @Test
    void shouldCreateTenant() throws Exception {
        String requestBody = """
                {
                  "code": "auto-test",
                  "name": "自动化测试团队"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .with(memberToken("ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.code").value("auto-test"))
                .andExpect(jsonPath("$.data.name").value("自动化测试团队"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldRejectDuplicateTenantCode() throws Exception {
        String requestBody = """
                {
                  "code": "duplicate-test",
                  "name": "重复编码测试"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .with(memberToken("ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .with(memberToken("ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TENANT_CODE_EXISTS"));
    }

    @Test
    void shouldRejectBlankRequest() throws Exception {
        String requestBody = """
                {
                  "code": "",
                  "name": ""
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .with(memberToken("ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectCustomerCreatingTenant() throws Exception {
        String requestBody = """
                {
                  "code": "customer-created",
                  "name": "客户建的租户"
                }
                """;

        // 建租户接口过去没有权限注解，客户令牌也能调用；补上 tenant:create 之后必须被拦住
        mockMvc.perform(
                        post("/api/v1/tenants")
                                .with(customerToken())
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldRejectAnonymousCreatingTenant() throws Exception {
        String requestBody = """
                {
                  "code": "anonymous-created",
                  "name": "匿名建的租户"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tenants")
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private RequestPostProcessor memberToken(String role) {
        return request -> {
            Jwt jwt = Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject("admin-one")
                    .claim("tenantId", 1L)
                    .claim("actorId", 4L)
                    .claim("actorType", "MEMBER")
                    .claim("roles", List.of(role))
                    .build();

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(
                            jwt,
                            TestAuthorities.authorities(jdbcTemplate, role),
                            "admin-one"
                    );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);

            TestSecurityContextHolder.setContext(context);

            return request;
        };
    }

    private RequestPostProcessor customerToken() {
        return request -> {
            Jwt jwt = Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject("customer@example.com")
                    .claim("tenantId", 1L)
                    .claim("actorId", 1L)
                    .claim("actorType", "CUSTOMER")
                    .claim("roles", List.of())
                    .build();

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(jwt, List.of(), "customer@example.com");

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);

            TestSecurityContextHolder.setContext(context);

            return request;
        };
    }
}

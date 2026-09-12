package com.example.ticketflow.customer.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class PortalAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tf_ticket_comment");
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_customer");
        jdbcTemplate.update("DELETE FROM tf_user");
        jdbcTemplate.update("DELETE FROM tf_tenant");

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (1, 'portal-tenant', 'Portal Tenant', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (2, 'portal-tenant-2', 'Portal Tenant 2', 'ACTIVE')
                """);
    }

    @Test
    void shouldRegisterCustomer() throws Exception {
        mockMvc.perform(
                        post("/api/v1/portal/auth/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody(
                                        "portal-tenant",
                                        "Alice@Example.com",
                                        "  Alice  "
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").value(1))
                .andExpect(jsonPath("$.data.email")
                        .value("alice@example.com"))
                .andExpect(jsonPath("$.data.displayName").value("Alice"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void shouldRejectRegisterWithUnknownTenantCode() throws Exception {
        mockMvc.perform(
                        post("/api/v1/portal/auth/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody(
                                        "no-such-tenant",
                                        "alice@example.com",
                                        "Alice"
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void shouldRejectDuplicateEmailInSameTenant() throws Exception {
        registerCustomer("portal-tenant", "alice@example.com");

        mockMvc.perform(
                        post("/api/v1/portal/auth/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody(
                                        "portal-tenant",
                                        "alice@example.com",
                                        "Alice"
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("CUSTOMER_EMAIL_EXISTS"));
    }

    @Test
    void shouldTreatEmailCaseInsensitively() throws Exception {
        registerCustomer("portal-tenant", "alice@example.com");

        mockMvc.perform(
                        post("/api/v1/portal/auth/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody(
                                        "portal-tenant",
                                        "ALICE@example.com",
                                        "Alice"
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("CUSTOMER_EMAIL_EXISTS"));
    }

    @Test
    void shouldAllowSameEmailInAnotherTenant() throws Exception {
        registerCustomer("portal-tenant", "alice@example.com");

        mockMvc.perform(
                        post("/api/v1/portal/auth/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody(
                                        "portal-tenant-2",
                                        "alice@example.com",
                                        "Alice"
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tenantId").value(2))
                .andExpect(jsonPath("$.data.email")
                        .value("alice@example.com"));
    }

    @Test
    void shouldRejectInvalidEmailFormat() throws Exception {
        mockMvc.perform(
                        post("/api/v1/portal/auth/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody(
                                        "portal-tenant",
                                        "not-an-email",
                                        "Alice"
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));
    }

    @Test
    void shouldIssueCustomerTokenOnLogin() throws Exception {
        registerCustomer("portal-tenant", "alice@example.com");

        MvcResult result = mockMvc.perform(
                        post("/api/v1/portal/auth/login")
                                .contentType(APPLICATION_JSON)
                                .content(loginBody(
                                        "portal-tenant",
                                        "alice@example.com",
                                        "Password123"
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(7200))
                .andExpect(jsonPath("$.data.profile.email")
                        .value("alice@example.com"))
                .andReturn();

        String token = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.accessToken"
        );

        Jwt jwt = jwtDecoder.decode(token);

        assertEquals("alice@example.com", jwt.getSubject());
        assertEquals("CUSTOMER", jwt.getClaimAsString("actorType"));
        assertEquals(
                1L,
                ((Number) jwt.getClaim("tenantId")).longValue()
        );
        assertEquals(List.of(), jwt.getClaimAsStringList("roles"));
    }

    @Test
    void shouldRejectLoginWithWrongPassword() throws Exception {
        registerCustomer("portal-tenant", "alice@example.com");

        mockMvc.perform(
                        post("/api/v1/portal/auth/login")
                                .contentType(APPLICATION_JSON)
                                .content(loginBody(
                                        "portal-tenant",
                                        "alice@example.com",
                                        "WrongPassword"
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRejectLoginWithUnknownEmail() throws Exception {
        mockMvc.perform(
                        post("/api/v1/portal/auth/login")
                                .contentType(APPLICATION_JSON)
                                .content(loginBody(
                                        "portal-tenant",
                                        "nobody@example.com",
                                        "Password123"
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void shouldRejectCustomerTokenOnMemberApi() throws Exception {
        mockMvc.perform(
                        get("/api/v1/tickets")
                                .with(customerToken(1L, 1L))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private RequestPostProcessor customerToken(
            long tenantId,
            long actorId
    ) {
        return request -> {
            Jwt jwt = Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject("alice@example.com")
                    .claim("tenantId", tenantId)
                    .claim("actorId", actorId)
                    .claim("actorType", "CUSTOMER")
                    .claim("roles", List.of())
                    .build();

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(
                            jwt,
                            List.of(),
                            "alice@example.com"
                    );

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);

            TestSecurityContextHolder.setContext(context);

            return request;
        };
    }

    private void registerCustomer(
            String tenantCode,
            String email
    ) throws Exception {
        mockMvc.perform(
                        post("/api/v1/portal/auth/register")
                                .contentType(APPLICATION_JSON)
                                .content(registerBody(
                                        tenantCode,
                                        email,
                                        "Alice"
                                ))
                )
                .andExpect(status().isCreated());
    }

    private String registerBody(
            String tenantCode,
            String email,
            String displayName
    ) {
        return """
                {
                  "tenantCode": "%s",
                  "email": "%s",
                  "password": "Password123",
                  "displayName": "%s"
                }
                """.formatted(tenantCode, email, displayName);
    }

    private String loginBody(
            String tenantCode,
            String email,
            String password
    ) {
        return """
                {
                  "tenantCode": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(tenantCode, email, password);
    }
}

package com.example.ticketflow.sla.controller;

import com.example.ticketflow.support.TestAuthorities;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SLA 规则接口：权限、全量替换、以及"数组元素的校验到底有没有生效"。 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class SlaPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantService tenantService;

    private long tenantId;

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

        tenantService.createTenant(
                new CreateTenantRequest("sla-http", "SLA HTTP")
        );

        tenantId = tenantIdOf("sla-http");

        jdbcTemplate.update(
                """
                        INSERT INTO tf_user
                            (id, tenant_id, username, password_hash, display_name, status)
                        VALUES
                            (1, ?, 'admin-one', 'test-hash', 'Admin One', 'ACTIVE'),
                            (2, ?, 'agent-one', 'test-hash', 'Agent One', 'ACTIVE')
                        """,
                tenantId,
                tenantId
        );
    }

    @Test
    void shouldListPolicies() throws Exception {
        mockMvc.perform(
                        get("/api/v1/sla-policies")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    void shouldRejectListingWithoutSlaManagePermission() throws Exception {
        // 客服不能看也不能改 SLA 规则
        mockMvc.perform(
                        get("/api/v1/sla-policies")
                                .with(memberToken("agent-one", 2L, "AGENT"))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReplacePolicies() throws Exception {
        mockMvc.perform(
                        put("/api/v1/sla-policies")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        [
                                          {"priority":"LOW","firstResponseMinutes":600,
                                           "resolutionMinutes":3000,"remindBeforeMinutes":120},
                                          {"priority":"MEDIUM","firstResponseMinutes":300,
                                           "resolutionMinutes":1500,"remindBeforeMinutes":60},
                                          {"priority":"HIGH","firstResponseMinutes":30,
                                           "resolutionMinutes":240,"remindBeforeMinutes":15},
                                          {"priority":"URGENT","firstResponseMinutes":15,
                                           "resolutionMinutes":120,"remindBeforeMinutes":10}
                                        ]
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[?(@.priority == 'URGENT')].firstResponseMinutes")
                        .value(15));
    }

    @Test
    void shouldValidateEachElementOfTheRequestBodyArray() throws Exception {
        // 第一条缺 priority：这验证的是 List<@Valid ...> 那个容器元素约束真的生效
        mockMvc.perform(
                        put("/api/v1/sla-policies")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        [
                                          {"firstResponseMinutes":480,
                                           "resolutionMinutes":2880,"remindBeforeMinutes":60},
                                          {"priority":"MEDIUM","firstResponseMinutes":240,
                                           "resolutionMinutes":1440,"remindBeforeMinutes":60},
                                          {"priority":"HIGH","firstResponseMinutes":60,
                                           "resolutionMinutes":480,"remindBeforeMinutes":30},
                                          {"priority":"URGENT","firstResponseMinutes":30,
                                           "resolutionMinutes":240,"remindBeforeMinutes":15}
                                        ]
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectIncompleteRuleSet() throws Exception {
        mockMvc.perform(
                        put("/api/v1/sla-policies")
                                .with(memberToken("admin-one", 1L, "ADMIN"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        [
                                          {"priority":"LOW","firstResponseMinutes":480,
                                           "resolutionMinutes":2880,"remindBeforeMinutes":60},
                                          {"priority":"HIGH","firstResponseMinutes":60,
                                           "resolutionMinutes":480,"remindBeforeMinutes":30}
                                        ]
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SLA_POLICY"));
    }

    private long tenantIdOf(String code) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_tenant WHERE code = ?",
                Long.class,
                code
        );

        return id == null ? -1L : id;
    }

    private RequestPostProcessor memberToken(
            String username,
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

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);

            TestSecurityContextHolder.setContext(context);

            return request;
        };
    }
}

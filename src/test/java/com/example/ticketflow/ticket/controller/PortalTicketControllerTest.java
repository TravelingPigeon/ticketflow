package com.example.ticketflow.ticket.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.ticketflow.support.TestAuthorities;
import org.springframework.security.core.GrantedAuthority;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class PortalTicketControllerTest {

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

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (1, 'portal-ticket-tenant', 'Portal Ticket Tenant', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_user
                    (id, tenant_id, username, password_hash, display_name, status)
                VALUES
                    (1, 1, 'agent-one', 'test-hash', 'Agent One', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_customer
                    (id, tenant_id, email, password_hash, display_name, status)
                VALUES
                    (1, 1, 'alice@example.com', 'test-hash', 'Alice', 'ACTIVE'),
                    (2, 1, 'bob@example.com', 'test-hash', 'Bob', 'ACTIVE')
                """);
    }

    @Test
    void shouldCreateTicketAsCustomer() throws Exception {
        mockMvc.perform(
                        post("/api/v1/portal/tickets")
                                .with(customerToken(1L, 1L))
                                .contentType(APPLICATION_JSON)
                                .content(ticketBody("CUST-001", "Cannot log in"))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tenantId").value(1))
                .andExpect(jsonPath("$.data.customerId").value(1))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        SELECT created_by, customer_id
                        FROM tf_ticket
                        WHERE ticket_no = ?
                        """,
                "CUST-001"
        );

        assertNull(row.get("created_by"));
        assertEquals(
                1L,
                ((Number) row.get("customer_id")).longValue()
        );
    }

    @Test
    void shouldOnlyListOwnTicketsForCustomer() throws Exception {
        createCustomerTicket(1L, 1L, "CUST-001");
        createCustomerTicket(1L, 2L, "CUST-002");

        mockMvc.perform(
                        get("/api/v1/portal/tickets")
                                .with(customerToken(1L, 1L))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].ticketNo")
                        .value("CUST-001"))
                .andExpect(jsonPath("$.data.records[0].customerId")
                        .value(1));
    }

    @Test
    void shouldNotExposeOtherCustomerTicket() throws Exception {
        createCustomerTicket(1L, 2L, "CUST-002");

        long ticketId = ticketIdOf("CUST-002");

        mockMvc.perform(
                        get("/api/v1/portal/tickets/" + ticketId)
                                .with(customerToken(1L, 1L))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("TICKET_NOT_FOUND"));
    }

    @Test
    void shouldAllowCustomerToReadOwnTicket() throws Exception {
        createCustomerTicket(1L, 1L, "CUST-001");

        long ticketId = ticketIdOf("CUST-001");

        mockMvc.perform(
                        get("/api/v1/portal/tickets/" + ticketId)
                                .with(customerToken(1L, 1L))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketNo").value("CUST-001"))
                .andExpect(jsonPath("$.data.customerId").value(1));
    }

    @Test
    void shouldNotExposeOperationTimelineOnPortalDetail() throws Exception {
        createCustomerTicket(1L, 1L, "CUST-001");

        long ticketId = ticketIdOf("CUST-001");

        // 审计记录里有"谁领取了、分配给了谁"，属于企业内部协作信息，客户侧不应该看到
        mockMvc.perform(
                        get("/api/v1/portal/tickets/" + ticketId)
                                .with(customerToken(1L, 1L))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketNo").value("CUST-001"))
                .andExpect(jsonPath("$.data.operations").doesNotExist());
    }

    @Test
    void shouldRejectMemberTokenOnPortalApi() throws Exception {
        mockMvc.perform(
                        get("/api/v1/portal/tickets")
                                .with(memberToken(1L, 1L, "agent-one", "AGENT"))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAllowAgentToSeeCustomerTicket() throws Exception {
        createCustomerTicket(1L, 1L, "CUST-001");

        long ticketId = ticketIdOf("CUST-001");

        mockMvc.perform(
                        get("/api/v1/tickets/" + ticketId)
                                .with(memberToken(1L, 1L, "agent-one", "AGENT"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketNo").value("CUST-001"))
                .andExpect(jsonPath("$.data.customerId").value(1));
    }

    @Test
    void shouldRecordCreatorForMemberTicket() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tickets")
                                .with(memberToken(1L, 1L, "agent-one", "AGENT"))
                                .contentType(APPLICATION_JSON)
                                .content(ticketBody("INTERNAL-001", "Internal task"))
                )
                .andExpect(status().isCreated());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        SELECT created_by, customer_id
                        FROM tf_ticket
                        WHERE ticket_no = ?
                        """,
                "INTERNAL-001"
        );

        assertEquals(
                1L,
                ((Number) row.get("created_by")).longValue()
        );
        assertNull(row.get("customer_id"));
    }

    private void createCustomerTicket(
            long tenantId,
            long customerId,
            String ticketNo
    ) throws Exception {
        mockMvc.perform(
                        post("/api/v1/portal/tickets")
                                .with(customerToken(tenantId, customerId))
                                .contentType(APPLICATION_JSON)
                                .content(ticketBody(ticketNo, "Customer issue"))
                )
                .andExpect(status().isCreated());
    }

    private long ticketIdOf(String ticketNo) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_ticket WHERE ticket_no = ?",
                Long.class,
                ticketNo
        );

        return id == null ? -1L : id;
    }

    private String ticketBody(String ticketNo, String title) {
        return """
                {
                  "ticketNo": "%s",
                  "title": "%s"
                }
                """.formatted(ticketNo, title);
    }

    private RequestPostProcessor customerToken(long tenantId, long actorId) {
        return token(tenantId, actorId, "customer@example.com", "CUSTOMER", null);
    }

    private RequestPostProcessor memberToken(
            long tenantId,
            long actorId,
            String username,
            String role
    ) {
        return token(tenantId, actorId, username, "MEMBER", role);
    }

    /**
     * @param roleCode 成员的角色编码，用来算出该挂哪些权限；客户传 null
     */
    private RequestPostProcessor token(
            long tenantId,
            long actorId,
            String subject,
            String actorType,
            String roleCode
    ) {
        List<GrantedAuthority> authorities = roleCode == null
                ? List.of()
                : TestAuthorities.authorities(jdbcTemplate, roleCode);

        return request -> {
            Jwt jwt = Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject(subject)
                    .claim("tenantId", tenantId)
                    .claim("actorId", actorId)
                    .claim("actorType", actorType)
                    .claim("roles", roleCode == null
                            ? List.of()
                            : List.of(roleCode))
                    .build();

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(jwt, authorities, subject);

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);

            TestSecurityContextHolder.setContext(context);

            return request;
        };
    }
}

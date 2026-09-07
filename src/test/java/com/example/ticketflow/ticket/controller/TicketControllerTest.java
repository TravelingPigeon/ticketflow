package com.example.ticketflow.ticket.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_tenant");

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (1, 'test-tenant', 'Test Tenant', 'ACTIVE')
                """);
        jdbcTemplate.update("""
        INSERT INTO tf_tenant (id, code, name, status)
        VALUES (2, 'test-tenant-2', 'Test Tenant 2', 'ACTIVE')
        """);
    }

    @Test
    void shouldCreateTicketWithHighPriority() throws Exception {
        String requestBody = """
                {
                  "ticketNo": "TF-TEST-001",
                  "title": "Login problem",
                  "description": "User cannot log in",
                  "priority": "HIGH"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").value(1))
                .andExpect(jsonPath("$.data.ticketNo").value("TF-TEST-001"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.priority").value("HIGH"));
    }

    @Test
    void shouldUseMediumPriorityByDefault() throws Exception {
        String requestBody = """
                {
                  "ticketNo": "TF-TEST-002",
                  "title": "Password reset",
                  "description": "User forgot password"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.priority").value("MEDIUM"));
    }

    @Test
    void shouldRejectUnknownTenant() throws Exception {
        String requestBody = """
                {
                  "ticketNo": "TF-TEST-003",
                  "title": "Unknown tenant test"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .session(tenantSession(999))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void shouldRejectDuplicateTicketNumber() throws Exception {
        String requestBody = """
                {
                  "ticketNo": "TF-TEST-004",
                  "title": "Duplicate ticket test"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TICKET_NO_EXISTS"));
    }

    @Test
    void shouldOnlyReturnTicketsForRequestedTenant() throws Exception {
        String tenantOneTicket = """
            {
              "ticketNo": "TENANT-1-001",
              "title": "Tenant one ticket"
            }
            """;

        String tenantTwoTicket = """
            {
              "ticketNo": "TENANT-2-001",
              "title": "Tenant two ticket"
            }
            """;

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(tenantOneTicket)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .session(tenantSession(2))
                                .contentType(APPLICATION_JSON)
                                .content(tenantTwoTicket)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/tickets")
                                .session(tenantSession(1))
                                .param("page", "1")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].tenantId").value(1))
                .andExpect(jsonPath("$.data.records[0].ticketNo")
                        .value("TENANT-1-001"));
    }

    @Test
    void shouldRejectUnknownTenantWhenListingTickets() throws Exception {
        mockMvc.perform(
                        get("/api/v1/tickets")
                                .session(tenantSession(999))
                                .param("page", "1")
                                .param("size", "10")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void shouldUpdateTicketStatus() throws Exception {
        long ticketId = createTestTicket("STATUS-001", 1);

        String updateBody = """
            {
              "tenantId": 1,
              "status": "PROCESSING"
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .contentType(APPLICATION_JSON)
                                .content(updateBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void shouldRejectInvalidStatusTransition() throws Exception {
        long ticketId = createTestTicket("STATUS-002", 1);

        String updateBody = """
            {
              "tenantId": 1,
              "status": "RESOLVED"
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .contentType(APPLICATION_JSON)
                                .content(updateBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void shouldNotUpdateTicketFromAnotherTenant() throws Exception {
        long ticketId = createTestTicket("STATUS-003", 1);

        String updateBody = """
            {
              "tenantId": 2,
              "status": "PROCESSING"
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .contentType(APPLICATION_JSON)
                                .content(updateBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("TICKET_NOT_FOUND"));
    }

    private long createTestTicket(
            String ticketNo,
            int tenantId
    ) throws Exception {
        String requestBody = """
            {
              "ticketNo": "%s",
              "title": "Status test"
            }
            """.formatted(ticketNo);

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .session(tenantSession(tenantId))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM tf_ticket
                WHERE tenant_id = ?
                  AND ticket_no = ?
                """,
                Long.class,
                tenantId,
                ticketNo
        );
    }

    private MockHttpSession tenantSession(long tenantId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CURRENT_TENANT_ID", tenantId);
        return session;
    }
}

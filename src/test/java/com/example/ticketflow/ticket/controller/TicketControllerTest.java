package com.example.ticketflow.ticket.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        jdbcTemplate.update("DELETE FROM tf_ticket_comment");
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_user");
        jdbcTemplate.update("DELETE FROM tf_tenant");

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (1, 'test-tenant', 'Test Tenant', 'ACTIVE')
                """);
        jdbcTemplate.update("""
        INSERT INTO tf_tenant (id, code, name, status)
        VALUES (2, 'test-tenant-2', 'Test Tenant 2', 'ACTIVE')
        """);

        jdbcTemplate.update("""
        INSERT INTO tf_user
            (id, tenant_id, username, password_hash, display_name, role, status)
        VALUES
            (1, 1, 'agent-one', 'test-hash', 'Agent One', 'AGENT', 'ACTIVE')
        """);

        jdbcTemplate.update("""
        INSERT INTO tf_user
            (id, tenant_id, username, password_hash, display_name, role, status)
        VALUES
            (2, 2, 'agent-two', 'test-hash', 'Agent Two', 'AGENT', 'ACTIVE')
        """);

        jdbcTemplate.update("""
        INSERT INTO tf_user
            (id, tenant_id, username, password_hash, display_name, role, status)
        VALUES
            (3, 1, 'requester-one', 'test-hash', 'Requester One', 'REQUESTER', 'ACTIVE')
        """);

        jdbcTemplate.update("""
        INSERT INTO tf_user
            (id, tenant_id, username, password_hash, display_name, role, status)
        VALUES
            (4, 1, 'admin-one', 'test-hash', 'Admin One', 'ADMIN', 'ACTIVE')
        """);

        jdbcTemplate.update("""
        INSERT INTO tf_user
            (id, tenant_id, username, password_hash, display_name, role, status)
        VALUES
            (5, 1, 'agent-beta', 'test-hash', 'Agent Beta', 'AGENT', 'ACTIVE')
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
                                .principal(authentication("agent-one", "AGENT"))
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
                                .principal(authentication("agent-one", "AGENT"))
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
                                .principal(authentication("agent-one", "AGENT"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .principal(authentication("agent-one", "AGENT"))
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
                                .principal(authentication("agent-one", "AGENT"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(tenantOneTicket)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .principal(authentication("agent-two", "AGENT"))
                                .session(tenantSession(2))
                                .contentType(APPLICATION_JSON)
                                .content(tenantTwoTicket)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        get("/api/v1/tickets")
                                .principal(authentication("agent-one", "AGENT"))
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
    void shouldReturnTicketDetailForCurrentTenant() throws Exception {
        long ticketId = createTestTicket("DETAIL-001", 1);

        mockMvc.perform(
                        get("/api/v1/tickets/" + ticketId)
                                .principal(authentication("agent-one", "AGENT"))
                                .session(tenantSession(1))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value((int) ticketId))
                .andExpect(jsonPath("$.data.tenantId").value(1))
                .andExpect(jsonPath("$.data.ticketNo")
                        .value("DETAIL-001"));
    }

    @Test
    void shouldNotReturnTicketForAnotherTenant() throws Exception {
        long ticketId = createTestTicket("DETAIL-002", 1);

        mockMvc.perform(
                        get("/api/v1/tickets/" + ticketId)
                                .principal(authentication("agent-two", "AGENT"))
                                .session(tenantSession(2))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("TICKET_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldUpdateTicketStatus() throws Exception {
        long ticketId = createTestTicket("STATUS-001", 1);

        String updateBody = """
            {
              "status": "PROCESSING"
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(updateBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldRejectInvalidStatusTransition() throws Exception {
        long ticketId = createTestTicket("STATUS-002", 1);

        String updateBody = """
            {
              "status": "RESOLVED"
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(updateBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @WithMockUser(username = "agent-two", roles = "AGENT")
    void shouldNotUpdateTicketFromAnotherTenant() throws Exception {
        long ticketId = createTestTicket("STATUS-003", 1);

        String updateBody = """
            {
              "status": "PROCESSING"
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .principal(authentication("agent-two", "AGENT"))
                                .session(tenantSession(2))
                                .contentType(APPLICATION_JSON)
                                .content(updateBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("TICKET_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldAssignTicketToAgentInSameTenant() throws Exception {
        long ticketId = createTestTicket("ASSIGN-001", 1);

        String requestBody = """
            {
              "assigneeId": 1
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/assignee")
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assigneeId").value(1))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldKeepStatusWhenAssigningNonOpenTicket() throws Exception {
        long ticketId = createTestTicket("ASSIGN-005", 1);
        setTicketStatusDirectly(ticketId, "RESOLVED");

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/assignee")
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "assigneeId": 1
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(1))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldRejectAssigneeFromAnotherTenant() throws Exception {
        long ticketId = createTestTicket("ASSIGN-002", 1);

        String requestBody = """
            {
              "assigneeId": 2
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/assignee")
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("ASSIGNEE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldRejectRequesterAsAssignee() throws Exception {
        long ticketId = createTestTicket("ASSIGN-003", 1);

        String requestBody = """
            {
              "assigneeId": 3
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/assignee")
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_ASSIGNEE_ROLE"));
    }

    @Test
    @WithMockUser(username = "requester-one", roles = "REQUESTER")
    void shouldRejectRequesterAssigningTicket() throws Exception {
        long ticketId = createTestTicket("ASSIGN-004", 1);

        String requestBody = """
            {
              "assigneeId": 1
            }
            """;

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/assignee")
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("FORBIDDEN"));
    }

    private long createTestTicket(
            String ticketNo,
            int tenantId
    ) throws Exception {
        return createTestTicket(
                ticketNo,
                tenantId,
                actorNameFor(tenantId),
                "AGENT"
        );
    }

    private long createTestTicket(
            String ticketNo,
            int tenantId,
            String username,
            String role
    ) throws Exception {
        String requestBody = """
            {
              "ticketNo": "%s",
              "title": "Status test"
            }
            """.formatted(ticketNo);

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .principal(authentication(username, role))
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

    private void assignTicketDirectly(long ticketId, long assigneeId) {
        jdbcTemplate.update(
                "UPDATE tf_ticket SET assignee_id = ? WHERE id = ?",
                assigneeId,
                ticketId
        );
    }

    private void setTicketStatusDirectly(long ticketId, String status) {
        jdbcTemplate.update(
                "UPDATE tf_ticket SET status = ? WHERE id = ?",
                status,
                ticketId
        );
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldFilterTicketsByStatus() throws Exception {
        long processingTicket =
                createTestTicket("FILTER-STATUS-001", 1);

        createTestTicket("FILTER-STATUS-002", 1);

        mockMvc.perform(
                        patch("/api/v1/tickets/" + processingTicket + "/status")
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                    {
                                      "status": "PROCESSING"
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/v1/tickets")
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .param("status", "PROCESSING")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].ticketNo")
                        .value("FILTER-STATUS-001"));
    }

    @Test
    void shouldFilterTicketsByPriority() throws Exception {
        createTestTicketWithPriority(
                "FILTER-PRIORITY-001",
                1,
                "HIGH"
        );

        createTestTicket(
                "FILTER-PRIORITY-002",
                1
        );

        mockMvc.perform(
                        get("/api/v1/tickets")
                                .principal(authentication("agent-one", "AGENT"))
                                .session(tenantSession(1))
                                .param("priority", "HIGH")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].ticketNo")
                        .value("FILTER-PRIORITY-001"));
    }

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldFilterTicketsByAssignee() throws Exception {
        long assignedTicket =
                createTestTicket("FILTER-ASSIGNEE-001", 1);

        createTestTicket("FILTER-ASSIGNEE-002", 1);

        mockMvc.perform(
                        patch("/api/v1/tickets/" + assignedTicket + "/assignee")
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                    {
                                      "assigneeId": 1
                                    }
                                    """)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/v1/tickets")
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .param("assigneeId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].ticketNo")
                        .value("FILTER-ASSIGNEE-001"));
    }

    private long createTestTicketWithPriority(
            String ticketNo,
            int tenantId,
            String priority
    ) throws Exception {
        String requestBody = """
            {
              "ticketNo": "%s",
              "title": "Priority filter test",
              "priority": "%s"
            }
            """.formatted(ticketNo, priority);

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .principal(authentication(actorNameFor(tenantId), "AGENT"))
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

    @Test
    @WithMockUser(username = "admin-one", roles = "ADMIN")
    void shouldUpdateTicketDetails() throws Exception {
        long ticketId = createTestTicket("EDIT-001", 1);

        String requestBody = """
            {
              "title": "Updated title",
              "description": "Updated description",
              "priority": "HIGH"
            }
            """;

        mockMvc.perform(
                        put("/api/v1/tickets/" + ticketId)
                                .principal(authentication("admin-one", "ADMIN"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title")
                        .value("Updated title"))
                .andExpect(jsonPath("$.data.description")
                        .value("Updated description"))
                .andExpect(jsonPath("$.data.priority")
                        .value("HIGH"))
                .andExpect(jsonPath("$.data.status")
                        .value("OPEN"));
    }

    @Test
    @WithMockUser(username = "agent-two", roles = "AGENT")
    void shouldNotEditTicketFromAnotherTenant() throws Exception {
        long ticketId = createTestTicket("EDIT-002", 1);

        String requestBody = """
            {
              "title": "Illegal update",
              "description": "Should not be updated",
              "priority": "LOW"
            }
            """;

        mockMvc.perform(
                        put("/api/v1/tickets/" + ticketId)
                                .principal(authentication("agent-two", "AGENT"))
                                .session(tenantSession(2))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("TICKET_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "requester-one", roles = "REQUESTER")
    void shouldRejectRequesterUpdatingTicket() throws Exception {
        long ticketId = createTestTicket("EDIT-003", 1);

        String requestBody = """
            {
              "title": "Requester update",
              "description": "Should be rejected",
              "priority": "MEDIUM"
            }
            """;

        mockMvc.perform(
                        put("/api/v1/tickets/" + ticketId)
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("FORBIDDEN"));
    }

    private Authentication authentication(
            String username,
            String role
    ) {
        return new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" + role
                        )
                )
        );
    }

    private String actorNameFor(int tenantId) {
        return tenantId == 1 ? "agent-one" : "agent-two";
    }

    @Test
    void shouldRecordCreatorWhenCreatingTicket() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tickets")
                                .session(tenantSession(1))
                                .principal(authentication("agent-one", "AGENT"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "ticketNo": "CREATOR-001",
                                          "title": "Record creator test"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdBy").value(1));

        Long createdBy = jdbcTemplate.queryForObject(
                "SELECT created_by FROM tf_ticket WHERE ticket_no = ?",
                Long.class,
                "CREATOR-001"
        );

        assertEquals(1L, createdBy);
    }

    @Test
    void shouldRejectCreatingTicketWithoutAuthentication() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tickets")
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "ticketNo": "NOAUTH-001",
                                          "title": "No auth test"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void shouldListAllTenantTicketsForAgent() throws Exception {
        createTestTicket("SCOPE-AGENT-001", 1, "agent-one", "AGENT");
        createTestTicket("SCOPE-AGENT-002", 1, "requester-one", "REQUESTER");

        mockMvc.perform(
                        get("/api/v1/tickets")
                                .principal(authentication("agent-one", "AGENT"))
                                .session(tenantSession(1))
                                .param("page", "1")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void shouldOnlyListOwnTicketsForRequester() throws Exception {
        createTestTicket("SCOPE-REQ-001", 1, "agent-one", "AGENT");

        long ownTicketId = createTestTicket(
                "SCOPE-REQ-002",
                1,
                "requester-one",
                "REQUESTER"
        );

        mockMvc.perform(
                        get("/api/v1/tickets")
                                .principal(authentication("requester-one", "REQUESTER"))
                                .session(tenantSession(1))
                                .param("page", "1")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id")
                        .value((int) ownTicketId))
                .andExpect(jsonPath("$.data.records[0].ticketNo")
                        .value("SCOPE-REQ-002"));
    }

    @Test
    void shouldAllowRequesterReadingOwnTicket() throws Exception {
        long ticketId = createTestTicket(
                "SCOPE-DETAIL-001",
                1,
                "requester-one",
                "REQUESTER"
        );

        mockMvc.perform(
                        get("/api/v1/tickets/" + ticketId)
                                .principal(authentication("requester-one", "REQUESTER"))
                                .session(tenantSession(1))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value((int) ticketId));
    }

    @Test
    void shouldNotExposeOtherTicketToRequester() throws Exception {
        long ticketId =
                createTestTicket("SCOPE-DETAIL-002", 1, "agent-one", "AGENT");

        mockMvc.perform(
                        get("/api/v1/tickets/" + ticketId)
                                .principal(authentication("requester-one", "REQUESTER"))
                                .session(tenantSession(1))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldAllowAgentUpdatingOwnAssignedTicket() throws Exception {
        long ticketId = createTestTicket("WRITE-001", 1);
        assignTicketDirectly(ticketId, 1);

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .principal(authentication("agent-one", "AGENT"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "status": "PROCESSING"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldRejectAgentUpdatingUnassignedTicket() throws Exception {
        long ticketId = createTestTicket("WRITE-002", 1);

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .principal(authentication("agent-one", "AGENT"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "status": "PROCESSING"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldRejectAgentUpdatingTicketAssignedToAnotherAgent() throws Exception {
        long ticketId = createTestTicket("WRITE-003", 1);
        assignTicketDirectly(ticketId, 5);

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .principal(authentication("agent-one", "AGENT"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "status": "PROCESSING"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldRejectAgentAssigningTicket() throws Exception {
        long ticketId = createTestTicket("WRITE-004", 1);

        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/assignee")
                                .principal(authentication("agent-one", "AGENT"))
                                .session(tenantSession(1))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "assigneeId": 1
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}

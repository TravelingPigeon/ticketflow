package com.example.ticketflow.ticket.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_tenant");

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (1, 'test-tenant', 'Test Tenant', 'ACTIVE')
                """);
    }

    @Test
    void shouldCreateTicketWithHighPriority() throws Exception {
        String requestBody = """
                {
                  "tenantId": 1,
                  "ticketNo": "TF-TEST-001",
                  "title": "Login problem",
                  "description": "User cannot log in",
                  "priority": "HIGH"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tickets")
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
                  "tenantId": 1,
                  "ticketNo": "TF-TEST-002",
                  "title": "Password reset",
                  "description": "User forgot password"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tickets")
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
                  "tenantId": 999,
                  "ticketNo": "TF-TEST-003",
                  "title": "Unknown tenant test"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tickets")
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
                  "tenantId": 1,
                  "ticketNo": "TF-TEST-004",
                  "title": "Duplicate ticket test"
                }
                """;

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/tickets")
                                .contentType(APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TICKET_NO_EXISTS"));
    }
}
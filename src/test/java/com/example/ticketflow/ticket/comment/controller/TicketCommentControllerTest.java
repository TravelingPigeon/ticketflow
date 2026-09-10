package com.example.ticketflow.ticket.comment.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class TicketCommentControllerTest {

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
                VALUES (1, 'comment-tenant-1', 'Comment Tenant 1', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (2, 'comment-tenant-2', 'Comment Tenant 2', 'ACTIVE')
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
                INSERT INTO tf_ticket
                    (id, tenant_id, ticket_no, title, status, priority)
                VALUES
                    (1, 1, 'COMMENT-001', 'Comment ticket', 'OPEN', 'MEDIUM')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_ticket
                    (id, tenant_id, ticket_no, title, status, priority)
                VALUES
                    (2, 2, 'COMMENT-002', 'Other tenant ticket', 'OPEN', 'MEDIUM')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_ticket
                    (id, tenant_id, ticket_no, title, status, priority)
                VALUES
                    (3, 1, 'COMMENT-003', 'Second ticket of tenant one', 'OPEN', 'MEDIUM')
                """);
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldCreateComment() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tickets/1/comments")
                                .session(tenantSession(1))
                                .principal(authentication("agent-one", "AGENT"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "content": "Please investigate this issue."
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").value(1))
                .andExpect(jsonPath("$.data.ticketId").value(1))
                .andExpect(jsonPath("$.data.authorId").value(1))
                .andExpect(jsonPath("$.data.content")
                        .value("Please investigate this issue."));
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldRejectCommentForAnotherTenantTicket() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tickets/2/comments")
                                .session(tenantSession(1))
                                .principal(authentication("agent-one", "AGENT"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "content": "Illegal cross-tenant comment."
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("TICKET_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldRejectBlankComment() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tickets/1/comments")
                                .session(tenantSession(1))
                                .principal(authentication("agent-one", "AGENT"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "content": " "
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldListTicketCommentsInCreatedAtOrder() throws Exception {
        insertComment(1, 1, 1, 3, "Third in time, inserted first", "2026-09-01 11:00:00");
        insertComment(2, 1, 1, 1, "First in time, inserted second", "2026-09-01 09:00:00");
        insertComment(3, 1, 1, 3, "Second in time, inserted third", "2026-09-01 10:00:00");

        mockMvc.perform(
                        get("/api/v1/tickets/1/comments")
                                .session(tenantSession(1))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].tenantId").value(1))
                .andExpect(jsonPath("$.data[0].ticketId").value(1))
                .andExpect(jsonPath("$.data[0].authorId").value(1))
                .andExpect(jsonPath("$.data[0].content")
                        .value("First in time, inserted second"))
                .andExpect(jsonPath("$.data[1].id").value(3))
                .andExpect(jsonPath("$.data[1].authorId").value(3))
                .andExpect(jsonPath("$.data[2].id").value(1));
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldReturnEmptyListWhenTicketHasNoComment() throws Exception {
        mockMvc.perform(
                        get("/api/v1/tickets/1/comments")
                                .session(tenantSession(1))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldOnlyListCommentsOfRequestedTicket() throws Exception {
        insertComment(1, 1, 1, 1, "Comment of ticket one", "2026-09-01 09:00:00");
        insertComment(2, 1, 3, 1, "Comment of ticket three", "2026-09-01 10:00:00");

        mockMvc.perform(
                        get("/api/v1/tickets/1/comments")
                                .session(tenantSession(1))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ticketId").value(1))
                .andExpect(jsonPath("$.data[0].content")
                        .value("Comment of ticket one"));
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldRejectListingCommentsOfAnotherTenantTicket() throws Exception {
        insertComment(1, 2, 2, 2, "Comment of other tenant", "2026-09-01 09:00:00");

        mockMvc.perform(
                        get("/api/v1/tickets/2/comments")
                                .session(tenantSession(1))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("TICKET_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldRejectListingCommentsWithoutLoginSession() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/1/comments"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHENTICATED"));
    }

    private void insertComment(
            long id,
            long tenantId,
            long ticketId,
            long authorId,
            String content,
            String createdAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO tf_ticket_comment
                            (id, tenant_id, ticket_id, author_id, content, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                id,
                tenantId,
                ticketId,
                authorId,
                content,
                createdAt
        );
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

    private MockHttpSession tenantSession(long tenantId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CURRENT_TENANT_ID", tenantId);
        return session;
    }
}

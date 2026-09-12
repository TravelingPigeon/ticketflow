package com.example.ticketflow.ticket.comment.controller;

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
                    (id, tenant_id, ticket_no, title, status, priority, created_by)
                VALUES
                    (1, 1, 'COMMENT-001', 'Comment ticket', 'OPEN', 'MEDIUM', 1)
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_ticket
                    (id, tenant_id, ticket_no, title, status, priority, created_by)
                VALUES
                    (2, 2, 'COMMENT-002', 'Other tenant ticket', 'OPEN', 'MEDIUM', 2)
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_ticket
                    (id, tenant_id, ticket_no, title, status, priority, created_by)
                VALUES
                    (3, 1, 'COMMENT-003', 'Second ticket of tenant one', 'OPEN', 'MEDIUM', 3)
                """);
    }

    @Test
    @WithMockUser(username = "agent-one", roles = "AGENT")
    void shouldCreateComment() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tickets/1/comments")
                                .with(jwtFor("agent-one", 1, "AGENT"))
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
                                .with(jwtFor("agent-one", 1, "AGENT"))
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
                                .with(jwtFor("agent-one", 1, "AGENT"))
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
                                .with(jwtFor("agent-one", 1, "AGENT"))
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
                                .with(jwtFor("agent-one", 1, "AGENT"))
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
                                .with(jwtFor("agent-one", 1, "AGENT"))
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
                                .with(jwtFor("agent-one", 1, "AGENT"))
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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("UNAUTHENTICATED"));
    }

    @Test
    @WithMockUser(username = "requester-one", roles = "REQUESTER")
    void shouldAllowRequesterCommentingOwnTicket() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tickets/3/comments")
                                .with(jwtFor("requester-one", 1, "REQUESTER"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "content": "My own ticket comment."
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorId").value(3))
                .andExpect(jsonPath("$.data.ticketId").value(3));
    }

    @Test
    @WithMockUser(username = "requester-one", roles = "REQUESTER")
    void shouldRejectRequesterCommentingOthersTicket() throws Exception {
        mockMvc.perform(
                        post("/api/v1/tickets/1/comments")
                                .with(jwtFor("requester-one", 1, "REQUESTER"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "content": "Comment on someone else's ticket."
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "requester-one", roles = "REQUESTER")
    void shouldAllowRequesterListingOwnTicketComments() throws Exception {
        insertComment(1, 1, 3, 3, "Requester comment", "2026-09-01 09:00:00");

        mockMvc.perform(
                        get("/api/v1/tickets/3/comments")
                                .with(jwtFor("requester-one", 1, "REQUESTER"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ticketId").value(3))
                .andExpect(jsonPath("$.data[0].content")
                        .value("Requester comment"));
    }

    @Test
    @WithMockUser(username = "requester-one", roles = "REQUESTER")
    void shouldRejectRequesterListingOthersTicketComments() throws Exception {
        insertComment(1, 1, 1, 1, "Agent comment", "2026-09-01 09:00:00");

        mockMvc.perform(
                        get("/api/v1/tickets/1/comments")
                                .with(jwtFor("requester-one", 1, "REQUESTER"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
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

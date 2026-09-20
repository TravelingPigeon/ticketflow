package com.example.ticketflow.ticket.comment.controller;

import com.example.ticketflow.sla.service.SlaPolicyService;
import com.example.ticketflow.support.TestAuthorities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F1-2：客户侧评论入口。
 *
 * <p>四件事：客户能发公开回复、客户看不到内部备注、客户回复"等待客户"的单会把工单推回处理中、
 * 客户回复会通知负责人。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class PortalTicketCommentControllerTest {

    private static final long AGENT_ID = 1L;

    private static final long CUSTOMER_ALICE = 1L;

    private static final long CUSTOMER_BOB = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SlaPolicyService slaPolicyService;

    @BeforeEach
    void setUp() {

        jdbcTemplate.update("DELETE FROM tf_notification");

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

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (1, 'portal-comment-tenant', 'Portal Comment Tenant', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_user
                    (id, tenant_id, username, password_hash, display_name, status)
                VALUES (1, 1, 'agent-one', 'test-hash', 'Agent One', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_customer
                    (id, tenant_id, email, password_hash, display_name, status)
                VALUES
                    (1, 1, 'alice@example.com', 'test-hash', 'Alice', 'ACTIVE'),
                    (2, 1, 'bob@example.com', 'test-hash', 'Bob', 'ACTIVE')
                """);

        // 客户提单也要算 SLA 截止时间，测试租户得有规则
        slaPolicyService.createDefaultPolicies(1L);
    }

    @Test
    void shouldLetCustomerCommentOwnTicket() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-001",
                "OPEN",
                null
        );

        mockMvc.perform(
                        post("/api/v1/portal/tickets/" + ticketId + "/comments")
                                .with(customerToken(CUSTOMER_ALICE))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"content": "还是登录不上，麻烦再看一下"}
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorType").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.authorId")
                        .value(CUSTOMER_ALICE))
                .andExpect(jsonPath("$.data.commentType")
                        .value("PUBLIC_REPLY"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                        SELECT author_id, author_type, comment_type
                        FROM tf_ticket_comment
                        WHERE ticket_id = ?
                        """,
                ticketId
        );

        assertEquals(CUSTOMER_ALICE, row.get("author_id"));
        assertEquals("CUSTOMER", row.get("author_type"));
        assertEquals("PUBLIC_REPLY", row.get("comment_type"));
    }

    @Test
    void shouldNotLetCustomerCommentAnotherCustomersTicket() throws Exception {
        long ticketOfBob = createTicket(
                CUSTOMER_BOB,
                "PC-002",
                "OPEN",
                null
        );

        // Alice 去评论 Bob 的单：不可见即不存在
        mockMvc.perform(
                        post("/api/v1/portal/tickets/" + ticketOfBob + "/comments")
                                .with(customerToken(CUSTOMER_ALICE))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"content": "偷看别人的单"}
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));

        assertEquals(0, commentCountOf(ticketOfBob));
    }

    @Test
    void shouldRejectInternalNoteFromCustomer() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-003",
                "OPEN",
                null
        );

        mockMvc.perform(
                        post("/api/v1/portal/tickets/" + ticketId + "/comments")
                                .with(customerToken(CUSTOMER_ALICE))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "content": "客户想留内部备注",
                                          "commentType": "INTERNAL_NOTE"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertEquals(0, commentCountOf(ticketId));
    }

    @Test
    void shouldRejectMemberOnPortalCommentEndpoint() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-004",
                "OPEN",
                null
        );

        // 门户接口只认客户身份
        mockMvc.perform(
                        get("/api/v1/portal/tickets/" + ticketId + "/comments")
                                .with(memberToken(AGENT_ID, "agent-one", "AGENT"))
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldHideInternalNotesFromCustomer() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-005",
                "OPEN",
                null
        );

        insertComment(
                ticketId,
                AGENT_ID,
                "MEMBER",
                "INTERNAL_NOTE",
                "内部排查结论：是网关缓存"
        );
        insertComment(
                ticketId,
                AGENT_ID,
                "MEMBER",
                "PUBLIC_REPLY",
                "已经修复，请再试一次"
        );

        mockMvc.perform(
                        get("/api/v1/portal/tickets/" + ticketId + "/comments")
                                .with(customerToken(CUSTOMER_ALICE))
                )
                .andExpect(status().isOk())
                // 内部备注不能出现在客户的响应里
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].commentType")
                        .value("PUBLIC_REPLY"))
                .andExpect(jsonPath("$.data[0].content")
                        .value("已经修复，请再试一次"));
    }

    @Test
    void shouldLetMemberSeeInternalNotes() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-006",
                "OPEN",
                null
        );

        insertComment(
                ticketId,
                AGENT_ID,
                "MEMBER",
                "INTERNAL_NOTE",
                "内部备注"
        );
        insertComment(
                ticketId,
                AGENT_ID,
                "MEMBER",
                "PUBLIC_REPLY",
                "公开回复"
        );

        // 同一个服务方法，成员侧不过滤：可见性跟着"查询者的视角"走
        mockMvc.perform(
                        get("/api/v1/tickets/" + ticketId + "/comments")
                                .with(memberToken(AGENT_ID, "agent-one", "AGENT"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void shouldResumeWaitingCustomerTicketOnCustomerReply() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-007",
                "WAITING_CUSTOMER",
                AGENT_ID
        );

        mockMvc.perform(
                        post("/api/v1/portal/tickets/" + ticketId + "/comments")
                                .with(customerToken(CUSTOMER_ALICE))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"content": "补充一下：只有内网能复现"}
                                        """)
                )
                .andExpect(status().isOk());

        assertEquals("PROCESSING", statusOf(ticketId));

        // 时间线上能看出"是客户回复把单子推回去的"
        Map<String, Object> operation = lastOperationOf(ticketId);

        assertEquals("CUSTOMER", operation.get("operator_type"));
        assertEquals(CUSTOMER_ALICE, operation.get("operator_id"));
        assertEquals("STATUS_CHANGED", operation.get("operation_type"));
        assertEquals("WAITING_CUSTOMER", operation.get("from_value"));
        assertEquals("PROCESSING", operation.get("to_value"));
    }

    @Test
    void shouldNotChangeStatusWhenTicketIsNotWaitingCustomer() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-008",
                "PROCESSING",
                AGENT_ID
        );

        mockMvc.perform(
                        post("/api/v1/portal/tickets/" + ticketId + "/comments")
                                .with(customerToken(CUSTOMER_ALICE))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"content": "再补充一句"}
                                        """)
                )
                .andExpect(status().isOk());

        assertEquals("PROCESSING", statusOf(ticketId));
        assertEquals(0, operationCountOf(ticketId));
    }

    @Test
    void shouldNotResumeWhenMemberReplies() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-009",
                "WAITING_CUSTOMER",
                AGENT_ID
        );

        // 设计稿里的 reply 命令：只有"客户回复等待中工单"才恢复处理
        mockMvc.perform(
                        post("/api/v1/tickets/" + ticketId + "/comments")
                                .with(memberToken(AGENT_ID, "agent-one", "AGENT"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"content": "我这边先记一下"}
                                        """)
                )
                .andExpect(status().isOk());

        assertEquals("WAITING_CUSTOMER", statusOf(ticketId));
    }

    @Test
    void shouldNotifyAssigneeOnCustomerReply() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-010",
                "PROCESSING",
                AGENT_ID
        );

        mockMvc.perform(
                        post("/api/v1/portal/tickets/" + ticketId + "/comments")
                                .with(customerToken(CUSTOMER_ALICE))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"content": "问题又出现了"}
                                        """)
                )
                .andExpect(status().isOk());

        List<Map<String, Object>> notifications =
                notificationsOf(AGENT_ID);

        assertEquals(1, notifications.size());
        assertEquals(
                "CUSTOMER_REPLIED",
                notifications.get(0).get("type")
        );
        assertEquals("客户回复了工单", notifications.get(0).get("title"));

        String content = (String) notifications.get(0).get("content");
        assertTrue(content.contains("PC-010"), content);
        assertTrue(content.contains("问题又出现了"), content);

        long commentId = lastCommentIdOf(ticketId);

        // 去重键带评论 ID：每次回复都是独立的一件事
        assertEquals(
                "ticket:customer-replied:" + ticketId
                        + ":comment:" + commentId
                        + ":member:" + AGENT_ID,
                notifications.get(0).get("business_key")
        );
    }

    @Test
    void shouldSendOneNotificationPerCustomerReply() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-011",
                "PROCESSING",
                AGENT_ID
        );

        for (String content : List.of("第一次补充", "第二次补充", "第三次补充")) {
            mockMvc.perform(
                            post("/api/v1/portal/tickets/" + ticketId + "/comments")
                                    .with(customerToken(CUSTOMER_ALICE))
                                    .contentType(APPLICATION_JSON)
                                    .content("{\"content\": \"" + content + "\"}")
                    )
                    .andExpect(status().isOk());
        }

        // 如果去重键照抄 SLA 那种"只带工单 ID"的写法，这里只会剩 1 条
        assertEquals(3, notificationsOf(AGENT_ID).size());
    }

    @Test
    void shouldNotNotifyWhenTicketHasNoAssignee() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-012",
                "OPEN",
                null
        );

        mockMvc.perform(
                        post("/api/v1/portal/tickets/" + ticketId + "/comments")
                                .with(customerToken(CUSTOMER_ALICE))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"content": "没人认领的单"}
                                        """)
                )
                .andExpect(status().isOk());

        // 没有负责人就没有收件人；这种单子在共享队列里，由 SLA 提醒去催管理员
        assertEquals(0, notificationCountOf(AGENT_ID));
    }

    @Test
    void shouldNotNotifyWhenMemberReplies() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "PC-013",
                "PROCESSING",
                AGENT_ID
        );

        mockMvc.perform(
                        post("/api/v1/tickets/" + ticketId + "/comments")
                                .with(memberToken(AGENT_ID, "agent-one", "AGENT"))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"content": "已处理，请确认"}
                                        """)
                )
                .andExpect(status().isOk());

        // 自己做的事不用提醒自己
        assertEquals(0, notificationCountOf(AGENT_ID));
    }

    private long createTicket(
            long customerId,
            String ticketNo,
            String status,
            Long assigneeId
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO tf_ticket
                            (tenant_id, ticket_no, title, status, priority,
                             customer_id, assignee_id,
                             first_response_due_at, resolution_due_at)
                        VALUES
                            (1, ?, '门户评论测试', ?, 'MEDIUM', ?, ?,
                             DATEADD('HOUR', 4, CURRENT_TIMESTAMP),
                             DATEADD('HOUR', 24, CURRENT_TIMESTAMP))
                        """,
                ticketNo,
                status,
                customerId,
                assigneeId
        );

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_ticket WHERE ticket_no = ?",
                Long.class,
                ticketNo
        );

        return id == null ? -1L : id;
    }

    private void insertComment(
            long ticketId,
            long authorId,
            String authorType,
            String commentType,
            String content
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO tf_ticket_comment
                            (tenant_id, ticket_id, author_id, author_type,
                             comment_type, content)
                        VALUES (1, ?, ?, ?, ?, ?)
                        """,
                ticketId,
                authorId,
                authorType,
                commentType,
                content
        );
    }

    private int commentCountOf(long ticketId) {
        return countOf(
                "SELECT COUNT(*) FROM tf_ticket_comment WHERE ticket_id = ?",
                ticketId
        );
    }

    private int notificationCountOf(long recipientId) {
        return countOf(
                "SELECT COUNT(*) FROM tf_notification WHERE recipient_id = ?",
                recipientId
        );
    }

    private int operationCountOf(long ticketId) {
        return countOf(
                "SELECT COUNT(*) FROM tf_ticket_operation WHERE ticket_id = ?",
                ticketId
        );
    }

    private int countOf(String sql, long parameter) {
        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                parameter
        );

        return count == null ? 0 : count;
    }

    private String statusOf(long ticketId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM tf_ticket WHERE id = ?",
                String.class,
                ticketId
        );
    }

    private long lastCommentIdOf(long ticketId) {
        Long id = jdbcTemplate.queryForObject(
                """
                        SELECT MAX(id)
                        FROM tf_ticket_comment
                        WHERE ticket_id = ?
                        """,
                Long.class,
                ticketId
        );

        return id == null ? -1L : id;
    }

    private Map<String, Object> lastOperationOf(long ticketId) {
        List<Map<String, Object>> operations = jdbcTemplate.queryForList(
                """
                        SELECT operator_type, operator_id, operation_type,
                               from_value, to_value
                        FROM tf_ticket_operation
                        WHERE ticket_id = ?
                        ORDER BY id
                        """,
                ticketId
        );

        return operations.get(operations.size() - 1);
    }

    private List<Map<String, Object>> notificationsOf(long recipientId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT type, title, content, business_key
                        FROM tf_notification
                        WHERE recipient_id = ?
                        ORDER BY id
                        """,
                recipientId
        );
    }

    private RequestPostProcessor customerToken(long actorId) {
        return token(1L, actorId, "customer@example.com", "CUSTOMER", null);
    }

    private RequestPostProcessor memberToken(
            long actorId,
            String username,
            String role
    ) {
        return token(1L, actorId, username, "MEMBER", role);
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

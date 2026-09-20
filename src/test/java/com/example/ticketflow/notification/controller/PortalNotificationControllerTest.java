package com.example.ticketflow.notification.controller;

import com.example.ticketflow.notification.domain.enums.NotificationType;
import com.example.ticketflow.notification.service.NotificationService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A2：客户侧通知。
 *
 * <p>两件事：客户能收/能读自己的通知；工单解决时提单客户会收到通知。</p>
 *
 * <p>这里有一组很多人会漏的用例：**同一个租户里成员 1 号和客户 1 号是两个不同的人**，
 * 所以"按 ID 找通知"必须同时比收件人类型。演示库里客户的 1 号就是 zhang，
 * 而成员的 1 号是 alice——这条路径不是理论上的越权。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class PortalNotificationControllerTest {

    private static final long AGENT_ID = 1L;

    private static final long CUSTOMER_ALICE = 1L;

    private static final long CUSTOMER_BOB = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NotificationService notificationService;

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
                VALUES (1, 'portal-notify-tenant', 'Portal Notify Tenant', 'ACTIVE')
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

        slaPolicyService.createDefaultPolicies(1L);
    }

    // ------------------------------------------------------------------
    // 收件箱与已读
    // ------------------------------------------------------------------

    @Test
    void shouldListOnlyOwnNotificationsForCustomer() throws Exception {
        givenMemberNotification();
        givenCustomerNotification(CUSTOMER_ALICE, "给 Alice 的通知");
        givenCustomerNotification(CUSTOMER_BOB, "给 Bob 的通知");

        mockMvc.perform(
                        get("/api/v1/portal/notifications")
                                .with(customerToken(CUSTOMER_ALICE))
                )
                .andExpect(status().isOk())
                // 同租户里还有一个 id 相同的成员通知，不能出现
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title")
                        .value("给 Alice 的通知"))
                .andExpect(jsonPath("$.data[0].recipientType")
                        .value("CUSTOMER"))
                .andExpect(jsonPath("$.data[0].recipientId")
                        .value(CUSTOMER_ALICE));
    }

    @Test
    void shouldMarkOwnNotificationAsRead() throws Exception {
        givenCustomerNotification(CUSTOMER_ALICE, "给 Alice 的通知");

        long notificationId = notificationIdOf("给 Alice 的通知");

        mockMvc.perform(
                        patch("/api/v1/portal/notifications/"
                                + notificationId + "/read")
                                .with(customerToken(CUSTOMER_ALICE))
                )
                .andExpect(status().isOk());

        assertNotNull(readAtOf(notificationId));

        // 再标一次不报错、也不刷新时间（这里只断言不抛异常）
        mockMvc.perform(
                        patch("/api/v1/portal/notifications/"
                                + notificationId + "/read")
                                .with(customerToken(CUSTOMER_ALICE))
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldOnlyReturnUnreadWhenAsked() throws Exception {
        givenCustomerNotification(CUSTOMER_ALICE, "第一条");
        givenCustomerNotification(CUSTOMER_ALICE, "第二条");

        long firstId = notificationIdOf("第一条");

        mockMvc.perform(
                        patch("/api/v1/portal/notifications/"
                                + firstId + "/read")
                                .with(customerToken(CUSTOMER_ALICE))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/v1/portal/notifications?unreadOnly=true")
                                .with(customerToken(CUSTOMER_ALICE))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("第二条"));
    }

    // ------------------------------------------------------------------
    // 越权：这是本轮修掉的那个缺陷
    // ------------------------------------------------------------------

    @Test
    void shouldNotMarkMemberNotificationWithSameId() throws Exception {
        givenMemberNotification();

        long memberNotificationId = notificationIdOf("给成员的通知");

        // 客户 1 去标成员 1 的通知：同租户、同 ID，只差收件人类型
        mockMvc.perform(
                        patch("/api/v1/portal/notifications/"
                                + memberNotificationId + "/read")
                                .with(customerToken(CUSTOMER_ALICE))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("NOTIFICATION_NOT_FOUND"));

        // 而且真的没被改
        assertEquals(null, readAtOf(memberNotificationId));
    }

    @Test
    void shouldNotMarkCustomerNotificationAsMember() throws Exception {
        givenCustomerNotification(CUSTOMER_ALICE, "给 Alice 的通知");

        long customerNotificationId = notificationIdOf("给 Alice 的通知");

        // 反方向：成员 1 去标客户 1 的通知
        mockMvc.perform(
                        patch("/api/v1/notifications/"
                                + customerNotificationId + "/read")
                                .with(memberToken())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("NOTIFICATION_NOT_FOUND"));

        assertEquals(null, readAtOf(customerNotificationId));
    }

    @Test
    void shouldNotMarkAnotherCustomersNotification() throws Exception {
        givenCustomerNotification(CUSTOMER_BOB, "给 Bob 的通知");

        long bobNotificationId = notificationIdOf("给 Bob 的通知");

        mockMvc.perform(
                        patch("/api/v1/portal/notifications/"
                                + bobNotificationId + "/read")
                                .with(customerToken(CUSTOMER_ALICE))
                )
                .andExpect(status().isNotFound());

        assertEquals(null, readAtOf(bobNotificationId));
    }

    @Test
    void shouldRejectMemberOnPortalNotificationEndpoint() throws Exception {
        mockMvc.perform(
                        get("/api/v1/portal/notifications")
                                .with(memberToken())
                )
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // 工单解决时通知提单客户
    // ------------------------------------------------------------------

    @Test
    void shouldNotifyCustomerWhenTicketIsResolved() throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "A2-001",
                "PROCESSING",
                AGENT_ID
        );

        resolve(ticketId);

        List<Map<String, Object>> notifications =
                notificationsOf(CUSTOMER_ALICE);

        assertEquals(1, notifications.size());
        assertEquals(
                "TICKET_RESOLVED",
                notifications.get(0).get("type")
        );
        assertEquals("你提交的工单已解决", notifications.get(0).get("title"));
        assertTrue(
                ((String) notifications.get(0).get("content"))
                        .contains("A2-001"),
                (String) notifications.get(0).get("content")
        );

        // 键带的是操作记录 ID：每次解决都是一件新的事
        String key = (String) notifications.get(0).get("business_key");
        assertTrue(
                key.startsWith("ticket:resolved:" + ticketId + ":operation:"),
                key
        );
    }

    @Test
    void shouldNotifyCustomerAgainAfterReopenAndSecondResolution()
            throws Exception {
        long ticketId = createTicket(
                CUSTOMER_ALICE,
                "A2-002",
                "PROCESSING",
                AGENT_ID
        );

        resolve(ticketId);

        mockMvc.perform(
                        post("/api/v1/portal/tickets/" + ticketId + "/reopen")
                                .with(customerToken(CUSTOMER_ALICE))
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"reason": "还是不行"}
                                        """)
                )
                .andExpect(status().isOk());

        resolve(ticketId);

        // 如果去重键只带 ticketId，这里只会剩 1 条
        assertEquals(2, notificationsOf(CUSTOMER_ALICE).size());
        assertEquals(
                2,
                distinctBusinessKeyCountOf(CUSTOMER_ALICE)
        );
    }

    @Test
    void shouldNotNotifyCustomerWhenTicketHasNoCustomer() throws Exception {
        // 成员自己建的单：customer_id 为空，没有收件人
        long ticketId = createTicket(
                null,
                "A2-003",
                "PROCESSING",
                AGENT_ID
        );

        resolve(ticketId);

        assertEquals(0, customerNotificationCountOf(1L));
    }

    // ------------------------------------------------------------------

    private void givenMemberNotification() {
        notificationService.notifyMember(
                1L,
                AGENT_ID,
                NotificationType.TICKET_ASSIGNED,
                null,
                "给成员的通知",
                null,
                "test:member-notification:" + AGENT_ID
        );
    }

    private void givenCustomerNotification(long customerId, String title) {
        notificationService.notifyCustomer(
                1L,
                customerId,
                NotificationType.TICKET_RESOLVED,
                null,
                title,
                null,
                "test:customer-notification:" + customerId + ":" + title
        );
    }

    private void resolve(long ticketId) throws Exception {
        mockMvc.perform(
                        patch("/api/v1/tickets/" + ticketId + "/status")
                                .with(memberToken())
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"status": "RESOLVED"}
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    private long createTicket(
            Long customerId,
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
                            (1, ?, '客户通知测试', ?, 'MEDIUM', ?, ?,
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

    private long notificationIdOf(String title) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_notification WHERE title = ?",
                Long.class,
                title
        );

        return id == null ? -1L : id;
    }

    private Object readAtOf(long notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT read_at FROM tf_notification WHERE id = ?",
                Object.class,
                notificationId
        );
    }

    private List<Map<String, Object>> notificationsOf(long customerId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT type, title, content, business_key
                        FROM tf_notification
                        WHERE recipient_type = 'CUSTOMER'
                          AND recipient_id = ?
                        ORDER BY id
                        """,
                customerId
        );
    }

    private int distinctBusinessKeyCountOf(long customerId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(DISTINCT business_key)
                        FROM tf_notification
                        WHERE recipient_type = 'CUSTOMER'
                          AND recipient_id = ?
                        """,
                Integer.class,
                customerId
        );

        return count == null ? 0 : count;
    }

    private int customerNotificationCountOf(long tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM tf_notification
                        WHERE tenant_id = ?
                          AND recipient_type = 'CUSTOMER'
                        """,
                Integer.class,
                tenantId
        );

        return count == null ? 0 : count;
    }

    private RequestPostProcessor customerToken(long actorId) {
        return token(1L, actorId, "customer@example.com", "CUSTOMER", null);
    }

    private RequestPostProcessor memberToken() {
        return token(1L, AGENT_ID, "agent-one", "MEMBER", "AGENT");
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

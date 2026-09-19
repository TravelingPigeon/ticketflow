package com.example.ticketflow.notification.service;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.notification.domain.Notification;
import com.example.ticketflow.notification.domain.enums.NotificationType;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.role.service.MemberRoleService;
import com.example.ticketflow.support.TestAuthorities;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.dto.AssignTicketRequest;
import com.example.ticketflow.ticket.dto.CreateTicketRequest;
import com.example.ticketflow.ticket.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 站内通知：发送、去重、隔离与已读。 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private MemberRoleService memberRoleService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long tenantId;

    private CurrentActor admin;

    private CurrentActor agent;

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

        tenantService.createTenant(
                new CreateTenantRequest("notify-tenant", "Notify Tenant")
        );

        tenantId = tenantIdOf("notify-tenant");

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

        memberRoleService.replaceMemberRoles(
                tenantId,
                1L,
                List.of(BuiltInRoles.ADMIN)
        );
        memberRoleService.replaceMemberRoles(
                tenantId,
                2L,
                List.of(BuiltInRoles.AGENT)
        );

        admin = actor(1L);
        agent = actor(2L);
    }

    @Test
    void shouldSendNotificationToMember() {
        notificationService.notifyMember(
                tenantId,
                2L,
                NotificationType.TICKET_ASSIGNED,
                null,
                "工单已分配给你",
                null,
                "ticket:assigned:1:member:2"
        );

        List<Notification> notifications = notificationService
                .listNotifications(agent, false);

        assertEquals(1, notifications.size());
        assertEquals("工单已分配给你", notifications.get(0).getTitle());
        assertEquals(NotificationType.TICKET_ASSIGNED, notifications.get(0).getType());
        assertEquals(ActorType.MEMBER, notifications.get(0).getRecipientType());
        assertNull(notifications.get(0).getReadAt());
    }

    @Test
    void shouldIgnoreDuplicateBusinessKey() {
        for (int i = 0; i < 3; i++) {
            notificationService.notifyMember(
                    tenantId,
                    2L,
                    NotificationType.TICKET_ASSIGNED,
                    null,
                    "工单已分配给你",
                    null,
                    "ticket:assigned:1:member:2"
            );
        }

        // 第二层去重：同一个 business_key 写多少次都只有一条
        assertEquals(1, notificationService.listNotifications(agent, false).size());
    }

    @Test
    void shouldNotSeeOtherMembersNotifications() {
        notificationService.notifyMember(
                tenantId,
                2L,
                NotificationType.TICKET_ASSIGNED,
                null,
                "给客服的通知",
                null,
                "ticket:assigned:1:member:2"
        );

        // 发给 2 号的通知，1 号看不到
        assertEquals(0, notificationService.listNotifications(admin, false).size());
    }

    @Test
    void shouldMarkNotificationAsRead() {
        notificationService.notifyMember(
                tenantId,
                2L,
                NotificationType.TICKET_ASSIGNED,
                null,
                "工单已分配给你",
                null,
                "ticket:assigned:1:member:2"
        );

        Long notificationId = notificationService
                .listNotifications(agent, true)
                .get(0)
                .getId();

        notificationService.markRead(agent, notificationId);

        Notification stored = notificationService
                .listNotifications(agent, false)
                .get(0);

        assertNotNull(stored.getReadAt());
        // 未读列表里就没有它了
        assertEquals(0, notificationService.listNotifications(agent, true).size());
    }

    @Test
    void shouldRejectMarkingAnotherMembersNotification() {
        notificationService.notifyMember(
                tenantId,
                2L,
                NotificationType.TICKET_ASSIGNED,
                null,
                "工单已分配给你",
                null,
                "ticket:assigned:1:member:2"
        );

        Long notificationId = notificationService
                .listNotifications(agent, false)
                .get(0)
                .getId();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> notificationService.markRead(admin, notificationId)
        );

        assertEquals("NOTIFICATION_NOT_FOUND", exception.getCode());

        // 而且真的没被改
        assertNull(
                notificationService.listNotifications(agent, false)
                        .get(0)
                        .getReadAt()
        );
    }

    @Test
    void shouldNotifyEveryActiveAdminButNotAgents() {
        notificationService.notifyAdmins(
                tenantId,
                NotificationType.SLA_BREACHED,
                null,
                "工单已超出 SLA",
                memberId -> "sla:breached:1:member:" + memberId
        );

        // 管理员收到
        assertEquals(1, notificationService.listNotifications(admin, false).size());
        // 客服不是管理员，收不到
        assertEquals(0, notificationService.listNotifications(agent, false).size());
    }

    @Test
    void shouldNotifyAssigneeWhenTicketIsAssigned() {
        Ticket ticket = ticketService.createTicket(
                admin,
                new CreateTicketRequest(
                        "NOTIFY-001",
                        "分配通知",
                        null,
                        TicketPriority.MEDIUM
                )
        );

        ticketService.assignTicket(
                admin,
                ticket.getId(),
                new AssignTicketRequest(2L)
        );

        List<Notification> notifications = notificationService
                .listNotifications(agent, false);

        assertEquals(1, notifications.size());
        assertEquals(NotificationType.TICKET_ASSIGNED, notifications.get(0).getType());
        assertEquals(ticket.getId(), notifications.get(0).getTicketId());

        // 再分配一次给同一个人：不会多出第二条
        ticketService.assignTicket(
                admin,
                ticket.getId(),
                new AssignTicketRequest(2L)
        );

        assertEquals(1, notificationService.listNotifications(agent, false).size());
    }

    @Test
    void shouldNotNotifyWhenAgentClaimsTicketBySelf() {
        Ticket ticket = ticketService.createTicket(
                admin,
                new CreateTicketRequest(
                        "NOTIFY-002",
                        "自己领取",
                        null,
                        TicketPriority.MEDIUM
                )
        );

        // 客服自己领单——自己做的事不用提醒自己
        ticketService.claimTicket(agent, ticket.getId());

        assertEquals(0, notificationService.listNotifications(agent, false).size());
    }

    private CurrentActor actor(long actorId) {
        String role = actorId == 1L ? BuiltInRoles.ADMIN : BuiltInRoles.AGENT;

        return new CurrentActor(
                tenantId,
                ActorType.MEMBER,
                actorId,
                actorId == 1L ? "admin-one" : "agent-one",
                TestAuthorities.permissionCodes(jdbcTemplate, role)
        );
    }

    private long tenantIdOf(String code) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_tenant WHERE code = ?",
                Long.class,
                code
        );

        return id == null ? -1L : id;
    }
}

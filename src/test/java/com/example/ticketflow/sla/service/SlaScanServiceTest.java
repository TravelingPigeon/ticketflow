package com.example.ticketflow.sla.service;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.notification.domain.Notification;
import com.example.ticketflow.notification.domain.enums.NotificationType;
import com.example.ticketflow.notification.service.NotificationService;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.role.service.MemberRoleService;
import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.sla.dto.SlaPolicyRequest;
import com.example.ticketflow.support.TestAuthorities;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
import com.example.ticketflow.ticket.comment.dto.CreateCommentRequest;
import com.example.ticketflow.ticket.comment.service.TicketCommentService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SLA 定时扫描。
 *
 * <p>这里直接调 {@link SlaScanService#scan()}，不等定时器——调度和逻辑分开的好处就在这。
 * 时间流逝用 SQL 把截止时间改到"过去"或"某分钟之后"来模拟，不需要真的等。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SlaScanServiceTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketCommentService ticketCommentService;

    @Autowired
    private SlaPolicyService slaPolicyService;

    @Autowired
    private SlaScanService slaScanService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MemberRoleService memberRoleService;

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
                new CreateTenantRequest("scan-tenant", "Scan Tenant")
        );

        tenantId = tenantIdOf("scan-tenant");

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

        // 动态 RBAC：ADMIN / AGENT 不是 tf_user 上的一列，要写进 tf_member_role
        // 才算数。selectActiveAdminIds 查的就是这张关联表。
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

        admin = actorOf(tenantId);
        agent = actorOf(tenantId, 2L, "agent-one", BuiltInRoles.AGENT);
    }

    @Test
    void shouldRemindTicketDueSoon() {
        long ticketId = createTicket(TicketPriority.HIGH);

        // HIGH 的提醒阈值是 30 分钟，把截止时间放到 10 分钟后 → 落在窗口内
        setResponseDue(ticketId, 10);

        SlaScanService.ScanSummary summary = slaScanService.scan();

        assertEquals(SlaStatus.REMINDED, responseStatusOf(ticketId));
        assertEquals(1, summary.reminded());
        assertEquals(0, summary.breached());
    }

    @Test
    void shouldNotRemindTicketOutsideTheWindow() {
        long ticketId = createTicket(TicketPriority.HIGH);

        // 60 分钟后到期，而 HIGH 的提醒阈值只有 30 分钟 → 还早，不动
        setResponseDue(ticketId, 60);

        slaScanService.scan();

        assertEquals(SlaStatus.NORMAL, responseStatusOf(ticketId));
    }

    @Test
    void shouldBreachOverdueTicket() {
        long ticketId = createTicket(TicketPriority.HIGH);

        setResponseDue(ticketId, -1);

        SlaScanService.ScanSummary summary = slaScanService.scan();

        assertEquals(SlaStatus.BREACHED, responseStatusOf(ticketId));
        assertEquals(1, summary.breached());
    }

    @Test
    void shouldNotTouchCompletedSla() {
        long ticketId = createTicket(TicketPriority.MEDIUM);

        // 及时回复 → 响应 SLA 完成
        ticketCommentService.createComment(
                admin,
                ticketId,
                new CreateCommentRequest("及时回复")
        );

        // 就算截止时间已经过去，已完成的不该被扫成超时
        setResponseDue(ticketId, -60);

        slaScanService.scan();

        assertEquals(SlaStatus.COMPLETED, responseStatusOf(ticketId));
    }

    @Test
    void shouldAdvanceResolutionSla() {
        long ticketId = createTicket(TicketPriority.HIGH);

        setResolutionDue(ticketId, 10);
        slaScanService.scan();
        assertEquals(SlaStatus.REMINDED, resolutionStatusOf(ticketId));

        setResolutionDue(ticketId, -1);
        slaScanService.scan();
        assertEquals(SlaStatus.BREACHED, resolutionStatusOf(ticketId));
    }

    @Test
    void shouldNotAdvanceTheSameTicketTwice() {
        long ticketId = createTicket(TicketPriority.HIGH);

        setResponseDue(ticketId, -1);

        assertEquals(1, slaScanService.scan().breached());

        // 第二次扫描时它已经是 BREACHED，条件更新匹配不到，所以不会重复推进
        assertEquals(0, slaScanService.scan().breached());
        assertEquals(SlaStatus.BREACHED, responseStatusOf(ticketId));
    }

    @Test
    void shouldUseEachTenantOwnPolicies() {
        tenantService.createTenant(
                new CreateTenantRequest("scan-tenant-2", "Scan Tenant 2")
        );

        long otherTenantId = tenantIdOf("scan-tenant-2");

        // 把 B 租户的 HIGH 提醒阈值从 30 分钟改成 5 分钟
        slaPolicyService.replacePolicies(
                otherTenantId,
                List.of(
                        new SlaPolicyRequest(TicketPriority.LOW, 480, 2880, 60),
                        new SlaPolicyRequest(TicketPriority.MEDIUM, 240, 1440, 60),
                        new SlaPolicyRequest(TicketPriority.HIGH, 60, 480, 5),
                        new SlaPolicyRequest(TicketPriority.URGENT, 30, 240, 15)
                )
        );

        long mine = createTicket(TicketPriority.HIGH);
        long theirs = createTicketFor(otherTenantId, TicketPriority.HIGH);

        // 两张单都是 10 分钟后到期，但两个租户的提醒阈值不同
        setResponseDue(mine, 10);
        setResponseDue(theirs, 10);

        slaScanService.scan();

        assertEquals(SlaStatus.REMINDED, responseStatusOf(mine));
        assertEquals(SlaStatus.NORMAL, responseStatusOf(theirs));
    }

    @Test
    void shouldOnlyScanActiveTenants() {
        long ticketId = createTicket(TicketPriority.HIGH);
        setResponseDue(ticketId, -1);

        jdbcTemplate.update(
                "UPDATE tf_tenant SET status = 'SUSPENDED' WHERE id = ?",
                tenantId
        );

        SlaScanService.ScanSummary summary = slaScanService.scan();

        // 停用的租户不在扫描范围内
        assertEquals(0, summary.tenants());
        assertEquals(SlaStatus.NORMAL, responseStatusOf(ticketId));
    }

    // ------------------------------------------------------------------
    // L2e-2：SLA 通知
    // ------------------------------------------------------------------

    @Test
    void shouldNotifyAssigneeWhenResponseSlaIsDueSoon() {
        long ticketId = createTicket(TicketPriority.HIGH);
        assignTo(ticketId, 2L);

        // HIGH 的提醒阈值是 30 分钟，10 分钟后到期 → 落在窗口内
        setResponseDue(ticketId, 10);

        slaScanService.scan();

        assertEquals(
                1,
                notificationsOf(agent, NotificationType.SLA_DUE_SOON).size()
        );
        // 有人负责时只催负责人，不再打扰管理员
        assertEquals(
                0,
                notificationsOf(admin, NotificationType.SLA_DUE_SOON).size()
        );
    }

    @Test
    void shouldNotifyAdminsWhenUnassignedTicketIsDueSoon() {
        long ticketId = createTicket(TicketPriority.HIGH);

        // 没有负责人 → 管理员兜底
        setResponseDue(ticketId, 10);

        slaScanService.scan();

        assertEquals(
                1,
                notificationsOf(admin, NotificationType.SLA_DUE_SOON).size()
        );
        assertEquals(
                0,
                notificationsOf(agent, NotificationType.SLA_DUE_SOON).size()
        );
    }

    @Test
    void shouldNotifyAssigneeAndAdminsWhenResponseSlaBreached() {
        long ticketId = createTicket(TicketPriority.HIGH);
        assignTo(ticketId, 2L);

        setResponseDue(ticketId, -1);

        slaScanService.scan();

        List<Notification> toAssignee =
                notificationsOf(agent, NotificationType.SLA_BREACHED);
        List<Notification> toAdmin =
                notificationsOf(admin, NotificationType.SLA_BREACHED);

        // 超时要让干活的人和管事的人都知道
        assertEquals(1, toAssignee.size());
        assertEquals(1, toAdmin.size());

        // 去重键格式固定：事件:标识:member:成员ID
        assertEquals(
                "sla:response:breached:" + ticketId + ":member:2",
                toAssignee.get(0).getBusinessKey()
        );
        assertEquals(ticketId, toAssignee.get(0).getTicketId());
    }

    @Test
    void shouldNotSendTheSameNotificationTwice() {
        long ticketId = createTicket(TicketPriority.HIGH);

        setResponseDue(ticketId, -1);

        slaScanService.scan();
        // 第二轮：状态已经是 BREACHED，条件更新匹配不到，不该再发一条
        slaScanService.scan();

        assertEquals(
                1,
                notificationsOf(admin, NotificationType.SLA_BREACHED).size()
        );
    }

    @Test
    void shouldSendOnlyOneNotificationWhenAssigneeIsAlsoAdmin() {
        long ticketId = createTicket(TicketPriority.HIGH);

        // 1 号既是负责人又是管理员：两条路径在同一个 business_key 上相遇，
        // 第二条被数据库唯一约束挡掉
        assignTo(ticketId, 1L);
        setResponseDue(ticketId, -1);

        slaScanService.scan();

        assertEquals(
                1,
                notificationsOf(admin, NotificationType.SLA_BREACHED).size()
        );
    }

    @Test
    void shouldNotifyOnResolutionSla() {
        long ticketId = createTicket(TicketPriority.HIGH);
        assignTo(ticketId, 2L);

        setResolutionDue(ticketId, 10);
        slaScanService.scan();
        assertEquals(
                1,
                notificationsOf(agent, NotificationType.SLA_DUE_SOON).size()
        );

        setResolutionDue(ticketId, -1);
        slaScanService.scan();
        assertEquals(
                1,
                notificationsOf(agent, NotificationType.SLA_BREACHED).size()
        );
        assertEquals(
                1,
                notificationsOf(admin, NotificationType.SLA_BREACHED).size()
        );
    }

    private long createTicket(TicketPriority priority) {
        return createTicketFor(tenantId, priority);
    }

    private long createTicketFor(long targetTenantId, TicketPriority priority) {
        Ticket ticket = ticketService.createTicket(
                actorOf(targetTenantId),
                new CreateTicketRequest(
                        "SCAN-" + System.nanoTime(),
                        "扫描测试",
                        null,
                        priority
                )
        );

        return ticket.getId();
    }

    private CurrentActor actorOf(long targetTenantId) {
        return actorOf(
                targetTenantId,
                1L,
                "admin-one",
                BuiltInRoles.ADMIN
        );
    }

    private CurrentActor actorOf(
            long targetTenantId,
            long memberId,
            String username,
            String roleCode
    ) {
        return new CurrentActor(
                targetTenantId,
                ActorType.MEMBER,
                memberId,
                username,
                TestAuthorities.permissionCodes(jdbcTemplate, roleCode)
        );
    }

    private void assignTo(long ticketId, long memberId) {
        ticketService.assignTicket(
                admin,
                ticketId,
                new AssignTicketRequest(memberId)
        );
    }

    /**
     * 从"这个人的通知列表"里挑出某一种类型。
     *
     * <p>必须按类型过滤：分配工单本身也会发通知，一张单上同时存在
     * 两种类型的通知是正常的。</p>
     */
    private List<Notification> notificationsOf(
            CurrentActor recipient,
            NotificationType type
    ) {
        return notificationService.listNotifications(recipient, false)
                .stream()
                .filter(notification -> notification.getType() == type)
                .toList();
    }

    private void setResponseDue(long ticketId, int minutesFromNow) {
        jdbcTemplate.update(
                """
                        UPDATE tf_ticket
                        SET first_response_due_at =
                                DATEADD('MINUTE', ?, CURRENT_TIMESTAMP)
                        WHERE id = ?
                        """,
                minutesFromNow,
                ticketId
        );
    }

    private void setResolutionDue(long ticketId, int minutesFromNow) {
        jdbcTemplate.update(
                """
                        UPDATE tf_ticket
                        SET resolution_due_at =
                                DATEADD('MINUTE', ?, CURRENT_TIMESTAMP)
                        WHERE id = ?
                        """,
                minutesFromNow,
                ticketId
        );
    }

    private SlaStatus responseStatusOf(long ticketId) {
        return SlaStatus.valueOf(
                jdbcTemplate.queryForObject(
                        "SELECT response_sla_status FROM tf_ticket WHERE id = ?",
                        String.class,
                        ticketId
                )
        );
    }

    private SlaStatus resolutionStatusOf(long ticketId) {
        return SlaStatus.valueOf(
                jdbcTemplate.queryForObject(
                        "SELECT resolution_sla_status FROM tf_ticket WHERE id = ?",
                        String.class,
                        ticketId
                )
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

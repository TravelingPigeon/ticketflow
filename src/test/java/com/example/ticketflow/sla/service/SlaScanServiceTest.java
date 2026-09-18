package com.example.ticketflow.sla.service;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.sla.dto.SlaPolicyRequest;
import com.example.ticketflow.support.TestAuthorities;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
import com.example.ticketflow.ticket.comment.dto.CreateCommentRequest;
import com.example.ticketflow.ticket.comment.service.TicketCommentService;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
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
    private JdbcTemplate jdbcTemplate;

    private long tenantId;

    private CurrentActor admin;

    @BeforeEach
    void setUp() {
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
                        VALUES (1, ?, 'admin-one', 'test-hash', 'Admin One', 'ACTIVE')
                        """,
                tenantId
        );

        admin = actorOf(tenantId);
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
        return new CurrentActor(
                targetTenantId,
                ActorType.MEMBER,
                1L,
                "admin-one",
                TestAuthorities.permissionCodes(jdbcTemplate, BuiltInRoles.ADMIN)
        );
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

package com.example.ticketflow.ticket.service;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.sla.dto.SlaPolicyRequest;
import com.example.ticketflow.sla.service.SlaPolicyService;
import com.example.ticketflow.support.TestAuthorities;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.dto.CreateTicketRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 建单时的 SLA 快照。
 *
 * <p>核心主张是"截止时间用快照、不实时算"：规则改了只影响之后的工单，
 * 已经建出来的工单截止时间原封不动。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketSlaSnapshotTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private SlaPolicyService slaPolicyService;

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
                new CreateTenantRequest("sla-snapshot", "SLA Snapshot")
        );

        tenantId = tenantIdOf("sla-snapshot");

        jdbcTemplate.update(
                """
                        INSERT INTO tf_user
                            (id, tenant_id, username, password_hash, display_name, status)
                        VALUES (1, ?, 'admin-one', 'test-hash', 'Admin One', 'ACTIVE')
                        """,
                tenantId
        );

        admin = new CurrentActor(
                tenantId,
                ActorType.MEMBER,
                1L,
                "admin-one",
                TestAuthorities.permissionCodes(jdbcTemplate, BuiltInRoles.ADMIN)
        );
    }

    @Test
    void shouldSnapshotDueDatesFromPolicyAtCreation() {
        Ticket ticket = createTicket("SLA-001", TicketPriority.HIGH);

        // HIGH 的默认规则是 60 / 480 分钟，而且都是从 created_at 起算
        assertEquals(
                ticket.getCreatedAt().plusMinutes(60),
                ticket.getFirstResponseDueAt()
        );
        assertEquals(
                ticket.getCreatedAt().plusMinutes(480),
                ticket.getResolutionDueAt()
        );
        assertEquals(SlaStatus.NORMAL, ticket.getResponseSlaStatus());
        assertEquals(SlaStatus.NORMAL, ticket.getResolutionSlaStatus());

        // 事件时间还不知道，必须是空
        assertEquals(null, ticket.getFirstRespondedAt());
        assertEquals(null, ticket.getResolvedAt());
    }

    @Test
    void shouldUseDifferentRulesForDifferentPriorities() {
        Ticket urgent = createTicket("SLA-URGENT", TicketPriority.URGENT);
        Ticket low = createTicket("SLA-LOW", TicketPriority.LOW);

        assertEquals(
                urgent.getCreatedAt().plusMinutes(30),
                urgent.getFirstResponseDueAt()
        );
        assertEquals(
                low.getCreatedAt().plusMinutes(480),
                low.getFirstResponseDueAt()
        );
    }

    @Test
    void shouldNotChangeExistingTicketsWhenPolicyChanges() {
        Ticket before = createTicket("SLA-002", TicketPriority.HIGH);

        // 把 HIGH 改成 45 / 300
        slaPolicyService.replacePolicies(
                tenantId,
                List.of(
                        new SlaPolicyRequest(TicketPriority.LOW, 480, 2880, 60),
                        new SlaPolicyRequest(TicketPriority.MEDIUM, 240, 1440, 60),
                        new SlaPolicyRequest(TicketPriority.HIGH, 45, 300, 20),
                        new SlaPolicyRequest(TicketPriority.URGENT, 30, 240, 15)
                )
        );

        Ticket after = createTicket("SLA-003", TicketPriority.HIGH);

        // 新工单用新规则
        assertEquals(
                after.getCreatedAt().plusMinutes(45),
                after.getFirstResponseDueAt()
        );

        // 老工单原封不动 —— 这就是"快照"的意义
        Ticket reloaded = ticketService.findTicket(admin, before.getId());

        assertEquals(before.getFirstResponseDueAt(), reloaded.getFirstResponseDueAt());
        assertEquals(
                reloaded.getCreatedAt().plusMinutes(60),
                reloaded.getFirstResponseDueAt()
        );
    }

    @Test
    void shouldRejectTicketCreationWhenPolicyIsMissing() {
        // 直接把 HIGH 的规则删掉，模拟"管理员没配全"
        jdbcTemplate.update(
                """
                        DELETE FROM tf_sla_policy
                        WHERE tenant_id = ?
                          AND priority = 'HIGH'
                        """,
                tenantId
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> createTicket("SLA-004", TicketPriority.HIGH)
        );

        assertEquals("SLA_POLICY_NOT_FOUND", exception.getCode());

        // 规则缺失时整单失败，不能留下一个算不出截止时间的工单
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tf_ticket WHERE ticket_no = ?",
                Integer.class,
                "SLA-004"
        );

        assertEquals(0, count);
    }

    @Test
    void shouldSnapshotForCustomerTicketToo() {
        CurrentActor customer = new CurrentActor(
                tenantId,
                ActorType.CUSTOMER,
                1L,
                "customer@example.com",
                java.util.Set.of()
        );

        Ticket ticket = ticketService.createTicket(
                customer,
                new CreateTicketRequest("SLA-CUST-001", "客户提单", null, TicketPriority.URGENT)
        );

        // 客户提单走的是同一条建单路径，SLA 快照一样生效
        assertEquals(
                ticket.getCreatedAt().plusMinutes(30),
                ticket.getFirstResponseDueAt()
        );
        assertEquals(null, ticket.getCreatedBy());
        assertEquals(1L, ticket.getCustomerId());
    }

    private Ticket createTicket(String ticketNo, TicketPriority priority) {
        return ticketService.createTicket(
                admin,
                new CreateTicketRequest(ticketNo, "SLA 测试", null, priority)
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

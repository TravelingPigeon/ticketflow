package com.example.ticketflow.ticket.service;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.support.TestAuthorities;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
import com.example.ticketflow.ticket.comment.dto.CreateCommentRequest;
import com.example.ticketflow.ticket.comment.service.TicketCommentService;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;
import com.example.ticketflow.ticket.dto.CreateTicketRequest;
import com.example.ticketflow.ticket.dto.UpdateTicketStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SLA 的完成条件：首次响应与解决。
 *
 * <p>规则见 docs/06 §10：**尚未超时**才算完成；已经违约的保留违约状态、只补时间。
 * 重开后再解决不覆盖原时间，也不重开新的 SLA 周期。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketSlaCompletionTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketCommentService ticketCommentService;

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
                new CreateTenantRequest("sla-completion", "SLA Completion")
        );

        tenantId = tenantIdOf("sla-completion");

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
    void shouldMarkResponseCompletedOnFirstMemberComment() {
        long ticketId = createTicket("SLA-C1", TicketPriority.MEDIUM);

        Ticket before = reload(ticketId);
        assertNull(before.getFirstRespondedAt());
        assertEquals(SlaStatus.NORMAL, before.getResponseSlaStatus());

        comment(ticketId, "第一次回复");

        Ticket after = reload(ticketId);

        assertNotNull(after.getFirstRespondedAt());
        assertEquals(SlaStatus.COMPLETED, after.getResponseSlaStatus());
        // 解决时限那一侧还没完成
        assertEquals(SlaStatus.NORMAL, after.getResolutionSlaStatus());
    }

    @Test
    void shouldKeepFirstResponseTimeOnLaterComments() {
        long ticketId = createTicket("SLA-C2", TicketPriority.MEDIUM);

        comment(ticketId, "第一次回复");
        Ticket first = reload(ticketId);

        comment(ticketId, "第二次回复");
        comment(ticketId, "第三次回复");
        Ticket latest = reload(ticketId);

        // 首次响应只记第一次
        assertEquals(first.getFirstRespondedAt(), latest.getFirstRespondedAt());
    }

    @Test
    void shouldKeepBreachedResponseStatusButStillRecordTime() {
        long ticketId = createTicket("SLA-C3", TicketPriority.MEDIUM);

        // 模拟 L2d 的扫描已经把它判成违约
        markResponseBreached(ticketId);

        comment(ticketId, "超时之后才回复");

        Ticket after = reload(ticketId);

        // 超时了就是超时了，事后回复洗不掉违约记录
        assertEquals(SlaStatus.BREACHED, after.getResponseSlaStatus());
        // 但实际回复时间还是要如实记下来
        assertNotNull(after.getFirstRespondedAt());
    }

    @Test
    void shouldMarkResolutionCompletedWhenResolved() {
        long ticketId = createTicket("SLA-C4", TicketPriority.HIGH);

        changeStatus(ticketId, TicketStatus.PROCESSING);
        changeStatus(ticketId, TicketStatus.RESOLVED);

        Ticket after = reload(ticketId);

        assertNotNull(after.getResolvedAt());
        assertEquals(SlaStatus.COMPLETED, after.getResolutionSlaStatus());
    }

    @Test
    void shouldKeepBreachedResolutionStatusButStillRecordTime() {
        long ticketId = createTicket("SLA-C5", TicketPriority.HIGH);

        changeStatus(ticketId, TicketStatus.PROCESSING);
        markResolutionBreached(ticketId);

        changeStatus(ticketId, TicketStatus.RESOLVED);

        Ticket after = reload(ticketId);

        assertEquals(SlaStatus.BREACHED, after.getResolutionSlaStatus());
        assertNotNull(after.getResolvedAt());
    }

    @Test
    void shouldNotRestartSlaWhenReopenedAndResolvedAgain() {
        long ticketId = createTicket("SLA-C6", TicketPriority.HIGH);

        changeStatus(ticketId, TicketStatus.PROCESSING);
        changeStatus(ticketId, TicketStatus.RESOLVED);

        Ticket firstResolved = reload(ticketId);

        // 重开再解决一次
        changeStatus(ticketId, TicketStatus.PROCESSING);
        changeStatus(ticketId, TicketStatus.RESOLVED);

        Ticket secondResolved = reload(ticketId);

        // 解决时间不被覆盖，状态也还是完成——当前版本不给重开单开新的 SLA 周期
        assertEquals(
                firstResolved.getResolvedAt(),
                secondResolved.getResolvedAt()
        );
        assertEquals(
                SlaStatus.COMPLETED,
                secondResolved.getResolutionSlaStatus()
        );
    }

    private long createTicket(String ticketNo, TicketPriority priority) {
        Ticket ticket = ticketService.createTicket(
                admin,
                new CreateTicketRequest(ticketNo, "SLA 完成测试", null, priority)
        );

        return ticket.getId();
    }

    private void comment(long ticketId, String content) {
        ticketCommentService.createComment(
                admin,
                ticketId,
                new CreateCommentRequest(content)
        );
    }

    private void changeStatus(long ticketId, TicketStatus status) {
        ticketService.updateStatus(
                admin,
                ticketId,
                new UpdateTicketStatusRequest(status)
        );
    }

    private Ticket reload(long ticketId) {
        return ticketService.findTicket(admin, ticketId);
    }

    private void markResponseBreached(long ticketId) {
        jdbcTemplate.update(
                "UPDATE tf_ticket SET response_sla_status = 'BREACHED' WHERE id = ?",
                ticketId
        );
    }

    private void markResolutionBreached(long ticketId) {
        jdbcTemplate.update(
                "UPDATE tf_ticket SET resolution_sla_status = 'BREACHED' WHERE id = ?",
                ticketId
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

package com.example.ticketflow.ticket.service;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.ticket.domain.TicketOperation;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;
import com.example.ticketflow.ticket.dto.UpdateTicketStatusRequest;
import com.example.ticketflow.ticket.dto.UpdateTicketRequest;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.mapper.TicketOperationMapper;
import com.example.ticketflow.user.domain.enums.UserRole;
import com.example.ticketflow.support.TestAuthorities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证"工单修改"与"审计记录"处在同一个事务里。
 *
 * <p>做法是把审计 Mapper 换成 mock：让它抛出异常来模拟"审计写入失败"，
 * 然后检查工单的修改是否被一起回滚。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketOperationTransactionTest {

    @MockitoBean
    private TicketOperationMapper ticketOperationMapper;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tf_ticket_operation");
        jdbcTemplate.update("DELETE FROM tf_ticket_comment");
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_customer");
        jdbcTemplate.update("DELETE FROM tf_member_role");
        jdbcTemplate.update("DELETE FROM tf_role_permission");
        jdbcTemplate.update("DELETE FROM tf_role");
        jdbcTemplate.update("DELETE FROM tf_user");
        jdbcTemplate.update("DELETE FROM tf_tenant");

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (1, 'tx-tenant', 'Tx Tenant', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_user
                    (id, tenant_id, username, password_hash, display_name, role, status)
                VALUES
                    (4, 1, 'admin-one', 'test-hash', 'Admin One', 'ADMIN', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_ticket
                    (id, tenant_id, ticket_no, title, status, priority, version)
                VALUES
                    (1, 1, 'TX-001', 'Transaction test', 'OPEN', 'MEDIUM', 0)
                """);
    }

    @Test
    void shouldUpdateTicketWhenAuditWriteSucceeds() {
        Mockito.when(ticketOperationMapper.insert(
                Mockito.any(TicketOperation.class)
        ))
                .thenReturn(1);

        ticketService.updateStatus(
                admin(),
                1L,
                new UpdateTicketStatusRequest(TicketStatus.PROCESSING)
        );

        assertEquals("PROCESSING", statusOf(1L));
    }

    @Test
    void shouldRollBackTicketUpdateWhenAuditWriteFails() {
        Mockito.when(ticketOperationMapper.insert(
                Mockito.any(TicketOperation.class)
        ))
                .thenThrow(new IllegalStateException("审计写入失败"));

        assertThrows(
                IllegalStateException.class,
                () -> ticketService.updateStatus(
                        admin(),
                        1L,
                        new UpdateTicketStatusRequest(
                                TicketStatus.PROCESSING
                        )
                )
        );

        // 关键断言：审计没写成功，工单的状态也不能留下改动
        assertEquals("OPEN", statusOf(1L));
    }

    @Test
    void shouldRollBackTicketEditWhenAuditWriteFails() {
        Mockito.when(ticketOperationMapper.insert(
                Mockito.any(TicketOperation.class)
        ))
                .thenThrow(new IllegalStateException("审计写入失败"));

        assertThrows(
                IllegalStateException.class,
                () -> ticketService.updateTicket(
                        admin(),
                        1L,
                        new UpdateTicketRequest(
                                "改过的标题",
                                null,
                                TicketPriority.HIGH
                        )
                )
        );

        // 编辑路径走的是同一个事务，标题同样不能留下改动
        assertEquals("Transaction test", titleOf(1L));
    }

    private CurrentActor admin() {
        return new CurrentActor(
                1L,
                ActorType.MEMBER,
                4L,
                "admin-one",
                TestAuthorities.permissionCodes(
                        jdbcTemplate,
                        UserRole.ADMIN.name()
                )
        );
    }

    private String statusOf(long ticketId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM tf_ticket WHERE id = ?",
                String.class,
                ticketId
        );
    }

    private String titleOf(long ticketId) {
        return jdbcTemplate.queryForObject(
                "SELECT title FROM tf_ticket WHERE id = ?",
                String.class,
                ticketId
        );
    }
}

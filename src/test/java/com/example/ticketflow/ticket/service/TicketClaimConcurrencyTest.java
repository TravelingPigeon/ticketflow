package com.example.ticketflow.ticket.service;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.mapper.TicketMapper;
import com.example.ticketflow.support.TestAuthorities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 领取工单的并发行为。
 *
 * <p>这里不经过 HTTP 层，而是直接调用 Service：这样可以让两个线程真正同时进入业务逻辑，
 * 模拟"两个客服同时点领取"的场景——这是 MockMvc 那种串行请求造不出来的时序。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketClaimConcurrencyTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketMapper ticketMapper;

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
        jdbcTemplate.update("DELETE FROM tf_sla_policy");
        jdbcTemplate.update("DELETE FROM tf_user");
        jdbcTemplate.update("DELETE FROM tf_tenant");

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (1, 'race-tenant', 'Race Tenant', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_user
                    (id, tenant_id, username, password_hash, display_name, status)
                VALUES
                    (1, 1, 'agent-one', 'test-hash', 'Agent One', 'ACTIVE'),
                    (5, 1, 'agent-beta', 'test-hash', 'Agent Beta', 'ACTIVE')
                """);
    }

    @Test
    void shouldAllowOnlyOneSuccessfulClaim() throws Exception {
        long ticketId = createOpenTicket("RACE-001");

        List<CurrentActor> competitors = List.of(
                actor(1L, "agent-one"),
                actor(5L, "agent-beta")
        );

        // CountDownLatch 让两个线程尽可能在同一时刻进入业务逻辑，
        // 从而制造出"都读到了同一个版本号"的时序。
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(competitors.size());

        long successCount;

        try {
            List<Future<Boolean>> results = new ArrayList<>();

            for (CurrentActor actor : competitors) {
                results.add(pool.submit(() -> {
                    start.await();

                    try {
                        ticketService.claimTicket(actor, ticketId);
                        return true;
                    } catch (BusinessException exception) {
                        return false;
                    }
                }));
            }

            start.countDown();

            successCount = 0;

            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successCount++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // 业务不变量：无论时序如何，只能有一个人领取成功
        assertEquals(1, successCount);

        Long assigneeId = jdbcTemplate.queryForObject(
                "SELECT assignee_id FROM tf_ticket WHERE id = ?",
                Long.class,
                ticketId
        );

        assertNotNull(assigneeId);
    }

    @Test
    void shouldRejectUpdateWithStaleVersion() {
        long ticketId = createOpenTicket("RACE-002");

        Ticket staleTicket = ticketMapper.selectById(ticketId);

        // 模拟"另一个事务改过这张工单"：改数据的同时把版本号 +1
        jdbcTemplate.update(
                """
                        UPDATE tf_ticket
                        SET title = ?, version = version + 1
                        WHERE id = ?
                        """,
                "Changed by someone else",
                ticketId
        );

        staleTicket.setTitle("Updated with a stale entity");

        int updatedRows = ticketMapper.updateById(staleTicket);

        // 版本号已经变了，这条更新匹配不到任何行
        assertEquals(0, updatedRows);
    }

    @Test
    void shouldIncreaseVersionOnClaim() {
        long ticketId = createOpenTicket("RACE-003");

        assertEquals(0L, versionOf(ticketId));

        ticketService.claimTicket(actor(1L, "agent-one"), ticketId);

        assertEquals(1L, versionOf(ticketId));
    }

    private CurrentActor actor(long actorId, String name) {
        return new CurrentActor(
                1L,
                ActorType.MEMBER,
                actorId,
                name,
                TestAuthorities.permissionCodes(
                        jdbcTemplate,
                        BuiltInRoles.AGENT
                )
        );
    }

    private long createOpenTicket(String ticketNo) {
        jdbcTemplate.update(
                """
                        INSERT INTO tf_ticket
                (tenant_id, ticket_no, title, status, priority, version,
                 first_response_due_at, resolution_due_at)
                VALUES (1, ?, 'Race test', 'OPEN', 'MEDIUM', 0,
                        CURRENT_TIMESTAMP, DATEADD('HOUR', 8, CURRENT_TIMESTAMP))
                        """,
                ticketNo
        );

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_ticket WHERE ticket_no = ?",
                Long.class,
                ticketNo
        );

        return id == null ? -1L : id;
    }

    private long versionOf(long ticketId) {
        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM tf_ticket WHERE id = ?",
                Long.class,
                ticketId
        );

        return version == null ? -1L : version;
    }
}

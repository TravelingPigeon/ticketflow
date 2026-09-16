package com.example.ticketflow.ticket.service;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.TicketOperation;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;
import com.example.ticketflow.ticket.dto.AssignTicketRequest;
import com.example.ticketflow.ticket.dto.CreateTicketRequest;
import com.example.ticketflow.ticket.dto.TicketDetailResponse;
import com.example.ticketflow.ticket.dto.UpdateTicketStatusRequest;
import com.example.ticketflow.ticket.dto.UpdateTicketRequest;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.support.TestAuthorities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class TicketOperationAuditTest {

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
                VALUES (1, 'audit-tenant', 'Audit Tenant', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES (2, 'audit-tenant-2', 'Audit Tenant 2', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_user
                    (id, tenant_id, username, password_hash, display_name, role, status)
                VALUES
                    (1, 1, 'agent-one', 'test-hash', 'Agent One', 'AGENT', 'ACTIVE'),
                    (4, 1, 'admin-one', 'test-hash', 'Admin One', 'ADMIN', 'ACTIVE'),
                    (5, 1, 'agent-beta', 'test-hash', 'Agent Beta', 'AGENT', 'ACTIVE')
                """);

        jdbcTemplate.update("""
                INSERT INTO tf_customer
                    (id, tenant_id, email, password_hash, display_name, status)
                VALUES
                    (1, 1, 'customer@example.com', 'test-hash', 'Customer', 'ACTIVE')
                """);
    }

    @Test
    void shouldRecordMemberTicketCreation() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-001"
        );

        assertEquals(1, countOperations(ticketId, "CREATED"));

        Map<String, Object> operation = lastOperation(ticketId, "CREATED");

        assertEquals("MEMBER", operation.get("operatorType"));
        assertEquals(4L, ((Number) operation.get("operatorId")).longValue());
        assertNull(operation.get("fromValue"));
        assertEquals("OPEN", operation.get("toValue"));
    }

    @Test
    void shouldRecordCustomerTicketCreation() {
        long ticketId = createOpenTicket(
                customer(1L),
                "AUDIT-002"
        );

        Map<String, Object> operation = lastOperation(ticketId, "CREATED");

        assertEquals("CUSTOMER", operation.get("operatorType"));
        assertEquals(1L, ((Number) operation.get("operatorId")).longValue());
        assertEquals("OPEN", operation.get("toValue"));
    }

    @Test
    void shouldRecordClaimWithCascadedStatusChange() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-003"
        );

        ticketService.claimTicket(member(5L, "agent-beta", BuiltInRoles.AGENT), ticketId);

        Map<String, Object> claimed = lastOperation(ticketId, "CLAIMED");

        assertEquals(5L, ((Number) claimed.get("operatorId")).longValue());
        assertNull(claimed.get("fromValue"));
        assertEquals("5", claimed.get("toValue"));

        // 分配/领取会连带把 OPEN 推进到 PROCESSING，这条变化也要如实记录
        Map<String, Object> statusChange = lastOperation(
                ticketId,
                "STATUS_CHANGED"
        );

        assertEquals("OPEN", statusChange.get("fromValue"));
        assertEquals("PROCESSING", statusChange.get("toValue"));
    }

    @Test
    void shouldRecordAssignmentWithPreviousAssignee() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-004"
        );

        ticketService.assignTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                ticketId,
                new AssignTicketRequest(1L)
        );

        ticketService.assignTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                ticketId,
                new AssignTicketRequest(5L)
        );

        Map<String, Object> latest = lastOperation(ticketId, "ASSIGNED");

        // 第二次分配时，"变更前"应该是第一位负责人，而不是 null
        assertEquals("1", latest.get("fromValue"));
        assertEquals("5", latest.get("toValue"));
    }

    @Test
    void shouldRecordStatusChangeWithPreviousStatus() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-005"
        );

        CurrentActor admin = member(4L, "admin-one", BuiltInRoles.ADMIN);

        ticketService.updateStatus(
                admin,
                ticketId,
                new UpdateTicketStatusRequest(TicketStatus.PROCESSING)
        );

        ticketService.updateStatus(
                admin,
                ticketId,
                new UpdateTicketStatusRequest(TicketStatus.WAITING_CUSTOMER)
        );

        ticketService.updateStatus(
                admin,
                ticketId,
                new UpdateTicketStatusRequest(TicketStatus.RESOLVED)
        );

        Map<String, Object> latest = lastOperation(
                ticketId,
                "STATUS_CHANGED"
        );

        assertEquals(3, countOperations(ticketId, "STATUS_CHANGED"));
        assertEquals("WAITING_CUSTOMER", latest.get("fromValue"));
        assertEquals("RESOLVED", latest.get("toValue"));
    }

    @Test
    void shouldListOperationsInIdOrder() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-006"
        );

        ticketService.claimTicket(member(5L, "agent-beta", BuiltInRoles.AGENT), ticketId);

        List<TicketOperation> operations = ticketService.listOperations(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                ticketId
        );

        assertEquals(3, operations.size());
        assertEquals("CREATED", operations.get(0).getOperationType().name());
        assertEquals("CLAIMED", operations.get(1).getOperationType().name());
        assertEquals(
                "STATUS_CHANGED",
                operations.get(2).getOperationType().name()
        );
    }

    @Test
    void shouldRejectListingOperationsOfAnotherTenant() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-007"
        );

        CurrentActor otherTenantAdmin = new CurrentActor(
                2L,
                ActorType.MEMBER,
                99L,
                "admin-two",
                TestAuthorities.permissionCodes(
                        jdbcTemplate,
                        BuiltInRoles.ADMIN
                )
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketService.listOperations(
                        otherTenantAdmin,
                        ticketId
                )
        );

        assertEquals("TICKET_NOT_FOUND", exception.getCode());
    }

    @Test
    void shouldRecordUpdatedWithChangedFieldNames() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-008"
        );

        ticketService.updateTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                ticketId,
                new UpdateTicketRequest(
                        "新的标题",
                        null,
                        TicketPriority.HIGH
                )
        );

        Map<String, Object> operation = lastOperation(ticketId, "UPDATED");

        // 标题和优先级变了，描述没变，所以只记这两个字段名
        assertNull(operation.get("fromValue"));
        assertEquals("title,priority", operation.get("toValue"));
        assertEquals("MEMBER", operation.get("operatorType"));
        assertEquals(4L, ((Number) operation.get("operatorId")).longValue());
    }

    @Test
    void shouldRecordOnlyTheFieldThatActuallyChanged() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-009"
        );

        CurrentActor admin = member(4L, "admin-one", BuiltInRoles.ADMIN);

        // 建单时描述是 null，这次只补描述，标题和优先级保持原样
        ticketService.updateTicket(
                admin,
                ticketId,
                new UpdateTicketRequest(
                        "Audit test",
                        "补充说明",
                        TicketPriority.MEDIUM
                )
        );

        assertEquals(
                "description",
                lastOperation(ticketId, "UPDATED").get("toValue")
        );
    }

    @Test
    void shouldNotRecordUpdatedWhenNothingChanged() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-010"
        );

        // 原样提交：标题、描述、优先级都和当前值一致
        ticketService.updateTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                ticketId,
                new UpdateTicketRequest(
                        "Audit test",
                        null,
                        TicketPriority.MEDIUM
                )
        );

        // 幂等保存不该在时间线里刷噪音
        assertEquals(0, countOperations(ticketId, "UPDATED"));
        assertEquals(1, countAllOperations(ticketId));
    }

    @Test
    void shouldNotRecordStatusChangeWhenOnlyContentChanges() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-011"
        );

        ticketService.updateTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                ticketId,
                new UpdateTicketRequest(
                        "只改内容",
                        null,
                        TicketPriority.HIGH
                )
        );

        // 编辑只动内容，不碰生命周期状态
        assertEquals(0, countOperations(ticketId, "STATUS_CHANGED"));
        assertEquals(2, countAllOperations(ticketId));
        assertEquals("OPEN", statusOf(ticketId));
    }

    @Test
    void shouldMapAllTicketFieldsIntoDetailResponse() {
        Ticket created = ticketService.createTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                new CreateTicketRequest(
                        "AUDIT-012",
                        "映射检查",
                        "描述",
                        TicketPriority.HIGH
                )
        );

        long ticketId = created.getId();

        ticketService.claimTicket(
                member(5L, "agent-beta", BuiltInRoles.AGENT),
                ticketId
        );

        TicketDetailResponse detail = ticketService.findTicketDetail(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                ticketId
        );

        // createdBy / customerId / assigneeId 都是 Long，最容易在 of() 里写串行
        assertEquals(ticketId, detail.id());
        assertEquals(1L, detail.tenantId());
        assertEquals("AUDIT-012", detail.ticketNo());
        assertEquals("映射检查", detail.title());
        assertEquals("描述", detail.description());
        assertEquals(TicketStatus.PROCESSING, detail.status());
        assertEquals(TicketPriority.HIGH, detail.priority());
        assertEquals(4L, detail.createdBy());
        assertEquals(5L, detail.assigneeId());
        assertNull(detail.customerId());
        assertNotNull(detail.createdAt());
    }

    @Test
    void shouldReturnSameTimelineFromDetailAndOperationsLookup() {
        long ticketId = createOpenTicket(
                member(4L, "admin-one", BuiltInRoles.ADMIN),
                "AUDIT-013"
        );

        ticketService.claimTicket(
                member(5L, "agent-beta", BuiltInRoles.AGENT),
                ticketId
        );

        CurrentActor admin = member(4L, "admin-one", BuiltInRoles.ADMIN);

        TicketDetailResponse detail = ticketService.findTicketDetail(
                admin,
                ticketId
        );

        List<TicketOperation> viaEndpoint = ticketService.listOperations(
                admin,
                ticketId
        );

        // 两个入口共用同一段查询，条数和顺序都不该有偏差
        assertEquals(3, detail.operations().size());
        assertEquals(
                viaEndpoint.stream().map(TicketOperation::getId).toList(),
                detail.operations().stream().map(TicketOperation::getId).toList()
        );
    }

    private long createOpenTicket(CurrentActor actor, String ticketNo) {
        Ticket ticket = ticketService.createTicket(
                actor,
                new CreateTicketRequest(
                        ticketNo,
                        "Audit test",
                        null,
                        null
                )
        );

        return ticket.getId();
    }

    private CurrentActor member(
            long actorId,
            String name,
            String roleCode
    ) {
        return new CurrentActor(
                1L,
                ActorType.MEMBER,
                actorId,
                name,
                TestAuthorities.permissionCodes(jdbcTemplate, roleCode)
        );
    }

    private CurrentActor customer(long actorId) {
        return new CurrentActor(
                1L,
                ActorType.CUSTOMER,
                actorId,
                "customer@example.com",
                Set.of()
        );
    }

    private int countOperations(long ticketId, String operationType) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM tf_ticket_operation
                        WHERE ticket_id = ?
                          AND operation_type = ?
                        """,
                Integer.class,
                ticketId,
                operationType
        );

        return count == null ? 0 : count;
    }

    private int countAllOperations(long ticketId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM tf_ticket_operation
                        WHERE ticket_id = ?
                        """,
                Integer.class,
                ticketId
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

    private Map<String, Object> lastOperation(
            long ticketId,
            String operationType
    ) {
        return jdbcTemplate.queryForMap(
                """
                        SELECT operator_type AS "operatorType",
                               operator_id   AS "operatorId",
                               from_value    AS "fromValue",
                               to_value      AS "toValue"
                        FROM tf_ticket_operation
                        WHERE ticket_id = ?
                          AND operation_type = ?
                        ORDER BY id DESC
                        LIMIT 1
                        """,
                ticketId,
                operationType
        );
    }
}

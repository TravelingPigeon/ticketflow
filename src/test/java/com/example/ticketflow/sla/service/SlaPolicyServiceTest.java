package com.example.ticketflow.sla.service;

import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.sla.dto.SlaPolicyRequest;
import com.example.ticketflow.sla.dto.SlaPolicyResponse;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SLA 规则：默认值、全量替换与校验规则。 */
@SpringBootTest
@ActiveProfiles("test")
class SlaPolicyServiceTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private SlaPolicyService slaPolicyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long tenantId;

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
                new CreateTenantRequest("sla-tenant", "SLA Tenant")
        );

        tenantId = tenantIdOf("sla-tenant");
    }

    @Test
    void shouldCreateDefaultPoliciesForNewTenant() {
        List<SlaPolicyResponse> policies = slaPolicyService.listPolicies(tenantId);

        assertEquals(4, policies.size());
        assertEquals(
                new TreeSet<>(
                        List.of(
                                TicketPriority.LOW,
                                TicketPriority.MEDIUM,
                                TicketPriority.HIGH,
                                TicketPriority.URGENT
                        )
                ),
                new TreeSet<>(
                        policies.stream()
                                .map(SlaPolicyResponse::priority)
                                .toList()
                )
        );

        SlaPolicyResponse low = policyOf(TicketPriority.LOW);
        assertEquals(480, low.firstResponseMinutes());
        assertEquals(2880, low.resolutionMinutes());
        assertEquals(60, low.remindBeforeMinutes());
        assertTrue(low.enabled());

        SlaPolicyResponse high = policyOf(TicketPriority.HIGH);
        assertEquals(60, high.firstResponseMinutes());
        assertEquals(480, high.resolutionMinutes());
        assertEquals(30, high.remindBeforeMinutes());

        SlaPolicyResponse urgent = policyOf(TicketPriority.URGENT);
        assertEquals(30, urgent.firstResponseMinutes());
        assertEquals(240, urgent.resolutionMinutes());
        assertEquals(15, urgent.remindBeforeMinutes());
    }

    @Test
    void shouldReplaceAllPolicies() {
        slaPolicyService.replacePolicies(
                tenantId,
                List.of(
                        request(TicketPriority.LOW, 600, 3000, 120),
                        request(TicketPriority.MEDIUM, 300, 1500, 60),
                        request(TicketPriority.HIGH, 45, 300, 20),
                        request(TicketPriority.URGENT, 15, 120, 10)
                )
        );

        assertEquals(45, policyOf(TicketPriority.HIGH).firstResponseMinutes());
        assertEquals(300, policyOf(TicketPriority.HIGH).resolutionMinutes());
        assertEquals(20, policyOf(TicketPriority.HIGH).remindBeforeMinutes());
        assertEquals(600, policyOf(TicketPriority.LOW).firstResponseMinutes());
        assertEquals(4, slaPolicyService.listPolicies(tenantId).size());
    }

    @Test
    void shouldRejectIncompleteRuleSetAndKeepExistingPolicies() {
        // 只提交 LOW 和 HIGH，缺 MEDIUM
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> slaPolicyService.replacePolicies(
                        tenantId,
                        List.of(
                                request(TicketPriority.LOW, 480, 2880, 60),
                                request(TicketPriority.HIGH, 60, 480, 30)
                        )
                )
        );

        assertEquals("INVALID_SLA_POLICY", exception.getCode());
        // 报错要说清缺的是哪一个，否则调用方只能一个个试
        assertTrue(exception.getMessage().contains("MEDIUM"));

        // 校验发生在删除之前，所以原有的四条规则必须原封不动
        assertEquals(4, slaPolicyService.listPolicies(tenantId).size());
        assertEquals(480, policyOf(TicketPriority.LOW).firstResponseMinutes());
    }

    @Test
    void shouldRejectDuplicatePriority() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> slaPolicyService.replacePolicies(
                        tenantId,
                        List.of(
                                request(TicketPriority.LOW, 480, 2880, 60),
                                request(TicketPriority.LOW, 100, 200, 10),
                                request(TicketPriority.MEDIUM, 240, 1440, 60),
                                request(TicketPriority.HIGH, 60, 480, 30),
                                request(TicketPriority.URGENT, 30, 240, 15)
                        )
                )
        );

        assertEquals("INVALID_SLA_POLICY", exception.getCode());
        assertEquals(4, slaPolicyService.listPolicies(tenantId).size());
    }

    @Test
    void shouldRejectRemindBeforeNotSmallerThanDurations() {
        // 提前提醒 60 分钟，而首次响应时限也是 60 分钟——提醒和超时同时发生，没有意义
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> slaPolicyService.replacePolicies(
                        tenantId,
                        List.of(
                                request(TicketPriority.LOW, 480, 2880, 60),
                                request(TicketPriority.MEDIUM, 240, 1440, 60),
                                request(TicketPriority.HIGH, 60, 480, 60),
                                request(TicketPriority.URGENT, 30, 240, 15)
                        )
                )
        );

        assertEquals("INVALID_SLA_POLICY", exception.getCode());
    }

    @Test
    void shouldRejectEmptyRuleSet() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> slaPolicyService.replacePolicies(tenantId, List.of())
        );

        assertEquals("INVALID_SLA_POLICY", exception.getCode());
    }

    @Test
    void shouldNotTouchAnotherTenantPolicies() {
        tenantService.createTenant(
                new CreateTenantRequest("sla-tenant-2", "SLA Tenant 2")
        );

        long otherTenantId = tenantIdOf("sla-tenant-2");

        slaPolicyService.replacePolicies(
                otherTenantId,
                List.of(
                        request(TicketPriority.LOW, 5, 10, 1),
                        request(TicketPriority.MEDIUM, 5, 10, 1),
                        request(TicketPriority.HIGH, 5, 10, 1),
                        request(TicketPriority.URGENT, 5, 10, 1)
                )
        );

        // 改 B 租户的规则不能影响 A 租户
        assertEquals(480, policyOf(TicketPriority.LOW).firstResponseMinutes());
        assertEquals(
                5,
                slaPolicyService.listPolicies(otherTenantId).get(0)
                        .firstResponseMinutes()
        );
    }

    private SlaPolicyResponse policyOf(TicketPriority priority) {
        return slaPolicyService.listPolicies(tenantId)
                .stream()
                .filter(policy -> policy.priority() == priority)
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("缺少优先级：" + priority)
                );
    }

    private SlaPolicyRequest request(
            TicketPriority priority,
            int firstResponseMinutes,
            int resolutionMinutes,
            int remindBeforeMinutes
    ) {
        return new SlaPolicyRequest(
                priority,
                firstResponseMinutes,
                resolutionMinutes,
                remindBeforeMinutes
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

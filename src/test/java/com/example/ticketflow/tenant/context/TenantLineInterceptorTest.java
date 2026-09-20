package com.example.ticketflow.tenant.context;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.notification.domain.Notification;
import com.example.ticketflow.notification.domain.enums.NotificationType;
import com.example.ticketflow.notification.mapper.NotificationMapper;
import com.example.ticketflow.role.mapper.PermissionMapper;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.mapper.TicketMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 租户拦截器：在 Mapper 这一层直接验证"SQL 有没有被加上租户条件"。
 *
 * <p>为什么不通过接口验证：拦截器是<b>第二道防线</b>，正常业务流程里显式条件已经保证了结果，
 * 所以从业务接口<b>看不出</b>它有没有生效。要证明它真的在改 SQL，只能在 Mapper 上看。</p>
 *
 * <p>注意这些查询都是直接调 Mapper，没有经过 HTTP，所以上下文要手动设置——
 * 这正是"没有上下文时拦截器不插手"那条规则的来源。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantLineInterceptorTest {

    @Autowired
    private TicketMapper ticketMapper;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long tenantOneId;

    private long tenantTwoId;

    private long ticketOfTenantTwoId;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

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
                VALUES
                    (1, 'interceptor-tenant-1', 'Tenant One', 'ACTIVE'),
                    (2, 'interceptor-tenant-2', 'Tenant Two', 'ACTIVE')
                """);

        tenantOneId = 1L;
        tenantTwoId = 2L;

        jdbcTemplate.update(
                """
                        INSERT INTO tf_ticket
                            (id, tenant_id, ticket_no, title, status, priority,
                             first_response_due_at, resolution_due_at)
                        VALUES
                            (1, 1, 'ITC-001', '租户一的车票', 'OPEN', 'MEDIUM',
                             DATEADD('HOUR', 4, CURRENT_TIMESTAMP),
                             DATEADD('HOUR', 24, CURRENT_TIMESTAMP)),
                            (2, 2, 'ITC-002', '租户二的车票', 'OPEN', 'MEDIUM',
                             DATEADD('HOUR', 4, CURRENT_TIMESTAMP),
                             DATEADD('HOUR', 24, CURRENT_TIMESTAMP))
                        """
        );

        ticketOfTenantTwoId = 2L;
    }

    @AfterEach
    void tearDown() {
        // 这个类手动设置上下文，必须自己清干净——否则会污染后面跑的测试类
        TenantContext.clear();
    }

    @Test
    void shouldHideOtherTenantsRowsWhenContextIsSet() {
        TenantContext.set(tenantOneId);

        // 按主键查别人的单：显式条件里没有租户，只有拦截器能挡住它
        assertNull(ticketMapper.selectById(ticketOfTenantTwoId));

        // 全表查也一样：只看得见自己租户那一张
        assertEquals(1, ticketMapper.selectList(null).size());
        assertEquals("ITC-001", ticketMapper.selectList(null).get(0).getTicketNo());
    }

    @Test
    void shouldNotTouchQueriesWithoutContext() {
        // 定时任务、登录接口本来就不属于任何租户：
        // 拦截器这时选择"不插手"——它必须能遍历所有租户，否则 SLA 扫描直接瘫掉
        assertNotNull(ticketMapper.selectById(ticketOfTenantTwoId));
        assertEquals(2, ticketMapper.selectList(null).size());
    }

    @Test
    void shouldNotFilterTablesWithoutTenantColumn() {
        TenantContext.set(tenantOneId);

        // tf_tenant 与 tf_permission 没有 tenant_id 列，必须被忽略，
        // 否则 SQL 里会出现一个不存在的列
        assertEquals(2, tenantMapper.selectList(null).size());
        assertFalse(permissionMapper.selectList(null).isEmpty());
    }

    @Test
    void shouldRejectUpdatingAnotherTenantsRow() {
        TenantContext.set(tenantOneId);

        Ticket other = new Ticket();
        other.setId(ticketOfTenantTwoId);
        other.setTitle("想偷偷改别人租户的标题");

        // UPDATE 也会被加上 tenant_id 条件，影响行数为 0
        assertEquals(0, ticketMapper.updateById(other));

        jdbcTemplate.queryForObject(
                "SELECT title FROM tf_ticket WHERE id = ?",
                String.class,
                ticketOfTenantTwoId
        ).equals("租户二的车票");
    }

    @Test
    void shouldStillInsertWithContextSet() {
        TenantContext.set(tenantOneId);

        Notification notification = new Notification();
        notification.setTenantId(tenantOneId);
        notification.setRecipientType(ActorType.MEMBER);
        notification.setRecipientId(1L);
        notification.setType(NotificationType.TICKET_ASSIGNED);
        notification.setBusinessKey("test:insert-with-context");
        notification.setTitle("插入测试");

        // 实体已经带了 tenant_id，拦截器不该再插一列（默认 ignoreInsert 会跳过分）
        assertEquals(1, notificationMapper.insert(notification));
    }
}

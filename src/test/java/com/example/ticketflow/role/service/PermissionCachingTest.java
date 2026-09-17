package com.example.ticketflow.role.service;

import com.example.ticketflow.role.dto.RoleResponse;
import com.example.ticketflow.support.InMemoryPermissionCache;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 权限缓存的行为。
 *
 * <p>用测试替身 {@link InMemoryPermissionCache} 代替 Redis，
 * 所以这里只验证"什么时候命中、什么时候失效"这套逻辑，不涉及真实的 Redis 连接。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class PermissionCachingTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MemberRoleService memberRoleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private InMemoryPermissionCache permissionCache;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long tenantId;

    private long firstMemberId;

    private long secondMemberId;

    @BeforeEach
    void setUp() {
        permissionCache.clear();

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
                new CreateTenantRequest("perm-cache", "Permission Cache")
        );

        tenantId = tenantIdOf("perm-cache");
        firstMemberId = createMember(tenantId, "first");
        secondMemberId = createMember(tenantId, "second");

        memberRoleService.replaceMemberRoles(
                tenantId,
                firstMemberId,
                List.of(BuiltInRoles.AGENT)
        );
        memberRoleService.replaceMemberRoles(
                tenantId,
                secondMemberId,
                List.of(BuiltInRoles.AGENT)
        );
    }

    @Test
    void shouldServePermissionsFromCache() {
        Set<String> before = permissionService.permissionsOf(tenantId, firstMemberId);

        assertTrue(before.contains("ticket:claim"));

        // 绕过接口直接改库，模拟"手工改库 / 运维脚本"这种外部变更
        jdbcTemplate.update(
                """
                        DELETE FROM tf_role_permission
                        WHERE role_id = ?
                          AND permission_id = (
                            SELECT id FROM tf_permission WHERE code = ?
                          )
                        """,
                roleOf(BuiltInRoles.AGENT).id(),
                "ticket:claim"
        );

        // 缓存里还是旧的权限，所以结果不变。
        // 这不是缺陷，而是缓存的固有代价：绕过接口改库要等 TTL 过期才生效（最长 10 分钟）。
        assertEquals(before, permissionService.permissionsOf(tenantId, firstMemberId));
    }

    @Test
    void shouldInvalidateCacheWhenRolePermissionsChange() {
        assertTrue(
                permissionService.permissionsOf(tenantId, firstMemberId)
                        .contains("ticket:claim")
        );

        roleService.replaceRolePermissions(
                tenantId,
                roleOf(BuiltInRoles.AGENT).id(),
                List.of("ticket:read")
        );

        // 走服务改权限会清缓存，所以立刻生效
        assertEquals(
                Set.of("ticket:read"),
                permissionService.permissionsOf(tenantId, firstMemberId)
        );
    }

    @Test
    void shouldInvalidateCacheOfEveryMemberHoldingTheRole() {
        permissionService.permissionsOf(tenantId, firstMemberId);
        permissionService.permissionsOf(tenantId, secondMemberId);

        roleService.replaceRolePermissions(
                tenantId,
                roleOf(BuiltInRoles.AGENT).id(),
                List.of("ticket:read")
        );

        // 两个人都挂了客服角色，改角色权限必须把两个人的缓存都清掉，
        // 只清一个就会出现"同一角色、两个成员、权限不一致"的诡异现象
        assertEquals(
                Set.of("ticket:read"),
                permissionService.permissionsOf(tenantId, firstMemberId)
        );
        assertEquals(
                Set.of("ticket:read"),
                permissionService.permissionsOf(tenantId, secondMemberId)
        );
    }

    @Test
    void shouldInvalidateCacheWhenMemberRolesChange() {
        assertTrue(
                permissionService.permissionsOf(tenantId, firstMemberId)
                        .contains("ticket:claim")
        );

        memberRoleService.replaceMemberRoles(
                tenantId,
                firstMemberId,
                List.of()
        );

        assertTrue(
                permissionService.permissionsOf(tenantId, firstMemberId).isEmpty()
        );
        // 另一个成员的权限不受影响
        assertFalse(
                permissionService.permissionsOf(tenantId, secondMemberId).isEmpty()
        );
    }

    @Test
    void shouldCacheEmptyPermissionSet() {
        memberRoleService.replaceMemberRoles(
                tenantId,
                secondMemberId,
                List.of()
        );

        assertTrue(permissionService.permissionsOf(tenantId, secondMemberId).isEmpty());

        // 空结果也必须进缓存：否则"没有权限的成员"每次请求都会穿透到数据库。
        // 这也是缓存实现用字符串而不是 Redis Set 的原因——Set 表达不了空集合。
        assertTrue(permissionCache.find(tenantId, secondMemberId).isPresent());
    }

    private RoleResponse roleOf(String code) {
        return roleService.listRoles(tenantId)
                .stream()
                .filter(role -> role.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("角色不存在：" + code));
    }

    private long tenantIdOf(String code) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_tenant WHERE code = ?",
                Long.class,
                code
        );

        return id == null ? -1L : id;
    }

    private long createMember(long tenantId, String username) {
        jdbcTemplate.update(
                """
                        INSERT INTO tf_user
                            (tenant_id, username, password_hash, display_name, status)
                        VALUES (?, ?, 'test-hash', ?, 'ACTIVE')
                        """,
                tenantId,
                username,
                username
        );

        Long id = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM tf_user
                        WHERE tenant_id = ?
                          AND username = ?
                        """,
                Long.class,
                tenantId,
                username
        );

        return id == null ? -1L : id;
    }
}

package com.example.ticketflow.role.service;

import com.example.ticketflow.support.InMemoryPermissionCache;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.role.domain.Role;
import com.example.ticketflow.role.mapper.RoleMapper;
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
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class RoleProvisioningTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryPermissionCache permissionCache;

    @BeforeEach
    void setUp() {
        // 每个用例都会把表清空再插回同一批 ID，缓存必须跟着清，
        // 否则上一个用例缓存的权限会被下一个用例读到
        permissionCache.clear();

        jdbcTemplate.update("DELETE FROM tf_ticket_operation");
        jdbcTemplate.update("DELETE FROM tf_ticket_comment");
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_customer");
        jdbcTemplate.update("DELETE FROM tf_member_role");
        jdbcTemplate.update("DELETE FROM tf_role_permission");
        jdbcTemplate.update("DELETE FROM tf_role");
        jdbcTemplate.update("DELETE FROM tf_user");
        jdbcTemplate.update("DELETE FROM tf_tenant");
    }

    @Test
    void shouldKnowEveryPermissionInDictionary() {
        // 权限字典是整个 RBAC 的地基：下面所有断言都基于它算出来的期望值。
        // 新增权限时这个数字要跟着改，改的时候正好会提醒你检查内置角色的默认授权。
        assertEquals(11, allPermissionCodes().size());
        assertTrue(allPermissionCodes().contains("ticket:handle:any"));
        assertTrue(allPermissionCodes().contains("role:manage"));
    }

    @Test
    void shouldCreateBuiltInRolesWhenTenantIsCreated() {
        long tenantId = createTenantWithRoles("rbac-tenant-1");

        List<Role> roles = rolesOf(tenantId);

        assertEquals(2, roles.size());
        assertEquals(
                List.of(BuiltInRoles.ADMIN, BuiltInRoles.AGENT),
                roles.stream().map(Role::getCode).toList()
        );

        // 内置角色必须打上标记并处于启用状态，否则既改不动也生效不了
        assertTrue(roles.stream().allMatch(Role::getBuiltIn));
        assertTrue(roles.stream().allMatch(Role::getEnabled));
    }

    @Test
    void shouldGrantDefaultPermissionsToBuiltInRoles() {
        long tenantId = createTenantWithRoles("rbac-tenant-2");

        // 客服：拿到的必须和常量清单逐字一致。
        // 如果 BuiltInRoles 里写错了编码，字典里查不到，这里就会少一条。
        assertEquals(
                new TreeSet<>(BuiltInRoles.AGENT_PERMISSIONS),
                permissionsOfRole(tenantId, BuiltInRoles.AGENT)
        );

        Set<String> expectedAdmin = allPermissionCodes();
        expectedAdmin.removeAll(BuiltInRoles.ADMIN_EXCLUDED_PERMISSIONS);

        assertEquals(
                expectedAdmin,
                permissionsOfRole(tenantId, BuiltInRoles.ADMIN)
        );

        // 领取是客服的自助动作，管理员不该有
        assertFalse(
                permissionsOfRole(tenantId, BuiltInRoles.ADMIN)
                        .contains("ticket:claim")
        );
    }

    @Test
    void shouldUnionPermissionsWhenMemberHasMultipleRoles() {
        long tenantId = createTenantWithRoles("rbac-tenant-3");
        long memberId = createMember(tenantId, "multi-role");

        assignRole(tenantId, memberId, BuiltInRoles.AGENT);
        assertEquals(
                BuiltInRoles.AGENT_PERMISSIONS.size(),
                permissionService.permissionsOf(tenantId, memberId).size()
        );

        assignRole(tenantId, memberId, BuiltInRoles.ADMIN);

        // 这里直接改库模拟"外部变更"：缓存不会自动失效，只能手动清。
        // 缓存本身的失效行为由 PermissionCachingTest 覆盖，这个用例只关心权限并集。
        permissionCache.clear();

        Set<String> union = permissionService.permissionsOf(tenantId, memberId);

        // 管理员缺 ticket:claim、客服有，并集正好覆盖整个字典；
        // 两个角色重叠的那几条不会重复计数（SQL 里 DISTINCT，Java 侧又装进 Set）
        assertEquals(allPermissionCodes(), union);
    }

    @Test
    void shouldIgnoreDisabledRoles() {
        long tenantId = createTenantWithRoles("rbac-tenant-4");
        long memberId = createMember(tenantId, "disabled-role");

        assignRole(tenantId, memberId, BuiltInRoles.ADMIN);

        assertFalse(permissionService.permissionsOf(tenantId, memberId).isEmpty());

        jdbcTemplate.update(
                """
                        UPDATE tf_role
                        SET enabled = FALSE
                        WHERE tenant_id = ?
                          AND code = ?
                        """,
                tenantId,
                BuiltInRoles.ADMIN
        );

        // 同上：直接改库，缓存要手动清
        permissionCache.clear();

        // 整租户停用管理员角色后，成员立刻变成"什么都做不了"
        assertTrue(permissionService.permissionsOf(tenantId, memberId).isEmpty());
    }

    @Test
    void shouldNotLeakPermissionsAcrossTenants() {
        long tenantA = createTenantWithRoles("rbac-tenant-a");
        long tenantB = createTenantWithRoles("rbac-tenant-b");

        long memberInA = createMember(tenantA, "member-a");
        assignRole(tenantA, memberInA, BuiltInRoles.ADMIN);

        assertFalse(permissionService.permissionsOf(tenantA, memberInA).isEmpty());

        // 同一个成员 ID 换个租户去查，必须查不到——两边的 1 号是两个不同的人
        assertTrue(permissionService.permissionsOf(tenantB, memberInA).isEmpty());
    }

    @Test
    void shouldReturnEmptyPermissionsWhenMemberHasNoRole() {
        long tenantId = createTenantWithRoles("rbac-tenant-5");
        long memberId = createMember(tenantId, "no-role");

        // 失败即拒绝：没有角色就是没有权限，不是"默认给点什么"
        assertTrue(permissionService.permissionsOf(tenantId, memberId).isEmpty());
    }

    private long createTenantWithRoles(String code) {
        tenantService.createTenant(new CreateTenantRequest(code, code));

        return tenantIdOf(code);
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

    private void assignRole(long tenantId, long memberId, String roleCode) {
        jdbcTemplate.update(
                """
                        INSERT INTO tf_member_role (tenant_id, member_id, role_id)
                        SELECT ?, ?, id
                        FROM tf_role
                        WHERE tenant_id = ?
                          AND code = ?
                        """,
                tenantId,
                memberId,
                tenantId,
                roleCode
        );
    }

    private List<Role> rolesOf(long tenantId) {
        return roleMapper.selectList(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getTenantId, tenantId)
                        .orderByAsc(Role::getCode)
        );
    }

    private Set<String> permissionsOfRole(long tenantId, String roleCode) {
        List<String> codes = jdbcTemplate.queryForList(
                """
                        SELECT permission_row.code
                        FROM tf_role role_row
                                 JOIN tf_role_permission role_permission
                                      ON role_permission.role_id = role_row.id
                                 JOIN tf_permission permission_row
                                      ON permission_row.id = role_permission.permission_id
                        WHERE role_row.tenant_id = ?
                          AND role_row.code = ?
                        ORDER BY permission_row.code
                        """,
                String.class,
                tenantId,
                roleCode
        );

        return new TreeSet<>(codes);
    }

    private Set<String> allPermissionCodes() {
        return new TreeSet<>(
                jdbcTemplate.queryForList(
                        "SELECT code FROM tf_permission",
                        String.class
                )
        );
    }
}

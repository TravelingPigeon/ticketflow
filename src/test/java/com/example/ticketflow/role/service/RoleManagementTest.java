package com.example.ticketflow.role.service;

import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.domain.Role;
import com.example.ticketflow.role.dto.CreateRoleRequest;
import com.example.ticketflow.role.dto.RoleResponse;
import com.example.ticketflow.role.mapper.RoleMapper;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
import com.example.ticketflow.support.InMemoryPermissionCache;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 角色管理服务：建角色、改角色权限、给成员分配角色。 */
@SpringBootTest
@ActiveProfiles("test")
class RoleManagementTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MemberRoleService memberRoleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryPermissionCache permissionCache;

    private long tenantId;

    private long memberId;

    @BeforeEach
    void setUp() {
        // 清库之前先清缓存：同一个 JVM 里多个用例会复用同一批 ID
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

        tenantService.createTenant(
                new CreateTenantRequest("role-mgmt", "Role Management")
        );

        tenantId = tenantIdOf("role-mgmt");
        memberId = createMember(tenantId, "admin-one");

        memberRoleService.replaceMemberRoles(
                tenantId,
                memberId,
                List.of(BuiltInRoles.ADMIN)
        );
    }

    @Test
    void shouldListRolesWithTheirPermissions() {
        List<RoleResponse> roles = roleService.listRoles(tenantId);

        assertEquals(2, roles.size());

        RoleResponse admin = roleOf(roles, BuiltInRoles.ADMIN);
        RoleResponse agent = roleOf(roles, BuiltInRoles.AGENT);

        assertTrue(admin.builtIn());
        assertEquals(10, admin.permissions().size());
        assertTrue(admin.permissions().contains("ticket:assign"));
        assertFalse(admin.permissions().contains("ticket:claim"));

        assertEquals(6, agent.permissions().size());
        assertTrue(agent.permissions().contains("ticket:claim"));
    }

    @Test
    void shouldCreateCustomRole() {
        RoleResponse created = roleService.createRole(
                tenantId,
                new CreateRoleRequest(
                        "READONLY",
                        "只读观察者",
                        List.of("ticket:read", "ticket:comment")
                )
        );

        assertEquals("READONLY", created.code());
        // 自定义角色不是内置角色，将来可以改编码、可以删除
        assertFalse(created.builtIn());
        assertTrue(created.enabled());
        assertEquals(
                List.of("ticket:comment", "ticket:read"),
                created.permissions()
        );
    }

    @Test
    void shouldRejectDuplicateRoleCode() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> roleService.createRole(
                        tenantId,
                        new CreateRoleRequest(
                                BuiltInRoles.AGENT,
                                "又一个客服",
                                List.of()
                        )
                )
        );

        assertEquals("ROLE_CODE_EXISTS", exception.getCode());
    }

    @Test
    void shouldRejectUnknownPermissionAndRollBackTheNewRole() {
        int before = countRoles();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> roleService.createRole(
                        tenantId,
                        new CreateRoleRequest(
                                "BROKEN",
                                "权限编码写错了的角色",
                                List.of("ticket:read", "ticket:assgin")
                        )
                )
        );

        assertEquals("INVALID_PERMISSION", exception.getCode());

        // 角色行已经插进去了，但事务要把它一起回滚掉，不能留下半成品
        assertEquals(before, countRoles());
    }

    @Test
    void shouldReplaceRolePermissions() {
        RoleResponse updated = roleService.replaceRolePermissions(
                tenantId,
                roleIdOf(BuiltInRoles.AGENT),
                List.of(
                        "ticket:read",
                        "ticket:create",
                        "ticket:update",
                        "ticket:status",
                        "ticket:comment",
                        "ticket:assign"
                )
        );

        assertEquals(
                new TreeSet<>(List.of(
                        "ticket:read",
                        "ticket:create",
                        "ticket:update",
                        "ticket:status",
                        "ticket:comment",
                        "ticket:assign"
                )),
                new TreeSet<>(updated.permissions())
        );
        // 客服原本能领取，现在被换掉了
        assertFalse(updated.permissions().contains("ticket:claim"));
    }

    @Test
    void shouldClearRolePermissionsWithEmptyList() {
        RoleResponse updated = roleService.replaceRolePermissions(
                tenantId,
                roleIdOf(BuiltInRoles.AGENT),
                List.of()
        );

        assertEquals(List.of(), updated.permissions());
    }

    @Test
    void shouldAllowEditingBuiltInRolePermissions() {
        // 内置角色只保护"编码和默认值由系统提供"，权限本身是可以调的
        RoleResponse updated = roleService.replaceRolePermissions(
                tenantId,
                roleIdOf(BuiltInRoles.ADMIN),
                List.of("ticket:read")
        );

        assertEquals(List.of("ticket:read"), updated.permissions());
        assertTrue(updated.builtIn());
    }

    @Test
    void shouldNotTouchAnotherTenantRole() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> roleService.replaceRolePermissions(
                        tenantId + 999L,
                        roleIdOf(BuiltInRoles.AGENT),
                        List.of()
                )
        );

        assertEquals("ROLE_NOT_FOUND", exception.getCode());

        // 别人查不到，更不该被改动
        assertEquals(
                6,
                roleOf(roleService.listRoles(tenantId), BuiltInRoles.AGENT)
                        .permissions()
                        .size()
        );
    }

    @Test
    void shouldUnionPermissionsFromMultipleRoles() {
        // 管理员单独没有 ticket:claim（领取是客服的动作），挂上客服角色之后就有了。
        // 注意这里必须把 ADMIN 留在新角色里，否则会触发"最后一个角色管理员"的保护——
        // 这个用例只关心并集，不关心自锁，所以别去碰那条规则。
        memberRoleService.replaceMemberRoles(
                tenantId,
                memberId,
                List.of(BuiltInRoles.ADMIN, BuiltInRoles.AGENT)
        );

        Set<String> permissions = permissionService.permissionsOf(tenantId, memberId);

        assertTrue(permissions.contains("role:manage"));
        assertTrue(permissions.contains("ticket:claim"));
        // 管理员缺 ticket:claim、客服缺 ticket:handle:any，并集正好覆盖整个字典
        assertEquals(11, permissions.size());
    }

    @Test
    void shouldApplyCustomRolePermissions() {
        roleService.createRole(
                tenantId,
                new CreateRoleRequest(
                        "READONLY",
                        "只读观察者",
                        List.of("ticket:read")
                )
        );

        long viewerId = createMember(tenantId, "viewer");

        memberRoleService.replaceMemberRoles(
                tenantId,
                viewerId,
                List.of("READONLY")
        );

        // 自定义角色的权限和内置角色走的是同一条查询路径，没有任何特殊对待
        assertEquals(
                Set.of("ticket:read"),
                permissionService.permissionsOf(tenantId, viewerId)
        );
    }

    @Test
    void shouldRejectUnknownMemberWhenAssigningRoles() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberRoleService.replaceMemberRoles(
                        tenantId,
                        9999L,
                        List.of(BuiltInRoles.AGENT)
                )
        );

        assertEquals("MEMBER_NOT_FOUND", exception.getCode());
    }

    @Test
    void shouldRejectUnknownRoleCodeWhenAssigningRoles() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberRoleService.replaceMemberRoles(
                        tenantId,
                        memberId,
                        List.of("NOT_A_ROLE")
                )
        );

        // 角色编码出现在请求体里，不是路径参数，所以是"引用不存在"而不是"资源不存在"
        assertEquals("INVALID_ROLE_CODE", exception.getCode());
    }

    @Test
    void shouldRejectRemovingTheLastRoleManager() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberRoleService.replaceMemberRoles(
                        tenantId,
                        memberId,
                        List.of(BuiltInRoles.AGENT)
                )
        );

        assertEquals("LAST_ROLE_MANAGER", exception.getCode());

        // 被拦下之后角色不应被改动，否则这个租户就没人能管权限了
        assertTrue(
                permissionService.permissionsOf(tenantId, memberId)
                        .contains("role:manage")
        );
    }

    @Test
    void shouldAllowDemotingWhenAnotherRoleManagerExists() {
        long anotherAdminId = createMember(tenantId, "admin-two");

        memberRoleService.replaceMemberRoles(
                tenantId,
                anotherAdminId,
                List.of(BuiltInRoles.ADMIN)
        );

        memberRoleService.replaceMemberRoles(
                tenantId,
                memberId,
                List.of(BuiltInRoles.AGENT)
        );

        Set<String> permissions = permissionService.permissionsOf(tenantId, memberId);

        assertFalse(permissions.contains("role:manage"));
        // 另一个管理员还在，所以这个租户依然管得动权限
        assertTrue(
                permissionService.permissionsOf(tenantId, anotherAdminId)
                        .contains("role:manage")
        );
    }

    private RoleResponse roleOf(List<RoleResponse> roles, String code) {
        return roles.stream()
                .filter(role -> role.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("角色不存在：" + code));
    }

    private long roleIdOf(String code) {
        return roleMapper.selectList(null)
                .stream()
                .filter(item -> item.getTenantId() == tenantId)
                .filter(item -> item.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("角色不存在：" + code))
                .getId();
    }

    private int countRoles() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tf_role",
                Integer.class
        );

        return count == null ? 0 : count;
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

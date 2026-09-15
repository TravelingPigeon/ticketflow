package com.example.ticketflow.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.domain.MemberRole;
import com.example.ticketflow.role.domain.Permission;
import com.example.ticketflow.role.domain.Role;
import com.example.ticketflow.role.domain.RolePermission;
import com.example.ticketflow.role.domain.Permissions;
import com.example.ticketflow.role.mapper.MemberRoleMapper;
import com.example.ticketflow.role.mapper.PermissionMapper;
import com.example.ticketflow.role.mapper.RolePermissionMapper;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 成员与角色的关系。
 *
 * <p>单独成一个服务，是因为它被两个入口用到：新建成员时挂内置角色、
 * 角色管理接口里改成员角色。放在 {@code UserAccountService} 里会让那个类同时管账号和权限，
 * 放在 {@code RoleService} 里又会让角色服务去查用户表。</p>
 */
@Service
public class MemberRoleService {

    private final MemberRoleMapper memberRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final UserAccountMapper userAccountMapper;
    private final RoleService roleService;
    private final PermissionService permissionService;

    public MemberRoleService(
            MemberRoleMapper memberRoleMapper,
            RolePermissionMapper rolePermissionMapper,
            PermissionMapper permissionMapper,
            UserAccountMapper userAccountMapper,
            RoleService roleService,
            PermissionService permissionService
    ) {
        this.memberRoleMapper = memberRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.userAccountMapper = userAccountMapper;
        this.roleService = roleService;
        this.permissionService = permissionService;
    }

    /** 新建成员时调用：按角色编码挂上一个角色，不替换已有角色 */
    @Transactional
    public void grantRoleByCode(
            Long tenantId,
            Long memberId,
            String roleCode
    ) {
        Role role = roleService.requireRoleByCode(tenantId, roleCode);

        link(tenantId, memberId, role.getId());

        // L1-4b：这里要清掉该成员的权限缓存
    }

    /** 角色管理接口：把成员的角色全量替换成给定的一组 */
    @Transactional
    public void replaceMemberRoles(
            Long tenantId,
            Long memberId,
            List<String> roleCodes
    ) {
        requireMemberInTenant(tenantId, memberId);

        List<Role> roles = new ArrayList<>();

        for (String roleCode : roleCodes.stream().distinct().toList()) {
            roles.add(roleService.requireRoleByCode(tenantId, roleCode));
        }

        requireNotRemovingLastRoleManager(tenantId, memberId, roles);

        memberRoleMapper.delete(
                new LambdaQueryWrapper<MemberRole>()
                        .eq(MemberRole::getTenantId, tenantId)
                        .eq(MemberRole::getMemberId, memberId)
        );

        for (Role role : roles) {
            link(tenantId, memberId, role.getId());
        }

        // L1-4b：这里要清掉该成员的权限缓存
    }

    /**
     * 防止把租户里最后一个能管理角色的成员降级。
     *
     * <p>角色权限可以随时在线调整，如果允许把最后一个拥有 {@code role:manage} 的成员改掉，
     * 这个租户就再也没人能进角色管理接口了——只能靠运维改数据库救场。
     * 这类"自锁"问题必须由服务端拦住，不能指望操作者自己小心。</p>
     */
    private void requireNotRemovingLastRoleManager(
            Long tenantId,
            Long memberId,
            List<Role> newRoles
    ) {
        boolean hadPermission = permissionService
                .permissionsOf(tenantId, memberId)
                .contains(Permissions.ROLE_MANAGE);

        if (!hadPermission) {
            return;
        }

        if (rolesGrantPermission(newRoles, Permissions.ROLE_MANAGE)) {
            return;
        }

        int others = memberRoleMapper.countOtherMembersWithPermission(
                tenantId,
                memberId,
                Permissions.ROLE_MANAGE
        );

        if (others == 0) {
            throw new BusinessException(
                    "LAST_ROLE_MANAGER",
                    "不能移除最后一个拥有角色管理权限的成员的角色"
            );
        }
    }

    private boolean rolesGrantPermission(
            List<Role> roles,
            String permissionCode
    ) {
        Set<Long> roleIds = roles.stream()
                .filter(Role::getEnabled)
                .map(Role::getId)
                .collect(Collectors.toSet());

        if (roleIds.isEmpty()) {
            return false;
        }

        Set<Long> permissionIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermission>()
                                .in(RolePermission::getRoleId, roleIds)
                )
                .stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toSet());

        if (permissionIds.isEmpty()) {
            return false;
        }

        return permissionMapper.selectCount(
                new LambdaQueryWrapper<Permission>()
                        .in(Permission::getId, permissionIds)
                        .eq(Permission::getCode, permissionCode)
        ) > 0;
    }

    private void requireMemberInTenant(Long tenantId, Long memberId) {
        UserAccount member = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getId, memberId)
                        .eq(UserAccount::getTenantId, tenantId)
        );

        if (member == null) {
            throw new BusinessException("MEMBER_NOT_FOUND", "成员不存在");
        }
    }

    private void link(Long tenantId, Long memberId, Long roleId) {
        MemberRole memberRole = new MemberRole();
        memberRole.setTenantId(tenantId);
        memberRole.setMemberId(memberId);
        memberRole.setRoleId(roleId);

        memberRoleMapper.insert(memberRole);
    }
}
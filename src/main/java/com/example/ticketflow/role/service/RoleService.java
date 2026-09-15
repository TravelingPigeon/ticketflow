package com.example.ticketflow.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.domain.Permission;
import com.example.ticketflow.role.domain.Role;
import com.example.ticketflow.role.domain.RolePermission;
import com.example.ticketflow.role.dto.CreateRoleRequest;
import com.example.ticketflow.role.dto.RoleResponse;
import com.example.ticketflow.role.mapper.PermissionMapper;
import com.example.ticketflow.role.mapper.RoleMapper;
import com.example.ticketflow.role.mapper.RolePermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public RoleService(
            RoleMapper roleMapper,
            PermissionMapper permissionMapper,
            RolePermissionMapper rolePermissionMapper
    ) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    /**
     * 为一个新建的租户建好两个内置角色，并按默认规则授予权限。
     */
    @Transactional
    public void createBuiltInRoles(Long tenantId) {
        List<Permission> permissions = permissionMapper.selectList(null);

        Role admin = insertRole(tenantId, BuiltInRoles.ADMIN, "管理员", true);

        for (Permission permission : permissions) {
            if (!BuiltInRoles.ADMIN_EXCLUDED_PERMISSIONS.contains(
                    permission.getCode()
            )) {
                grantPermission(tenantId, admin.getId(), permission.getId());
            }
        }

        Role agent = insertRole(tenantId, BuiltInRoles.AGENT, "客服", true);

        for (Permission permission : permissions) {
            if (BuiltInRoles.AGENT_PERMISSIONS.contains(permission.getCode())) {
                grantPermission(tenantId, agent.getId(), permission.getId());
            }
        }
    }

    /** 查询本租户的全部角色，每个角色带上它拥有的权限编码 */
    public List<RoleResponse> listRoles(Long tenantId) {
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getTenantId, tenantId)
                        .orderByAsc(Role::getCode)
        );

        List<RoleResponse> responses = new ArrayList<>();

        for (Role role : roles) {
            responses.add(
                    RoleResponse.of(role, permissionCodesOfRole(role.getId()))
            );
        }

        return responses;
    }

    /** 新建自定义角色。编码在租户内唯一，权限编码必须都在字典里 */
    @Transactional
    public RoleResponse createRole(
            Long tenantId,
            CreateRoleRequest request
    ) {
        String code = request.code().trim();

        if (findRoleByCode(tenantId, code) != null) {
            throw new BusinessException(
                    "ROLE_CODE_EXISTS",
                    "当前租户下角色编码已存在"
            );
        }

        Role role = insertRole(tenantId, code, request.name().trim(), false);

        replacePermissions(tenantId, role, request.permissions());

        return RoleResponse.of(role, permissionCodesOfRole(role.getId()));
    }

    /**
     * 全量替换一个角色拥有的权限。
     *
     * <p>内置角色同样允许调整权限——这正是"动态"的意义：
     * 内置只表示编码和默认值由系统提供，不表示权限不可改。</p>
     */
    @Transactional
    public RoleResponse replaceRolePermissions(
            Long tenantId,
            Long roleId,
            List<String> permissionCodes
    ) {
        Role role = requireRole(tenantId, roleId);

        replacePermissions(tenantId, role, permissionCodes);

        // L1-4b：这里要清掉所有挂了该角色的成员的权限缓存

        return RoleResponse.of(role, permissionCodesOfRole(role.getId()));
    }

    Role requireRoleByCode(Long tenantId, String roleCode) {
        Role role = findRoleByCode(tenantId, roleCode);

        if (role == null) {
            throw new BusinessException(
                    "ROLE_NOT_FOUND",
                    "角色不存在：" + roleCode
            );
        }

        return role;
    }

    private Role findRoleByCode(Long tenantId, String roleCode) {
        return roleMapper.selectOne(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getTenantId, tenantId)
                        .eq(Role::getCode, roleCode)
        );
    }

    private Role requireRole(Long tenantId, Long roleId) {
        Role role = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getId, roleId)
                        .eq(Role::getTenantId, tenantId)
        );

        if (role == null) {
            throw new BusinessException("ROLE_NOT_FOUND", "角色不存在");
        }

        return role;
    }

    private Role insertRole(
            Long tenantId,
            String code,
            String name,
            boolean builtIn
    ) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setName(name);
        role.setBuiltIn(builtIn);
        role.setEnabled(true);

        roleMapper.insert(role);

        return role;
    }

    private void replacePermissions(
            Long tenantId,
            Role role,
            List<String> permissionCodes
    ) {
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, role.getId())
        );

        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }

        for (Long permissionId : permissionIdsOf(permissionCodes)) {
            grantPermission(tenantId, role.getId(), permissionId);
        }
    }

    /**
     * 把权限编码翻译成 ID。
     *
     * <p>任何一个编码在字典里不存在都直接报错，而不是静默跳过——
     * 静默跳过会让"我明明给了权限，用户却没有"变成一场排查噩梦。</p>
     */
    private List<Long> permissionIdsOf(List<String> permissionCodes) {
        List<String> distinct = permissionCodes.stream().distinct().toList();

        List<Permission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>()
                        .in(Permission::getCode, distinct)
        );

        if (permissions.size() != distinct.size()) {
            List<String> known = permissions.stream()
                    .map(Permission::getCode)
                    .toList();

            List<String> unknown = distinct.stream()
                    .filter(code -> !known.contains(code))
                    .toList();

            throw new BusinessException(
                    "INVALID_PERMISSION",
                    "权限编码不存在：" + String.join("、", unknown)
            );
        }

        return permissions.stream().map(Permission::getId).toList();
    }

    private List<String> permissionCodesOfRole(Long roleId) {
        List<RolePermission> links = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId)
        );

        if (links.isEmpty()) {
            return List.of();
        }

        Set<Long> permissionIds = links.stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toSet());

        return permissionMapper.selectList(
                        new LambdaQueryWrapper<Permission>()
                                .in(Permission::getId, permissionIds)
                                .orderByAsc(Permission::getCode)
                )
                .stream()
                .map(Permission::getCode)
                .toList();
    }

    private void grantPermission(
            Long tenantId,
            Long roleId,
            Long permissionId
    ) {
        RolePermission rolePermission = new RolePermission();
        rolePermission.setTenantId(tenantId);
        rolePermission.setRoleId(roleId);
        rolePermission.setPermissionId(permissionId);

        rolePermissionMapper.insert(rolePermission);
    }
}
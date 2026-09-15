package com.example.ticketflow.role.service;

import com.example.ticketflow.role.domain.Permission;
import com.example.ticketflow.role.domain.Role;
import com.example.ticketflow.role.domain.RolePermission;
import com.example.ticketflow.role.mapper.PermissionMapper;
import com.example.ticketflow.role.mapper.RoleMapper;
import com.example.ticketflow.role.mapper.RolePermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        Role admin = createRole(tenantId, BuiltInRoles.ADMIN, "管理员");

        for (Permission permission : permissions) {
            if (!BuiltInRoles.ADMIN_EXCLUDED_PERMISSIONS.contains(
                    permission.getCode()
            )) {
                grantPermission(tenantId, admin.getId(), permission.getId());
            }
        }

        Role agent = createRole(tenantId, BuiltInRoles.AGENT, "客服");

        for (Permission permission : permissions) {
            if (BuiltInRoles.AGENT_PERMISSIONS.contains(permission.getCode())) {
                grantPermission(tenantId, agent.getId(), permission.getId());
            }
        }
    }

    private Role createRole(
            Long tenantId,
            String code,
            String name
    ) {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setName(name);
        role.setBuiltIn(true);
        role.setEnabled(true);

        roleMapper.insert(role);

        return role;
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
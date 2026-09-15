package com.example.ticketflow.role.service;

import java.util.List;

/**
 * 系统内置角色的编码与默认权限。
 *
 * <p>注意：{@code V11__create_rbac_tables.sql} 里有一份等价的 SQL，用于给已有租户补数据。
 * 那份 SQL 是一次性的历史数据迁移，这里的常量才是新建租户时真正生效的定义。
 * 两者如果哪天不一致，以这里为准。</p>
 */
public final class BuiltInRoles {

    public static final String ADMIN = "ADMIN";

    public static final String AGENT = "AGENT";

    /** 管理员默认拥有权限字典里的全部权限，只有这些除外 */
    public static final List<String> ADMIN_EXCLUDED_PERMISSIONS = List.of(
            "ticket:claim"
    );

    /** 客服默认拥有的权限 */
    public static final List<String> AGENT_PERMISSIONS = List.of(
            "ticket:read",
            "ticket:create",
            "ticket:update",
            "ticket:claim",
            "ticket:status",
            "ticket:comment"
    );

    private BuiltInRoles() {
    }
}
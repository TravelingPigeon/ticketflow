package com.example.ticketflow.role.domain;

/**
 * 权限编码常量。
 *
 * <p>编码本身定义在权限字典表 {@code tf_permission} 里，这里只是给 Java 代码一份
 * 编译期可检查的引用。注解里的权限串必须写成字面量
 * （例如 {@code @PreAuthorize("hasAuthority('ticket:assign')")}），
 * 因为注解参数要求编译期常量；两者的一致性由 {@code PermissionAnnotationTest}
 * 扫描注解并比对字典来保证。</p>
 */
public final class Permissions {

    public static final String TENANT_CREATE = "tenant:create";

    public static final String USER_CREATE = "user:create";

    public static final String ROLE_MANAGE = "role:manage";

    public static final String TICKET_READ = "ticket:read";

    public static final String TICKET_CREATE = "ticket:create";

    public static final String TICKET_UPDATE = "ticket:update";

    public static final String TICKET_ASSIGN = "ticket:assign";

    public static final String TICKET_CLAIM = "ticket:claim";

    public static final String TICKET_STATUS = "ticket:status";

    public static final String TICKET_COMMENT = "ticket:comment";

    public static final String TICKET_HANDLE_ANY = "ticket:handle:any";

    private Permissions() {
    }
}
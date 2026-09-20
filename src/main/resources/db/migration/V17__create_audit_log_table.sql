-- 通用审计日志（docs/04 §8.2）。
--
-- 和 tf_ticket_operation 的分工：
--   tf_ticket_operation 记"这张工单发生过什么"，跟着业务事务一起提交/回滚；
--   tf_audit_log 记"谁在什么时候试图做什么"，覆盖面更广（登录、成员与角色变更、SLA 配置变更），
--   写入独立于业务事务——最典型的就是登录失败：失败要抛异常，异常会让外层事务回滚，
--   审计若跟着回滚，最该被记录的尝试反而没留下痕迹。
--
-- tenant_id 允许为空：登录时如果租户编码本身就写错了，根本不知道是哪个租户，
-- 而"有人在探测不存在的租户"恰恰是最该记下来的。
-- 这类记录不属于任何租户的查询范围，只能由运维在库里查——平台级超管视图不在本期范围。
--
-- 刻意不加指向 tf_tenant 的外键：审计的意义是"记录发生过的事"，
-- 不该因为被审计对象将来被删掉就写不进去、或者反过来挡住删除。
--
-- detail_json 不得保存密码、密码哈希或完整 JWT（docs/04 §8.2）。

CREATE TABLE tf_audit_log (
                              id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                              tenant_id BIGINT UNSIGNED NULL,

    -- MEMBER / CUSTOMER，和令牌、通知表同一套取值
                              actor_type VARCHAR(16) NOT NULL,

    -- 失败时可能不知道是谁（租户或用户都不存在），所以可空
                              actor_id BIGINT UNSIGNED NULL,

                              action VARCHAR(32) NOT NULL,

                              result VARCHAR(16) NOT NULL,

    -- 预留：本期没有链路追踪，恒为 NULL
                              request_id VARCHAR(64) NULL,

    -- 登录场景下没有业务资源，恒为 NULL；
    -- 留给后续"成员变更 / 角色变更 / SLA 配置变更"这几类审计
                              resource_type VARCHAR(32) NULL,
                              resource_id BIGINT UNSIGNED NULL,

                              detail_json VARCHAR(1000) NULL,

                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              INDEX idx_audit_tenant_created
                                  (tenant_id, created_at, id),

                              INDEX idx_audit_tenant_resource
                                  (tenant_id, resource_type, resource_id, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 新增权限：查看本租户的审计日志
INSERT INTO tf_permission (code, name, permission_group, description)
VALUES ('audit:read', '查看审计日志', '审计', '查看本租户的登录与关键操作审计日志');

-- 已有租户的管理员角色补上这条权限。
-- 新租户不用管：RoleService.createBuiltInRoles 会按字典里现有的编码自动授予
-- （管理员默认拥有全部权限，只排除 ADMIN_EXCLUDED_PERMISSIONS）。
INSERT INTO tf_role_permission (tenant_id, role_id, permission_id)
SELECT role_row.tenant_id, role_row.id, permission_row.id
FROM tf_role role_row
         JOIN tf_permission permission_row
              ON permission_row.code = 'audit:read'
WHERE role_row.code = 'ADMIN'
  AND role_row.built_in = TRUE;
-- SLA 规则表 + 权限字典新增 sla:manage + 给已有租户补默认规则。
--
-- 规则按"租户 + 优先级"配置，每个租户每个优先级最多一条。
-- 默认值取自 docs/06 第 9 节，四条对应 TicketPriority 的四个取值。

CREATE TABLE tf_sla_policy (
                               id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                               tenant_id BIGINT UNSIGNED NOT NULL,

                               priority VARCHAR(16) NOT NULL,

                               first_response_minutes INT UNSIGNED NOT NULL,

                               resolution_minutes INT UNSIGNED NOT NULL,

                               remind_before_minutes INT UNSIGNED NOT NULL,

                               enabled BOOLEAN NOT NULL DEFAULT TRUE,

                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,

                               CONSTRAINT uk_sla_tenant_priority
                                   UNIQUE (tenant_id, priority),

                               CONSTRAINT fk_sla_tenant
                                   FOREIGN KEY (tenant_id)
                                       REFERENCES tf_tenant(id),

                               INDEX idx_sla_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 权限字典新增一条：配置 SLA 规则的权限
INSERT INTO tf_permission (code, name, permission_group, description)
VALUES ('sla:manage', '配置 SLA 规则', 'SLA', '调整本租户各优先级的响应与解决时限');

-- 已有租户的管理员角色要补上这条权限
-- （V11 给 ADMIN 授权时用的是"除 ticket:claim 以外全部"，那只覆盖到当时的字典，
--   之后新增的权限必须显式补）
INSERT INTO tf_role_permission (tenant_id, role_id, permission_id)
SELECT role_row.tenant_id, role_row.id, permission_row.id
FROM tf_role role_row
         JOIN tf_permission permission_row
              ON permission_row.code = 'sla:manage'
WHERE role_row.code = 'ADMIN';

-- 已有租户补默认规则
INSERT INTO tf_sla_policy
(tenant_id, priority, first_response_minutes, resolution_minutes, remind_before_minutes, enabled)
SELECT tenant_row.id, defaults.priority, defaults.first_response, defaults.resolution, defaults.remind_before, TRUE
FROM tf_tenant tenant_row
         CROSS JOIN (
             SELECT 'LOW' AS priority, 480 AS first_response, 2880 AS resolution, 60 AS remind_before
             UNION ALL
             SELECT 'MEDIUM', 240, 1440, 60
             UNION ALL
             SELECT 'HIGH', 60, 480, 30
             UNION ALL
             SELECT 'URGENT', 30, 240, 15
         ) defaults;

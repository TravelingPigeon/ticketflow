-- 动态 RBAC：角色与权限改为数据驱动，模型与 docs/04 第 4.3 ~ 4.5 节一致。
--
--   tf_permission       权限字典（全局，不含 tenant_id）
--   tf_role             租户内定义的角色，built_in 标记系统内置角色
--   tf_role_permission  角色 → 权限
--   tf_member_role      成员 → 角色（多对多，权限取并集）
--
-- 本迁移只做三件事：建表、灌入权限字典与两个内置角色的默认权限、把历史 tf_user.role
-- 转换成 tf_member_role 关联。Java 代码在这一步不改，系统行为与迁移前完全一致。

CREATE TABLE tf_permission (
                               id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                               code VARCHAR(64) NOT NULL,

                               name VARCHAR(128) NOT NULL,

                               permission_group VARCHAR(32) NOT NULL,

                               description VARCHAR(255) NULL,

                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT uk_permission_code UNIQUE (code)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tf_role (
                         id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                         tenant_id BIGINT UNSIGNED NOT NULL,

                         code VARCHAR(64) NOT NULL,

                         name VARCHAR(128) NOT NULL,

    -- 内置角色不允许删除或改编码，只允许调整它拥有的权限
                         built_in BOOLEAN NOT NULL DEFAULT FALSE,

                         enabled BOOLEAN NOT NULL DEFAULT TRUE,

                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,

                         CONSTRAINT uk_role_tenant_code
                             UNIQUE (tenant_id, code),

                         CONSTRAINT fk_role_tenant
                             FOREIGN KEY (tenant_id)
                                 REFERENCES tf_tenant(id),

                         INDEX idx_role_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tf_role_permission (
                                    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                                    tenant_id BIGINT UNSIGNED NOT NULL,

                                    role_id BIGINT UNSIGNED NOT NULL,

                                    permission_id BIGINT UNSIGNED NOT NULL,

                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT uk_role_permission
                                        UNIQUE (tenant_id, role_id, permission_id),

                                    CONSTRAINT fk_role_permission_role
                                        FOREIGN KEY (role_id)
                                            REFERENCES tf_role(id),

                                    CONSTRAINT fk_role_permission_permission
                                        FOREIGN KEY (permission_id)
                                            REFERENCES tf_permission(id),

                                    INDEX idx_role_permission_role (tenant_id, role_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tf_member_role (
                                id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                                tenant_id BIGINT UNSIGNED NOT NULL,

                                member_id BIGINT UNSIGNED NOT NULL,

                                role_id BIGINT UNSIGNED NOT NULL,

                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                CONSTRAINT uk_member_role
                                    UNIQUE (tenant_id, member_id, role_id),

                                CONSTRAINT fk_member_role_member
                                    FOREIGN KEY (member_id)
                                        REFERENCES tf_user(id),

                                CONSTRAINT fk_member_role_role
                                    FOREIGN KEY (role_id)
                                        REFERENCES tf_role(id),

                                INDEX idx_member_role_member (tenant_id, member_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 权限字典：全局配置，所有租户共用同一套编码
INSERT INTO tf_permission (code, name, permission_group, description)
VALUES
    ('tenant:create',    '创建租户',       '租户', '引导接口，创建新的企业租户'),
    ('user:create',      '创建成员',       '成员', '在租户内创建企业成员账号'),
    ('role:manage',      '管理角色与权限', '权限', '创建角色、调整角色权限、给成员分配角色'),
    ('ticket:read',      '查看工单',       '工单', '查看工单列表、详情与操作记录'),
    ('ticket:create',    '创建工单',       '工单', '创建工单'),
    ('ticket:update',    '编辑工单',       '工单', '修改标题、描述与优先级'),
    ('ticket:assign',    '分配负责人',     '工单', '把工单指派给本租户的其他成员'),
    ('ticket:claim',     '领取工单',       '工单', '把未分配的工单领取给自己'),
    ('ticket:status',    '流转工单状态',   '工单', '推进或回退工单状态'),
    ('ticket:comment',   '发表评论',       '工单', '在工单下发表评论'),
    ('ticket:handle:any','处理任意工单',   '工单', '数据范围：可处理本租户任意工单');

-- 为每个已有租户建立两个内置角色
INSERT INTO tf_role (tenant_id, code, name, built_in, enabled)
SELECT id, 'ADMIN', '管理员', TRUE, TRUE
FROM tf_tenant;

INSERT INTO tf_role (tenant_id, code, name, built_in, enabled)
SELECT id, 'AGENT', '客服', TRUE, TRUE
FROM tf_tenant;

-- 管理员：除"领取工单"以外的全部权限。
-- 领取是客服的自助动作，管理员通过"分配"完成同样的结果，这条边界与迁移前一致。
INSERT INTO tf_role_permission (tenant_id, role_id, permission_id)
SELECT role_row.tenant_id, role_row.id, permission_row.id
FROM tf_role role_row
         JOIN tf_permission permission_row
              ON permission_row.code <> 'ticket:claim'
WHERE role_row.code = 'ADMIN';

-- 客服：只能看单、建单、改单、领取、流转、评论；不能分配、不能处理别人的工单
INSERT INTO tf_role_permission (tenant_id, role_id, permission_id)
SELECT role_row.tenant_id, role_row.id, permission_row.id
FROM tf_role role_row
         JOIN tf_permission permission_row
              ON permission_row.code IN (
                                         'ticket:read',
                                         'ticket:create',
                                         'ticket:update',
                                         'ticket:claim',
                                         'ticket:status',
                                         'ticket:comment'
                  )
WHERE role_row.code = 'AGENT';

-- 历史数据：把 tf_user.role 的字符串转成 tf_member_role 关联
--
-- 关联不上的账号（比如 role 里是某个已废弃的值）不会得到任何角色，
-- 也就没有任何权限——这是有意的"失败即拒绝"方向，不会凭空给出权限。
-- 这类账号需要管理员在角色管理接口里手工指派角色。
INSERT INTO tf_member_role (tenant_id, member_id, role_id)
SELECT user_row.tenant_id, user_row.id, role_row.id
FROM tf_user user_row
         JOIN tf_role role_row
              ON role_row.tenant_id = user_row.tenant_id
                  AND role_row.code = user_row.role;
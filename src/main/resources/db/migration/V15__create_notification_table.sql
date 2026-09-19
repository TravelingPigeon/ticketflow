-- 站内通知。
--
-- business_key 是这个设计的核心：它是"这条通知在业务上唯一标识什么"的字符串，
-- 例如 sla:response:breached:17:member:3。唯一约束 (tenant_id, business_key) 保证
-- 同一件事不会给同一个人发两次——这是"第二层去重"：业务代码先判断该不该发，
-- 数据库再兜一次底，防止并发或重试导致的重复。

CREATE TABLE tf_notification (
                                 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                                 tenant_id BIGINT UNSIGNED NOT NULL,

    -- MEMBER / CUSTOMER：和令牌里的 actorType 同一套取值
                                 recipient_type VARCHAR(16) NOT NULL,

                                 recipient_id BIGINT UNSIGNED NOT NULL,

    -- TICKET_ASSIGNED / SLA_DUE_SOON / SLA_BREACHED
                                 type VARCHAR(32) NOT NULL,

                                 ticket_id BIGINT UNSIGNED NULL,

                                 business_key VARCHAR(128) NOT NULL,

                                 title VARCHAR(200) NOT NULL,

                                 content VARCHAR(500) NULL,

                                 read_at TIMESTAMP NULL,

                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT uk_notification_tenant_key
                                     UNIQUE (tenant_id, business_key),

                                 CONSTRAINT fk_notification_tenant
                                     FOREIGN KEY (tenant_id)
                                         REFERENCES tf_tenant(id),

                                 CONSTRAINT fk_notification_ticket
                                     FOREIGN KEY (ticket_id)
                                         REFERENCES tf_ticket(id),

                                 INDEX idx_notification_recipient_read
                                     (tenant_id, recipient_type, recipient_id, read_at, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
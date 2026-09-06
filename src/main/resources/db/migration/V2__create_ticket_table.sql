CREATE TABLE tf_ticket (
                           id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                           tenant_id BIGINT UNSIGNED NOT NULL,

                           ticket_no VARCHAR(32) NOT NULL,

                           title VARCHAR(200) NOT NULL,

                           description TEXT,

                           status VARCHAR(32) NOT NULL DEFAULT 'OPEN',

                           priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',

                           created_by BIGINT UNSIGNED NULL,

                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,

                           CONSTRAINT uk_ticket_tenant_no
                               UNIQUE (tenant_id, ticket_no),

                           CONSTRAINT fk_ticket_tenant
                               FOREIGN KEY (tenant_id)
                                   REFERENCES tf_tenant(id),

                           INDEX idx_ticket_tenant_status (tenant_id, status),

                           INDEX idx_ticket_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
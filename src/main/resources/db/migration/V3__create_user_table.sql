CREATE TABLE tf_user (
                         id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                         tenant_id BIGINT UNSIGNED NOT NULL,

                         username VARCHAR(64) NOT NULL,

                         password_hash VARCHAR(255) NOT NULL,

                         display_name VARCHAR(128) NOT NULL,

                         role VARCHAR(32) NOT NULL DEFAULT 'AGENT',

                         status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',

                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,

                         CONSTRAINT uk_user_tenant_username
                             UNIQUE (tenant_id, username),

                         CONSTRAINT fk_user_tenant
                             FOREIGN KEY (tenant_id)
                                 REFERENCES tf_tenant(id),

                         INDEX idx_user_tenant_status (tenant_id, status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
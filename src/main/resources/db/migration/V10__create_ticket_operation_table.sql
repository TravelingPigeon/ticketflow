CREATE TABLE tf_ticket_operation (
                                     id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                                     tenant_id BIGINT UNSIGNED NOT NULL,

                                     ticket_id BIGINT UNSIGNED NOT NULL,

                                     operator_type VARCHAR(16) NOT NULL,

                                     operator_id BIGINT UNSIGNED NOT NULL,

                                     operation_type VARCHAR(32) NOT NULL,

                                     from_value VARCHAR(64) NULL,

                                     to_value VARCHAR(64) NULL,

                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                     CONSTRAINT fk_operation_tenant
                                         FOREIGN KEY (tenant_id)
                                             REFERENCES tf_tenant(id),

                                     CONSTRAINT fk_operation_ticket
                                         FOREIGN KEY (ticket_id)
                                             REFERENCES tf_ticket(id),

                                     INDEX idx_operation_tenant_ticket
                                         (tenant_id, ticket_id, id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
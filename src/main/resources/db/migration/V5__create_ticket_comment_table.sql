CREATE TABLE tf_ticket_comment (
                                   id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,

                                   tenant_id BIGINT UNSIGNED NOT NULL,

                                   ticket_id BIGINT UNSIGNED NOT NULL,

                                   author_id BIGINT UNSIGNED NOT NULL,

                                   content VARCHAR(5000) NOT NULL,

                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                   CONSTRAINT fk_comment_tenant
                                       FOREIGN KEY (tenant_id)
                                           REFERENCES tf_tenant(id),

                                   CONSTRAINT fk_comment_ticket
                                       FOREIGN KEY (ticket_id)
                                           REFERENCES tf_ticket(id),

                                   CONSTRAINT fk_comment_author
                                       FOREIGN KEY (author_id)
                                           REFERENCES tf_user(id),

                                   INDEX idx_comment_tenant_ticket_created
                                       (tenant_id, ticket_id, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
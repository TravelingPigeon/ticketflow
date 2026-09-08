ALTER TABLE tf_ticket
    ADD COLUMN assignee_id BIGINT UNSIGNED NULL
        AFTER created_by;

ALTER TABLE tf_ticket
    ADD CONSTRAINT fk_ticket_assignee
        FOREIGN KEY (assignee_id)
            REFERENCES tf_user(id);

CREATE INDEX idx_ticket_tenant_assignee
    ON tf_ticket (tenant_id, assignee_id);
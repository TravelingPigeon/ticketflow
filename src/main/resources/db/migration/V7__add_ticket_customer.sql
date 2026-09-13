ALTER TABLE tf_ticket
    ADD COLUMN customer_id BIGINT UNSIGNED NULL AFTER created_by,

    ADD CONSTRAINT fk_ticket_customer
        FOREIGN KEY (customer_id)
            REFERENCES tf_customer(id);
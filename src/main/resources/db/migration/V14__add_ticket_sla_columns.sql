-- 给工单加 SLA 快照列。
--
-- 截止时间用"快照"而不是每次实时算：规则改了不该影响已存在的工单（docs/06 §9）。
--
-- 三步走：先加可空列 → 回填历史工单 → 再收紧为 NOT NULL。
-- 直接加 NOT NULL 列会失败（已有行没有值），这是给有数据的表加非空列的标准做法。

ALTER TABLE tf_ticket
    ADD COLUMN first_response_due_at DATETIME(3) NULL,
    ADD COLUMN resolution_due_at DATETIME(3) NULL,
    ADD COLUMN first_responded_at DATETIME(3) NULL,
    ADD COLUMN resolved_at DATETIME(3) NULL,
    ADD COLUMN response_sla_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN resolution_sla_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL';

-- 回填历史工单：按它们各自的优先级，用租户当前的规则从 created_at 推算。
-- 对历史数据来说这是近似值（当时的规则可能和现在不同），但比留空有意义得多。
UPDATE tf_ticket ticket
    JOIN tf_sla_policy policy
ON policy.tenant_id = ticket.tenant_id
    AND policy.priority = ticket.priority
    SET ticket.first_response_due_at =
        ticket.created_at + INTERVAL policy.first_response_minutes MINUTE,
        ticket.resolution_due_at =
        ticket.created_at + INTERVAL policy.resolution_minutes MINUTE
WHERE ticket.first_response_due_at IS NULL;

-- 回填完再收紧。这里如果报错，说明有工单的优先级找不到规则——
-- 那是数据不一致，应该让迁移失败，而不是放一个空值过去。
ALTER TABLE tf_ticket
    MODIFY COLUMN first_response_due_at DATETIME(3) NOT NULL,
    MODIFY COLUMN resolution_due_at DATETIME(3) NOT NULL;

-- 两个索引给定时扫描（L2d）用：扫描查的就是"某状态的工单里，截止时间落在某个区间的"。
ALTER TABLE tf_ticket
    ADD INDEX idx_ticket_response_due
    (tenant_id, response_sla_status, first_response_due_at),
    ADD INDEX idx_ticket_resolution_due
        (tenant_id, resolution_sla_status, resolution_due_at);
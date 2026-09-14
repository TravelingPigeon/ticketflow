-- 乐观锁版本号：每次更新工单时 +1，用于检测并发修改。
-- 默认 0，因此历史数据无需单独回填。
ALTER TABLE tf_ticket
    ADD COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0;
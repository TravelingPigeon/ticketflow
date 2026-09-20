-- F1：评论分型。
--
-- 背景：tf_ticket_comment.author_id 上本来有外键指向 tf_user(id)，
-- 所以客户（在 tf_customer 表里）写不进评论——这是"客户评论"一直没做的根因。
--
-- 三条改动：
--   1. author_type：作者是 MEMBER（企业成员）还是 CUSTOMER（外部客户），
--      和 tf_notification.recipient_type 用同一套取值。
--      author_id 只有配合 author_type 才有意义——成员表的 1 号和客户表的 1 号是两个不同的人。
--   2. comment_type：PUBLIC_REPLY（公开回复）/ INTERNAL_NOTE（内部备注）。
--      内部备注只有企业成员可见，客户看不到。
--   3. 删掉 fk_comment_author。
--
-- 为什么是删外键、而不是"再加一个 customer_id 列"：
-- 同一张表没法既引用 tf_user 又引用 tf_customer，这叫多态外键，数据库层给不了。
-- 代价是失去数据库级的引用完整性，补偿办法是把写入口收窄成一处——
-- TicketCommentService：authorType / authorId 都从登录身份里取，
-- 请求体里根本没有这两个字段，所以脏数据没有入口。
--
-- 历史数据：这段历史里评论只可能由成员创建，所以两点都用 DEFAULT 回填。
-- 注意这是一条 ALTER：MySQL 的 DDL 不能回滚，拆成两条如果第二条失败，
-- 会留下"列加了但外键还在"的半迁移状态。

ALTER TABLE tf_ticket_comment
    ADD COLUMN author_type VARCHAR(16) NOT NULL DEFAULT 'MEMBER' AFTER author_id,
    ADD COLUMN comment_type VARCHAR(16) NOT NULL DEFAULT 'PUBLIC_REPLY' AFTER content,
    DROP FOREIGN KEY fk_comment_author;

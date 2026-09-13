-- REQUESTER 角色已废弃：外部客户由 tf_customer 独立承载，企业成员只保留 ADMIN / AGENT。
--
-- 历史数据里的 REQUESTER 账号必须处理，否则 role 列的值在 Java 侧无法转换成 UserRole 枚举，
-- 只要读取到这些行就会报错（例如这些账号尝试登录时会得到 500）。
--
-- 处理方式是"改角色 + 停用"，两个动作各有原因：
--   1. 改成 AGENT：让记录能被正常读取，因为枚举里已经没有 REQUESTER 这个值；
--   2. 置为 LOCKED：禁止这些账号登录，避免它们凭空获得客服权限。
-- 选择不删除记录，是为了保留历史与工单里 created_by 的引用关系；
-- 如果确实需要继续使用某个账号，可由管理员改派角色后重新启用。
UPDATE tf_user
SET role = 'AGENT',
    status = 'LOCKED'
WHERE role = 'REQUESTER';

-- 删除 tf_user.role 过渡列。
--
-- 背景：动态 RBAC 落地后，"用户有哪些角色"的唯一来源是 tf_member_role 关联表，
-- tf_user.role 从 L1-5a 起已经没有任何代码读写（当时实测把它改成错误值，
-- 登录与鉴权都不受影响）。两个"用户的角色"来源迟早会不一致，
-- 而它已经不一致了——用 roleCodes 建的账号，这一列还停在默认值 'AGENT'。
--
-- 删之前核对过：没有任何账号是"只有 tf_user.role、却没有 tf_member_role 关联"的
-- （users_without_any_role = 0），所以这次删除不会让谁失去权限，不需要回填数据。
--
-- 这条列还在 V3 里定义过、在 V8 里被更新过，那两条迁移是历史记录，保持原样不动。

ALTER TABLE tf_user
DROP COLUMN role;
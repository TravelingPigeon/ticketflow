package com.example.ticketflow.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticketflow.user.domain.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserAccountMapper
        extends BaseMapper<UserAccount> {

    /**
     * 查询租户里所有启用的管理员成员 ID。
     *
     * <p>动态 RBAC 之后，"管理员"不再是 {@code tf_user} 上的一列，
     * 要穿过 {@code tf_member_role} → {@code tf_role} 才问得出来——
     * 这就是"角色是数据"的代价，也是它的代价所在。</p>
     */
    @Select("""
            SELECT user_row.id
            FROM tf_user user_row
                     JOIN tf_member_role member_role
                          ON member_role.member_id = user_row.id
                              AND member_role.tenant_id = user_row.tenant_id
                     JOIN tf_role role_row
                          ON role_row.id = member_role.role_id
                              AND role_row.tenant_id = member_role.tenant_id
                              AND role_row.enabled = TRUE
            WHERE user_row.tenant_id = #{tenantId}
              AND user_row.status = 'ACTIVE'
              AND role_row.code = 'ADMIN'
            ORDER BY user_row.id
            """)
    List<Long> selectActiveAdminIds(@Param("tenantId") Long tenantId);
}
package com.example.ticketflow.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticketflow.role.domain.MemberRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemberRoleMapper extends BaseMapper<MemberRole> {

    /**
     * 查询某个成员最终拥有的全部权限编码。
     *
     * <p>三次 JOIN 的含义：先从成员找到他挂的角色（顺带排除停用的角色），
     * 再从角色找到它被授予的权限，最后取出权限编码。</p>
     */
    @Select("""
            SELECT DISTINCT permission_row.code
            FROM tf_member_role member_role
                     JOIN tf_role role_row
                          ON role_row.id = member_role.role_id
                              AND role_row.tenant_id = member_role.tenant_id
                              AND role_row.enabled = TRUE
                     JOIN tf_role_permission role_permission
                          ON role_permission.role_id = role_row.id
                              AND role_permission.tenant_id = role_row.tenant_id
                     JOIN tf_permission permission_row
                          ON permission_row.id = role_permission.permission_id
            WHERE member_role.tenant_id = #{tenantId}
              AND member_role.member_id = #{memberId}
            ORDER BY permission_row.code
            """)
    List<String> selectPermissionCodes(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId
    );

    /**
     * 统计租户里除指定成员外，还有多少成员拥有某个权限。
     *
     * <p>用在"不能移除最后一个角色管理员"的校验上。</p>
     */
    @Select("""
            SELECT COUNT(DISTINCT member_role.member_id)
            FROM tf_member_role member_role
                     JOIN tf_role role_row
                          ON role_row.id = member_role.role_id
                              AND role_row.tenant_id = member_role.tenant_id
                              AND role_row.enabled = TRUE
                     JOIN tf_role_permission role_permission
                          ON role_permission.role_id = role_row.id
                     JOIN tf_permission permission_row
                          ON permission_row.id = role_permission.permission_id
            WHERE member_role.tenant_id = #{tenantId}
              AND member_role.member_id <> #{memberId}
              AND permission_row.code = #{permissionCode}
            """)
    int countOtherMembersWithPermission(
            @Param("tenantId") Long tenantId,
            @Param("memberId") Long memberId,
            @Param("permissionCode") String permissionCode
    );

    /** 查询挂了某个角色的全部成员 ID */
    @Select("""
            SELECT DISTINCT member_id
            FROM tf_member_role
            WHERE tenant_id = #{tenantId}
              AND role_id = #{roleId}
            """)
    List<Long> selectMemberIdsByRoleId(
            @Param("tenantId") Long tenantId,
            @Param("roleId") Long roleId
    );
}
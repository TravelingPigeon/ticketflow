package com.example.ticketflow.support;

import com.example.ticketflow.role.service.BuiltInRoles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 测试用的授权工具。
 *
 * <p>MockMvc 测试把安全过滤器链关掉了（{@code addFilters = false}），
 * 所以"令牌换权限"那段代码不会执行，测试必须自己把 authority 挂到安全上下文上。
 * 这里按内置角色的默认规则算出权限，保证测试里的授权和真实运行时的授权出自同一份定义——
 * 改了 {@link BuiltInRoles} 或权限字典，测试跟着一起变。</p>
 */
public final class TestAuthorities {

    private TestAuthorities() {
    }

    public static Set<String> permissionCodes(
            JdbcTemplate jdbcTemplate,
            String roleCode
    ) {
        if (BuiltInRoles.AGENT.equals(roleCode)) {
            return Set.copyOf(BuiltInRoles.AGENT_PERMISSIONS);
        }

        if (BuiltInRoles.ADMIN.equals(roleCode)) {
            Set<String> codes = new TreeSet<>(
                    jdbcTemplate.queryForList(
                            "SELECT code FROM tf_permission",
                            String.class
                    )
            );

            codes.removeAll(BuiltInRoles.ADMIN_EXCLUDED_PERMISSIONS);

            return Set.copyOf(codes);
        }

        return Set.of();
    }

    public static List<GrantedAuthority> authorities(
            JdbcTemplate jdbcTemplate,
            String roleCode
    ) {
        return authorities(permissionCodes(jdbcTemplate, roleCode));
    }

    public static List<GrantedAuthority> authorities(Set<String> permissionCodes) {
        return permissionCodes.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}

package com.example.ticketflow.role;

import com.example.ticketflow.auth.security.TokenService;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.role.service.RoleService;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 走真实安全过滤器链的 RBAC 端到端测试。
 *
 * <p>其它 MockMvc 测试都写着 {@code addFilters = false}，也就是把安全过滤器链关掉了，
 * 因此"用令牌里的 tenantId / actorId 去数据库查权限"这段代码在那些测试里永远不会执行。
 * 这个类不加这个开关，用 {@link TokenService} 真签一个令牌打进来，专门覆盖那段逻辑。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RbacEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long tenantId;

    private long memberId;

    private String token;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM tf_ticket_operation");
        jdbcTemplate.update("DELETE FROM tf_ticket_comment");
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_customer");
        jdbcTemplate.update("DELETE FROM tf_member_role");
        jdbcTemplate.update("DELETE FROM tf_role_permission");
        jdbcTemplate.update("DELETE FROM tf_role");
        jdbcTemplate.update("DELETE FROM tf_user");
        jdbcTemplate.update("DELETE FROM tf_tenant");

        jdbcTemplate.update(
                """
                        INSERT INTO tf_tenant (code, name, status)
                        VALUES ('rbac-e2e', 'RBAC E2E', 'ACTIVE')
                        """
        );

        tenantId = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_tenant WHERE code = 'rbac-e2e'",
                Long.class
        );

        // 用真实的开通逻辑建内置角色，而不是手工插数据
        roleService.createBuiltInRoles(tenantId);

        jdbcTemplate.update(
                """
                        INSERT INTO tf_user
                            (tenant_id, username, password_hash, display_name, role, status)
                        VALUES (?, 'e2e-admin', 'test-hash', 'E2E Admin', 'ADMIN', 'ACTIVE')
                        """,
                tenantId
        );

        memberId = jdbcTemplate.queryForObject(
                """
                        SELECT id
                        FROM tf_user
                        WHERE tenant_id = ?
                          AND username = 'e2e-admin'
                        """,
                Long.class,
                tenantId
        );

        assignRole(BuiltInRoles.ADMIN);

        token = issueTokenFor("e2e-admin");
    }

    @Test
    void shouldAuthorizeUsingPermissionsLoadedFromDatabase() throws Exception {
        mockMvc.perform(
                        get("/api/v1/tickets")
                                .header("Authorization", bearer())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // /auth/me 返回的是权限串而不是角色名，说明 authority 确实来自权限表
        mockMvc.perform(
                        get("/api/v1/auth/me")
                                .header("Authorization", bearer())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorities[?(@.authority == 'ticket:read')]")
                        .exists());
    }

    @Test
    void shouldRejectCustomerTokenOnWorkbenchEndpoint() throws Exception {
        String customerToken = tokenService.issueCustomerToken(
                customer(1L, tenantId)
        ).accessToken();

        mockMvc.perform(
                        get("/api/v1/tickets")
                                .header("Authorization", "Bearer " + customerToken)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldApplyRevokedPermissionWithoutReissuingToken() throws Exception {
        // 同一个令牌，改权限之前可以访问
        mockMvc.perform(
                        get("/api/v1/tickets")
                                .header("Authorization", bearer())
                )
                .andExpect(status().isOk());

        revoke("ticket:read");

        // 令牌没变、没重新登录，只改了数据库里的角色权限，立刻就该被拦住
        mockMvc.perform(
                        get("/api/v1/tickets")
                                .header("Authorization", bearer())
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        grant("ticket:read");

        // 恢复权限后同一个令牌又能用了，说明每次请求都在重新读权限
        mockMvc.perform(
                        get("/api/v1/tickets")
                                .header("Authorization", bearer())
                )
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyWorkbenchEndpointWhenMemberHasNoRole() throws Exception {
        jdbcTemplate.update(
                "DELETE FROM tf_member_role WHERE tenant_id = ? AND member_id = ?",
                tenantId,
                memberId
        );

        mockMvc.perform(
                        get("/api/v1/tickets")
                                .header("Authorization", bearer())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectAnonymousRequestOnWorkbenchEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String issueTokenFor(String username) {
        UserAccount user = new UserAccount();
        user.setId(memberId);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setRole(UserRole.ADMIN);

        return tokenService.issueMemberToken(user).accessToken();
    }

    private com.example.ticketflow.customer.domain.Customer customer(
            long customerId,
            long customerTenantId
    ) {
        com.example.ticketflow.customer.domain.Customer customer =
                new com.example.ticketflow.customer.domain.Customer();

        customer.setId(customerId);
        customer.setTenantId(customerTenantId);
        customer.setEmail("customer@example.com");

        return customer;
    }

    private void assignRole(String roleCode) {
        jdbcTemplate.update(
                """
                        INSERT INTO tf_member_role (tenant_id, member_id, role_id)
                        SELECT ?, ?, id
                        FROM tf_role
                        WHERE tenant_id = ?
                          AND code = ?
                        """,
                tenantId,
                memberId,
                tenantId,
                roleCode
        );
    }

    private void revoke(String permissionCode) {
        jdbcTemplate.update(
                """
                        DELETE FROM tf_role_permission
                        WHERE role_id = (
                            SELECT id FROM tf_role
                            WHERE tenant_id = ? AND code = 'ADMIN'
                        )
                          AND permission_id = (
                            SELECT id FROM tf_permission WHERE code = ?
                        )
                        """,
                tenantId,
                permissionCode
        );
    }

    private void grant(String permissionCode) {
        jdbcTemplate.update(
                """
                        INSERT INTO tf_role_permission
                            (tenant_id, role_id, permission_id)
                        SELECT ?, id, (
                            SELECT id FROM tf_permission WHERE code = ?
                        )
                        FROM tf_role
                        WHERE tenant_id = ?
                          AND code = 'ADMIN'
                        """,
                tenantId,
                permissionCode,
                tenantId
        );
    }
}

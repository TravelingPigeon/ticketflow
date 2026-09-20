package com.example.ticketflow.audit.service;

import com.example.ticketflow.auth.service.AuthService;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.role.service.MemberRoleService;
import com.example.ticketflow.role.service.RoleService;
import com.example.ticketflow.support.TestAuthorities;
import com.example.ticketflow.user.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A3-1：登录审计。
 *
 * <p>覆盖三件事：成功与四种失败都留痕、审计里永远没有密码、
 * 以及审计写入<b>独立于业务事务</b>（失败要在外层事务回滚后仍然存在）。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class LoginAuditTest {

    private static final String RAW_PASSWORD = "Password123";

    private static final long TENANT_ID = 1L;

    private static final long ALICE_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MemberRoleService memberRoleService;

    @Autowired
    private AuthService authService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {

        jdbcTemplate.update("DELETE FROM tf_audit_log");
        jdbcTemplate.update("DELETE FROM tf_notification");

        jdbcTemplate.update("DELETE FROM tf_ticket_operation");
        jdbcTemplate.update("DELETE FROM tf_ticket_comment");
        jdbcTemplate.update("DELETE FROM tf_ticket");
        jdbcTemplate.update("DELETE FROM tf_customer");
        jdbcTemplate.update("DELETE FROM tf_member_role");
        jdbcTemplate.update("DELETE FROM tf_role_permission");
        jdbcTemplate.update("DELETE FROM tf_role");
        jdbcTemplate.update("DELETE FROM tf_sla_policy");
        jdbcTemplate.update("DELETE FROM tf_user");
        jdbcTemplate.update("DELETE FROM tf_tenant");

        jdbcTemplate.update("""
                INSERT INTO tf_tenant (id, code, name, status)
                VALUES
                    (1, 'audit-tenant', 'Audit Tenant', 'ACTIVE'),
                    (2, 'audit-tenant-2', 'Audit Tenant 2', 'ACTIVE')
                """);

        jdbcTemplate.update(
                """
                        INSERT INTO tf_user
                            (id, tenant_id, username, password_hash,
                             display_name, status)
                        VALUES
                            (1, 1, 'alice', ?, 'Alice', 'ACTIVE'),
                            (2, 1, 'locked-one', ?, 'Locked', 'LOCKED'),
                            (3, 2, 'bob', ?, 'Bob', 'ACTIVE')
                        """,
                passwordEncoder.encode(RAW_PASSWORD),
                passwordEncoder.encode(RAW_PASSWORD),
                passwordEncoder.encode(RAW_PASSWORD)
        );

        roleService.createBuiltInRoles(TENANT_ID);
        memberRoleService.replaceMemberRoles(
                TENANT_ID,
                ALICE_ID,
                List.of(BuiltInRoles.ADMIN)
        );
    }

    // ------------------------------------------------------------------
    // 登录留痕
    // ------------------------------------------------------------------

    @Test
    void shouldRecordSuccessfulLogin() throws Exception {
        login("audit-tenant", "alice", RAW_PASSWORD)
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = auditRows();

        assertEquals(1, rows.size());

        Map<String, Object> row = rows.get(0);

        assertEquals("LOGIN", row.get("action"));
        assertEquals("SUCCESS", row.get("result"));
        assertEquals("MEMBER", row.get("actor_type"));
        assertEquals(ALICE_ID, row.get("actor_id"));
        assertEquals(TENANT_ID, row.get("tenant_id"));

        String detail = (String) row.get("detail_json");

        assertTrue(detail.contains("alice"), detail);
        assertTrue(detail.contains("audit-tenant"), detail);
        // 成功没有失败原因
        assertFalse(detail.contains("reason"), detail);
    }

    @Test
    void shouldRecordFailureWhenPasswordIsWrong() throws Exception {
        login("audit-tenant", "alice", "WrongPassword-999")
                .andExpect(status().isUnauthorized());

        Map<String, Object> row = onlyAuditRow();

        assertEquals("FAILURE", row.get("result"));
        assertEquals("MEMBER", row.get("actor_type"));
        // 用户是存在的，所以 actor_id 记得下来
        assertEquals(ALICE_ID, row.get("actor_id"));
        assertTrue(((String) row.get("detail_json")).contains("BAD_PASSWORD"));
    }

    @Test
    void shouldRecordFailureWhenUserDoesNotExist() throws Exception {
        login("audit-tenant", "nobody", RAW_PASSWORD)
                .andExpect(status().isUnauthorized());

        Map<String, Object> row = onlyAuditRow();

        assertEquals("FAILURE", row.get("result"));
        // 租户存在，所以 tenant_id 记得下来；用户不存在，actor_id 只能是空
        assertEquals(TENANT_ID, row.get("tenant_id"));
        assertNull(row.get("actor_id"));
        assertTrue(((String) row.get("detail_json")).contains("USER_NOT_FOUND"));
    }

    @Test
    void shouldRecordFailureWhenTenantDoesNotExist() throws Exception {
        login("no-such-tenant", "alice", RAW_PASSWORD)
                .andExpect(status().isUnauthorized());

        Map<String, Object> row = onlyAuditRow();

        assertEquals("FAILURE", row.get("result"));
        // 连租户都不知道是哪个：这正是"有人在探测"最该留下的痕迹
        assertNull(row.get("tenant_id"));
        assertNull(row.get("actor_id"));

        String detail = (String) row.get("detail_json");

        assertTrue(detail.contains("TENANT_NOT_FOUND"), detail);
        assertTrue(detail.contains("no-such-tenant"), detail);
    }

    @Test
    void shouldRecordFailureWhenUserIsLocked() throws Exception {
        login("audit-tenant", "locked-one", RAW_PASSWORD)
                .andExpect(status().isForbidden());

        Map<String, Object> row = onlyAuditRow();

        assertEquals("FAILURE", row.get("result"));
        assertTrue(((String) row.get("detail_json")).contains("USER_LOCKED"));
    }

    @Test
    void shouldNeverStorePasswordInAudit() throws Exception {
        String attemptedPassword = "SuperSecret-1234";

        login("audit-tenant", "alice", attemptedPassword)
                .andExpect(status().isUnauthorized());

        Map<String, Object> row = onlyAuditRow();

        // 整行序列化后不能出现密码、也不能出现密码哈希
        String rowAsText = row.toString();

        assertFalse(rowAsText.contains(attemptedPassword), rowAsText);
        assertFalse(
                rowAsText.contains(
                        passwordEncoder.encode(RAW_PASSWORD)
                ),
                rowAsText
        );
    }

    // ------------------------------------------------------------------
    // 独立事务：这是本轮最难写也最重要的一条
    // ------------------------------------------------------------------

    @Test
    void shouldKeepFailureAuditWhenOuterTransactionRollsBack() {
        TransactionTemplate template =
                new TransactionTemplate(transactionManager);

        template.executeWithoutResult(status -> {
            assertThrows(
                    BusinessException.class,
                    () -> authService.verifyCredentials(
                            new LoginRequest(
                                    "audit-tenant",
                                    "alice",
                                    "WrongPassword-999"
                            )
                    )
            );

            // 模拟"外层业务因为别的原因回滚"
            status.setRollbackOnly();
        });

        // 外层事务回滚了，但审计是 REQUIRES_NEW，独立提交过
        assertEquals(1, auditRows().size());
        assertEquals("FAILURE", onlyAuditRow().get("result"));
    }

    // ------------------------------------------------------------------
    // 查询接口
    // ------------------------------------------------------------------

    @Test
    void shouldOnlyReturnOwnTenantLogs() throws Exception {
        login("audit-tenant", "alice", RAW_PASSWORD);

        // 别的租户的日志，以及"不知道属于谁"的日志
        insertAuditRow(2L, "MEMBER", 3L, "LOGIN", "SUCCESS");
        insertAuditRow(null, "MEMBER", null, "LOGIN", "FAILURE");

        mockMvc.perform(
                        get("/api/v1/audit-logs")
                                .with(memberToken("alice", ALICE_ID, "ADMIN"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                // tenant_id 为空的记录不属于任何租户的查询范围
                .andExpect(jsonPath("$.data.records[0].tenantId")
                        .value(TENANT_ID));
    }

    @Test
    void shouldFilterByResult() throws Exception {
        login("audit-tenant", "alice", RAW_PASSWORD);
        login("audit-tenant", "alice", "WrongPassword-999");

        mockMvc.perform(
                        get("/api/v1/audit-logs?result=FAILURE")
                                .with(memberToken("alice", ALICE_ID, "ADMIN"))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].result")
                        .value("FAILURE"));
    }

    @Test
    void shouldRejectAgentOnAuditLogEndpoint() throws Exception {
        // 客服没有 audit:read
        mockMvc.perform(
                        get("/api/v1/audit-logs")
                                .with(memberToken("alice", ALICE_ID, "AGENT"))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectInvalidPageSize() throws Exception {
        mockMvc.perform(
                        get("/api/v1/audit-logs?size=500")
                                .with(memberToken("alice", ALICE_ID, "ADMIN"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
    }

    // ------------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions login(
            String tenantCode,
            String username,
            String password
    ) throws Exception {
        String body = """
                {
                  "tenantCode": "%s",
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(tenantCode, username, password);

        return mockMvc.perform(
                post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(body)
        );
    }

    private List<Map<String, Object>> auditRows() {
        return jdbcTemplate.queryForList(
                """
                        SELECT tenant_id, actor_type, actor_id, action,
                               result, detail_json
                        FROM tf_audit_log
                        ORDER BY id
                        """
        );
    }

    private Map<String, Object> onlyAuditRow() {
        List<Map<String, Object>> rows = auditRows();

        assertEquals(1, rows.size(), "审计条数不是 1：" + rows);

        return rows.get(0);
    }

    private void insertAuditRow(
            Long tenantId,
            String actorType,
            Long actorId,
            String action,
            String result
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO tf_audit_log
                            (tenant_id, actor_type, actor_id, action, result)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                tenantId,
                actorType,
                actorId,
                action,
                result
        );
    }

    private RequestPostProcessor memberToken(
            String username,
            long actorId,
            String role
    ) {
        List<GrantedAuthority> authorities =
                TestAuthorities.authorities(jdbcTemplate, role);

        return request -> {
            Jwt jwt = Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject(username)
                    .claim("tenantId", TENANT_ID)
                    .claim("actorId", actorId)
                    .claim("actorType", "MEMBER")
                    .claim("roles", List.of(role))
                    .build();

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(jwt, authorities, username);

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);

            TestSecurityContextHolder.setContext(context);

            return request;
        };
    }
}

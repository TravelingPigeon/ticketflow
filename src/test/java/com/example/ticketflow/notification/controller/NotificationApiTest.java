package com.example.ticketflow.notification.controller;

import com.example.ticketflow.notification.domain.enums.NotificationType;
import com.example.ticketflow.notification.service.NotificationService;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.role.service.MemberRoleService;
import com.example.ticketflow.support.TestAuthorities;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通知接口：只看得到自己的、能标记已读。 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class NotificationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private MemberRoleService memberRoleService;

    @Autowired
    private NotificationService notificationService;

    private long tenantId;

    @BeforeEach
    void setUp() {

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

        tenantService.createTenant(
                new CreateTenantRequest("notify-api", "Notify API")
        );

        tenantId = tenantIdOf("notify-api");

        jdbcTemplate.update(
                """
                        INSERT INTO tf_user
                            (id, tenant_id, username, password_hash, display_name, status)
                        VALUES
                            (1, ?, 'agent-one', 'test-hash', 'Agent One', 'ACTIVE'),
                            (2, ?, 'agent-two', 'test-hash', 'Agent Two', 'ACTIVE')
                        """,
                tenantId,
                tenantId
        );

        memberRoleService.replaceMemberRoles(
                tenantId,
                1L,
                List.of(BuiltInRoles.AGENT)
        );
        memberRoleService.replaceMemberRoles(
                tenantId,
                2L,
                List.of(BuiltInRoles.AGENT)
        );

        notificationService.notifyMember(
                tenantId,
                1L,
                NotificationType.TICKET_ASSIGNED,
                null,
                "工单已分配给你",
                null,
                "ticket:assigned:1:member:1"
        );
    }

    @Test
    void shouldListOwnNotifications() throws Exception {
        mockMvc.perform(
                        get("/api/v1/notifications")
                                .with(memberToken(1L))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type")
                        .value("TICKET_ASSIGNED"))
                .andExpect(jsonPath("$.data[0].readAt").doesNotExist());
    }

    @Test
    void shouldNotExposeAnotherMembersNotifications() throws Exception {
        // 同一个租户的另一个客服：一条也看不到
        mockMvc.perform(
                        get("/api/v1/notifications")
                                .with(memberToken(2L))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void shouldMarkNotificationAsRead() throws Exception {
        Long notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_notification WHERE recipient_id = ?",
                Long.class,
                1L
        );

        mockMvc.perform(
                        patch("/api/v1/notifications/" + notificationId + "/read")
                                .with(memberToken(1L))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 未读列表空了，全量列表里能看到 readAt
        mockMvc.perform(
                        get("/api/v1/notifications")
                                .param("unreadOnly", "true")
                                .with(memberToken(1L))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(
                        get("/api/v1/notifications")
                                .with(memberToken(1L))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].readAt").isNotEmpty());
    }

    @Test
    void shouldRejectMarkingAnotherMembersNotification() throws Exception {
        Long notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_notification WHERE recipient_id = ?",
                Long.class,
                1L
        );

        mockMvc.perform(
                        patch("/api/v1/notifications/" + notificationId + "/read")
                                .with(memberToken(2L))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    private long tenantIdOf(String code) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_tenant WHERE code = ?",
                Long.class,
                code
        );

        return id == null ? -1L : id;
    }

    private RequestPostProcessor memberToken(long actorId) {
        String username = actorId == 1L ? "agent-one" : "agent-two";

        return request -> {
            Jwt jwt = Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject(username)
                    .claim("tenantId", tenantId)
                    .claim("actorId", actorId)
                    .claim("actorType", "MEMBER")
                    .claim("roles", List.of(BuiltInRoles.AGENT))
                    .build();

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(
                            jwt,
                            TestAuthorities.authorities(
                                    jdbcTemplate,
                                    BuiltInRoles.AGENT
                            ),
                            username
                    );

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);

            TestSecurityContextHolder.setContext(context);

            return request;
        };
    }
}

package com.example.ticketflow.ticket.comment.service;

import com.example.ticketflow.auth.security.ActorType;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.role.service.BuiltInRoles;
import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.support.TestAuthorities;
import com.example.ticketflow.tenant.dto.CreateTenantRequest;
import com.example.ticketflow.tenant.service.TenantService;
import com.example.ticketflow.ticket.comment.domain.TicketComment;
import com.example.ticketflow.ticket.comment.domain.enums.CommentType;
import com.example.ticketflow.ticket.comment.dto.CreateCommentRequest;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.dto.CreateTicketRequest;
import com.example.ticketflow.ticket.service.TicketService;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F1：评论分型。
 *
 * <p>核心是 docs/06 §5 那条规则：只有<b>企业成员的公开回复</b>才算首次响应。
 * 在 F1 之前评论只有一种类型，"第一条成员评论"和"第一条成员公开回复"是等价的，
 * 所以那条规则是<b>巧合成立的</b>；现在评论分了型，它必须真的被判断。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class TicketCommentTypeTest {

    /**
     * 客户 ID 故意选一个 {@code tf_user} 里不存在的号。
     *
     * <p>老结构上 {@code fk_comment_author} 指向 {@code tf_user(id)}，
     * 这条评论会直接插入失败；它能写进去，说明 V16 真的把外键删掉了——
     * 这条断言就是防止将来有人"顺手把外键加回来"。</p>
     */
    private static final long CUSTOMER_ID = 7L;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketCommentService ticketCommentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    private long tenantId;

    private CurrentActor admin;

    private CurrentActor customer;

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
                new CreateTenantRequest("comment-type", "Comment Type")
        );

        tenantId = tenantIdOf("comment-type");

        jdbcTemplate.update(
                """
                        INSERT INTO tf_user
                            (id, tenant_id, username, password_hash, display_name, status)
                        VALUES (1, ?, 'admin-one', 'test-hash', 'Admin One', 'ACTIVE')
                        """,
                tenantId
        );

        admin = new CurrentActor(
                tenantId,
                ActorType.MEMBER,
                1L,
                "admin-one",
                TestAuthorities.permissionCodes(jdbcTemplate, BuiltInRoles.ADMIN)
        );

        customer = new CurrentActor(
                tenantId,
                ActorType.CUSTOMER,
                CUSTOMER_ID,
                "customer@example.com",
                Set.of()
        );
    }

    @Test
    void shouldNotCountInternalNoteAsFirstResponse() {
        long ticketId = createTicket("TYPE-001");

        ticketCommentService.createComment(
                admin,
                ticketId,
                new CreateCommentRequest("先内部确认一下环境", CommentType.INTERNAL_NOTE)
        );

        assertEquals("INTERNAL_NOTE", latestCommentTypeOf(ticketId));
        assertEquals("MEMBER", latestAuthorTypeOf(ticketId));

        // 内部备注客户看不到，客户并没有收到响应，所以不能算首次响应
        assertNull(ticketOf(ticketId).getFirstRespondedAt());
        assertEquals(
                SlaStatus.NORMAL,
                ticketOf(ticketId).getResponseSlaStatus()
        );
    }

    @Test
    void shouldCountFirstPublicReplyAsFirstResponse() {
        long ticketId = createTicket("TYPE-002");

        ticketCommentService.createComment(
                admin,
                ticketId,
                new CreateCommentRequest("已收到，马上排查", CommentType.PUBLIC_REPLY)
        );

        assertNotNull(ticketOf(ticketId).getFirstRespondedAt());
        assertEquals(
                SlaStatus.COMPLETED,
                ticketOf(ticketId).getResponseSlaStatus()
        );
    }

    @Test
    void shouldCountFirstPublicReplyEvenIfANoteCameFirst() {
        long ticketId = createTicket("TYPE-003");

        ticketCommentService.createComment(
                admin,
                ticketId,
                new CreateCommentRequest("内部备注", CommentType.INTERNAL_NOTE)
        );
        assertNull(ticketOf(ticketId).getFirstRespondedAt());

        ticketCommentService.createComment(
                admin,
                ticketId,
                new CreateCommentRequest("公开回复", CommentType.PUBLIC_REPLY)
        );

        // 算的是"第一条公开回复"，不是"第一条评论"
        assertNotNull(ticketOf(ticketId).getFirstRespondedAt());
        assertEquals(
                SlaStatus.COMPLETED,
                ticketOf(ticketId).getResponseSlaStatus()
        );
    }

    @Test
    void shouldDefaultToPublicReplyWhenTypeIsMissing() throws Exception {
        long ticketId = createTicket("TYPE-004");

        mockMvc.perform(
                        post("/api/v1/tickets/" + ticketId + "/comments")
                                .with(jwtFor())
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {"content": "请求体里没有 commentType"}
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentType")
                        .value("PUBLIC_REPLY"))
                .andExpect(jsonPath("$.data.authorType").value("MEMBER"));

        assertEquals("PUBLIC_REPLY", latestCommentTypeOf(ticketId));
        // 缺省类型就是公开回复，所以它也算首次响应
        assertNotNull(ticketOf(ticketId).getFirstRespondedAt());
    }

    @Test
    void shouldStoreInternalNoteRequestedByMember() throws Exception {
        long ticketId = createTicket("TYPE-005");

        mockMvc.perform(
                        post("/api/v1/tickets/" + ticketId + "/comments")
                                .with(jwtFor())
                                .contentType(APPLICATION_JSON)
                                .content("""
                                        {
                                          "content": "只在内部说",
                                          "commentType": "INTERNAL_NOTE"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commentType")
                        .value("INTERNAL_NOTE"));

        assertEquals("INTERNAL_NOTE", latestCommentTypeOf(ticketId));
        assertNull(ticketOf(ticketId).getFirstRespondedAt());
    }

    @Test
    void shouldRejectInternalNoteFromCustomer() {
        long ticketId = createCustomerTicket("TYPE-006");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> ticketCommentService.createComment(
                        customer,
                        ticketId,
                        new CreateCommentRequest(
                                "客户想悄悄留个备注",
                                CommentType.INTERNAL_NOTE
                        )
                )
        );

        assertEquals("FORBIDDEN", exception.getCode());
        // 被拒之后不能留下半条评论
        assertEquals(0, commentCountOf(ticketId));
    }

    @Test
    void shouldAllowCustomerPublicReply() {
        long ticketId = createCustomerTicket("TYPE-007");

        TicketComment comment = ticketCommentService.createComment(
                customer,
                ticketId,
                new CreateCommentRequest("我也补充一下", CommentType.PUBLIC_REPLY)
        );

        assertEquals(ActorType.CUSTOMER, comment.getAuthorType());
        assertEquals(CommentType.PUBLIC_REPLY, comment.getCommentType());
        assertEquals("CUSTOMER", latestAuthorTypeOf(ticketId));

        // 客户说话不是"响应"：响应 SLA 必须原样不动
        assertNull(ticketOf(ticketId).getFirstRespondedAt());
        assertEquals(
                SlaStatus.NORMAL,
                ticketOf(ticketId).getResponseSlaStatus()
        );
    }

    @Test
    void shouldShowInternalNotesToMembers() {
        long ticketId = createTicket("TYPE-008");

        ticketCommentService.createComment(
                admin,
                ticketId,
                new CreateCommentRequest("内部备注", CommentType.INTERNAL_NOTE)
        );
        ticketCommentService.createComment(
                admin,
                ticketId,
                new CreateCommentRequest("公开回复", CommentType.PUBLIC_REPLY)
        );

        // 成员侧列表不过滤：内部备注就是给成员看的
        assertEquals(
                2,
                ticketCommentService.listComments(admin, ticketId).size()
        );
    }

    private long createTicket(String ticketNo) {
        return ticketService.createTicket(
                admin,
                new CreateTicketRequest(
                        ticketNo,
                        "评论分型测试",
                        null,
                        TicketPriority.MEDIUM
                )
        ).getId();
    }

    private long createCustomerTicket(String ticketNo) {
        return ticketService.createTicket(
                customer,
                new CreateTicketRequest(
                        ticketNo,
                        "客户提的单",
                        null,
                        TicketPriority.MEDIUM
                )
        ).getId();
    }

    private Ticket ticketOf(long ticketId) {
        return ticketService.findTicket(admin, ticketId);
    }

    private String latestCommentTypeOf(long ticketId) {
        List<String> values = jdbcTemplate.queryForList(
                """
                        SELECT comment_type
                        FROM tf_ticket_comment
                        WHERE ticket_id = ?
                        ORDER BY id
                        """,
                String.class,
                ticketId
        );

        return values.isEmpty() ? null : values.get(values.size() - 1);
    }

    private String latestAuthorTypeOf(long ticketId) {
        List<String> values = jdbcTemplate.queryForList(
                """
                        SELECT author_type
                        FROM tf_ticket_comment
                        WHERE ticket_id = ?
                        ORDER BY id
                        """,
                String.class,
                ticketId
        );

        return values.isEmpty() ? null : values.get(values.size() - 1);
    }

    private int commentCountOf(long ticketId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tf_ticket_comment WHERE ticket_id = ?",
                Integer.class,
                ticketId
        );

        return count == null ? 0 : count;
    }

    private long tenantIdOf(String code) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM tf_tenant WHERE code = ?",
                Long.class,
                code
        );

        return id == null ? -1L : id;
    }

    private RequestPostProcessor jwtFor() {
        return request -> {
            Jwt jwt = Jwt.withTokenValue("test-token")
                    .header("alg", "HS256")
                    .subject("admin-one")
                    .claim("tenantId", tenantId)
                    .claim("actorId", 1L)
                    .claim("actorType", "MEMBER")
                    .claim("roles", List.of(BuiltInRoles.ADMIN))
                    .build();

            JwtAuthenticationToken authentication =
                    new JwtAuthenticationToken(
                            jwt,
                            TestAuthorities.authorities(
                                    jdbcTemplate,
                                    BuiltInRoles.ADMIN
                            ),
                            "admin-one"
                    );

            SecurityContext context =
                    SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);

            TestSecurityContextHolder.setContext(context);

            return request;
        };
    }
}

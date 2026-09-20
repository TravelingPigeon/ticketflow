package com.example.ticketflow.ticket.comment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import com.example.ticketflow.notification.domain.enums.NotificationType;
import com.example.ticketflow.notification.service.NotificationService;
import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.ticket.comment.domain.TicketComment;
import com.example.ticketflow.ticket.comment.domain.enums.CommentType;
import com.example.ticketflow.ticket.comment.dto.CreateCommentRequest;
import com.example.ticketflow.ticket.comment.mapper.TicketCommentMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;
import com.example.ticketflow.ticket.mapper.TicketMapper;
import com.example.ticketflow.ticket.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TicketCommentService {

    private final TicketCommentMapper ticketCommentMapper;
    private final TicketMapper ticketMapper;
    private final TicketService ticketService;
    private final NotificationService notificationService;

    public TicketCommentService(
            TicketCommentMapper ticketCommentMapper,
            TicketMapper ticketMapper,
            TicketService ticketService,
            NotificationService notificationService
    ) {
        this.ticketCommentMapper = ticketCommentMapper;
        this.ticketMapper = ticketMapper;
        this.ticketService = ticketService;
        this.notificationService = notificationService;
    }

    @Transactional
    public TicketComment createComment(
            CurrentActor actor,
            Long ticketId,
            CreateCommentRequest request
    ) {
        Ticket ticket = findVisibleTicket(actor, ticketId);

        requireCommentable(ticket);

        requireCommentTypeAllowed(actor, request.commentType());

        TicketComment comment = new TicketComment();
        comment.setTenantId(actor.tenantId());
        comment.setTicketId(ticket.getId());
        comment.setAuthorType(actor.actorType());
        comment.setAuthorId(actor.actorId());
        comment.setCommentType(request.commentType());
        comment.setContent(request.content().trim());

        ticketCommentMapper.insert(comment);

        // 客户回复"等待客户"的单 → 自动回到处理中。
        // 状态机、乐观锁和审计都在 TicketService 里，这里只负责在正确的时机调用它。
        ticketService.resumeAfterCustomerReply(actor, ticket);

        // 首次响应只认企业成员的公开回复（客户回复不算响应）
        recordFirstResponse(ticket, actor, request.commentType());

        // 客户说话了，得让负责人知道
        notifyAssigneeAboutCustomerReply(actor, ticket, comment);

        return ticketCommentMapper.selectById(comment.getId());
    }

    /**
     * 记录首次响应：只认<b>企业成员的公开回复</b>。
     *
     * <p>这正是 docs/06 §5 说的规则，也是 L2c 留下的那个衔接点：
     * 在 F1 之前评论只有一种类型，所以"第一条成员评论"和"第一条成员公开回复"
     * 是等价的；现在评论分了型，两个条件缺一不可——</p>
     * <ul>
     *   <li>客户的回复不是"响应"，那是客户在说话；</li>
     *   <li>内部备注不对客户可见，客户根本没收到响应，自然不能算。</li>
     * </ul>
     */
    private void recordFirstResponse(
            Ticket ticket,
            CurrentActor actor,
            CommentType commentType
    ) {
        if (!actor.isMember() || commentType != CommentType.PUBLIC_REPLY) {
            return;
        }

        if (ticket.getFirstRespondedAt() != null) {
            return;
        }

        ticket.setFirstRespondedAt(LocalDateTime.now());

        // 已经违约的保留违约状态、只补时间——事后回复不该把违约记录洗掉
        if (ticket.getResponseSlaStatus() != SlaStatus.BREACHED) {
            ticket.setResponseSlaStatus(SlaStatus.COMPLETED);
        }

        // 并发下另一个请求可能先记上了：乐观锁会让这次更新影响 0 行，
        // 而"首次响应只记一次"正是我们要的结果，所以不报错、直接放过
        ticketMapper.updateById(ticket);
    }

    /**
     * 客户只能发公开回复。
     *
     * <p>内部备注是"说给同事看的话"，客户能发就失去意义了。
     * 这条规则放在 Service 而不是 Controller：F1-2 接客户评论入口时，
     * 不需要在新入口里重复写一遍——写评论永远只有这一条路径。</p>
     */
    private void requireCommentTypeAllowed(
            CurrentActor actor,
            CommentType commentType
    ) {
        if (actor.isCustomer() && commentType != CommentType.PUBLIC_REPLY) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "客户只能发表公开回复"
            );
        }
    }

    /**
     * 已关闭的工单不能再评论（docs/06 §4：{@code reply} / {@code note} 只在"未关闭状态"可用）。
     *
     * <p>客户想继续说，应该走"申请重开"——那条路径会先把状态推回处理中，
     * 再把原因作为一条公开回复写回来。这也解释了
     * {@link #reopenByCustomerWithReason} 为什么必须<b>先改状态、再写评论</b>：
     * 顺序反了，这次插入会被这条规则当场挡住。</p>
     */
    private void requireCommentable(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "工单已关闭，如需继续请申请重开"
            );
        }
    }

    /**
     * 客户申请重开：先把状态推回处理中，再把"原因"作为一条公开回复留下来。
     *
     * <p><b>原因为什么存成评论</b>：审计表只记"谁在什么时候把状态从 A 改成 B"，
     * 而且门户不返回操作时间线——原因塞进审计表，写它的客户自己都看不到。
     * 存成公开回复，客服和客户都看得到，还顺带复用了"客户回复 → 通知负责人"这条链路。</p>
     *
     * <p><b>它为什么在评论服务里</b>：在本设计里"重开"就是"一条带原因的公开回复 +
     * 一次状态推进"，两件事合起来才是一次完整命令。如果将来它继续膨胀
     * （要带附件、要单独通知客户、要限制重开次数），那就该抽一个专门的编排服务，
     * 而不是让这个类越滚越大。</p>
     */
    @Transactional
    public Ticket reopenByCustomerWithReason(
            CurrentActor actor,
            Long ticketId,
            String reason
    ) {
        Ticket reopened = ticketService.reopenByCustomer(actor, ticketId);

        // 同类内部调用不再经过 Spring 代理，@Transactional 不会重新生效——
        // 但我们本来就在事务里（上面那行也是），所以这里是安全的。
        createComment(
                actor,
                ticketId,
                new CreateCommentRequest(reason, CommentType.PUBLIC_REPLY)
        );

        return reopened;
    }

    /**
     * 客户公开回复时，通知当前负责人（docs/06 §13）。
     *
     * <p>三种情况都不通知：</p>
     * <ul>
     *   <li>成员自己发的公开回复——他本来就在处理这张单，通知他"你回复了"是噪音；</li>
     *   <li>内部备注——那是同事之间的对话，不产生对外事件；</li>
     *   <li>工单没有负责人——按设计稿，这条规则只发给"当前负责人"，
     *       没人负责时工单在共享队列里，由 SLA 提醒去催管理员。</li>
     * </ul>
     */
    private void notifyAssigneeAboutCustomerReply(
            CurrentActor actor,
            Ticket ticket,
            TicketComment comment
    ) {
        if (!actor.isCustomer()
                || comment.getCommentType() != CommentType.PUBLIC_REPLY) {
            return;
        }

        if (ticket.getAssigneeId() == null) {
            return;
        }

        notificationService.notifyMember(
                ticket.getTenantId(),
                ticket.getAssigneeId(),
                NotificationType.CUSTOMER_REPLIED,
                ticket,
                "客户回复了工单",
                "工单 " + ticket.getTicketNo()
                        + "（" + ticket.getTitle() + "）收到客户回复："
                        + summaryOf(comment.getContent()),
                "ticket:customer-replied:" + ticket.getId()
                        + ":comment:" + comment.getId()
                        + ":member:" + ticket.getAssigneeId()
        );
    }

    /** 通知正文只放评论开头：content 字段上限 500，评论本身可以有 5000 字 */
    private String summaryOf(String content) {
        return content.length() <= 100
                ? content
                : content.substring(0, 100) + "…";
    }

    public List<TicketComment> listComments(
            CurrentActor actor,
            Long ticketId
    ) {
        findVisibleTicket(actor, ticketId);

        return ticketCommentMapper.selectList(
                new LambdaQueryWrapper<TicketComment>()
                        .eq(TicketComment::getTenantId, actor.tenantId())
                        .eq(TicketComment::getTicketId, ticketId)
                        // 客户看不到内部备注。过滤条件跟着"查询者的视角"走，
                        // 而不是指望每个 Controller 自己记得过滤——
                        // 将来多一个入口忘了过滤，就等于漏了内部备注。
                        .eq(
                                actor.isCustomer(),
                                TicketComment::getCommentType,
                                CommentType.PUBLIC_REPLY
                        )
                        .orderByAsc(TicketComment::getCreatedAt)
        );
    }

    private Ticket findVisibleTicket(
            CurrentActor actor,
            Long ticketId
    ) {
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getId, ticketId)
                        .eq(Ticket::getTenantId, actor.tenantId())
                        .eq(
                                actor.isCustomer(),
                                Ticket::getCustomerId,
                                actor.actorId()
                        )
        );

        if (ticket == null) {
            throw new BusinessException(
                    ErrorCode.TICKET_NOT_FOUND,
                    "工单不存在"
            );
        }

        return ticket;
    }
}

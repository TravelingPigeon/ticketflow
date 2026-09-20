package com.example.ticketflow.ticket.comment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.ticket.comment.domain.TicketComment;
import com.example.ticketflow.ticket.comment.domain.enums.CommentType;
import com.example.ticketflow.ticket.comment.dto.CreateCommentRequest;
import com.example.ticketflow.ticket.comment.mapper.TicketCommentMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TicketCommentService {

    private final TicketCommentMapper ticketCommentMapper;
    private final TicketMapper ticketMapper;

    public TicketCommentService(
            TicketCommentMapper ticketCommentMapper,
            TicketMapper ticketMapper
    ) {
        this.ticketCommentMapper = ticketCommentMapper;
        this.ticketMapper = ticketMapper;
    }

    @Transactional
    public TicketComment createComment(
            CurrentActor actor,
            Long ticketId,
            CreateCommentRequest request
    ) {
        Ticket ticket = findVisibleTicket(actor, ticketId);

        requireCommentTypeAllowed(actor, request.commentType());

        TicketComment comment = new TicketComment();
        comment.setTenantId(actor.tenantId());
        comment.setTicketId(ticket.getId());
        comment.setAuthorType(actor.actorType());
        comment.setAuthorId(actor.actorId());
        comment.setCommentType(request.commentType());
        comment.setContent(request.content().trim());

        ticketCommentMapper.insert(comment);

        recordFirstResponse(ticket, actor, request.commentType());

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

    public List<TicketComment> listComments(
            CurrentActor actor,
            Long ticketId
    ) {
        findVisibleTicket(actor, ticketId);

        return ticketCommentMapper.selectList(
                new LambdaQueryWrapper<TicketComment>()
                        .eq(TicketComment::getTenantId, actor.tenantId())
                        .eq(TicketComment::getTicketId, ticketId)
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

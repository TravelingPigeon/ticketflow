package com.example.ticketflow.ticket.comment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.common.exception.ErrorCode;
import com.example.ticketflow.sla.domain.enums.SlaStatus;
import com.example.ticketflow.ticket.comment.domain.TicketComment;
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

        TicketComment comment = new TicketComment();
        comment.setTenantId(actor.tenantId());
        comment.setTicketId(ticket.getId());
        comment.setAuthorId(actor.actorId());
        comment.setContent(request.content().trim());

        ticketCommentMapper.insert(comment);

        recordFirstResponse(ticket, actor);

        return ticketCommentMapper.selectById(comment.getId());
    }

    /**
     * 记录首次响应：只认<b>企业成员</b>的第一条评论。
     *
     * <p>当前评论入口只对成员开放，所以"是不是成员"这个判断现在恒为真；
     * 等 F1 开放客户评论之后，它就会拦住"客户回复也算首次响应"这个错误。</p>
     */
    private void recordFirstResponse(Ticket ticket, CurrentActor actor) {
        if (!actor.isMember()) {
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
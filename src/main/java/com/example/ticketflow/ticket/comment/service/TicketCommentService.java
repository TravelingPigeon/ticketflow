package com.example.ticketflow.ticket.comment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.auth.security.CurrentActor;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.ticket.comment.domain.TicketComment;
import com.example.ticketflow.ticket.comment.dto.CreateCommentRequest;
import com.example.ticketflow.ticket.comment.mapper.TicketCommentMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Service;

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

    public TicketComment createComment(
            CurrentActor actor,
            Long ticketId,
            CreateCommentRequest request
    ) {
        Ticket ticket = findVisibleTicket(actor, ticketId);

        TicketComment comment = new TicketComment();
        comment.setTenantId(actor.tenantId());
        comment.setTicketId(ticket.getId());
        comment.setAuthorId(actor.userId());
        comment.setContent(request.content().trim());

        ticketCommentMapper.insert(comment);

        return ticketCommentMapper.selectById(comment.getId());
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
                                actor.isRequester(),
                                Ticket::getCreatedBy,
                                actor.userId()
                        )
        );

        if (ticket == null) {
            throw new BusinessException(
                    "TICKET_NOT_FOUND",
                    "工单不存在"
            );
        }

        return ticket;
    }
}
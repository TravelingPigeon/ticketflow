package com.example.ticketflow.ticket.comment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.ticket.comment.domain.TicketComment;
import com.example.ticketflow.ticket.comment.dto.CreateCommentRequest;
import com.example.ticketflow.ticket.comment.mapper.TicketCommentMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.mapper.TicketMapper;
import com.example.ticketflow.user.domain.UserAccount;
import com.example.ticketflow.user.domain.enums.UserStatus;
import com.example.ticketflow.user.mapper.UserAccountMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TicketCommentService {

    private final TicketCommentMapper ticketCommentMapper;
    private final TicketMapper ticketMapper;
    private final UserAccountMapper userAccountMapper;

    public TicketCommentService(
            TicketCommentMapper ticketCommentMapper,
            TicketMapper ticketMapper,
            UserAccountMapper userAccountMapper
    ) {
        this.ticketCommentMapper = ticketCommentMapper;
        this.ticketMapper = ticketMapper;
        this.userAccountMapper = userAccountMapper;
    }

    public TicketComment createComment(
            Long tenantId,
            Long ticketId,
            String username,
            CreateCommentRequest request
    ) {
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getId, ticketId)
                        .eq(Ticket::getTenantId, tenantId)
        );

        if (ticket == null) {
            throw new BusinessException(
                    "TICKET_NOT_FOUND",
                    "工单不存在"
            );
        }

        UserAccount author = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getTenantId, tenantId)
                        .eq(UserAccount::getUsername, username)
                        .eq(UserAccount::getStatus, UserStatus.ACTIVE)
        );

        if (author == null) {
            throw new BusinessException(
                    "AUTHOR_NOT_FOUND",
                    "发表评论的用户不存在"
            );
        }

        TicketComment comment = new TicketComment();
        comment.setTenantId(tenantId);
        comment.setTicketId(ticketId);
        comment.setAuthorId(author.getId());
        comment.setContent(request.content().trim());

        ticketCommentMapper.insert(comment);

        return ticketCommentMapper.selectById(comment.getId());
    }

    public List<TicketComment> listComments(
            Long tenantId,
            Long ticketId
    ) {
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getId, ticketId)
                        .eq(Ticket::getTenantId, tenantId)
        );

        if (ticket == null) {
            throw new BusinessException(
                    "TICKET_NOT_FOUND",
                    "工单不存在"
            );
        }

        return ticketCommentMapper.selectList(
                new LambdaQueryWrapper<TicketComment>()
                        .eq(TicketComment::getTenantId, tenantId)
                        .eq(TicketComment::getTicketId, ticketId)
                        .orderByAsc(TicketComment::getCreatedAt)
        );
    }
}
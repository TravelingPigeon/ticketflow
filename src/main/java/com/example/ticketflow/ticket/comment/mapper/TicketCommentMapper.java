package com.example.ticketflow.ticket.comment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticketflow.ticket.comment.domain.TicketComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketCommentMapper
        extends BaseMapper<TicketComment> {
}
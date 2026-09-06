package com.example.ticketflow.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
}
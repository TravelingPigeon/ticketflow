package com.example.ticketflow.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ticketflow.common.exception.BusinessException;
import com.example.ticketflow.tenant.domain.Tenant;
import com.example.ticketflow.tenant.mapper.TenantMapper;
import com.example.ticketflow.ticket.domain.Ticket;
import com.example.ticketflow.ticket.domain.enums.TicketPriority;
import com.example.ticketflow.ticket.domain.enums.TicketStatus;
import com.example.ticketflow.ticket.dto.CreateTicketRequest;
import com.example.ticketflow.ticket.mapper.TicketMapper;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

    private final TicketMapper ticketMapper;
    private final TenantMapper tenantMapper;

    public TicketService(
            TicketMapper ticketMapper,
            TenantMapper tenantMapper
    ) {
        this.ticketMapper = ticketMapper;
        this.tenantMapper = tenantMapper;
    }

    public Ticket createTicket(CreateTicketRequest request) {
        Tenant tenant = tenantMapper.selectById(request.tenantId());

        if (tenant == null) {
            throw new BusinessException(
                    "TENANT_NOT_FOUND",
                    "租户不存在"
            );
        }

        Ticket existingTicket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getTenantId, request.tenantId())
                        .eq(Ticket::getTicketNo, request.ticketNo())
        );

        if (existingTicket != null) {
            throw new BusinessException(
                    "TICKET_NO_EXISTS",
                    "当前租户下工单编号已存在"
            );
        }

        Ticket ticket = new Ticket();
        ticket.setTenantId(request.tenantId());
        ticket.setTicketNo(request.ticketNo().trim());
        ticket.setTitle(request.title().trim());
        ticket.setDescription(request.description());
        ticket.setStatus(TicketStatus.OPEN);

        if (request.priority() == null) {
            ticket.setPriority(TicketPriority.MEDIUM);
        } else {
            ticket.setPriority(request.priority());
        }

        ticketMapper.insert(ticket);

        return ticketMapper.selectById(ticket.getId());
    }
}